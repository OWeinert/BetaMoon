package betamoon.worldgen.terrain;

import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;

/**
 * World-bound identity. A pending native spawn is never inferred from a later
 * respawn point.
 */
public final class WorldGeneratorManifest {
    public static final int SCHEMA_REVISION = 1;
    public final long worldSeed;
    public final int dimension;
    public final String profileKey;
    public final String signature;
    public final OriginalSpawn originalSpawn;

    public WorldGeneratorManifest(long worldSeed, int dimension, String profileKey, String signature,
            OriginalSpawn originalSpawn) {
        if (dimension != 0 || profileKey == null || profileKey.length() > 256
                || !WorldGenKey.parse(profileKey, WorldGenKind.GENERATOR).toString().equals(profileKey)
                || signature == null || !signature.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid world generator manifest identity");
        }
        this.worldSeed = worldSeed;
        this.dimension = dimension;
        this.profileKey = profileKey;
        this.signature = signature;
        this.originalSpawn = originalSpawn;
    }

    public WorldGeneratorManifest withOriginalSpawn(int x, int y, int z) {
        if (originalSpawn != null) {
            throw new IllegalStateException("Original world spawn is immutable");
        }
        return new WorldGeneratorManifest(worldSeed, dimension, profileKey, signature, new OriginalSpawn(x, y, z));
    }

    public void requireCompatible(long seed, int worldDimension, WorldGeneratorProfile profile) {
        if (worldSeed != seed || dimension != worldDimension) {
            throw new IllegalStateException("Generator manifest belongs to another world seed or dimension");
        }
        if (profile == null) {
            throw new IllegalStateException("Missing saved generator profile '" + profileKey + "'; restore its script");
        }
        if (!profileKey.equals(profile.key) || !signature.equals(profile.signature)) {
            throw new IllegalStateException("Generator profile '" + profileKey
                    + "' changed incompatibly; restore the saved definition before opening this world");
        }
    }

    public static final class OriginalSpawn {
        public final int x;
        public final int y;
        public final int z;

        public OriginalSpawn(int x, int y, int z) {
            if (x < -30000000 || x > 30000000 || z < -30000000 || z > 30000000 || y < 0 || y > 127) {
                throw new IllegalArgumentException("Original world spawn is outside the supported world bounds");
            }
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
