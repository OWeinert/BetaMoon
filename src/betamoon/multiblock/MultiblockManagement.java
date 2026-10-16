package betamoon.multiblock;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Validated managed discovery and activation policy; no live Lua contexts. */
public final class MultiblockManagement {
    public final boolean manual;
    public final String seeds;
    public final List<Integer> facings;
    public final Set<Integer> seedBlocks;
    public final boolean automatic;
    public final int item;
    public final int itemDamage;
    public final String targets;
    public final boolean adjacent;
    public final int damageCost;
    public final int consumeCost;
    public final String fillCells;
    public final int fillBlock;
    public final String facingState;

    public MultiblockManagement(boolean manual, String seeds, List<Integer> facings, Set<Integer> seedBlocks,
            boolean automatic, int item, int itemDamage, String targets, boolean adjacent, int damageCost,
            int consumeCost, String fillCells, int fillBlock, String facingState) {
        this.manual = manual;
        this.seeds = seeds;
        this.facings = Collections.unmodifiableList(facings);
        this.seedBlocks = Collections.unmodifiableSet(seedBlocks);
        this.automatic = automatic;
        this.item = item;
        this.itemDamage = itemDamage;
        this.targets = targets;
        this.adjacent = adjacent;
        this.damageCost = damageCost;
        this.consumeCost = consumeCost;
        this.fillCells = fillCells;
        this.fillBlock = fillBlock;
        this.facingState = facingState;
    }
}
