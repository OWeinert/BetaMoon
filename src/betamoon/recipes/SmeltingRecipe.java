package betamoon.recipes;

import java.util.Map;
import net.minecraft.src.IRecipe;
import net.minecraft.src.InventoryCrafting;
import net.minecraft.src.ItemStack;

public final class SmeltingRecipe implements IRecipe {
    private final Map smeltingMap;
    private Map.Entry smeltingRecipe;
    private int inputId;
    private ItemStack output;

    public SmeltingRecipe(Map smeltingMap, Map.Entry smeltingRecipe) {
        this.smeltingMap = smeltingMap;
        this.smeltingRecipe = smeltingRecipe;
        this.inputId = ((Integer) smeltingRecipe.getKey()).intValue();
        this.output = (ItemStack) smeltingRecipe.getValue();
    }

    /**
     * Returns the raw smelting entry (input id -> output stack).
     */
    public Map.Entry getSmeltingEntry() {
        return this.smeltingRecipe;
    }

    /**
     * Returns the input item or block id for this smelting recipe.
     */
    public int getInputId() {
        return this.inputId;
    }

    public ItemStack getOutput() {
        return this.output;
    }

    /** Returns whether this wrapper's input is still present in the furnace registry. */
    public boolean isRegistered() {
        return this.smeltingMap.containsKey(Integer.valueOf(this.inputId));
    }

    /**
     * Updates the input id and keeps the furnace recipe map in sync.
     *
     * @param newInputId new input item or block id
     * @return true when the input id was updated
     */
    public boolean setInputId(int newInputId) {
        if (this.inputId == newInputId) {
            return true;
        }
        Integer newKey = Integer.valueOf(newInputId);
        if (this.smeltingMap.containsKey(newKey)) {
            return false;
        }
        Integer oldKey = Integer.valueOf(this.inputId);
        this.smeltingMap.remove(oldKey);
        this.smeltingMap.put(newKey, this.output);
        this.inputId = newInputId;
        this.smeltingRecipe = findEntryForKey(newKey);
        return true;
    }

    /**
     * Updates the output stack and keeps the furnace recipe map in sync.
     *
     * @param newOutput new output stack
     */
    public void setOutput(ItemStack newOutput) {
        this.output = newOutput;
        this.smeltingMap.put(Integer.valueOf(this.inputId), newOutput);
        this.smeltingRecipe = findEntryForKey(Integer.valueOf(this.inputId));
    }

    /**
     * Removes this smelting recipe from the furnace recipe map.
     *
     * @return true when the recipe was removed
     */
    public boolean removeFromFurnace() {
        Integer key = Integer.valueOf(this.inputId);
        if (!this.smeltingMap.containsKey(key)) {
            return false;
        }
        this.smeltingMap.remove(key);
        return true;
    }

    /**
     * Replaces the input id and output stack in one call.
     *
     * @param newInputId new input item or block id
     * @param newOutput new output stack
     * @return true when the recipe was updated
     */
    public boolean replaceRecipe(int newInputId, ItemStack newOutput) {
        Integer newKey = Integer.valueOf(newInputId);
        Integer oldKey = Integer.valueOf(this.inputId);
        if (newKey.equals(oldKey)) {
            setOutput(newOutput);
            return true;
        }
        if (this.smeltingMap.containsKey(newKey)) {
            return false;
        }
        this.smeltingMap.remove(oldKey);
        this.smeltingMap.put(newKey, newOutput);
        this.inputId = newInputId;
        this.output = newOutput;
        this.smeltingRecipe = findEntryForKey(newKey);
        return true;
    }

    private Map.Entry findEntryForKey(Integer key) {
        for (java.util.Iterator it = this.smeltingMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            if (key.equals(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    /*
    ----------------------------------------------------------------------
        This is a dummy wrapper for use in RecipeModificationHandler.
        Therefore the IRecipe functions will never be called for this
        class and return dummy values.
    ----------------------------------------------------------------------
    */

    public boolean matches(InventoryCrafting var1) {
        return false;
    }

    public ItemStack getCraftingResult(InventoryCrafting var1) {
        return this.output == null ? null : this.output.copy();
    }

    public int getRecipeSize() {
        return 0;
    }

    public ItemStack getRecipeOutput() {
        return this.output;
    }
}
