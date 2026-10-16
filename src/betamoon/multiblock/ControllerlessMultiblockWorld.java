package betamoon.multiblock;

import betamoon.BetaMoonCommon;
import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.multiblock.MultiblockDefinition.Variant;
import betamoon.multiblock.MultiblockSearch.Match;
import betamoon.multiblock.MultiblockSearch.Seed;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.src.Chunk;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/**
 * Loaded-only candidate discovery and placement lifecycle. All work is on the
 * game thread.
 */
final class ControllerlessMultiblockWorld implements MultiblockMatchJob.Reader {
    final World world;
    final Map<String, ControllerlessMultiblockInstance> instances = new LinkedHashMap<>();
    private final Map<Long, Chunk> chunks = new LinkedHashMap<>();
    private final Map<Long, Integer> scans = new LinkedHashMap<>();
    private final Set<Long> rescan = new LinkedHashSet<>();
    private final Map<Integer, List<Seed>> seeds = new HashMap<>();
    private final Map<Long, Set<ControllerlessMultiblockInstance>> watchers = new HashMap<>();
    private final Deque<NBTTagCompound> restoring = new ArrayDeque<>();
    private final List<NBTTagCompound> orphaned = new ArrayList<>();
    private final Set<MultiblockPosition> orphanOutputs = new LinkedHashSet<>();
    private MultiblockWorldData storage;
    private final Set<MultiblockPosition> pendingSeeds = new LinkedHashSet<>();
    private MultiblockPosition searchPoint;
    private List<Seed> searchSeeds;
    private int chunkTurn;
    private MultiblockSearch search;
    private long revision;
    private long searchRevision;
    private int instanceTurn;
    private final Map<String, Integer> ownerTurns = new HashMap<>();
    private int workRemaining;
    private int validationAllowance;
    private boolean inTick;
    private int maintenance;
    private int maintenanceInstanceTurn;
    private int orphanTurn;
    private final Map<String, Integer> orphanBlockTurns = new HashMap<>();

    ControllerlessMultiblockWorld(World world) {
        this.world = world;
        refresh();
    }

    boolean current() {
        return ControllerlessMultiblockRuntime.current(world, this);
    }

    NBTTagCompound records() {
        if (storage == null) {
            String name = "betamoon_multiblock_placements_" + world.worldProvider.worldType;
            storage = (MultiblockWorldData) world.loadItemData(MultiblockWorldData.class, name);
            if (storage == null) {
                storage = new MultiblockWorldData(name);
                world.setItemData(name, storage);
            }
            for (Object value : storage.records().func_28110_c()) {
                if (value instanceof NBTTagCompound) {
                    restoring.add((NBTTagCompound) value);
                }
            }
        }
        return storage.records();
    }

    void markDirty() {
        records();
        storage.markDirty();
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

    void loaded(Chunk chunk) {
        long key = MultiblockPosition.chunk(chunk.xPosition, chunk.zPosition);
        if (chunks.put(key, chunk) != chunk) {
            revision++;
            for (long candidate : chunks.keySet()) {
                queue(candidate);
            }
            invalidateChunk(key);
        }
    }

    void unloaded(Chunk chunk) {
        long key = MultiblockPosition.chunk(chunk.xPosition, chunk.zPosition);
        if (chunks.get(key) == chunk) {
            chunks.remove(key);
            scans.remove(key);
            rescan.remove(key);
            revision++;
            invalidateChunk(key);
        }
    }

    private void invalidateChunk(long key) {
        Set<ControllerlessMultiblockInstance> affected = watchers.get(key);
        if (affected != null) {
            for (ControllerlessMultiblockInstance instance : affected) {
                instance.invalidate();
            }
        }
    }

    void changing(MultiblockPosition point) {
        revision++;
        Set<ControllerlessMultiblockInstance> affected = watchers.get(point.chunk());
        if (affected != null) {
            for (ControllerlessMultiblockInstance instance : new ArrayList<>(affected)) {
                if (instance.footprint.contains(point)) {
                    instance.changing(point);
                }
            }
        }
    }

    void reconcile(Chunk chunk) {
        long key = MultiblockPosition.chunk(chunk.xPosition, chunk.zPosition);
        revision++;
        invalidateChunk(key);
        queue(key);
    }

    void changed(Chunk chunk, int x, int y, int z) {
        if (!seeds.isEmpty() && seeds.containsKey(chunk.getBlockID(x, y, z))) {
            if (pendingSeeds.size() < 4096) {
                pendingSeeds.add(new MultiblockPosition(chunk.xPosition * 16 + x, y, chunk.zPosition * 16 + z));
            } else {
                queue(MultiblockPosition.chunk(chunk.xPosition, chunk.zPosition));
            }
        }
    }

    private void queue(long key) {
        if (scans.containsKey(key)) {
            rescan.add(key);
        } else if (scans.size() < 4096) {
            scans.put(key, 0);
        }
    }

    void refresh() {
        seeds.clear();
        search = null;
        revision++;
        for (MultiblockDefinition definition : MultiblockRegistry.all()) {
            MultiblockManagement policy = definition.management;
            if (policy != null && !policy.manual) {
                for (Seed seed : MultiblockSearch.seeds(definition, policy.seeds, policy.facings, definition.mirror,
                        null)) {
                    for (int id : seed.cell.rule.candidateBlocks()) {
                        seeds.computeIfAbsent(id, ignored -> new ArrayList<>()).add(seed);
                    }
                }
            }
        }
        for (Map.Entry<Integer, List<Seed>> indexed : seeds.entrySet()) {
            Map<String, Deque<Seed>> owners = new TreeMap<>();
            for (Seed seed : indexed.getValue()) {
                owners.computeIfAbsent(seed.definition.owner, ignored -> new ArrayDeque<>()).add(seed);
            }
            List<Seed> interleaved = new ArrayList<>();
            while (!owners.isEmpty()) {
                Iterator<Deque<Seed>> iterator = owners.values().iterator();
                while (iterator.hasNext()) {
                    Deque<Seed> queue = iterator.next();
                    interleaved.add(queue.removeFirst());
                    if (queue.isEmpty()) {
                        iterator.remove();
                    }
                }
            }
            indexed.setValue(interleaved);
        }
        for (ControllerlessMultiblockInstance instance : new ArrayList<>(instances.values())) {
            if (MultiblockRegistry.find(instance.definition.key) != instance.definition) {
                MultiblockDefinition replacement = MultiblockRegistry.find(instance.definition.key);
                MultiblockPlacement placement = restorePlacement(replacement, instance.saved);
                if (placement != null && placement.key.equals(instance.placement.key)
                        && compatible(instance.definition, replacement)) {
                    instance.save();
                    detach(instance);
                    attach(new ControllerlessMultiblockInstance(this, placement, instance.saved));
                } else {
                    instance.retire("definition_changed");
                }
            }
        }
        for (long key : chunks.keySet()) {
            queue(key);
        }
    }

    private boolean compatible(MultiblockDefinition first, MultiblockDefinition second) {
        MultiblockManagement left = first.management;
        MultiblockManagement right = second.management;
        return left != null && right != null && left.automatic == right.automatic && left.fillBlock == right.fillBlock
                && left.fillCells.equals(right.fillCells) && Objects.equals(left.facingState, right.facingState);
    }

    private MultiblockPlacement restorePlacement(MultiblockDefinition definition, NBTTagCompound saved) {
        if (definition == null || definition.management == null) {
            return null;
        }
        int facing = saved.getInteger("Facing");
        if (!definition.management.facings.contains(facing) || saved.getBoolean("Mirror") && !definition.mirror) {
            return null;
        }
        for (Variant variant : definition.variants) {
            if (variant.repeats == saved.getInteger("Repeats")) {
                return new MultiblockPlacement(definition,
                        new MultiblockPosition(saved.getInteger("X"), saved.getInteger("Y"), saved.getInteger("Z")),
                        facing, saved.getBoolean("Mirror"), variant);
            }
        }
        return null;
    }

    private void restore() {
        records();
        for (int count = 0; count < 32 && !restoring.isEmpty(); count++) {
            NBTTagCompound saved = restoring.removeFirst();
            try {
                MultiblockDefinition definition = MultiblockRegistry
                        .find(ContentKey.parseForType(saved.getString("Definition"), ContentType.of("multiblock")));
                MultiblockPlacement placement = restorePlacement(definition, saved);
                if (placement != null && placement.key.equals(saved.getString("Key"))
                        && !instances.containsKey(placement.key)) {
                    attach(new ControllerlessMultiblockInstance(this, placement, saved));
                } else if (placement == null || !placement.key.equals(saved.getString("Key"))) {
                    orphan(saved);
                }
            } catch (RuntimeException invalid) {
                orphan(saved);
            }
        }
    }

    private void orphan(NBTTagCompound saved) {
        orphaned.add(saved);
        for (Object value : saved.getCompoundTag("Owned").func_28110_c()) {
            if (value instanceof NBTTagCompound && !((NBTTagCompound) value).getBoolean("Released")) {
                MultiblockOwnedBlock output = new MultiblockOwnedBlock((NBTTagCompound) value);
                if (output.valid()) {
                    orphanOutputs.add(output.position);
                } else {
                    BetaMoonCommon.LOGGER
                            .warning("Retaining invalid multiblock cleanup record: " + saved.getString("Key"));
                }
            }
        }
    }

    private void attach(ControllerlessMultiblockInstance instance) {
        instances.put(instance.placement.key, instance);
        for (MultiblockPosition point : instance.placement.positions) {
            watchers.computeIfAbsent(point.chunk(), ignored -> new LinkedHashSet<>()).add(instance);
        }
    }

    private void detach(ControllerlessMultiblockInstance instance) {
        instances.remove(instance.placement.key);
        for (Set<ControllerlessMultiblockInstance> group : watchers.values()) {
            group.remove(instance);
        }
        watchers.values().removeIf(Set::isEmpty);
        instance.expired = true;
    }

    private void removeRecord(String key) {
        Iterator<?> iterator = records().func_28110_c().iterator();
        while (iterator.hasNext()) {
            if (((NBTBase) iterator.next()).getKey().equals(key)) {
                iterator.remove();
                break;
            }
        }
        markDirty();
    }

    MultiblockMatchJob.Reader reader(MultiblockPlacement placement) {
        ControllerlessMultiblockInstance instance = instances.get(placement.key);
        return instance == null || instance.expired ? this : instance.reader();
    }

    boolean revalidate(ControllerlessMultiblockInstance instance) {
        MultiblockMatchJob job = new MultiblockMatchJob(instance.definition, instance.reader(), instance.position.x,
                instance.position.y, instance.position.z, instance.facing, instance.mirror, false,
                instance.placement.variant.repeats);
        job.advance(65536);
        if (!job.done() || !job.report().status.equals("complete")) {
            return false;
        }
        instance.report = job.report();
        return true;
    }

    void tick(int budget) {
        restore();
        cleanupOrphans();
        if (!restoring.isEmpty()) {
            return;
        }
        List<ControllerlessMultiblockInstance> entries = scheduled();
        workRemaining = budget;
        inTick = true;
        try {
            if (!entries.isEmpty()) {
                for (int index = 0; index < entries.size() && workRemaining > budget / 2; index++) {
                    ControllerlessMultiblockInstance instance = entries.get(index);
                    validationAllowance = Math.min(256, workRemaining);
                    workRemaining -= instance.validate(validationAllowance);
                    validationAllowance = 0;
                    if (!instance.active && !instance.owned.isEmpty()) {
                        workRemaining -= instance.cleanup(Math.min(32, Math.max(0, workRemaining)));
                    }
                    if (!instance.expired && instance.isFormed() && instance.definition.management.automatic
                            && !instance.active && !instance.inhibited) {
                        instance.activate();
                    }
                    if (instance.expired && instance.owned.isEmpty()) {
                        detach(instance);
                        removeRecord(instance.placement.key);
                        for (MultiblockPosition point : instance.placement.positions) {
                            queue(point.chunk());
                        }
                    }
                }
                instanceTurn = (instanceTurn + 1) % entries.size();
            }
            while (workRemaining > 0 && !seeds.isEmpty()) {
                if (search != null) {
                    if (searchRevision != revision) {
                        startSearch(searchSeeds, searchPoint);
                        continue;
                    }
                    workRemaining -= search.advance(Math.min(256, workRemaining));
                    if (search.done()) {
                        for (Match match : search.matches()) {
                            register(match);
                        }
                        search = null;
                    }
                    continue;
                }
                if (!pendingSeeds.isEmpty()) {
                    MultiblockPosition point = pendingSeeds.iterator().next();
                    pendingSeeds.remove(point);
                    workRemaining--;
                    if (loaded(point.x, point.z)) {
                        List<Seed> selected = seeds.get(blockId(point.x, point.y, point.z));
                        if (selected != null) {
                            startSearch(selected, point);
                        }
                    }
                    continue;
                }
                if (scans.isEmpty()) {
                    break;
                }
                long key = scans.keySet().iterator().next();
                int cursor = scans.get(key);
                Chunk chunk = chunks.get(key);
                if (chunk == null) {
                    scans.remove(key);
                    continue;
                }
                int x = cursor >> 11;
                int z = cursor >> 7 & 15;
                int y = cursor & 127;
                int id = chunk.getBlockID(x, y, z);
                workRemaining--;
                cursor++;
                scans.remove(key);
                if (cursor < 32768) {
                    scans.put(key, cursor);
                } else if (rescan.remove(key)) {
                    scans.put(key, 0);
                }
                List<Seed> selected = seeds.get(id);
                if (selected != null) {
                    MultiblockPosition point = new MultiblockPosition(chunk.xPosition * 16 + x, y,
                            chunk.zPosition * 16 + z);
                    startSearch(selected, point);
                }
            }
            // Bounded reconciliation also catches third-party array writes without setter
            // notifications.
            if (++maintenance >= 20) {
                maintenance = 0;
                List<ControllerlessMultiblockInstance> stable = new ArrayList<>(instances.values());
                for (int index = 0; index < Math.min(16, stable.size()); index++) {
                    stable.get(Math.floorMod(maintenanceInstanceTurn++, stable.size())).invalidate();
                }
                if (!chunks.isEmpty()) {
                    List<Long> keys = new ArrayList<>(chunks.keySet());
                    queue(keys.get(Math.floorMod(chunkTurn++, keys.size())));
                }
            }
        } finally {
            inTick = false;
            validationAllowance = 0;
        }
    }

    private List<ControllerlessMultiblockInstance> scheduled() {
        Map<String, List<ControllerlessMultiblockInstance>> groups = new TreeMap<>();
        for (ControllerlessMultiblockInstance instance : instances.values()) {
            groups.computeIfAbsent(instance.definition.owner, ignored -> new ArrayList<>()).add(instance);
        }
        List<List<ControllerlessMultiblockInstance>> rotated = new ArrayList<>();
        for (Map.Entry<String, List<ControllerlessMultiblockInstance>> group : groups.entrySet()) {
            List<ControllerlessMultiblockInstance> values = group.getValue();
            int start = ownerTurns.getOrDefault(group.getKey(), 0) % values.size();
            List<ControllerlessMultiblockInstance> ordered = new ArrayList<>();
            for (int index = 0; index < values.size(); index++) {
                ordered.add(values.get((start + index) % values.size()));
            }
            ownerTurns.put(group.getKey(), start + 1);
            rotated.add(ordered);
        }
        List<ControllerlessMultiblockInstance> result = new ArrayList<>();
        for (int round = 0; result.size() < instances.size(); round++) {
            for (int owner = 0; owner < rotated.size(); owner++) {
                List<ControllerlessMultiblockInstance> values = rotated.get((instanceTurn + owner) % rotated.size());
                if (round < values.size()) {
                    result.add(values.get(round));
                }
            }
        }
        return result;
    }

    boolean reserveActivation(ControllerlessMultiblockInstance instance) {
        int work = instance.placement.variant.cells.size() * (8 + instance.definition.constraints.size() * 4);
        if (inTick) {
            if (workRemaining - validationAllowance < work) {
                return false;
            }
            workRemaining -= work;
        }
        return true;
    }

    private void startSearch(List<Seed> selected, MultiblockPosition point) {
        searchSeeds = selected;
        searchPoint = point;
        search = new MultiblockSearch(MultiblockSearch.near(selected, point, null, null), this::reader, 256);
        searchRevision = revision;
    }

    private void cleanupOrphans() {
        int remaining = 256;
        List<NBTTagCompound> records = new ArrayList<>(orphaned);
        if (records.isEmpty()) {
            return;
        }
        int start = Math.floorMod(orphanTurn++, records.size());
        for (int recordIndex = 0; recordIndex < records.size() && remaining > 0; recordIndex++) {
            NBTTagCompound saved = records.get((start + recordIndex) % records.size());
            String key = saved.getString("Key");
            List<NBTBase> blocks = new ArrayList<>();
            for (Object value : saved.getCompoundTag("Owned").func_28110_c()) {
                blocks.add((NBTBase) value);
            }
            int offset = blocks.isEmpty() ? 0 : orphanBlockTurns.getOrDefault(key, 0) % blocks.size();
            int checked = 0;
            for (; checked < blocks.size() && remaining > 0; checked++) {
                NBTBase value = blocks.get((offset + checked) % blocks.size());
                remaining--;
                if (!(value instanceof NBTTagCompound)) {
                    continue;
                }
                NBTTagCompound record = (NBTTagCompound) value;
                if (record.getBoolean("Released")) {
                    saved.getCompoundTag("Owned").func_28110_c().remove(record);
                    continue;
                }
                MultiblockOwnedBlock block = new MultiblockOwnedBlock(record);
                if (!block.valid() || block.originalId != 0 && Block.blocksList[block.originalId] == null
                        || !loaded(block.position.x, block.position.z)) {
                    continue;
                }
                if (blockId(block.position.x, block.position.y, block.position.z) == block.placedId
                        && metadata(block.position.x, block.position.y, block.position.z) == block.placedMetadata) {
                    try {
                        world.setBlockAndMetadataWithNotify(block.position.x, block.position.y, block.position.z,
                                block.originalId, block.originalMetadata);
                    } catch (RuntimeException failure) {
                        continue;
                    }
                }
                if (blockId(block.position.x, block.position.y, block.position.z) == block.placedId
                        && metadata(block.position.x, block.position.y, block.position.z) == block.placedMetadata) {
                    continue;
                }
                orphanOutputs.remove(block.position);
                saved.getCompoundTag("Owned").func_28110_c().remove(record);
                markDirty();
            }
            orphanBlockTurns.put(key, offset + checked);
            if (saved.getCompoundTag("Owned").func_28110_c().isEmpty()) {
                orphaned.remove(saved);
                orphanBlockTurns.remove(key);
                removeRecord(key);
            }
        }
    }

    private ControllerlessMultiblockInstance register(Match match) {
        ControllerlessMultiblockInstance existing = instances.get(match.placement.key);
        if (existing != null) {
            return existing;
        }
        for (MultiblockPosition point : match.placement.positions) {
            if (orphanOutputs.contains(point)) {
                return null;
            }
        }
        if (conflict(match.report, match.placement.definition.exclusive, null) != null
                || MultiblockRuntime.conflict(world, match.report, match.placement.definition.exclusive) != null) {
            return null;
        }
        int owner = MultiblockRuntime.ownerCount(world, match.placement.definition.owner);
        for (ControllerlessMultiblockInstance instance : instances.values()) {
            if (instance.definition.owner.equals(match.placement.definition.owner)) {
                owner++;
            }
        }
        if (instances.size() + MultiblockRuntime.instanceCount(world) >= MultiblockLimits.worldInstances()
                || owner >= MultiblockLimits.ownerInstances()) {
            return null;
        }
        ControllerlessMultiblockInstance instance = new ControllerlessMultiblockInstance(this, match.placement,
                new NBTTagCompound());
        attach(instance);
        instance.commit(match.report);
        return instance;
    }

    boolean conflicts(ControllerlessMultiblockInstance instance, MultiblockReport report) {
        MultiblockInstance conflict = conflict(report, instance.definition.exclusive, instance);
        if (conflict == null) {
            conflict = MultiblockRuntime.conflict(world, report, instance.definition.exclusive);
        }
        instance.conflict = conflict == null ? null : conflict.position;
        if (conflict != null) {
            report.status = "incomplete";
            report.reason = "exclusive_conflict";
        }
        return conflict != null;
    }

    MultiblockInstance conflict(MultiblockReport report, boolean exclusive, ControllerlessMultiblockInstance excluded) {
        for (ControllerlessMultiblockInstance instance : instances.values()) {
            if (instance == excluded || instance.expired) {
                continue;
            }
            for (MultiblockReport.Match match : report.matches) {
                MultiblockPosition point = new MultiblockPosition(match.x, match.y, match.z);
                if (instance.owned.containsKey(point)
                        || match.blockId != 0 && instance.formedHint && !instance.status.equals("suspended")
                                && (exclusive || instance.definition.exclusive) && instance.footprint.contains(point)) {
                    return instance;
                }
            }
        }
        return null;
    }

    boolean outputReserved(MultiblockPosition point, ControllerlessMultiblockInstance excluded) {
        return orphanOutputs.contains(point) || outputOwner(point, excluded) != null;
    }

    ControllerlessMultiblockInstance outputOwner(MultiblockPosition point, ControllerlessMultiblockInstance excluded) {
        for (ControllerlessMultiblockInstance instance : instances.values()) {
            if (instance != excluded && instance.owned.containsKey(point)) {
                return instance;
            }
        }
        return null;
    }

    List<MultiblockInstance> at(MultiblockPosition point, ContentKey filter) {
        List<MultiblockInstance> found = new ArrayList<>();
        Set<ControllerlessMultiblockInstance> candidates = watchers.get(point.chunk());
        if (candidates != null) {
            for (ControllerlessMultiblockInstance instance : candidates) {
                if (!instance.expired && instance.footprint.contains(point)
                        && (filter == null || instance.definition.key.equals(filter))) {
                    found.add(instance);
                }
            }
        }
        found.sort((left, right) -> left.identity.compareTo(right.identity));
        return found;
    }

    MultiblockActivationResult activate(MultiblockPlacement placement, EntityPlayer player, ItemStack stack,
            MultiblockPosition clicked, int face) {
        records();
        if (!restoring.isEmpty()) {
            restore();
            if (!restoring.isEmpty()) {
                return new MultiblockActivationResult(false, true, "checking", null);
            }
        }
        ControllerlessMultiblockInstance pending = instances.get(placement.key);
        if (pending != null && pending.expired && !pending.owned.isEmpty()) {
            return new MultiblockActivationResult(false, true, "cleanup_pending", null);
        }
        for (MultiblockPosition point : placement.positions) {
            if (orphanOutputs.contains(point)) {
                return new MultiblockActivationResult(false, true, "cleanup_pending", null);
            }
        }
        MultiblockManagement policy = placement.definition.management;
        if (policy == null || policy.automatic) {
            return new MultiblockActivationResult(false, false, "item_activation_required", null);
        }
        if (stack == null || stack.itemID != policy.item
                || policy.itemDamage >= 0 && stack.getItemDamage() != policy.itemDamage) {
            return new MultiblockActivationResult(false, false, "item_mismatch", null);
        }
        MultiblockPosition target = target(clicked, face, policy.adjacent);
        boolean accepted = false;
        for (int index = 0; index < placement.variant.cells.size(); index++) {
            if (policy.targets.contains(placement.variant.cells.get(index).symbol)
                    && placement.positions.get(index).equals(target)) {
                accepted = true;
                break;
            }
        }
        if (!accepted) {
            return new MultiblockActivationResult(false, false, "target_mismatch", null);
        }
        MultiblockMatchJob job = new MultiblockMatchJob(placement.definition, reader(placement), placement.anchor.x,
                placement.anchor.y, placement.anchor.z, placement.facing, placement.mirror, false,
                placement.variant.repeats);
        job.advance(65536);
        if (!job.done() || !job.report().status.equals("complete")) {
            return new MultiblockActivationResult(false, !job.done() || job.report().status.equals("unavailable"),
                    !job.done() ? "search_budget_exceeded" : job.report().reason, null);
        }
        ControllerlessMultiblockInstance instance = register(new Match(placement, job.report()));
        if (instance == null) {
            boolean conflict = conflict(job.report(), placement.definition.exclusive, null) != null
                    || MultiblockRuntime.conflict(world, job.report(), placement.definition.exclusive) != null;
            return new MultiblockActivationResult(false, true, conflict ? "exclusive_conflict" : "instance_limit",
                    null);
        }
        if (instance.expired) {
            return new MultiblockActivationResult(false, true, "cleanup_pending", null);
        }
        instance.commit(job.report());
        return instance.activate(player, stack, clicked, target, face);
    }

    MultiblockActivationResult use(EntityPlayer player, ItemStack stack, MultiblockPosition clicked, int face) {
        records();
        if (!restoring.isEmpty()) {
            restore();
            if (!restoring.isEmpty()) {
                return new MultiblockActivationResult(false, true, "checking", null);
            }
        }
        int budget = 65536;
        List<Match> matches = new ArrayList<>();
        for (MultiblockDefinition definition : MultiblockRegistry.all()) {
            MultiblockManagement policy = definition.management;
            if (policy == null || policy.manual || policy.automatic || policy.item != stack.itemID
                    || policy.itemDamage >= 0 && policy.itemDamage != stack.getItemDamage()) {
                continue;
            }
            MultiblockPosition target = target(clicked, face, policy.adjacent);
            Set<ControllerlessMultiblockInstance> pending = watchers.get(target.chunk());
            if (pending != null) {
                for (ControllerlessMultiblockInstance instance : pending) {
                    if (instance.expired && !instance.owned.isEmpty() && instance.definition.key.equals(definition.key)
                            && instance.footprint.contains(target)) {
                        return new MultiblockActivationResult(false, true, "cleanup_pending", null);
                    }
                }
            }
            MultiblockSearch query = new MultiblockSearch(MultiblockSearch.near(
                    MultiblockSearch.seeds(definition, policy.targets, policy.facings, definition.mirror, null), target,
                    null, null), this::reader, 64);
            budget -= query.advance(budget);
            if (!query.complete()) {
                return new MultiblockActivationResult(false, true, query.reason(), null);
            }
            matches.addAll(query.matches());
        }
        if (matches.size() != 1) {
            return new MultiblockActivationResult(false, !matches.isEmpty(),
                    matches.isEmpty() ? "no_match" : "ambiguous", null);
        }
        MultiblockPlacement placement = matches.get(0).placement;
        int activationWork = placement.variant.cells.size() * (8 + placement.definition.constraints.size() * 4);
        if (budget < activationWork) {
            return new MultiblockActivationResult(false, true, "search_budget_exceeded", null);
        }
        return activate(placement, player, stack, clicked, face);
    }

    private static MultiblockPosition target(MultiblockPosition clicked, int face, boolean adjacent) {
        if (!adjacent) {
            return clicked;
        }
        if (face < 0 || face > 5) {
            throw new IllegalArgumentException("Adjacent item activation requires a clicked block face");
        }
        int[] dx = {0, 0, 0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0, 0, 0};
        int[] dz = {0, 0, -1, 1, 0, 0};
        return new MultiblockPosition(clicked.x + dx[face], clicked.y + dy[face], clicked.z + dz[face]);
    }
}
