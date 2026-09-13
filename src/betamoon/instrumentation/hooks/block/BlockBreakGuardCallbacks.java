package betamoon.instrumentation.hooks.block;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockCallbackOverrides;
import betamoon.luaapi.block.BlockCallback;
import betamoon.luaapi.utils.LuaOverrideCallback;
import org.luaj.vm2.LuaValue;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.block.LuaBlockActionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.src.World;

/** Runtime callbacks loaded only after the game classes are available. */
public final class BlockBreakGuardCallbacks {
    private BlockBreakGuardCallbacks() {
    }

    public static int before(Minecraft minecraft, int x, int y, int z, int face) {
        if (minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return 0;
        }
        World world = minecraft.theWorld;
        BlockDefinition definition = BlockCallbackRegistry.get(world.getBlockId(x, y, z));
        int blockId = world.getBlockId(x, y, z);
        LuaOverrideCallback override = BlockCallbackOverrides.get(blockId, BlockCallback.CAN_BREAK);
        if (world.multiplayerWorld
                || override == null && (definition == null || !definition.callbacks.has(BlockCallback.CAN_BREAK))) {
            return 0;
        }
        try (LuaBlockActionContext context = new LuaBlockActionContext(world, x, y, z, minecraft.thePlayer,
                minecraft.thePlayer.getCurrentEquippedItem(), face, false)) {
            if (override != null) {
                return override.invoke(context,
                        () -> LuaValue.valueOf(definition == null || !definition.callbacks.has(BlockCallback.CAN_BREAK)
                                || definition.callbacks.query(BlockCallback.CAN_BREAK, context, false)),
                        LuaOverrideCallback.Result.BOOLEAN).toboolean() ? 0 : -1;
            }
            return definition.callbacks.query(BlockCallback.CAN_BREAK, context, false) ? 0 : -1;
        }
    }

    public static boolean after(boolean result, int permission) {
        return permission == 0 && result;
    }
}
