package betamoon.worldgen.terrain;

import betamoon.luaapi.world.WorldGenApi;
import betamoon.worldgen.BlockSet;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.ChunkLoader;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.MapGenCaves;
import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Compiles actual Lua declarations and exercises the instrumented provider and
 * save boundaries.
 */
public final class TerrainPipelineTest {
    public static void main(String[] args) throws Exception {
        require(Block.stone != null, "Initialize native content");
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        WorldGenApi.attach(module);
        lua.set("betamoon", module);
        try (SpatialRegistry.Batch spatial = SpatialRegistry.begin("terrain.lua");
                TerrainRegistry.Batch terrain = TerrainRegistry.begin("terrain.lua")) {
            lua.load("flat=betamoon.worldgen.terrain.sources:add{key='test:terrain_source/flat',type='flat',"
                    + "layers={{block='minecraft:block/bedrock',depth=1},{block='minecraft:block/dirt',depth=3},"
                    + "{block='minecraft:block/grass',depth=1}}};"
                    + "betamoon.worldgen.terrain.sources:add{key='test:terrain_source/void',type='void'};"
                    + "betamoon.worldgen.terrain.sources:add{key='test:terrain_source/platform',type='void',"
                    + "spawnPlatform={block=89,size=5,y=24}};"
                    + "betamoon.worldgen.terrain.sources:add{key='test:terrain_source/density',type='density',"
                    + "density={type='y_gradient',fromY=0,toY=128,fromValue=64,toValue=-64},"
                    + "material={block=35,metadata=7},cell={x=4,y=8,z=4}};"
                    + "carver=betamoon.worldgen.carvers:add{key='test:carver/slice',type='field',"
                    + "field={type='y'},min=20,max=30,replaceable={35},avoidFluids=false};"
                    + "betamoon.worldgen.carvers:add{key='test:carver/tunnels',type='tunnel',probability=1,"
                    + "length=40,radius=4,minY=20,maxY=40,replaceable={35}};"
                    + "betamoon.worldgen.generatorProfiles:add{key='test:generator/flat',defaultForNewWorlds=true,"
                    + "dimensions={overworld={terrain=flat,surfaces='none',population={native='none'}}}}").call();
            spatial.validate();
            terrain.validate();
            spatial.publish();
            terrain.publish();
        }
        TerrainRegistry.Snapshot definitions = TerrainRegistry.snapshot();
        surfaceIndexing();
        BiomeGenBase[] biomes = new BiomeGenBase[256];
        Arrays.fill(biomes, BiomeGenBase.plains);
        GenerationContext context = new GenerationContext(new FieldContext(42, 0, true, 0, 0), -1, 1, biomes);
        ChunkBlockBuffer density = new ChunkBlockBuffer();
        definitions.source("test:density").fill(context, density);
        require(density.blockId(3, 63, 11) == 35 && density.metadata(3, 63, 11) == 7 && density.blockId(3, 64, 11) == 0,
                "Interpolated density and metadata must agree at exact zero");
        definitions.carver("test:slice").apply(context, density);
        require(density.blockId(3, 20, 11) == 0 && density.blockId(3, 30, 11) == 0 && density.metadata(3, 20, 11) == 0
                && density.blockId(3, 31, 11) == 35, "Carvers must remove precisely their mask and clear metadata");
        ChunkBlockBuffer repeated = new ChunkBlockBuffer();
        definitions.source("test:density").fill(context, repeated);
        CompiledCarver tunnels = definitions.carver("test:tunnels");
        tunnels.apply(context, repeated);
        byte[] expected = repeated.copyBlocks();
        GenerationContext other = new GenerationContext(context.fields, 3, -5, biomes);
        ChunkBlockBuffer temporary = new ChunkBlockBuffer();
        definitions.source("test:density").fill(other, temporary);
        tunnels.apply(other, temporary);
        repeated = new ChunkBlockBuffer();
        definitions.source("test:density").fill(context, repeated);
        tunnels.apply(context, repeated);
        require(Arrays.equals(expected, repeated.copyBlocks()), "Carver output must be independent of query order");

        Path directory = Files.createTempDirectory("betamoon-custom-pipeline-");
        TerrainFoundationTest.Save save = new TerrainFoundationTest.Save(directory);
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(save, 42);
        world.isNewWorld = true;
        world.createOuterProvider();
        WorldGeneratorManifest manifest = WorldGenerationRuntime.manifest(world);
        require(manifest != null && manifest.originalSpawn != null && manifest.originalSpawn.y < 10,
                "Low flat world must persist its original spawn before chunk generation");
        world.selectInitialSpawn();
        world.setSpawnLocation();
        require(world.getSpawnPoint().y == manifest.originalSpawn.y,
                "Both native sea-level spawn searches must be skipped");
        IChunkProvider provider = world.worldProvider.getChunkProvider();
        // The native factory is already instrumented; use that selected provider
        // directly.
        require(provider instanceof BetaMoonChunkProvider, "Profile factory must choose the owned provider");
        Chunk chunk = provider.provideChunk(-2, 3);
        require(chunk.getBlockID(2, 4, 9) == 2 && chunk.getBlockID(2, 5, 9) == 0,
                "Provider must emit authored layers at negative chunk coordinates");
        ChunkBlockBuffer luminous = new ChunkBlockBuffer();
        luminous.set(8, 20, 8, Block.glowStone.blockID, 0);
        luminous.set(9, 20, 8, Block.stone.blockID, 0);
        luminous.set(2, 40, 11, 35, 7);
        Chunk lit = RawChunkFinalizer.build(world, 0, 0, luminous);
        require(lit.blocklightMap.getNibble(8, 20, 8) == 15 && lit.blocklightMap.getNibble(7, 20, 8) == 14
                && lit.blocklightMap.getNibble(9, 20, 8) == 0,
                "Emitted light must propagate through air and respect opaque blocks");
        require(lit.getHeightValue(2, 11) == 41 && lit.getBlockMetadata(2, 40, 11) == 7,
                "Native height and metadata must reflect the final asymmetric raw buffer");
        NBTTagCompound lightingSaved = new NBTTagCompound();
        ChunkLoader.storeChunkInCompound(lit, world, lightingSaved);
        Chunk lightingRestored = ChunkLoader.loadChunkIntoWorldFromCompound(world, lightingSaved);
        require(Arrays.equals(lit.blocklightMap.data, lightingRestored.blocklightMap.data)
                && Arrays.equals(lit.skylightMap.data, lightingRestored.skylightMap.data),
                "Native persistence must retain emitted light and skylight");
        NBTTagCompound saved = new NBTTagCompound();
        ChunkLoader.storeChunkInCompound(chunk, world, saved);
        Chunk restored = ChunkLoader.loadChunkIntoWorldFromCompound(world, saved);
        require(Arrays.equals(chunk.blocks, restored.blocks) && Arrays.equals(chunk.data.data, restored.data.data),
                "Native chunk persistence must preserve generated blocks and metadata");
        world.setSpawnPoint(new ChunkCoordinates(300, 8, -80));
        world.setSpawnLocation();
        require(world.getSpawnPoint().x == 300 && WorldGenerationRuntime.manifest(world).originalSpawn.x == 0,
                "Changing current spawn must preserve the original anchor");
        for (int seed = 0; seed < 20; seed++) {
            TerrainFoundationTest.Fixture parityWorld = new TerrainFoundationTest.Fixture(save, seed);
            ProfileWorldChunkManager manager = new ProfileWorldChunkManager(parityWorld.getWorldChunkManager(), null);
            for (int[] coordinate : new int[][]{{0, 0}, {-1, 3}, {3, -1}, {-2, -2}, {400000, -400000},
                    {-400000, 400000}, {1874998, -1874998}, {-1874998, 1874998}}) {
                int cx = coordinate[0];
                int cz = coordinate[1];
                BiomeGenBase[] samples = parityWorld.getWorldChunkManager().loadBlockGeneratorData(null, cx * 16,
                        cz * 16, 16, 16);
                byte[] nativeBlocks = new byte[32768];
                new ChunkProviderGenerate(parityWorld, seed).generateTerrain(cx, cz, nativeBlocks, samples,
                        parityWorld.getWorldChunkManager().temperature);
                CompiledTerrainSource beta = new CompiledTerrainSource("test:terrain_source/beta",
                        Collections.<String, Object>singletonMap("type", "beta_compatible"), SpatialRegistry.snapshot(),
                        definitions, Collections.<String, String>emptyMap());
                GenerationContext betaContext = new GenerationContext(new FieldContext(seed, 0, true, 0, 0, manager),
                        cx, cz, samples);
                ChunkBlockBuffer owned = new ChunkBlockBuffer();
                beta.fill(betaContext, owned);
                require(Arrays.equals(nativeBlocks, owned.copyBlocks()),
                        "Beta raw preset must match native density: " + seed + ":" + cx + "," + cz);
                byte[] nativeCarved = nativeBlocks.clone();
                new MapGenCaves().func_867_a(null, parityWorld, cx, cz, nativeCarved);
                byte[] ownedCarved = nativeBlocks.clone();
                new NativeCaveSampler().carve(seed, cx, cz, ownedCarved);
                require(Arrays.equals(nativeCarved, ownedCarved), "Detached native caves must preserve native output");
            }
        }
        heightPreviewEviction(world, definitions);
        GeneratorProfileRegistry.replaceOwner("terrain.lua", Collections.<WorldGeneratorProfile>emptyList());
        require(Arrays.equals(chunk.blocks, provider.provideChunk(-2, 3).blocks),
                "Active world must retain its compiled profile after owner removal");
        System.out.println("Terrain pipeline passed: Lua declarations, density, masks, order independence, "
                + "bounded low spawn, instrumented provider, native NBT and pinned world definitions.");
    }

    private static void heightPreviewEviction(TerrainFoundationTest.Fixture world, TerrainRegistry.Snapshot definitions)
            throws java.io.IOException {
        java.util.Map<String, Object> value = betamoon.assets.model.ModelJson.read(("{\"type\":\"profile_map\","
                + "\"entries\":[{\"profile\":{\"baseHeight\":30},\"biomes\":{\"include\":[\"test:absent\"]}}],"
                + "\"material\":{\"type\":\"when\",\"condition\":{\"type\":\"slope\",\"min\":0,\"max\":0},"
                + "\"thenRule\":{\"block\":35,\"metadata\":2},\"elseRule\":{\"block\":35,\"metadata\":3}}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        TerrainSource source = new ProfileMapTerrainSource("test:terrain_source/sloped_fallback", value,
                SpatialRegistry.snapshot(), definitions, definitions, Collections.<String, String>emptyMap());
        FieldContext fields = WorldGenerationRuntime.fieldContext(world);
        ChunkBlockBuffer expected = new ChunkBlockBuffer();
        source.fill(GenerationContext.preview(fields, -2, 3), expected);
        for (int index = 0; index < 20; index++) {
            source.height(fields, index * 16, -48);
            definitions.source("test:density").height(fields, index * 16, -48);
        }
        ChunkBlockBuffer actual = new ChunkBlockBuffer();
        source.fill(GenerationContext.preview(fields, -2, 3), actual);
        require(Arrays.equals(expected.copyBlocks(), actual.copyBlocks())
                && Arrays.equals(expected.copyMetadata(), actual.copyMetadata()),
                "Bounded height-preview cache eviction and source/seed reuse must preserve material output");
    }

    private static void surfaceIndexing() {
        byte[] blocks = new byte[32768];
        blocks[(2 << 11) | (11 << 7) | 40] = 1;
        blocks[(11 << 11) | (2 << 7) | 40] = 1;
        SurfaceRuleSet rule = new SurfaceRuleSet(WorldGenKey.parse("test:surface/asymmetric", WorldGenKind.SURFACE),
                "test.lua", "test", "test", new BlockSet(1),
                Collections.singletonList(new SurfaceRuleSet.Layer(35, new IntRange(1, 1))), null, 63);
        rule.apply(1, 0, 0, blocks, 2, 11);
        require((blocks[(2 << 11) | (11 << 7) | 40] & 255) == 35 && blocks[(11 << 11) | (2 << 7) | 40] == 1,
                "Surface columns must not transpose X and Z");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
