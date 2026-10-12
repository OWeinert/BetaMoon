package betamoon.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bounded declarative trunk, branch, and canopy compiler. */
public final class TreeFeature implements WorldFeature {
    private final int trunkBlock;
    private final int trunkMetadata;
    private final IntRange height;
    private final int trunkRadius;
    private final double bend;
    private final Branches branches;
    private final int leavesBlock;
    private final int leavesMetadata;
    private final String canopyShape;
    private final IntRange canopyRadius;
    private final double canopyDensity;
    private final BlockSet ground;
    private final BlockSet replace;

    public TreeFeature(int trunkBlock, int trunkMetadata, IntRange height, int trunkRadius, double bend,
            Branches branches, int leavesBlock, int leavesMetadata, String canopyShape, IntRange canopyRadius,
            double canopyDensity, BlockSet ground, BlockSet replace) {
        this.trunkBlock = trunkBlock;
        this.trunkMetadata = trunkMetadata;
        this.height = height;
        this.trunkRadius = trunkRadius;
        this.bend = bend;
        this.branches = branches;
        this.leavesBlock = leavesBlock;
        this.leavesMetadata = leavesMetadata;
        this.canopyShape = canopyShape;
        this.canopyRadius = canopyRadius;
        this.canopyDensity = canopyDensity;
        this.ground = ground;
        this.replace = replace;
    }

    @Override
    public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
        int groundBlock = context.blockId(origin.x, origin.y - 1, origin.z);
        if (groundBlock < 0) {
            return FeatureResult.rejected(context.failure());
        }
        if (!ground.contains(groundBlock)) {
            return FeatureResult.rejected(FeatureResult.INVALID_GROUND);
        }

        Random random = context.random();
        int sampledHeight = height.sample(random);
        List<BlockPosition> trunk = new ArrayList<BlockPosition>();
        int centerX = origin.x;
        int centerZ = origin.z;
        for (int level = 0; level < sampledHeight; level++) {
            if (level > 1 && bend > 0.0D && random.nextDouble() < bend) {
                if (random.nextBoolean()) {
                    centerX += random.nextBoolean() ? 1 : -1;
                } else {
                    centerZ += random.nextBoolean() ? 1 : -1;
                }
            }
            BlockPosition center = new BlockPosition(centerX, origin.y + level, centerZ);
            trunk.add(center);
            for (int x = centerX - trunkRadius + 1; x <= centerX + trunkRadius - 1; x++) {
                for (int z = centerZ - trunkRadius + 1; z <= centerZ + trunkRadius - 1; z++) {
                    String rejection = replaceable(context, x, center.y, z);
                    if (rejection != null) {
                        return FeatureResult.rejected(rejection);
                    }
                    output.setBlock(x, center.y, z, trunkBlock, trunkMetadata);
                }
            }
        }

        List<BlockPosition> canopyCenters = new ArrayList<BlockPosition>();
        canopyCenters.add(trunk.get(trunk.size() - 1));
        if (branches != null) {
            int count = branches.count.sample(random);
            int startLevel = Math.max(1, Math.min(sampledHeight - 1,
                    (int) Math.floor(sampledHeight * branches.start)));
            for (int index = 0; index < count; index++) {
                BlockPosition start = trunk.get(startLevel + random.nextInt(sampledHeight - startLevel));
                int length = branches.length.sample(random);
                double angle = random.nextDouble() * Math.PI * 2.0D;
                int rise = Math.max(0, (int) Math.round(length * branches.upwardBias));
                BlockPosition end = new BlockPosition(start.x + (int) Math.round(Math.cos(angle) * length),
                        Math.min(127, start.y + rise), start.z + (int) Math.round(Math.sin(angle) * length));
                String rejection = branch(context, output, start, end);
                if (rejection != null) {
                    return FeatureResult.rejected(rejection);
                }
                canopyCenters.add(end);
            }
        }

        for (BlockPosition center : canopyCenters) {
            int radius = canopyRadius.sample(random);
            String rejection = canopy(context, output, center, radius, random);
            if (rejection != null) {
                return FeatureResult.rejected(rejection);
            }
        }
        return output.failure() == null ? FeatureResult.placed(output.size(), null, null)
                : FeatureResult.rejected(output.failure());
    }

    private String branch(FeatureContext context, PlacementPlan output, BlockPosition start, BlockPosition end) {
        int steps = Math.max(Math.abs(end.x - start.x), Math.max(Math.abs(end.y - start.y),
                Math.abs(end.z - start.z)));
        for (int step = 1; step <= steps; step++) {
            int x = start.x + (end.x - start.x) * step / steps;
            int y = start.y + (end.y - start.y) * step / steps;
            int z = start.z + (end.z - start.z) * step / steps;
            String rejection = replaceable(context, x, y, z);
            if (rejection != null) {
                return rejection;
            }
            output.setBlock(x, y, z, trunkBlock, trunkMetadata);
        }
        return null;
    }

    private String canopy(FeatureContext context, PlacementPlan output, BlockPosition center, int radius,
            Random random) {
        for (int y = center.y - radius; y <= center.y + radius; y++) {
            int dy = y - center.y;
            int layerRadius = layerRadius(radius, dy);
            for (int x = center.x - layerRadius; x <= center.x + layerRadius; x++) {
                for (int z = center.z - layerRadius; z <= center.z + layerRadius; z++) {
                    int dx = x - center.x;
                    int dz = z - center.z;
                    if (!insideCanopy(dx, dy, dz, radius, layerRadius) || random.nextDouble() > canopyDensity
                            || output.contains(x, y, z)) {
                        continue;
                    }
                    String rejection = replaceable(context, x, y, z);
                    if (rejection != null) {
                        return rejection;
                    }
                    output.setBlock(x, y, z, leavesBlock, leavesMetadata);
                }
            }
        }
        return null;
    }

    private int layerRadius(int radius, int dy) {
        if (canopyShape.equals("cone")) {
            return Math.max(0, radius - (dy + radius) / 2);
        }
        if (canopyShape.equals("layered_disk")) {
            return Math.max(0, radius - Math.abs(dy) / 2);
        }
        return radius;
    }

    private boolean insideCanopy(int dx, int dy, int dz, int radius, int layerRadius) {
        if (canopyShape.equals("layered_disk") || canopyShape.equals("cone")) {
            return dx * dx + dz * dz <= layerRadius * layerRadius;
        }
        double vertical = canopyShape.equals("clustered_sphere") ? dy * dy * 0.75D : dy * dy;
        return dx * dx + vertical + dz * dz <= radius * radius;
    }

    private String replaceable(FeatureContext context, int x, int y, int z) {
        int existing = context.blockId(x, y, z);
        if (existing < 0) {
            return context.failure();
        }
        return replace.contains(existing) ? null : FeatureResult.BLOCKED;
    }

    public static final class Branches {
        public final double start;
        public final IntRange count;
        public final IntRange length;
        public final double upwardBias;

        public Branches(double start, IntRange count, IntRange length, double upwardBias) {
            this.start = start;
            this.count = count;
            this.length = length;
            this.upwardBias = upwardBias;
        }
    }
}
