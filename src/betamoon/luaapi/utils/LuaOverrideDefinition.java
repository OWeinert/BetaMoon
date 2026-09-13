package betamoon.luaapi.utils;

import betamoon.luamodloader.LuaScriptRegistry;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.action;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;

/** Parsed callback declaration; tick cadence belongs to Minecraft, not to override registration. */
public final class LuaOverrideDefinition {
    public final String name;
    public final String owner;
    public final LuaValue action;
    public final int interval;
    public final boolean selectedOnly;

    public LuaOverrideDefinition(String name, LuaValue declaration) {
        this.name = name;
        owner = LuaScriptRegistry.getCurrentScriptFile();
        boolean inventoryTick = name.equals("onInventoryTick");
        action = inventoryTick ? action(declaration, name, "interval", "when") : action(declaration, name);
        if (action.isnil()) {
            throw error(name, "callback action is required");
        }
        interval = !inventoryTick || declaration.isfunction() || declaration.get("interval").isnil() ? 1
                : integer(declaration.get("interval"), name + ".interval", 1, 1000000);
        String when = !inventoryTick || declaration.isfunction() || declaration.get("when").isnil() ? "always"
                : string(declaration.get("when"), name + ".when");
        if (!when.equals("always") && !when.equals("selected")) {
            throw error(name + ".when", "expected always or selected");
        }
        selectedOnly = when.equals("selected");
    }
}
