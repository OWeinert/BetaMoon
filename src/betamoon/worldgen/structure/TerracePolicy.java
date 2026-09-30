package betamoon.worldgen.structure;

import java.util.Locale;

/** Validated declaration for two-sided cut-and-fill terrain grading. */
public final class TerracePolicy {
    public enum TransitionType {
        HARD, STEPPED, GRADED, BLENDED, NATURAL, AUTHORED;

        public static TransitionType parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown terrace transition type: " + value);
            }
        }
    }

    public final TerrainFootprintPolicy footprint;
    public final TransitionType transitionType;
    public final int radius;
    public final int maxStep;
    public final String transitionMask;
    public final int maxCutDepth;
    public final int maxFillDepth;
    public final TerrainMaterialPolicy topMaterial;
    public final TerrainMaterialPolicy fillMaterial;
    public final TerrainReplacementPolicy.Selector replace;
    public final TerrainReplacementPolicy.Fluid fluids;
    public final int maxBlocks;
    public final boolean compatibility;

    public TerracePolicy(TerrainFootprintPolicy footprint, TransitionType transitionType, int radius, int maxStep,
            String transitionMask, int maxCutDepth, int maxFillDepth, TerrainMaterialPolicy topMaterial,
            TerrainMaterialPolicy fillMaterial, TerrainReplacementPolicy.Replace replace,
            TerrainReplacementPolicy.Fluid fluids, boolean compatibility) {
        this(footprint, transitionType, radius, maxStep, transitionMask, maxCutDepth, maxFillDepth, topMaterial,
                fillMaterial, TerrainReplacementPolicy.Selector.preset(replace), fluids, 4096, compatibility);
    }

    public TerracePolicy(TerrainFootprintPolicy footprint, TransitionType transitionType, int radius, int maxStep,
            String transitionMask, int maxCutDepth, int maxFillDepth, TerrainMaterialPolicy topMaterial,
            TerrainMaterialPolicy fillMaterial, TerrainReplacementPolicy.Selector replace,
            TerrainReplacementPolicy.Fluid fluids, int maxBlocks, boolean compatibility) {
        this.footprint = footprint;
        this.transitionType = transitionType;
        this.radius = radius;
        this.maxStep = maxStep;
        this.transitionMask = transitionMask;
        this.maxCutDepth = maxCutDepth;
        this.maxFillDepth = maxFillDepth;
        this.topMaterial = topMaterial;
        this.fillMaterial = fillMaterial;
        this.replace = replace;
        this.fluids = fluids;
        this.maxBlocks = maxBlocks;
        this.compatibility = compatibility;
    }

    String signature() {
        return footprint.signature() + '|' + transitionType + '|' + radius + '|' + maxStep + '|' + transitionMask + '|'
                + maxCutDepth + '|' + maxFillDepth + '|' + topMaterial.signature() + '|' + fillMaterial.signature()
                + '|' + replace.signature() + '|' + fluids + '|' + maxBlocks + '|' + compatibility;
    }
}
