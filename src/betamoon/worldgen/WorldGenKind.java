package betamoon.worldgen;

/** Public world-generation registry kinds used by typed keys. */
public enum WorldGenKind {
    FEATURE("feature"),
    PLACEMENT("placement"),
    TREE("tree"),
    STRUCTURE("structure"),
    BIOME("biome"),
    BIOME_SOURCE("biome_source"),
    SURFACE("surface"),
    CARVER("carver"),
    TERRAIN("terrain"),
    DIMENSION("dimension");

    private final String path;

    WorldGenKind(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    static WorldGenKind fromPath(String path) {
        for (WorldGenKind kind : values()) {
            if (path.equals(kind.path) || path.startsWith(kind.path + "/")) {
                return kind;
            }
        }
        return null;
    }
}
