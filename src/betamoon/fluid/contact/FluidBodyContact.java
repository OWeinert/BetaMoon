package betamoon.fluid.contact;

import betamoon.fluid.FluidRegistration;

/** Aggregated body intersection for one registered fluid. */
public final class FluidBodyContact {
    private final FluidRegistration fluid;
    private final double volume;
    private final int firstX;
    private final int firstY;
    private final int firstZ;

    FluidBodyContact(FluidRegistration fluid, double volume, int firstX, int firstY, int firstZ) {
        this.fluid = fluid;
        this.volume = volume;
        this.firstX = firstX;
        this.firstY = firstY;
        this.firstZ = firstZ;
    }

    public FluidRegistration fluid() {
        return fluid;
    }

    public double volume() {
        return volume;
    }

    public int firstX() {
        return firstX;
    }

    public int firstY() {
        return firstY;
    }

    public int firstZ() {
        return firstZ;
    }
}
