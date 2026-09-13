package betamoon.worldgen;

import org.luaj.vm2.LuaError;

/** One of Minecraft's three native biome spawn lists. */
public enum BiomeSpawnGroup {
    MONSTER("monsters"), CREATURE("creatures"), WATER("water");

    private final String luaName;

    BiomeSpawnGroup(String luaName) {
        this.luaName = luaName;
    }

    public String getLuaName() {
        return luaName;
    }

    public static BiomeSpawnGroup parse(String name) {
        String key = name.trim().toLowerCase();
        if (key.equals("monster") || key.equals("monsters")) {
            return MONSTER;
        }
        if (key.equals("creature") || key.equals("creatures") || key.equals("animal") || key.equals("animals")) {
            return CREATURE;
        }
        if (key.equals("water") || key.equals("watercreature") || key.equals("watercreatures")) {
            return WATER;
        }
        throw new LuaError("Biome: unknown spawn list type: " + name);
    }
}
