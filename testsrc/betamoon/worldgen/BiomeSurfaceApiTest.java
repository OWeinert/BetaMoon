package betamoon.worldgen;

import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Verifies keyed biomes, decorators, sources, tags, and raw-buffer surfaces. */
public final class BiomeSurfaceApiTest {
    private BiomeSurfaceApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks are initialized");
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "biome_surface_test.lua");
        BiomeGenRegistry.clear();
        WorldGenRegistry.clear();
        try {
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            try (WorldGenRegistry.PublicationBatch worldgen = WorldGenRegistry.beginPublication(
                    "biome_surface_test.lua", "Biome surface test");
                    BiomeGenRegistry.PublicationBatch biomes = BiomeGenRegistry.beginPublication(
                            "biome_surface_test.lua", "Biome surface test")) {
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
            require(WorldGenRegistry.placementSnapshot().get(0).key
                    .equals("test:placement/biome_decorators/grove/01"), "Decorator receives a stable derived key");
            require(WorldGenRegistry.placementSnapshot().get(2).template,
                    "Referenced placement becomes a reusable decorator template instead of running twice");
            BiomeGenRegistry.SourceDescription source = BiomeGenRegistry.sourceSnapshot().get(0);
            require(source.active && source.overlapCells > 0 && source.uncoveredCells == 0,
                    "Climate diagnostics report selected source overlap and coverage");

            WorldGenKey biomeKey = WorldGenKey.parse("test:grove", WorldGenKind.BIOME);
            BiomeGenBase biome = BiomeGenRegistry.biomeFor(biomeKey);
            require(BiomeGenRegistry.matchesSelectors(biome,
                    Collections.singleton("#test:forest")), "Biome tags match placement/spawn selectors");
            require(BiomeGenRegistry.matchesSelectors(biome,
                    Collections.singleton("test:biome/grove")), "Biome keys match placement/spawn selectors");

            verifySurfaceApplication();
            int activeBiomes = BiomeGenRegistry.snapshot().size();
            expectFailure(new Runnable() {
                @Override
                public void run() {
                    try (BiomeGenRegistry.PublicationBatch batch = BiomeGenRegistry.beginPublication(
                            "biome_surface_test.lua", "Biome surface test")) {
                        lua.load("betamoon.worldgen.biomeSources:add{key='test:broken',type='fixed',"
                                + "biome='test:missing',active=true}").call();
                        batch.publish();
                    }
                }
            }, "Unknown biome-source references are rejected");
            require(BiomeGenRegistry.snapshot().size() == activeBiomes,
                    "Failed biome publication preserves the active snapshot");
            System.out.println("Biome/surface API checks passed.");
        } finally {
            BiomeGenRegistry.clear();
            WorldGenRegistry.clear();
            owner.invoke(null, new Object[]{null});
        }
    }

    private static void verifySurfaceApplication() {
        SurfaceRuleSet surface = new SurfaceRuleSet(WorldGenKey.parse("test:direct", WorldGenKind.SURFACE),
                "test.lua", "Test", "test", new BlockSet(Block.stone.blockID),
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
