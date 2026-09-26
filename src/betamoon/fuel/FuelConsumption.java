package betamoon.fuel;

import betamoon.assets.AssetKey;
import net.minecraft.src.IInventory;
import net.minecraft.src.ItemStack;

/** Checked one-item fuel consumption shared by Lua machines. */
public final class FuelConsumption {
    private FuelConsumption() {
    }

    public static int consume(IInventory inventory, int slot, AssetKey setKey) {
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) {
            return 0;
        }
        ItemStack fuel = inventory.getStackInSlot(slot);
        if (fuel == null || fuel.stackSize <= 0) {
            return 0;
        }
        FuelResolution resolution = FuelRegistry.resolve(fuel, setKey);
        if (!resolution.isFuel()) {
            return 0;
        }
        if (fuel.getItem().hasContainerItem()) {
            inventory.setInventorySlotContents(slot, new ItemStack(fuel.getItem().getContainerItem()));
        } else {
            inventory.decrStackSize(slot, 1);
        }
        return resolution.burnTime;
    }
}
