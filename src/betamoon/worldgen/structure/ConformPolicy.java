package betamoon.worldgen.structure;

/**
 * Declarative selection and smoothing rules for terrain-conforming structure
 * columns.
 */
public final class ConformPolicy {
    public final TerrainFootprintPolicy columns;
    public final int maxDisplacement;
    public final int maxStep;
    public final int smoothingRadius;
    public final int smoothingPasses;
    public final boolean allowEmpty;
    public final boolean compatibilityMarkersOnly;

    public ConformPolicy(TerrainFootprintPolicy columns, int maxDisplacement, int maxStep, int smoothingRadius,
            int smoothingPasses, boolean allowEmpty, boolean compatibilityMarkersOnly) {
        this.columns = columns;
        this.maxDisplacement = maxDisplacement;
        this.maxStep = maxStep;
        this.smoothingRadius = smoothingRadius;
        this.smoothingPasses = smoothingPasses;
        this.allowEmpty = allowEmpty;
        this.compatibilityMarkersOnly = compatibilityMarkersOnly;
    }

    String signature() {
        return columns.signature() + '|' + maxDisplacement + '|' + maxStep + '|' + smoothingRadius + '|'
                + smoothingPasses + '|' + allowEmpty + '|' + compatibilityMarkersOnly;
    }
}
