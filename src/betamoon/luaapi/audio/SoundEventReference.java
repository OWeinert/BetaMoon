package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable Lua reference to a sound-event key. */
public final class SoundEventReference extends LuaTable {
    private final AssetKey key;

    SoundEventReference(AssetKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(key.toString());
            }
        });
    }

    AssetKey key() {
        return key;
    }
}
