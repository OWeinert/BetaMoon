package betamoon.tileentity;

import betamoon.assets.AssetKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable slot layout for a Lua container. */
public final class ContainerDefinition {
    public final String name;
    public final String owner;
    public final TileEntityDefinition tileEntity;
    public final List<SlotDefinition> slots;
    public final int playerX;
    public final int playerY;
    public final boolean includeHotbar;

    public ContainerDefinition(String name, String owner, TileEntityDefinition tileEntity, List<SlotDefinition> slots,
            int playerX, int playerY, boolean includeHotbar) {
        this.name = name;
        this.owner = owner;
        this.tileEntity = tileEntity;
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
        this.playerX = playerX;
        this.playerY = playerY;
        this.includeHotbar = includeHotbar;
    }

    /** One visible tile-inventory slot. */
    public static final class SlotDefinition {
        public final String name;
        public final int index;
        public final int x;
        public final int y;
        public final boolean outputOnly;
        public final AssetKey acceptedFuelSet;

        public SlotDefinition(String name, int index, int x, int y, boolean outputOnly) {
            this(name, index, x, y, outputOnly, null);
        }

        public SlotDefinition(String name, int index, int x, int y, boolean outputOnly, AssetKey acceptedFuelSet) {
            this.name = name;
            this.index = index;
            this.x = x;
            this.y = y;
            this.outputOnly = outputOnly;
            this.acceptedFuelSet = acceptedFuelSet;
        }
    }
}
