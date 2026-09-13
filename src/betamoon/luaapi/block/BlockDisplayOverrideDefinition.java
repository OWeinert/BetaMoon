package betamoon.luaapi.block;

import betamoon.luamodloader.LuaScriptRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;

/** Validated display override declaration, independent of runtime dispatch. */
public final class BlockDisplayOverrideDefinition {
    public final LuaValue action;
    public final String owner;

    public BlockDisplayOverrideDefinition(LuaValue definition) {
        fields(definition, "override.onDisplayTick", "action");
        action = definition.get("action");
        if (!action.isfunction()) {
            throw new LuaError("override.onDisplayTick.action must be a function");
        }
        owner = LuaScriptRegistry.getCurrentScriptFile();
    }
}
