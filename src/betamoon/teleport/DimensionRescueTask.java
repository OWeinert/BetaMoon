package betamoon.teleport;

import betamoon.dimension.DimensionKey;
import betamoon.dimension.DimensionPlayerState;
import betamoon.dimension.DimensionRuntime;
import betamoon.dimension.DimensionSession;
import betamoon.dimension.SavedDimensionInfo;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.dimension.storage.DimensionSaveFlush;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagDouble;
import net.minecraft.src.NBTTagFloat;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.World;

/**
 * Explicit evacuation to the real Overworld's bed/spawn, only after a completed
 * full save backup.
 */
public final class DimensionRescueTask {
    private final DimensionSession session;
    private final SavedDimensionInfo saved;
    private final Function<World, EntityPlayer> players;
    private final String transfer = UUID.randomUUID().toString();
    private final TeleportOptions options;
    private World world;
    private EntityPlayer player;
    private ChunkPreparation chunks;
    private ChunkCoordinates bed;
    private ArrivalSearch search;
    private boolean bedArrival;
    private TeleportLocation arrival;
    private NBTTagCompound target;
    private int phase;
    private int ticks;
    private DimensionCleanupTask cleanup;

    public DimensionRescueTask(DimensionSession session, SavedDimensionInfo saved,
            Function<World, EntityPlayer> players) {
        this.session = session;
        this.saved = saved;
        this.players = players;
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("maxChunks", 81);
        values.put("verticalRange", 127);
        values.put("timeoutTicks", 1200);
        values.put("rememberReturn", false);
        options = new TeleportOptions(values, false);
    }

    public boolean step() throws IOException {
        if (++ticks > 1200) {
            throw new IOException("timeout: rescue preparation exceeded its tick budget");
        }
        if (phase == 0) {
            session.prepareRescue(saved.player);
            world = DimensionRuntime.create(session, DimensionKey.OVERWORLD);
            player = players.apply(world);
            NBTTagCompound temporary = DimensionNbt.copy(saved.player, TeleportJournal.PLAYER_BYTES);
            temporary.setString("BetaMoonDimension", DimensionKey.OVERWORLD.toString());
            temporary.setInteger("Dimension", 0);
            player.readFromNBT(temporary);
            bed = DimensionPlayerState.bed(player, DimensionKey.OVERWORLD);
            chunks = new ChunkPreparation(world, 81);
            if (session.navigation.hasCleanup(DimensionKey.OVERWORLD.toString())) {
                cleanup = new DimensionCleanupTask(world, session, chunks);
                phase = 3;
                return false;
            }
            reserveSpawn();
            phase = 1;
            return false;
        }
        if (phase == 3) {
            if (!cleanup.step()) {
                return false;
            }
            reserveSpawn();
            phase = 1;
            return false;
        }
        if (phase == 1) {
            if (!chunks.step()) {
                return false;
            }
            ChunkCoordinates valid = bed == null ? null : EntityPlayer.func_25060_a(world, bed);
            bedArrival = valid != null;
            TeleportLocation preferred = valid == null
                    ? spawn()
                    : new TeleportLocation(DimensionKey.OVERWORLD, valid.x + 0.5, valid.y + 0.1, valid.z + 0.5, 0, 0);
            search = new ArrivalSearch(world, options, preferred, false, options.searchRadius, chunks);
            phase = 2;
            return false;
        }
        if (!search.step(player)) {
            return false;
        }
        arrival = search.arrival();
        if (arrival == null && bedArrival) {
            bedArrival = false;
            search = new ArrivalSearch(world, options, spawn(), false, options.searchRadius, chunks);
            return false;
        }
        if (arrival == null) {
            throw new IOException("Rescue found no safe Overworld arrival: " + search.failure());
        }
        return true;
    }

    private void reserveSpawn() {
        ChunkCoordinates point = bed == null ? world.getSpawnPoint() : bed;
        chunks.reserve(point.x >> 4, point.z >> 4, true);
    }

    private TeleportLocation spawn() {
        ChunkCoordinates point = world.getSpawnPoint();
        return new TeleportLocation(DimensionKey.OVERWORLD, point.x + 0.5, point.y, point.z + 0.5, 0, 0);
    }

    public void beginCommit() throws IOException {
        DimensionSaveFlush.flush(world, session);
        NBTTagCompound source = DimensionNbt.copy(saved.player, TeleportJournal.PLAYER_BYTES);
        session.navigation.stamp(source, session.navigation.revision(), source.getString("BetaMoonTransfer"));
        session.savePlayerSnapshot(source);
        NBTTagList position = source.getTagList("Pos");
        NBTTagList rotation = source.getTagList("Rotation");
        TeleportLocation departure = new TeleportLocation(saved.key, ((NBTTagDouble) position.tagAt(0)).doubleValue,
                ((NBTTagDouble) position.tagAt(1)).doubleValue - player.yOffset,
                ((NBTTagDouble) position.tagAt(2)).doubleValue, ((NBTTagFloat) rotation.tagAt(0)).floatValue,
                ((NBTTagFloat) rotation.tagAt(1)).floatValue);
        target = DimensionPlayerState.target(player, session, source, arrival, 0, options, transfer);
        session.navigation.begin(transfer, "betamoon:rescue", "default", "none", null, departure, source, target,
                new NBTTagList());
    }

    public void complete() throws IOException {
        session.savePlayerSnapshot(target);
        session.navigation.complete(transfer);
    }

    public World world() {
        return world;
    }

    public EntityPlayer player() {
        return player;
    }

    public NBTTagCompound target() {
        return target;
    }

    public void release() {
        if (world != null) {
            DimensionRuntime.releaseInactive(world);
        }
    }
}
