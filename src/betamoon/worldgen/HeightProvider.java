package betamoon.worldgen;

import java.util.Random;

/** Compiled height sampling strategy. */
public interface HeightProvider {
    int sample(FeatureContext context, int x, int z);

    static HeightProvider uniform(final int min, final int max) {
        return new HeightProvider() {
            @Override
            public int sample(FeatureContext context, int x, int z) {
                return min == max ? min : min + context.random().nextInt(max - min + 1);
            }
        };
    }

    static HeightProvider triangular(final int min, final int max) {
        return new HeightProvider() {
            @Override
            public int sample(FeatureContext context, int x, int z) {
                Random random = context.random();
                int range = max - min + 1;
                return min + (random.nextInt(range) + random.nextInt(range)) / 2;
            }
        };
    }

    static HeightProvider fixed(final int value) {
        return uniform(value, value);
    }

    static HeightProvider surface(final int offset) {
        return surface(TerrainSurface.WORLD_SURFACE, offset);
    }

    static HeightProvider surface(final TerrainSurface surface, final int offset) {
        return new HeightProvider() {
            @Override
            public int sample(FeatureContext context, int x, int z) {
                int height = context.surfaceHeight(x, z, surface);
                return height < 0 ? height : Math.max(0, Math.min(127, height + offset));
            }
        };
    }

    static HeightProvider underground(final int minDepth, final int maxDepth) {
        return new HeightProvider() {
            @Override
            public int sample(FeatureContext context, int x, int z) {
                int surface = context.surfaceHeight(x, z, TerrainSurface.SOLID_SURFACE);
                if (surface < 0) {
                    return -1;
                }
                int depth = minDepth == maxDepth ? minDepth
                        : minDepth + context.random().nextInt(maxDepth - minDepth + 1);
                return Math.max(WorldGenLimits.MIN_HEIGHT, surface - depth);
            }
        };
    }

    static HeightProvider caveFloor(final int min, final int max, final int minimumClearance) {
        return new HeightProvider() {
            @Override
            public int sample(FeatureContext context, int x, int z) {
                return context.caveFloor(x, z, min, max, minimumClearance);
            }
        };
    }

    static HeightProvider ceiling(final int min, final int max) {
        return new HeightProvider() {
            @Override
            public int sample(FeatureContext context, int x, int z) {
                for (int y = max; y >= min; y--) {
                    int block = context.blockId(x, y, z);
                    if (block < 0) {
                        return -1;
                    }
                    if (block == 0 && y < 127 && context.blockId(x, y + 1, z) != 0) {
                        return y;
                    }
                }
                return -1;
            }
        };
    }
}
