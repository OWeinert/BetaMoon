package betamoon.fluid;

/** Side-neutral holder for the client-assigned custom render ID. */
public final class FluidRenderType {
    private static volatile int value = -1;

    private FluidRenderType() {
    }

    public static int get() {
        return value;
    }

    public static void install(int renderType) {
        if (renderType < 0) {
            throw new IllegalArgumentException("Fluid render type must be nonnegative.");
        }
        if (value >= 0 && value != renderType) {
            throw new IllegalStateException("Fluid render type is already installed.");
        }
        value = renderType;
    }
}
