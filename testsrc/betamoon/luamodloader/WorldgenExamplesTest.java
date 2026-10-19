package betamoon.luamodloader;

import betamoon.luaapi.item.ItemCallback;
import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.item.ItemDefinition;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.creation.GenerationCatalog;
import betamoon.worldgen.creation.GenerationDraft;
import betamoon.worldgen.creation.GenerationOption;
import betamoon.worldgen.creation.GenerationSelection;
import betamoon.worldgen.creation.GenerationSelectionStore;
import betamoon.worldgen.creation.WorldCreationSelection;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.terrain.BetaMoonChunkProvider;
import betamoon.worldgen.terrain.ChunkBlockBuffer;
import betamoon.worldgen.terrain.GenerationContext;
import betamoon.worldgen.terrain.GeneratorPreview;
import betamoon.worldgen.terrain.GeneratorSpawnSearch;
import betamoon.worldgen.terrain.ProfileWorldChunkManager;
import betamoon.worldgen.terrain.TerrainFoundationTest;
import betamoon.worldgen.terrain.TerrainGenerationOptimizationTest;
import betamoon.worldgen.terrain.TerrainRegistry;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Load the distributed JSON/Lua packages and exercise their actual raw
 * generators.
 */
public final class WorldgenExamplesTest {
    static void loadExamples(boolean includeBiomeExample) throws Exception {
        require(Block.stone != null, "Initialize native blocks");
        List<LuaScriptFiles.PreflightFailure> failures = new ArrayList<LuaScriptFiles.PreflightFailure>();
        List<LuaModSource> sources = new LuaScriptFiles().discover(new File("examples"), failures);
        require(failures.isEmpty(), "Example discovery must succeed");
        int loaded = 0;
        for (LuaModSource source : sources) {
            if (!source.ownerId().equals("03_adv_17_rolling_meadows")
                    && !source.ownerId().equals("03_adv_20_hollow_marches")
                    && !(includeBiomeExample && source.ownerId().equals("02_int_05_biome_from_scratch"))) {
                continue;
            }
            List<String> errors = new ArrayList<String>();
            ScriptMod mod = new ScriptModParser().parse(source, source.readEntrypoint(), errors);
            require(mod != null && errors.isEmpty(), "Example entrypoint failed: " + errors);
            String owner = source.entrypointRelative();
            try (ScriptExecutionScope scope = ScriptExecutionScope.open(owner);
                    SpatialRegistry.Batch spatial = SpatialRegistry.begin(owner);
                    TerrainRegistry.Batch terrain = TerrainRegistry.begin(owner);
                    WorldGenRegistry.PublicationBatch generation = WorldGenRegistry.beginPublication(owner, mod.name);
                    BiomeGenRegistry.PublicationBatch biomes = BiomeGenRegistry.beginPublication(owner, mod.name)) {
                mod.modInit.call();
                spatial.validate();
                generation.validate();
                biomes.validate();
                terrain.validate();
                generation.publish();
                biomes.publish();
                spatial.publish();
                terrain.publish();
            }
            loaded++;
        }
        require(loaded == (includeBiomeExample ? 3 : 2), "Requested worldgen example packages must load");
        if (includeBiomeExample) {
            BiomeGenRegistry.applyBiomeGenerators();
        }
    }

    public static void main(String[] args) throws Exception {
        loadExamples(true);
        verifyMeadowSettings();
        WorldGeneratorProfile simple = TerrainRegistry.snapshot().generator("example:generator/rolling_meadows");
        WorldGeneratorProfile complex = TerrainRegistry.snapshot().generator("example:generator/hollow_marches");
        verify(simple, false);
        verify(complex, true);
        TerrainGenerationOptimizationTest.verifyProfileOutput(simple);
        TerrainGenerationOptimizationTest.verifyProfileOutput(complex);
        surveyor(complex);
        GenerationDraft draft = new GenerationDraft(new GenerationCatalog(complex));
        require(draft.catalog.options.containsKey("placement/example:placement/marches_redstone"),
                "The new preset exposes its scheduled content in Generation Settings");
        draft.toggle("group/ores");
        require(!GenerationSelection.create(draft, 42).contentEnabled("example:placement/marches_redstone"),
                "The ore parent controls the new example's custom vein");
        System.out.println("Worldgen examples passed: real packages/JSON, safe spawn, bounded terrain, "
                + "deterministic previews, shared regions and settings eligibility.");
    }

    private static void verifyMeadowSettings() throws Exception {
        BiomeGenBase meadow = BiomeGenRegistry.biomeFor(WorldGenKey.parse("example:biome/meadow", WorldGenKind.BIOME));
        require(meadow != null, "The biome example registers Example Meadow");
        require(BiomeGenBase.getBiomeFromLookup(0.7, 0.8) == meadow,
                "The example's active climate source selects its biome before opening settings");
        GenerationDraft draft = new GenerationDraft(new GenerationCatalog(null));
        GenerationOption option = draft.catalog.options.get("biome/example:biome/meadow");
        require(option != null, "Example Meadow must be an eligible checkbox in Minecraft generation settings");
        require(option.label.equals("Example Meadow"), "The biome example exposes a readable settings name");
        for (int i = 0; i < 3; i++) {
            require(Arrays.asList(BiomeGenRegistry.copyClimateLookup()).contains(meadow),
                    "Climate snapshot reads must preserve scripted biome selection");
        }
        require(BiomeGenBase.getBiomeFromLookup(0.7, 0.8) == meadow,
                "Opening settings must not overwrite the live climate lookup");
        GenerationSelection enabled = GenerationSelection.create(draft, 42);
        require(enabled.climate.contains("example:biome/meadow") && enabled.biomeEnabled(meadow),
                "New worlds retain the selected climate source and custom biome");
        draft.toggle(option.key);
        GenerationSelection disabled = GenerationSelection.create(draft, 42);
        ProfileWorldChunkManager manager = new ProfileWorldChunkManager(
                new TerrainFoundationTest.Fixture(null, 42).getWorldChunkManager(), null, disabled);
        for (BiomeGenBase biome : manager.loadBlockGeneratorData(null, -64, -64, 128, 128)) {
            require(biome != meadow, "The custom biome checkbox filters generation when disabled");
        }
        Path file = Files.createTempDirectory("meadow-settings-").resolve("settings.dat");
        GenerationSelectionStore store = new GenerationSelectionStore(file.toFile());
        store.write(disabled);
        GenerationSelection reopened = store.read();
        require(!reopened.biomeEnabled(meadow) && reopened.climate.equals(enabled.climate),
                "Saved settings preserve both the climate selection and disabled custom biome");
    }

    private static void verify(WorldGeneratorProfile profile, boolean complex) throws Exception {
        require(profile != null, "Example registers a selectable generator");
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(
                new TerrainFoundationTest.Save(Files.createTempDirectory("betamoon-example-world-")), 42);
        ProfileWorldChunkManager manager = new ProfileWorldChunkManager(world.getWorldChunkManager(),
                profile.biomeSource);
        require(GeneratorSpawnSearch.find(42, profile, manager).y > 60, "Preset has a safe dry spawn");
        FieldContext fields = new FieldContext(42, 0, true, 0, 0, manager, profile.spatial);
        manager.setFieldContext(fields);
        int farX = 0;
        int farZ = 0;
        if (complex) {
            double strongest = 0;
            for (int x = -1536; x <= 1536; x += 64) {
                for (int z = -1536; z <= 1536; z += 64) {
                    double value = profile.spatial.field("example:field/marches_regions").sample(fields, x, 0, z);
                    if (value > strongest) {
                        strongest = value;
                        farX = x;
                        farZ = z;
                    }
                }
            }
            require(strongest > 0.65, "The seed reaches lantern/mesa regions within exploration range");
            require(profile.spatial.field("example:field/marches_regions").sample(fields, 0, 0, 0) == 0,
                    "The original spawn remains in lowlands");
            require(profile.settings.heightOutputs.get("lanterns").sample(fields, farX, 0, farZ) == 116,
                    "Authored lantern height is independent of the terrain envelope");
            require(manager.biomeMatches("#example:marches_mesa", farX, farZ),
                    "The same region field selects the mesa biome");
        }
        GenerationContext context = GenerationContext.preview(fields, farX >> 4, farZ >> 4);
        ChunkBlockBuffer buffer = BetaMoonChunkProvider.generateRaw(profile, context);
        int solid = 0;
        int sandstone = 0;
        int clay = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                require(buffer.blockId(x, 0, z) == Block.bedrock.blockID, "Carvers preserve the bedrock floor");
                for (int y = 1; y < 128; y++) {
                    int block = buffer.blockId(x, y, z);
                    solid += block != 0 ? 1 : 0;
                    sandstone += block == 24 ? 1 : 0;
                    clay += block == 82 ? 1 : 0;
                }
            }
        }
        require(solid > 10000, "The example emits substantial terrain");
        require(!complex || sandstone > 100 && clay > 100, "The mesa material rules emit sandstone and clay bands");
        Map<String, Object> first = GeneratorPreview.chunk(profile, fields, farX >> 4, farZ >> 4);
        GeneratorPreview.chunk(profile, fields, -3, 5);
        Map<String, Object> repeated = GeneratorPreview.chunk(profile, fields, farX >> 4, farZ >> 4);
        require(first.get("rawHash").equals(repeated.get("rawHash")), "Example preview is independent of query order");
    }

    private static void surveyor(WorldGeneratorProfile profile) throws Exception {
        TerrainFoundationTest.Save save = new TerrainFoundationTest.Save(Files.createTempDirectory("marches-tool-"));
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(save, 42);
        world.isNewWorld = true;
        try (WorldCreationSelection pending = WorldCreationSelection
                .begin(GenerationSelection.create(new GenerationDraft(new GenerationCatalog(profile)), 42))) {
            WorldGenerationRuntime.prepare(world, save);
        }
        ItemDefinition definition = ItemCallbackRegistry.get(5050);
        require(definition != null, "The surveyor item has registered callbacks");
        for (final boolean sneaking : new boolean[]{false, true}) {
            try (LuaCallbackScope scope = new LuaCallbackScope(false)) {
                LuaTable ctx = new LuaTable();
                ctx.set("world", LuaWorldActionAccess.create(scope, world, -73, 80, 92));
                LuaTable position = new LuaTable();
                position.set("x", -73);
                position.set("z", 92);
                ctx.set("position", position);
                LuaTable player = new LuaTable();
                player.set("isSneaking", new ZeroArgFunction() {
                    public LuaValue call() {
                        return valueOf(sneaking);
                    }
                });
                ctx.set("player", player);
                require(definition.callbacks.interaction(ItemCallback.USE_ON_BLOCK, ctx) == InteractionOutcome.HANDLED,
                        "The real surveyor callback must handle sampling and optional bounded export");
                require(definition.callbacks.has(ItemCallback.USE_ON_BLOCK),
                        "Surveyor callback did not disable itself");
            }
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
