package betamoon.luaapi.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.fixture.NativeFixtureMod;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import com.sun.management.ThreadMXBean;
import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Identical Lua call sites can run against original/candidate runtime artifacts
 * in fresh JVM forks.
 */
public final class NativeInvokerProfile {
    public static void main(String[] args) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(NativeFixtureMod.class.getClassLoader());
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        String[] modes = {"field", "selected", "set", "curated"};
        String[] expressions = {"return o.number", "return selected:call(nil,x)", "field:set(x); return x",
                "return length:call(text)"};
        try {
            ScriptExecution.withOwner("invoker-profile", () -> {
                for (int mode = 0; mode < modes.length; mode++) {
                    NativeFixtureMod fixture = new NativeFixtureMod();
                    try (LuaCallbackScope scope = new LuaCallbackScope(true);
                            NativeContext context = NativeContext.open(bindings, "invoker-profile", scope, true,
                                    Collections.singletonMap("subject", fixture), () -> true)) {
                        NativeAccessApi api = new NativeAccessApi(NativeIndexSnapshot::empty);
                        LuaTable n = new LuaTable();
                        api.attach(n);
                        Globals lua = JsePlatform.standardGlobals();
                        lua.set("n", n);
                        lua.set("a", new NativeLuaAccess(api, context, true));
                        lua.set("fixture", LuaValue.valueOf(NativeFixtureMod.class.getName()));
                        lua.load("local T=n:type(fixture); o=a:subject(); field=o:field('number','I'); "
                                + "selected=assert(a:bind(T:method('staticEcho','(I)I'))); "
                                + "length=assert(a:bind(n:type('java.lang.String'):method('length','()I'))); "
                                + "text=n:value('java.lang.String','changing call input');").call();
                        LuaValue function = lua.load("return function(x) " + expressions[mode] + " end").call();
                        long checksum = 0;
                        for (int iteration = 0; iteration < 12000; iteration++) {
                            fixture.number = iteration & 255;
                            checksum += function.call(LuaValue.valueOf(iteration & 255)).toint();
                        }
                        long[] times = new long[4000];
                        long thread = Thread.currentThread().getId();
                        long bytes = memory.getThreadAllocatedBytes(thread);
                        for (int iteration = 0; iteration < times.length; iteration++) {
                            fixture.number = iteration & 255;
                            LuaValue input = LuaValue.valueOf(iteration & 255);
                            long started = System.nanoTime();
                            checksum += function.call(input).toint();
                            times[iteration] = System.nanoTime() - started;
                        }
                        bytes = memory.getThreadAllocatedBytes(thread) - bytes;
                        Arrays.sort(times);
                        if (checksum <= 0) {
                            throw new AssertionError("invoker result was not consumed");
                        }
                        System.out.println("Native invoker " + modes[mode] + ": p50=" + times[2000] + "ns p95="
                                + times[3800] + "ns p99=" + times[3960] + "ns bytes/call=" + bytes / times.length
                                + "; actual Lua invocation, entry preparation excluded");
                    }
                }
                return null;
            });
        } finally {
            bindings.close();
            loaders.close();
        }
    }
}
