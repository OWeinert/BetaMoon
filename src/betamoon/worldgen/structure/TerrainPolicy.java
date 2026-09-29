package betamoon.worldgen.structure;

import betamoon.worldgen.TerrainSurface;
import java.util.Locale;

/** Immutable, validated rules for fitting one structure template to terrain. */
public final class TerrainPolicy {
    public enum Mode {
        EXACT,
        FIT,
        FOUNDATION,
        TERRACE,
        CONFORM;

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
        MINIMUM,
        MAXIMUM,
        MEDIAN,
        MEAN,
        CENTER,
        PERCENTILE;

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

    public static final TerrainPolicy EXACT = new TerrainPolicy(Mode.EXACT, TerrainSurface.EXACT, Anchor.MEDIAN,
            0.5D, Integer.MAX_VALUE, Integer.MAX_VALUE, 0.0D, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);

    public final Mode mode;
    public final TerrainSurface surface;
    public final Anchor anchor;
    public final double percentile;
    public final int maxSlope;
    public final int maxStep;
    public final double requireSupportRatio;
    public final int verticalOffset;
    public final int foundationBlock;
    public final int foundationMetadata;
    public final int maxFoundationDepth;
    public final int maxCutDepth;
    public final int maxFillDepth;
    public final int padding;
    public final int blendRadius;
    public final int maxBlendStep;
    public final int maxConformDisplacement;

    public TerrainPolicy(Mode mode, TerrainSurface surface, Anchor anchor, double percentile, int maxSlope,
            int maxStep, double requireSupportRatio, int verticalOffset, int foundationBlock,
            int foundationMetadata, int maxFoundationDepth, int maxCutDepth, int maxFillDepth, int padding,
            int blendRadius, int maxBlendStep, int maxConformDisplacement) {
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
    }

    public boolean adaptsTerrain() {
        return mode != Mode.EXACT;
    }

    public int extraBlocks(int supportColumns, int sizeX, int sizeZ) {
        long result;
        if (mode == Mode.FOUNDATION) {
            result = (long) supportColumns * maxFoundationDepth;
        } else if (mode == Mode.TERRACE) {
            int outer = padding + blendRadius;
            long columns = (long) (sizeX + outer * 2) * (sizeZ + outer * 2);
            result = columns * Math.max(maxCutDepth, maxFillDepth);
        } else {
            result = 0L;
        }
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    public int extraRadius() {
        int offset = Math.abs(verticalOffset);
        switch (mode) {
            case FOUNDATION:
                return offset + maxFoundationDepth;
            case TERRACE:
                return offset + Math.max(Math.max(maxCutDepth, maxFillDepth), padding + blendRadius);
            case CONFORM:
                return offset + maxConformDisplacement;
            case FIT:
                return offset;
            default:
                return 0;
        }
    }

    public String signature() {
        return mode.luaName() + '|' + surface.getName() + '|' + anchor.luaName() + '|'
                + Double.doubleToLongBits(percentile) + '|' + maxSlope + '|' + maxStep + '|'
                + Double.doubleToLongBits(requireSupportRatio) + '|' + verticalOffset + '|' + foundationBlock + ':'
                + foundationMetadata + '|' + maxFoundationDepth + '|' + maxCutDepth + '|' + maxFillDepth + '|'
                + padding + '|' + blendRadius + '|' + maxBlendStep + '|' + maxConformDisplacement;
    }
}
