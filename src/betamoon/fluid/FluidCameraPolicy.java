package betamoon.fluid;

import betamoon.client.assets.AssetLocation;

/** Immutable client presentation selected by the registered fluid at the camera eye. */
public final class FluidCameraPolicy {
    private final Integer fogColor;
    private final Float fogDensity;
    private final String nativeOverlay;
    private final AssetLocation overlayAsset;

    public FluidCameraPolicy(Integer fogColor, Float fogDensity, String nativeOverlay, AssetLocation overlayAsset) {
        if (fogColor != null && (fogColor.intValue() & 0xFF000000) != 0) {
            throw new IllegalArgumentException("Fluid camera fog color must be 24-bit RGB.");
        }
        if (fogDensity != null && (Float.isNaN(fogDensity.floatValue()) || Float.isInfinite(fogDensity.floatValue())
                || fogDensity.floatValue() < 0.0F || fogDensity.floatValue() > 1.0F)) {
            throw new IllegalArgumentException("Fluid camera fog density must be within 0..1.");
        }
        if (nativeOverlay != null && overlayAsset != null) {
            throw new IllegalArgumentException("Fluid camera overlay has two sources.");
        }
        this.fogColor = fogColor;
        this.fogDensity = fogDensity;
        this.nativeOverlay = nativeOverlay;
        this.overlayAsset = overlayAsset;
    }

    public static FluidCameraPolicy forPreset(String preset) {
        if ("water".equals(preset)) {
            return new FluidCameraPolicy(Integer.valueOf(0x050533), Float.valueOf(0.1F), "/misc/water.png", null);
        }
        if ("lava".equals(preset)) {
            return new FluidCameraPolicy(Integer.valueOf(0x991A00), Float.valueOf(1.0F), null, null);
        }
        return new FluidCameraPolicy(null, null, null, null);
    }

    public Integer fogColor() {
        return fogColor;
    }

    public Float fogDensity() {
        return fogDensity;
    }

    public String nativeOverlay() {
        return nativeOverlay;
    }

    public AssetLocation overlayAsset() {
        return overlayAsset;
    }

    public boolean hasOverlay() {
        return nativeOverlay != null || overlayAsset != null;
    }
}
