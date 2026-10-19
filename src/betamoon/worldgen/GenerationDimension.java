package betamoon.worldgen;

import betamoon.dimension.DimensionKey;
import betamoon.dimension.DimensionSelector;
import java.util.Locale;
import org.luaj.vm2.LuaError;

/** Dimensions in which an ore declaration may run. */
public final class GenerationDimension {
    public static final GenerationDimension OVERWORLD = new GenerationDimension("overworld");
    public static final GenerationDimension NETHER = new GenerationDimension("nether");
    public static final GenerationDimension BOTH = new GenerationDimension("both");

    private final String luaName;

    private GenerationDimension(String luaName) {
        this.luaName = luaName;
    }

    public static GenerationDimension[] values() {
        return new GenerationDimension[]{OVERWORLD, NETHER, BOTH};
    }

    public String getLuaName() {
        return luaName;
    }

    public boolean includes(boolean nether) {
        return this == BOTH || (nether ? this == NETHER : this == OVERWORLD) || luaName.equals(DimensionSelector.ANY);
    }

    public static GenerationDimension parse(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("overworld") || normalized.equals("minecraft:overworld")) {
            return OVERWORLD;
        }
        if (normalized.equals("nether") || normalized.equals("hell") || normalized.equals("minecraft:nether")) {
            return NETHER;
        }
        if (normalized.equals("both") || normalized.equals("all")) {
            return BOTH;
        }
        try {
            return new GenerationDimension(
                    normalized.equals(DimensionSelector.ANY) ? normalized : DimensionKey.parse(normalized).toString());
        } catch (IllegalArgumentException exception) {
            throw new LuaError("OreGen.dimension: " + exception.getMessage());
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GenerationDimension && luaName.equals(((GenerationDimension) other).luaName);
    }

    @Override
    public int hashCode() {
        return luaName.hashCode();
    }

    @Override
    public String toString() {
        return luaName;
    }
}
