package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDefinitionCallbackLayers;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import betamoon.luaapi.utils.LuaOverrideLayers;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.luamodloader.ScriptExecution;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Real dispatcher fixtures, sharing N3's prepared arbitrary-mod binding. */
final class NativeAuthorityTest {
    private NativeAuthorityTest() {
    }

    static void verify(Globals lua, NativeBindings bindings, String owner) {
        ScriptExecution.invokeApi(owner, lua.load("assert(type(C.counter)=='number')", "prepared static field"),
                LuaValue.NONE);
        lua.set("nestedNativeCallback", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
                    scope.nativeEntry(new NativeEntry(bindings, scope, NativeEntry.Phase.GAMEPLAY, true,
                            Collections.emptyMap(), () -> true));
                    return ScriptExecution.invoke(owner, "nested authorized callback", args.arg1(),
                            new NativeCallbackTable(scope));
                }
            }
        });
        ScriptExecution.invoke(owner, "unintegrated callback",
                lua.load(
                        "assert(n:type('int').name=='int'); " + "assert(not pcall(function() return o.number end)); "
                                + "assert(not pcall(function() n:withContext(function() end) end)); "
                                + "nestedNativeCallback(function(ctx) "
                                + "assert(not pcall(function() return ctx.native:bind('int') end)); end)",
                        "native metadata barrier"),
                LuaValue.NONE);

        try (LuaCallbackScope scope = new LuaCallbackScope(false)) {
            scope.nativeEntry(new NativeEntry(bindings, scope, NativeEntry.Phase.QUERY, false, Collections.emptyMap(),
                    () -> true));
            NativeCallbackTable context = new NativeCallbackTable(scope);
            context.set("native", "previous field");
            ScriptExecution.invoke(owner, "audited query",
                    lua.load("local ctx=...; assert(o.number==4); " + "assert(not pcall(function() o.number=9 end)); "
                            + "assert(not pcall(function() o:which(1) end)); "
                            + "assert(not pcall(function() C:new() end)); "
                            + "assert(not pcall(function() return C.counter end)); "
                            + "assert(not pcall(function() a:array('int',2) end)); "
                            + "assert(not pcall(function() T:method('which','(I)Ljava/lang/String;'):call(o,1) end)); "
                            + "n:withContext(function(access) "
                            + "assert(not pcall(function() access:direct():call(T,'which',o,1) end)); end); "
                            + "nestedNativeCallback(function(inner) "
                            + "assert(o.number==4); assert(not pcall(function() o.number=8 end)); "
                            + "assert(not pcall(function() inner.native:direct():call(T,'which',o,1) end)); end); "
                            + "savedQueryAccess=ctx.native", "native query intersection"),
                    context);
            if (!context.get("native").tojstring().equals("previous field")) {
                throw new AssertionError("Dispatcher did not restore the prior context field");
            }
        }
        ScriptExecution.invokeApi(owner,
                lua.load(
                        "local ok,e=pcall(function() savedQueryAccess:world() end); "
                                + "assert(not ok and tostring(e):find('expired_scope')); assert(o.number==4)",
                        "native invocation expiry"),
                LuaValue.NONE);
        lua.set("otherOwnerCallback", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return ScriptExecution.invoke("other_native_owner.lua", "override layer",
                        lua.load(
                                "local ctx=...; assert(ctx.native~=outerNative); "
                                        + "assert(not pcall(function() outerNative:world() end)); "
                                        + "assert(ctx.native:bind('int')); savedOtherNative=ctx.native",
                                "other owner layer"),
                        args);
            }
        });
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            scope.nativeEntry(new NativeEntry(bindings, scope, NativeEntry.Phase.GAMEPLAY, true, Collections.emptyMap(),
                    () -> true));
            ScriptExecution.invoke(owner, "outer override layer",
                    lua.load(
                            "local ctx=...; outerNative=ctx.native; otherOwnerCallback(ctx); "
                                    + "assert(ctx.native==outerNative and ctx.native:bind('int')); "
                                    + "local ok,e=pcall(function() savedOtherNative:world() end); "
                                    + "assert(not ok and tostring(e):find('expired_scope'))",
                            "owner stack restoration"),
                    new NativeCallbackTable(scope));
            NativeCallbackTable broken = new NativeCallbackTable(scope) {
                @Override
                public void rawset(LuaValue key, LuaValue value) {
                    throw new LuaError("fixture context installation failure");
                }
            };
            try {
                ScriptExecution.invoke(owner, "failed context installation", lua.load("error('not reached')"), broken);
                throw new AssertionError("Expected context installation failure");
            } catch (LuaError expected) {
                if (!expected.getMessage().contains("fixture context installation failure")) {
                    throw expected;
                }
            }
        }
        ScriptExecution.invokeApi(owner, lua.load("assert(o.number==4)", "entry failure restoration"), LuaValue.NONE);
        ScriptExecution.invokeApi(owner,
                lua.load("local function recurse() n:withContext(function() recurse() end) end; "
                        + "local ok,e=pcall(recurse); assert(not ok and tostring(e):find('native_depth_limit')); "
                        + "assert(o.number==4)", "native nesting limit"),
                LuaValue.NONE);
        verifyPostCall(lua, bindings, owner);
        verifyBaseContinuation(lua, bindings);
        lua.set("nestedNativeCallback", LuaValue.NIL);
        lua.set("otherOwnerCallback", LuaValue.NIL);
    }

    private static void verifyPostCall(Globals lua, NativeBindings bindings, String owner) {
        AtomicBoolean valid = new AtomicBoolean(true);
        ArrayList<Object> custom = new ArrayList<Object>() {
            @Override
            public int size() {
                valid.set(false);
                return 0;
            }
        };
        try (LuaCallbackScope callback = new LuaCallbackScope(true);
                NativeContext context = NativeContext.open(bindings, owner, callback, true,
                        Collections.singletonMap("subject", custom), valid::get)) {
            lua.set("postCallAccess", NativeAccessApi.contextAccess(context));
            ScriptExecution.invokeApi(owner,
                    lua.load(
                            "local ok,e=pcall(function() return postCallAccess:direct():subject():length() end); "
                                    + "assert(not ok and tostring(e):find('stale_environment'))",
                            "custom collection post-call validation"),
                    LuaValue.NONE);
        }
    }

    private static void verifyBaseContinuation(Globals lua, NativeBindings bindings) {
        String originalOwner = "native_original_fixture.lua";
        String overrideOwner = "native_override_fixture.lua";
        lua.set("currentNativeFixtureOwner", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                return LuaValue.valueOf(LuaScriptRegistry.getCurrentScriptFile());
            }
        });
        LuaValue original = lua.load(
                "local ctx=...; assert(currentNativeFixtureOwner()=='native_original_fixture.lua'); "
                        + "assert(ctx.native~=overrideNative and ctx.native:bind('int')); baseNativeSeen=true; return 'base'",
                "native original callback");
        AtomicReference<LuaValue> effective = new AtomicReference<>(original);
        OverrideManager.Property<Object, LuaOverrideLayers<LuaOverrideDefinition>> property = LuaDefinitionCallbackLayers
                .property("onTick", original, (target, callback) -> effective.set(callback));
        LuaOverrideDefinition definition = ScriptExecution.withOwner(overrideOwner,
                () -> new LuaOverrideDefinition("onTick", lua.load("local ctx=...; overrideNative=ctx.native; "
                        + "assert(currentNativeFixtureOwner()=='native_override_fixture.lua'); "
                        + "local result=ctx:base(); assert(ctx.native==overrideNative and ctx.native:bind('int')); "
                        + "overrideNativeCompleted=true; return result", "native override callback")));
        OverrideManager.Layer<Object, LuaOverrideLayers<LuaOverrideDefinition>> layer = ScriptExecution
                .withOwner(overrideOwner, () -> OverrideManager.apply("fixture:native-base", new Object(), property,
                        LuaOverrideLayers.single(definition), 0));
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            scope.nativeEntry(new NativeEntry(bindings, scope, NativeEntry.Phase.GAMEPLAY, true, Collections.emptyMap(),
                    () -> true));
            LuaValue result = ScriptExecution
                    .invoke(originalOwner, "layered callback", effective.get(), new NativeCallbackTable(scope)).arg1();
            if (!result.tojstring().equals("base") || !lua.get("baseNativeSeen").toboolean()
                    || !lua.get("overrideNativeCompleted").toboolean()) {
                throw new AssertionError("Base continuation lost its native owner context or disabled the override");
            }
        } finally {
            layer.remove();
            ScriptResourceTracker.unload(overrideOwner);
            lua.set("currentNativeFixtureOwner", LuaValue.NIL);
        }
    }
}
