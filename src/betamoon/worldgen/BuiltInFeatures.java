package betamoon.worldgen;

import java.util.List;
import java.util.Random;
import net.minecraft.src.MathHelper;

/** Compiled implementations for the initial declarative feature types. */
public final class BuiltInFeatures {
    private BuiltInFeatures() {
    }

    public static WorldFeature oreVein(final int blockId, final int metadata, final int size,
            final BlockSet replace) {
        return new WorldFeature() {
            @Override
            public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
                Random random = context.random();
                float angle = random.nextFloat() * (float) Math.PI;
                double x1 = origin.x + 8 + MathHelper.sin(angle) * size / 8.0F;
                double x2 = origin.x + 8 - MathHelper.sin(angle) * size / 8.0F;
                double z1 = origin.z + 8 + MathHelper.cos(angle) * size / 8.0F;
                double z2 = origin.z + 8 - MathHelper.cos(angle) * size / 8.0F;
                double y1 = origin.y + random.nextInt(3) + 2;
                double y2 = origin.y + random.nextInt(3) + 2;
                for (int index = 0; index <= size; index++) {
                    double xPosition = x1 + (x2 - x1) * index / size;
                    double yPosition = y1 + (y2 - y1) * index / size;
                    double zPosition = z1 + (z2 - z1) * index / size;
                    double randomSize = random.nextDouble() * size / 16.0D;
                    double horizontal = (MathHelper.sin((float) index * (float) Math.PI / size) + 1.0F)
                            * randomSize + 1.0D;
                    double vertical = (MathHelper.sin((float) index * (float) Math.PI / size) + 1.0F)
                            * randomSize + 1.0D;
                    int minX = MathHelper.floor_double(xPosition - horizontal / 2.0D);
                    int minY = Math.max(0, MathHelper.floor_double(yPosition - vertical / 2.0D));
                    int minZ = MathHelper.floor_double(zPosition - horizontal / 2.0D);
                    int maxX = MathHelper.floor_double(xPosition + horizontal / 2.0D);
                    int maxY = Math.min(127, MathHelper.floor_double(yPosition + vertical / 2.0D));
                    int maxZ = MathHelper.floor_double(zPosition + horizontal / 2.0D);
                    for (int x = minX; x <= maxX; x++) {
                        double dx = (x + 0.5D - xPosition) / (horizontal / 2.0D);
                        for (int y = minY; dx * dx < 1.0D && y <= maxY; y++) {
                            double dy = (y + 0.5D - yPosition) / (vertical / 2.0D);
                            for (int z = minZ; dx * dx + dy * dy < 1.0D && z <= maxZ; z++) {
                                double dz = (z + 0.5D - zPosition) / (horizontal / 2.0D);
                                if (dx * dx + dy * dy + dz * dz < 1.0D) {
                                    int existing = context.blockId(x, y, z);
                                    if (existing < 0) {
                                        return FeatureResult.rejected(context.failure());
                                    }
                                    if (replace.contains(existing)) {
                                        output.setBlock(x, y, z, blockId, metadata);
                                    }
                                }
                            }
                        }
                    }
                }
                return planned(output);
            }
        };
    }

    public static WorldFeature patch(final int blockId, final int metadata, final int radius, final int tries,
            final BlockSet replace) {
        return new WorldFeature() {
            @Override
            public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
                Random random = context.random();
                for (int attempt = 0; attempt < tries; attempt++) {
                    int x = origin.x + random.nextInt(radius * 2 + 1) - radius;
                    int y = origin.y + random.nextInt(3) - 1;
                    int z = origin.z + random.nextInt(radius * 2 + 1) - radius;
                    int existing = context.blockId(x, y, z);
                    if (existing < 0) {
                        return FeatureResult.rejected(context.failure());
                    }
                    if (replace.contains(existing)) {
                        output.setBlock(x, y, z, blockId, metadata);
                    }
                }
                return planned(output);
            }
        };
    }

    public static WorldFeature column(final int blockId, final int metadata, final IntRange height,
            final boolean downward, final BlockSet replace) {
        return new WorldFeature() {
            @Override
            public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
                int length = height.sample(context.random());
                for (int offset = 0; offset < length; offset++) {
                    int y = origin.y + (downward ? -offset : offset);
                    int existing = context.blockId(origin.x, y, origin.z);
                    if (existing < 0) {
                        return FeatureResult.rejected(context.failure());
                    }
                    if (!replace.contains(existing)) {
                        return offset == 0 ? FeatureResult.rejected(FeatureResult.BLOCKED) : planned(output);
                    }
                    output.setBlock(origin.x, y, origin.z, blockId, metadata);
                }
                return planned(output);
            }
        };
    }

    public static WorldFeature disk(final int blockId, final int metadata, final IntRange radius,
            final int halfHeight, final BlockSet replace) {
        return new WorldFeature() {
            @Override
            public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
                int sampledRadius = radius.sample(context.random());
                int radiusSquared = sampledRadius * sampledRadius;
                for (int x = origin.x - sampledRadius; x <= origin.x + sampledRadius; x++) {
                    for (int z = origin.z - sampledRadius; z <= origin.z + sampledRadius; z++) {
                        int dx = x - origin.x;
                        int dz = z - origin.z;
                        if (dx * dx + dz * dz > radiusSquared) {
                            continue;
                        }
                        for (int y = origin.y - halfHeight; y <= origin.y + halfHeight; y++) {
                            int existing = context.blockId(x, y, z);
                            if (existing < 0) {
                                return FeatureResult.rejected(context.failure());
                            }
                            if (replace.contains(existing)) {
                                output.setBlock(x, y, z, blockId, metadata);
                            }
                        }
                    }
                }
                return planned(output);
            }
        };
    }

    public static WorldFeature weighted(final List<WorldGenKey> features, final int[] cumulativeWeights) {
        return new WorldFeature() {
            @Override
            public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
                int choice = context.random().nextInt(cumulativeWeights[cumulativeWeights.length - 1]);
                for (int index = 0; index < cumulativeWeights.length; index++) {
                    if (choice < cumulativeWeights[index]) {
                        return context.plan(features.get(index), origin, output);
                    }
                }
                return FeatureResult.rejected(FeatureResult.RUNTIME_ERROR);
            }
        };
    }

    public static WorldFeature sequence(final List<WorldGenKey> features) {
        return new WorldFeature() {
            @Override
            public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
                for (WorldGenKey feature : features) {
                    FeatureResult result = context.plan(feature, origin, output);
                    if (!result.placed && !FeatureResult.NO_CHANGES.equals(result.reason)) {
                        return result;
                    }
                }
                return planned(output);
            }
        };
    }

    public static WorldFeature noOp() {
        return new WorldFeature() {
            @Override
            public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
                return FeatureResult.rejected(FeatureResult.NO_CHANGES);
            }
        };
    }

    private static FeatureResult planned(PlacementPlan output) {
        if (output.failure() != null) {
            return FeatureResult.rejected(output.failure());
        }
        return output.size() == 0 ? FeatureResult.rejected(FeatureResult.NO_CHANGES)
                : FeatureResult.placed(output.size(), null, null);
    }
}
