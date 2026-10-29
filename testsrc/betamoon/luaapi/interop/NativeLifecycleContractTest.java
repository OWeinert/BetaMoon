package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collections;

/**
 * Capacity, unchanged-boundary allocation, immediate invalidation and fair FIFO
 * cleanup.
 */
final class NativeLifecycleContractTest {
    private static final int[] ORDER = new int[2048];
    private static int disposed;

    static void run(NativeBindings bindings) {
        disposed = 0;
        for (int owner = 0; owner < 8; owner++) {
            String name = "lifecycle-scale-" + owner;
            int offset = owner * 256;
            ScriptExecution.withOwner(name, () -> {
                try (LuaCallbackScope scope = new LuaCallbackScope(true);
                        NativeContext context = NativeContext.open(bindings, name, scope, true, Collections.emptyMap(),
                                () -> true)) {
                    NativeTypeRef type = bindings.reference(Resource.class);
                    NativeMemberPlan disposer = bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.METHOD, type,
                            type, "dispose", "()V", "runtime", "runtime"), context, true);
                    for (int index = 0; index < 256; index++) {
                        bindings.owned.retain(context, new Resource(offset + index), "owner", disposer);
                    }
                }
                return null;
            });
        }
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        for (int repeat = 0; repeat < 2000; repeat++) {
            observe(bindings);
        }
        long thread = Thread.currentThread().getId();
        long bytes = memory.getThreadAllocatedBytes(thread);
        for (int repeat = 0; repeat < 1000; repeat++) {
            observe(bindings);
        }
        bytes = memory.getThreadAllocatedBytes(thread) - bytes;
        if (bytes != 0) {
            throw new AssertionError("Unchanged lifecycle allocated " + bytes);
        }
        for (int owner = 0; owner < 8; owner++) {
            ScriptResourceTracker.unload("lifecycle-scale-" + owner);
        }
        if (disposed != 0 || ((Number) bindings.owned.diagnostics().get("active")).intValue() != 0) {
            throw new AssertionError("Invalidation did not separate from physical disposal");
        }
        observe(bindings);
        if (disposed < 1 || disposed > 64) {
            throw new AssertionError("Cleanup slice did not bound/progress work");
        }
        for (int boundary = 0; boundary < 2048 && disposed < 2048; boundary++) {
            observe(bindings);
        }
        if (disposed != 2048) {
            throw new AssertionError("Cleanup starved queued entries");
        }
        for (int index = 0; index < ORDER.length; index++) {
            int expected = index / 256 * 256 + 255 - index % 256;
            if (ORDER[index] != expected) {
                throw new AssertionError("Cleanup reversed FIFO/owner disposal order");
            }
        }
        System.out.println(
                "Lifecycle scale passed: 2048 entries, zero unchanged-boundary bytes, immediate invalidation, bounded FIFO disposal.");
    }

    private static void observe(NativeBindings bindings) {
        bindings.clients.observe();
        bindings.owned.observe(null, null);
        bindings.interfaces.observe(null, null);
    }

    public static final class Resource {
        private final int identity;
        Resource(int identity) {
            this.identity = identity;
        }

        public void dispose() {
            ORDER[disposed++] = identity;
        }
    }
}
