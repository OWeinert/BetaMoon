package betamoon.worldgen;

/** Compiled reusable feature; implementations plan changes without mutating the world. */
public interface WorldFeature {
    FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output);
}
