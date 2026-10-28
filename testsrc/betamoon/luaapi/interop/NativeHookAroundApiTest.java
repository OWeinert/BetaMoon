package betamoon.luaapi.interop;

import betamoon.hooks.HookDeclarations;
import betamoon.hooks.HookCallbackFailure;
import betamoon.hooks.HookDispatcher;
import betamoon.hooks.HookFileCollectionContext;
import betamoon.hooks.HookPreparation;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import org.objectweb.asm.Type;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.function.Supplier;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * LuaJ return/conversion/failure tests; real game lifecycle suspension is
 * verified in rendered acceptance.
 */
public final class NativeHookAroundApiTest {
    private static final String OWNER = "around-lua.lua";

    public static void run(HookPreparation preparation, TinyMappingResolver mappings, Class<?> target, Object receiver,
            Method method) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(target.getClassLoader());
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, scope, true, Collections.emptyMap(),
                            () -> true)) {
                for (Method candidate : target.getDeclaredMethods()) {
                    if (!candidate.isSynthetic() || !candidate.getName().startsWith("betamoon$hook$body$")) {
                        continue;
                    }
                    try {
                        bindings.exact(
                                new NativeMemberRef(NativeMemberRef.Kind.METHOD,
                                        NativeTypeRef.named(target.getName(), "runtime", null, null), null,
                                        candidate.getName(), Type.getMethodDescriptor(candidate), "runtime", "runtime"),
                                context, true);
                        throw new AssertionError("private generated helper exposed through normal direct binding");
                    } catch (NativeAccessFailure expected) {
                        require(expected.reason.equals("member_not_found"), "helper selection is hidden");
                    }
                }
            }
            return null;
        });
        Globals globals = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        LuaTable nativeApi = new LuaTable();
        new NativeAccessApi(NativeIndexSnapshot::empty).attach(nativeApi);
        module.set("native", nativeApi);
        globals.set("betamoon", module);
        HookDispatcher.Subscription subscription = subscribe(preparation, target, globals, bindings, "",
                "saved=call; old=call.receiver; before=call.native; local value=call:proceed(); "
                        + "assert(not pcall(function() old:describe() end)); "
                        + "assert(not pcall(function() before:describe() end)); "
                        + "assert(call.receiver:describe().bound and not call.native:describe().gameplayWrites); "
                        + "assert(not pcall(function() call:proceed() end)); return value+10");
        require(method.invoke(receiver, 1, 2L, 3.0, null).equals(13), "Lua continuation and typed return");
        globals.load("assert(not pcall(function() saved:proceed() end)); "
                + "assert(not pcall(function() return saved.receiver end))").call();
        subscription.close();
        subscription = subscribe(preparation, target, globals, bindings, "", "return call:proceed()");
        int bodies = target.getField("bodyCalls").getInt(null);
        ScriptExecution.withOwner("query-caller.lua", () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(false, false);
                    NativeContext parent = NativeContext.open(bindings, "query-caller.lua", scope, false,
                            Collections.emptyMap(), () -> true)) {
                try {
                    method.invoke(receiver, 1, 2L, 3.0, null);
                    throw new AssertionError("around continuation gained query edit authority");
                } catch (InvocationTargetException expected) {
                    require(expected.getCause() instanceof HookCallbackFailure,
                            "read-only enclosing caller refuses around edits");
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
                parent.check();
                try {
                    LuaCallbackScope.requireGameplayWrite();
                    throw new AssertionError("around failure removed the enclosing query restriction");
                } catch (RuntimeException expected) {
                    // The enclosing query remains installed after callback cleanup.
                }
            }
            return null;
        });
        require(target.getField("bodyCalls").getInt(null) == bodies,
                "query refusal never executes native continuation");
        LuaCallbackScope.requireGameplayWrite();
        subscription.close();
        subscription = subscribe(preparation, target, globals, bindings, "",
                "return call:proceedWith(4, betamoon.native:long('2'), 3.0, betamoon.native.NULL)");
        require(method.invoke(receiver, 1, 2L, 3.0, null).equals(6), "Lua exact replacement parameters");
        subscription.close();
        RuntimeException original = new IllegalStateException("native around Lua failure");
        target.getField("injectedFailure").set(null, original);
        subscription = subscribe(preparation, target, globals, bindings, "",
                "local ok=pcall(function() call:proceed() end); assert(not ok); "
                        + "evidence=call.failure; assert(evidence.type=='java.lang.IllegalStateException'); return 1");
        try {
            method.invoke(receiver, -2, 2L, 3.0, null);
            throw new AssertionError("Lua pcall swallowed native failure");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() == original, "Lua pcall keeps original native Throwable");
        }
        globals.load("assert(not pcall(function() return evidence.type end))").call();
        subscription.close();
        subscription = subscribe(preparation, target, globals, bindings, ",nativeErrors='recover'",
                "local ok=pcall(function() call:proceed() end); assert(not ok); return 23");
        require(method.invoke(receiver, -2, 2L, 3.0, null).equals(23), "explicit Lua recovery");
        subscription.close();
        target.getField("injectedFailure").set(null, null);
        bindings.close();
        loaders.close();
        System.out.println(
                "Around Lua contracts passed: typed proceed/proceedWith, expired views, scoped failure evidence and recovery.");
    }

    private static HookDispatcher.Subscription subscribe(HookPreparation preparation, Class<?> target, Globals globals,
            NativeBindings bindings, String options, String body) {
        String text = "local r=betamoon.hooks:createRegistry(); r:register({id='around_lua',target=betamoon.native:type('"
                + target.getName() + "'):method('calculate','(IJDLjava/lang/Object;)I'),at='around',effects='edit'"
                + options + "},function(call) " + body + " end)";
        HookFileCollectionContext.Registration registration = HookDeclarations
                .evaluate("owner", "hooks.lua", text, 1, globals, new HookDeclarations.Budget()).get(0);
        HookDispatcher.Subscription subscription = HookDispatcher.subscribe(
                preparation.targets(registration.definition).get(0).id, target, registration.definition, invocation -> {
                    String caller = LuaScriptRegistry.getCurrentScriptFile();
                    boolean edits = NativeContext.hookEditsAllowed();
                    ScriptExecution.withOwner(OWNER, () -> {
                        try (Observation observation = new Observation(bindings, caller)) {
                            NativeHookCall call = new NativeHookCall(invocation, () -> observation.context, edits,
                                    values -> observation.proceed(() -> invocation.proceed(values)));
                            Varargs returned = ScriptExecution.invokeNative(OWNER, "around", registration.callback,
                                    call);
                            call.complete(returned);
                        }
                        return null;
                    });
                });
        HookDispatcher.activate(Collections.singletonList(subscription));
        return subscription;
    }

    private static final class Observation implements AutoCloseable {
        private final NativeBindings bindings;
        private final String caller;
        private LuaCallbackScope scope;
        private NativeContext context;

        Observation(NativeBindings bindings, String caller) {
            this.bindings = bindings;
            this.caller = caller;
            open();
        }

        private void open() {
            scope = new LuaCallbackScope(false, false);
            context = NativeContext.open(bindings, OWNER, scope, false, Collections.emptyMap(), () -> true,
                    NativeEntry.Phase.HOOK);
        }

        Object proceed(Supplier<Object> continuation) {
            close();
            try {
                return ScriptExecution.withOwner(caller, continuation);
            } finally {
                open();
            }
        }

        @Override
        public void close() {
            context.close();
            scope.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
