package betamoon.fluid;

/**
 * Independent environment traits; a custom fluid's carrier does not opt it into
 * native water behavior.
 */
public final class FluidEcologyPolicy {
    public final boolean fallingBlocksPassThrough;
    public final boolean hydratesFarmland;
    public final boolean supportsReeds;
    public final boolean absorbedBySponge;
    public final boolean boatsFloat;
    public final boolean aquaticHabitat;
    public final String pathfinding;

    public FluidEcologyPolicy(boolean fallingBlocksPassThrough, boolean hydratesFarmland, boolean supportsReeds,
            boolean absorbedBySponge, boolean boatsFloat, boolean aquaticHabitat, String pathfinding) {
        if (!pathfinding.equals("blocked") && !pathfinding.equals("passable") && !pathfinding.equals("water")
                && !pathfinding.equals("lava")) {
            throw new IllegalArgumentException("Fluid pathfinding must be blocked, passable, water or lava");
        }
        this.fallingBlocksPassThrough = fallingBlocksPassThrough;
        this.hydratesFarmland = hydratesFarmland;
        this.supportsReeds = supportsReeds;
        this.absorbedBySponge = absorbedBySponge;
        this.boatsFloat = boatsFloat;
        this.aquaticHabitat = aquaticHabitat;
        this.pathfinding = pathfinding;
    }

    public static FluidEcologyPolicy defaults(boolean vanilla, String preset) {
        boolean water = vanilla && preset.equals("water");
        boolean lava = vanilla && preset.equals("lava");
        return new FluidEcologyPolicy(water || lava, water, water, false, water, water,
                water ? "water" : lava ? "lava" : "blocked");
    }
}
