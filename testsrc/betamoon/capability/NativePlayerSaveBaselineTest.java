package betamoon.capability;

import net.minecraft.src.Chunk;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Session;
import java.lang.reflect.Field;

/** Read-only baseline for vanilla player inventory and dimension save behavior. */
public final class NativePlayerSaveBaselineTest {
    private NativePlayerSaveBaselineTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Field blockList = Session.class.getDeclaredFields()[0];
        blockList.setAccessible(true);
        Field modLoaderList = ModLoader.class.getDeclaredField("field_blockList");
        modLoaderList.setAccessible(true);
        modLoaderList.set(null, blockList);
        require(Block.stone.blockID > 0, "Native blocks initialize before player crafting recipes");
        World world = new TestWorld();
        EntityPlayer original = player(world);
        original.dimension = -1;
        original.inventory.mainInventory[4] = new ItemStack(Item.appleRed, 3);
        original.inventory.armorInventory[1] = new ItemStack(Item.helmetSteel, 1);

        NBTTagCompound saved = new NBTTagCompound();
        original.writeEntityToNBT(saved);
        require(saved.hasKey("Inventory") && saved.getInteger("Dimension") == -1,
                "Native player saves use Inventory and Dimension tags");

        EntityPlayer restored = player(world);
        restored.readEntityFromNBT(saved);
        require(restored.dimension == -1 && restored.inventory.mainInventory[4] != null
                && restored.inventory.mainInventory[4].itemID == Item.appleRed.shiftedIndex
                && restored.inventory.mainInventory[4].stackSize == 3
                && restored.inventory.armorInventory[1] != null
                && restored.inventory.armorInventory[1].itemID == Item.helmetSteel.shiftedIndex,
                "Vanilla player inventory and dimension round-trip without BetaMoon state overrides");
        System.out.println("Native player save baseline passed.");
    }

    private static EntityPlayer player(World world) {
        return new EntityPlayer(world) {
            @Override
            public void func_6420_o() {
            }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private TestWorld() {
            super(null, "native_player_save_baseline", new WorldProvider() { }, 123L);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                public boolean chunkExists(int x, int z) { return false; }
                public Chunk provideChunk(int x, int z) { return null; }
                public Chunk prepareChunk(int x, int z) { return null; }
                public void populate(IChunkProvider provider, int x, int z) { }
                public boolean saveChunks(boolean force, IProgressUpdate progress) { return true; }
                public boolean unload100OldestChunks() { return false; }
                public boolean canSave() { return false; }
                public String makeString() { return "NativePlayerSaveBaselineTest"; }
            };
        }
    }
}
