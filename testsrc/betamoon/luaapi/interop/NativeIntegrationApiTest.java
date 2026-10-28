package betamoon.luaapi.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.fixture.NativeFixtureMod;
import betamoon.interop.fixture.NativeInterfaceFixture;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import com.sun.management.ThreadMXBean;
import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Repeated real Lua/native ownership, proxy and read-only consumer integration,
 * with informational costs.
 */
public final class NativeIntegrationApiTest {
    private static final String OWNER = "native_n7.lua";
    private static final String FIXTURE = "betamoon.interop.fixture.NativeInterfaceFixture";

    public static void main(String[] args) throws Exception {
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(NativeIntegrationApiTest.class.getClassLoader());
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        bindings.interfaces.entryFactory(scope -> new NativeEntry(bindings, scope, NativeEntry.Phase.INTERFACE, false,
                Collections.emptyMap(), () -> true));
        Map<String, Object> measurements = new LinkedHashMap<>();
        try {
            NativeLazyFacadeTest.run(bindings);
            cachedCalls(bindings, measurements);
            proxyCalls(bindings, measurements);
            repeatedCleanup(bindings);
        } finally {
            ScriptResourceTracker.unload(OWNER);
            bindings.close();
            loaders.close();
        }
        measurements.put("java", System.getProperty("java.version"));
        measurements.put("cleanupCycles", 100);
        measurements.put("timingPolicy", "informational warmed equivalent inputs; no machine-specific timing gate");
        Files.write(Paths.get("build/native-n7-runtime-profile.json"),
                CatalogJson.encode(measurements).getBytes(StandardCharsets.UTF_8));
        System.out.println("N7 runtime profile: " + CatalogJson.encode(measurements));
        System.out.println(
                "Native integration passed: cached calls, real Lua proxies, 100 reload/cleanup cycles and UI read barriers.");
    }

    private static void cachedCalls(NativeBindings bindings, Map<String, Object> measurements) {
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, scope, true, Collections.emptyMap(),
                            () -> true)) {
                NativeTypeRef type = NativeTypeRef.named(NativeFixtureMod.class.getName(), "runtime", null, null);
                NativeMemberRef method = new NativeMemberRef(NativeMemberRef.Kind.METHOD, type, null, "longEcho",
                        "(J)J", "runtime", "runtime");
                NativeMemberPlan plan = bindings.exact(method, context, true);
                NativeFixtureMod receiver = new NativeFixtureMod();
                long[] checksum = {0};
                measurements.put("directJavaCall", measure(() -> checksum[0] += receiver.longEcho(123L)));
                measurements.put("cachedNativeCall", measure(() -> checksum[0] += (Long) bindings
                        .exact(method, context, true).call(context, true, receiver, new Object[]{123L})));
                require(bindings.exact(method, context, true) == plan && checksum[0] > 0,
                        "exact binding cache changed identity");
            }
            return null;
        });
    }

    private static void proxyCalls(NativeBindings bindings, Map<String, Object> measurements) throws Exception {
        Globals lua = globals();
        invoke(lua, bindings, true, implementation() + "calls=0; proxy=a:implement{interfaces={O},methods={{"
                + "method=O:method('changed','(I)V'),callback=function(ctx,x) calls=calls+x end}}}");
        NativeInterfaceFixture.Observer proxy = ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, scope, true, Collections.emptyMap(),
                            () -> true)) {
                return (NativeInterfaceFixture.Observer) ((NativeLuaImplementation) lua.get("proxy")).value
                        .use(context);
            }
        });
        AtomicInteger directCalls = new AtomicInteger();
        NativeInterfaceFixture.Observer direct = value -> directCalls.addAndGet(value);
        measurements.put("directJavaObserver", measure(() -> direct.changed(1)));
        measurements.put("luaInterfaceCallback", measure(() -> proxy.changed(1)));
        require(lua.get("calls").toint() == directCalls.get(), "proxy profile changed equivalent call count");
        ScriptResourceTracker.unload(OWNER);
        cleared(proxy);
        int before = lua.get("calls").toint();
        try {
            proxy.changed(1);
            throw new AssertionError("closed retained proxy reentered Lua");
        } catch (NativeAccessFailure expected) {
            require(lua.get("calls").toint() == before, "stale proxy executed callback");
        }
        bindings.interfaces.observe(null, null);
        bindings.owned.observe(null, null);
    }

    private static void repeatedCleanup(NativeBindings bindings) throws Exception {
        NativeInterfaceFixture.Consumer host = NativeInterfaceFixture.HOST;
        AtomicInteger unrelated = new AtomicInteger();
        NativeInterfaceFixture.Observer listener = value -> unrelated.incrementAndGet();
        host.add(listener);
        try {
            for (int index = 0; index < 100; index++) {
                Globals lua = globals();
                invoke(lua, bindings, true, implementation()
                        + "calls=0; proxy=a:implement{interfaces={O},methods={{method=O:method('changed','(I)V'),"
                        + "callback=function(ctx,x) calls=calls+1; saved=ctx.native; "
                        + "assert(ctx.native:describe().gameplayWrites==false); "
                        + "assert(not pcall(function() ctx.native:direct():array('int',1) end)) end}}}; "
                        + "local C=n:type('" + FIXTURE + "$Consumer'); local H=n:type('" + FIXTURE + "'); "
                        + "host=assert(a:bind(H)).HOST; attachment=a:attach{implementation=proxy,receiver=host,"
                        + "add=C:method('add','(L" + FIXTURE.replace('.', '/') + "$Observer;)V'),"
                        + "remove=C:method('remove','(L" + FIXTURE.replace('.', '/')
                        + "$Observer;)V'),lifetime='owner'}; "
                        + "owned=a:copy(a:array('int',{1,2,3})); detached={value=23}");
                require(host.size() == 2, "attachment registration leaked from a previous cycle");
                host.emit(index);
                require(lua.get("calls").toint() == 1, "owned callback count changed");
                invoke(lua, bindings, false,
                        "assert(detached.value==23); " + "assert(not pcall(function() saved:client() end)); "
                                + "local a=(...).native; assert(not pcall(function() a:direct():array('int',1) end)); "
                                + "assert(a:describe().gameplayWrites==false)");
                NativeInterfaceFixture.Observer retained = ScriptExecution.withOwner(OWNER, () -> {
                    try (LuaCallbackScope scope = new LuaCallbackScope(true);
                            NativeContext context = NativeContext.open(bindings, OWNER, scope, true,
                                    Collections.emptyMap(), () -> true)) {
                        return (NativeInterfaceFixture.Observer) ((NativeLuaImplementation) lua.get("proxy")).value
                                .use(context);
                    }
                });
                ScriptResourceTracker.unload(OWNER);
                bindings.interfaces.observe(null, null);
                bindings.owned.observe(null, null);
                cleared(retained);
                require(host.size() == 1, "identity cleanup removed an unrelated listener or leaked a proxy");
                require(number(bindings.interfaces.diagnostics(), "implementations") == 0
                        && number(bindings.interfaces.diagnostics(), "attachments") == 0
                        && number(bindings.interfaces.diagnostics(), "pendingRemovals") == 0
                        && number(bindings.owned.diagnostics(), "active") == 0
                        && number(bindings.owned.diagnostics(), "pendingDisposals") == 0,
                        "cleanup counters retained owned native state");
            }
            require(unrelated.get() == 100, "unrelated listener lost notifications");
        } finally {
            host.remove(listener);
        }
    }

    private static String implementation() {
        return "local a=(...).native:direct(); O=n:type('" + FIXTURE + "$Observer'); ";
    }

    private static Globals globals() {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable nativeApi = new LuaTable();
        new NativeAccessApi(NativeIndexSnapshot::empty).attach(nativeApi);
        lua.set("n", nativeApi);
        return lua;
    }

    private static void invoke(Globals lua, NativeBindings bindings, boolean mutable, String text) {
        try (LuaCallbackScope scope = new LuaCallbackScope(mutable)) {
            scope.nativeEntry(new NativeEntry(bindings, scope, NativeEntry.Phase.INTERFACE, mutable,
                    Collections.emptyMap(), () -> true));
            ScriptExecution.invoke(OWNER, "N7 integration", lua.load(text, "N7 integration"),
                    new NativeCallbackTable(scope));
        }
    }

    private static Map<String, Object> measure(Runnable action) {
        for (int index = 0; index < 2000; index++) {
            action.run();
        }
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long[] times = new long[2000];
        long bytes = memory.getThreadAllocatedBytes(thread);
        for (int index = 0; index < times.length; index++) {
            long start = System.nanoTime();
            action.run();
            times[index] = System.nanoTime() - start;
        }
        bytes = memory.getThreadAllocatedBytes(thread) - bytes;
        Arrays.sort(times);
        return CatalogValues.record("p50Nanos", times[1000], "p95Nanos", times[1900], "p99Nanos", times[1980],
                "allocatedBytesPerCall", (double) bytes / times.length, "samples", times.length);
    }

    private static void cleared(Object proxy) throws Exception {
        Object handler = Proxy.getInvocationHandler(proxy);
        Field callbacks = handler.getClass().getDeclaredField("callbacks");
        callbacks.setAccessible(true);
        require(callbacks.get(handler) == null, "closed proxy retained Lua callback references");
    }

    private static int number(Map<String, Object> values, String key) {
        return ((Number) values.get(key)).intValue();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
