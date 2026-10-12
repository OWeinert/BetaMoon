package betamoon.fluid.item;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.fluid.FluidAppearance;
import betamoon.fluid.FluidCarrier;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidDisplacementPolicy;
import betamoon.fluid.FluidFlowPolicy;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.block.FlowingFluidBlock;
import betamoon.fluid.block.StillFluidBlock;
import forge.MinecraftForge;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MovingObjectPosition;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/** Exercises collection, placement, permissions, containers and failure atomicity. */
public final class FluidBucketTest {
    private FluidBucketTest() {
    }

    public static void main(String[] arguments) {
        int[] blockIds = freeBlockIds(4);
        int bucketId = freeItemId();
        FluidAppearance appearance = new FluidAppearance(1, 2, 0xFFFFFF, 1, 0, 3);
        new FlowingFluidBlock(blockIds[0], FluidCarrier.WATER.material(), appearance);
        new StillFluidBlock(blockIds[1], FluidCarrier.WATER.material(), appearance);
        FluidBucketItem bucket = new FluidBucketItem(bucketId);
        FluidRegistration contained = FluidRegistry.instance().publish(new FluidDefinition(
                ContentKey.of("test", ContentType.FLUID, "bucket_behavior"), "fluid_bucket_test.lua", "water",
                FluidCarrier.WATER, FluidFlowPolicy.water(), appearance, FluidDisplacementPolicy.DROP,
                blockIds[0], blockIds[1], bucketId, false));

        new FlowingFluidBlock(blockIds[2], FluidCarrier.WATER.material(), appearance);
        new StillFluidBlock(blockIds[3], FluidCarrier.WATER.material(), appearance);
        FluidRegistration uncontained = FluidRegistry.instance().publish(new FluidDefinition(
                ContentKey.of("test", ContentType.FLUID, "no_bucket_behavior"), "fluid_bucket_test.lua", "neutral",
                FluidCarrier.WATER, FluidFlowPolicy.neutral(), appearance, FluidDisplacementPolicy.DROP,
                blockIds[2], blockIds[3], -1, false));

        FluidBucketHandler.register();
        verifyCollection(contained, uncontained);
        verifyPlacement(contained, uncontained, bucket);
        System.out.println("Fluid bucket checks passed.");
    }

    private static void verifyCollection(FluidRegistration contained, FluidRegistration uncontained) {
        TestWorld world = new TestWorld(false);
        int flowingId = contained.definition().flowingBlockId();
        world.setBlockAndMetadata(4, 64, 4, flowingId, 0);
        ItemStack collected = MinecraftForge.fillCustomBucket(world, 4, 64, 4);
        require(collected != null && collected.itemID == contained.definition().bucketItemId(),
                "A registered source returns its configured filled bucket");
        require(world.getBlockId(4, 64, 4) == 0, "Successful collection removes exactly one source");

        world.setBlockAndMetadata(4, 64, 4, flowingId, 1);
        require(MinecraftForge.fillCustomBucket(world, 4, 64, 4) == null
                        && world.getBlockId(4, 64, 4) == flowingId,
                "A partial fluid level is not collected");

        world.setBlockMetadata(4, 64, 4, 0);
        world.rejectNextRemoval = true;
        ItemStack failedCollection = MinecraftForge.fillCustomBucket(world, 4, 64, 4);
        require(failedCollection != null && failedCollection.itemID == Item.bucketEmpty.shiftedIndex
                        && world.getBlockId(4, 64, 4) == flowingId,
                "Failed source removal leaves both the source and empty bucket unchanged");

        int uncontainedId = uncontained.definition().flowingBlockId();
        world.setBlockAndMetadata(4, 64, 4, uncontainedId, 0);
        ItemStack unsupported = MinecraftForge.fillCustomBucket(world, 4, 64, 4);
        require(unsupported != null && unsupported.itemID == Item.bucketEmpty.shiftedIndex
                        && world.getBlockId(4, 64, 4) == uncontainedId,
                "A custom source without a bucket blocks vanilla carrier fallback without changing the world");
    }

    private static void verifyPlacement(FluidRegistration contained, FluidRegistration uncontained,
            FluidBucketItem bucket) {
        require(bucket.getItemStackLimit() == 1 && bucket.getContainerItem() == Item.bucketEmpty,
                "A filled custom bucket has the vanilla empty crafting remainder");
        TestWorld world = new TestWorld(false);
        TestPlayer player = new TestPlayer(world);
        ItemStack held = new ItemStack(bucket);
        world.preparePlacement(6, 64, 6, 1);
        world.deny(6, 65, 6);
        require(bucket.onItemRightClick(held, world, player) == held && world.getBlockId(6, 65, 6) == 0,
                "Destination permission failure preserves the held bucket and world");
        require(world.permissionChecks == 2, "Bucket placement checks both selected and destination coordinates");

        world.allowAll();
        ItemStack emptied = bucket.onItemRightClick(held, world, player);
        require(emptied.itemID == Item.bucketEmpty.shiftedIndex
                        && world.getBlockId(6, 65, 6) == contained.definition().flowingBlockId()
                        && world.getBlockMetadata(6, 65, 6) == 0,
                "Successful placement creates a flowing-role source and returns the empty bucket");

        ItemStack solidBlocked = new ItemStack(bucket);
        world.preparePlacement(7, 64, 7, 1);
        world.setBlockAndMetadata(7, 65, 7, Block.stone.blockID, 0);
        require(bucket.onItemRightClick(solidBlocked, world, player) == solidBlocked
                        && world.getBlockId(7, 65, 7) == Block.stone.blockID,
                "Bucket placement cannot replace a solid destination");

        ItemStack fluidBlocked = new ItemStack(bucket);
        world.preparePlacement(8, 64, 8, 1);
        world.setBlockAndMetadata(8, 65, 8, uncontained.definition().flowingBlockId(), 0);
        require(bucket.onItemRightClick(fluidBlocked, world, player) == fluidBlocked
                        && world.getBlockId(8, 65, 8) == uncontained.definition().flowingBlockId(),
                "Bucket placement cannot replace another registered fluid");

        ItemStack rejected = new ItemStack(bucket);
        world.preparePlacement(9, 64, 9, 1);
        world.rejectNextPlacement = true;
        require(bucket.onItemRightClick(rejected, world, player) == rejected && world.getBlockId(9, 65, 9) == 0,
                "Failed world mutation does not consume a filled bucket");

        TestWorld hell = new TestWorld(true);
        TestPlayer hellPlayer = new TestPlayer(hell);
        hell.preparePlacement(10, 64, 10, 1);
        ItemStack evaporated = bucket.onItemRightClick(new ItemStack(bucket), hell, hellPlayer);
        require(evaporated.itemID == Item.bucketEmpty.shiftedIndex && hell.getBlockId(10, 65, 10) == 0,
                "Water-preset bucket evaporation consumes the contents without placing a block");
    }

    private static int[] freeBlockIds(int count) {
        int[] result = new int[count];
        int found = 0;
        for (int id = 239; id >= 1 && found < count; id--) {
            if (Block.blocksList[id] == null && FluidRegistry.instance().getByBlockId(id) == null) {
                result[found++] = id;
            }
        }
        if (found != count) {
            throw new AssertionError("Not enough free block IDs for fluid bucket tests");
        }
        return result;
    }

    private static int freeItemId() {
        for (int id = Item.itemsList.length - 1; id >= 256; id--) {
            if (Item.itemsList[id] == null && FluidRegistry.instance().getByBucketItemId(id) == null) {
                return id;
            }
        }
        throw new AssertionError("No free item ID for fluid bucket tests");
    }

    private static final class TestWorld extends World {
        private final Chunk chunk;
        private MovingObjectPosition hit;
        private int deniedX = Integer.MIN_VALUE;
        private int deniedY;
        private int deniedZ;
        private int permissionChecks;
        private boolean rejectNextRemoval;
        private boolean rejectNextPlacement;

        private TestWorld(boolean hell) {
            super(null, "fluid_bucket_test", provider(hell), 123L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
        }

        private static WorldProvider provider(boolean hell) {
            WorldProvider provider = new WorldProvider() {
            };
            provider.isHellWorld = hell;
            return provider;
        }

        private void preparePlacement(int x, int y, int z, int side) {
            setBlockAndMetadata(x, y, z, Block.stone.blockID, 0);
            hit = new MovingObjectPosition(x, y, z, side, Vec3D.createVector(x + 0.5D, y + 0.5D, z + 0.5D));
            permissionChecks = 0;
        }

        private void deny(int x, int y, int z) {
            deniedX = x;
            deniedY = y;
            deniedZ = z;
        }

        private void allowAll() {
            deniedX = Integer.MIN_VALUE;
            permissionChecks = 0;
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                @Override
                public boolean chunkExists(int x, int z) {
                    return x == 0 && z == 0;
                }

                @Override
                public Chunk provideChunk(int x, int z) {
                    return chunk;
                }

                @Override
                public Chunk prepareChunk(int x, int z) {
                    return chunk;
                }

                @Override
                public void populate(IChunkProvider provider, int x, int z) {
                }

                @Override
                public boolean saveChunks(boolean force, IProgressUpdate progress) {
                    return true;
                }

                @Override
                public boolean unload100OldestChunks() {
                    return false;
                }

                @Override
                public boolean canSave() {
                    return false;
                }

                @Override
                public String makeString() {
                    return "FluidBucketTest";
                }
            };
        }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < 128;
        }

        @Override
        public MovingObjectPosition rayTraceBlocks_do(Vec3D start, Vec3D end, boolean hitLiquids) {
            return hit;
        }

        @Override
        public boolean func_6466_a(EntityPlayer player, int x, int y, int z) {
            permissionChecks++;
            return x != deniedX || y != deniedY || z != deniedZ;
        }

        @Override
        public boolean setBlockWithNotify(int x, int y, int z, int blockId) {
            if (rejectNextRemoval && blockId == 0) {
                rejectNextRemoval = false;
                return false;
            }
            return super.setBlockWithNotify(x, y, z, blockId);
        }

        @Override
        public boolean setBlockAndMetadataWithNotify(int x, int y, int z, int blockId, int metadata) {
            if (rejectNextPlacement && blockId != 0) {
                rejectNextPlacement = false;
                return false;
            }
            return super.setBlockAndMetadataWithNotify(x, y, z, blockId, metadata);
        }
    }

    private static final class TestPlayer extends EntityPlayer {
        private TestPlayer(World world) {
            super(world);
        }

        @Override
        public void func_6420_o() {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
