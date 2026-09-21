package betamoon.luamodloader;

/** Optional presentation sink for loader diagnostics; dedicated servers retain logging only. */
public final class LuaLoaderFeedback {
    public interface Sink {
        void reportIssues();

        void reportReloadSummary();
    }

    private static final Sink HEADLESS = new Sink() {
        @Override
        public void reportIssues() {
        }

        @Override
        public void reportReloadSummary() {
        }
    };

    private static volatile Sink sink = HEADLESS;

    private LuaLoaderFeedback() {
    }

    public static void install(Sink next) {
        sink = next == null ? HEADLESS : next;
    }

    static void reportIssues() {
        sink.reportIssues();
    }

    static void reportReloadSummary() {
        sink.reportReloadSummary();
    }
}
