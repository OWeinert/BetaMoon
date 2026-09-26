package betamoon.system;

import betamoon.assets.AssetKey;
import betamoon.data.DataSchema;
import org.luaj.vm2.LuaValue;

/** Immutable declaration for one Lua-owned persistent service per world. */
public final class WorldServiceDefinition {
    public final AssetKey key;
    public final String owner;
    public final DataSchema data;
    public final int tickInterval;
    public final LuaValue onLoad;
    public final LuaValue onTick;
    public final LuaValue onUnload;

    public WorldServiceDefinition(AssetKey key, String owner, DataSchema data, int tickInterval,
            LuaValue onLoad, LuaValue onTick, LuaValue onUnload) {
        this.key = key;
        this.owner = owner;
        this.data = data;
        this.tickInterval = tickInterval;
        this.onLoad = onLoad;
        this.onTick = onTick;
        this.onUnload = onUnload;
    }
}
