package betamoon.hooks;

/**
 * Standalone fork entry; the identical fixture can run against original and
 * candidate runtime artifacts.
 */
public final class NativePerformanceBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            HookDispatcher.configureTiming(args[0], args.length > 1 ? Integer.parseInt(args[1]) : 64);
            System.out.println("Timing mode=" + args[0]);
        }
        HookScaleProfile.verify();
    }
}
