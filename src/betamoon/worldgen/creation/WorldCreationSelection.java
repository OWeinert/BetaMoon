package betamoon.worldgen.creation;

/**
 * One synchronous startWorld call owns a selection, cleared even when loading
 * fails.
 */
public final class WorldCreationSelection implements AutoCloseable {
    private static final ThreadLocal<WorldCreationSelection> CURRENT = new ThreadLocal<WorldCreationSelection>();
    private GenerationSelection pending;

    private WorldCreationSelection(GenerationSelection selection) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested world creation is not supported");
        }
        pending = selection;
        CURRENT.set(this);
    }

    public static WorldCreationSelection begin(GenerationSelection selection) {
        return new WorldCreationSelection(selection);
    }

    public static GenerationSelection consume() {
        WorldCreationSelection current = CURRENT.get();
        if (current == null) {
            return null;
        }
        GenerationSelection selection = current.pending;
        current.pending = null;
        return selection;
    }

    @Override
    public void close() {
        pending = null;
        CURRENT.remove();
    }
}
