package betamoon.luaapi.interop;

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
import java.util.Collections;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real read-only constructor facade, scoped receivers and no
 * edit/cancellation/continuation surface.
 */
public final class NativeHookConstructedApiTest {
    public static void run(HookPreparation preparation, TinyMappingResolver mappings, Class<?> type,
            HookDefinition definition) throws Exception {
        String owner = "constructor-lua.lua";
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(type.getClassLoader());
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        Globals globals = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        LuaTable nativeApi = new LuaTable();
        new NativeAccessApi(NativeIndexSnapshot::empty).attach(nativeApi);
        module.set("native", nativeApi);
        globals.set("betamoon", module);
        LuaValue callback = globals.load("return function(call) "
                + "assert(call.mode=='constructed' and call.arguments.count==1 and call.arguments:get(0)==6); "
                + "assert(call.receiver:describe().bound and not call.native:describe().gameplayWrites); "
                + "assert(call.cancel==nil and call.proceed==nil and call.setResult==nil and call.arguments.set==nil); "
                + "saved=call; savedReceiver=call.receiver; copied=call.arguments:get(0); " + "end").call();
        HookDispatcher.Subscription subscription = HookDispatcher.subscribe(preparation.targets(definition).get(0).id,
                type, definition, invocation -> ScriptExecution.withOwner(owner, () -> {
                    try (LuaCallbackScope scope = new LuaCallbackScope(false, false);
                            NativeContext context = NativeContext.open(bindings, owner, scope, false,
                                    Collections.emptyMap(), () -> true, NativeEntry.Phase.HOOK)) {
                        ScriptExecution.invokeNative(owner, "constructed", callback,
                                new NativeHookConstructedCall(invocation, () -> context));
                    }
                    return null;
                }));
        try {
            HookDispatcher.activate(Collections.singletonList(subscription));
            Object instance = type.getConstructor(int.class).newInstance(6);
            if (instance == null || subscription.calls != 1 || subscription.failures != 0) {
                throw new AssertionError("constructor Lua observation failed or changed native construction");
            }
            globals.load("assert(copied==6); assert(not pcall(function() return saved.arguments:get(0) end)); "
                    + "assert(not pcall(function() savedReceiver:describe() end))").call();
        } finally {
            subscription.close();
            bindings.close();
            loaders.close();
        }
        System.out.println(
                "Constructor Lua facade passed: observation-only methods, scoped views and detached arguments.");
    }
}
