package betamoon.luaapi.block;

import betamoon.luaapi.utils.LuaCallbackKey;

/** Callback identities accepted by block declarations. */
public enum BlockCallback implements LuaCallbackKey {
    TICK("onTick"), ACTIVATE("onActivate"), CLICK("onClick"), CAN_PLACE("canPlace"), PLACED("onPlaced"), ADDED(
            "onAdded"), REMOVED("onRemoved"), NEIGHBOR_CHANGED("onNeighborChanged"), CAN_BREAK("canBreak"), BROKEN(
                    "onBroken"), EXPLODED("onExploded"), ENTITY_WALK("onEntityWalk"), ENTITY_COLLIDE(
                            "onEntityCollide"), GET_DROPS("getDrops"), INPUT_CHANGED("onInputChanged");

    private final String luaName;

    BlockCallback(String luaName) {
        this.luaName = luaName;
    }

    @Override
    public String luaName() {
        return luaName;
    }

    public static BlockCallback fromLuaName(String name) {
        for (BlockCallback callback : values()) {
            if (callback.luaName.equals(name)) {
                return callback;
            }
        }
        return null;
    }
}
