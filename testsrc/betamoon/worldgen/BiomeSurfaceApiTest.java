package betamoon.worldgen;

import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.EnumCreatureType;
import net.minecraft.src.EntityPig;
import net.minecraft.src.SpawnListEntry;
import betamoon.worldgen.biome.LuaBiome;
import net.minecraft.src.Block;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Verifies keyed biomes, decorators, sources, tags, and raw-buffer surfaces.
 */
public final class BiomeSurfaceApiTest {
    private BiomeSurfaceApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        verifyNativeBiomeCopy();
        require(Block.stone != null, "Vanilla blocks are initialized");
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "biome_surface_test.lua");
        BiomeGenRegistry.clear();
        WorldGenRegistry.clear();
        try {
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            try (WorldGenRegistry.PublicationBatch worldgen = WorldGenRegistry
                    .beginPublication("biome_surface_test.lua", "Biome surface test");
                    BiomeGenRegistry.PublicationBatch biomes = BiomeGenRegistry
                            .beginPublication("biome_surface_test.lua", "Biome surface test")) {
                lua.load("local w=betamoon.worldgen; "
                        + "local surface=w.surfaces:add{key='test:moon',layers={{block=12,depth={min=2,max=2}}},"
                        + "underwaterBlock=13}; "
                        + "local flower=w.features:add{key='test:flowers',type='block_patch',block=37,tries=2}; "
                        + "local shared=w.placements:add{key='test:shared_flowers',feature=flower,attempts=1}; "
                        + "local biome=w.biomes:add{key='test:grove',name='Moon Grove',tags={'test:forest'},"
                        + "surface=surface,decorator={placements={shared,{feature=flower,attempts=2}}}}; "
                        + "assert(biome:getKey()=='test:biome/grove'); "
                        + "assert(w.biomes:getRequired('test:grove'):getKey()==biome:getKey()); "
                        + "w.biomeSources:add{key='test:climate',type='vanilla_climate',active=true,entries={"
                        + "{biome=biome,temperature={0,0.75},humidity={0,1}},"
                        + "{biome=biome,temperature={0.5,1},humidity={0,1},priority=1}}}; "
                        + "assert(w.surfaces:get('test:moon'):getKey()=='test:surface/moon')").call();
                worldgen.publish();
                biomes.publish();
            }

            require(BiomeGenRegistry.snapshot().size() == 1, "One keyed biome was published");
            require(BiomeGenRegistry.surfaceSnapshot().size() == 1, "One surface was published");
            require(BiomeGenRegistry.sourceSnapshot().size() == 1, "One biome source was published");
            require(BiomeGenRegistry.snapshot().get(0).decorators == 2, "Biome records its compiled decorators");
            require(WorldGenRegistry.placementSnapshot().size() == 3,
                    "Referenced and inline biome decorators compile into the ordinary placement registry");
            require(WorldGenRegistry.placementSnapshot().get(0).key.equals("test:placement/biome_decorators/grove/01"),
                    "Decorator receives a stable derived key");
            require(WorldGenRegistry.placementSnapshot().get(2).template,
                    "Referenced placement becomes a reusable decorator template instead of running twice");
            long revisionBeforeDescription = BiomeGenRegistry.terrainRevision();
            BiomeGenRegistry.SourceDescription source = BiomeGenRegistry.sourceSnapshot().get(0);
            require(BiomeGenRegistry.terrainRevision() == revisionBeforeDescription,
                    "Reading source descriptions must not invalidate terrain caches");
            require(source.active && source.overlapCells > 0 && source.uncoveredCells == 0,
                    "Climate diagnostics report selected source overlap and coverage");

            WorldGenKey biomeKey = WorldGenKey.parse("test:grove", WorldGenKind.BIOME);
            BiomeGenBase biome = BiomeGenRegistry.biomeFor(biomeKey);
            require(BiomeGenRegistry.matchesSelectors(biome, Collections.singleton("#test:forest")),
                    "Biome tags match placement/spawn selectors");
            require(BiomeGenRegistry.matchesSelectors(biome, Collections.singleton("test:biome/grove")),
                    "Biome keys match placement/spawn selectors");

            verifySurfaceApplication();
            int activeBiomes = BiomeGenRegistry.snapshot().size();
            expectFailure(new Runnable() {
                @Override
                public void run() {
                    try (BiomeGenRegistry.PublicationBatch batch = BiomeGenRegistry
                            .beginPublication("biome_surface_test.lua", "Biome surface test")) {
                        lua.load("betamoon.worldgen.biomeSources:add{key='test:broken',type='fixed',"
                                + "biome='test:missing',active=true}").call();
                        batch.publish();
                    }
                }
            }, "Unknown biome-source references are rejected");
            require(BiomeGenRegistry.snapshot().size() == activeBiomes,
                    "Failed biome publication preserves the active snapshot");
            verifyDisplayNameAliases(lua);
            verifyTerrainInvalidation(lua);
            System.out.println("Biome/surface API checks passed.");
        } finally {
            BiomeGenRegistry.clear();
            WorldGenRegistry.clear();
            owner.invoke(null, new Object[]{null});
        }
    }

    private static void verifyDisplayNameAliases(Globals lua) {
        lua.load(String.join("\n", "local biomes = betamoon.worldgen.biomes",
                "local canonical = biomes:add{key='test:canonical_name',displayName='Canonical Biome'}",
                "assert(canonical.displayName == 'Canonical Biome' and canonical.name == canonical.displayName)",
                "local legacy = biomes:add{key='test:legacy_name',name='Legacy Biome'}",
                "assert(legacy.displayName == 'Legacy Biome' and legacy.name == legacy.displayName)",
                "local both = biomes:add{key='test:both_names',name='Ignored',displayName='Preferred Biome'}",
                "assert(both.name == 'Preferred Biome' and both.displayName == both.name)",
                "local fallback = biomes:add{key='test:unnamed'}",
                "assert(fallback.displayName == 'test:biome/unnamed' and fallback.name == fallback.displayName)",
                "local unkeyed = biomes:add{name='Legacy Unkeyed'}", "assert(unkeyed.displayName == 'Legacy Unkeyed')",
                "assert(not pcall(function() biomes:add{} end))",
                "assert(not pcall(function() biomes:add{displayName={},name='Cannot Hide Invalid Name'} end))",
                "assert(biomes:one{displayName='Canonical Biome'}:getKey() == canonical:getKey())",
                "assert(biomes:one{name='Legacy Biome'}:getKey() == legacy:getKey())",
                "assert(biomes:one{displayName='Preferred Biome',name='Ignored'}:getKey() == both:getKey())",
                "assert(biomes:getRequired('Preferred Biome'):getKey() == both:getKey())",
                "local a = canonical:override{priority=10, changes={name='Legacy Layer'}}",
                "local b = canonical:override{priority=20, changes={displayName='Canonical Layer'}}",
                "assert(canonical.name == 'Canonical Layer' and canonical.displayName == canonical.name)", "a:remove()",
                "assert(canonical.displayName == 'Canonical Layer')", "b:remove()",
                "assert(canonical.displayName == 'Canonical Biome' and canonical.name == canonical.displayName)",
                "local c = canonical:override{changes={name='Ignored Layer',displayName='Preferred Layer'}}",
                "assert(canonical.name == 'Preferred Layer' and canonical.displayName == canonical.name)", "c:remove()",
                "local forest = biomes:getRequired('Forest')", "local original = forest.displayName",
                "local native = forest:override{name='Forest Alias'}",
                "assert(forest.displayName == 'Forest Alias' and forest.name == forest.displayName)", "native:remove()",
                "assert(forest.displayName == original and forest.name == original)")).call();
        for (BiomeGenRegistry.Description biome : BiomeGenRegistry.snapshot()) {
            if (biome.key.equals("test:biome/canonical_name")) {
                require(biome.displayName.equals("Canonical Biome"), "Debug descriptions use the canonical label");
            }
        }
    }

    private static void verifyTerrainInvalidation(Globals lua) {
        long before = BiomeGenRegistry.terrainRevision();
        LuaValue rename = lua.load("return betamoon.worldgen.biomes:getRequired('Forest'):override{"
                + "displayName='Changed Terrain Selector'}").call();
        require(BiomeGenRegistry.terrainRevision() > before,
                "Renaming a biome must invalidate cached terrain using name selectors");
        before = BiomeGenRegistry.terrainRevision();
        rename.get("remove").call(rename);
        require(BiomeGenRegistry.terrainRevision() > before,
                "Removing a biome-name override must also invalidate cached terrain");
        before = BiomeGenRegistry.terrainRevision();
        LuaValue range = lua.load("return betamoon.worldgen.biomes:getRequired('test:grove'):override{"
                + "changes={range={temperature={0.1,0.2}}}}").call();
        require(BiomeGenRegistry.terrainRevision() > before,
                "Changing biome climate selection must invalidate base snapshots");
        before = BiomeGenRegistry.terrainRevision();
        range.get("remove").call(range);
        require(BiomeGenRegistry.terrainRevision() > before,
                "Removing a climate-range override must invalidate base snapshots");
    }

    private static void verifyNativeBiomeCopy() {
        LuaBiome source = new LuaBiome("Copy source");
        source.applyRainEnabled(false);
        source.applySnowEnabled(true);
        source.clearSpawns(BiomeSpawnGroup.CREATURE);
        source.addSpawn(BiomeSpawnGroup.CREATURE, EntityPig.class, 7);
        LuaBiome copy = new LuaBiome("Copy target");
        copy.applyDefaultsFrom(source);
        require(copy.getEnableSnow() && !copy.canSpawnLightningBolt(), "Copied native weather flags must match");
        SpawnListEntry original = (SpawnListEntry) source.getSpawnableList(EnumCreatureType.creature).get(0);
        SpawnListEntry cloned = (SpawnListEntry) copy.getSpawnableList(EnumCreatureType.creature).get(0);
        require(original != cloned && cloned.entityClass == EntityPig.class && cloned.spawnRarityRate == 7,
                "Biome copies must clone spawn entries and preserve their values");
        cloned.spawnRarityRate = 99;
        copy.clearSpawns(BiomeSpawnGroup.CREATURE);
        require(original.spawnRarityRate == 7 && source.getSpawnableList(EnumCreatureType.creature).size() == 1,
                "Changing copied spawn entries or lists must not mutate the source biome");
        copy.applySnowEnabled(false);
        copy.applyRainEnabled(true);
        require(!copy.getEnableSnow() && copy.canSpawnLightningBolt() && source.getEnableSnow(),
                "Cached field access must update the selected instance without changing its source");
    }

    private static void verifySurfaceApplication() {
        SurfaceRuleSet surface = new SurfaceRuleSet(WorldGenKey.parse("test:direct", WorldGenKind.SURFACE), "test.lua",
                "Test", "test", new BlockSet(Block.stone.blockID),
                Arrays.asList(new SurfaceRuleSet.Layer(Block.sand.blockID, new IntRange(2, 2))),
                Integer.valueOf(Block.gravel.blockID), 64);
        byte[] first = new byte[32768];
        byte[] second = new byte[32768];
        for (int y = 0; y <= 63; y++) {
            first[y] = (byte) Block.stone.blockID;
            second[y] = (byte) Block.stone.blockID;
        }
        surface.apply(99L, 0, 0, first, 0, 0);
        surface.apply(99L, 0, 0, second, 0, 0);
        require(Arrays.equals(first, second), "Surface output is deterministic");
        require((first[63] & 255) == Block.gravel.blockID && (first[62] & 255) == Block.gravel.blockID,
                "Underwater surface replaces the configured top layer");
        require((first[61] & 255) == Block.stone.blockID, "Surface respects its compiled depth bound");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (LuaError expected) {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
