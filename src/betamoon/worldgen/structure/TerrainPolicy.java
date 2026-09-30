package betamoon.worldgen.structure;

import betamoon.worldgen.TerrainSurface;
import betamoon.worldgen.WorldGenLimits;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.src.Block;

/**
 * Immutable, normalized rules for fitting and terrain operations around one
 * structure.
 */
public final class TerrainPolicy {
    public enum Mode {
        EXACT, FIT, FOUNDATION, TERRACE, CONFORM;

        public static Mode parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown terrain mode: " + value);
            }
        }

        public String luaName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Anchor {
        MINIMUM, MAXIMUM, MEDIAN, MEAN, CENTER, PERCENTILE;

        public static Anchor parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown terrain anchor: " + value);
            }
        }

        public String luaName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final TerrainPolicy EXACT = new TerrainPolicy(Mode.EXACT, TerrainSurface.EXACT, Anchor.MEDIAN, 0.5D,
            Integer.MAX_VALUE, Integer.MAX_VALUE, 0.0D, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, null, null, null, null,
            Collections.<String, TerrainFootprintPolicy>emptyMap());

    public final Mode mode;
    public final TerrainSurface surface;
    public final Anchor anchor;
    public final double percentile;
    public final int maxSlope;
    public final int maxStep;
    public final double requireSupportRatio;
    public final int verticalOffset;

    /**
     * Legacy normalized fields retained for source compatibility and concise
     * descriptions.
     */
    public final int foundationBlock;
    public final int foundationMetadata;
    public final int maxFoundationDepth;
    public final int maxCutDepth;
    public final int maxFillDepth;
    public final int padding;
    public final int blendRadius;
    public final int maxBlendStep;
    public final int maxConformDisplacement;

    public final FoundationPolicy foundation;
    public final TerracePolicy terrace;
    public final ConformPolicy conform;
    public final ExcavationPolicy excavation;
    public final TerrainAdaptationPolicy adaptation;
    public final Map<String, TerrainFootprintPolicy> masks;

    /** Compatibility constructor used by existing integrations and tests. */
    public TerrainPolicy(Mode mode, TerrainSurface surface, Anchor anchor, double percentile, int maxSlope, int maxStep,
            double requireSupportRatio, int verticalOffset, int foundationBlock, int foundationMetadata,
            int maxFoundationDepth, int maxCutDepth, int maxFillDepth, int padding, int blendRadius, int maxBlendStep,
            int maxConformDisplacement) {
        this(mode, surface, anchor, percentile, maxSlope, maxStep, requireSupportRatio, verticalOffset, foundationBlock,
                foundationMetadata, maxFoundationDepth, maxCutDepth, maxFillDepth, padding, blendRadius, maxBlendStep,
                maxConformDisplacement, legacyFoundation(mode, foundationBlock, foundationMetadata, maxFoundationDepth),
                legacyTerrace(mode, foundationBlock, foundationMetadata, maxCutDepth, maxFillDepth, padding,
                        blendRadius, maxBlendStep),
                legacyConform(mode, maxConformDisplacement, maxStep), null,
                Collections.<String, TerrainFootprintPolicy>emptyMap());
    }

    public TerrainPolicy(Mode mode, TerrainSurface surface, Anchor anchor, double percentile, int maxSlope, int maxStep,
            double requireSupportRatio, int verticalOffset, int foundationBlock, int foundationMetadata,
            int maxFoundationDepth, int maxCutDepth, int maxFillDepth, int padding, int blendRadius, int maxBlendStep,
            int maxConformDisplacement, FoundationPolicy foundation, TerracePolicy terrace, ConformPolicy conform,
            ExcavationPolicy excavation, Map<String, TerrainFootprintPolicy> masks) {
        this.mode = mode;
        this.surface = surface;
        this.anchor = anchor;
        this.percentile = percentile;
        this.maxSlope = maxSlope;
        this.maxStep = maxStep;
        this.requireSupportRatio = requireSupportRatio;
        this.verticalOffset = verticalOffset;
        this.foundationBlock = foundationBlock;
        this.foundationMetadata = foundationMetadata;
        this.maxFoundationDepth = maxFoundationDepth;
        this.maxCutDepth = maxCutDepth;
        this.maxFillDepth = maxFillDepth;
        this.padding = padding;
        this.blendRadius = blendRadius;
        this.maxBlendStep = maxBlendStep;
        this.maxConformDisplacement = maxConformDisplacement;
        this.foundation = foundation;
        this.terrace = terrace;
        this.conform = conform;
        this.excavation = excavation;
        adaptation = foundation == null
                ? terrace == null ? null : TerrainAdaptationPolicy.terrace(terrace)
                : TerrainAdaptationPolicy.foundation(foundation);
        this.masks = Collections.unmodifiableMap(new LinkedHashMap<String, TerrainFootprintPolicy>(masks));
        if (foundation != null && terrace != null) {
            throw new IllegalArgumentException("foundation and terrace policies cannot both be enabled");
        }
    }

    public boolean adaptsTerrain() {
        return foundation != null || terrace != null || excavation != null;
    }

    public int extraBlocks(int supportColumns, int sizeX, int sizeZ) {
        long result = 0L;
        if (foundation != null) {
            int width = sizeX + (foundation.footprint.padding + foundation.maxExpansion) * 2;
            int depth = sizeZ + (foundation.footprint.padding + foundation.maxExpansion) * 2;
            result += Math.min((long) foundation.maxBlocks,
                    (long) Math.max(supportColumns, width * depth) * foundation.maxDepth);
        }
        if (terrace != null) {
            int outer = terrace.footprint.padding + terrace.radius;
            long columns = (long) (sizeX + outer * 2) * (sizeZ + outer * 2);
            result += Math.min((long) terrace.maxBlocks, columns * Math.max(terrace.maxCutDepth, terrace.maxFillDepth));
        }
        if (excavation != null) {
            result += excavation.maxBlocks;
        }
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    public int extraRadius() {
        return requiredRadius(0);
    }

    public int requiredRadius(int templateRadius) {
        int result = templateRadius + Math.abs(verticalOffset);
        if (foundation != null) {
            int core = footprintRadius(foundation.footprint, templateRadius, new java.util.LinkedHashSet<String>());
            if (foundation.edgeType == FoundationPolicy.EdgeType.AUTHORED) {
                TerrainFootprintPolicy edge = masks.get(foundation.edgeMask);
                if (edge != null) {
                    core = Math.max(core, footprintRadius(edge, templateRadius, new java.util.LinkedHashSet<String>()));
                }
            }
            result = Math.max(result, core + foundation.maxExpansion);
            result = Math.max(result, templateRadius + Math.abs(verticalOffset) + foundation.maxDepth);
        }
        if (terrace != null) {
            int core = footprintRadius(terrace.footprint, templateRadius, new java.util.LinkedHashSet<String>());
            if (terrace.transitionType == TerracePolicy.TransitionType.AUTHORED) {
                TerrainFootprintPolicy transition = masks.get(terrace.transitionMask);
                if (transition != null) {
                    core = Math.max(core,
                            footprintRadius(transition, templateRadius, new java.util.LinkedHashSet<String>()));
                }
            } else {
                core += terrace.radius;
            }
            result = Math.max(result, core);
            result = Math.max(result,
                    templateRadius + Math.abs(verticalOffset) + Math.max(terrace.maxCutDepth, terrace.maxFillDepth));
        }
        if (conform != null) {
            result = Math.max(result, templateRadius + Math.abs(verticalOffset) + conform.maxDisplacement);
        }
        if (excavation != null) {
            for (ExcavationPolicy.Volume volume : excavation.volumes) {
                result = Math.max(result, Math.abs(verticalOffset) + volumeRadius(volume, templateRadius));
            }
        }
        return result;
    }

    public String signature() {
        StringBuilder result = new StringBuilder();
        result.append(mode.luaName()).append('|').append(surface.getName()).append('|').append(anchor.luaName())
                .append('|').append(Double.doubleToLongBits(percentile)).append('|').append(maxSlope).append('|')
                .append(maxStep).append('|').append(Double.doubleToLongBits(requireSupportRatio)).append('|')
                .append(verticalOffset).append('|').append(foundation == null ? "-" : foundation.signature())
                .append('|').append(terrace == null ? "-" : terrace.signature()).append('|')
                .append(conform == null ? "-" : conform.signature()).append('|')
                .append(excavation == null ? "-" : excavation.signature());
        for (Map.Entry<String, TerrainFootprintPolicy> entry : new TreeMap<String, TerrainFootprintPolicy>(masks)
                .entrySet()) {
            result.append("|mask:").append(entry.getKey()).append('=').append(entry.getValue().signature());
        }
        return result.toString();
    }

    private static FoundationPolicy legacyFoundation(Mode mode, int block, int metadata, int depth) {
        if (mode != Mode.FOUNDATION) {
            return null;
        }
        TerrainMaterialPolicy material = TerrainMaterialPolicy.fixed(block == 0 ? Block.cobblestone.blockID : block,
                metadata);
        return new FoundationPolicy(TerrainFootprintPolicy.SUPPORT, FoundationPolicy.EdgeType.HARD, 1, 0, null,
                material, material, material, depth, TerrainReplacementPolicy.Replace.TERRAIN_AND_VEGETATION,
                TerrainReplacementPolicy.Fluid.REJECT, 0, 0.0D, true);
    }

    private static TerracePolicy legacyTerrace(Mode mode, int block, int metadata, int cut, int fill, int padding,
            int blend, int step) {
        if (mode != Mode.TERRACE) {
            return null;
        }
        TerrainFootprintPolicy footprint = new TerrainFootprintPolicy(TerrainFootprintPolicy.Source.BASE_BOUNDS,
                TerrainFootprintPolicy.Shape.BOUNDS, null, padding, 0, 0, false, Collections.<String>emptySet(),
                Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet(), "any",
                null, null, null);
        TerrainMaterialPolicy material = block == 0
                ? TerrainMaterialPolicy.SAMPLE_SURFACE
                : TerrainMaterialPolicy.fixed(block, metadata);
        return new TerracePolicy(footprint,
                blend == 0 ? TerracePolicy.TransitionType.HARD : TerracePolicy.TransitionType.GRADED, blend, step, null,
                cut, fill, material, block == 0 ? TerrainMaterialPolicy.SAMPLE_SUBSURFACE : material,
                TerrainReplacementPolicy.Replace.ORDINARY_TERRAIN, TerrainReplacementPolicy.Fluid.REJECT, true);
    }

    private static ConformPolicy legacyConform(Mode mode, int displacement, int step) {
        return mode == Mode.CONFORM
                ? new ConformPolicy(TerrainFootprintPolicy.SUPPORT, displacement, step, 0, 0, false, true)
                : null;
    }

    private int volumeRadius(ExcavationPolicy.Volume volume, int templateRadius) {
        if ((volume.shape == ExcavationPolicy.Shape.FOOTPRINT || volume.shape == ExcavationPolicy.Shape.AUTHORED)
                && volume.from != null && volume.to != null) {
            int horizontal = footprintRadius(volume.footprint, templateRadius, new java.util.LinkedHashSet<String>());
            int surfaceExtent = Math.max(0, WorldGenLimits.MAX_FEATURE_RADIUS - Math.abs(verticalOffset));
            int from = volume.from.surface ? surfaceExtent : Math.abs(volume.from.value);
            int to = volume.to.surface ? surfaceExtent : Math.abs(volume.to.value);
            return Math.max(horizontal, Math.max(from, to));
        }
        if (volume.shape == ExcavationPolicy.Shape.BOX && volume.minimum != null && volume.maximum != null) {
            return Math.max(Math.max(Math.abs(volume.minimum.x), Math.abs(volume.maximum.x)),
                    Math.max(Math.max(Math.abs(volume.minimum.y), Math.abs(volume.maximum.y)),
                            Math.max(Math.abs(volume.minimum.z), Math.abs(volume.maximum.z))));
        }
        if (volume.shape == ExcavationPolicy.Shape.AUTHORED) {
            java.util.List<betamoon.worldgen.BlockPosition> cells = volume.cells;
            if (volume.name != null) {
                java.util.List<betamoon.worldgen.BlockPosition> named = excavation.masks.get(volume.name);
                cells = named == null ? Collections.<betamoon.worldgen.BlockPosition>emptyList() : named;
            }
            int radius = 0;
            for (betamoon.worldgen.BlockPosition cell : cells) {
                radius = Math.max(radius, Math.max(Math.abs(cell.x), Math.max(Math.abs(cell.y), Math.abs(cell.z))));
            }
            return cells.isEmpty() ? templateRadius : radius;
        }
        if (volume.center != null) {
            return Math.max(Math.abs(volume.center.x) + volume.radiusX,
                    Math.max(Math.abs(volume.center.y) + volume.radiusY, Math.abs(volume.center.z) + volume.radiusZ));
        }
        return Math.max(volume.radiusX, Math.max(volume.radiusY, volume.radiusZ));
    }

    private int footprintRadius(TerrainFootprintPolicy policy, int templateRadius, java.util.Set<String> active) {
        if (policy == null) {
            return templateRadius;
        }
        int radius = templateRadius;
        if (!policy.authoredCells.isEmpty()) {
            radius = 0;
            for (betamoon.worldgen.BlockPosition cell : policy.authoredCells) {
                radius = Math.max(radius, Math.max(Math.abs(cell.x), Math.abs(cell.z)));
            }
        } else if (policy.source == TerrainFootprintPolicy.Source.NAMED && active.add(policy.name)) {
            TerrainFootprintPolicy named = masks.get(policy.name);
            if (named != null) {
                radius = footprintRadius(named, templateRadius, active);
            }
            active.remove(policy.name);
        }
        return radius + policy.padding;
    }
}
