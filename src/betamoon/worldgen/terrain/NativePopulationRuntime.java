package betamoon.worldgen.terrain;

/**
 * Scoped category selection. Nested population restores its caller's policy.
 */
public final class NativePopulationRuntime implements AutoCloseable {
    private static final ThreadLocal<PopulationPolicy> CURRENT = new ThreadLocal<PopulationPolicy>();
    private final PopulationPolicy previous;

    private NativePopulationRuntime(PopulationPolicy policy) {
        previous = CURRENT.get();
        CURRENT.set(policy);
    }

    public static NativePopulationRuntime begin(PopulationPolicy policy) {
        return new NativePopulationRuntime(policy);
    }

    public static boolean enabled(String category) {
        PopulationPolicy policy = CURRENT.get();
        return policy == null || policy.nativeCategories.contains(category);
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
