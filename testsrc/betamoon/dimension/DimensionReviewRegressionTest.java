package betamoon.dimension;

import betamoon.assets.model.ModelJson;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.luaapi.world.DimensionsApi;
import betamoon.teleport.ChunkPreparation;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.HeightProvider;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.PlacementConditions;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.creation.GenerationCatalog;
import betamoon.worldgen.creation.GenerationDraft;
import betamoon.worldgen.creation.GenerationSelection;
import betamoon.worldgen.creation.GenerationSelectionStore;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.ScalarFieldCompiler;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.spatial.SpatialSelector;
import betamoon.worldgen.terrain.GeneratorProfileRegistry;
import betamoon.worldgen.terrain.ProfileWorldChunkManager;
import betamoon.worldgen.terrain.PopulationPolicy;
import betamoon.worldgen.terrain.TerrainRegistry;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.BaseMod;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.ModLoader;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.World;
import net.minecraft.src.WorldChunkManagerHell;
import net.minecraft.src.WorldInfo;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;

/**
 * Integration regressions from the feature review, using real save and
 * ModLoader boundaries.
 */
final class DimensionReviewRegressionTest {
    private DimensionReviewRegressionTest() {
    }

    static void verify() throws Exception {
        nativeSaveRecovery();
        nativeFamilySelections();
        scopedSelectors();
        populationOwnership();
        failedCapabilities();
        System.out.println("Dimension review regressions passed: native recovery, provider choices/biomes, "
                + "scoped selectors, ModLoader population ownership and capability refusal.");
    }

    private static void nativeSaveRecovery() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-native-recovery-");
        SaveOldDir save = new SaveOldDir(directory.toFile(), "world", false);
        WorldInfo info = new WorldInfo(42, "ordinary");
        NBTTagCompound foreign = new NBTTagCompound();
        foreign.setInteger("Dimension", 7);
        info.setPlayerNBTTagCompound(foreign);
        save.saveWorldInfo(info);
        Path root = directory.resolve("world");
        byte[] original = Files.readAllBytes(root.resolve("level.dat"));
        require(SavedDimensionInfo.inspect(root) == null, "Foreign native identity belongs to the native loader");
        Files.move(root.resolve("level.dat"), root.resolve("level.dat_old"));
        require(SavedDimensionInfo.inspect(root) == null && save.loadWorldInfo() != null,
                "Missing ordinary primary retains native backup recovery");
        Files.write(root.resolve("level.dat"), new byte[]{1, 2, 3});
        require(SavedDimensionInfo.inspect(root) == null, "Corrupt ordinary primary does not enter dimension recovery");
        Files.delete(root.resolve("level.dat_old"));
        require(SavedDimensionInfo.inspect(root) == null, "Ordinary missing backup remains a native concern");
        Files.write(root.resolve("level.dat_old"), original);
        Files.createDirectories(root.resolve("betamoon"));
        Files.write(root.resolve("betamoon/dimensions.dat"), new byte[]{1});
        try {
            SavedDimensionInfo.inspect(root);
            throw new AssertionError("Owned corrupt primary must require checked recovery");
        } catch (IOException expected) {
            // Do not accept a possibly older player snapshot without journal validation.
        }
        Files.delete(root.resolve("level.dat"));
        try {
            SavedDimensionInfo.inspect(root);
            throw new AssertionError("Owned missing primary must require checked recovery");
        } catch (IOException expected) {
            require(expected.getMessage().contains("recovery_required"), "Explain the missing owned snapshot");
        }
        Files.delete(root.resolve("betamoon/dimensions.dat"));
        Files.createDirectories(root.resolve("betamoon/dimensions"));
        try {
            SavedDimensionInfo.inspect(root);
            throw new AssertionError("Orphaned dimension directories still require checked recovery");
        } catch (IOException expected) {
            require(expected.getMessage().contains("recovery_required"), "Do not replace an orphaned dimension save");
        }
        Path markerDirectory = Files.createTempDirectory("betamoon-player-marker-");
        SaveOldDir markerSave = new SaveOldDir(markerDirectory.toFile(), "world", false);
        foreign.setString("BetaMoonDimension", "minecraft:overworld");
        markerSave.saveWorldInfo(info);
        Path markerRoot = markerDirectory.resolve("world");
        Files.move(markerRoot.resolve("level.dat"), markerRoot.resolve("level.dat_old"));
        try {
            SavedDimensionInfo.inspect(markerRoot);
            throw new AssertionError("An owned backup player cannot be adopted without a checked primary");
        } catch (IOException expected) {
            require(expected.getMessage().contains("recovery_required"), "Explain backup-only player ownership");
        }
    }

    private static void nativeFamilySelections() throws Exception {
        for (String family : new String[]{"nether", "sky"}) {
            try {
                new WorldGeneratorProfile("review:generator/invalid", "review.lua",
                        Collections.singletonMap("native_base", family), null,
                        new PopulationPolicy("review:population/invalid", json("{\"native\":{\"trees\":false}}")), null,
                        null, false, true);
                throw new AssertionError("Invalid native policies must fail before publication into the picker");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("all or none"), "Explain unsupported native granularity");
            }
            WorldGeneratorProfile profile = new WorldGeneratorProfile("review:generator/" + family, "review.lua",
                    Collections.singletonMap("native_base", family));
            GeneratorProfileRegistry.replaceOwner("review.lua", Collections.singletonList(profile));
            GenerationDraft draft = new GenerationDraft(new GenerationCatalog(profile));
            require(draft.catalog.options.containsKey("native/all")
                    && !draft.catalog.options.containsKey("native/trees"),
                    "Non-granular providers expose only all/none native population");
            GenerationSelection selected = GenerationSelection.create(draft, 42);
            selected.validate(42);
            BiomeGenBase biome = family.equals("nether") ? BiomeGenBase.hell : BiomeGenBase.sky;
            require(selected.climate.get(0).equals("minecraft:biome/" + (family.equals("nether") ? "hell" : "sky")),
                    "Persist the effective family's native biome identity");
            ProfileWorldChunkManager manager = new ProfileWorldChunkManager(new WorldChunkManagerHell(biome, 1, 0),
                    null, selected);
            require(manager.getBiomeGenAt(-16, 8) == biome, "Untouched settings retain the native family's climate");
            Path file = Files.createTempDirectory("betamoon-family-settings-").resolve("settings.dat");
            GenerationSelectionStore store = new GenerationSelectionStore(file.toFile());
            store.write(selected);
            require(store.read().climate.equals(selected.climate), "Resolved climate survives save/reopen");
            draft.toggle("native/all");
            GenerationSelection disabled = GenerationSelection.create(draft, 42);
            require(selected.population().nativeCategories.size() == 9
                    && disabled.population().nativeCategories.isEmpty(),
                    "Every offered native toggle resolves to a supported population policy");
        }
        GeneratorProfileRegistry.replaceOwner("review.lua", Collections.emptyList());
    }

    private static void scopedSelectors() throws Exception {
        try (SpatialRegistry.Batch batch = SpatialRegistry.begin("review-scopes.lua")) {
            SpatialRegistry.add(WorldGenKind.FIELD,
                    json("{\"key\":\"review:field/scoped\",\"dimension\":\"overworld\",\"node\":1}"));
            SpatialRegistry.add(WorldGenKind.FIELD, json("{\"key\":\"review:field/composite\",\"node\":{"
                    + "\"type\":\"add\",\"inputs\":[\"review:field/scoped\",1]}}"));
            batch.publish();
        }
        SpatialSelector selector = new SpatialSelector(Collections.singletonMap("field", "review:field/composite"),
                "review.selector", SpatialRegistry.snapshot());
        FieldContext overworld = new FieldContext(42, 0);
        ScalarFieldCompiler reused = new ScalarFieldCompiler("review:reuse", SpatialRegistry.snapshot());
        require(!reused.compile("review:field/scoped").supports(new FieldContext(42, -1))
                && reused.compile(1).supports(new FieldContext(42, -1)),
                "Separate compiler requests cannot inherit a previous graph's dimension restriction");
        require(selector.weight(overworld, 0, 0) == 1, "Matching dimension still selects normally");
        for (FieldContext context : new FieldContext[]{new FieldContext(42, -1),
                new FieldContext(42, 2, "review:dimension/one", false, 0, 0, null, SpatialRegistry.snapshot()),
                new FieldContext(42, 3, "review:dimension/two", false, 0, 0, null, SpatialRegistry.snapshot())}) {
            require(selector.weight(context, 0, 0) == 0,
                    "All selector consumers reject incompatible transitive scopes without sampling");
            try {
                selector.value(context, 0, 0);
                throw new AssertionError("Direct field queries must still report scope misuse");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains("dimension"), "Direct query reports its scope");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void populationOwnership() throws Exception {
        int[] calls = new int[2];
        Field mods = ModLoader.class.getDeclaredField("modList");
        mods.setAccessible(true);
        List<BaseMod> installed = (List<BaseMod>) mods.get(null);
        BaseMod bridge = new BaseMod() {
            public String Version() {
                return "review";
            }

            public void GenerateNether(World world, Random random, int x, int z) {
                calls[1]++;
                WorldGenRegistry.generateNether(world, random, x, z);
            }

            public void GenerateSurface(World world, Random random, int x, int z) {
                calls[1]++;
                WorldGenRegistry.generateSurface(world, random, x, z);
            }
        };
        installed.add(bridge);
        try {
            for (String family : new String[]{"overworld", "nether", "sky"}) {
                for (String policy : new String[]{"none", "all", "legacy"}) {
                    calls[0] = 0;
                    calls[1] = 0;
                    String key = "review:dimension/" + family + "_" + policy;
                    try (WorldGenRegistry.PublicationBatch generation = WorldGenRegistry
                            .beginPublication("review-population.lua", "Test");
                            TerrainRegistry.Batch terrain = TerrainRegistry.begin("review-population.lua");
                            DimensionRegistry.Batch dimensions = DimensionRegistry.begin("review-population.lua")) {
                        WorldGenKey feature = WorldGenRegistry.addFeature("review:feature/count", "test",
                                (context, origin, plan) -> {
                                    calls[0]++;
                                    return FeatureResult.rejected("test");
                                }, Collections.emptyList(), 1, 1);
                        WorldGenRegistry.addPlacement("review:placement/count", feature,
                                GenerationStage.SURFACE_FEATURES, Collections.singleton(key), new IntRange(1, 1), 0, 1,
                                HeightProvider.fixed(64), PlacementConditions.any(), Collections.emptyList(),
                                Collections.emptyList(), 0, 0, 1, "chunk", 1);
                        TerrainRegistry.add(WorldGenKind.POPULATION,
                                json("{\"key\":\"review:population/policy\",\"native\":\""
                                        + (policy.equals("legacy") ? "none" : policy) + "\"}"));
                        DimensionRegistry.add(json("{\"key\":\"" + key + "\",\"generator\":{\"type\":\"builtin\","
                                + "\"name\":\"" + family + "\""
                                + (policy.equals("legacy") ? "" : ",\"population\":\"review:population/policy\"")
                                + "}}"));
                        generation.validate();
                        terrain.validate();
                        dimensions.validate();
                        generation.publish();
                        terrain.publish();
                        dimensions.publish();
                    }
                    SaveOldDir save = new SaveOldDir(
                            Files.createTempDirectory("betamoon-population-ownership-").toFile(), "world", false);
                    save.saveWorldInfo(new WorldInfo(42, "world"));
                    World root = new World(save, "world", 42, null);
                    World world = DimensionRuntime.create(DimensionQueries.session(root), DimensionKey.parse(key));
                    DimensionRuntime.binding(world).save.metadata.bootstrapAnchor(0, 64, 0);
                    ChunkPreparation chunks = new ChunkPreparation(world, 25);
                    chunks.reserve(0, 0, true);
                    for (int tick = 0; !chunks.step(); tick++) {
                        require(tick < 100, "Native family preparation remains bounded");
                    }
                    require(calls[0] == 4 && calls[1] == 4,
                            family + "/" + policy + ": one placement and other-mod callback per populated chunk; got "
                                    + calls[0] + "/" + calls[1]);
                    DimensionRuntime.releaseInactive(world);
                }
            }
        } finally {
            installed.remove(bridge);
        }
    }

    private static void failedCapabilities() throws Exception {
        DimensionCapabilities.requireReady(true);
        SaveOldDir save = new SaveOldDir(Files.createTempDirectory("betamoon-capabilities-").toFile(), "world", false);
        save.saveWorldInfo(new WorldInfo(42, "world"));
        DimensionSession session = DimensionQueries.session(new World(save, "world", 42, null));
        for (String suffix : new String[]{"sky", "clouds", "compass", "clock", "water", "lava", "sleep", "bed_disabled",
                "bed_explosion", "weather", "map:read", "map:write", "map:terrain_identity", "map:marker_identity",
                "native_portal", "portal_charge", "source_save", "player_read", "player_write", "respawn",
                "load_screen", "chunk_access", "chunk_budget", "population_budget", "prewarm", "freeze_updateEntities",
                "freeze_joinEntityInSurroundings", "freeze_randomDisplayUpdates"}) {
            String hook = "betamoon:dimensions:" + suffix;
            AgentRuntime.getReport().failed(hook, "Injected regression failure");
            try {
                try {
                    DimensionCapabilities.requireReady(true);
                    throw new AssertionError("Failed required hook must refuse activation: " + hook);
                } catch (DimensionCapabilities.Unavailable expected) {
                    require(expected.getMessage().contains(hook), "Capability refusal identifies the missing hook");
                }
                try {
                    DimensionRuntime.create(session, DimensionKey.parse("test:flat"));
                    throw new AssertionError("World creation cannot bypass failed capabilities");
                } catch (DimensionCapabilities.Unavailable expected) {
                    require(session.index.entries().isEmpty(), "Refusal cannot allocate a destination ID or storage");
                }
                LuaTable module = new LuaTable();
                DimensionsApi.attach(module);
                LuaTable declaration = new LuaTable();
                declaration.set("key", "review:unavailable");
                LuaTable generator = new LuaTable();
                generator.set("type", "builtin");
                generator.set("name", "overworld");
                declaration.set("generator", generator);
                try {
                    module.get("dimensions").get("add").call(declaration);
                    throw new AssertionError("Lua registration must reject failed required hooks");
                } catch (LuaError expected) {
                    require(expected.getMessage().contains(hook), "Lua refusal retains the hook diagnostic");
                }
            } finally {
                AgentRuntime.getReport().applied(hook, "restored regression fixture");
            }
        }
    }

    private static Map<String, Object> json(String value) throws IOException {
        return ModelJson.read(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
