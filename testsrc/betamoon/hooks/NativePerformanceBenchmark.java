package betamoon.hooks;

/**
 * Standalone fork entry; the identical fixture can run against original and
 * candidate runtime artifacts.
 */
public final class NativePerformanceBenchmark {
    public static void main(String[] args) throws Exception {
        HookScaleProfile.verify();
    }
}
