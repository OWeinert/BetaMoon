package betamoon.dimension;

import betamoon.dimension.storage.DimensionNbt;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.NativeFieldAccess;
import betamoon.teleport.TeleportJournal;
import betamoon.teleport.TeleportLocation;
import betamoon.teleport.TeleportOptions;
import betamoon.teleport.TeleportRuntime;
import betamoon.utils.WeakIdentityMap;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagDouble;
import net.minecraft.src.NBTTagFloat;
import net.minecraft.src.NBTTagList;

/**
 * Saved canonical identity/revisions and per-key beds; no portable Lua player
 * identity.
 */
public final class DimensionPlayerState {
    private static final WeakIdentityMap<EntityPlayer, State> PLAYERS = new WeakIdentityMap<EntityPlayer, State>();
    private static boolean synchronizingBed;
    private static final AtomicLong NEXT_TOKEN = new AtomicLong();
    private static final Field IN_PORTAL = NativeFieldAccess.field(EntityPlayer.class,
            new FieldRef(new ClassRef("net/minecraft/src/EntityPlayer"), "inPortal", "Z"));

    private DimensionPlayerState() {
    }

    public static int suppressPortalCharge(EntityPlayerSP player) {
        if (DimensionIdentity.isCustom(player.worldObj) || TeleportRuntime.busy()) {
            clearPortalCharge(player);
        }
        return 0;
    }

    public static void clearPortalCharge(EntityPlayer player) {
        try {
            IN_PORTAL.setBoolean(player, false);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot clear native portal charging state", exception);
        }
        player.timeInPortal = 0;
        player.prevTimeInPortal = 0;
    }

    public static synchronized void read(EntityPlayer player, NBTTagCompound tag) {
        read(player, tag, true);
    }

    public static synchronized void inheritRespawn(EntityPlayer player, NBTTagCompound tag) {
        read(player, tag, false);
    }

    private static void read(EntityPlayer player, NBTTagCompound tag, boolean restoreCursor) {
        DimensionRuntime.WorldBinding binding = DimensionRuntime.binding(player.worldObj);
        if (binding == null) {
            return;
        }
        try {
            TeleportJournal journal = binding.session.navigation;
            if (tag.hasKey("BetaMoonPlayer") && !journal.playerId().equals(tag.getString("BetaMoonPlayer"))) {
                throw new IOException("Saved player UUID does not belong to this root session");
            }
            State state = new State();
            State previous = PLAYERS.get(player);
            if (previous != null) {
                state.runtimeToken = previous.runtimeToken;
            }
            state.revision = tag.hasKey("BetaMoonRevision") ? tag.getLong("BetaMoonRevision") : journal.revision();
            state.transfer = tag.getString("BetaMoonTransfer");
            state.beds = tag.hasKey("BetaMoonBeds")
                    ? DimensionNbt.copy(tag.getCompoundTag("BetaMoonBeds"), 65536)
                    : new NBTTagCompound();
            if (!tag.hasKey("BetaMoonBeds") && tag.hasKey("SpawnX") && tag.hasKey("SpawnY") && tag.hasKey("SpawnZ")) {
                state.beds.setCompoundTag(DimensionKey.OVERWORLD.toString(),
                        bed(tag.getInteger("SpawnX"), tag.getInteger("SpawnY"), tag.getInteger("SpawnZ")));
            }
            if (state.beds.func_28110_c().size() > 256) {
                throw new IOException("Saved beds exceed their dimension budget");
            }
            for (Object value : state.beds.func_28110_c()) {
                NBTBase entry = (NBTBase) value;
                DimensionKey.parse(entry.getKey());
                if (!(entry instanceof NBTTagCompound)) {
                    throw new IOException("Invalid per-dimension bed entry");
                }
                validateBed((NBTTagCompound) entry);
            }
            PLAYERS.put(player, state);
            if (restoreCursor) {
                ItemStack cursor = tag.hasKey("BetaMoonCursor")
                        ? new ItemStack(tag.getCompoundTag("BetaMoonCursor"))
                        : null;
                if (cursor != null && (cursor.itemID < 0 || cursor.itemID >= Item.itemsList.length
                        || Item.itemsList[cursor.itemID] == null || cursor.stackSize < 1 || cursor.stackSize > 127)) {
                    throw new IOException("Invalid saved player cursor stack");
                }
                player.inventory.setItemStack(cursor);
            }
            synchronizeBed(player, state);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("recovery_required: cannot read dimension player extensions", exception);
        }
    }

    public static synchronized void write(EntityPlayer player, NBTTagCompound tag) {
        DimensionRuntime.WorldBinding binding = DimensionRuntime.binding(player.worldObj);
        if (binding == null) {
            return;
        }
        try {
            State state = state(player, binding.session.navigation);
            binding.session.navigation.ensureSaved();
            binding.session.navigation.stamp(tag, state.revision, state.transfer);
            tag.setString("BetaMoonDimension", DimensionIdentity.key(player.worldObj).toString());
            tag.setInteger("Dimension", player.worldObj.worldProvider.worldType);
            tag.setCompoundTag("BetaMoonBeds", DimensionNbt.copy(state.beds, 65536));
            ItemStack cursor = player.inventory.getItemStack();
            if (cursor != null) {
                tag.setCompoundTag("BetaMoonCursor", cursor.writeToNBT(new NBTTagCompound()));
            } else {
                DimensionNbt.remove(tag, "BetaMoonCursor");
            }
            applyBed(tag, state.beds, DimensionIdentity.key(player.worldObj));
        } catch (IOException exception) {
            throw new IllegalStateException("save_failed: cannot save dimension player extensions", exception);
        }
    }

    public static synchronized void bedChanged(EntityPlayer player, ChunkCoordinates position) {
        if (synchronizingBed) {
            return;
        }
        DimensionRuntime.WorldBinding binding = DimensionRuntime.binding(player.worldObj);
        if (binding == null) {
            return;
        }
        State state = state(player, binding.session.navigation);
        DimensionKey key = DimensionIdentity.key(player.worldObj);
        DimensionEnvironment environment = DimensionRules.environment(player.worldObj.worldProvider);
        if (environment != null && !environment.beds.equals("sleep")) {
            synchronizeBed(player, state);
            return;
        }
        if (position == null) {
            DimensionNbt.remove(state.beds, key.toString());
        } else {
            if (!state.beds.hasKey(key.toString()) && state.beds.func_28110_c().size() >= 256) {
                synchronizeBed(player, state);
                throw new IllegalStateException("Per-player dimension bed budget exceeded");
            }
            NBTTagCompound bed = bed(position.x, position.y, position.z);
            try {
                validateBed(bed);
            } catch (IOException exception) {
                synchronizeBed(player, state);
                throw new IllegalArgumentException("Invalid bed coordinates", exception);
            }
            state.beds.setCompoundTag(key.toString(), bed);
        }
    }

    public static NBTTagCompound target(EntityPlayer player, DimensionSession session, NBTTagCompound source,
            TeleportLocation destination, int numericId, TeleportOptions options, String transfer) throws IOException {
        NBTTagCompound target = DimensionNbt.copy(source, TeleportJournal.PLAYER_BYTES);
        target.setString("BetaMoonDimension", destination.dimension.toString());
        target.setInteger("Dimension", numericId);
        target.setTag("Pos", doubles(destination.x, destination.y + player.yOffset, destination.z));
        NBTTagList rotation = new NBTTagList();
        rotation.setTag(new NBTTagFloat(destination.yaw));
        rotation.setTag(new NBTTagFloat(destination.pitch));
        target.setTag("Rotation", rotation);
        if (!options.velocity.equals("preserve")) {
            target.setTag("Motion", doubles(options.motionX, options.motionY, options.motionZ));
        }
        if (!options.preserveFallDistance) {
            target.setFloat("FallDistance", 0);
        }
        applyBed(target, target.getCompoundTag("BetaMoonBeds"), destination.dimension);
        session.navigation.stamp(target, session.navigation.revision() + 1, transfer);
        return target;
    }

    public static synchronized ChunkCoordinates bed(EntityPlayer player, DimensionKey key) {
        State state = PLAYERS.get(player);
        if (state == null || !state.beds.hasKey(key.toString())) {
            return null;
        }
        NBTTagCompound value = state.beds.getCompoundTag(key.toString());
        return new ChunkCoordinates(value.getInteger("X"), value.getInteger("Y"), value.getInteger("Z"));
    }

    public static synchronized long runtimeToken(EntityPlayer player, DimensionSession session) {
        return state(player, session.navigation).runtimeToken;
    }

    private static State state(EntityPlayer player, TeleportJournal journal) {
        State state = PLAYERS.get(player);
        if (state == null) {
            state = new State();
            state.revision = journal.revision();
            ChunkCoordinates legacy = player.getPlayerSpawnCoordinate();
            if (legacy != null) {
                state.beds.setCompoundTag(DimensionKey.OVERWORLD.toString(), bed(legacy.x, legacy.y, legacy.z));
            }
            PLAYERS.put(player, state);
        }
        return state;
    }

    private static void synchronizeBed(EntityPlayer player, State state) {
        DimensionKey key = DimensionIdentity.key(player.worldObj);
        NBTTagCompound value = state.beds.getCompoundTag(key.toString());
        DimensionEnvironment environment = DimensionRules.environment(player.worldObj.worldProvider);
        boolean allowed = environment == null || environment.beds.equals("sleep");
        synchronizingBed = true;
        try {
            player.setPlayerSpawnCoordinate(allowed && state.beds.hasKey(key.toString())
                    ? new ChunkCoordinates(value.getInteger("X"), value.getInteger("Y"), value.getInteger("Z"))
                    : null);
        } finally {
            synchronizingBed = false;
        }
    }

    private static void applyBed(NBTTagCompound player, NBTTagCompound beds, DimensionKey key) {
        DimensionNbt.remove(player, "SpawnX");
        DimensionNbt.remove(player, "SpawnY");
        DimensionNbt.remove(player, "SpawnZ");
        if (beds.hasKey(key.toString())) {
            NBTTagCompound bed = beds.getCompoundTag(key.toString());
            player.setInteger("SpawnX", bed.getInteger("X"));
            player.setInteger("SpawnY", bed.getInteger("Y"));
            player.setInteger("SpawnZ", bed.getInteger("Z"));
        }
    }

    private static NBTTagCompound bed(int x, int y, int z) {
        NBTTagCompound value = new NBTTagCompound();
        value.setInteger("X", x);
        value.setInteger("Y", y);
        value.setInteger("Z", z);
        return value;
    }

    private static void validateBed(NBTTagCompound bed) throws IOException {
        if (Math.abs((long) bed.getInteger("X")) >= 30000000 || Math.abs((long) bed.getInteger("Z")) >= 30000000
                || bed.getInteger("Y") < 0 || bed.getInteger("Y") > 127) {
            throw new IOException("Invalid saved bed coordinates");
        }
    }

    private static NBTTagList doubles(double x, double y, double z) {
        NBTTagList list = new NBTTagList();
        list.setTag(new NBTTagDouble(x));
        list.setTag(new NBTTagDouble(y));
        list.setTag(new NBTTagDouble(z));
        return list;
    }

    private static final class State {
        private long runtimeToken = NEXT_TOKEN.incrementAndGet();
        private long revision;
        private String transfer = "";
        private NBTTagCompound beds = new NBTTagCompound();
    }
}
