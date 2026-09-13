package betamoon.luaapi.block;

import betamoon.luaapi.utils.LuaCallbackDispatcher;
import betamoon.luaapi.utils.LuaCallbackDeclarations;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;

/**
 * Compiled optional capabilities for custom blocks. No Lua lookup on static
 * queries.
 */
public final class BlockDefinition {
    public final BlockStateSchema state;
    public final BlockDropDefinition drops;
    public final BlockVisualDefinition visual;
    public final LuaCallbackDispatcher<BlockCallback> callbacks;
    public final BlockPlacementDefinition placement;
    public final BlockShapeDefinition shapes;
    public final BlockRedstoneDefinition redstone;
    public final boolean normalCube;
    public final int fireSpread;
    public final int fireBurn;
    public BlockDefinition(LuaValue def) {
        drops = new BlockDropDefinition(def.get("drops"));
        visual = new BlockVisualDefinition(def);
        state = new BlockStateSchema(def.get("state"));
        LuaCallbackDeclarations.Builder<BlockCallback> callbackBuilder = LuaCallbackDeclarations
                .builder(BlockCallback.class, "block " + def.get("key"));
        for (BlockCallback callback : BlockCallback.values()) {
            if (callback != BlockCallback.TICK && callback != BlockCallback.INPUT_CHANGED) {
                callbackBuilder.parse(def, callback);
            }
        }
        placement = new BlockPlacementDefinition(def.get("placement"), state);
        shapes = new BlockShapeDefinition(def);
        boolean fullCube = shapes.hasFullCubeCollision() && visual.renderType == 0
                && (visual.bounds == null || visual.bounds.isFullCube());
        normalCube = bool(def.get("normalCube"), "normalCube", fullCube);
        redstone = new BlockRedstoneDefinition(def, state, placement);
        if (!def.get("redstone").isnil()) {
            callbackBuilder.parse(def.get("redstone"), BlockCallback.INPUT_CHANGED);
            if (def.get("tileEntity").isnil() && !def.get("redstone").get("onNeighborChanged").isnil()) {
                throw error("redstone.onNeighborChanged", "legacy tile callback requires tileEntity; "
                        + "use the block's top-level onNeighborChanged for a normal block");
            }
        }
        int spread = 0;
        int burn = 0;
        if (!def.get("fire").isnil()) {
            fields(def.get("fire"), "fire", "spread", "burn");
            spread = integer(def.get("fire").get("spread"), "fire.spread", 0, 300);
            burn = integer(def.get("fire").get("burn"), "fire.burn", 0, 300);
        }
        fireSpread = spread;
        fireBurn = burn;
        callbacks = new LuaCallbackDispatcher<BlockCallback>(callbackBuilder.build());
    }

    public int facing(int metadata) {
        return placement.horizontal ? BlockFaces.side(state.get(metadata, "facing").tojstring(), -1) : -1;
    }

}
