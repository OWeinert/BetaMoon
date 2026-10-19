package betamoon.multiblock;

import betamoon.content.ContentKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.Chunk;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

/**
 * Public integration boundary; world state and search policy live in
 * domain-specific classes.
 */
public final class ControllerlessMultiblockRuntime {
    private static final Map<World, ControllerlessMultiblockWorld> WORLDS = new WeakHashMap<>();

    private ControllerlessMultiblockRuntime() {
    }

    static ControllerlessMultiblockWorld runtime(World world) {
        return WORLDS.computeIfAbsent(world, ControllerlessMultiblockWorld::new);
    }

    static boolean current(World world, ControllerlessMultiblockWorld runtime) {
        return WORLDS.get(world) == runtime;
    }

    public static void loaded(Chunk chunk) {
        if (!chunk.worldObj.multiplayerWorld) {
            runtime(chunk.worldObj).loaded(chunk);
        }
    }

    public static void unloaded(Chunk chunk) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(chunk.worldObj);
        if (runtime != null) {
            runtime.unloaded(chunk);
        }
    }

    public static void changing(Chunk chunk, int x, int y, int z) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(chunk.worldObj);
        if (runtime != null) {
            runtime.changing(new MultiblockPosition(chunk.xPosition * 16 + x, y, chunk.zPosition * 16 + z));
        }
    }

    public static void changed(Chunk chunk, int x, int y, int z) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(chunk.worldObj);
        if (runtime != null) {
            runtime.changed(chunk, x, y, z);
        }
    }

    public static void reconcile(Chunk chunk) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(chunk.worldObj);
        if (runtime != null) {
            runtime.reconcile(chunk);
        }
    }

    public static void definitionsChanged() {
        for (ControllerlessMultiblockWorld runtime : new ArrayList<>(WORLDS.values())) {
            runtime.refresh();
        }
    }

    public static void tick(World world, int budget) {
        runtime(world).tick(budget);
    }

    public static void unload(World world) {
        ControllerlessMultiblockWorld runtime = WORLDS.remove(world);
        if (runtime != null) {
            for (ControllerlessMultiblockInstance instance : runtime.instances.values()) {
                instance.save();
                instance.dirty = true;
                instance.expired = true;
            }
        }
    }

    public static void flush(World world) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(world);
        if (runtime != null) {
            for (ControllerlessMultiblockInstance instance : runtime.instances.values()) {
                instance.save();
            }
        }
    }

    public static List<MultiblockInstance> at(World world, int x, int y, int z, ContentKey filter) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(world);
        return runtime == null ? Collections.emptyList() : runtime.at(new MultiblockPosition(x, y, z), filter);
    }

    public static MultiblockSearch.ReaderFactory readers(World world) {
        return placement -> placement.definition.management == null
                ? MultiblockRuntime.reader(world)
                : runtime(world).reader(placement);
    }

    public static int instanceCount(World world) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(world);
        return runtime == null ? 0 : runtime.instances.size();
    }

    public static int ownerCount(World world, String owner) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(world);
        int count = 0;
        if (runtime != null) {
            for (ControllerlessMultiblockInstance instance : runtime.instances.values()) {
                if (instance.definition.owner.equals(owner)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static MultiblockMatchJob.Reader checkReader(World world, MultiblockDefinition definition, int x, int y,
            int z, int facing, boolean mirror, Integer repeats) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(world);
        if (runtime != null && definition.management != null && definition.management.facings.contains(facing)) {
            for (MultiblockDefinition.Variant variant : definition.variants) {
                if (repeats == null || repeats == variant.repeats) {
                    MultiblockPlacement placement = new MultiblockPlacement(definition, new MultiblockPosition(x, y, z),
                            facing, mirror, variant);
                    ControllerlessMultiblockInstance instance = runtime.instances.get(placement.key);
                    if (instance != null && !instance.expired) {
                        return instance.reader();
                    }
                }
            }
        }
        return MultiblockRuntime.reader(world);
    }

    public static MultiblockInstance conflict(World world, MultiblockReport report, boolean exclusive) {
        ControllerlessMultiblockWorld runtime = WORLDS.get(world);
        return runtime == null ? null : runtime.conflict(report, exclusive, null);
    }

    public static MultiblockActivationResult activate(World world, MultiblockPlacement placement, EntityPlayer player,
            ItemStack stack, MultiblockPosition clicked, int face) {
        if (world.multiplayerWorld || player == null || player.worldObj != world || player.isDead
                || player.health <= 0) {
            return new MultiblockActivationResult(false, false, "player_unavailable", null);
        }
        return runtime(world).activate(placement, player, stack, clicked, face);
    }

    public static MultiblockActivationResult use(World world, EntityPlayer player, ItemStack stack, int x, int y, int z,
            int face) {
        if (world.multiplayerWorld || player == null || stack == null) {
            return new MultiblockActivationResult(false, false, "no_match", null);
        }
        boolean registered = false;
        for (MultiblockDefinition definition : MultiblockRegistry.all()) {
            MultiblockManagement policy = definition.management;
            registered |= policy != null && !policy.manual && !policy.automatic && policy.item == stack.itemID;
        }
        return registered
                ? runtime(world).use(player, stack, new MultiblockPosition(x, y, z), face)
                : new MultiblockActivationResult(false, false, "no_match", null);
    }
}
