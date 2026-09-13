package betamoon.luaapi.item;

import betamoon.luaapi.utils.LuaCallbackKey;

/** Callback identities accepted by item declarations. */
public enum ItemCallback implements LuaCallbackKey {
    USE_FIRST("onUseFirst"), USE_ON_BLOCK("onUseOnBlock"), USE("onUse"), USE_ON_ENTITY("onUseOnEntity"), HIT_ENTITY(
            "onHitEntity"), BLOCK_DESTROYED("onBlockDestroyed"), CRAFTED("onCrafted"), CAN_HARVEST(
                    "canHarvest"), MINING_SPEED("getMiningSpeed"), INVENTORY_TICK("onInventoryTick");

    private final String luaName;

    ItemCallback(String luaName) {
        this.luaName = luaName;
    }

    @Override
    public String luaName() {
        return luaName;
    }

    public static ItemCallback fromLuaName(String name) {
        for (ItemCallback callback : values()) {
            if (callback.luaName.equals(name)) {
                return callback;
            }
        }
        return null;
    }
}
