package betamoon.dimension;

import betamoon.assets.model.ModelJson;
import betamoon.dimension.storage.DimensionMapStorage;
import betamoon.dimension.storage.DimensionMetadataStore;
import betamoon.dimension.storage.DimensionPaths;
import betamoon.dimension.storage.DimensionSaveHandler;
import betamoon.system.BetaMoonWorldData;
import betamoon.worldgen.terrain.ChunkBlockBuffer;
import betamoon.worldgen.terrain.ProfileWorldChunkManager;
import betamoon.worldgen.terrain.RawChunkFinalizer;
import betamoon.worldgen.terrain.TerrainRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.ChunkProviderHell;
import net.minecraft.src.ChunkProviderSky;
import net.minecraft.src.IChunkLoader;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.MapData;
import net.minecraft.src.MapStorage;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.SaveHandler;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import net.minecraft.src.WorldProviderHell;
import net.minecraft.src.WorldProviderSky;
import net.minecraft.src.WorldProviderSurface;

/** Native format, independent storage and shared native-map ownership gates. */
public final class DimensionStorageTest {
    public static void main(String[] args) throws Exception {
        require(Block.stone != null, "Initialize native blocks");
        isolated(false);
        isolated(true);
        nativeFamilies();
        savedPlayerIdentity();
        System.out.println("Dimension storage passed: old/McRegion chunk isolation, metadata, shared map IDs/cache, "
                + "native generator family parity and strict player identity.");
    }

    private static void isolated(boolean regionFormat) throws Exception {
        Path root = Files.createTempDirectory("betamoon-dimension-chunks-");
        ISaveHandler nativeSave = regionFormat
                ? new SaveOldDir(root.toFile(), "world", false)
                : new SaveHandler(root.toFile(), "world", false);
        DimensionPaths paths = new DimensionPaths(root.resolve("world"));
        DimensionGenerationBinding first = binding("test:first", "overworld");
        DimensionGenerationBinding second = binding("test:second", "overworld");
        DimensionMetadataStore firstMeta = new DimensionMetadataStore(paths, first, 2, 42);
        DimensionMetadataStore secondMeta = new DimensionMetadataStore(paths, second, 3, 42);
        firstMeta.initialize(0.5, 65, 0.5, 0, 0);
        secondMeta.initialize(0.5, 65, 0.5, 0, 0);
        DimensionSaveHandler one = new DimensionSaveHandler(nativeSave, firstMeta, paths, player -> {
            throw new AssertionError("No root player may be written by a detached world");
        });
        DimensionSaveHandler two = new DimensionSaveHandler(nativeSave, secondMeta, paths, player -> {
            throw new AssertionError("No root player may be written by a detached world");
        });
        World firstWorld = world(one, first, firstMeta);
        World secondWorld = world(two, second, secondMeta);
        MapStorage global = new MapStorage(nativeSave);
        firstWorld.field_28108_z = new DimensionMapStorage(one, global);
        secondWorld.field_28108_z = new DimensionMapStorage(two, global);
        MapData map = new MapData("map_0");
        firstWorld.setItemData("map_0", map);
        require(secondWorld.loadItemData(MapData.class, "map_0") == map && firstWorld.getUniqueDataId("map") == 0
                && secondWorld.getUniqueDataId("map") == 1,
                "Custom worlds must share one native map cache and ID allocator");
        BetaMoonWorldData localOne = new BetaMoonWorldData("betamoon_systems");
        BetaMoonWorldData localTwo = new BetaMoonWorldData("betamoon_systems");
        localOne.markDirty();
        localTwo.markDirty();
        firstWorld.setItemData("betamoon_systems", localOne);
        secondWorld.setItemData("betamoon_systems", localTwo);
        require(firstWorld.loadItemData(BetaMoonWorldData.class, "betamoon_systems") == localOne
                && secondWorld.loadItemData(BetaMoonWorldData.class, "betamoon_systems") == localTwo,
                "Identical service names must remain dimension-local");
        IChunkLoader firstLoader = one.getChunkLoader(firstWorld.worldProvider);
        IChunkLoader secondLoader = two.getChunkLoader(secondWorld.worldProvider);
        ChunkBlockBuffer blocks = new ChunkBlockBuffer();
        blocks.set(3, 42, 7, Block.cloth.blockID, 7);
        Chunk firstChunk = RawChunkFinalizer.build(firstWorld, -2, 3, blocks);
        blocks.set(3, 42, 7, Block.cloth.blockID, 11);
        Chunk secondChunk = RawChunkFinalizer.build(secondWorld, -2, 3, blocks);
        firstLoader.saveChunk(firstWorld, firstChunk);
        secondLoader.saveChunk(secondWorld, secondChunk);
        firstLoader.saveExtraData();
        secondLoader.saveExtraData();
        Chunk firstSaved = firstLoader.loadChunk(firstWorld, -2, 3);
        Chunk secondSaved = secondLoader.loadChunk(secondWorld, -2, 3);
        require(firstSaved.getBlockMetadata(3, 42, 7) == 7 && secondSaved.getBlockMetadata(3, 42, 7) == 11,
                "Equal chunk coordinates must persist independent blocks/metadata");
        firstWorld.setWorldTime(938);
        secondWorld.setWorldTime(17);
        one.saveWorldInfoAndPlayer(firstWorld.getWorldInfo(), Collections.emptyList());
        two.saveWorldInfoAndPlayer(secondWorld.getWorldInfo(), Collections.emptyList());
        require(one.loadWorldInfo().getWorldTime() == 938 && two.loadWorldInfo().getWorldTime() == 17,
                "Dimension-local time must survive save/reopen independently");
        require(!Files.exists(root.resolve("world/level.dat")) && !Files.exists(root.resolve("world/DIM-1"))
                && !Files.exists(root.resolve("world/region")),
                "Custom chunk/metadata saves must not write native directories or level metadata");
        firstWorld.field_28108_z.saveAllData();
        secondWorld.field_28108_z.saveAllData();
        require(Files.exists(paths.resolve("betamoon/dimensions/test/dimension/first/data/betamoon_systems.dat"))
                && Files.exists(paths.resolve("betamoon/dimensions/test/dimension/second/data/betamoon_systems.dat")),
                "Services must be saved under their own dimension paths");
    }

    private static World world(DimensionSaveHandler save, DimensionGenerationBinding binding,
            DimensionMetadataStore metadata) {
        return new World(save, "dimension-test", metadata.seed,
                new DimensionWorldProvider(binding, metadata.numericId));
    }

    private static void nativeFamilies() throws Exception {
        Path root = Files.createTempDirectory("betamoon-dimension-families-");
        ISaveHandler save = new SaveOldDir(root.toFile(), "world", false);
        for (String family : new String[]{"overworld", "nether", "sky"}) {
            DimensionGenerationBinding binding = binding("test:" + family, family);
            World custom = new World(save, "custom", new DimensionWorldProvider(binding, 2), 42);
            WorldProvider provider = family.equals("nether")
                    ? new WorldProviderHell()
                    : family.equals("sky") ? new WorldProviderSky() : new WorldProviderSurface();
            World nativeWorld = new World(save, "native", provider, 42);
            custom.worldProvider.worldChunkMgr = new ProfileWorldChunkManager(custom.getWorldChunkManager(), null);
            BiomeGenBase[] selected = custom.getWorldChunkManager().loadBlockGeneratorData(null, -16, 32, 16, 16);
            require(selected[0] == nativeWorld.getWorldChunkManager().getBiomeGenAt(-16, 32),
                    "Pinned biome managers must preserve fixed Hell/Sky climates and their missing variation samples");
            IChunkProvider original = family.equals("nether")
                    ? new ChunkProviderHell(nativeWorld, 42)
                    : family.equals("sky")
                            ? new ChunkProviderSky(nativeWorld, 42)
                            : new ChunkProviderGenerate(nativeWorld, 42);
            IChunkProvider candidate = custom.worldProvider.getChunkProvider();
            for (int[] coordinate : new int[][]{{0, 0}, {-2, 3}, {3, -2}}) {
                require(Arrays.equals(original.provideChunk(coordinate[0], coordinate[1]).blocks,
                        candidate.provideChunk(coordinate[0], coordinate[1]).blocks),
                        "Built-in dimension terrain must match its native family and shared seed: " + family);
            }
        }
    }

    private static void savedPlayerIdentity() throws Exception {
        Path root = Files.createTempDirectory("betamoon-dimension-player-");
        ISaveHandler save = new SaveOldDir(root.toFile(), "world", false);
        World source = new World(save, "native", new WorldProviderSurface(), 42);
        DimensionSession session = DimensionSession.get(source, save);
        int id = session.index.allocate(DimensionKey.parse("test:moon"));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Dimension", id);
        tag.setString("BetaMoonDimension", "test:dimension/moon");
        require(session.savedDimension(tag).equals(DimensionKey.parse("test:moon")),
                "Saved canonical identity must resolve through this save's ID index");
        tag.setInteger("Dimension", 0);
        try {
            session.savedDimension(tag);
            throw new AssertionError("Canonical/numeric disagreement must not silently load Overworld");
        } catch (IOException expected) {
            require(expected.getMessage().contains("disagree"), "Report saved identity disagreement");
        }
    }

    private static DimensionGenerationBinding binding(String key, String family) throws Exception {
        String json = "{\"key\":\"" + key + "\",\"seed\":{\"type\":\"shared\"},"
                + "\"generator\":{\"type\":\"builtin\",\"name\":\"" + family + "\"}}";
        return new DimensionGenerationBinding(
                new DimensionDefinition("storage.lua", ModelJson.read(json.getBytes(StandardCharsets.UTF_8))),
                TerrainRegistry.snapshot());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
