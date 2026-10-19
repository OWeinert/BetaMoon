package betamoon.entity.motion;

/** Thread affinity established by the native client lifecycle, never by Lua access. */
public final class EntitySimulationThread {
    private static volatile Thread thread;

    private EntitySimulationThread() {
    }

    public static synchronized void initialize() {
        if (thread != null && thread != Thread.currentThread()) {
            throw new IllegalStateException("Entity simulation is already owned by another thread");
        }
        thread = Thread.currentThread();
    }

    public static void requireCurrent() {
        Thread expected = thread;
        if (expected == null) {
            throw new IllegalStateException("Entity simulation is not initialized");
        }
        if (expected != Thread.currentThread()) {
            throw new IllegalStateException("Entity access requires the simulation thread");
        }
    }

    public static boolean isCurrent() {
        return thread == Thread.currentThread();
    }
}
