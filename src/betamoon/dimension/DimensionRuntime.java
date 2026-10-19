package betamoon.dimension;

import betamoon.dimension.storage.DimensionMapStorage;
import betamoon.dimension.storage.DimensionRootSaveHandler;
import betamoon.dimension.storage.DimensionSaveHandler;
import betamoon.worldgen.creation.WorldGeneratorFallback;
import betamoon.worldgen.terrain.ProfileWorldChunkManager;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import net.minecraft.src.WorldProvider;

/**
 * Installs custom identity/storage before native chunk access, including
 * initial save reopening.
 */
public final class DimensionRuntime {
    private static final Map<World, WorldBinding> WORLDS = new WeakHashMap<World, WorldBinding>();
    private static final ThreadLocal<DimensionKey> CREATING = new ThreadLocal<DimensionKey>();

    private DimensionRuntime() {
    }

    public static synchronized void prepare(World world, ISaveHandler save) {
        if (world.multiplayerWorld || WORLDS.containsKey(world) || save == null || !DimensionSession.needed(save)) {
            return;
        }
        try {
            DimensionSession session = DimensionSession.get(world, save);
            WorldGeneratorFallback.retainFor(session.root);
            DimensionSaveHandler child = save instanceof DimensionSaveHandler ? (DimensionSaveHandler) save : null;
            NBTTagCompound savedPlayer = world.getWorldInfo().getPlayerNBTTagCompound();
            if (CREATING.get() != null) {
                world.getWorldInfo().setPlayerNBTTagCompound(null);
            } else if (child == null && savedPlayer != null) {
                session.reconcile(savedPlayer);
                DimensionKey key = session.savedDimension(savedPlayer);
                if (!key.isNative()) {
                    DimensionCapabilities.requireReady(true);
                    child = session.child(key);
                    WorldInfo info = child.loadWorldInfo();
                    info.setPlayerNBTTagCompound(savedPlayer);
                    DimensionWorldAccess.replaceStorage(world, child, info);
                    DimensionWorldAccess.replaceProviderBeforeChunks(world,
                            new DimensionWorldProvider(session.binding(key), child.metadata.numericId));
                    world.worldProvider.registerWorld(world);
                }
            }
            if (child == null) {
                WORLDS.put(world, new WorldBinding(session, null));
                DimensionWorldAccess.replaceStorage(world, new DimensionRootSaveHandler(session, world),
                        world.getWorldInfo());
                world.field_28108_z = session.globalMaps;
                return;
            }
            if (!(world.worldProvider instanceof DimensionWorldProvider)) {
                throw new IOException("Dimension save handler requires its custom provider");
            }
            DimensionCapabilities.requireReady(true);
            WORLDS.put(world, new WorldBinding(session, child));
            world.field_28108_z = new DimensionMapStorage(child, session.globalMaps);
            DimensionGenerationBinding binding = ((DimensionWorldProvider) world.worldProvider).binding;
            ProfileWorldChunkManager manager = new ProfileWorldChunkManager(world.getWorldChunkManager(),
                    binding.profile == null ? null : binding.profile.biomeSource, null, binding.biomes);
            world.worldProvider.worldChunkMgr = manager;
            manager.setFieldContext(WorldGenerationRuntime.fieldContext(world));
            binding.bindCatalogs(world);
            DimensionRules.prepareWeather(world);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot safely prepare dimension storage: " + exception.getMessage(),
                    exception);
        }
    }

    public static synchronized WorldBinding binding(World world) {
        return WORLDS.get(world);
    }

    public static synchronized void attachNative(World world, DimensionSession session) {
        if (!WORLDS.containsKey(world)) {
            WorldBinding binding = new WorldBinding(session, null);
            binding.preparing = false;
            WORLDS.put(world, binding);
            DimensionWorldAccess.replaceStorage(world, new DimensionRootSaveHandler(session, world),
                    world.getWorldInfo());
            world.field_28108_z = session.globalMaps;
        }
    }

    public static synchronized boolean isPreparing(World world) {
        WorldBinding binding = WORLDS.get(world);
        return binding != null && binding.preparing;
    }

    public static synchronized void activate(World world) {
        WorldBinding binding = WORLDS.get(world);
        if (binding != null) {
            if (binding.save != null) {
                DimensionCapabilities.requireReady(true);
            }
            binding.preparing = false;
        }
    }

    public static DimensionGenerationBinding generation(World world) {
        return world != null && world.worldProvider instanceof DimensionWorldProvider
                ? ((DimensionWorldProvider) world.worldProvider).binding
                : null;
    }

    public static void releaseInactive(World world) {
        WorldBinding binding = binding(world);
        if (binding != null && DimensionIdentity.isCustom(world)) {
            binding.session.release(DimensionIdentity.key(world));
        }
    }

    public static World create(DimensionSession session, DimensionKey key) throws IOException {
        DimensionCapabilities.requireReady(!key.isNative());
        if (CREATING.get() != null) {
            throw new IOException("Nested dimension world preparation is unsupported");
        }
        CREATING.set(key);
        boolean succeeded = false;
        try {
            if (key.isNative()) {
                WorldInfo rootInfo = session.root.loadWorldInfo();
                World world = new World(session.root, rootInfo == null ? "World" : rootInfo.getWorldName(),
                        session.rootSeed,
                        WorldProvider.getProviderForDimension(key.equals(DimensionKey.NETHER) ? -1 : 0));
                succeeded = true;
                return world;
            }
            DimensionSaveHandler child = session.child(key);
            DimensionGenerationBinding binding = session.binding(key);
            World world = new World(child, binding.definition.displayName, child.metadata.seed,
                    new DimensionWorldProvider(binding, child.metadata.numericId));
            succeeded = true;
            return world;
        } finally {
            CREATING.remove();
            if (!succeeded) {
                session.release(key);
            }
        }
    }

    public static final class WorldBinding {
        public final DimensionSession session;
        public final DimensionSaveHandler save;
        private boolean preparing = true;

        private WorldBinding(DimensionSession session, DimensionSaveHandler save) {
            this.session = session;
            this.save = save;
        }
    }
}
