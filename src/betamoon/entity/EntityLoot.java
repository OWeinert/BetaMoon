package betamoon.entity;

import net.minecraft.src.Entity;
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

    private static boolean canDrop(Entity entity) {
        return entity != null && entity.worldObj != null && !entity.worldObj.multiplayerWorld;
    }
}
