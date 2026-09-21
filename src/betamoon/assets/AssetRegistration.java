package betamoon.assets;

/**
 * Immutable snapshot of a published declaration; retaining it does not keep it
 * registered.
 */
public final class AssetRegistration {
    private final AssetDefinition definition;
    private final String owner;
    private final long generation;

    AssetRegistration(AssetDefinition definition, String owner, long generation) {
        this.definition = definition;
        this.owner = owner;
        this.generation = generation;
    }

    public AssetDefinition getDefinition() {
        return definition;
    }

    public String getOwner() {
        return owner;
    }

    /**
     * Process-local revision for stale-work checks, not a persisted or network
     * identity.
     */
    public long getGeneration() {
        return generation;
    }
}
