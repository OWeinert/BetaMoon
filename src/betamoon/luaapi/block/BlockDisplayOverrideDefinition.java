package betamoon.luaapi.block;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luaapi.utils.LuaOverrideActionDefinition;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/** Validated display override declaration, independent of runtime dispatch. */
public final class BlockDisplayOverrideDefinition implements LuaOverrideActionDefinition {
    public final LuaValue action;
    public final String owner;
    public final double chance;
    public final int attempts;

    public BlockDisplayOverrideDefinition(LuaValue definition) {
        fields(definition, "override.onDisplayTick", "action", "chance", "attempts");
        action = definition.get("action");
        if (!action.isfunction()) {
            throw new LuaError("override.onDisplayTick.action must be a function");
        }
        chance = definition.get("chance").isnil() ? 1.0D
                : number(definition.get("chance"), "override.onDisplayTick.chance");
        if (chance < 0 || chance > 1) {
            throw new LuaError("override.onDisplayTick.chance must be between 0 and 1");
        }
        attempts = definition.get("attempts").isnil() ? 1
                : integer(definition.get("attempts"), "override.onDisplayTick.attempts", 1, 64);
        owner = LuaScriptRegistry.getCurrentScriptFile();
    }

    public String getName() {
        return "onDisplayTick";
    }

    public String getOwner() {
        return owner;
    }

    public LuaValue getAction() {
        return action;
    }
}
