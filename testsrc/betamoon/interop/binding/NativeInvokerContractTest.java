package betamoon.interop.binding;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Adapter preparation cannot initialize classes or weaken reflective admission.
 */
public final class NativeInvokerContractTest {
    public static void run() throws Exception {
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        NativeNames names = new NativeNames(mappings.snapshot(), "named");
        NativeMemberPlan method = new NativeMemberPlan(Cold.class.getMethod("read"), names);
        NativeMemberPlan constructor = new NativeMemberPlan(Cold.class.getConstructor(), names);
        NativeInvoker.call(method);
        NativeInvoker.call(constructor);
        NativeInvoker.get(new NativeMemberPlan(Cold.class.getField("number"), names));
        if (System.getProperty("betamoon.invoker.cold") != null) {
            throw new AssertionError("Preparing an adapter initialized its owner");
        }
        NativeMemberPlan widening = new NativeMemberPlan(Values.class.getMethod("widen", long.class), names);
        NativeMemberPlan broken = new NativeMemberPlan(Broken.class.getMethod("read"), names);
        NativeMemberPlan abstractConstructor = new NativeMemberPlan(Abstract.class.getConstructor(), names);
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        NativeBindings bindings = new NativeBindings(names, loaders);
        ScriptExecution.withOwner("invoker-contract", () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, "invoker-contract", scope, true,
                            Collections.emptyMap(), () -> true)) {
                Object result = widening.call(context, true, null, new Object[]{Integer.valueOf(19)});
                if (!Long.valueOf(19).equals(result)) {
                    throw new AssertionError("Raw Java widening changed");
                }
                expect("native_linkage_failure", () -> broken.callConverted(context, true, null, new Object[0]));
                expect("access_denied", () -> abstractConstructor.callConverted(context, true, null, new Object[0]));
            }
            return null;
        });
        bindings.close();
        loaders.close();
        System.out
                .println("Invoker contracts passed: no preparation initialization, widening and exception admission.");
    }

    private static void expect(String reason, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected " + reason);
        } catch (NativeAccessFailure failure) {
            if (!reason.equals(failure.reason)) {
                throw new AssertionError(failure.reason, failure);
            }
        }
    }

    public static class Cold {
        public static int number;
        static {
            System.setProperty("betamoon.invoker.cold", "initialized");
        }
        public Cold() {
        }

        public static int read() {
            return number;
        }
    }

    public static class Values {
        public static long widen(long value) {
            return value;
        }
    }

    public abstract static class Abstract {
        public Abstract() {
        }
    }

    public static class Broken {
        static {
            if (System.nanoTime() != 0) {
                throw new IllegalStateException("cold initialization");
            }
        }
        public static int read() {
            return 1;
        }
    }
}
