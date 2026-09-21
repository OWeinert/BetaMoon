package betamoon.entity;

import net.minecraft.src.Entity;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

/** Shared server-side item emission for typed entity loot and direct stack drops. */
public final class EntityLoot {
    private EntityLoot() {
    }

    public static boolean dropLoot(Entity entity) {
        if (!(entity instanceof TypedEntity) || entity instanceof LuaEntityPart) {
            return false;
        }
        EntityInstanceState state = ((TypedEntity) entity).entityState();
        EntityTypeDefinition definition = state.definition();
        if (definition == null || !canDrop(entity)) {
            return false;
        }

        definition.drops.drop(entity);
        state.dropContents(entity, definition);
        return true;
    }

    public static boolean dropItem(Entity entity, ItemStack stack) {
        if (!canDrop(entity) || stack == null || stack.stackSize <= 0) {
            return false;
        }
        entity.entityDropItem(stack, entity.height * 0.5F);
        return true;
    }

    static void dropCount(Entity entity, int itemId, int count, int damage) {
        if (!canDrop(entity) || itemId <= 0 || itemId >= Item.itemsList.length
                || Item.itemsList[itemId] == null || count <= 0) {
            return;
        }
        int stackLimit = Math.min(64, new ItemStack(itemId, 1, damage).getMaxStackSize());
        if (stackLimit <= 0) {
            return;
        }

        int remaining = count;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, stackLimit);
            entity.entityDropItem(new ItemStack(itemId, stackSize, damage), entity.height * 0.5F);
            remaining -= stackSize;
        }
    }

    private static boolean canDrop(Entity entity) {
        return entity != null && entity.worldObj != null && !entity.worldObj.multiplayerWorld;
    }
}
