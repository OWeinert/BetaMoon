package betamoon.worldgen;

import org.luaj.vm2.LuaError;

/** Dimensions in which an ore declaration may run. */
public enum GenerationDimension {
    OVERWORLD("overworld"), NETHER("nether"), BOTH("both");

    private final String luaName;

    GenerationDimension(String luaName) {
        this.luaName = luaName;
    }

    public String getLuaName() {
        return luaName;
    }

    public boolean includes(boolean nether) {
        return this == BOTH || (nether ? this == NETHER : this == OVERWORLD);
    }

    public static GenerationDimension parse(String name) {
        String normalized = name.trim().toLowerCase();
        if (normalized.equals("overworld")) {
            return OVERWORLD;
        }
        if (normalized.equals("nether") || normalized.equals("hell")) {
            return NETHER;
        }
        if (normalized.equals("both") || normalized.equals("all")) {
            return BOTH;
        }
        throw new LuaError("OreGen: unknown dimension: " + name);
    }
}
