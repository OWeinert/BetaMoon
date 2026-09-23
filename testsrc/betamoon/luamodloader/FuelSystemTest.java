package betamoon.luamodloader;

import betamoon.assets.AssetKey;
import betamoon.fuel.FuelConsumption;
import betamoon.fuel.FuelRegistry;
import betamoon.fuel.FuelRegistration;
import betamoon.fuel.FuelSetDefinition;
import betamoon.instrumentation.hooks.fuel.FuelBurnTimeCallbacks;
import betamoon.luaapi.fuel.FuelsApi;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.LuaContainer;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IInventory;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.Slot;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Exercises fuel precedence, composition, Lua bindings, and atomic consumption. */
public final class FuelSystemTest {
    private FuelSystemTest() {
    }

    public static void main(String[] arguments) throws Exception {
        verifyNativeRules();
        verifyRegistryAndComposition();
        verifyConsumption();
        verifyLuaApiAndCleanup();
        verifyFurnaceBridge();
        System.out.println("Fuel system passed: native rules, sets, Lua API, consumption and furnace bridge.");
    }

    private static void verifyNativeRules() {
        require(FuelRegistry.resolve(new ItemStack(Block.planks), FuelRegistry.FURNACE).burnTime == 300,
                "Wooden blocks must keep their vanilla burn time");
        require(FuelRegistry.resolve(new ItemStack(Item.stick), FuelRegistry.FURNACE).burnTime == 100,
                "Sticks must keep their vanilla burn time");
        require(FuelRegistry.resolve(new ItemStack(Item.coal), FuelRegistry.FURNACE).burnTime == 1600,
                "Coal must keep its vanilla burn time");
        require(FuelRegistry.resolve(new ItemStack(Item.bucketLava), FuelRegistry.FURNACE).burnTime == 20000,
                "Lava buckets must keep their vanilla burn time");
        require(FuelRegistry.resolve(new ItemStack(Block.sapling), FuelRegistry.FURNACE).burnTime == 100,
                "Saplings must keep their vanilla burn time");
        require(FuelRegistry.resolve(null, FuelRegistry.FURNACE).burnTime == 0,
                "A null stack must not resolve as fuel");
    }

    private static void verifyRegistryAndComposition() {
        AssetKey primaryKey = AssetKey.parse("test:fuel/primary");
        AssetKey combinedKey = AssetKey.parse("test:fuel/combined");
        FuelSetDefinition primary = FuelRegistry.addSet("fuel-test", primaryKey,
                Collections.<AssetKey>emptyList());
        FuelSetDefinition combined = FuelRegistry.addSet("fuel-test", combinedKey,
                Arrays.asList(primaryKey, FuelRegistry.FURNACE));
        FuelRegistration wildcard = FuelRegistry.add("fuel-test", primaryKey, Item.diamond.shiftedIndex, null, 400);
        FuelRegistration exact = FuelRegistry.add("fuel-test", primaryKey, Item.diamond.shiftedIndex,
                Integer.valueOf(2), 900);

        require(FuelRegistry.resolve(new ItemStack(Item.diamond, 1, 2), primaryKey).burnTime == 900,
                "Exact damage must win over a wildcard rule");
        require(FuelRegistry.resolve(new ItemStack(Item.diamond, 1, 1), primaryKey).burnTime == 400,
                "Wildcard rules must accept other damage values");
        require(FuelRegistry.resolve(new ItemStack(Item.coal), combinedKey).burnTime == 1600,
                "Included furnace sets must retain native fuel rules");
        require(FuelRegistry.resolve(new ItemStack(Item.diamond), combinedKey).burnTime == 400,
                "Included custom sets must be resolved in declaration order");

        try {
            FuelRegistry.add("fuel-test", primaryKey, Item.diamond.shiftedIndex, null, 500);
            throw new AssertionError("An ambiguous duplicate fuel rule must be rejected");
        } catch (IllegalArgumentException expected) {
            // Duplicate specificity rejected.
        }

        try {
            FuelRegistry.addSet("other-owner", primaryKey, Collections.<AssetKey>emptyList());
            throw new AssertionError("Another owner must not replace a fuel set");
        } catch (IllegalArgumentException expected) {
            // Ownership collision rejected.
        }
        try {
            FuelRegistry.addSet("fuel-test", primaryKey, Collections.singletonList(combinedKey));
            throw new AssertionError("A duplicate same-owner fuel set must be rejected");
        } catch (IllegalArgumentException expected) {
            // Duplicate key rejected without changing the active set.
        }

        require(FuelRegistry.remove(exact), "An active exact registration must be removable");
        require(FuelRegistry.resolve(new ItemStack(Item.diamond, 1, 2), primaryKey).burnTime == 400,
                "Removing an exact rule must reveal its wildcard rule");
        require(FuelRegistry.removeSet(combined), "A custom set must be removable");
        require(FuelRegistry.removeSet(primary), "A custom set and its registrations must be removable");
        require(!FuelRegistry.contains(wildcard), "Removing a set must remove its direct registrations");
    }

    private static void verifyConsumption() {
        AssetKey setKey = AssetKey.parse("test:fuel/consumption");
        FuelSetDefinition set = FuelRegistry.addSet("fuel-consumption", setKey,
                Collections.<AssetKey>emptyList());
        FuelRegistry.add("fuel-consumption", setKey, Item.diamond.shiftedIndex, null, 700);
        TestInventory inventory = new TestInventory(new ItemStack(Item.diamond, 2, 0));
        require(FuelConsumption.consume(inventory, 0, setKey) == 700,
                "Accepted fuel must return its burn duration");
        require(inventory.stack != null && inventory.stack.stackSize == 1 && inventory.changed == 1,
                "Fuel consumption must remove exactly one item and dirty the inventory once");

        inventory = new TestInventory(new ItemStack(Item.bucketLava));
        require(FuelConsumption.consume(inventory, 0, FuelRegistry.FURNACE) == 20000,
                "Native fuel must be consumable through the shared service");
        require(inventory.stack != null && inventory.stack.itemID == Item.bucketEmpty.shiftedIndex,
                "A native container item must replace consumed fuel");
        FuelRegistry.removeSet(set);
    }

    private static void verifyLuaApiAndCleanup() throws Exception {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable root = new LuaTable();
        FuelsApi.attach(root);
        TileEntityApi.attach(root);
        lua.set("betamoon", root);
        String script = "fuel-lua-test.lua";
        Field nativeRegistration = TileEntityRegistry.class.getDeclaredField("minecraftTypeRegistered");
        nativeRegistration.setAccessible(true);
        boolean previousRegistration = nativeRegistration.getBoolean(null);
        nativeRegistration.setBoolean(null, true);
        try {
            try (ScriptExecutionScope ignored = ScriptExecutionScope.open(script)) {
                lua.load("local set=betamoon.fuels.sets:add{key='luatest:fuel/arcane',"
                        + "include={'minecraft:fuel/furnace'}};"
                        + "local rule=betamoon.fuels:add{set=set,item=" + Item.diamond.shiftedIndex
                        + ",damage=3,burnTime=1200};"
                        + "betamoon.fuels:add{set=set,item={id=" + Item.diamond.shiftedIndex
                        + ",damage=4},burnTime=1300};"
                        + "assert(set.key=='luatest:fuel/arcane' and set.exists);"
                        + "assert(rule.item==" + Item.diamond.shiftedIndex + " and rule.damage==3);"
                        + "assert(set:getBurnTime({id=" + Item.diamond.shiftedIndex + ",damage=3})==1200);"
                        + "assert(set:getBurnTime({id=" + Item.diamond.shiftedIndex + ",damage=4})==1300);"
                        + "assert(set:contains({id=" + Item.coal.shiftedIndex + "}));"
                        + "assert(betamoon.fuels:isFuel({id=" + Item.coal.shiftedIndex + "}));"
                        + "assert(betamoon.fuels:getBurnTime(nil)==0 and not set:contains(nil));"
                        + "assert(not betamoon.fuels.sets:getRequired('minecraft:fuel/furnace'):remove());"
                        + "assert(betamoon.fuels.sets:getRequired('luatest:fuel/arcane').key==set.key);"
                        + "local temp=betamoon.fuels.sets:add{key='luatest:fuel/temporary'};"
                        + "local tempRule=betamoon.fuels:add{set=temp,item=" + Item.ingotIron.shiftedIndex
                        + ",burnTime=20}; assert(temp:remove() and not temp.exists and not tempRule.exists);"
                        + "local tile=betamoon.tileEntities:add{name='luatest:block/fuel_machine',"
                        + "inventory={slots={fuel={index=0}}}};"
                        + "betamoon.containers:add{name='luatest:block/fuel_machine',tileEntity=tile,"
                        + "slots={{slot='fuel',x=8,y=8,acceptsFuel=set}},"
                        + "playerInventory={x=8,y=30,includeHotbar=true}}")
                        .call();
            }
            require(FuelRegistry.findSet(AssetKey.parse("luatest:fuel/arcane")) != null,
                    "Lua declarations must publish their set");
            verifyFuelSlot();
        } finally {
            ScriptResourceTracker.unload(script);
            TileEntityRegistry.removeOwned(script);
            nativeRegistration.setBoolean(null, previousRegistration);
        }
        require(FuelRegistry.findSet(AssetKey.parse("luatest:fuel/arcane")) == null,
                "Unloading a script must remove its sets and registrations");
    }

    private static void verifyFuelSlot() {
        ContainerDefinition definition = TileEntityRegistry.getContainer("luatest:block/fuel_machine");
        require(definition != null && definition.slots.get(0).acceptedFuelSet
                .equals(AssetKey.parse("luatest:fuel/arcane")),
                "Container parsing must retain the selected fuel set");
        LuaTileEntity tile = new LuaTileEntity("luatest:block/fuel_machine");
        TestPlayer player = new TestPlayer();
        LuaContainer container = new LuaContainer(player.inventory, tile, definition);
        Slot fuelSlot = container.getSlot(0);
        require(fuelSlot.isItemValid(new ItemStack(Item.coal)),
                "A fuel slot must accept fuels inherited from an included set");
        require(fuelSlot.isItemValid(new ItemStack(Item.diamond, 1, 3)),
                "A fuel slot must accept an exact custom fuel");
        require(!fuelSlot.isItemValid(new ItemStack(Item.ingotIron)),
                "A fuel slot must reject an item outside its set");

        player.inventory.mainInventory[0] = new ItemStack(Item.ingotIron);
        require(container.getStackInSlot(28) == null && tile.getStackInSlot(0) == null,
                "Shift-click must not insert a rejected fuel");
        player.inventory.mainInventory[0] = new ItemStack(Item.coal);
        require(container.getStackInSlot(28) != null && tile.getStackInSlot(0) != null
                && tile.getStackInSlot(0).itemID == Item.coal.shiftedIndex,
                "Shift-click must route an accepted fuel into the filtered slot");
    }

    private static void verifyFurnaceBridge() {
        FuelRegistration exact = FuelRegistry.add("fuel-hook", FuelRegistry.FURNACE, Item.coal.shiftedIndex,
                Integer.valueOf(2), 2400);
        require(FuelBurnTimeCallbacks.result(1600, new ItemStack(Item.coal, 1, 2)) == 2400,
                "The furnace hook must prefer an exact BetaMoon rule");
        require(FuelBurnTimeCallbacks.result(1600, new ItemStack(Item.coal, 1, 0)) == 1600,
                "The furnace hook must preserve unmatched native results");
        FuelRegistry.remove(exact);

        FuelRegistration wildcard = FuelRegistry.add("fuel-hook", FuelRegistry.FURNACE, Item.diamond.shiftedIndex,
                null, 777);
        require(FuelRegistry.getLegacyFurnaceBurnTime(Item.diamond.shiftedIndex) == 777,
                "The ModLoader fallback must expose wildcard furnace registrations");
        FuelRegistry.remove(wildcard);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestInventory implements IInventory {
        private ItemStack stack;
        private int changed;

        private TestInventory(ItemStack stack) {
            this.stack = stack;
        }

        public int getSizeInventory() {
            return 1;
        }

        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? stack : null;
        }

        public ItemStack decrStackSize(int slot, int amount) {
            if (slot != 0 || stack == null || amount <= 0) {
                return null;
            }
            ItemStack removed = stack.stackSize <= amount ? stack : stack.splitStack(amount);
            if (removed == stack || stack.stackSize <= 0) {
                stack = null;
            }
            onInventoryChanged();
            return removed;
        }

        public void setInventorySlotContents(int slot, ItemStack value) {
            if (slot == 0) {
                stack = value;
                onInventoryChanged();
            }
        }

        public String getInvName() {
            return "fuel-test";
        }

        public int getInventoryStackLimit() {
            return 64;
        }

        public void onInventoryChanged() {
            changed++;
        }

        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static final class TestPlayer extends EntityPlayer {
        private TestPlayer() {
            super(new TestWorld());
        }

        @Override
        public void func_6420_o() {
        }
    }

    private static final class TestWorld extends World {
        private TestWorld() {
            super(null, "fuel-test", new WorldProvider() {
            }, 0L);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return null;
        }
    }
}
