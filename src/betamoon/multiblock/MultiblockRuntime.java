package betamoon.multiblock;

import betamoon.interop.runtime.NativeEventAccess;
import betamoon.luaapi.interop.NativeCallbackTable;

import betamoon.BetaMoonCommon;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.luaapi.multiblock.MultiblocksApi;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptExecution;
import betamoon.multiblock.MultiblockDefinition.Cell;
import betamoon.multiblock.MultiblockDefinition.Variant;
import betamoon.multiblock.MultiblockReport.Match;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import net.minecraft.src.Chunk;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTBase;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Main-thread recognition. Native observers only update dependencies and
 * queues, never invoke Lua.
 */
public final class MultiblockRuntime {
    private static final String STORAGE_KEY = "betamoon_multiblocks";
    private static final Map<World, RuntimeWorld> WORLDS = new WeakHashMap<>();

    private MultiblockRuntime() {
    }

    private static RuntimeWorld runtime(World world) {
        RuntimeWorld runtime = WORLDS.get(world);
        if (runtime == null) {
            runtime = new RuntimeWorld(world);
            WORLDS.put(world, runtime);
        }
        return runtime;
    }

    public static void chunkLoaded(Chunk chunk) {
        if (chunk != null) {
            ControllerlessMultiblockRuntime.loaded(chunk);
        }
        if (chunk != null) {
            RuntimeWorld runtime = runtime(chunk.worldObj);
            long key = MultiblockPosition.chunk(chunk.xPosition, chunk.zPosition);
            Chunk previous = runtime.chunks.put(key, chunk);
            if (previous != chunk) {
                runtime.queueDiscovery(key);
                runtime.chunkChanged(key);
            }
        }
    }

    public static void chunkUnloaded(Chunk chunk) {
        ControllerlessMultiblockRuntime.unloaded(chunk);
        RuntimeWorld runtime = WORLDS.get(chunk.worldObj);
        if (runtime != null) {
            long key = MultiblockPosition.chunk(chunk.xPosition, chunk.zPosition);
            if (runtime.chunks.get(key) == chunk) {
                runtime.chunks.remove(key);
                runtime.discovery.remove(key);
                runtime.chunkChanged(key);
            }
        }
    }

    /**
     * Entry invalidation makes even callbacks executed inside a setter see an
     * unusable structure.
     */
    public static void changing(Chunk chunk, int x, int y, int z) {
        ControllerlessMultiblockRuntime.changing(chunk, x, y, z);
        RuntimeWorld runtime = WORLDS.get(chunk.worldObj);
        if (runtime != null) {
            MultiblockPosition position = new MultiblockPosition(chunk.xPosition * 16 + x, y, chunk.zPosition * 16 + z);
            runtime.changed(position);
            Instance existing = runtime.instances.get(position);
            if (existing != null && chunk.getBlockID(x, y, z) != existing.definition.controller) {
                // A nested setter can observe an intermediate removal before the outer setter
                // returns.
                runtime.retire(existing, true);
            }
        }
    }

    public static void changed(Chunk chunk, int x, int y, int z, int oldState) {
        if (chunk.worldObj.multiplayerWorld) {
            return;
        }
        int id = chunk.getBlockID(x, y, z);
        int metadata = chunk.getBlockMetadata(x, y, z);
        if ((id << 4 | metadata) == oldState) {
            return;
        }
        ControllerlessMultiblockRuntime.changed(chunk, x, y, z);
        RuntimeWorld runtime = runtime(chunk.worldObj);
        MultiblockPosition position = new MultiblockPosition(chunk.xPosition * 16 + x, y, chunk.zPosition * 16 + z);
        runtime.changed(position);
        if ((oldState >> 4) != id) {
            runtime.forgetSavedController(position, oldState >> 4);
        }
        Instance previous = runtime.instances.get(position);
        if (previous != null && previous.definition.controller != id) {
            runtime.retire(previous, true);
        }
        if (runtime.controllers.containsKey(id)) {
            if (runtime.pendingControllers.size() < 4096) {
                runtime.pendingControllers.add(position);
            } else {
                runtime.queueDiscovery(position.chunk());
            }
        }
    }

    /**
     * Bulk writers which directly edit chunk arrays can explicitly request
     * reconciliation.
     */
    public static void reconcile(Chunk chunk) {
        chunkLoaded(chunk);
        ControllerlessMultiblockRuntime.reconcile(chunk);
        RuntimeWorld runtime = WORLDS.get(chunk.worldObj);
        if (runtime != null) {
            long key = MultiblockPosition.chunk(chunk.xPosition, chunk.zPosition);
            runtime.queueDiscovery(key);
            runtime.chunkChanged(key);
        }
    }

    public static void definitionsChanged() {
        ControllerlessMultiblockRuntime.definitionsChanged();
        for (RuntimeWorld runtime : new ArrayList<RuntimeWorld>(WORLDS.values())) {
            runtime.refreshDefinitions();
        }
    }

    public static void tick(World world) {
        if (world != null && !world.multiplayerWorld) {
            boolean managed = ControllerlessMultiblockRuntime.instanceCount(world) > 0;
            for (MultiblockDefinition definition : MultiblockRegistry.all()) {
                managed |= definition.management != null;
            }
            int allowance = MultiblockLimits.visitsPerTick();
            runtime(world).tick(managed ? allowance / 2 : allowance);
            ControllerlessMultiblockRuntime.tick(world, managed ? allowance - allowance / 2 : 0);
        }
    }

    public static void unload(World world) {
        ControllerlessMultiblockRuntime.unload(world);
        RuntimeWorld runtime = WORLDS.remove(world);
        if (runtime != null) {
            for (Instance instance : runtime.instances.values()) {
                instance.dirty = true;
                instance.expired = true;
            }
        }
    }

    public static boolean hasControllerAt(World world, int x, int y, int z) {
        RuntimeWorld runtime = WORLDS.get(world);
        return runtime != null && !world.multiplayerWorld && y >= 0 && y < 128 && runtime.loaded(x, z)
                && runtime.controllers.containsKey(runtime.blockId(x, y, z));
    }

    public static Instance get(World world, int x, int y, int z) {
        if (world == null || world.multiplayerWorld || !MultiblockMatchJob.inBounds(x, y, z)) {
            return null;
        }
        RuntimeWorld runtime = runtime(world);
        MultiblockPosition position = new MultiblockPosition(x, y, z);
        if (!runtime.loaded(x, z)) {
            return null;
        }
        return runtime.discover(position);
    }

    public static int instanceCount(World world) {
        RuntimeWorld runtime = WORLDS.get(world);
        return runtime == null ? 0 : runtime.instances.size();
    }

    public static int ownerCount(World world, String owner) {
        RuntimeWorld runtime = WORLDS.get(world);
        return runtime == null ? 0 : runtime.ownerCounts.getOrDefault(owner, 0);
    }

    public static List<MultiblockInstance> at(World world, int x, int y, int z) {
        RuntimeWorld runtime = WORLDS.get(world);
        List<MultiblockInstance> result = new ArrayList<>();
        if (runtime != null) {
            MultiblockPosition point = new MultiblockPosition(x, y, z);
            Set<Instance> candidates = runtime.watchers.get(point.chunk());
            if (candidates != null) {
                for (Instance instance : candidates) {
                    if (!instance.expired && instance.footprint.contains(point)) {
                        result.add(instance);
                    }
                }
            }
        }
        return result;
    }

    public static MultiblockInstance conflict(World world, MultiblockReport report, boolean exclusive) {
        RuntimeWorld runtime = WORLDS.get(world);
        if (runtime != null) {
            for (Match match : report.matches) {
                Set<Instance> owners = runtime.claims.get(new MultiblockPosition(match.x, match.y, match.z));
                if (owners != null) {
                    for (Instance owner : owners) {
                        if (!owner.expired && (exclusive || owner.definition.exclusive)) {
                            return owner;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Map<String, Long> statistics(World world) {
        RuntimeWorld runtime = WORLDS.get(world);
        Map<String, Long> result = new LinkedHashMap<>();
        if (runtime != null) {
            result.put("visits", runtime.visits);
            result.put("ticks", runtime.ticks);
            result.put("instances", (long) runtime.instances.size());
            result.put("discoveryChunks", (long) runtime.discovery.size());
            long pending = 0;
            for (Instance instance : runtime.instances.values()) {
                if (instance.dirty) {
                    pending++;
                }
            }
            result.put("pending", pending);
        }
        return Collections.unmodifiableMap(result);
    }

    public static MultiblockMatchJob.Reader reader(World world) {
        RuntimeWorld runtime = WORLDS.get(world);
        return new MultiblockMatchJob.Reader() {
            public boolean loaded(int x, int z) {
                // Remote diagnostics may read chunks only after the native availability check.
                if (runtime != null && runtime.loaded(x, z)) {
                    return true;
                }
                return !world.multiplayerWorld && (runtime == null || !AgentRuntime.isRegistered())
                        && world.blockExists(x, 64, z);
            }

            public int blockId(int x, int y, int z) {
                return runtime == null || !runtime.loaded(x, z) ? world.getBlockId(x, y, z) : runtime.blockId(x, y, z);
            }

            public int metadata(int x, int y, int z) {
                return runtime == null || !runtime.loaded(x, z)
                        ? world.getBlockMetadata(x, y, z)
                        : runtime.metadata(x, y, z);
            }
        };
    }

    public static int facing(MultiblockDefinition definition, MultiblockMatchJob.Reader reader, int x, int y, int z) {
        if (definition.fixedFacing != null) {
            return definition.fixedFacing.intValue();
        }
        return MultiblockOrientation
                .facing(definition.controllerState.get(reader.metadata(x, y, z), "facing").tojstring());
    }

    public static final class Instance extends MultiblockInstance {
        private boolean usableHint;
        private boolean resume;
        private long revision;
        private long jobRevision;
        private MultiblockMatchJob job;
        private final Set<MultiblockPosition> footprint = new LinkedHashSet<>();
        private final Set<Long> watchedChunks = new LinkedHashSet<>();
        private final Set<MultiblockPosition> claimed = new LinkedHashSet<>();
        private final RuntimeWorld runtime;
        private final NBTTagCompound saved;
        private final String savedKey;

        private Instance(RuntimeWorld runtime, MultiblockPosition position, MultiblockDefinition definition,
                NBTTagCompound saved, String savedKey) {
            super(position, definition, saved, () -> runtime.storage().markDirty());
            this.runtime = runtime;
            this.saved = saved;
            this.savedKey = savedKey;
            usableHint = saved.getBoolean("Formed");
            resume = usableHint;
            saved.setString("Identity", identity);
            saved.setString("Definition", definition.key.toString());
            saved.setInteger("Controller", definition.controller);
            saved.setInteger("X", position.x);
            saved.setInteger("Y", position.y);
            saved.setInteger("Z", position.z);
            runtime.storage().records().setCompoundTag(savedKey, saved);
            runtime.savedPositions.computeIfAbsent(position, ignored -> new LinkedHashSet<>()).add(savedKey);
            runtime.storage().markDirty();
        }

        @Override
        public World world() {
            return runtime.world;
        }

        public void requireCurrent() {
            if (expired || WORLDS.get(runtime.world) != runtime
                    || MultiblockRegistry.find(definition.key) != definition) {
                throw new IllegalStateException(
                        "Multiblock instance access expired after removal, reload or world change");
            }
        }

        public void requestCheck() {
            requireCurrent();
            invalidate();
        }

        private void invalidate() {
            dirty = true;
            revision++;
            job = null;
        }

        private void saveHint(boolean formed) {
            saved.setBoolean("Formed", formed);
            runtime.storage().markDirty();
        }
    }

    private static final class RuntimeWorld implements MultiblockMatchJob.Reader {
        private final World world;
        private final String storageKey;
        private final Map<Long, Chunk> chunks = new LinkedHashMap<>();
        private final Map<Long, Integer> discovery = new LinkedHashMap<>();
        private final Map<Integer, MultiblockDefinition> controllers = new HashMap<>();
        private final Map<String, Integer> ownerCounts = new HashMap<>();
        private final Map<MultiblockPosition, Set<String>> savedPositions = new HashMap<>();
        private final Map<MultiblockPosition, Instance> instances = new TreeMap<>();
        private final Map<Long, Set<Instance>> watchers = new HashMap<>();
        private final Map<MultiblockPosition, Set<Instance>> claims = new HashMap<>();
        private final Set<MultiblockPosition> pendingControllers = new LinkedHashSet<>();
        private final List<Instance> removed = new ArrayList<>();
        private MultiblockWorldData storage;
        private long ticks;
        private int maintenanceCursor;
        private boolean capWarning;
        private long visits;
        private int ownerTurn;
        private final Map<String, MultiblockPosition> ownerTurns = new HashMap<>();
        private final Map<MultiblockPosition, MultiblockReport> completed = new TreeMap<>();

        private RuntimeWorld(World world) {
            this.world = world;
            storageKey = STORAGE_KEY + "_" + world.worldProvider.worldType;
            refreshDefinitions();
        }

        private MultiblockWorldData storage() {
            if (storage == null) {
                storage = (MultiblockWorldData) world.loadItemData(MultiblockWorldData.class, storageKey);
                if (storage == null) {
                    storage = new MultiblockWorldData(storageKey);
                    world.setItemData(storageKey, storage);
                }
                for (Object value : storage.records().func_28110_c()) {
                    if (value instanceof NBTTagCompound) {
                        NBTTagCompound record = (NBTTagCompound) value;
                        MultiblockPosition position = new MultiblockPosition(record.getInteger("X"),
                                record.getInteger("Y"), record.getInteger("Z"));
                        savedPositions.computeIfAbsent(position, ignored -> new LinkedHashSet<>()).add(record.getKey());
                    }
                }
            }
            return storage;
        }

        public boolean loaded(int x, int z) {
            return chunks.containsKey(MultiblockPosition.chunk(x >> 4, z >> 4));
        }

        public int blockId(int x, int y, int z) {
            return chunks.get(MultiblockPosition.chunk(x >> 4, z >> 4)).getBlockID(x & 15, y, z & 15);
        }

        public int metadata(int x, int y, int z) {
            return chunks.get(MultiblockPosition.chunk(x >> 4, z >> 4)).getBlockMetadata(x & 15, y, z & 15);
        }

        private void queueDiscovery(long key) {
            if (discovery.containsKey(key) || discovery.size() < 4096) {
                discovery.put(key, 0);
            }
        }

        private void refreshDefinitions() {
            removed.removeIf(instance -> MultiblockRegistry.find(instance.definition.key) != instance.definition);
            controllers.clear();
            for (MultiblockDefinition definition : MultiblockRegistry.all()) {
                if (definition.controller >= 0) {
                    controllers.put(definition.controller, definition);
                }
            }
            for (Instance instance : new ArrayList<Instance>(instances.values())) {
                if (MultiblockRegistry.find(instance.definition.key) != instance.definition) {
                    // Keep its save record dormant. A compatible replacement uses the previous
                    // formed hint.
                    retire(instance, false);
                }
            }
            for (Long chunk : chunks.keySet()) {
                queueDiscovery(chunk);
            }
        }

        private void changed(MultiblockPosition position) {
            Set<Instance> affected = watchers.get(position.chunk());
            if (affected != null) {
                for (Instance instance : new ArrayList<Instance>(affected)) {
                    if (instance.footprint.contains(position)) {
                        instance.invalidate();
                    }
                }
            }
        }

        private void chunkChanged(long key) {
            Set<Instance> affected = watchers.get(key);
            if (affected != null) {
                for (Instance instance : new ArrayList<Instance>(affected)) {
                    instance.invalidate();
                }
            }
        }

        private void watch(Instance instance) {
            int facing = facing(instance.definition, this, instance.position.x, instance.position.y,
                    instance.position.z);
            if (!instance.footprint.isEmpty() && instance.facing == facing) {
                return;
            }
            unwatch(instance);
            instance.facing = facing;
            for (Variant variant : instance.definition.variants) {
                for (int reflection = 0; reflection < (instance.definition.mirror ? 2 : 1); reflection++) {
                    for (Cell cell : variant.cells) {
                        MultiblockPosition position = new MultiblockPosition(
                                instance.position.x
                                        + MultiblockOrientation.x(cell.x, cell.z, instance.facing, reflection == 1),
                                instance.position.y + cell.y, instance.position.z
                                        + MultiblockOrientation.z(cell.x, cell.z, instance.facing, reflection == 1));
                        instance.footprint.add(position);
                        instance.watchedChunks.add(position.chunk());
                    }
                }
            }
            for (Long key : instance.watchedChunks) {
                watchers.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(instance);
            }
        }

        private void unwatch(Instance instance) {
            for (Long key : instance.watchedChunks) {
                Set<Instance> chunk = watchers.get(key);
                if (chunk != null) {
                    chunk.remove(instance);
                    if (chunk.isEmpty()) {
                        watchers.remove(key);
                    }
                }
            }
            instance.watchedChunks.clear();
            instance.footprint.clear();
        }

        private Instance discover(MultiblockPosition position) {
            if (!loaded(position.x, position.z) || !MultiblockMatchJob.inBounds(position.x, position.y, position.z)) {
                return null;
            }
            Instance existing = instances.get(position);
            if (existing != null) {
                if (existing.definition.controller == blockId(position.x, position.y, position.z)) {
                    return existing;
                }
                retire(existing, true);
            }
            MultiblockDefinition definition = controllers.get(blockId(position.x, position.y, position.z));
            if (definition == null) {
                return null;
            }
            int owned = ownerCounts.getOrDefault(definition.owner, 0);
            if (instances.size() + ControllerlessMultiblockRuntime.instanceCount(world) >= MultiblockLimits
                    .worldInstances()
                    || owned + ControllerlessMultiblockRuntime.ownerCount(world, definition.owner) >= MultiblockLimits
                            .ownerInstances()) {
                if (!capWarning) {
                    BetaMoonCommon.LOGGER
                            .warning("Multiblock instance limit reached; controllers remain queued for discovery");
                    capWarning = true;
                }
                return null;
            }
            String key = definition.key + "@" + position;
            NBTTagCompound saved = storage().records().getCompoundTag(key);
            if (saved.hasKey("Controller") && saved.getInteger("Controller") != definition.controller) {
                saved = new NBTTagCompound();
            }
            Instance instance = new Instance(this, position, definition, saved, key);
            instances.put(position, instance);
            ownerCounts.put(definition.owner, owned + 1);
            watch(instance);
            return instance;
        }

        private void retire(Instance instance, boolean destroyed) {
            instance.expired = true;
            instance.dirty = true;
            instance.status = "removed";
            instances.remove(instance.position);
            ownerCounts.put(instance.definition.owner, ownerCounts.get(instance.definition.owner) - 1);
            unwatch(instance);
            release(instance);
            if (destroyed) {
                removeSaved(instance.position, instance.savedKey);
                removed.add(instance);
            }
        }

        private void forgetSavedController(MultiblockPosition position, int oldId) {
            storage();
            Set<String> keys = savedPositions.get(position);
            if (keys != null) {
                for (String key : new ArrayList<String>(keys)) {
                    if (storage.records().getCompoundTag(key).getInteger("Controller") == oldId) {
                        removeSaved(position, key);
                    }
                }
            }
        }

        private void removeSaved(MultiblockPosition position, String key) {
            Iterator<?> iterator = storage().records().func_28110_c().iterator();
            while (iterator.hasNext()) {
                if (((NBTBase) iterator.next()).getKey().equals(key)) {
                    iterator.remove();
                    break;
                }
            }
            Set<String> keys = savedPositions.get(position);
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    savedPositions.remove(position);
                }
            }
            storage().markDirty();
        }

        private void release(Instance instance) {
            for (MultiblockPosition position : instance.claimed) {
                Set<Instance> owners = claims.get(position);
                if (owners != null) {
                    owners.remove(instance);
                    if (owners.isEmpty()) {
                        claims.remove(position);
                    }
                }
                claimReleased(position, instance);
            }
            instance.claimed.clear();
        }

        private void claimReleased(MultiblockPosition position, Instance released) {
            Set<Instance> affected = watchers.get(position.chunk());
            if (affected != null) {
                for (Instance instance : affected) {
                    if (instance != released && instance.footprint.contains(position)
                            && instance.report.reason.equals("exclusive_conflict")) {
                        instance.invalidate();
                    }
                }
            }
        }

        private void tick(int allowance) {
            ticks++;
            notifyRemoved();
            discoverPlaced();
            completed.clear();

            int scanSpent = discoverLoadedChunks(allowance / 4);
            visits += scanSpent;
            maintainUnobservedChanges();
            matchQueued(allowance - scanSpent);
            commitCompleted();
        }

        private void notifyRemoved() {
            List<Instance> retired = new ArrayList<>(removed);
            removed.clear();
            for (Instance instance : retired) {
                if (instance.usableHint) {
                    instance.report.status = "incomplete";
                    instance.report.reason = "controller_removed";
                    invoke(instance, "onInvalidated", true);
                }
            }
        }

        private void discoverPlaced() {
            List<MultiblockPosition> placed = new ArrayList<>(pendingControllers);
            pendingControllers.clear();
            Collections.sort(placed);
            for (MultiblockPosition position : placed) {
                discover(position);
            }
        }

        private int discoverLoadedChunks(int allowance) {
            int scanBudget = allowance;
            for (Long key : new ArrayList<Long>(discovery.keySet())) {
                Chunk chunk = chunks.get(key);
                if (chunk == null) {
                    discovery.remove(key);
                    continue;
                }
                int cursor = discovery.get(key);
                while (cursor < 32768 && scanBudget > 0) {
                    int index = cursor++;
                    scanBudget--;
                    int id = chunk.blocks[index] & 255;
                    if (controllers.containsKey(id)) {
                        discover(new MultiblockPosition(chunk.xPosition * 16 + (index >> 11), index & 127,
                                chunk.zPosition * 16 + ((index >> 7) & 15)));
                    }
                }
                if (cursor == 32768) {
                    discovery.remove(key);
                } else {
                    discovery.put(key, cursor);
                }
                if (scanBudget == 0) {
                    break;
                }
            }
            return allowance - scanBudget;
        }

        private void maintainUnobservedChanges() {
            if (ticks % 20 == 0) {
                if (!chunks.isEmpty()) {
                    List<Long> keys = new ArrayList<>(chunks.keySet());
                    Long key = keys.get((int) ((ticks / 20 - 1) % keys.size()));
                    if (!discovery.containsKey(key)) {
                        queueDiscovery(key);
                    }
                }
                List<Instance> all = new ArrayList<>(instances.values());
                for (int i = 0; i < Math.min(16, all.size()); i++) {
                    Instance instance = all.get(Math.floorMod(maintenanceCursor++, all.size()));
                    if (!instance.dirty) {
                        instance.invalidate();
                    }
                }
            }
        }

        private List<Deque<Instance>> readyQueues() {
            Map<String, Deque<Instance>> owners = new LinkedHashMap<>();
            for (Instance instance : instances.values()) {
                if (instance.dirty) {
                    owners.computeIfAbsent(instance.definition.owner, ignored -> new ArrayDeque<>()).add(instance);
                }
            }
            List<String> ownerOrder = new ArrayList<>(owners.keySet());
            if (!ownerOrder.isEmpty()) {
                Collections.rotate(ownerOrder, -(ownerTurn++ % ownerOrder.size()));
            }
            List<Deque<Instance>> queues = new ArrayList<>();
            for (String owner : ownerOrder) {
                Deque<Instance> queue = owners.get(owner);
                queues.add(queue);
                MultiblockPosition last = ownerTurns.get(owner);
                if (last != null) {
                    int remaining = queue.size();
                    while (remaining-- > 0 && queue.peek().position.compareTo(last) <= 0) {
                        queue.add(queue.poll());
                    }
                }
            }
            return queues;
        }

        private void matchQueued(int budget) {
            List<Deque<Instance>> queues = readyQueues();
            boolean progress = true;
            while (budget > 0 && progress) {
                progress = false;
                for (Deque<Instance> queue : queues) {
                    Instance instance = queue.poll();
                    if (instance == null) {
                        continue;
                    }
                    progress = true;
                    ownerTurns.put(instance.definition.owner, instance.position);
                    int spent = advance(instance, Math.min(256, budget));
                    visits += spent;
                    budget -= spent;
                    if (instance.job != null && !instance.job.done() && !instance.expired) {
                        queue.add(instance);
                    }
                    if (budget <= 0) {
                        break;
                    }
                }
            }
        }

        private void commitCompleted() {
            for (Map.Entry<MultiblockPosition, MultiblockReport> entry : completed.entrySet()) {
                Instance instance = instances.get(entry.getKey());
                if (instance != null && !instance.expired && instance.jobRevision == instance.revision) {
                    commit(instance, entry.getValue());
                }
            }
        }

        private int advance(Instance instance, int budget) {
            if (instance.expired) {
                return 1;
            }
            MultiblockPosition position = instance.position;
            if (!loaded(position.x, position.z)) {
                MultiblockReport report = new MultiblockReport();
                report.reason = "chunk_unavailable";
                instance.jobRevision = instance.revision;
                completed.put(instance.position, report);
                return 1;
            }
            if (blockId(position.x, position.y, position.z) != instance.definition.controller) {
                retire(instance, true);
                return 1;
            }
            if (instance.job == null) {
                watch(instance);
                instance.job = new MultiblockMatchJob(instance.definition, this, position.x, position.y, position.z,
                        instance.facing, false, false, null, true);
                instance.jobRevision = instance.revision;
            }
            MultiblockMatchJob job = instance.job;
            int spent = job.advance(budget);
            if (job.done() && instance.jobRevision == instance.revision) {
                instance.job = null;
                completed.put(instance.position, job.report());
            }
            return Math.max(1, spent);
        }

        private void commit(Instance instance, MultiblockReport report) {
            String previous = instance.status;
            boolean priorUsable = instance.usableHint;
            instance.conflict = null;
            if (report.status.equals("complete")) {
                MultiblockInstance conflict = ControllerlessMultiblockRuntime.conflict(world, report,
                        instance.definition.exclusive);
                if (conflict != null) {
                    instance.conflict = conflict.position;
                    report.status = "incomplete";
                    report.reason = "exclusive_conflict";
                }
            }
            if (report.status.equals("complete")) {
                for (Match match : report.matches) {
                    if (match.blockId == 0) {
                        continue;
                    }
                    Set<Instance> owners = claims.get(new MultiblockPosition(match.x, match.y, match.z));
                    if (owners != null) {
                        for (Instance owner : owners) {
                            if (owner != instance && (instance.definition.exclusive || owner.definition.exclusive)) {
                                instance.conflict = owner.position;
                                report.status = "incomplete";
                                report.reason = "exclusive_conflict";
                                break;
                            }
                        }
                    }
                    if (instance.conflict != null) {
                        break;
                    }
                }
            }
            instance.report = report;
            instance.mirror = report.mirror;
            instance.dirty = false;
            if (report.status.equals("complete")) {
                Set<MultiblockPosition> desired = new LinkedHashSet<>();
                for (Match match : report.matches) {
                    if (match.blockId != 0) {
                        desired.add(new MultiblockPosition(match.x, match.y, match.z));
                    }
                }
                for (MultiblockPosition old : new ArrayList<MultiblockPosition>(instance.claimed)) {
                    if (!desired.contains(old)) {
                        Set<Instance> owners = claims.get(old);
                        owners.remove(instance);
                        if (owners.isEmpty()) {
                            claims.remove(old);
                        }
                        instance.claimed.remove(old);
                        claimReleased(old, instance);
                    }
                }
                // Keep existing claims during valid-to-valid rechecks; no observable
                // release/reacquire gap.
                for (Match match : report.matches) {
                    if (match.blockId != 0) {
                        MultiblockPosition position = new MultiblockPosition(match.x, match.y, match.z);
                        claims.computeIfAbsent(position, ignored -> new LinkedHashSet<>()).add(instance);
                        instance.claimed.add(position);
                    }
                }
                instance.status = "formed";
                instance.usableHint = true;
                instance.saveHint(true);
                if (instance.resume) {
                    instance.resume = false;
                    invoke(instance, "onResumed", false);
                } else if (!priorUsable) {
                    invoke(instance, "onFormed", false);
                }
            } else if (report.status.equals("unavailable")) {
                release(instance);
                instance.dirty = false;
                instance.status = "suspended";
                instance.resume = priorUsable;
                if (priorUsable && !previous.equals("suspended")) {
                    invoke(instance, "onSuspended", false);
                }
            } else {
                release(instance);
                instance.dirty = false;
                instance.status = "unformed";
                instance.usableHint = false;
                instance.resume = false;
                instance.saveHint(false);
                if (priorUsable) {
                    invoke(instance, "onInvalidated", false);
                }
            }
        }

        private void invoke(Instance instance, String name, boolean removed) {
            LuaValue callback = instance.definition.callbacks.get(name);
            if (callback == null || callback.isnil()) {
                return;
            }
            try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
                NativeEventAccess.configure(scope, world, null);
                LuaTable context = new NativeCallbackTable(scope);
                context.set("x", instance.position.x);
                context.set("y", instance.position.y);
                context.set("z", instance.position.z);
                context.set("key", instance.definition.key.toString());
                context.set("owner", instance.definition.owner);
                context.set("reason", instance.report.reason);
                context.set("report", MultiblocksApi.report(instance.definition, instance.position, instance.facing,
                        instance.mirror, instance.report));
                context.set("multiblock", MultiblocksApi.instance(scope, instance, removed));
                context.set("world", LuaWorldActionAccess.create(scope, world, instance.position.x, instance.position.y,
                        instance.position.z));
                context.set("data", MultiblocksApi.data(scope, instance, removed));
                ScriptExecution.invoke(instance.definition.owner, "multiblocks." + name, callback, context);
            } catch (Throwable error) {
                instance.callbackError = name + ": " + error.getMessage();
                LuaScriptErrors.add(instance.definition.owner,
                        "Multiblock " + instance.definition.key + " " + instance.callbackError,
                        LuaScriptErrors.Outcome.UNKNOWN, error);
            }
        }
    }
}
