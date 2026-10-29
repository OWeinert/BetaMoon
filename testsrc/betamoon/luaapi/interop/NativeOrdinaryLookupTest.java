package betamoon.luaapi.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.binding.NativeOrdinaryMember;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.fixture.NativeFixtureMod;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A prepared absent alternative remains usable in a read-only callback without
 * reflection preparation.
 */
final class NativeOrdinaryLookupTest {
    static void run() throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(NativeFixtureMod.class.getClassLoader());
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        AtomicInteger diagnostics = new AtomicInteger();
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders, () -> {
            diagnostics.incrementAndGet();
            return NativeIndexSnapshot.empty();
        });
        NativeTypeRef type = NativeTypeRef.named(NativeFixtureMod.class.getName(), "runtime", null, null);
        NativeOrdinaryMember[] prepared = new NativeOrdinaryMember[3];
        ScriptExecution.withOwner("lookup-performance", () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, "lookup-performance", scope, true,
                            Collections.emptyMap(), () -> true)) {
                prepared[0] = bindings.ordinary(type, "longEcho", false, context);
                prepared[1] = bindings.ordinary(type, "node", false, context);
                prepared[2] = bindings.ordinary(type, "absentAlternative", false, context);
                if (!prepared[0].method || prepared[0].field != null || prepared[1].field != null || prepared[1].method
                        || prepared[2].field != null || prepared[2].method) {
                    throw new AssertionError("ordinary classification changed visibility/absence");
                }
            }
            try (LuaCallbackScope scope = new LuaCallbackScope(false);
                    NativeContext context = NativeContext.open(bindings, "lookup-performance", scope, false,
                            Collections.emptyMap(), () -> true)) {
                String[] names = {"longEcho", "node", "absentAlternative"};
                for (int repeat = 0; repeat < 1000; repeat++) {
                    for (int index = 0; index < names.length; index++) {
                        if (bindings.ordinary(type, names[index], false, context) != prepared[index]) {
                            throw new AssertionError("prepared classification was rebuilt");
                        }
                    }
                }
                if (diagnostics.get() != 0) {
                    throw new AssertionError("normal alternative lookup created failure diagnostics");
                }
            }
            return null;
        });
        bindings.close();
        loaders.close();
        System.out.println(
                "Ordinary lookup passed: cached negative alternatives, read-only preparation and no failure diagnostics.");
    }
}
