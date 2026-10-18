package betamoon.worldgen.terrain;

import betamoon.assets.AssetKey;
import betamoon.assets.model.ModelJson;
import betamoon.loot.LootContext;
import betamoon.loot.LootTableRegistry;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.spatial.SpatialSelector;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Geographic consumers, source composition, frozen materials and exports share
 * actual Lua declarations.
 */
public final class WorldgenCompositionTest {
    public static void main(String[] args) throws Exception {
        require(Block.stone != null, "Initialize native blocks");
        effectiveProfileMembership();
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "composition.lua");
        Globals lua = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, lua);
        try (SpatialRegistry.Batch spatial = SpatialRegistry.begin("composition.lua");
                TerrainRegistry.Batch terrain = TerrainRegistry.begin("composition.lua");
                WorldGenRegistry.PublicationBatch worldgen = WorldGenRegistry.beginPublication("composition.lua",
                        "Composition");
                BiomeGenRegistry.PublicationBatch biomes = BiomeGenRegistry.beginPublication("composition.lua",
                        "Composition");
                LootTableRegistry.PublicationBatch loot = LootTableRegistry.beginPublication("composition.lua",
                        "Composition")) {
            lua.load("local w=betamoon.worldgen;" + "side=w.spatial.fields:add{key='test:field/side',node={type='x'}};"
                    + "scan=w.spatial.fields:add{key='test:field/scan',node={type='find_top_surface',threshold=1000,"
                    + "input={type='add',inputs={{type='x'},{type='y'}}}}};"
                    + "narrow=w.spatial.fields:add{key='test:field/narrow',node=0.25};"
                    + "local feature=w.features:add{key='test:feature/scatter',type='block_patch',block=1};"
                    + "w.placements:add{key='test:placement/scatter',feature=feature,stage='surface_features',spatial={field=side}};"
                    + "left=w.biomes:add{key='test:left',name='Left',tags={'test:west'}};"
                    + "right=w.biomes:add{key='test:right',name='Right',tags={'test:east'}};"
                    + "climate=w.biomeSources:add{key='test:split',type='spatial_climate',entries={"
                    + "{biome=left,temperature={0,1},humidity={0,1},spatial={field=side,max=-0.001}},"
                    + "{biome=right,temperature={0,1},humidity={0,1},spatial={field=side,min=0}}}};"
                    + "low=w.terrain.profiles:add{key='test:terrain/low',baseHeight=50};"
                    + "high=w.terrain.profiles:add{key='test:terrain/high',baseHeight=80};"
                    + "mapped=w.terrain.sources:add{key='test:terrain_source/map',type='profile_map',fallback=low,"
                    + "blendWidth=32,entries={{profile=high,spatial={field=side,min=0}}}};"
                    + "band=w.terrain.sources:add{key='test:terrain_source/band',type='heightmap',height=20,"
                    + "ceiling=30.2,material={block=35,metadata=2}};"
                    + "cut=w.terrain.sources:add{key='test:terrain_source/cut',type='heightmap',height=25};"
                    + "combined=w.terrain.sources:add{key='test:terrain_source/combined',type='compose',children={"
                    + "{source=band},{source=cut,operation='subtract'}}};"
                    + "w.terrain.sources:add{key='test:terrain_source/materials',type='heightmap',height=40,material={"
                    + "type='sequence',rules={{type='when',condition={type='exposure'},thenRule={block=35,metadata=4}},"
                    + "{type='when',condition={type='stone_depth',min=1,max=3},thenRule=3},1}}};"
                    + "w.carvers:add{key='test:carver/exact',type='field',field=1,replaceable={{block=35,metadata=4},7},"
                    + "avoidFluids=false};"
                    + "w.carvers:add{key='test:carver/filtered',type='field',field=1,minY=12,maxY=14,"
                    + "biomes={includeTags={'test:east'}},profiles={high},avoidFluids=false,replaceable={35}};"
                    + "inline=w.terrain.sources:add{key='test:terrain_source/inline',type='compose',children={"
                    + "{source={type='profile_map',fallback={baseHeight=25},blendWidth=0,seaLevel=-1,"
                    + "entries={{profile={baseHeight=30}}}}}}};"
                    + "w.generatorProfiles:add{key='test:generator/inline',dimensions={overworld={terrain=inline,"
                    + "biomeSource={type='fixed',biome=left},surfaces='none',population={native='none'},"
                    + "carvers={{type='compose',children={{type='field',field=1,minY=10,maxY=10,avoidFluids=false}}}}}}};"
                    + "w.generatorProfiles:add{key='test:generator/report',dimensions={overworld={"
                    + "terrain=mapped,biomeSource=climate,surfaces='none',population={native='none'},"
                    + "heightOutputs={east=side},features={includeTags={'test:plants'}}}}};"
                    + "betamoon.lootTables:add{key='test:child',pools={{key='gated',when={spatial={field=side,min=0}},"
                    + "entries={{type='item',stack={item=263,count=1,damage=1}}}}}};"
                    + "betamoon.lootTables:add{key='test:parent',pools={{key='nested',entries={{type='table',table='test:child'}}}}};"
                    + "betamoon.lootTables:add{key='test:plain',pools={{key='plain',entries={{type='item',stack={item=263,count=2}}}}}}")
                    .call();
            spatial.validate();
            loot.validate();
            worldgen.validate();
            biomes.validate();
            terrain.validate();
            worldgen.publish();
            biomes.publish();
            loot.publish();
            spatial.publish();
            terrain.publish();
        } finally {
            owner.invoke(null, new Object[]{null});
        }
        TerrainRegistry.Snapshot definitions = TerrainRegistry.snapshot();
        Path root = Files.createTempDirectory("betamoon-worldgen-composition-");
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(new TerrainFoundationTest.Save(root),
                99);
        ProfileWorldChunkManager manager = new ProfileWorldChunkManager(world.getWorldChunkManager(),
                BiomeGenRegistry.sourceFor(WorldGenKey.parse("test:split", WorldGenKind.BIOME_SOURCE)));
        FieldContext fields = new FieldContext(99, 0, true, 0, 0, manager);
        manager.setFieldContext(fields);
        BiomeGenBase west = manager.getBiomeGenAt(-1, 7);
        BiomeGenBase east = manager.getBiomeGenAt(0, 7);
        require(west != east && manager.biomeMatches("#test:west", -1, 7) && manager.biomeMatches("#test:east", 0, 7),
                "Positional biomes must match runtime tags on both sides of a boundary");
        BiomeGenBase[] rectangle = manager.loadBlockGeneratorData(null, -2, 5, 4, 3);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 3; z++) {
                require(rectangle[x * 3 + z] == manager.getBiomeGenAt(x - 2, z + 5),
                        "Rectangular and point biome sampling must agree");
            }
        }
        require(definitions.source("test:inline").height(fields, 0, 0) == 30,
                "Nested inline sources and height profiles must share the registered-definition parser");
        require(definitions.generator("test:inline").biomeSource.fixedBiome
                .equals(WorldGenKey.parse("test:left", WorldGenKind.BIOME)),
                "Inline biome sources must resolve staged biome references");
        TerrainSource map = definitions.source("test:map");
        require(map.height(fields, -32, 0) == 50 && map.height(fields, -16, 0) == 65 && map.height(fields, 0, 0) == 80,
                "Profile blending must preserve fractional envelopes across negative grid coordinates");
        ChunkBlockBuffer combined = new ChunkBlockBuffer();
        definitions.source("test:combined").fill(GenerationContext.preview(fields, -1, 0), combined);
        require(combined.blockId(2, 25, 3) == 0 && combined.blockId(2, 26, 3) == 35 && combined.metadata(2, 30, 3) == 2
                && combined.blockId(2, 31, 3) == 0,
                "Ceiling bands and subtraction must retain exact boundaries and metadata");
        ChunkBlockBuffer materials = new ChunkBlockBuffer();
        definitions.source("test:materials").fill(GenerationContext.preview(fields, 0, 0), materials);
        require(materials.blockId(2, 40, 3) == 35 && materials.metadata(2, 40, 3) == 4
                && materials.blockId(2, 39, 3) == 3 && materials.blockId(2, 36, 3) == 1,
                "Exposure/depth conditions must read frozen complete occupancy");
        materials.set(1, 20, 1, 35, 3);
        materials.set(2, 20, 2, 35, 4);
        materials.set(0, 0, 0, 7, 0);
        definitions.carver("test:exact").apply(GenerationContext.preview(fields, 0, 0), materials);
        require(materials.blockId(1, 20, 1) == 35 && materials.blockId(2, 20, 2) == 0
                && materials.blockId(0, 0, 0) == 7,
                "Carvers must honor exact metadata selectors and protected bedrock");
        require(LootTableRegistry.find(AssetKey.parse("test:parent")).generate(1).isEmpty(),
                "Location-conditional nested loot must not emit without context");
        require(LootTableRegistry.find(AssetKey.parse("test:parent")).generate(1, new LootContext(fields, -1, 50, 0))
                .isEmpty()
                && LootTableRegistry.find(AssetKey.parse("test:parent")).generate(1, new LootContext(fields, 1, 50, 0))
                        .size() == 1,
                "Nested tables must retain the same spatial context");
        require(LootTableRegistry.find(AssetKey.parse("test:plain")).generate(1).get(0).count == LootTableRegistry
                .find(AssetKey.parse("test:plain")).generate(1, new LootContext(fields, 1, 50, 0)).get(0).count,
                "Unconditional seeded loot must be unchanged by adding a context");
        WorldGeneratorProfile profile = definitions.generator("test:report");
        Map<String, Object> first = GeneratorPreview.chunk(profile, fields, -1, 0);
        GeneratorPreview.chunk(profile, fields, 0, -1);
        require(first.get("rawHash").equals(GeneratorPreview.chunk(profile, fields, -1, 0).get("rawHash")),
                "Read-only preview hashes must not depend on request order");
        GenerationContext filteredContext = GenerationContext.preview(fields, 0, 0);
        filteredContext.terrainSource = map;
        ChunkBlockBuffer filtered = new ChunkBlockBuffer();
        filtered.fillColumn(0, 0, 0, 30, 35, 0);
        definitions.carver("test:filtered").apply(filteredContext, filtered);
        require(filtered.blockId(0, 11, 0) == 35 && filtered.blockId(0, 12, 0) == 0 && filtered.blockId(0, 14, 0) == 0
                && filtered.blockId(0, 15, 0) == 35,
                "Carver biome/profile filters and inclusive vertical limits must combine");
        Path chunkExport = Paths.get(GenerationDiagnosticExport.chunk(root, first));
        require(chunkExport.getParent().equals(root.toRealPath()) && Files.readAllLines(chunkExport).size() == 257,
                "Chunk CSV must remain inside the debug root and contain exactly 256 columns");
        Map<String, Object> excessiveSlice = new LinkedHashMap<String, Object>();
        excessiveSlice.put("width", 256);
        excessiveSlice.put("height", 256);
        try {
            GenerationDiagnosticExport.field(root, SpatialRegistry.snapshot().field("test:scan"), fields,
                    excessiveSlice);
            throw new AssertionError("Surface-scan work must count toward the CSV sampling budget");
        } catch (IllegalArgumentException expectedBudget) {
            require(expectedBudget.getMessage().contains("work budget"),
                    "Budget failure must identify the sampling limit");
        }
        Path slice = Paths.get(GenerationDiagnosticExport.field(root, SpatialRegistry.snapshot().field("test:side"),
                fields, Collections.<String, Object>emptyMap()));
        require(Files.readAllLines(slice).size() == 257, "Default field slice must contain 16x16 samples");
        try (TerrainRegistry.Batch batch = TerrainRegistry.begin("invalid.lua")) {
            TerrainRegistry.add(WorldGenKind.TERRAIN_SOURCE, ModelJson
                    .read(("{\"key\":\"test:terrain_source/invalid\",\"type\":\"density\",\"density\":1,\"material\":{"
                            + "\"type\":\"when\",\"condition\":{\"type\":\"surface_depth\",\"min\":0,\"max\":1},\"thenRule\":1}}")
                            .getBytes(StandardCharsets.UTF_8)));
            try {
                batch.validate();
                throw new AssertionError("Missing material envelope must fail before generation");
            } catch (IllegalArgumentException expected) {
                require(TerrainRegistry.snapshot() == definitions,
                        "Failed validation must retain the previous catalog");
            }
        }
        SpatialRegistry.Snapshot oldFields = SpatialRegistry.snapshot();
        SpatialSelector narrow = new SpatialSelector(Collections.<String, Object>singletonMap("field", "test:narrow"),
                "test.narrow", oldFields);
        String populationBefore = GenerationSignature.of(WorldGenRegistry.generationDependencies());
        String lootBefore = LootTableRegistry.find(AssetKey.parse("test:child")).dependencySignature();
        try (SpatialRegistry.Batch batch = SpatialRegistry.begin("composition.lua")) {
            lua.load(
                    "betamoon.worldgen.spatial.fields:add{key='test:field/side',node={type='add',inputs={{type='x'},1}}};"
                            + "betamoon.worldgen.spatial.fields:add{key='test:field/narrow',node=2}")
                    .call();
            batch.publish();
        }
        FieldContext oldVersion = new FieldContext(99, 0, true, 0, 0, manager, oldFields);
        FieldContext newVersion = new FieldContext(99, 0, true, 0, 0, manager, SpatialRegistry.snapshot());
        require(narrow.value(oldVersion, 0, 0) == 0.25 && narrow.value(newVersion, 0, 0) == 2
                && narrow.weight(newVersion, 0, 0) == 1,
                "Selectors must use the world's field version and resolve omitted bounds from that version");
        require(!populationBefore.equals(GenerationSignature.of(WorldGenRegistry.generationDependencies()))
                && !lootBefore.equals(LootTableRegistry.find(AssetKey.parse("test:child")).dependencySignature()),
                "Transitive placement/loot signatures must detect a field edit without republishing their owners");
        SpatialRegistry.retainOwners(Collections.<String>emptySet());
        TerrainRegistry.retainOwners(Collections.singleton("composition.lua"));
        require(TerrainRegistry.snapshot().generator("test:report") == null,
                "Owner cleanup must prune dependent generators instead of aborting the loader");
        require(map.height(fields, -16, 0) == 65, "Pinned compiled samplers must survive catalog cleanup");
        System.out.println("Worldgen composition passed: profile blends, spatial biomes, materials, exact carvers, "
                + "contextual loot, previews/CSV and dependent-owner cleanup.");
    }

    private static void effectiveProfileMembership() throws Exception {
        TerrainSource child = new TerrainSource() {
            public boolean isNative() {
                return false;
            }

            public void fill(GenerationContext context, ChunkBlockBuffer buffer) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        buffer.fillColumn(x, z, 1, 20, 1, 0);
                    }
                }
            }

            public double height(FieldContext context, int x, int z) {
                return 20;
            }

            public String signature() {
                return "profile-membership-test";
            }

            public boolean usesHeightProfile(FieldContext context, int x, int z, String key) {
                return key.equals("test:terrain/profile");
            }
        };
        FieldContext fields = new FieldContext(99, 0);
        ComposedTerrainSource masked = composed("[{\"source\":\"test:child\",\"mask\":0,\"min\":1,\"max\":1}]", child);
        require(!masked.usesHeightProfile(fields, -1, 0, "test:terrain/profile"),
                "A wholly excluded child cannot enable profile-restricted carvers");
        ComposedTerrainSource subtracted = composed(
                "[{\"source\":\"test:child\"}," + "{\"source\":\"test:child\",\"operation\":\"subtract\"}]", child);
        require(!subtracted.usesHeightProfile(fields, 0, 0, "test:terrain/profile"),
                "Subtraction erases membership when it removes all participating material");
        ComposedTerrainSource nested = composed("[{\"source\":\"test:child\"}]", masked);
        require(!nested.usesHeightProfile(fields, 16, -16, "test:terrain/profile"),
                "Nested compositions retain effective membership");
        ComposedTerrainSource intersected = composed(
                "[{\"source\":\"test:child\"}," + "{\"source\":\"test:child\",\"operation\":\"intersection\"}]", child);
        require(intersected.usesHeightProfile(fields, 0, 0, "test:terrain/profile"),
                "Intersection retains membership for surviving material");
        ComposedTerrainSource replaced = new ComposedTerrainSource("test:terrain_source/replaced",
                ModelJson.read(("{\"children\":[{\"source\":\"test:present\"},{\"source\":\"test:absent\","
                        + "\"operation\":\"replace\"}]}").getBytes(StandardCharsets.UTF_8)),
                SpatialRegistry.snapshot(), key -> key.equals("test:present") ? child : masked, Collections.emptyMap());
        require(!replaced.usesHeightProfile(fields, 0, 0, "test:terrain/profile"),
                "A later replacement removes earlier profile membership");
    }

    private static ComposedTerrainSource composed(String children, TerrainSource source) throws Exception {
        return new ComposedTerrainSource("test:terrain_source/membership",
                ModelJson.read(("{\"children\":" + children + "}").getBytes(StandardCharsets.UTF_8)),
                SpatialRegistry.snapshot(), key -> source, Collections.emptyMap());
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
