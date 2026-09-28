package betamoon.worldgen;

import betamoon.system.BetaMoonWorldData;
import betamoon.system.WorldServiceRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/** Per-world saved index of regional starts and completed chunk slices. */
final class RegionalStructureIndex {
    private static final Map<World, RegionalStructureIndex> WORLDS =
            new WeakHashMap<World, RegionalStructureIndex>();

    private final BetaMoonWorldData storage;
    private final NBTTagCompound savedStarts;
    private final NBTTagCompound savedRejected;
    private final Map<String, RegionalStructurePlan> starts = new LinkedHashMap<String, RegionalStructurePlan>();
    private final Set<String> rejectedStarts = new LinkedHashSet<String>();
    private final Map<Long, List<RegionalStructurePlan>> byChunk =
            new LinkedHashMap<Long, List<RegionalStructurePlan>>();

    private RegionalStructureIndex(BetaMoonWorldData storage) {
        this.storage = storage;
        savedStarts = storage.structures().hasKey("Starts")
                ? storage.structures().getCompoundTag("Starts") : new NBTTagCompound();
        storage.structures().setCompoundTag("Starts", savedStarts);
        savedRejected = storage.structures().hasKey("Rejected")
                ? storage.structures().getCompoundTag("Rejected") : new NBTTagCompound();
        storage.structures().setCompoundTag("Rejected", savedRejected);
        int loaded = 0;
        for (Object value : savedStarts.func_28110_c()) {
            if (!(value instanceof NBTTagCompound) || loaded++ >= 65536) {
                continue;
            }
            RegionalStructurePlan plan = RegionalStructurePlan.read((NBTTagCompound) value);
            if (plan != null) {
                starts.put(plan.id(), plan);
                index(plan);
            }
        }
        loaded = 0;
        for (Object value : savedRejected.func_28110_c()) {
            if (value instanceof NBTBase && loaded++ < 65536) {
                rejectedStarts.add(((NBTBase) value).getKey());
            }
        }
    }

    static synchronized RegionalStructureIndex get(World world) {
        if (world == null || world.multiplayerWorld) {
            return null;
        }
        RegionalStructureIndex result = WORLDS.get(world);
        if (result != null) {
            return result;
        }
        BetaMoonWorldData storage = WorldServiceRuntime.storage(world);
        if (storage == null) {
            return null;
        }
        result = new RegionalStructureIndex(storage);
        WORLDS.put(world, result);
        return result;
    }

    static synchronized void clearRuntime() {
        WORLDS.clear();
    }

    synchronized RegionalStructurePlan find(String id) {
        return starts.get(id);
    }

    synchronized boolean contains(String id) {
        return starts.containsKey(id) || rejectedStarts.contains(id);
    }

    synchronized void add(RegionalStructurePlan plan) {
        starts.put(plan.id(), plan);
        index(plan);
        save(plan);
    }

    synchronized void changed(RegionalStructurePlan plan) {
        save(plan);
    }

    synchronized void reject(String id) {
        rejectedStarts.add(id);
        savedRejected.setCompoundTag(id, new NBTTagCompound());
        storage.markDirty();
    }

    synchronized List<RegionalStructurePlan> all() {
        return new ArrayList<RegionalStructurePlan>(starts.values());
    }

    synchronized List<RegionalStructurePlan> forChunk(int chunkX, int chunkZ) {
        List<RegionalStructurePlan> values = byChunk.get(chunkKey(chunkX, chunkZ));
        return values == null ? java.util.Collections.<RegionalStructurePlan>emptyList()
                : new ArrayList<RegionalStructurePlan>(values);
    }

    private void index(RegionalStructurePlan plan) {
        java.util.Set<Long> occupied = new java.util.LinkedHashSet<Long>();
        for (RegionalStructurePlan.Piece piece : plan.pieces) {
            int minX = Math.floorDiv(piece.bounds.min.x, 16);
            int maxX = Math.floorDiv(piece.bounds.max.x, 16);
            int minZ = Math.floorDiv(piece.bounds.min.z, 16);
            int maxZ = Math.floorDiv(piece.bounds.max.z, 16);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    occupied.add(chunkKey(x, z));
                }
            }
        }
        for (Long chunk : occupied) {
            List<RegionalStructurePlan> values = byChunk.get(chunk);
            if (values == null) {
                values = new ArrayList<RegionalStructurePlan>();
                byChunk.put(chunk, values);
            }
            values.add(plan);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private void save(RegionalStructurePlan plan) {
        savedStarts.setCompoundTag(plan.id(), plan.write());
        storage.markDirty();
    }
}
