package betamoon.luaapi.world;

import net.minecraft.src.WorldInfo;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaWorldInfo extends LuaTable {
    private static final LuaTable IMMUTABLE_META = buildImmutableMeta();

    public LuaWorldInfo(WorldInfo info) {
        if (info == null) {
            return;
        }
        set("name", LuaValue.valueOf(info.getWorldName()));
        set("seed", LuaValue.valueOf(info.getRandomSeed()));
        set("spawnX", LuaValue.valueOf(info.getSpawnX()));
        set("spawnY", LuaValue.valueOf(info.getSpawnY()));
        set("spawnZ", LuaValue.valueOf(info.getSpawnZ()));
        LuaTable spawn = new LuaTable();
            spawn.set("x", LuaValue.valueOf(info.getSpawnX()));
            spawn.set("y", LuaValue.valueOf(info.getSpawnY()));
            spawn.set("z", LuaValue.valueOf(info.getSpawnZ()));
        set("spawn", spawn);
        set("worldTime", LuaValue.valueOf(info.getWorldTime()));
        set("lastTimePlayed", LuaValue.valueOf(info.getLastTimePlayed()));
        set("sizeOnDisk", LuaValue.valueOf(info.getSizeOnDisk()));
        set("dimension", LuaValue.valueOf(info.getDimension()));
        set("saveVersion", LuaValue.valueOf(info.getSaveVersion()));
        set("raining", LuaValue.valueOf(info.getRaining()));
        set("rainTime", LuaValue.valueOf(info.getRainTime()));
        set("thundering", LuaValue.valueOf(info.getThundering()));
        set("thunderTime", LuaValue.valueOf(info.getThunderTime()));
        setmetatable(IMMUTABLE_META);
    }

    private static LuaTable buildImmutableMeta() {
        LuaTable meta = new LuaTable();
        meta.set("__newindex", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                throw new LuaError("WorldInfo is read-only.");
            }
        });
        return meta;
    }
}
