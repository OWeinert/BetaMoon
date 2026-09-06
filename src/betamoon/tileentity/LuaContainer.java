package betamoon.tileentity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.src.Container;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ICrafting;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Slot;

/** Generated Minecraft container backed by a standalone Lua container definition. */
public final class LuaContainer extends Container {
    private final LuaTileEntity entity;
    private final ContainerDefinition definition;
    private final List syncedFields = new ArrayList();
    private final List lastValues = new ArrayList();
    private final int tileSlotCount;

    public LuaContainer(InventoryPlayer player, LuaTileEntity entity, ContainerDefinition definition) {
        this.entity = entity;
        this.definition = definition;
        for (int i = 0; i < definition.slots.size(); i++) {
            ContainerDefinition.SlotDefinition slot = (ContainerDefinition.SlotDefinition) definition.slots.get(i);
            addSlot(slot.outputOnly ? new OutputSlot(entity, slot.index, slot.x, slot.y)
                : new Slot(entity, slot.index, slot.x, slot.y));
        }
        tileSlotCount = definition.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9,
                    definition.playerX + column * 18, definition.playerY + row * 18));
            }
        }
        if (definition.includeHotbar) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column, definition.playerX + column * 18,
                    definition.playerY + 58));
            }
        }
        Iterator fields = definition.tileEntity.fields.values().iterator();
        while (fields.hasNext()) {
            TileEntityDefinition.Field field = (TileEntityDefinition.Field) fields.next();
            if (field.sync && ("integer".equals(field.type) || "boolean".equals(field.type))) {
                syncedFields.add(field.name); lastValues.add(Integer.valueOf(Integer.MIN_VALUE));
            }
        }
    }

    public boolean isUsableByPlayer(EntityPlayer player) { return entity.canInteractWith(player); }

    public void updateCraftingResults() {
        super.updateCraftingResults();
        for (int fieldIndex = 0; fieldIndex < syncedFields.size(); fieldIndex++) {
            int value = entity.getDataInt((String) syncedFields.get(fieldIndex));
            if (((Integer) lastValues.get(fieldIndex)).intValue() == value) continue;
            for (int listener = 0; listener < field_20121_g.size(); listener++) {
                ((ICrafting) field_20121_g.get(listener)).func_20158_a(this, fieldIndex, value);
            }
            lastValues.set(fieldIndex, Integer.valueOf(value));
        }
    }

    public void func_20112_a(int id, int value) {
        if (id >= 0 && id < syncedFields.size()) entity.setSyncedData((String) syncedFields.get(id), value);
    }

    /** Implements ordinary shift-click transfer between tile and player slots. */
    public ItemStack getStackInSlot(int index) {
        if (index < 0 || index >= slots.size()) return null;
        Slot slot = (Slot) slots.get(index);
        if (!slot.getHasStack()) return null;
        ItemStack source = slot.getStack();
        ItemStack copy = source.copy();
        if (index < tileSlotCount) func_28125_a(source, tileSlotCount, slots.size(), true);
        else transferToTile(source);
        if (source.stackSize == 0) slot.putStack(null); else slot.onSlotChanged();
        return source.stackSize == copy.stackSize ? null : copy;
    }

    /** Honors output-only slots while moving a player stack into the tile inventory. */
    private void transferToTile(ItemStack source) {
        for (int i = 0; i < tileSlotCount && source.stackSize > 0; i++) {
            Slot target = (Slot) slots.get(i);
            if (!target.isItemValid(source)) continue;
            ItemStack current = target.getStack();
            if (current != null && current.isItemEqual(source)) {
                int room = Math.min(target.getSlotStackLimit(), current.getMaxStackSize()) - current.stackSize;
                int moved = Math.min(room, source.stackSize);
                if (moved > 0) { current.stackSize += moved; source.stackSize -= moved; target.onSlotChanged(); }
            }
        }
        for (int i = 0; i < tileSlotCount && source.stackSize > 0; i++) {
            Slot target = (Slot) slots.get(i);
            if (!target.isItemValid(source) || target.getHasStack()) continue;
            int moved = Math.min(source.stackSize, target.getSlotStackLimit());
            ItemStack inserted = source.copy(); inserted.stackSize = moved;
            target.putStack(inserted); source.stackSize -= moved;
        }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(LuaTileEntity inventory, int index, int x, int y) { super(inventory, index, x, y); }
        public boolean isItemValid(ItemStack stack) { return false; }
    }
}
