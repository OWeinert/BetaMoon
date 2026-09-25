package betamoon.worldgen;

import java.util.List;

/** Ground-validated weighted structure template used as a tree. */
public final class StructureTreeFeature implements WorldFeature {
    private final List<WorldGenKey> templates;
    private final int[] cumulativeWeights;
    private final BlockSet ground;
    private final String rotation;
    private final String mirror;

    public StructureTreeFeature(List<WorldGenKey> templates, int[] cumulativeWeights, BlockSet ground,
            String rotation, String mirror) {
        this.templates = templates;
        this.cumulativeWeights = cumulativeWeights.clone();
        this.ground = ground;
        this.rotation = rotation;
        this.mirror = mirror;
    }

    @Override
    public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
        int existing = context.blockId(origin.x, origin.y - 1, origin.z);
        if (existing < 0) {
            return FeatureResult.rejected(context.failure());
        }
        if (!ground.contains(existing)) {
            return FeatureResult.rejected(FeatureResult.INVALID_GROUND);
        }
        int selected = context.random().nextInt(cumulativeWeights[cumulativeWeights.length - 1]) + 1;
        int index = 0;
        while (selected > cumulativeWeights[index]) {
            index++;
        }
        return context.plan(templates.get(index), origin, output, new FeatureOptions(rotation, mirror));
    }
}
