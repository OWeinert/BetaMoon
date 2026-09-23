package betamoon.tileentity;

import betamoon.BetaMoonCommon;

import betamoon.assets.AssetKey;
import betamoon.fuel.FuelConsumption;
import betamoon.fuel.FuelRegistry;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.LuaBlockActionContext;
import betamoon.luaapi.fuel.FuelsApi;
import betamoon.luaapi.tileentity.LuaTileDataAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.IInventory;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.TileEntity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Persistent, inventory-capable tile entity whose behavior is defined by Lua.
 */
public final class LuaTileEntity extends TileEntity implements IInventory {
    private String typeName;
    private ItemStack[] inventory = new ItemStack[0];
    private final TileDataStore data = new TileDataStore();
    private int tickCounter;
    private boolean tickEnabled = true;
    private boolean inventoryActionEnabled = true;
    private boolean notifyingInventory;
    private boolean committingRecipe;

    /** Required by Minecraft's NBT factory. */
    public LuaTileEntity() {
    }

    public LuaTileEntity(String typeName) {
        this.typeName = typeName;
        initializeDefinition();
    }

    public TileEntityDefinition getDefinition() {
        return TileEntityRegistry.getTileEntity(typeName);
    }

    private void initializeDefinition() {
        TileEntityDefinition definition = getDefinition();
        if (definition == null) {
            return;
        }
        if (inventory.length != definition.slots.size()) {
            inventory = new ItemStack[definition.slots.size()];
        }
        data.initialize(definition);
    }

    @Override
    public void updateEntity() {
        TileEntityDefinition definition = getDefinition();
        if (definition == null || definition.tickAction.isnil() || !tickEnabled || worldObj == null
                || worldObj.multiplayerWorld) {
            return;
        }
        tickCounter++;
        boolean run = definition.randomTicks
                ? worldObj.rand.nextDouble() < definition.randomTickChance
                : tickCounter == definition.initialTickDelay
                        || definition.repeatTickDelay > 0 && tickCounter > definition.initialTickDelay
                                && (tickCounter - definition.initialTickDelay) % definition.repeatTickDelay == 0;
        if (!run) {
            return;
        }
        try {
            invokeTick(definition);
        } catch (Throwable error) {
            tickEnabled = false;
            reportCallbackError(definition, "onTick", error);
        }
    }

    private void invokeTick(TileEntityDefinition definition) {
        try (Context context = createScopedContext()) {
            int blockId = worldObj.getBlockId(xCoord, yCoord, zCoord);
            if (BlockCallbackRegistry.get(blockId) != null) {
                try (LuaBlockActionContext blockContext = new LuaBlockActionContext(
                        worldObj, xCoord, yCoord, zCoord, null, null, -1, true)) {
                    context.set("state", blockContext.get("state"));
                    definition.tickAction.call(context);
                }
                return;
            }
            definition.tickAction.call(context);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        typeName = tag.getString("BetaMoonType");
        initializeDefinition();
        tickCounter = tag.getInteger("BetaMoonTicks");
        NBTTagList items = tag.getTagList("Items");
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound itemTag = (NBTTagCompound) items.tagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < inventory.length) {
                inventory[slot] = new ItemStack(itemTag);
            }
        }
        TileEntityDefinition definition = getDefinition();
        if (definition == null) {
            return;
        }
        data.read(tag, definition);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("BetaMoonType", typeName == null ? "" : typeName);
        tag.setInteger("BetaMoonTicks", tickCounter);
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == null) {
                continue;
            }
            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setByte("Slot", (byte) i);
            inventory[i].writeToNBT(itemTag);
            items.setTag(itemTag);
        }
        tag.setTag("Items", items);
        TileEntityDefinition definition = getDefinition();
        if (definition == null) {
            return;
        }
        data.write(tag, definition);
    }

    @Override
    public int getSizeInventory() {
        return inventory.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return validSlot(slot) ? inventory[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (!validSlot(slot) || inventory[slot] == null || amount <= 0) {
            return null;
        }
        ItemStack result;
        if (inventory[slot].stackSize <= amount) {
            result = inventory[slot];
            inventory[slot] = null;
        } else {
            result = inventory[slot].splitStack(amount);
            if (inventory[slot].stackSize == 0) {
                inventory[slot] = null;
            }
        }
        onInventoryChanged();
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (!validSlot(slot)) {
            return;
        }
        inventory[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        onInventoryChanged();
    }

    @Override
    public void onInventoryChanged() {
        super.onInventoryChanged();
        TileEntityDefinition definition = getDefinition();
        if (definition == null || definition.inventoryChangedAction.isnil() || !inventoryActionEnabled
                || notifyingInventory || worldObj == null || worldObj.multiplayerWorld) {
            return;
        }
        notifyingInventory = true;
        try (Context context = createScopedContext()) {
            definition.inventoryChangedAction.call(context);
        } catch (Throwable error) {
            inventoryActionEnabled = false;
            reportCallbackError(definition, "onInventoryChanged", error);
        } finally {
            notifyingInventory = false;
        }
    }

    @Override
    public String getInvName() {
        TileEntityDefinition d = getDefinition();
        return d == null ? "BetaMoon" : d.inventoryName;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return worldObj != null && worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
    }

    public int getDataInt(String name) {
        return data.getSyncValue(name);
    }

    /** Returns a data value for declarative GUI bindings. */
    public Object getDataValue(String name) {
        return data.get(name);
    }

    /** Returns the item in a named slot for a visual item element. */
    public ItemStack getStackInNamedSlot(String name) {
        return getStackInSlot(slot(name));
    }

    public void setDataInt(String name, int value) {
        setDataValue(name, Integer.valueOf(value));
    }

    public void setSyncedData(String name, int value) {
        data.setSyncValue(getDefinition(), name, value);
        super.onInventoryChanged();
    }

    public void markDirty() {
        super.onInventoryChanged();
    }

    /**
     * Recipe commits require a live authoritative tile, not a removed or
     * client-side view.
     */
    public boolean isRecipeInventoryValid() {
        return getDefinition() != null && !func_31006_g() && worldObj != null && !worldObj.multiplayerWorld
                && worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this;
    }

    public boolean isRecipeCommitBlocked() {
        return committingRecipe || notifyingInventory;
    }

    /** Returns an independent inventory snapshot for recipe preparation. */
    public ItemStack[] copyRecipeInventory() {
        return copyInventory(inventory);
    }

    /**
     * Publishes a prepared inventory only if its source snapshot is still current.
     */
    public RecipeCommitResult commitRecipeInventory(ItemStack[] expected, ItemStack[] planned) {
        if (!isRecipeInventoryValid() || expected == null || planned == null || expected.length != inventory.length
                || planned.length != inventory.length) {
            return RecipeCommitResult.INVALID_INVENTORY;
        }
        if (isRecipeCommitBlocked()) {
            return RecipeCommitResult.REENTRANT;
        }
        for (int i = 0; i < inventory.length; i++) {
            if (!ItemStack.areItemStacksEqual(inventory[i], expected[i])) {
                return RecipeCommitResult.INVENTORY_CHANGED;
            }
        }
        committingRecipe = true;
        try {
            inventory = copyInventory(planned);
            onInventoryChanged();
        } finally {
            committingRecipe = false;
        }
        return RecipeCommitResult.APPLIED;
    }

    private static ItemStack[] copyInventory(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].copy();
        }
        return copy;
    }

    private boolean validSlot(int slot) {
        return slot >= 0 && slot < inventory.length;
    }

    /** Creates the safe Lua view shared by tick and neighbor callbacks. */
    public LuaTable createContext() {
        return createScopedContext();
    }

    public Context createScopedContext() {
        return new Context(this);
    }

    private int slot(String name) {
        TileEntityDefinition definition = getDefinition();
        Integer value = definition == null ? null : definition.slots.get(name);
        if (value == null) {
            throw new LuaError("Unknown inventory slot: " + name);
        }
        return value.intValue();
    }

    public void setDataValue(String name, Object value) {
        data.set(getDefinition(), name, value);
        super.onInventoryChanged();
    }

    private static void reportCallbackError(TileEntityDefinition definition, String callback, Throwable error) {
        String detail = error.getMessage() == null ? error.toString() : error.getMessage();
        String message = "tile entity " + callback + " was disabled after an error: " + detail;
        LuaScriptErrors.add(definition.owner, message);
        BetaMoonCommon.LOGGER.warning(definition.owner + ": " + message);
    }

    public static final class Context extends LuaTable implements AutoCloseable {
        private final LuaCallbackScope scope;

        private Context(LuaTileEntity entity) {
            scope = new LuaCallbackScope(entity.worldObj != null && !entity.worldObj.multiplayerWorld);
            LuaTable entityValue = new LuaTable();
            entityValue.set("data", LuaTileDataAccess.create(entity));
            entityValue.set("inventory", new InventoryAccess(entity));
            entityValue.set("markDirty", new MarkDirty(entity, entityValue));
            set("entity", entityValue);
            set("x", entity.xCoord);
            set("y", entity.yCoord);
            set("z", entity.zCoord);
            set("world", LuaWorldActionAccess.create(
                    scope, entity.worldObj, entity.xCoord, entity.yCoord, entity.zCoord));
            set("recipes", new RecipeAccess(entity));
            set("fuels", new FuelAccess());
        }

        @Override
        public void close() {
            scope.close();
        }
    }

    private static final class InventoryAccess extends LuaTable {
        private final LuaTileEntity entity;
        private InventoryAccess(LuaTileEntity entity) {
            this.entity = entity;
            set("get", new InventoryGet(this));
            set("set", new InventorySet(this));
            set("remove", new InventoryRemove(this));
            set("canAdd", new InventoryCanAdd(this));
            set("add", new InventoryAdd(this));
            set("consumeFuel", new ConsumeFuel(this));
        }
    }
    private static final class InventoryGet extends VarArgFunction {
        private final InventoryAccess access;
        private InventoryGet(InventoryAccess access) {
            this.access = access;
        }

        public Varargs invoke(Varargs args) {
            return stackToLua(access.entity
                    .getStackInSlot(access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring())));
        }
    }
    private static final class InventorySet extends VarArgFunction {
        private final InventoryAccess access;
        private InventorySet(InventoryAccess access) {
            this.access = access;
        }

        public Varargs invoke(Varargs args) {
            int slot = access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring());
            access.entity.setInventorySlotContents(slot, luaToStack(LuaTileEntity.arg(args, access, 2)));
            return LuaValue.NIL;
        }
    }
    private static final class InventoryRemove extends VarArgFunction {
        private final InventoryAccess access;
        private InventoryRemove(InventoryAccess access) {
            this.access = access;
        }

        public Varargs invoke(Varargs args) {
            return stackToLua(
                    access.entity.decrStackSize(access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring()),
                            LuaTileEntity.arg(args, access, 2).optint(1)));
        }
    }
    private static final class InventoryCanAdd extends VarArgFunction {
        private final InventoryAccess access;
        private InventoryCanAdd(InventoryAccess access) {
            this.access = access;
        }

        public Varargs invoke(Varargs args) {
            ItemStack incoming = luaToStack(LuaTileEntity.arg(args, access, 2));
            ItemStack current = access.entity
                    .getStackInSlot(access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring()));
            boolean fits = incoming != null && (current == null || current.isItemEqual(incoming)
                    && current.stackSize + incoming.stackSize <= Math.min(64, current.getMaxStackSize()));
            return LuaValue.valueOf(fits);
        }
    }
    private static final class InventoryAdd extends VarArgFunction {
        private final InventoryAccess access;
        private InventoryAdd(InventoryAccess access) {
            this.access = access;
        }

        public Varargs invoke(Varargs args) {
            int slot = access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring());
            ItemStack incoming = luaToStack(LuaTileEntity.arg(args, access, 2));
            ItemStack current = access.entity.getStackInSlot(slot);
            if (incoming == null) {
                return LuaValue.FALSE;
            }
            if (current == null) {
                access.entity.setInventorySlotContents(slot, incoming);
            } else if (current.isItemEqual(incoming)
                    && current.stackSize + incoming.stackSize <= Math.min(64, current.getMaxStackSize())) {
                current.stackSize += incoming.stackSize;
                access.entity.onInventoryChanged();
            } else {
                return LuaValue.FALSE;
            }
            return LuaValue.TRUE;
        }
    }
    private static final class ConsumeFuel extends VarArgFunction {
        private final InventoryAccess access;
        private ConsumeFuel(InventoryAccess access) {
            this.access = access;
        }

        public Varargs invoke(Varargs args) {
            int slot = access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring());
            AssetKey setKey = FuelsApi.optionalSetKey(LuaTileEntity.arg(args, access, 2), "consumeFuel set");
            return LuaValue.valueOf(FuelConsumption.consume(access.entity, slot, setKey));
        }
    }
    private static final class MarkDirty extends VarArgFunction {
        private final LuaTileEntity entity;
        private final LuaValue receiver;
        private MarkDirty(LuaTileEntity entity, LuaValue receiver) {
            this.entity = entity;
            this.receiver = receiver;
        }

        public Varargs invoke(Varargs args) {
            entity.markDirty();
            return LuaValue.NIL;
        }
    }
    private static final class RecipeAccess extends LuaTable {
        private RecipeAccess(LuaTileEntity entity) {
            set("getSmeltingResult", new SmeltingResult(this));
            betamoon.recipes.custom.RecipeMatching.attach(this, entity);
        }
    }
    private static final class SmeltingResult extends VarArgFunction {
        private final RecipeAccess access;
        private SmeltingResult(RecipeAccess access) {
            this.access = access;
        }

        public Varargs invoke(Varargs args) {
            ItemStack input = luaToStack(LuaTileEntity.arg(args, access, 1));
            if (input == null) {
                return LuaValue.NIL;
            }
            ItemStack result = FurnaceRecipes.smelting().getSmeltingResult(input.itemID);
            return stackToLua(result == null ? null : result.copy());
        }
    }
    private static final class FuelAccess extends LuaTable {
        private FuelAccess() {
            set("getBurnTime", new BurnTime(this));
        }
    }
    private static final class BurnTime extends VarArgFunction {
        private final FuelAccess access;
        private BurnTime(FuelAccess access) {
            this.access = access;
        }

        public Varargs invoke(Varargs args) {
            ItemStack stack = luaToStack(LuaTileEntity.arg(args, access, 1));
            AssetKey setKey = FuelsApi.optionalSetKey(LuaTileEntity.arg(args, access, 2), "getBurnTime set");
            return LuaValue.valueOf(FuelRegistry.resolve(stack, setKey).burnTime);
        }
    }

    private static LuaValue arg(Varargs args, LuaValue receiver, int index) {
        return args.arg(args.arg1() == receiver ? index + 1 : index);
    }

    private static LuaValue stackToLua(ItemStack stack) {
        if (stack == null) {
            return LuaValue.NIL;
        }
        LuaTable value = new LuaTable();
        value.set("id", stack.itemID);
        value.set("count", stack.stackSize);
        value.set("damage", stack.getItemDamage());
        return value;
    }

    private static ItemStack luaToStack(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        if (!value.istable()) {
            throw new LuaError("Expected an item stack table or nil.");
        }
        LuaValue idValue = value.get("id");
        if (idValue.isnil()) {
            LuaValue item = value.get("item");
            idValue = item.isnumber() ? item : item.get("id");
        }
        return new ItemStack(idValue.checkint(), value.get("count").optint(1), value.get("damage").optint(0));
    }
}
