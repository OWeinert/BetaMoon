package betamoon.luaapi.interop;

import betamoon.hooks.HookCallbackFailure;
import betamoon.hooks.HookDeclarations;
import betamoon.hooks.HookDefinition;
import betamoon.hooks.HookDispatcher;
import betamoon.hooks.HookPreparation;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real Lua facades/conversions. No display or Minecraft classes are needed for
 * these contract checks.
 */
public final class NativeHookOperationApiTest {
    private static final String OWNER = "operation-lua.lua";

    public static void run(HookPreparation preparation, TinyMappingResolver mappings, Class<?> target, Object receiver,
            Object other, Method method, String declarations) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(target.getClassLoader());
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        Globals globals = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        LuaTable nativeApi = new LuaTable();
        new NativeAccessApi(NativeIndexSnapshot::empty).attach(nativeApi);
        module.set("native", nativeApi);
        globals.set("betamoon", module);
        HookDispatcher.Subscription before = subscribe(preparation, target, globals, bindings, declarations, "before",
                "saved=operation; savedReceiver=operation.receiver; "
                        + "assert(operation.mode=='invoke_before' and operation.point.ordinal==1 and operation.point.matches==2); "
                        + "assert(operation.arguments.count==1 and operation.arguments:get(0)==42); "
                        + "assert(operation.enclosing.arguments.count==2 and operation.enclosing.arguments:get(1)==1); "
                        + "assert(not pcall(function() operation.enclosing.arguments:set(1,3) end)); "
                        + "assert(not operation.native:describe().gameplayWrites); " + "operation.arguments:set(0,9)",
                true);
        HookDispatcher.Subscription after = subscribe(preparation, target, globals, bindings, declarations, "after",
                "assert(operation.result==18 and operation.arguments:get(0)==9); operation:setResult(21)", true);
        require(method.invoke(receiver, other, 1).equals(23), "Lua invoke operand/result edits");
        globals.load("assert(not pcall(function() return saved.arguments:get(0) end)); "
                + "assert(not pcall(function() savedReceiver:describe() end))").call();
        before.close();
        after.close();
        HookDispatcher.Subscription denied = subscribe(preparation, target, globals, bindings, declarations, "before",
                "operation.arguments:set(0,9)", false);
        events(target).clear();
        expectFailure(method, receiver, other);
        require(events(target).size() == 3 && events(target).get(2).equals("second:42"),
                "invalid edit preserves completed argument effects but never invokes the selected callee");
        denied.close();
        HookDispatcher.Subscription invalid = subscribe(preparation, target, globals, bindings, declarations, "after",
                "operation:setResult('wrong int')", true);
        events(target).clear();
        expectFailure(method, receiver, other);
        require(events(target).size() == 4 && events(target).get(3).equals("callee:42"),
                "after edit failure keeps the completed native effect exactly once");
        invalid.close();
        HookDispatcher.Subscription field = subscribe(preparation, target, globals, bindings, declarations, "write",
                "assert(operation.value:decimal()=='7' and operation.arguments.count==0); "
                        + "assert(operation.enclosing.arguments:get(0):decimal()=='7'); "
                        + "operation:setValue(betamoon.native:long('12')); assert(operation.value:decimal()=='12')",
                true);
        target.getMethod("write", long.class).invoke(receiver, 7L);
        require(target.getField("counter").getLong(receiver) == 12L,
                "Lua field value access uses exact native conversion");
        field.close();
        HookDefinition definition = definition(declarations, "after");
        HookDispatcher.Subscription invalidated = HookDispatcher.subscribe(preparation.targets(definition).get(0).id,
                target, definition, invocation -> {
                    invocation.result(9999);
                    invocation.invalidateObservation();
                    throw new IllegalStateException("stale observation cannot publish its replacement");
                });
        HookDispatcher.activate(Collections.singletonList(invalidated));
        require(method.invoke(receiver, other, 1).equals(86),
                "local invalidation preserves already completed operation result");
        invalidated.close();
        bindings.close();
        loaders.close();
        System.out.println(
                "Operation Lua contracts passed: exact edits, distinct enclosing data, expiry, failure and invalidation.");
    }

    private static HookDispatcher.Subscription subscribe(HookPreparation preparation, Class<?> target, Globals globals,
            NativeBindings bindings, String declarations, String id, String body, boolean edits) {
        HookDefinition definition = definition(declarations, id);
        LuaValue callback = globals.load("return function(operation) " + body + " end").call();
        HookDispatcher.Subscription subscription = HookDispatcher.subscribe(preparation.targets(definition).get(0).id,
                target, definition, invocation -> ScriptExecution.withOwner(OWNER, () -> {
                    try (LuaCallbackScope scope = new LuaCallbackScope(false, false);
                            NativeContext context = NativeContext.open(bindings, OWNER, scope, false,
                                    Collections.emptyMap(), () -> true, NativeEntry.Phase.HOOK)) {
                        NativeHookOperationCall call = new NativeHookOperationCall(invocation, () -> context, edits);
                        ScriptExecution.invokeNative(OWNER, "operation:" + id, callback, call);
                    }
                    return null;
                }));
        HookDispatcher.activate(Collections.singletonList(subscription));
        return subscription;
    }

    private static HookDefinition definition(String declarations, String id) {
        return HookDeclarations
                .evaluate("operations", "hooks.lua", declarations, 1, null, new HookDeclarations.Budget()).stream()
                .map(value -> value.definition).filter(value -> value.id.equals(id)).findFirst().get();
    }

    @SuppressWarnings("unchecked")
    private static List<String> events(Class<?> target) throws Exception {
        return (List<String>) target.getField("EVENTS").get(null);
    }

    private static void expectFailure(Method method, Object receiver, Object other) throws Exception {
        try {
            method.invoke(receiver, other, 1);
            throw new AssertionError("invalid operation edit fabricated success");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() instanceof HookCallbackFailure,
                    "editing failure remains a hook callback failure");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
