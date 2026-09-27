package betamoon.luaapi.block;

import betamoon.luaapi.resource.OverrideManager;
import betamoon.luaapi.utils.LuaOverrideCallback;
import betamoon.luaapi.utils.LuaOverrideActionDefinition;
import betamoon.luaapi.utils.LuaOverrideLayers;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.src.Block;
import net.minecraft.src.World;

/**
 * Script-owned display overrides. Property layering supplies the effective
 * callback for each block.
 */
public final class BlockDisplayTickOverrides {
    private static final Map<Integer, LuaOverrideCallback> CALLBACKS = new ConcurrentHashMap<Integer, LuaOverrideCallback>();

    public static final OverrideManager.PropertyAdapter<Block, LuaOverrideLayers<BlockDisplayOverrideDefinition>> ADAPTER = new OverrideManager.PropertyAdapter<Block, LuaOverrideLayers<BlockDisplayOverrideDefinition>>() {
        @SuppressWarnings("unchecked")
        public LuaOverrideLayers<BlockDisplayOverrideDefinition> read(Block target) {
            LuaOverrideCallback callback = CALLBACKS.get(((Block) target).blockID);
            return callback == null ? null : (LuaOverrideLayers<BlockDisplayOverrideDefinition>) callback.layers();
        }

        public void write(Block target, LuaOverrideLayers<BlockDisplayOverrideDefinition> value) {
            int id = ((Block) target).blockID;
            if (value == null) {
                CALLBACKS.remove(id);
            } else {
                CALLBACKS.put(id, new LuaOverrideCallback(value));
            }
        }
    };

    private BlockDisplayTickOverrides() {
    }

    /**
     * Called by the world dispatch hook with the original virtual-call arguments.
     */
    public static void display(Block block, World world, int x, int y, int z, Random random) {
        LuaOverrideCallback callback = CALLBACKS.get(block.blockID);
        if (callback == null || !callback.isEnabled()) {
            block.randomDisplayTick(world, x, y, z, random);
            return;
        }
        LuaOverrideActionDefinition effective = callback.effectiveDefinition();
        BlockDisplayOverrideDefinition definition = effective instanceof BlockDisplayOverrideDefinition
                ? (BlockDisplayOverrideDefinition) effective : null;
        int attempts = definition == null ? 1 : definition.attempts;
        double chance = definition == null ? 1.0D : definition.chance;
        for (int index = 0; index < attempts; index++) {
            if (random.nextDouble() <= chance) {
                BlockTickRegistry.invokeDisplayOverride(callback, block, world, x, y, z, random);
                if (!callback.isEnabled()) {
                    return;
                }
            }
        }
    }
}
