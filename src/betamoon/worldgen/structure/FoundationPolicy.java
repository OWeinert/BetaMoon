package betamoon.worldgen.structure;

import java.util.Locale;

/**
 * Validated declaration for one-sided support construction beneath a rigid
 * structure.
 */
public final class FoundationPolicy {
    public enum EdgeType {
        HARD, STEPPED, BLENDED, NATURAL, AUTHORED;

        public static EdgeType parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown foundation edge type: " + value);
            }
        }
    }

    public final TerrainFootprintPolicy footprint;
    public final EdgeType edgeType;
    public final int stepEvery;
    public final int maxExpansion;
    public final String edgeMask;
    public final TerrainMaterialPolicy capMaterial;
    public final TerrainMaterialPolicy edgeMaterial;
    public final TerrainMaterialPolicy fillMaterial;
    public final int maxDepth;
    public final TerrainReplacementPolicy.Selector replace;
    public final TerrainReplacementPolicy.Fluid fluids;
    public final int maxUnsupportedSpan;
    public final double requireSupportRatio;
    public final int maxBlocks;
    public final boolean compatibility;

    public FoundationPolicy(TerrainFootprintPolicy footprint, EdgeType edgeType, int stepEvery, int maxExpansion,
            String edgeMask, TerrainMaterialPolicy capMaterial, TerrainMaterialPolicy edgeMaterial,
            TerrainMaterialPolicy fillMaterial, int maxDepth, TerrainReplacementPolicy.Replace replace,
            TerrainReplacementPolicy.Fluid fluids, int maxUnsupportedSpan, double requireSupportRatio,
            boolean compatibility) {
        this(footprint, edgeType, stepEvery, maxExpansion, edgeMask, capMaterial, edgeMaterial, fillMaterial, maxDepth,
                TerrainReplacementPolicy.Selector.preset(replace), fluids, maxUnsupportedSpan, requireSupportRatio,
                4096, compatibility);
    }

    public FoundationPolicy(TerrainFootprintPolicy footprint, EdgeType edgeType, int stepEvery, int maxExpansion,
            String edgeMask, TerrainMaterialPolicy capMaterial, TerrainMaterialPolicy edgeMaterial,
            TerrainMaterialPolicy fillMaterial, int maxDepth, TerrainReplacementPolicy.Selector replace,
            TerrainReplacementPolicy.Fluid fluids, int maxUnsupportedSpan, double requireSupportRatio, int maxBlocks,
            boolean compatibility) {
        this.footprint = footprint;
        this.edgeType = edgeType;
        this.stepEvery = stepEvery;
        this.maxExpansion = maxExpansion;
        this.edgeMask = edgeMask;
        this.capMaterial = capMaterial;
        this.edgeMaterial = edgeMaterial;
        this.fillMaterial = fillMaterial;
        this.maxDepth = maxDepth;
        this.replace = replace;
        this.fluids = fluids;
        this.maxUnsupportedSpan = maxUnsupportedSpan;
        this.requireSupportRatio = requireSupportRatio;
        this.maxBlocks = maxBlocks;
        this.compatibility = compatibility;
    }

    String signature() {
        return footprint.signature() + '|' + edgeType + '|' + stepEvery + '|' + maxExpansion + '|' + edgeMask + '|'
                + capMaterial.signature() + '|' + edgeMaterial.signature() + '|' + fillMaterial.signature() + '|'
                + maxDepth + '|' + replace.signature() + '|' + fluids + '|' + maxUnsupportedSpan + '|'
                + Double.doubleToLongBits(requireSupportRatio) + '|' + maxBlocks + '|' + compatibility;
    }
}
