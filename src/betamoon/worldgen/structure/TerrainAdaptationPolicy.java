package betamoon.worldgen.structure;

/**
 * Shared compiled model used by the distinct foundation and terrace
 * declarations.
 */
public final class TerrainAdaptationPolicy {
    public enum Operation {
        SUPPORT_FILL, CUT_AND_FILL
    }

    public final Operation operation;
    public final TerrainFootprintPolicy footprint;
    public final TerrainMaterialPolicy topMaterial;
    public final TerrainMaterialPolicy edgeMaterial;
    public final TerrainMaterialPolicy fillMaterial;
    public final TerrainReplacementPolicy.Selector replace;
    public final TerrainReplacementPolicy.Fluid fluids;
    public final int maximumCutDepth;
    public final int maximumFillDepth;
    public final int transitionRadius;
    public final int maximumTransitionStep;
    public final int maximumWrites;

    private TerrainAdaptationPolicy(Operation operation, TerrainFootprintPolicy footprint,
            TerrainMaterialPolicy topMaterial, TerrainMaterialPolicy edgeMaterial, TerrainMaterialPolicy fillMaterial,
            TerrainReplacementPolicy.Selector replace, TerrainReplacementPolicy.Fluid fluids, int maximumCutDepth,
            int maximumFillDepth, int transitionRadius, int maximumTransitionStep, int maximumWrites) {
        this.operation = operation;
        this.footprint = footprint;
        this.topMaterial = topMaterial;
        this.edgeMaterial = edgeMaterial;
        this.fillMaterial = fillMaterial;
        this.replace = replace;
        this.fluids = fluids;
        this.maximumCutDepth = maximumCutDepth;
        this.maximumFillDepth = maximumFillDepth;
        this.transitionRadius = transitionRadius;
        this.maximumTransitionStep = maximumTransitionStep;
        this.maximumWrites = maximumWrites;
    }

    static TerrainAdaptationPolicy foundation(FoundationPolicy policy) {
        return new TerrainAdaptationPolicy(Operation.SUPPORT_FILL, policy.footprint, policy.capMaterial,
                policy.edgeMaterial, policy.fillMaterial, policy.replace, policy.fluids, 0, policy.maxDepth,
                policy.maxExpansion, policy.stepEvery, policy.maxBlocks);
    }

    static TerrainAdaptationPolicy terrace(TerracePolicy policy) {
        return new TerrainAdaptationPolicy(Operation.CUT_AND_FILL, policy.footprint, policy.topMaterial,
                policy.topMaterial, policy.fillMaterial, policy.replace, policy.fluids, policy.maxCutDepth,
                policy.maxFillDepth, policy.radius, policy.maxStep, policy.maxBlocks);
    }
}
