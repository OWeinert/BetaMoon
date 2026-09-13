package betamoon.luaapi.block;

import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.RedstoneDefinition;
import betamoon.tileentity.TileEntityDefinition;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * Resolves and validates structural block handles before registration mutates
 * engine state.
 */
public final class BlockComponents {
    public final TileEntityDefinition tile;
    public final ContainerDefinition container;
    public final ContainerGuiDefinition gui;
    public final RedstoneDefinition redstone;

    public BlockComponents(LuaValue definition) {
        LuaValue tileHandle = definition.get("tileEntity");
        LuaValue containerHandle = definition.get("container");
        LuaValue guiHandle = definition.get("gui");
        if (tileHandle.isnil()) {
            if (!containerHandle.isnil() || !guiHandle.isnil()) {
                throw new LuaError("A structural block requires a tileEntity handle.");
            }
            tile = null;
            container = null;
            gui = null;
            redstone = null;
            return;
        }

        tile = TileEntityApi.tileHandle(tileHandle).definition;
        if (!tile.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            throw new LuaError("A block and its structural tile entity must be declared by the same script.");
        }
        if (containerHandle.isnil() != guiHandle.isnil()) {
            throw new LuaError("A block must provide both container and gui, or neither.");
        }
        container = containerHandle.isnil() ? null : TileEntityApi.containerHandle(containerHandle).definition;
        gui = guiHandle.isnil() ? null : TileEntityApi.guiHandle(guiHandle).definition;
        if (container != null && (container.tileEntity != tile || gui.container != container)) {
            throw new LuaError("Block, tileEntity, container and GUI handles must reference compatible definitions.");
        }
        redstone = parseRedstone(definition.get("redstone"));
    }

    private static RedstoneDefinition parseRedstone(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        if (!value.istable()) {
            throw new LuaError("redstone must be a definition table.");
        }
        LuaValue changed = value.get("onNeighborChanged");
        LuaValue action = LuaValue.NIL;
        if (!changed.isnil()) {
            if (!changed.istable()) {
                throw new LuaError("redstone.onNeighborChanged must be a table.");
            }
            action = changed.get("action");
            if (action.isnil()) {
                throw new LuaError("Definition requires 'action'.");
            }
            if (!action.isfunction()) {
                throw new LuaError("redstone.onNeighborChanged.action must be a function.");
            }
        }
        // Power is handled by BlockRedstoneDefinition; this retains the legacy neighbor
        // callback.
        return new RedstoneDefinition(null, null, action);
    }
}
