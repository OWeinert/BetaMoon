package betamoon.luaapi.client.ui;

import betamoon.client.tick.ClientTickEvents;
import betamoon.gui.inventory.GuiInventoryProfile;
import betamoon.gui.inventory.GuiInventoryRuntime;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenDefinition;
import betamoon.gui.graphics.GuiResourceDefinitions;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Inventory presentation metadata and detached callback data; native slots are
 * never exposed as mutable views.
 */
public final class GuiInventoryLuaAccess {
    static LuaTable registry(String owner) {
        LuaTable access = new LuaTable();
        access.set("add", GuiUiApi.owned(owner, args -> {
            GuiInventoryProfile profile = GuiInventoryParser.parse(owner, args.arg(args.arg1() == access ? 2 : 1));
            if (profile.screen != null) {
                GuiScreenDefinition screen = GuiClientUiRuntime.screens().getRequired(profile.screen);
                if (!screen.owner.equals(owner)) {
                    throw new IllegalArgumentException("Inventory screen belongs to another owner");
                }
                if (profile.backgroundImage != null) {
                    GuiResourceDefinitions.Resource resource = screen.resources.entries.get(profile.backgroundImage);
                    if (resource == null || resource.kind != GuiResourceDefinitions.Kind.IMAGE
                            && resource.kind != GuiResourceDefinitions.Kind.DRAWABLE) {
                        throw new IllegalArgumentException(
                                "Inventory background requires a declared image or drawable");
                    }
                }
            }
            GuiInventoryRuntime.registry().add(profile);
            return metadata(profile);
        }));
        for (String method : new String[]{"get", "getRequired"}) {
            access.set(method, GuiUiApi.owned(owner, args -> {
                String key = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1),
                        "inventory profile key");
                GuiInventoryProfile profile = method.equals("get")
                        ? GuiInventoryRuntime.registry().get(key)
                        : GuiInventoryRuntime.registry().getRequired(key);
                return profile == null ? LuaValue.NIL : metadata(profile);
            }));
        }
        access.set("activate", GuiUiApi.owned(owner, args -> {
            ClientTickEvents.requireAvailable();
            String key = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1), "inventory profile key");
            return GuiPresentationLuaAccess.activation(owner, GuiInventoryRuntime.registry().activate(owner, key));
        }));
        return access;
    }

    private static LuaTable metadata(GuiInventoryProfile profile) {
        LuaTable value = new LuaTable();
        value.set("key", profile.key);
        value.set("priority", profile.priority);
        if (profile.screen != null) {
            value.set("screen", profile.screen);
        }
        return value;
    }

    /**
     * The callback retains only detached layout values, even when the expired Lua
     * table is kept.
     */
    public static LuaTable context(LuaCallbackScope scope, GuiScopedSnapshot layout) {
        return layout.access(scope, "layout");
    }

    private GuiInventoryLuaAccess() {
    }
}
