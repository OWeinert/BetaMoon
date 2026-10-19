package betamoon.teleport;

import betamoon.dimension.DimensionKey;
import betamoon.dimension.DimensionRuntime;
import betamoon.dimension.DimensionSession;
import betamoon.dimension.SavedDimensionInfo;
import betamoon.dimension.storage.DimensionSaveFlush;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagDouble;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.World;

/**
 * Bounded interrupted-platform reconciliation and exact saved-world
 * preparation, with no active player.
 */
public final class DimensionRestoreTask {
    private final DimensionSession session;
    private final SavedDimensionInfo saved;
    private World world;
    private ChunkPreparation chunks;
    private NBTTagCompound pending;
    private TeleportPlatformPlan rollback;
    private boolean rollbackPreparation;
    private int phase;
    private int ticks;
    private final List<Long> rollbackChunks = new ArrayList<Long>();
    private int rollbackCursor;
    private DimensionCleanupTask cleanup;

    public DimensionRestoreTask(DimensionSession session, SavedDimensionInfo saved) {
        this.session = session;
        this.saved = saved;
    }

    public boolean step() throws IOException {
        if (++ticks > 1200) {
            throw new IOException("timeout: saved dimension preparation exceeded its tick budget");
        }
        if (phase == 0) {
            String side = session.navigation.recoverySide(saved.player);
            if (side.equals("target")) {
                session.navigation.complete(session.navigation.intent().getString("Id"));
            } else if (side.equals("source")) {
                pending = session.navigation.intent();
            }
            NBTTagCompound preparation = session.navigation.preparation();
            NBTTagList cells = pending == null ? new NBTTagList() : pending.getTagList("Platform");
            DimensionKey affected = pending == null ? null : session.savedDimension(pending.getCompoundTag("Target"));
            if (preparation != null) {
                pending = preparation;
                rollbackPreparation = true;
                cells = preparation.getTagList("Cells");
                affected = DimensionKey.parse(preparation.getString("Dimension"));
            }
            if (cells.tagCount() != 0) {
                rollback = TeleportPlatformPlan.read(cells);
                world = DimensionRuntime.create(session, affected);
                chunks = new ChunkPreparation(world, 81);
                Set<Long> positions = new LinkedHashSet<Long>();
                for (int index = 0; index < cells.tagCount(); index++) {
                    NBTTagCompound cell = (NBTTagCompound) cells.tagAt(index);
                    positions.add(
                            ((long) (cell.getInteger("X") >> 4) << 32) ^ ((cell.getInteger("Z") >> 4) & 0xffffffffL));
                }
                rollbackChunks.addAll(positions);
                reserveRollbackChunk();
                phase = 1;
            } else {
                clearPending();
                phase = 2;
            }
            return false;
        }
        if (phase == 1) {
            if (!chunks.step()) {
                return false;
            }
            if (rollbackCursor < rollbackChunks.size()) {
                reserveRollbackChunk();
                return false;
            }
            rollback.rollback(world);
            DimensionSaveFlush.flush(world, session);
            clearPending();
            DimensionRuntime.releaseInactive(world);
            world = null;
            chunks = null;
            phase = 2;
            return false;
        }
        if (phase == 2) {
            world = DimensionRuntime.create(session, saved.key);
            DimensionRuntime.WorldBinding binding = DimensionRuntime.binding(world);
            if (binding != null && binding.save != null && !binding.save.metadata.initialized()) {
                throw new IOException("Saved player cannot reopen an uninitialized custom dimension");
            }
            chunks = new ChunkPreparation(world, session.navigation.hasCleanup(saved.key.toString()) ? 81 : 25);
            if (session.navigation.hasCleanup(saved.key.toString())) {
                cleanup = new DimensionCleanupTask(world, session, chunks);
                phase = 4;
                return false;
            }
            reserveSavedPosition();
            phase = 3;
            return false;
        }
        if (phase == 4) {
            if (!cleanup.step()) {
                return false;
            }
            reserveSavedPosition();
            phase = 3;
            return false;
        }
        return chunks.step();
    }

    private void reserveSavedPosition() {
        NBTTagList position = saved.player.getTagList("Pos");
        int x = (int) Math.floor(((NBTTagDouble) position.tagAt(0)).doubleValue);
        int z = (int) Math.floor(((NBTTagDouble) position.tagAt(2)).doubleValue);
        chunks.reserve(x >> 4, z >> 4, true);
    }

    private void reserveRollbackChunk() {
        long position = rollbackChunks.get(rollbackCursor++);
        chunks.reserve((int) (position >> 32), (int) position, true);
    }

    private void clearPending() throws IOException {
        if (pending != null) {
            if (rollbackPreparation) {
                session.navigation.clearPreparation(pending.getString("Id"));
            } else {
                session.navigation.abort(pending.getString("Id"));
            }
            pending = null;
        }
    }

    public World world() {
        return world;
    }

    public int acquiredChunks() {
        return chunks == null ? 0 : chunks.acquiredCount();
    }

    public int progressPercent() {
        return chunks == null ? -1 : chunks.progressPercent();
    }

    public String stage() {
        return phase < 2 ? "recovery" : "destination";
    }

    public void release() {
        if (world != null) {
            DimensionRuntime.releaseInactive(world);
        }
    }
}
