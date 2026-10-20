package betamoon.fluid;

import net.minecraft.src.Material;

/**
 * Native material bridge used by a fluid. Fluid identity and policies remain
 * independent of the selected native material.
 */
public interface FluidCarrier {
    FluidCarrier WATER = new Vanilla("water", Material.water);
    FluidCarrier LAVA = new Vanilla("lava", Material.lava);

    String value();

    Material material();

    default String signature() {
        return value();
    }

    static FluidCarrier fromMaterial(Material material) {
        if (material == Material.water) {
            return WATER;
        }
        if (material == Material.lava) {
            return LAVA;
        }
        throw new IllegalArgumentException("Fluid material is not a vanilla water or lava carrier.");
    }

    static FluidCarrier fromName(String name) {
        if (WATER.value().equals(name)) {
            return WATER;
        }
        if (LAVA.value().equals(name)) {
            return LAVA;
        }
        throw new IllegalArgumentException("Unknown vanilla fluid material '" + name + "'; expected water or lava.");
    }

    final class Custom implements FluidCarrier {
        private final String key;
        private final String signature;
        private final Material material;

        public Custom(String key, String signature, Material material) {
            if (material == null || !material.getIsLiquid()) {
                throw new IllegalArgumentException("Custom fluid material must be liquid.");
            }
            this.key = key;
            this.signature = signature;
            this.material = material;
        }

        @Override
        public String value() {
            return key;
        }

        @Override
        public String signature() {
            return key + "|" + signature;
        }

        @Override
        public Material material() {
            return material;
        }
    }

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
