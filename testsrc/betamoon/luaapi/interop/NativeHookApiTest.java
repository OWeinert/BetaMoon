package betamoon.luaapi.interop;

import betamoon.hooks.HookCallbackFailure;
import betamoon.hooks.HookDeclarations;
import betamoon.hooks.HookDispatcher;
import betamoon.hooks.HookFileCollectionContext;
import betamoon.hooks.HookPreparation;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.diagnostics.NativeDiagnosticEvidenceTest.HostileFailure;
import betamoon.interop.discovery.NativeQueryService;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Actual Lua callbacks and native conversion/authority integration without a
 * game on the classpath.
 */
public final class NativeHookApiTest {
    private static final String OWNER = "hook-native.lua";

    public static void run(HookPreparation preparation, TinyMappingResolver mappings, Class<?> target, Object receiver,
            Method method) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(target.getClassLoader());
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        NativeIndexSnapshot hookSnapshot = NativeIndexSnapshot.empty().withHooks(preparation.status(),
                preparation.revision(), "available");
        Map<String, Object> query = NativeQueryService.query(hookSnapshot,
                CatalogValues.record("kind", "hooks", "owner", "owner", "limit", 200));
        require(((List<?>) query.get("items")).stream()
                .anyMatch(row -> "HOOK_SITE".equals(CatalogValues.object(row).get("kind"))),
                "owner filter includes shared target sites");
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, scope, true, Collections.emptyMap(),
                            () -> true)) {
                NativeTypeRef type = NativeTypeRef.named(target.getName(), null, null, null);
                bindings.type(type, context);
                bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.METHOD, type, null, "calculate",
                        "(IJDLjava/lang/Object;)I", null, null), context, false);
            }
            return null;
        });
        Globals globals = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        LuaTable nativeApi = new LuaTable();
        new NativeAccessApi(NativeIndexSnapshot::empty).attach(nativeApi);
        module.set("native", nativeApi);
        globals.set("betamoon", module);
        String callback = "saved=call; copied=call.arguments:get(0); "
                + "assert(call.arguments.count==4 and call.mode=='before'); "
                + "assert(call.arguments:get(1):decimal()=='9223372036854775807'); "
                + "assert(sharedValue==31 and bm.native==betamoon.native); " + "local normalT=bm.native:type('"
                + target.getName() + "'); " + "assert(T==normalT and normalT==T and M.lookupType==T); "
                + "assert(T.kind=='type' and T.dimensions==0 and M.kind=='METHOD'); "
                + "assert(#M.parameterTypes==4 and M.parameterTypes[2].name=='long' and M.returnType.name=='int'); "
                + "assert(M==normalT:method('calculate','(IJDLjava/lang/Object;)I')); "
                + "call.arguments:set(0,sharedValue); call.arguments:set(1,bm.native:long('2')); "
                + "call.arguments:set(3,bm.native.NULL); "
                + "assert(not pcall(function() call.native:direct():call(T,'voidValue',nil) end)); "
                + "assert(call.native:bind(T)~=nil); " + "assert(call.native:bind(M)~=nil);";
        HookDispatcher.Subscription first = subscribe(preparation, target, globals, bindings, "lua_entry", "calculate",
                "(IJDLjava/lang/Object;)I", "before", callback, true);
        globals.set("sharedValue", 31);
        require(method.invoke(receiver, 1, Long.MAX_VALUE, 3.0, new Object()).equals(33), "Lua argument edits");
        globals.load("assert(copied==1); assert(not pcall(function() return saved.arguments:get(0) end)); "
                + "assert(not pcall(function() return saved.native end))").call();
        first.close();
        HookDispatcher.Subscription after = subscribe(preparation, target, globals, bindings, "lua_after", "calculate",
                "(IJDLjava/lang/Object;)I", "after",
                "assert(call.result==3); call:setResult(42); assert(call.result==42)", true);
        require(method.invoke(receiver, 1, 2L, 3.0, null).equals(42), "Lua result transform");
        after.close();
        HookDispatcher.Subscription invalid = subscribe(preparation, target, globals, bindings, "lua_invalid",
                "calculate", "(IJDLjava/lang/Object;)I", "before",
                "call.arguments:set(0,55); call.arguments:set(1,0.5)", true);
        int bodies = target.getField("bodyCalls").getInt(null);
        expectFailure(method, receiver);
        require(target.getField("bodyCalls").getInt(null) == bodies, "invalid Lua edits do not execute body");
        invalid.close();
        HookDispatcher.Subscription denied = subscribe(preparation, target, globals, bindings, "lua_denied",
                "calculate", "(IJDLjava/lang/Object;)I", "before", "call.arguments:set(0,55)", false);
        expectFailure(method, receiver);
        denied.close();
        HookDispatcher.Subscription cancel = subscribe(preparation, target, globals, bindings, "lua_cancel",
                "voidValue", "()V", "before", "call:cancel()", true);
        bodies = target.getField("bodyCalls").getInt(null);
        target.getMethod("voidValue").invoke(null);
        require(target.getField("bodyCalls").getInt(null) == bodies, "Lua void cancellation skips native body");
        cancel.close();
        HookDispatcher.Subscription wide = subscribe(preparation, target, globals, bindings, "lua_long", "longValue",
                "(J)J", "before", "call:returnValue(bm.native:long('-9223372036854775808'))", true);
        require(target.getMethod("longValue", long.class).invoke(null, 1L).equals(Long.MIN_VALUE),
                "exact long early return");
        wide.close();
        HostileFailure.calls = 0;
        HostileFailure originalFailure = new HostileFailure("hostile native body", new IllegalStateException("inner"));
        target.getField("injectedFailure").set(null, originalFailure);
        HookDispatcher.Subscription causeObserver = subscribe(preparation, target, globals, bindings, "lua_causes",
                "calculate", "(IJDLjava/lang/Object;)I", "error",
                "capturedNativeError=call.exception; assert(call.exception.message=='hostile native body'); "
                        + "assert(#call.exception.nativeCauses.chain==2); "
                        + "assert(call.exception.nativeCauses.chain[2].message=='inner'); error('observer failed')",
                false);
        try {
            method.invoke(receiver, -2, 2L, 3.0, null);
            throw new AssertionError("native exception was consumed");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() == originalFailure, "observer failure preserves native exception identity");
        }
        globals.load("assert(capturedNativeError.message=='hostile native body'); "
                + "assert(capturedNativeError.nativeCauses.chain[2].message=='inner')").call();
        require(HostileFailure.calls == 0, "hook diagnostics invoke no foreign exception getters");
        causeObserver.close();
        target.getField("injectedFailure").set(null, null);
        bindings.close();
        loaders.close();
        System.out.println(
                "Native hook Lua integration passed: live owner globals, scoped native access, exact values and typed edits.");
    }

    private static HookDispatcher.Subscription subscribe(HookPreparation preparation, Class<?> target, Globals globals,
            NativeBindings bindings, String id, String method, String descriptor, String at, String body,
            boolean edits) {
        String text = "local bm=betamoon; local T=bm.native:type('" + target.getName() + "'); " + "local M=T:method('"
                + method + "','" + descriptor + "'); local r=bm.hooks:createRegistry(); " + "r:register({id='" + id
                + "',target=M,at='" + at + "',effects='" + (at.equals("error") ? "observe" : "edit")
                + "'},function(call) " + body + " end)";
        HookFileCollectionContext.Registration registered = HookDeclarations
                .evaluate("owner", "hooks/lua.lua", text, 4, globals, new HookDeclarations.Budget()).get(0);
        int site = preparation.targets(registered.definition).get(0).id;
        HookDispatcher.Subscription result = HookDispatcher.subscribe(site, target, registered.definition,
                invocation -> ScriptExecution.withOwner(OWNER, () -> {
                    try (LuaCallbackScope scope = new LuaCallbackScope(false, false);
                            NativeContext context = NativeContext.open(bindings, OWNER, scope, false,
                                    Collections.emptyMap(), () -> true, NativeEntry.Phase.HOOK)) {
                        NativeHookCall call = new NativeHookCall(invocation, context, edits);
                        ScriptExecution.invokeNative(OWNER, "hook:" + id, registered.callback, call);
                    }
                    return null;
                }));
        HookDispatcher.activate(Collections.singletonList(result));
        return result;
    }

    private static void expectFailure(Method method, Object receiver) throws Exception {
        try {
            method.invoke(receiver, 1, 2L, 3.0, null);
            throw new AssertionError("invalid Lua edit succeeded");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() instanceof HookCallbackFailure,
                    "strict Lua edit failure preserves bridge cause");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
