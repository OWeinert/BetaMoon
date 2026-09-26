package betamoon.luaapi.item;

import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Owns item callback registration and routes engine events into invocation
 * contexts.
 */
public final class ItemCallbackRegistry {
    private static final Map<Integer, ItemDefinition> DEFINITIONS = new HashMap<Integer, ItemDefinition>();

    private ItemCallbackRegistry() {
    }

    public static ItemDefinition get(int id) {
        return DEFINITIONS.get(id);
    }

    public static int registeredCount() {
        return DEFINITIONS.size();
    }

    public static void install(final int id, final ItemDefinition def) {
        DEFINITIONS.put(id, def);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                if (DEFINITIONS.get(id) == def) {
                    DEFINITIONS.remove(id);
                }
                ItemUseHandler.clear(def);
            }
        });
    }

    public static InteractionOutcome interact(ItemCallback callback, ItemStack stack, EntityPlayer player, World world,
            int x, int y, int z, int face, Entity target) {
        ItemDefinition def = get(stack.itemID);
        if (def == null || !def.callbacks.has(callback)) {
            return InteractionOutcome.PASS;
        }
        if (world.multiplayerWorld) {
            return InteractionOutcome.HANDLED;
        }
        if (!ItemUseHandler.ready(def, player, world)) {
            ItemInteractionRouting.deny();
            return InteractionOutcome.DENY;
        }
        try (LuaItemActionContext ctx = new LuaItemActionContext(world, x, y, z, player, stack, face, true)) {
            if (target != null) {
                LuaTable value = new LuaTable();
                value.set("kind", "entity");
                value.set("entity", ctx.entityAccess(target));
                ctx.set("target", value);
            } else if (callback == ItemCallback.USE) {
                LuaTable value = new LuaTable();
                value.set("kind", "miss");
                ctx.set("target", value);
            }
            InteractionOutcome result = def.callbacks.interaction(callback, ctx);
            if (stack.stackSize <= 0 && player.getCurrentEquippedItem() == stack) {
                player.inventory.mainInventory[player.inventory.currentItem] = null;
            }
            if (result == InteractionOutcome.DENY) {
                ItemInteractionRouting.deny();
            }
            if (result == InteractionOutcome.HANDLED) {
                ItemUseHandler.markUsed(def, player, world);
            }
            return result;
        }
    }

    public static void event(ItemCallback callback, ItemStack stack, World world, Entity entity, int x, int y, int z,
            boolean selected, int slot) {
        ItemDefinition def = get(stack.itemID);
        if (def == null || !def.callbacks.has(callback) || world.multiplayerWorld) {
            return;
        }
        if (callback == ItemCallback.INVENTORY_TICK
                && (world.getWorldTime() % def.interval != 0 || !def.tickWhen.accepts(selected))) {
            return;
        }
        EntityPlayer player = entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
        try (LuaItemActionContext ctx = new LuaItemActionContext(world, x, y, z, player, stack, -1, true)) {
            ctx.set("selected", LuaValue.valueOf(selected));
            ctx.set("slot", slot);
            if (entity != null) {
                ctx.set("entity", ctx.entityAccess(entity));
            }
            def.callbacks.call(callback, ctx, LuaValue.NIL);
        }
    }

    public static void hit(ItemStack stack, EntityLiving target, EntityLiving attacker) {
        ItemDefinition def = get(stack.itemID);
        if (def == null || !def.callbacks.has(ItemCallback.HIT_ENTITY) || target.worldObj.multiplayerWorld) {
            return;
        }
        EntityPlayer player = attacker instanceof EntityPlayer ? (EntityPlayer) attacker : null;
        try (LuaItemActionContext ctx = new LuaItemActionContext(target.worldObj, MathHelper.floor_double(target.posX),
                MathHelper.floor_double(target.posY), MathHelper.floor_double(target.posZ), player, stack, -1, true)) {
            ctx.set("entity", ctx.entityAccess(target));
            def.callbacks.call(ItemCallback.HIT_ENTITY, ctx, LuaValue.NIL);
        }
    }

    public static boolean canHarvest(int itemId, Block block, int metadata, boolean fallback) {
        ItemDefinition definition = get(itemId);
        if (definition == null) {
            return fallback;
        }
        Boolean declared = definition.tool.harvest(block, metadata);
        boolean result = declared == null ? fallback : declared.booleanValue();
        return definition.callbacks.has(ItemCallback.CAN_HARVEST)
                ? definition.callbacks.query(ItemCallback.CAN_HARVEST, miningContext(itemId, block, metadata), result)
                : result;
    }

    public static float miningSpeed(ItemStack stack, Block block, int metadata, float fallback) {
        ItemDefinition definition = get(stack.itemID);
        if (definition == null) {
            return fallback;
        }
        float speed = definition.tool.efficiency != null && (definition.tool.effective(block, metadata)
                || (definition.tool.classes.isEmpty() && forge.ForgeHooks.isToolEffective(stack, block, metadata)))
                        ? definition.tool.efficiency.floatValue()
                        : fallback;
        return definition.callbacks.has(ItemCallback.MINING_SPEED)
                ? definition.callbacks.numberQuery(ItemCallback.MINING_SPEED,
                        miningContext(stack.itemID, block, metadata), speed)
                : speed;
    }

    private static LuaTable miningContext(int itemId, Block block, int metadata) {
        LuaTable context = new LuaTable();
        LuaTable target = new LuaTable();
        target.set("id", block.blockID);
        target.set("damage", metadata);
        context.set("block", target);
        context.set("itemId", itemId);
        return context;
    }
}
