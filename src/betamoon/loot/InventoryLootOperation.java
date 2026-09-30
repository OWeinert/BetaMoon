package betamoon.loot;

import betamoon.loot.LootStackDefinition.LootStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.src.IInventory;
import net.minecraft.src.ItemStack;

/** One validated, deterministic inventory mutation planned by a structure loot element. */
public final class InventoryLootOperation {
    public enum SlotMode {
        RANDOM_EMPTY,
        ORDERED_EMPTY,
        EXPLICIT
    }

    public enum ExistingPolicy {
        REQUIRE_EMPTY,
        PRESERVE,
        REPLACE
    }

    public enum OverflowPolicy {
        DISCARD,
        REJECT
    }

    public enum Result {
        APPLIED,
        INCOMPATIBLE_SIZE,
        EXISTING_CONTENTS,
        OVERFLOW
    }

    private final List<FixedStack> fixed;
    private final List<LootStack> generated;
    private final SlotMode slotMode;
    private final List<Integer> explicitSlots;
    private final ExistingPolicy existing;
    private final OverflowPolicy overflow;
    private final long slotSeed;

    private InventoryLootOperation(List<FixedStack> fixed, List<LootStack> generated, SlotMode slotMode,
            List<Integer> explicitSlots, ExistingPolicy existing, OverflowPolicy overflow, long slotSeed) {
        this.fixed = Collections.unmodifiableList(new ArrayList<FixedStack>(fixed));
        this.generated = Collections.unmodifiableList(new ArrayList<LootStack>(generated));
        this.slotMode = slotMode;
        this.explicitSlots = Collections.unmodifiableList(new ArrayList<Integer>(explicitSlots));
        this.existing = existing;
        this.overflow = overflow;
        this.slotSeed = slotSeed;
    }

    public static InventoryLootOperation fixed(List<FixedStack> stacks, ExistingPolicy existing) {
        return new InventoryLootOperation(stacks, Collections.<LootStack>emptyList(), SlotMode.EXPLICIT,
                Collections.<Integer>emptyList(), existing, OverflowPolicy.REJECT, 0L);
    }

    public static InventoryLootOperation random(List<LootStack> stacks, SlotMode slotMode,
            List<Integer> explicitSlots, ExistingPolicy existing, OverflowPolicy overflow, long slotSeed) {
        return new InventoryLootOperation(Collections.<FixedStack>emptyList(), stacks, slotMode, explicitSlots,
                existing, overflow, slotSeed);
    }

    /** Applies after all checks that can fail have completed. */
    public Result apply(IInventory inventory) {
        return fixed.isEmpty() ? applyGenerated(inventory) : applyFixed(inventory);
    }

    private Result applyFixed(IInventory inventory) {
        for (FixedStack entry : fixed) {
            if (!validSlot(inventory, entry.slot) || entry.stack.count > inventory.getInventoryStackLimit()) {
                return Result.INCOMPATIBLE_SIZE;
            }
            if (existing == ExistingPolicy.REQUIRE_EMPTY && inventory.getStackInSlot(entry.slot) != null) {
                return Result.EXISTING_CONTENTS;
            }
        }
        for (FixedStack entry : fixed) {
            ItemStack previous = inventory.getStackInSlot(entry.slot);
            if (existing == ExistingPolicy.PRESERVE && previous != null) {
                continue;
            }
            inventory.setInventorySlotContents(entry.slot, entry.stack.create());
        }
        inventory.onInventoryChanged();
        return Result.APPLIED;
    }

    private Result applyGenerated(IInventory inventory) {
        for (LootStack stack : generated) {
            if (stack.count > inventory.getInventoryStackLimit()) {
                return Result.INCOMPATIBLE_SIZE;
            }
        }
        List<Integer> eligible = eligibleSlots(inventory);
        if (eligible == null) {
            return Result.INCOMPATIBLE_SIZE;
        }
        if (existing == ExistingPolicy.REQUIRE_EMPTY) {
            for (Integer slot : eligible) {
                if (inventory.getStackInSlot(slot.intValue()) != null) {
                    return Result.EXISTING_CONTENTS;
                }
            }
        }
        List<Integer> available = new ArrayList<Integer>();
        for (Integer slot : eligible) {
            if (existing == ExistingPolicy.REPLACE || inventory.getStackInSlot(slot.intValue()) == null) {
                available.add(slot);
            }
        }
        if (overflow == OverflowPolicy.REJECT && generated.size() > available.size()) {
            return Result.OVERFLOW;
        }
        if (slotMode == SlotMode.RANDOM_EMPTY || slotMode == SlotMode.EXPLICIT) {
            Collections.shuffle(available, new Random(slotSeed));
        }
        if (existing == ExistingPolicy.REPLACE) {
            for (Integer slot : eligible) {
                inventory.setInventorySlotContents(slot.intValue(), null);
            }
        }
        int count = Math.min(generated.size(), available.size());
        for (int index = 0; index < count; index++) {
            inventory.setInventorySlotContents(available.get(index).intValue(), generated.get(index).create());
        }
        inventory.onInventoryChanged();
        return Result.APPLIED;
    }

    private List<Integer> eligibleSlots(IInventory inventory) {
        if (slotMode == SlotMode.EXPLICIT) {
            for (Integer slot : explicitSlots) {
                if (!validSlot(inventory, slot.intValue())) {
                    return null;
                }
            }
            return new ArrayList<Integer>(explicitSlots);
        }
        List<Integer> result = new ArrayList<Integer>();
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            result.add(Integer.valueOf(slot));
        }
        return result;
    }

    private static boolean validSlot(IInventory inventory, int slot) {
        return slot >= 0 && slot < inventory.getSizeInventory();
    }

    public static final class FixedStack {
        public final int slot;
        public final LootStack stack;

        public FixedStack(int slot, LootStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }
}
