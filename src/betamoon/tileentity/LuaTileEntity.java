package betamoon.tileentity;

import betamoon.BetaMoonMain;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.IInventory;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.TileEntity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Persistent, inventory-capable tile entity whose behavior is defined by Lua. */
public final class LuaTileEntity extends TileEntity implements IInventory {
    private String typeName;
    private ItemStack[] inventory = new ItemStack[0];
    private final Map data = new HashMap();
    private int tickCounter;
    private boolean tickEnabled = true;
    private boolean inventoryActionEnabled = true;
    private boolean notifyingInventory;

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
        if (definition == null) return;
        if (inventory.length != definition.slots.size()) inventory = new ItemStack[definition.slots.size()];
        Iterator iterator = definition.fields.values().iterator();
        while (iterator.hasNext()) {
            TileEntityDefinition.Field field = (TileEntityDefinition.Field) iterator.next();
            if (!data.containsKey(field.name)) data.put(field.name, field.defaultValue);
        }
    }

    public void updateEntity() {
        TileEntityDefinition definition = getDefinition();
        if (definition == null || definition.tickAction.isnil() || !tickEnabled
            || worldObj == null || worldObj.multiplayerWorld) return;
        tickCounter++;
        boolean run = definition.randomTicks ? worldObj.rand.nextDouble() < definition.randomTickChance
            : tickCounter == definition.initialTickDelay
                || definition.repeatTickDelay > 0 && tickCounter > definition.initialTickDelay
                    && (tickCounter - definition.initialTickDelay) % definition.repeatTickDelay == 0;
        if (!run) return;
        try {
            definition.tickAction.call(createContext());
        } catch (Throwable error) {
            tickEnabled = false;
            reportCallbackError(definition, "onTick", error);
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        typeName = tag.getString("BetaMoonType");
        initializeDefinition();
        tickCounter = tag.getInteger("BetaMoonTicks");
        NBTTagList items = tag.getTagList("Items");
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound itemTag = (NBTTagCompound) items.tagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < inventory.length) inventory[slot] = new ItemStack(itemTag);
        }
        TileEntityDefinition definition = getDefinition();
        if (definition == null) return;
        Iterator iterator = definition.fields.values().iterator();
        while (iterator.hasNext()) {
            TileEntityDefinition.Field field = (TileEntityDefinition.Field) iterator.next();
            String key = "Data_" + field.name;
            if (!tag.hasKey(key)) continue;
            if ("integer".equals(field.type)) data.put(field.name, Integer.valueOf(tag.getInteger(key)));
            else if ("number".equals(field.type)) data.put(field.name, Double.valueOf(tag.getDouble(key)));
            else if ("boolean".equals(field.type)) data.put(field.name, Boolean.valueOf(tag.getBoolean(key)));
            else if ("string".equals(field.type)) data.put(field.name, tag.getString(key));
        }
    }

    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("BetaMoonType", typeName == null ? "" : typeName);
        tag.setInteger("BetaMoonTicks", tickCounter);
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == null) continue;
            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setByte("Slot", (byte) i);
            inventory[i].writeToNBT(itemTag);
            items.setTag(itemTag);
        }
        tag.setTag("Items", items);
        TileEntityDefinition definition = getDefinition();
        if (definition == null) return;
        Iterator iterator = definition.fields.values().iterator();
        while (iterator.hasNext()) {
            TileEntityDefinition.Field field = (TileEntityDefinition.Field) iterator.next();
            Object value = data.get(field.name);
            String key = "Data_" + field.name;
            if ("integer".equals(field.type)) tag.setInteger(key, ((Number) value).intValue());
            else if ("number".equals(field.type)) tag.setDouble(key, ((Number) value).doubleValue());
            else if ("boolean".equals(field.type)) tag.setBoolean(key, ((Boolean) value).booleanValue());
            else if ("string".equals(field.type)) tag.setString(key, String.valueOf(value));
        }
    }

    public int getSizeInventory() { return inventory.length; }
    public ItemStack getStackInSlot(int slot) { return validSlot(slot) ? inventory[slot] : null; }
    public ItemStack decrStackSize(int slot, int amount) {
        if (!validSlot(slot) || inventory[slot] == null || amount <= 0) return null;
        ItemStack result;
        if (inventory[slot].stackSize <= amount) { result = inventory[slot]; inventory[slot] = null; }
        else { result = inventory[slot].splitStack(amount); if (inventory[slot].stackSize == 0) inventory[slot] = null; }
        onInventoryChanged();
        return result;
    }
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (!validSlot(slot)) return;
        inventory[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) stack.stackSize = getInventoryStackLimit();
        onInventoryChanged();
    }
    public void onInventoryChanged() {
        super.onInventoryChanged();
        TileEntityDefinition definition = getDefinition();
        if (definition == null || definition.inventoryChangedAction.isnil()
            || !inventoryActionEnabled || notifyingInventory || worldObj == null || worldObj.multiplayerWorld) return;
        notifyingInventory = true;
        try {
            definition.inventoryChangedAction.call(createContext());
        } catch (Throwable error) {
            inventoryActionEnabled = false;
            reportCallbackError(definition, "onInventoryChanged", error);
        } finally {
            notifyingInventory = false;
        }
    }
    public String getInvName() { TileEntityDefinition d = getDefinition(); return d == null ? "BetaMoon" : d.inventoryName; }
    public int getInventoryStackLimit() { return 64; }
    public boolean canInteractWith(EntityPlayer player) {
        return worldObj != null && worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
    }
    public int getDataInt(String name) {
        Object value = data.get(name);
        return value instanceof Number ? ((Number) value).intValue()
            : Boolean.TRUE.equals(value) ? 1 : 0;
    }
    /** Returns a data value for declarative GUI bindings. */
    public Object getDataValue(String name) { return data.get(name); }
    /** Returns the item in a named slot for a visual item element. */
    public ItemStack getStackInNamedSlot(String name) { return getStackInSlot(slot(name)); }
    public void setDataInt(String name, int value) { setData(name, Integer.valueOf(value)); }
    public void setSyncedData(String name, int value) {
        TileEntityDefinition.Field field = (TileEntityDefinition.Field) getDefinition().fields.get(name);
        setData(name, field != null && "boolean".equals(field.type)
            ? Boolean.valueOf(value != 0) : Integer.valueOf(value));
    }
    public void markDirty() { super.onInventoryChanged(); }
    private boolean validSlot(int slot) { return slot >= 0 && slot < inventory.length; }

    /** Creates the safe Lua view shared by tick and neighbor callbacks. */
    public LuaTable createContext() {
        return new TickContext(this);
    }

    private int slot(String name) {
        TileEntityDefinition definition = getDefinition();
        Integer value = definition == null ? null : (Integer) definition.slots.get(name);
        if (value == null) throw new LuaError("Unknown inventory slot: " + name);
        return value.intValue();
    }

    private void setData(String name, Object value) {
        TileEntityDefinition definition = getDefinition();
        TileEntityDefinition.Field field = definition == null ? null : (TileEntityDefinition.Field) definition.fields.get(name);
        if (field == null) throw new LuaError("Unknown tile entity data field: " + name);
        if ("integer".equals(field.type) && !(value instanceof Integer)
            || "number".equals(field.type) && !(value instanceof Number)
            || "boolean".equals(field.type) && !(value instanceof Boolean)
            || "string".equals(field.type) && !(value instanceof String)) {
            throw new LuaError("Invalid value for '" + name + "'; expected " + field.type + ".");
        }
        data.put(name, value);
        super.onInventoryChanged();
    }

    private static void reportCallbackError(TileEntityDefinition definition, String callback,
        Throwable error) {
        String detail = error.getMessage() == null ? error.toString() : error.getMessage();
        String message = "tile entity " + callback + " was disabled after an error: " + detail;
        LuaScriptErrors.add(definition.owner, message);
        BetaMoonMain.LOGGER.warning(definition.owner + ": " + message);
    }

    private static final class TickContext extends LuaTable {
        private TickContext(LuaTileEntity entity) {
            LuaTable entityValue = new LuaTable();
            entityValue.set("data", new DataAccess(entity));
            entityValue.set("inventory", new InventoryAccess(entity));
            entityValue.set("markDirty", new MarkDirty(entity, entityValue));
            set("entity", entityValue);
            set("x", entity.xCoord); set("y", entity.yCoord); set("z", entity.zCoord);
            set("world", new WorldAccess(entity));
            set("recipes", new RecipeAccess());
            set("fuels", new FuelAccess());
        }
    }

    private static final class WorldAccess extends LuaTable {
        private final LuaTileEntity entity;
        private WorldAccess(LuaTileEntity entity) {
            this.entity = entity;
            set("isPowered", new IsPowered(this));
            set("notifyNeighbors", new NotifyNeighbors(this));
        }
    }
    private static final class IsPowered extends VarArgFunction {
        private final WorldAccess access; private IsPowered(WorldAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(access.entity.worldObj != null && access.entity.worldObj
                .isBlockIndirectlyGettingPowered(access.entity.xCoord, access.entity.yCoord, access.entity.zCoord));
        }
    }
    private static final class NotifyNeighbors extends VarArgFunction {
        private final WorldAccess access; private NotifyNeighbors(WorldAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            if (access.entity.worldObj != null && !access.entity.worldObj.multiplayerWorld) {
                int id = access.entity.worldObj.getBlockId(access.entity.xCoord, access.entity.yCoord, access.entity.zCoord);
                access.entity.worldObj.notifyBlocksOfNeighborChange(access.entity.xCoord,
                    access.entity.yCoord, access.entity.zCoord, id);
            }
            return LuaValue.NIL;
        }
    }

    private static final class DataAccess extends LuaTable {
        private final LuaTileEntity entity;
        private DataAccess(LuaTileEntity entity) { this.entity = entity; set("get", new DataGet(this)); set("set", new DataSet(this)); }
    }
    private static final class DataGet extends VarArgFunction {
        private final DataAccess access; private DataGet(DataAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) { return toLua(access.entity.data.get(LuaTileEntity.arg(args, access, 1).checkjstring())); }
    }
    private static final class DataSet extends VarArgFunction {
        private final DataAccess access; private DataSet(DataAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            String name = LuaTileEntity.arg(args, access, 1).checkjstring();
            TileEntityDefinition.Field field = (TileEntityDefinition.Field) access.entity.getDefinition().fields.get(name);
            if (field == null) throw new LuaError("Unknown tile entity data field: " + name);
            LuaValue value = LuaTileEntity.arg(args, access, 2);
            Object converted = "integer".equals(field.type) ? Integer.valueOf(value.checkint())
                : "number".equals(field.type) ? Double.valueOf(value.checkdouble())
                : "boolean".equals(field.type) ? Boolean.valueOf(value.checkboolean()) : value.checkjstring();
            access.entity.setData(name, converted); return LuaValue.NIL;
        }
    }

    private static final class InventoryAccess extends LuaTable {
        private final LuaTileEntity entity;
        private InventoryAccess(LuaTileEntity entity) {
            this.entity = entity; set("get", new InventoryGet(this)); set("set", new InventorySet(this));
            set("remove", new InventoryRemove(this)); set("canAdd", new InventoryCanAdd(this));
            set("add", new InventoryAdd(this)); set("consumeFuel", new ConsumeFuel(this));
        }
    }
    private static final class InventoryGet extends VarArgFunction {
        private final InventoryAccess access; private InventoryGet(InventoryAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) { return stackToLua(access.entity.getStackInSlot(access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring()))); }
    }
    private static final class InventorySet extends VarArgFunction {
        private final InventoryAccess access; private InventorySet(InventoryAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            int slot = access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring());
            access.entity.setInventorySlotContents(slot, luaToStack(LuaTileEntity.arg(args, access, 2))); return LuaValue.NIL;
        }
    }
    private static final class InventoryRemove extends VarArgFunction {
        private final InventoryAccess access; private InventoryRemove(InventoryAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            return stackToLua(access.entity.decrStackSize(access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring()),
                LuaTileEntity.arg(args, access, 2).optint(1)));
        }
    }
    private static final class InventoryCanAdd extends VarArgFunction {
        private final InventoryAccess access; private InventoryCanAdd(InventoryAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            ItemStack incoming = luaToStack(LuaTileEntity.arg(args, access, 2));
            ItemStack current = access.entity.getStackInSlot(access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring()));
            boolean fits = incoming != null && (current == null || current.isItemEqual(incoming)
                && current.stackSize + incoming.stackSize <= Math.min(64, current.getMaxStackSize()));
            return LuaValue.valueOf(fits);
        }
    }
    private static final class InventoryAdd extends VarArgFunction {
        private final InventoryAccess access; private InventoryAdd(InventoryAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            int slot = access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring());
            ItemStack incoming = luaToStack(LuaTileEntity.arg(args, access, 2));
            ItemStack current = access.entity.getStackInSlot(slot);
            if (incoming == null) return LuaValue.FALSE;
            if (current == null) access.entity.setInventorySlotContents(slot, incoming);
            else if (current.isItemEqual(incoming)
                && current.stackSize + incoming.stackSize <= Math.min(64, current.getMaxStackSize())) {
                current.stackSize += incoming.stackSize; access.entity.onInventoryChanged();
            } else return LuaValue.FALSE;
            return LuaValue.TRUE;
        }
    }
    private static final class ConsumeFuel extends VarArgFunction {
        private final InventoryAccess access; private ConsumeFuel(InventoryAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            int slot = access.entity.slot(LuaTileEntity.arg(args, access, 1).checkjstring());
            ItemStack fuel = access.entity.getStackInSlot(slot);
            int time = fuelTime(fuel);
            if (time <= 0) return LuaValue.ZERO;
            if (fuel.getItem().hasContainerItem()) {
                access.entity.setInventorySlotContents(slot, new ItemStack(fuel.getItem().getContainerItem()));
            } else {
                access.entity.decrStackSize(slot, 1);
            }
            return LuaValue.valueOf(time);
        }
    }
    private static final class MarkDirty extends VarArgFunction {
        private final LuaTileEntity entity; private final LuaValue receiver;
        private MarkDirty(LuaTileEntity entity, LuaValue receiver) { this.entity = entity; this.receiver = receiver; }
        public Varargs invoke(Varargs args) { entity.markDirty(); return LuaValue.NIL; }
    }
    private static final class RecipeAccess extends LuaTable {
        private RecipeAccess() { set("getSmeltingResult", new SmeltingResult(this)); }
    }
    private static final class SmeltingResult extends VarArgFunction {
        private final RecipeAccess access; private SmeltingResult(RecipeAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) {
            ItemStack input = luaToStack(LuaTileEntity.arg(args, access, 1));
            if (input == null) return LuaValue.NIL;
            ItemStack result = FurnaceRecipes.smelting().getSmeltingResult(input.itemID);
            return stackToLua(result == null ? null : result.copy());
        }
    }
    private static final class FuelAccess extends LuaTable {
        private FuelAccess() { set("getBurnTime", new BurnTime(this)); }
    }
    private static final class BurnTime extends VarArgFunction {
        private final FuelAccess access; private BurnTime(FuelAccess access) { this.access = access; }
        public Varargs invoke(Varargs args) { return LuaValue.valueOf(fuelTime(luaToStack(LuaTileEntity.arg(args, access, 1)))); }
    }

    private static int fuelTime(ItemStack stack) {
        if (stack == null) return 0;
        int id = stack.getItem().shiftedIndex;
        return id < 256 && Block.blocksList[id] != null && Block.blocksList[id].blockMaterial == Material.wood ? 300
            : id == Item.stick.shiftedIndex ? 100 : id == Item.coal.shiftedIndex ? 1600
            : id == Item.bucketLava.shiftedIndex ? 20000 : id == Block.sapling.blockID ? 100 : ModLoader.AddAllFuel(id);
    }
    private static LuaValue arg(Varargs args, LuaValue receiver, int index) { return args.arg(args.arg1() == receiver ? index + 1 : index); }
    private static LuaValue toLua(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof Boolean) return LuaValue.valueOf(((Boolean) value).booleanValue());
        if (value instanceof Integer) return LuaValue.valueOf(((Integer) value).intValue());
        if (value instanceof Number) return LuaValue.valueOf(((Number) value).doubleValue());
        return LuaValue.valueOf(String.valueOf(value));
    }
    private static LuaValue stackToLua(ItemStack stack) {
        if (stack == null) return LuaValue.NIL;
        LuaTable value = new LuaTable(); value.set("id", stack.itemID); value.set("count", stack.stackSize);
        value.set("damage", stack.getItemDamage()); return value;
    }
    private static ItemStack luaToStack(LuaValue value) {
        if (value.isnil()) return null;
        if (!value.istable()) throw new LuaError("Expected an item stack table or nil.");
        LuaValue idValue = value.get("id");
        if (idValue.isnil()) {
            LuaValue item = value.get("item"); idValue = item.isnumber() ? item : item.get("id");
        }
        return new ItemStack(idValue.checkint(), value.get("count").optint(1), value.get("damage").optint(0));
    }
}
