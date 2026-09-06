package betamoon.luamodloader;

/** Stores the latest script-reload result independently of the Scripts screen lifecycle. */
public final class ScriptReloadStatus {
    public enum State {
        IDLE,
        RELOADING,
        SUCCESS,
        FAILED
    }

    private static State state = State.IDLE;
    private static int errorCount;
    private static long completedAt;

    private ScriptReloadStatus() {
    }

    /** Marks a reload as active. */
    public static synchronized void begin() {
        state = State.RELOADING;
        errorCount = 0;
        completedAt = 0L;
    }

    /** Records the final reload result. */
    public static synchronized void complete(int errors) {
        errorCount = Math.max(0, errors);
        state = errorCount == 0 ? State.SUCCESS : State.FAILED;
        completedAt = System.currentTimeMillis();
    }

    public static synchronized State getState() {
        return state;
    }

    public static synchronized int getErrorCount() {
        return errorCount;
    }

    public static synchronized long getCompletedAt() {
        return completedAt;
    }

    /** Hides an expired success indicator without clearing error details. */
    public static synchronized void expireSuccess() {
        if (state == State.SUCCESS) {
            state = State.IDLE;
        }
    }
}
