package betamoon.fluid;

import net.minecraft.src.Material;

/**
 * Native material bridge used by a fluid. Only vanilla water and lava are
 * currently resolved from Lua; a future custom-material registry can supply
 * another implementation without changing fluid identity or simulation.
 */
public interface FluidCarrier {
    FluidCarrier WATER = new Vanilla("water", Material.water);
    FluidCarrier LAVA = new Vanilla("lava", Material.lava);

    String value();

    Material material();

    static FluidCarrier fromMaterial(Material material) {
        if (material == Material.water) {
            return WATER;
        }
        if (material == Material.lava) {
            return LAVA;
        }
        throw new IllegalArgumentException("Fluid material must be vanilla water or lava for now.");
    }

    static FluidCarrier fromName(String name) {
        if (WATER.value().equals(name)) {
            return WATER;
        }
        if (LAVA.value().equals(name)) {
            return LAVA;
        }
        throw new IllegalArgumentException("Unknown fluid material '" + name + "'; expected water or lava.");
    }

    /** Initial adapter; later material registrations can supply another implementation. */
    final class Vanilla implements FluidCarrier {
        private final String name;
        private final Material material;

        private Vanilla(String name, Material material) {
            this.name = name;
            this.material = material;
        }

        @Override
        public String value() {
            return name;
        }

        @Override
        public Material material() {
            return material;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
