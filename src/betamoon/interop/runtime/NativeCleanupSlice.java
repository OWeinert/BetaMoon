package betamoon.interop.runtime;

/**
 * FIFO physical cleanup budget. Invalidation and urgent identity detaches never
 * use this budget.
 */
public final class NativeCleanupSlice {
    private static final int MAX_ITEMS = 64;
    private static final long MAX_NANOS = 1000000L;
    private final boolean shutdown;
    private final long started = System.nanoTime();
    private int count;

    public NativeCleanupSlice(boolean shutdown) {
        this.shutdown = shutdown;
    }

    public boolean next() {
        // Guarantee progress even when the first native disposer exceeds the entire
        // slice.
        if (shutdown || count == 0 || count < MAX_ITEMS && System.nanoTime() - started < MAX_NANOS) {
            count++;
            return true;
        }
        return false;
    }
}
