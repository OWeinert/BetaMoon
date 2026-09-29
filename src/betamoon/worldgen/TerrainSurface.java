package betamoon.worldgen;

import java.util.Locale;

/** Named vertical references used by height providers and structure terrain policies. */
public enum TerrainSurface {
    WORLD_SURFACE("world_surface"),
    SOLID_SURFACE("solid_surface"),
    OCEAN_FLOOR("ocean_floor"),
    FLUID_SURFACE("fluid_surface"),
    EXACT("exact");

    private final String name;

    TerrainSurface(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static TerrainSurface parse(String value) {
        String normalized = value == null ? "solid_surface" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("surface")) {
            return WORLD_SURFACE;
        }
        for (TerrainSurface surface : values()) {
            if (surface.name.equals(normalized)) {
                return surface;
            }
        }
        throw new IllegalArgumentException("unknown surface sampler: " + value);
    }
}
