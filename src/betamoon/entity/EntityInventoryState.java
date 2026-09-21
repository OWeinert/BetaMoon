package betamoon.entity;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IInventory;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;

/** Preserves container and logical equipment contents even while a definition is unavailable. */
public final class EntityInventoryState implements IInventory {
    private final ItemStack[] contents = new ItemStack[54];
    private final Map<EntityEquipmentDefinition.Slot, ItemStack> equipment =
            new EnumMap<>(EntityEquipmentDefinition.Slot.class);
    private Entity owner;
    private EntityTypeDefinition definition;

    public EntityInventoryState bind(Entity owner, EntityTypeDefinition definition) {
        this.owner = owner;
        this.definition = definition;
        return this;
    }

    public ItemStack equipment(EntityEquipmentDefinition.Slot slot) {
        return equipment.get(slot);
    }

    public void equipment(EntityEquipmentDefinition.Slot slot, ItemStack stack) {
        if (stack == null) {
            equipment.remove(slot);
        } else {
            equipment.put(slot, stack);
        }
    }

    public void dropContents(Entity entity, EntityTypeDefinition type) {
        if (type.inventory != null && type.inventory.dropOnDeath) {
            for (int index = 0; index < type.inventory.size; index++) {
                drop(entity, contents[index]);
                contents[index] = null;
            }
        }
        if (type.equipment != null && type.equipment.dropOnDeath) {
            for (EntityEquipmentDefinition.Slot slot : type.equipment.slots) {
                drop(entity, equipment.remove(slot));
            }
        }
    }

    private static void drop(Entity entity, ItemStack stack) {
        if (stack != null && stack.stackSize > 0 && entity.worldObj != null && !entity.worldObj.multiplayerWorld) {
            entity.entityDropItem(stack, entity.height * 0.5f);
        }
    }

    public void write(NBTTagCompound parent) {
        NBTTagList inventory = new NBTTagList();
        for (int index = 0; index < contents.length; index++) {
            if (contents[index] != null) {
                NBTTagCompound item = new NBTTagCompound();
                item.setByte("Slot", (byte) index);
                contents[index].writeToNBT(item);
                inventory.setTag(item);
            }
        }
        parent.setTag("BetaMoonInventory", inventory);
        NBTTagList equipped = new NBTTagList();
        for (Map.Entry<EntityEquipmentDefinition.Slot, ItemStack> entry : equipment.entrySet()) {
            if (entry.getValue() != null) {
                NBTTagCompound item = new NBTTagCompound();
                item.setString("Slot", entry.getKey().luaName);
                entry.getValue().writeToNBT(item);
                equipped.setTag(item);
            }
        }
        parent.setTag("BetaMoonEquipment", equipped);
    }

    public void read(NBTTagCompound parent) {
        clear();
        NBTTagList inventory = parent.getTagList("BetaMoonInventory");
        for (int index = 0; index < inventory.tagCount(); index++) {
            NBTTagCompound item = (NBTTagCompound) inventory.tagAt(index);
            int slot = item.getByte("Slot") & 255;
            if (slot < contents.length) {
                contents[slot] = readStack(item);
            }
        }
        NBTTagList equipped = parent.getTagList("BetaMoonEquipment");
        for (int index = 0; index < equipped.tagCount(); index++) {
            NBTTagCompound item = (NBTTagCompound) equipped.tagAt(index);
            try {
                ItemStack stack = readStack(item);
                if (stack != null) {
                    equipment.put(EntityEquipmentDefinition.Slot.parse(item.getString("Slot"), "saved equipment"),
                            stack);
                }
            } catch (RuntimeException ignored) {
                // Preserve valid slots and ignore corrupt or future slot identifiers safely.
            }
        }
    }

    private static ItemStack readStack(NBTTagCompound tag) {
        ItemStack stack = new ItemStack(tag);
        if (stack.itemID <= 0 || stack.itemID >= Item.itemsList.length || Item.itemsList[stack.itemID] == null
                || stack.stackSize <= 0 || stack.getItemDamage() < 0 || stack.getItemDamage() > 32767) {
            return null;
        }
        int maximum = Math.min(64, stack.getMaxStackSize());
        return stack.stackSize <= maximum ? stack : null;
    }

    private void clear() {
        for (int index = 0; index < contents.length; index++) {
            contents[index] = null;
        }
        equipment.clear();
    }

    @Override
    public int getSizeInventory() {
        return definition == null || definition.inventory == null ? 0 : definition.inventory.size;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < getSizeInventory() ? contents[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) {
            return null;
        }
        if (stack.stackSize <= amount) {
            contents[slot] = null;
            onInventoryChanged();
            return stack;
        }
        ItemStack removed = stack.splitStack(amount);
        if (stack.stackSize == 0) {
            contents[slot] = null;
        }
        onInventoryChanged();
        return removed;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getSizeInventory()) {
            throw new IndexOutOfBoundsException("Inventory slot " + slot);
        }
        contents[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        onInventoryChanged();
    }

    @Override
    public String getInvName() {
        return definition == null || definition.inventory == null ? "Entity" : definition.inventory.title;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void onInventoryChanged() {
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return owner != null && !owner.isDead && player != null && player.worldObj == owner.worldObj
                && player.getDistanceSqToEntity(owner) <= 64;
    }
}
