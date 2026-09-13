package betamoon.luaapi.item;

import betamoon.luaapi.utils.LuaCallbackDispatcher;
import betamoon.luaapi.utils.LuaCallbackDeclarations;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Validated item callbacks and independently parsed use/tool settings. */
public final class ItemDefinition {
    public final LuaCallbackDispatcher<ItemCallback> callbacks;
    public final int interval;
    public final ItemUseDefinition use;
    public final ItemToolDefinition tool;
    public final ItemVisualDefinition visual;
    public final InventoryTickMode tickWhen;
    public ItemDefinition(LuaValue def) {
        LuaCallbackDeclarations.Builder<ItemCallback> callbackBuilder = LuaCallbackDeclarations
                .builder(ItemCallback.class, "item " + def.get("key"));
        for (ItemCallback callback : ItemCallback.values()) {
            if (callback != ItemCallback.INVENTORY_TICK) {
                callbackBuilder.parse(def, callback);
            }
        }
        callbackBuilder.parse(def, ItemCallback.INVENTORY_TICK, "interval", "when");
        LuaValue tick = def.get("onInventoryTick");
        interval = tick.isnil() || tick.get("interval").isnil()
                ? 1
                : integer(tick.get("interval"), "onInventoryTick.interval", 1, 1000000);
        tickWhen = InventoryTickMode.parse(
                tick.isnil() || tick.get("when").isnil() ? "always" : string(tick.get("when"), "onInventoryTick.when"));
        use = new ItemUseDefinition(def);
        tool = new ItemToolDefinition(def.get("tool"), def.get("efficiency"));
        visual = new ItemVisualDefinition(def.get("render"));
        callbacks = new LuaCallbackDispatcher<ItemCallback>(callbackBuilder.build());
    }

}
