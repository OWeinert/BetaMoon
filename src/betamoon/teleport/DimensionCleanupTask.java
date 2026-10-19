package betamoon.teleport;

import betamoon.dimension.DimensionIdentity;
import betamoon.dimension.DimensionSession;
import betamoon.dimension.storage.DimensionSaveFlush;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.World;

/**
 * Retained platform undo records are resolved in their own dimension before it
 * can become active.
 */
public final class DimensionCleanupTask {
    private final World world;
    private final DimensionSession session;
    private final ChunkPreparation chunks;
    private final List<NBTTagCompound> records;
    private final List<int[]> centers = new ArrayList<int[]>();
    private int recordIndex;
    private int centerIndex;
    private TeleportPlatformPlan plan;

    public DimensionCleanupTask(World world, DimensionSession session, ChunkPreparation chunks) throws IOException {
        this.world = world;
        this.session = session;
        this.chunks = chunks;
        records = session.navigation.cleanupFor(DimensionIdentity.key(world).toString());
    }

    public boolean step() throws IOException {
        if (recordIndex == records.size()) {
            return true;
        }
        NBTTagCompound record = records.get(recordIndex);
        if (plan == null) {
            NBTTagList cells = record.getTagList("Cells");
            plan = TeleportPlatformPlan.read(cells);
            centers.clear();
            for (int index = 0; index < cells.tagCount(); index++) {
                NBTTagCompound cell = (NBTTagCompound) cells.tagAt(index);
                int x = cell.getInteger("X") >> 4;
                int z = cell.getInteger("Z") >> 4;
                boolean covered = false;
                for (int[] center : centers) {
                    covered |= Math.abs(center[0] - x) <= 1 && Math.abs(center[1] - z) <= 1;
                }
                if (!covered) {
                    centers.add(new int[]{x, z});
                }
            }
            centerIndex = 0;
            if (!centers.isEmpty()) {
                reserveNext();
                return false;
            }
        }
        if (!centers.isEmpty() && !chunks.step()) {
            return false;
        }
        if (centerIndex < centers.size()) {
            reserveNext();
            return false;
        }
        plan.rollback(world);
        DimensionSaveFlush.flush(world, session);
        session.navigation.clearCleanup(record.getString("Id"));
        plan = null;
        recordIndex++;
        return recordIndex == records.size();
    }

    private void reserveNext() {
        int[] center = centers.get(centerIndex++);
        chunks.reserve(center[0], center[1], true);
    }
}
