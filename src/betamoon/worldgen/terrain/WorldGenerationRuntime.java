package betamoon.worldgen.terrain;

import betamoon.assets.AssetKey;
import betamoon.loot.LootTableDefinition;
import betamoon.loot.LootTableRegistry;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.NativeBiomeEnvironment;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.EnumSkyBlock;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/**
 * Selects before provider creation, pins world snapshots and persists native
 * original spawn once.
 */
public final class WorldGenerationRuntime {
    private static final String MANIFEST_NAME = "betamoon_generator_overworld";
    private static final String NATIVE_ANCHOR_NAME = "betamoon_original_spawn_overworld";
    private static final String NATIVE_ANCHOR_KEY = "betamoon:generator/native_anchor";
    private static final String NATIVE_ANCHOR_SIGNATURE = GenerationSignature
            .of(Collections.singletonMap("native_original_spawn", "1"));
    private static final Map<World, NativeAnchor> NATIVE_ANCHORS = new WeakHashMap<World, NativeAnchor>();
    private static final Map<World, Binding> WORLDS = new WeakHashMap<World, Binding>();

    private WorldGenerationRuntime() {
    }

    public static synchronized void prepare(World world, ISaveHandler saveHandler) {
        if (world.multiplayerWorld || world.worldProvider.worldType != 0 || WORLDS.containsKey(world)) {
            return;
        }
        GeneratorProfileRegistry.Snapshot snapshot = GeneratorProfileRegistry.snapshot();
        File location = saveHandler == null ? null : saveHandler.func_28113_a(MANIFEST_NAME);
        if (location == null) {
            if (world.isNewWorld && snapshot.defaultForNewWorld() != null) {
                throw new IllegalStateException("Selected generator profile requires persistent world storage");
            }
            return;
        }
        GeneratorManifestStore store = new GeneratorManifestStore(location);
        try {
            WorldGeneratorManifest manifest = store.read();
            WorldGeneratorProfile profile;
            if (manifest == null) {
                profile = world.isNewWorld ? snapshot.defaultForNewWorld() : null;
                if (profile == null) {
                    prepareNativeAnchor(world, saveHandler);
                    return;
                }
                profile = profile.withPopulationCatalog(WorldGenRegistry.generationDependencies());
                if (profile.terrain != null) {
                    GenerationHookAvailability.requireReady();
                }
                manifest = new WorldGeneratorManifest(world.getRandomSeed(), 0, profile.key, profile.signature, null);
                if (isCustom(profile)) {
                    ProfileWorldChunkManager biomes = new ProfileWorldChunkManager(world.getWorldChunkManager(),
                            profile.biomeSource);
                    WorldGeneratorManifest.OriginalSpawn spawn = GeneratorSpawnSearch.find(world.getRandomSeed(),
                            profile, biomes);
                    manifest = manifest.withOriginalSpawn(spawn.x, spawn.y, spawn.z);
                }
                saveHandler.func_22150_b();
                store.write(manifest);
            } else {
                profile = snapshot.get(manifest.profileKey);
                if (profile != null) {
                    profile = profile.withPopulationCatalog(WorldGenRegistry.generationDependencies());
                }
                manifest.requireCompatible(world.getRandomSeed(), 0, profile);
                if (profile.terrain != null) {
                    GenerationHookAvailability.requireReady();
                }
                if (manifest.originalSpawn == null && !world.isNewWorld) {
                    throw new IllegalStateException("Generator manifest has an unfinished original-spawn record; "
                            + "restore a consistent world backup before opening this world");
                }
            }
            WORLDS.put(world, new Binding(profile, manifest, store, saveHandler));
            WorldGenRegistry.pinGenerationCatalogs(world);
            if (profile.terrain != null) {
                ProfileWorldChunkManager manager = new ProfileWorldChunkManager(world.getWorldChunkManager(),
                        profile.biomeSource);
                world.worldProvider.worldChunkMgr = manager;
                manager.setFieldContext(fieldContext(world));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot safely read or persist the world generator manifest", exception);
        }
    }

    private static void prepareNativeAnchor(World world, ISaveHandler saveHandler) throws IOException {
        File file = saveHandler.func_28113_a(NATIVE_ANCHOR_NAME);
        if (file == null) {
            return;
        }
        GeneratorManifestStore store = new GeneratorManifestStore(file);
        WorldGeneratorManifest manifest = store.read();
        if (manifest == null) {
            if (!world.isNewWorld) {
                return;
            }
            manifest = new WorldGeneratorManifest(world.getRandomSeed(), 0, NATIVE_ANCHOR_KEY, NATIVE_ANCHOR_SIGNATURE,
                    null);
            saveHandler.func_22150_b();
            store.write(manifest);
        } else if (manifest.worldSeed != world.getRandomSeed() || manifest.dimension != 0
                || !manifest.profileKey.equals(NATIVE_ANCHOR_KEY)
                || !manifest.signature.equals(NATIVE_ANCHOR_SIGNATURE)) {
            throw new IOException("Original-spawn anchor does not match this native world");
        }
        NATIVE_ANCHORS.put(world, new NativeAnchor(manifest, store, saveHandler));
    }

    public static synchronized IChunkProvider select(WorldProvider provider, IChunkProvider nativeProvider) {
        Binding binding = WORLDS.get(provider.worldObj);
        if (binding == null) {
            return nativeProvider;
        }
        binding.providerSelected = true;
        return new BetaMoonChunkProvider(provider.worldObj, nativeProvider, binding.profile);
    }

    public static synchronized boolean stageEnabled(World world, GenerationStage stage) {
        Binding binding = WORLDS.get(world);
        return binding == null || binding.profile.population == null
                || binding.profile.population.stages.contains(stage);
    }

    private static boolean isCustom(WorldGeneratorProfile profile) {
        return profile.terrain != null && !profile.terrain.isNative();
    }

    public static synchronized boolean ownsPopulation(World world) {
        Binding binding = WORLDS.get(world);
        return binding != null && binding.profile.population != null;
    }

    public static synchronized boolean nativeCategoryEnabled(World world, String category) {
        Binding binding = WORLDS.get(world);
        return binding == null || binding.profile.population == null
                || binding.profile.population.nativeCategories.contains(category);
    }

    public static synchronized int initialSpawn(World world) {
        Binding binding = WORLDS.get(world);
        if (binding == null || binding.profile.terrain == null) {
            return 0;
        }
        WorldGeneratorManifest.OriginalSpawn spawn = binding.manifest.originalSpawn;
        if (spawn == null && binding.profile.terrain.isNative()) {
            for (int attempt = 0; attempt < 81; attempt++) {
                int x = (attempt % 9 - 4) * 32;
                int z = (attempt / 9 - 4) * 32;
                int y = world.getHeightValue(x, z) - 1;
                if (y >= 0 && y <= 125 && GeneratorSpawnSearch.safeGround(world.getBlockId(x, y, z))
                        && world.isAirBlock(x, y + 1, z) && world.isAirBlock(x, y + 2, z)) {
                    world.setSpawnPoint(new ChunkCoordinates(x, y, z));
                    recordNativeSpawn(world);
                    return 1;
                }
            }
            if (!binding.profile.requireSafeSpawn) {
                world.setSpawnPoint(new ChunkCoordinates(0, 64, 0));
                recordNativeSpawn(world);
                return 1;
            }
            throw new IllegalStateException("Native profile has no safe spawn within its bounded search area");
        }
        if (spawn == null) {
            throw new IllegalStateException("Custom generator has no persisted original spawn");
        }
        world.setSpawnPoint(new ChunkCoordinates(spawn.x, spawn.y, spawn.z));
        return 1;
    }

    public static synchronized int skipNativeSpawnRecheck(World world) {
        Binding binding = WORLDS.get(world);
        return binding != null && binding.profile.terrain != null ? 1 : 0;
    }

    public static synchronized void chunkLoaded(Chunk chunk) {
        Binding binding = WORLDS.get(chunk.worldObj);
        if (binding == null || !isCustom(binding.profile)) {
            return;
        }
        int x = chunk.xPosition * 16;
        int z = chunk.zPosition * 16;
        chunk.worldObj.scheduleLightingUpdate(EnumSkyBlock.Block, x, 0, z, x + 15, 127, z + 15);
    }

    public static synchronized void requireSelectedProvider(World world) {
        Binding binding = WORLDS.get(world);
        if (binding != null && !binding.providerSelected) {
            throw new IllegalStateException("Saved generator profile cannot use this world's provider factory; "
                    + "required generation hooks are unavailable or another mod replaced the factory");
        }
    }

    public static synchronized void recordNativeSpawn(World world) {
        Binding binding = WORLDS.get(world);
        if (binding == null) {
            NativeAnchor nativeAnchor = NATIVE_ANCHORS.get(world);
            if (nativeAnchor != null && nativeAnchor.manifest.originalSpawn == null && world.isNewWorld) {
                ChunkCoordinates spawn = world.getSpawnPoint();
                WorldGeneratorManifest completed = nativeAnchor.manifest.withOriginalSpawn(spawn.x, spawn.y, spawn.z);
                try {
                    nativeAnchor.saveHandler.func_22150_b();
                    nativeAnchor.store.write(completed);
                    nativeAnchor.manifest = completed;
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot persist original native world spawn", exception);
                }
            }
            return;
        }
        if (binding.manifest.originalSpawn != null) {
            return;
        }
        ChunkCoordinates spawn = world.getSpawnPoint();
        WorldGeneratorManifest completed = binding.manifest.withOriginalSpawn(spawn.x, spawn.y, spawn.z);
        try {
            binding.saveHandler.func_22150_b();
            binding.store.write(completed);
            binding.manifest = completed;
            if (world.getWorldChunkManager() instanceof ProfileWorldChunkManager) {
                ((ProfileWorldChunkManager) world.getWorldChunkManager()).setFieldContext(fieldContext(world));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist original world spawn", exception);
        }
    }

    public static synchronized WorldGeneratorProfile profile(World world) {
        Binding binding = WORLDS.get(world);
        return binding == null ? null : binding.profile;
    }

    public static synchronized boolean contentEnabled(World world, boolean structure, String key, String secondaryKey,
            Set<String> tags) {
        WorldGeneratorProfile profile = profile(world);
        return profile == null || profile.settings == null
                || (structure ? profile.settings.structures : profile.settings.features).matches(key, secondaryKey,
                        tags);
    }

    public static synchronized double authoredHeight(World world, String output, int x, int z) {
        WorldGeneratorProfile profile = profile(world);
        if (profile == null || profile.settings == null || !profile.settings.heightOutputs.containsKey(output)) {
            return Double.NaN;
        }
        return profile.settings.heightOutputs.get(output).sample(fieldContext(world), x, 0, z);
    }

    public static synchronized WorldGeneratorManifest manifest(World world) {
        Binding binding = WORLDS.get(world);
        NativeAnchor nativeAnchor = NATIVE_ANCHORS.get(world);
        return binding == null ? nativeAnchor == null ? null : nativeAnchor.manifest : binding.manifest;
    }

    public static synchronized FieldContext fieldContext(World world) {
        WorldGeneratorManifest manifest = manifest(world);
        WorldGeneratorManifest.OriginalSpawn spawn = manifest == null ? null : manifest.originalSpawn;
        return new FieldContext(world.getRandomSeed(), world.worldProvider.worldType, spawn != null,
                spawn == null ? 0 : spawn.x, spawn == null ? 0 : spawn.z,
                world.getWorldChunkManager() instanceof FieldContext.Environment
                        ? (FieldContext.Environment) world.getWorldChunkManager()
                        : new NativeBiomeEnvironment(world.getWorldChunkManager()),
                spatial(world));
    }

    public static synchronized SpatialRegistry.Snapshot spatial(World world) {
        Binding binding = WORLDS.get(world);
        return binding == null ? SpatialRegistry.snapshot() : binding.spatial;
    }

    public static boolean biomeMatches(World world, int x, int z, Set<String> selectors) {
        FieldContext.Environment environment = fieldContext(world).environment;
        for (String selector : selectors) {
            if (environment.biomeMatches(selector, x, z)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized TerrainRegistry.Snapshot terrainDefinitions(World world) {
        Binding binding = WORLDS.get(world);
        return binding == null ? TerrainRegistry.snapshot() : binding.terrainDefinitions;
    }

    public static synchronized Map<AssetKey, LootTableDefinition> lootDefinitions(World world) {
        Binding binding = WORLDS.get(world);
        return binding == null ? LootTableRegistry.captureDefinitions() : binding.lootDefinitions;
    }

    private static final class NativeAnchor {
        private WorldGeneratorManifest manifest;
        private final GeneratorManifestStore store;
        private final ISaveHandler saveHandler;

        private NativeAnchor(WorldGeneratorManifest manifest, GeneratorManifestStore store, ISaveHandler saveHandler) {
            this.manifest = manifest;
            this.store = store;
            this.saveHandler = saveHandler;
        }
    }

    private static final class Binding {
        private final WorldGeneratorProfile profile;
        private final GeneratorManifestStore store;
        private final ISaveHandler saveHandler;
        private WorldGeneratorManifest manifest;
        private boolean providerSelected;
        private final SpatialRegistry.Snapshot spatial;
        private final TerrainRegistry.Snapshot terrainDefinitions;
        private final Map<AssetKey, LootTableDefinition> lootDefinitions;

        private Binding(WorldGeneratorProfile profile, WorldGeneratorManifest manifest, GeneratorManifestStore store,
                ISaveHandler saveHandler) {
            this.profile = profile;
            lootDefinitions = LootTableRegistry.captureDefinitions();
            terrainDefinitions = TerrainRegistry.snapshot();
            this.manifest = manifest;
            this.store = store;
            this.saveHandler = saveHandler;
            spatial = profile.spatial == null ? SpatialRegistry.snapshot() : profile.spatial;
        }
    }
}
