package betamoon.luaapi.world;

import betamoon.luaapi.utils.PositionI;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class LuaWorldInfo extends LuaTable {
    public LuaWorldInfo(WorldInfo info) {
        if (info == null) {
            return;
        }
        put("name", LuaValue.valueOf(info.getWorldName()));
        put("seed", LuaValue.valueOf(info.getRandomSeed()));
        put("getSpawnX", LuaValue.valueOf(info.getSpawnX()));
        put("getSpawnY", LuaValue.valueOf(info.getSpawnY()));
        put("getSpawnZ", LuaValue.valueOf(info.getSpawnZ()));
        put("spawnPos", new PositionI(info.getSpawnX(), info.getSpawnY(), info.getSpawnZ()));
        put("worldTime", LuaValue.valueOf(info.getWorldTime()));
        put("lastTimePlayed", LuaValue.valueOf(info.getLastTimePlayed()));
        put("sizeOnDisk", LuaValue.valueOf(info.getSizeOnDisk()));
        put("dimension", LuaValue.valueOf(info.getDimension()));
        put("saveVersion", LuaValue.valueOf(info.getSaveVersion()));
        put("raining", LuaValue.valueOf(info.getRaining()));
        put("rainTime", LuaValue.valueOf(info.getRainTime()));
        put("thundering", LuaValue.valueOf(info.getThundering()));
        put("thunderTime", LuaValue.valueOf(info.getThunderTime()));
    }

    public static LuaWorldInfo fromWorld(World world) {
        LuaWorldInfo result = new LuaWorldInfo(world.getWorldInfo());
        long time = world.getWorldTime();
        result.put("day", LuaValue.valueOf(Math.floorDiv(time, 24000L)));
        result.put("timeOfDay", LuaValue.valueOf(Math.floorMod(time, 24000L)));
        result.put("celestialAngle", LuaValue.valueOf(world.getCelestialAngle(1.0F)));
        result.put("daytime", LuaValue.valueOf(world.isDaytime()));
        result.put("difficulty", LuaValue.valueOf(world.difficultySetting));
        result.put("height", LuaValue.valueOf(128));
        return result;
    }

    private void put(String key, LuaValue value) {
        super.rawset(LuaValue.valueOf(key), value);
    }

    @Override
    public void set(int key, LuaValue value) {
        readOnly();
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        readOnly();
    }

    @Override
    public void rawset(int key, LuaValue value) {
        readOnly();
    }

    @Override
    public void rawset(LuaValue key, LuaValue value) {
        readOnly();
    }

    @Override
    public LuaValue remove(int position) {
        return readOnly();
    }

    @Override
    public void insert(int position, LuaValue value) {
        readOnly();
    }

    @Override
    public void sort(LuaValue comparator) {
        readOnly();
    }

    @Override
    public LuaValue setmetatable(LuaValue metatable) {
        return readOnly();
    }

    private LuaValue readOnly() {
        throw new LuaError("WorldInfo is read-only.");
    }
}
