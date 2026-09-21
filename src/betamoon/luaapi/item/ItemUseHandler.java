package betamoon.luaapi.item;

import betamoon.entity.EntitySpawner;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

/** Applies item use policies and owns their transient per-player cooldowns. */
public final class ItemUseHandler {
    private static final Map<EntityPlayer, Map<ItemDefinition, Long>> COOLDOWNS = new WeakHashMap<EntityPlayer, Map<ItemDefinition, Long>>();

    private ItemUseHandler() {
    }

    static void clear(ItemDefinition definition) {
        for (Map<ItemDefinition, Long> entries : COOLDOWNS.values()) {
            entries.remove(definition);
        }
    }

    public static boolean suppressDefaultUse(ItemStack stack, World world, EntityPlayer player) {
        ItemDefinition def = ItemCallbackRegistry.get(stack.itemID);
        return def != null && (!ready(def, player, world) || stack.stackSize < def.use.consume
                || def.use.ammunition >= 0 && ammunitionSlot(def, stack, player) < 0);
    }

    public static ItemStack finishUse(ItemStack before, ItemStack after, int oldCount, World world,
            EntityPlayer player) {
        ItemDefinition def = ItemCallbackRegistry.get(before.itemID);
        if (def == null || world.multiplayerWorld) {
            return after;
        }
        boolean used = after != before || after != null && after.stackSize != oldCount;
        if (def.use.hasProjectile()) {
            int slot = def.use.ammunition < 0 ? -1 : ammunitionSlot(def, before, player);
            if (def.use.ammunition >= 0 && slot < 0) {
                return after;
            }
            if (!launch(def, world, player)) {
                return after;
            }
            if (slot >= 0) {
                ItemStack ammunition = player.inventory.mainInventory[slot];
                if (ammunition == before) {
                    oldCount--;
                }
                ammunition.stackSize--;
                if (ammunition.stackSize == 0) {
                    player.inventory.mainInventory[slot] = null;
                }
            }
            world.playSoundAtEntity(player, "random.bow", 1, 1);
            used = true;
        }
        if (!def.use.food && (def.use.consume > 0 || used)) {
            if (after == before && before.stackSize == oldCount) {
                before.stackSize -= def.use.consume;
            }
            used = true;
        }
        if (used) {
            markUsed(def, player, world);
            if (def.use.remainder >= 0) {
                ItemStack rem = new ItemStack(def.use.remainder, 1, 0);
                if (after == null || after.stackSize <= 0) {
                    return rem;
                }
                if (!player.inventory.addItemStackToInventory(rem)) {
                    player.dropPlayerItem(rem);
                }
            }
        }
        return after;
    }

    private static boolean launch(ItemDefinition definition, World world, EntityPlayer player) {
        if (definition.use.customProjectile != null) {
            float yaw = player.rotationYaw;
            float pitch = player.rotationPitch;
            double x = player.posX - Math.cos(Math.toRadians(yaw)) * 0.16;
            double y = player.posY + player.getEyeHeight() - 0.1;
            double z = player.posZ - Math.sin(Math.toRadians(yaw)) * 0.16;
            try {
                return EntitySpawner.spawn(world, definition.use.customProjectile, x, y, z, yaw, pitch, player)
                        .entity != null;
            } catch (IllegalArgumentException missingType) {
                return false;
            }
        }
        Entity projectile = definition.use.projectile.create(world, player);
        return world.entityJoinedWorld(projectile);
    }

    /**
     * Keeps declared consumption available when the held item is also ammunition.
     */
    private static int ammunitionSlot(ItemDefinition definition, ItemStack held, EntityPlayer player) {
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            ItemStack candidate = player.inventory.mainInventory[slot];
            int reserved = candidate == held ? definition.use.consume : 0;
            if (candidate != null && candidate.itemID == definition.use.ammunition && candidate.stackSize > reserved) {
                return slot;
            }
        }
        return -1;
    }

    static boolean ready(ItemDefinition definition, EntityPlayer player, World world) {
        Map<ItemDefinition, Long> entries = COOLDOWNS.get(player);
        Long end = entries == null ? null : entries.get(definition);
        return end == null || world.getWorldTime() >= end.longValue();
    }

    static void markUsed(ItemDefinition definition, EntityPlayer player, World world) {
        if (definition.use.cooldown == 0) {
            return;
        }
        Map<ItemDefinition, Long> entries = COOLDOWNS.get(player);
        if (entries == null) {
            entries = new HashMap<ItemDefinition, Long>();
            COOLDOWNS.put(player, entries);
        }
        entries.put(definition, world.getWorldTime() + definition.use.cooldown);
    }

}
