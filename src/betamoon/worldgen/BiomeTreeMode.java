package betamoon.worldgen;

import org.luaj.vm2.LuaError;

/** Tree generator policy for a custom biome. */
public enum BiomeTreeMode {
    DEFAULT("default"), BIG("big"), NORMAL("normal"), NONE("none");

    private final String luaName;

    BiomeTreeMode(String luaName) {
        this.luaName = luaName;
    }

    public String getLuaName() {
        return luaName;
    }

    public static BiomeTreeMode parse(String name) {
        String normalized = name.trim().toLowerCase();
        for (BiomeTreeMode mode : values()) {
            if (mode.luaName.equals(normalized)) {
                return mode;
            }
        }
        throw new LuaError("Biome: unknown tree generator mode: " + name);
    }
}
