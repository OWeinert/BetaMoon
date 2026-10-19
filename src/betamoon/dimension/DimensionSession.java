package betamoon.dimension;

import betamoon.dimension.storage.CheckedMapStorage;
import betamoon.dimension.storage.DimensionIndexStore;
import betamoon.dimension.storage.DimensionMetadataStore;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.dimension.storage.DimensionPaths;
import betamoon.dimension.storage.DimensionRootSaveHandler;
import betamoon.dimension.storage.DimensionSaveHandler;
import betamoon.teleport.TeleportJournal;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.MapStorage;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.SaveHandler;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;

/**
 * Root ownership shared by the active world and one preparing destination. No
 * inactive World is retained.
 */
public final class DimensionSession {
    private static final Map<ISaveHandler, WeakReference<DimensionSession>> SESSIONS = new WeakHashMap<ISaveHandler, WeakReference<DimensionSession>>();
    public final ISaveHandler root;
    public final DimensionPaths paths;
    public final DimensionIndexStore index;
    public final long rootSeed;
    public final MapStorage globalMaps;
    public final TeleportJournal navigation;
    private WorldInfo rootInfo;
    private NBTTagCompound rootPlayer;
    private final Map<DimensionKey, DimensionGenerationBinding> pinned = new LinkedHashMap<DimensionKey, DimensionGenerationBinding>();

    private DimensionSession(ISaveHandler root, World world) throws IOException {
        this(root, world.getWorldInfo(), world.field_28108_z);
    }

    private DimensionSession(ISaveHandler root, WorldInfo info, MapStorage maps) throws IOException {
        this.root = root;
        if (root.getClass() != SaveHandler.class && root.getClass() != SaveOldDir.class) {
            throw new IOException("unsupported_environment: save handler has no dimension persistence adapter");
        }
        paths = DimensionPaths.forSave(root);
        rootInfo = new WorldInfo(info);
        rootPlayer = rootInfo.getPlayerNBTTagCompound();
        if (rootPlayer != null) {
            rootPlayer = DimensionNbt.copy(rootPlayer, TeleportJournal.PLAYER_BYTES);
        }
        rootInfo.setPlayerNBTTagCompound(null);
        rootSeed = rootInfo.getRandomSeed();
        index = new DimensionIndexStore(paths);
        CheckedMapStorage checkedMaps = new CheckedMapStorage(paths, "data/", maps);
        checkedMaps.validateCounterFile();
        globalMaps = checkedMaps;
        navigation = new TeleportJournal(paths);
    }

    /**
     * Opens ownership from already checked root metadata, without constructing or
     * loading any World.
     */
    public static synchronized DimensionSession open(ISaveHandler root, WorldInfo info) throws IOException {
        DimensionSession session = new DimensionSession(root, info, new MapStorage(root));
        SESSIONS.put(root, new WeakReference<DimensionSession>(session));
        return session;
    }

    public static synchronized DimensionSession get(World world, ISaveHandler save) throws IOException {
        ISaveHandler root = save instanceof DimensionSaveHandler
                ? ((DimensionSaveHandler) save).root
                : save instanceof DimensionRootSaveHandler ? ((DimensionRootSaveHandler) save).session.root : save;
        WeakReference<DimensionSession> reference = SESSIONS.get(root);
        DimensionSession session = reference == null ? null : reference.get();
        if (session == null) {
            session = new DimensionSession(root, world);
            SESSIONS.put(root, new WeakReference<DimensionSession>(session));
        }
        return session;
    }

    public static boolean needed(ISaveHandler save) {
        if (save instanceof DimensionSaveHandler || save instanceof DimensionRootSaveHandler
                || !DimensionRegistry.snapshot().isEmpty()) {
            return true;
        }
        try {
            return Files.exists(DimensionPaths.forSave(save).resolve("betamoon/dimensions.dat"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect dimension storage safely", exception);
        }
    }

    public synchronized DimensionGenerationBinding binding(DimensionKey key) {
        DimensionGenerationBinding binding = pinned.get(key);
        if (binding == null) {
            binding = DimensionRegistry.binding(key);
            if (binding != null) {
                pinned.put(key, binding);
            }
        }
        return binding;
    }

    public synchronized DimensionGenerationBinding availableBinding(DimensionKey key) {
        DimensionGenerationBinding binding = pinned.get(key);
        return binding == null ? DimensionRegistry.binding(key) : binding;
    }

    public synchronized void release(DimensionKey key) {
        pinned.remove(key);
    }

    public DimensionSaveHandler child(DimensionKey key) throws IOException {
        root.func_22150_b();
        DimensionGenerationBinding binding = binding(key);
        if (binding == null) {
            throw new IOException("dimension_unavailable: " + key);
        }
        int id = index.allocate(key);
        DimensionMetadataStore metadata = new DimensionMetadataStore(paths, binding, id, rootSeed);
        metadata.save(metadata.worldInfo());
        return new DimensionSaveHandler(root, metadata, paths, this::saveRootPlayer);
    }

    public DimensionKey savedDimension(NBTTagCompound player) throws IOException {
        return savedDimension(index, player);
    }

    public static DimensionKey savedDimension(DimensionIndexStore index, NBTTagCompound player) throws IOException {
        if (player == null) {
            return DimensionKey.OVERWORLD;
        }
        DimensionKey byId = index.resolve(player.getInteger("Dimension"));
        if (!player.hasKey("BetaMoonDimension")) {
            if (byId == null) {
                throw new IOException("Saved player dimension has no allocation mapping");
            }
            return byId;
        }
        DimensionKey byKey;
        try {
            byKey = DimensionKey.parse(player.getString("BetaMoonDimension"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid saved canonical player dimension", exception);
        }
        if (!byKey.equals(byId)) {
            throw new IOException("Saved player canonical dimension and numeric ID disagree");
        }
        return byKey;
    }

    public synchronized void rememberRootMetadata(World world) {
        if (DimensionIdentity.key(world).isNative() && !DimensionRuntime.isPreparing(world)) {
            rootInfo = new WorldInfo(world.getWorldInfo());
            rootInfo.setPlayerNBTTagCompound(null);
        }
    }

    public synchronized void saveNativeMetadata(World world, WorldInfo info, List<?> players) throws IOException {
        root.func_22150_b();
        if (!players.isEmpty()) {
            if (players.size() != 1 || !(players.get(0) instanceof EntityPlayer)
                    || ((EntityPlayer) players.get(0)).worldObj != world) {
                throw new IOException("Native root metadata requires its one local player");
            }
            NBTTagCompound saved = new NBTTagCompound();
            ((EntityPlayer) players.get(0)).writeToNBT(saved);
            // A pending operation may only save either exact journal snapshot.
            navigation.recoverySide(saved);
            rootInfo = new WorldInfo(info);
            rootInfo.setPlayerNBTTagCompound(null);
            savePlayerSnapshot(saved);
        } else {
            if (!DimensionRuntime.isPreparing(world)) {
                rootInfo = new WorldInfo(info);
                rootInfo.setPlayerNBTTagCompound(null);
            }
            writeRoot(rootPlayer);
        }
    }

    public synchronized void saveRootPlayer(EntityPlayer player) {
        root.func_22150_b();
        try {
            NBTTagCompound saved = new NBTTagCompound();
            player.writeToNBT(saved);
            saved.setString("BetaMoonDimension", DimensionIdentity.key(player.worldObj).toString());
            saved.setInteger("Dimension", player.worldObj.worldProvider.worldType);
            savePlayerSnapshot(saved);
        } catch (IOException exception) {
            throw new IllegalStateException("save_failed: cannot persist root player state", exception);
        }
    }

    public synchronized void savePlayerSnapshot(NBTTagCompound saved) throws IOException {
        NBTTagCompound detached = DimensionNbt.copy(saved, TeleportJournal.PLAYER_BYTES);
        writeRoot(detached);
        rootPlayer = detached;
    }

    private void writeRoot(NBTTagCompound saved) throws IOException {
        root.func_22150_b();
        NBTTagCompound level = rootInfo.getNBTTagCompoundWithPlayer(Collections.emptyList());
        if (saved != null) {
            level.setCompoundTag("Player", DimensionNbt.copy(saved, TeleportJournal.PLAYER_BYTES));
        }
        NBTTagCompound document = new NBTTagCompound();
        document.setCompoundTag("Data", level);
        DimensionNbt.writeCompressed(paths, "level.dat", document, 1024 * 1024);
    }

    public synchronized void reconcile(NBTTagCompound player) throws IOException {
        if (navigation.preparation() != null) {
            throw new IOException("recovery_required: unfinished platform preparation needs checked rollback");
        }
        String side = navigation.recoverySide(player);
        if (side.equals("target")) {
            navigation.complete(navigation.intent().getString("Id"));
        } else if (side.equals("source")) {
            NBTTagCompound intent = navigation.intent();
            if (intent.getTagList("Platform").tagCount() != 0) {
                throw new IOException("recovery_required: prepared platform needs checked rollback before resuming");
            }
            navigation.abort(intent.getString("Id"));
        }
    }

    /**
     * Called only after the explicit rescue backup has completed and source/target
     * agreement was verified.
     */
    public synchronized void prepareRescue(NBTTagCompound player) throws IOException {
        String side = navigation.recoverySide(player);
        if (side.equals("target")) {
            navigation.complete(navigation.intent().getString("Id"));
        } else if (side.equals("source")) {
            navigation.deferPendingCleanup(savedDimension(navigation.intent().getCompoundTag("Target")).toString());
        }
        NBTTagCompound preparation = navigation.preparation();
        if (preparation != null) {
            navigation.deferPendingCleanup(preparation.getString("Dimension"));
        }
    }
}
