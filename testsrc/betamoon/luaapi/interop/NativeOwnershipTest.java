package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.fixture.NativeFixtureOwned;
import betamoon.interop.runtime.NativeEntry;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeOwnedValue;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Ownership survives callbacks, never the declared owner/world lifetime. */
final class NativeOwnershipTest {
    private static final String OWNER = "native_ownership_fixture.lua";

    private NativeOwnershipTest() {
    }

    static void verify(Globals lua, NativeBindings bindings) {
        lua.set("ownedFixtureType", NativeFixtureOwned.class.getName());
        invoke(lua, bindings, OWNER, true, Collections.emptyMap(),
                "local a=(...).native:direct(); R=n:type(ownedFixtureType); "
                        + "local obj=assert(a:bind(R)):new(); obj.number=7; "
                        + "h=a:retain(obj,{lifetime='owner',dispose=R:method('close','()V')}); "
                        + "assert(h:describe().state=='active'); oldOwnedView=a:use(h); "
                        + "assert(not pcall(function() a:retain(obj,{lifetime='owner'}) end)); "
                        + "assert(not pcall(function() a:retain(obj,{}) end)); "
                        + "local ar=a:array('int',{1,2}); copied=a:copy(ar); "
                        + "a:use(copied)[0]=9; assert(ar[0]==1); "
                        + "local own=a:retain(ar,{lifetime='owner'}); local view=a:use(own); a:release(own); "
                        + "assert(not pcall(function() return ar[0] end)); "
                        + "assert(not pcall(function() return view[0] end)); "
                        + "local original=assert(a:bind(R)):new(); local oldField=original:field('child'); "
                        + "local bag=a:array('java.lang.Object',{original}); "
                        + "local bagHandle=a:retain(bag,{lifetime='owner'}); "
                        + "local parent=a:retain(original,{lifetime='owner'}); "
                        + "local child=original:field('child'):get(); local early=oldField:get(); "
                        + "local named=a:call(R,'getChild',original); local method=original:getChild(); "
                        + "local contained=bag:getAt(0):field('child'):get(); a:release(parent); "
                        + "assert(not pcall(function() return original.number end)); "
                        + "assert(not pcall(function() return child.number end)); "
                        + "assert(not pcall(function() return early.number end)); "
                        + "assert(not pcall(function() return named.number end)); "
                        + "assert(not pcall(function() return method.number end)); "
                        + "assert(not pcall(function() return contained.number end)); a:release(bagHandle)");
        invoke(lua, bindings, OWNER, false, Collections.emptyMap(),
                "local a=(...).native; assert(a:use(h).number==7); "
                        + "assert(not pcall(function() return oldOwnedView.number end)); "
                        + "assert(not pcall(function() a:direct():release(h) end)); "
                        + "assert(not pcall(function() a:direct():retain(a:use(h),{lifetime='owner'}) end)); "
                        + "assert(not pcall(function() a:copy(a:use(copied)) end))");
        invoke(lua, bindings, "another_native_owner.lua", true, Collections.emptyMap(),
                "local a=(...).native; local ok,e=pcall(function() a:use(h) end); "
                        + "assert(not ok and tostring(e):find('wrong_owner'))");
        invoke(lua, bindings, OWNER, true, Collections.emptyMap(),
                "local a=(...).native; assert(not pcall(function() a:release(h) end)); "
                        + "local d=a:direct(); local v=d:use(h); local fieldChild=v:field('child'):get(); "
                        + "local nameChild=d:call(R,'getChild',v); "
                        + "local exactChild=d:bind(R:method('getChild','()Lbetamoon/interop/fixture/NativeFixtureOwned$Node;')):call(v); "
                        + "d:release(h); d:release(h); " + "assert(not pcall(function() return v.number end)); "
                        + "assert(not pcall(function() return fieldChild.number end)); "
                        + "assert(not pcall(function() return nameChild.number end)); "
                        + "assert(not pcall(function() return exactChild.number end)); "
                        + "assert(h:describe().state=='released'); "
                        + "assert(not pcall(function() a:use(h) end)); a:direct():release(copied)");
        require(NativeFixtureOwned.closed == 1, "Explicit release was not exactly once");

        Object world = new Object();
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("world", world);
        roots.put("player", new Object());
        invoke(lua, bindings, OWNER, true, roots,
                "local a=(...).native:direct(); "
                        + "local ok,e=pcall(function() a:retain(a:world(),{lifetime='owner'}) end); "
                        + "assert(not ok and tostring(e):find('borrowed_root')); "
                        + "wh=a:retain(assert(a:bind(R)):new(),{lifetime='world',dispose=R:method('close','()V')})");
        lua.set("observeReplacement", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                bindings.owned.observe(null, null);
                return LuaValue.NONE;
            }
        });
        invoke(lua, bindings, OWNER, false, roots,
                "local a=(...).native; observeReplacement(); assert(wh:describe().state=='stale_world'); "
                        + "assert(not pcall(function() a:use(wh) end))");
        require(NativeFixtureOwned.closed == 1, "Query executed a native disposer");
        bindings.owned.observe(null, null);
        require(NativeFixtureOwned.closed == 2, "Lifecycle did not drain deferred disposal");
        lua.set("observeReplacement", LuaValue.NIL);

        invoke(lua, bindings, OWNER, true, Collections.emptyMap(), "local a=(...).native:direct(); "
                + "good=a:retain(assert(a:bind(R)):new(),{lifetime='owner',dispose=R:method('close','()V')}); "
                + "bad=a:retain(assert(a:bind(R)):new(),{lifetime='owner',dispose=R:method('failClose','()V')})");
        ScriptResourceTracker.unload(OWNER);
        require(NativeFixtureOwned.closed == 3 && NativeFixtureOwned.failed == 1,
                "Owner cleanup did not continue after failed disposal");
        require(((Number) bindings.owned.diagnostics().get("active")).intValue() == 0,
                "Owner cleanup retained native payloads");
        invoke(lua, bindings, OWNER, true, Collections.emptyMap(),
                "assert(good:describe().state=='stale_owner'); "
                        + "local a=(...).native; local ok,e=pcall(function() a:use(good) end); "
                        + "assert(not ok and tostring(e):find('stale_owner'))");
        verifyWorkerUnload(lua, bindings);
        verifySessionClose(bindings);
    }

    private static void verifyWorkerUnload(Globals lua, NativeBindings bindings) {
        invoke(lua, bindings, OWNER, true, Collections.emptyMap(), "local a=(...).native:direct(); "
                + "workerOwned=a:retain(assert(a:bind(R)):new(),{lifetime='owner',dispose=R:method('close','()V')})");
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                ScriptResourceTracker.unload(OWNER);
            } catch (Throwable failure) {
                failed.set(failure);
            }
        }, "native owner unload fixture");
        worker.start();
        try {
            worker.join(5000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        require(!worker.isAlive() && failed.get() == null, "Worker unload failed: " + failed.get());
        require(NativeFixtureOwned.closed == 3, "Worker executed a native disposer");
        require(((Number) bindings.owned.diagnostics().get("active")).intValue() == 1,
                "Worker mutated the client-owned registry");
        invoke(lua, bindings, OWNER, false, Collections.emptyMap(),
                "assert(workerOwned:describe().state=='stale_owner'); "
                        + "local a=(...).native; local ok,e=pcall(function() a:use(workerOwned) end); "
                        + "assert(not ok and tostring(e):find('stale_owner'))");
        require(NativeFixtureOwned.closed == 3, "Query executed a worker-deferred disposer");
        bindings.owned.observe(null, null);
        require(NativeFixtureOwned.closed == 4, "Client lifecycle did not finish worker-deferred cleanup");
        require(((Number) bindings.owned.diagnostics().get("active")).intValue() == 0,
                "Client lifecycle retained an unloaded owner's payload");
    }

    private static void verifySessionClose(NativeBindings original) {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        NativeBindings bindings = new NativeBindings(original.names, loaders);
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope callback = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, callback, true, Collections.emptyMap(),
                            () -> true)) {
                NativeOwnedValue value = bindings.owned.retain(context, new int[]{1}, "owner", null);
                AtomicBoolean reading = new AtomicBoolean(true);
                AtomicReference<Throwable> failed = new AtomicReference<>();
                CountDownLatch started = new CountDownLatch(1);
                Thread reader = new Thread(() -> {
                    try {
                        while (reading.get()) {
                            Map<String, Object> status = bindings.diagnostics();
                            require(status.get("ownership") instanceof Map, "Missing detached ownership report");
                            require(status.get("failures") instanceof Map, "Missing detached failure report");
                            started.countDown();
                        }
                    } catch (Throwable failure) {
                        failed.set(failure);
                    }
                }, "native diagnostics fixture");
                reader.start();
                try {
                    try {
                        require(started.await(5, TimeUnit.SECONDS), "Diagnostics worker did not start");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    for (int i = 0; i < 300; i++) {
                        NativeOwnedValue temporary = bindings.owned.retain(context, new int[]{i}, "owner", null);
                        temporary.release(context, true);
                        bindings.recordFailure("fixture_reason_" + i % 8);
                    }
                } finally {
                    reading.set(false);
                    try {
                        reader.join(5000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
                require(!reader.isAlive() && failed.get() == null, "Concurrent diagnostics failed: " + failed.get());
                bindings.close();
                require(value.describe().get("state").equals("session_closed"), "Session retained an owned payload");
                try {
                    Field cleanups = ScriptResourceTracker.class.getDeclaredField("CLEANUPS");
                    cleanups.setAccessible(true);
                    require(!((Map<?, ?>) cleanups.get(null)).containsKey(OWNER),
                            "Closed session retained its owner cleanup/loader lease");
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
                try {
                    context.check();
                    throw new AssertionError("Closed session allowed an existing execution scope");
                } catch (NativeAccessFailure expected) {
                    require(expected.reason.equals("native_session_closed"), "Wrong session-close refusal");
                }
            }
            return null;
        });
        ScriptResourceTracker.unload(OWNER);
        loaders.close();
    }

    private static void invoke(Globals lua, NativeBindings bindings, String owner, boolean mutable,
            Map<String, Object> roots, String code) {
        try (LuaCallbackScope scope = new LuaCallbackScope(mutable)) {
            scope.nativeEntry(new NativeEntry(bindings, scope,
                    mutable ? NativeEntry.Phase.GAMEPLAY : NativeEntry.Phase.QUERY, mutable, roots, () -> true));
            ScriptExecution.invoke(owner, "ownership fixture", lua.load(code, "native ownership fixture"),
                    new NativeCallbackTable(scope));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
