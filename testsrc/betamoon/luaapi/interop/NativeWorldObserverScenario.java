package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;

/**
 * Actual World listener registration/iteration/removal in both isolated
 * artifact namespaces.
 */
final class NativeWorldObserverScenario {
    private static final String OWNER = "native_world_observer_fixture.lua";

    static void verify(Globals lua, NativeBindings bindings, Class<?> worldType, Object world) throws Exception {
        Class<?> observerType = bindings.names.curatedType("net/minecraft/src/IWorldAccess");
        AtomicReference<Map<String, Object>> roots = new AtomicReference<>(Collections.singletonMap("world", world));
        bindings.interfaces.entryFactory(scope -> {
            Map<String, Object> current = roots.get();
            return new NativeEntry(bindings, scope, NativeEntry.Phase.INTERFACE, false, current,
                    () -> roots.get() == current);
        });
        List<Object> listeners = new ArrayList<>();
        AtomicInteger unrelated = new AtomicInteger();
        Object other = Proxy.newProxyInstance(observerType.getClassLoader(), new Class<?>[]{observerType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.getName().equals("equals")
                                ? proxy == args[0]
                                : method.getName().equals("hashCode")
                                        ? System.identityHashCode(proxy)
                                        : "fixture listener";
                    }
                    unrelated.incrementAndGet();
                    return null;
                });
        listeners.add(other);
        Method notify = ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, scope, true, roots.get(), () -> true)) {
                NativeTypeRef type = bindings.reference(worldType);
                Field field = (Field) bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.FIELD, type, type,
                        "worldAccesses", "Ljava/util/List;", "mapped", "mapped"), context, true).member;
                field.setAccessible(true);
                try {
                    field.set(world, listeners);
                } catch (IllegalAccessException failure) {
                    throw new AssertionError(failure);
                }
                NativeMemberPlan method = bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.METHOD, type, type,
                        "markBlockNeedsUpdate", "(III)V", "mapped", "mapped"), context, false);
                return (Method) method.member;
            }
        });
        invoke(lua, bindings, roots.get(), "local a=observerAccess; "
                + "WI=betamoon.native:type('net.minecraft.src.IWorldAccess'); "
                + "local ok,e=pcall(function() a:observeWorld{methods={{method=WI:method('markBlockAndNeighborsNeedsUpdate','(III)V'),"
                + "policy='gameplay',callback=function() end}}} end); assert(not ok and tostring(e):find('invalid_observer_contract')); "
                + "one=a:observeWorld{methods={{method=WI:method('markBlockAndNeighborsNeedsUpdate','(III)V'),"
                + "callback=function(ctx,x,y,z) firstCount=(firstCount or 0)+1; savedWorldCall=ctx; "
                + "assert(ctx.native:describe().phase=='interface'); "
                + "assert(not pcall(function() ctx.native:direct():array('int',1) end)); "
                + "if closeOne then one:close() end end}}}; "
                + "two=a:observeWorld{methods={{method=WI:method('markBlockAndNeighborsNeedsUpdate','(III)V'),"
                + "callback=function(ctx,x,y,z) secondCount=(secondCount or 0)+1 end}}}");
        require(listeners.size() == 3, "World observers were not installed");
        notify.invoke(world, 1, 2, 3);
        require(lua.get("firstCount").toint() == 1 && lua.get("secondCount").toint() == 1,
                "Actual native World notification did not dispatch mapped methods");
        lua.set("closeOne", LuaValue.TRUE);
        notify.invoke(world, 1, 2, 3);
        require(listeners.size() == 3 && lua.get("secondCount").toint() == 2,
                "Removal during World iteration skipped another listener");
        notify.invoke(world, 1, 2, 3);
        require(lua.get("firstCount").toint() == 2 && lua.get("secondCount").toint() == 3,
                "Closed world observer still entered Lua");
        bindings.interfaces.observe(world, null);
        require(listeners.size() == 2 && listeners.get(0) == other, "Deferred detach changed unrelated listeners");
        invoke(lua, bindings, roots.get(), "assert(one:describe().state=='closed'); "
                + "assert(not pcall(function() savedWorldCall.native:world() end)); "
                + "fault=observerAccess:observeWorld{methods={{method=WI:method('markBlockAndNeighborsNeedsUpdate','(III)V'),"
                + "callback=function() error('fixture world observer fault') end}}}");
        notify.invoke(world, 1, 2, 3);
        require(lua.get("secondCount").toint() == 4, "Observer error corrupted native dispatch");
        bindings.interfaces.observe(world, null);
        require(listeners.size() == 2 && listeners.get(0) == other, "Faulted observer was not detached by identity");
        // Replacement is checked at fresh callback entry even before the next lifecycle
        // poll.
        roots.set(Collections.emptyMap());
        notify.invoke(world, 1, 2, 3);
        require(lua.get("secondCount").toint() == 4, "Old world observer ran against replacement roots");
        bindings.owned.observe(null, null);
        bindings.interfaces.observe(null, null);
        require(listeners.size() == 1 && listeners.get(0) == other && unrelated.get() == 5,
                "World replacement retained an owned observer or removed another listener");
        roots.set(Collections.singletonMap("world", world));
        invoke(lua, bindings, roots.get(), "reloadWorld=observerAccess:observeWorld{methods={}}");
        require(listeners.size() == 2, "Empty audited observer defaults were not generated");
        ScriptResourceTracker.unload(OWNER);
        notify.invoke(world, 1, 2, 3);
        bindings.owned.observe(world, null);
        bindings.interfaces.observe(world, null);
        require(listeners.size() == 1 && listeners.get(0) == other, "Owner unload retained a world observer");
    }

    private static void invoke(Globals lua, NativeBindings bindings, Map<String, Object> roots, String code) {
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, scope, true, roots, () -> true)) {
                lua.set("observerAccess", NativeAccessApi.contextAccess(context));
                ScriptExecution.invokeApi(OWNER, lua.load(code, "native world observer fixture"), LuaValue.NONE);
            }
            return null;
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
