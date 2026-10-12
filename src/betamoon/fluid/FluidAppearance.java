package betamoon.fluid;

/** Resolved atlas and render properties consumed without calling Lua at render time. */
public final class FluidAppearance {
    private final int stillTexture;
    private final int flowingTexture;
    private final int tint;
    private final int renderPass;
    private final int light;
    private final int opacity;

    public FluidAppearance(int stillTexture, int flowingTexture, int tint, int renderPass, int light, int opacity) {
        if (stillTexture < 0 || flowingTexture < 0) {
            throw new IllegalArgumentException("Fluid texture indices must be nonnegative.");
        }
        if ((tint & 0xFF000000) != 0) {
            throw new IllegalArgumentException("Fluid tint must be a 24-bit RGB value.");
        }
        if (renderPass < 0 || renderPass > 1) {
            throw new IllegalArgumentException("Fluid render pass must be 0 or 1.");
        }
        if (light < 0 || light > 15) {
            throw new IllegalArgumentException("Fluid light must be between 0 and 15.");
        }
        if (opacity < 0 || opacity > 255) {
            throw new IllegalArgumentException("Fluid opacity must be between 0 and 255.");
        }
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.tint = tint;
        this.renderPass = renderPass;
        this.light = light;
        this.opacity = opacity;
    }

    public int stillTexture() {
        return stillTexture;
    }

    public int flowingTexture() {
        return flowingTexture;
    }

    public int tint() {
        return tint;
    }

    public int renderPass() {
        return renderPass;
    }

    public int light() {
        return light;
    }

    public int opacity() {
        return opacity;
    }
}
