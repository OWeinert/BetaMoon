package betamoon.worldgen;

import betamoon.loot.InventoryLootOperation;
import betamoon.loot.LootStackDefinition;
import betamoon.loot.LootTableDefinition;
import betamoon.loot.LootTableRegistry;
import betamoon.worldgen.structure.SitePolicy;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureProcessors;
import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.TerrainPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IInventory;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/** Verifies deterministic loot tables, typed structure loot, and atomic inventory application. */
public final class StructureLootTest {
    private StructureLootTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks and items are initialized");
        verifiesDeterministicPoolsAndStackMetadata();
        rejectsNestedWorstCaseOutputDuringPublication();
        placesFixedAndRandomLootInLocalStructures();
        rollsBackWhenTheTargetIsNotAnInventory();
        verifiesInsertionPolicies();
        parsesBundledLootTableExamples();
        rollsBackBlocksAndInventoryOnConflict();
        rejectsLegacyLootMarkersAndFlattenedStacks();
        System.out.println("Structure loot checks passed.");
    }

    private static void parsesBundledLootTableExamples() throws Exception {
        int parsed = 0;
        try (Stream<Path> paths = Files.walk(Paths.get("examples"))) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/loot_tables/") && normalized.endsWith(".json")) {
                    LootTableDefinition.read(Files.readAllBytes(path));
                    parsed++;
                }
            }
        }
        require(parsed >= 1, "Bundled loot-table JSON examples use the public format");
    }

    private static void rollsBackWhenTheTargetIsNotAnInventory() throws Exception {
        String structure = "{\"format\":\"betamoon_structure\",\"palette\":{"
                + "\"stone\":{\"block\":" + Block.stone.blockID + "}},\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"stone\"},"
                + "{\"type\":\"loot\",\"pos\":[0,0,0],\"items\":[{\"slot\":0,\"stack\":{"
                + "\"item\":" + Item.ingotIron.shiftedIndex + "}}]}]}";
        StructureFeature feature = new StructureFeature(
                StructureTemplate.read(structure.getBytes(StandardCharsets.UTF_8)), "memory:not_inventory.json",
                "none", "none", new StructureProcessors(false, Collections.<Integer, Integer>emptyMap(), 0.0D,
                        null, "reject", "reject"), TerrainPolicy.EXACT);
        TestWorld world = new TestWorld();
        FeatureContext context = context(world, 17L);
        PlacementPlan plan = new PlacementPlan(new BlockPosition(7, 64, 7), 8, 8);
        require(feature.plan(context, new BlockPosition(7, 64, 7), plan).placed,
                "A statically unknown inventory target can be planned");
        FeatureResult result = plan.commit(context);
        require(!result.placed && result.reason.equals(FeatureResult.LOOT_TARGET_MISSING)
                && world.getBlockId(7, 64, 7) == 0,
                "A non-inventory target reports a focused reason and rolls back its block");
    }

    private static void verifiesInsertionPolicies() {
        LootStackDefinition.LootStack iron = new LootStackDefinition.LootStack(Item.ingotIron.shiftedIndex, 2, 1);
        LootStackDefinition.LootStack diamond = new LootStackDefinition.LootStack(Item.diamond.shiftedIndex, 1, 0);

        TestInventory preserved = new TestInventory(4);
        preserved.setInventorySlotContents(0, new ItemStack(Item.coal, 1, 0));
        InventoryLootOperation ordered = InventoryLootOperation.random(Arrays.asList(iron, diamond),
                InventoryLootOperation.SlotMode.ORDERED_EMPTY, Collections.<Integer>emptyList(),
                InventoryLootOperation.ExistingPolicy.PRESERVE, InventoryLootOperation.OverflowPolicy.DISCARD, 1L);
        require(ordered.apply(preserved) == InventoryLootOperation.Result.APPLIED
                && preserved.getStackInSlot(0).itemID == Item.coal.shiftedIndex
                && preserved.getStackInSlot(1).itemID == Item.ingotIron.shiftedIndex
                && preserved.getStackInSlot(2).itemID == Item.diamond.shiftedIndex,
                "Ordered preserve fills the lowest empty slots without changing existing contents");

        TestInventory tooSmall = new TestInventory(1);
        InventoryLootOperation rejecting = InventoryLootOperation.random(Arrays.asList(iron, diamond),
                InventoryLootOperation.SlotMode.EXPLICIT, Collections.singletonList(Integer.valueOf(0)),
                InventoryLootOperation.ExistingPolicy.PRESERVE, InventoryLootOperation.OverflowPolicy.REJECT, 2L);
        require(rejecting.apply(tooSmall) == InventoryLootOperation.Result.OVERFLOW
                && tooSmall.getStackInSlot(0) == null,
                "Reject overflow does not partially mutate the inventory");

        TestInventory replaced = new TestInventory(2);
        replaced.setInventorySlotContents(0, new ItemStack(Item.coal, 1, 0));
        replaced.setInventorySlotContents(1, new ItemStack(Item.stick, 1, 0));
        InventoryLootOperation replacing = InventoryLootOperation.random(Collections.singletonList(iron),
                InventoryLootOperation.SlotMode.ORDERED_EMPTY, Collections.<Integer>emptyList(),
                InventoryLootOperation.ExistingPolicy.REPLACE, InventoryLootOperation.OverflowPolicy.DISCARD, 3L);
        require(replacing.apply(replaced) == InventoryLootOperation.Result.APPLIED
                && replaced.getStackInSlot(0).itemID == Item.ingotIron.shiftedIndex
                && replaced.getStackInSlot(1) == null,
                "Replace clears every eligible slot before insertion");

        InventoryLootOperation.FixedStack invalid = new InventoryLootOperation.FixedStack(2, iron);
        InventoryLootOperation fixed = InventoryLootOperation.fixed(Collections.singletonList(invalid),
                InventoryLootOperation.ExistingPolicy.REQUIRE_EMPTY);
        require(fixed.apply(new TestInventory(1)) == InventoryLootOperation.Result.INCOMPATIBLE_SIZE,
                "Fixed slots are checked against the concrete inventory size");
    }

    private static void rejectsNestedWorstCaseOutputDuringPublication() throws Exception {
        LootTableRegistry.clear();
        try (LootTableRegistry.PublicationBatch batch = LootTableRegistry.beginPublication("test", "test")) {
            String child = "{\"format\":\"betamoon_loot_table\",\"pools\":[{"
                    + "\"key\":\"child\",\"rolls\":3,\"entries\":[{\"type\":\"item\",\"stack\":{"
                    + "\"item\":" + Item.ingotIron.shiftedIndex + "}}]}]}";
            String parent = "{\"format\":\"betamoon_loot_table\",\"pools\":[{"
                    + "\"key\":\"parent\",\"rolls\":64,\"entries\":[{\"type\":\"table\","
                    + "\"table\":\"test:child\"}]}]}";
            LootTableRegistry.add("test:child",
                    LootTableDefinition.read(child.getBytes(StandardCharsets.UTF_8)), "memory:child");
            LootTableRegistry.add("test:parent",
                    LootTableDefinition.read(parent.getBytes(StandardCharsets.UTF_8)), "memory:parent");
            try {
                batch.validate();
                throw new AssertionError("Expected nested output budget validation to fail");
            } catch (java.io.IOException error) {
                require(error.getMessage().contains("can emit up to"),
                        "Nested worst-case output rejects during publication");
            }
        } finally {
            LootTableRegistry.clear();
        }
    }

    private static void verifiesDeterministicPoolsAndStackMetadata() throws Exception {
        String json = "{\"format\":\"betamoon_loot_table\",\"pools\":["
                + "{\"key\":\"guaranteed\",\"rolls\":1,\"entries\":["
                + "{\"type\":\"item\",\"stack\":{\"item\":" + Item.ingotIron.shiftedIndex
                + ",\"count\":2,\"damage\":3}}]},"
                + "{\"key\":\"random\",\"rolls\":{\"min\":2,\"max\":4},\"entries\":["
                + "{\"type\":\"item\",\"weight\":3,\"stack\":{\"item\":"
                + Item.diamond.shiftedIndex + ",\"count\":{\"min\":1,\"max\":4}}},"
                + "{\"type\":\"empty\",\"weight\":1}]}]}";
        LootTableDefinition table = LootTableDefinition.read(json.getBytes(StandardCharsets.UTF_8));
        List<LootStackDefinition.LootStack> first = table.generate(123456L);
        List<LootStackDefinition.LootStack> second = table.generate(123456L);
        require(same(first, second), "The same loot seed reproduces all stacks and metadata");
        require(!first.isEmpty() && first.get(0).itemId == Item.ingotIron.shiftedIndex
                && first.get(0).count == 2 && first.get(0).damage == 3,
                "A pool emits the complete shared item-stack definition");
        Set<String> variants = new HashSet<String>();
        for (long seed = 0L; seed < 64L; seed++) {
            variants.add(semantics(table.generate(seed)));
        }
        require(variants.size() > 1, "Different placement seeds vary randomized loot across a useful sample");

        String blockItemJson = "{\"format\":\"betamoon_loot_table\",\"pools\":[{"
                + "\"key\":\"block_item\",\"entries\":[{\"type\":\"item\",\"stack\":{"
                + "\"item\":\"minecraft:block/stone\"}}]}]}";
        LootTableDefinition blockItemTable = LootTableDefinition.read(
                blockItemJson.getBytes(StandardCharsets.UTF_8));
        require(blockItemTable.generate(1L).get(0).itemId == Block.stone.blockID,
                "Canonical block-item keys resolve into inventory stacks");
    }

    private static void placesFixedAndRandomLootInLocalStructures() throws Exception {
        String structure = "{\"format\":\"betamoon_structure\",\"palette\":{"
                + "\"chest\":{\"block\":" + Block.chest.blockID + "}},\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"chest\"},"
                + "{\"type\":\"loot\",\"key\":\"main_chest\",\"pos\":[0,0,0],\"pools\":["
                + "{\"key\":\"main\",\"rolls\":2,\"entries\":["
                + "{\"type\":\"item\",\"stack\":{\"item\":" + Item.bread.shiftedIndex + "}},"
                + "{\"type\":\"item\",\"stack\":{\"item\":" + Item.ingotIron.shiftedIndex
                + ",\"damage\":2}}]}]}]}";
        StructureTemplate template = StructureTemplate.read(structure.getBytes(StandardCharsets.UTF_8));
        require(template.loots.size() == 1 && template.resolve(55L).loots.size() == 1,
                "Loot compiles as a typed resolved structure element");
        StructureFeature feature = new StructureFeature(template, "memory:loot.json", "none", "none",
                new StructureProcessors(false, Collections.<Integer, Integer>emptyMap(), 0.0D, null, "reject",
                        "reject"), TerrainPolicy.EXACT);
        TestWorld world = new TestWorld();
        FeatureContext context = context(world, 99L);
        PlacementPlan plan = new PlacementPlan(new BlockPosition(4, 64, 4), 8, 8);
        require(feature.plan(context, new BlockPosition(4, 64, 4), plan).placed
                && plan.inventoryOperationCount() == 1, "Local planning includes one inventory operation");
        require(plan.commit(context).placed, "The chest block and loot commit atomically");
        TileEntity tile = world.getBlockTileEntity(4, 64, 4);
        require(tile instanceof IInventory, "The structure placed a vanilla inventory");
        IInventory inventory = (IInventory) tile;
        int occupied = 0;
        boolean metadata = false;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null) {
                occupied++;
                metadata |= stack.itemID == Item.ingotIron.shiftedIndex && stack.getItemDamage() == 2;
            }
        }
        require(occupied == 2 && metadata, "Every item result receives a deterministic empty slot and metadata");
    }

    private static void rollsBackBlocksAndInventoryOnConflict() {
        TestWorld world = new TestWorld();
        world.setBlockAndMetadata(5, 64, 5, Block.chest.blockID, 0);
        IInventory chest = (IInventory) world.getBlockTileEntity(5, 64, 5);
        chest.setInventorySlotContents(0, new ItemStack(Item.ingotIron, 1, 0));

        PlacementPlan plan = new PlacementPlan(new BlockPosition(5, 64, 5), 8, 8);
        plan.setBlock(6, 64, 5, Block.stone.blockID, 0);
        InventoryLootOperation.FixedStack fixed = new InventoryLootOperation.FixedStack(0,
                new LootStackDefinition.LootStack(Item.bread.shiftedIndex, 1, 0));
        plan.addInventoryLoot(5, 64, 5, InventoryLootOperation.fixed(Collections.singletonList(fixed),
                InventoryLootOperation.ExistingPolicy.REQUIRE_EMPTY));
        FeatureResult result = plan.commit(context(world, 8L));
        require(!result.placed && result.reason.equals(FeatureResult.LOOT_EXISTING_CONTENTS)
                && world.getBlockId(6, 64, 5) == 0,
                "A loot conflict rolls back other block changes");
        ItemStack retained = chest.getStackInSlot(0);
        require(retained != null && retained.itemID == Item.ingotIron.shiftedIndex,
                "Rollback preserves the inventory that caused the conflict");
    }

    private static void rejectsLegacyLootMarkersAndFlattenedStacks() throws Exception {
        expectStructureFailure("{\"format\":\"betamoon_structure\",\"palette\":{"
                + "\"chest\":{\"block\":" + Block.chest.blockID + "}},\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"chest\"},"
                + "{\"type\":\"marker\",\"pos\":[0,0,0],\"name\":\"loot\"}]}",
                "dedicated element type");
        try {
            LootTableDefinition.read(("{\"format\":\"betamoon_loot_table\",\"pools\":[{"
                    + "\"key\":\"main\",\"rolls\":1,\"entries\":[{\"type\":\"item\","
                    + "\"item\":" + Item.appleRed.shiftedIndex + "}]}]}")
                    .getBytes(StandardCharsets.UTF_8));
            throw new AssertionError("Expected flattened stack fields to fail");
        } catch (java.io.IOException error) {
            require(error.getMessage().contains("unsupported field") || error.getMessage().contains("stack"),
                    "Flattened item fields produce a focused validation error");
        }
    }

    private static FeatureContext context(World world, long seed) {
        return new FeatureContext(world, new Random(seed),
                WorldGenKey.parse("test:feature/loot", WorldGenKind.FEATURE), 32768, null,
                FeatureOptions.DEFAULT, SitePolicy.ANY);
    }

    private static boolean same(List<LootStackDefinition.LootStack> left,
            List<LootStackDefinition.LootStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            LootStackDefinition.LootStack a = left.get(index);
            LootStackDefinition.LootStack b = right.get(index);
            if (a.itemId != b.itemId || a.count != b.count || a.damage != b.damage) {
                return false;
            }
        }
        return true;
    }

    private static String semantics(List<LootStackDefinition.LootStack> stacks) {
        StringBuilder result = new StringBuilder();
        for (LootStackDefinition.LootStack stack : stacks) {
            result.append(stack.itemId).append(':').append(stack.count).append(':').append(stack.damage).append('|');
        }
        return result.toString();
    }

    private static void expectStructureFailure(String json, String message) throws Exception {
        try {
            StructureTemplate.read(json.getBytes(StandardCharsets.UTF_8));
            throw new AssertionError("Expected structure parsing to fail");
        } catch (java.io.IOException error) {
            require(error.getMessage().contains(message), "Expected failure containing " + message);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private final Chunk chunk;

        private TestWorld() {
            super(null, "structure_loot_test", new WorldProvider() {
            }, 123L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
            chunkProvider = new IChunkProvider() {
                public boolean chunkExists(int x, int z) { return x == 0 && z == 0; }
                public Chunk provideChunk(int x, int z) { return chunk; }
                public Chunk prepareChunk(int x, int z) { return chunk; }
                public void populate(IChunkProvider provider, int x, int z) { }
                public boolean saveChunks(boolean force, IProgressUpdate progress) { return true; }
                public boolean unload100OldestChunks() { return false; }
                public boolean canSave() { return false; }
                public String makeString() { return "StructureLootTest"; }
            };
        }

        @Override
        protected IChunkProvider getChunkProvider() { return null; }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) { return chunk; }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < 128;
        }
    }

    private static final class TestInventory implements IInventory {
        private final ItemStack[] contents;

        private TestInventory(int size) {
            contents = new ItemStack[size];
        }

        public int getSizeInventory() {
            return contents.length;
        }

        public ItemStack getStackInSlot(int slot) {
            return contents[slot];
        }

        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = contents[slot];
            if (stack == null || amount <= 0) {
                return null;
            }
            if (stack.stackSize <= amount) {
                contents[slot] = null;
                return stack;
            }
            return stack.splitStack(amount);
        }

        public void setInventorySlotContents(int slot, ItemStack stack) {
            contents[slot] = stack;
        }

        public String getInvName() {
            return "structure-loot-test";
        }

        public int getInventoryStackLimit() {
            return 64;
        }

        public void onInventoryChanged() {
        }

        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}
