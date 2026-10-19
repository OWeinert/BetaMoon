package betamoon.teleport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;

/**
 * Deterministic, sliced search over actual support surfaces, including
 * fractional custom block shapes.
 */
public final class ArrivalSearch {
    private static final int CHECKS_PER_TICK = 2048;
    private static final int MAX_CHECKS = 131072;
    private final World world;
    private final TeleportOptions options;
    private final TeleportLocation preferred;
    private final boolean exact;
    private final List<Column> columns = new ArrayList<Column>();
    private final Set<Long> prepared = new HashSet<Long>();
    private final ChunkPreparation chunks;
    private int columnIndex;
    private List<Double> heights;
    private int heightIndex;
    private boolean preparing;
    private long preparingKey;
    private int checks;
    private boolean missingChunks;
    private boolean done;
    private TeleportLocation arrival;
    private String failure;

    public ArrivalSearch(World world, TeleportOptions options, TeleportLocation preferred, boolean exact) {
        this(world, options, preferred, exact, options.searchRadius,
                options.loadChunks ? new ChunkPreparation(world, options.maxChunks) : null);
    }

    public ArrivalSearch(World world, TeleportOptions options, TeleportLocation preferred, boolean exact, int radius,
            ChunkPreparation chunks) {
        this.world = world;
        this.options = options;
        this.preferred = preferred;
        this.exact = exact;
        this.chunks = chunks;
        columns.add(new Column(preferred.x, preferred.z));
        if (!exact) {
            int baseX = floor(preferred.x);
            int baseZ = floor(preferred.z);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double x = baseX + dx + 0.5;
                    double z = baseZ + dz + 0.5;
                    double distance = square(x - preferred.x) + square(z - preferred.z);
                    if (distance <= radius * radius && distance != 0 && Math.abs(x) < 30000000
                            && Math.abs(z) < 30000000) {
                        columns.add(new Column(x, z));
                    }
                }
            }
            columns.sort(Comparator.comparingDouble((Column column) -> distance(column))
                    .thenComparingDouble(column -> column.x).thenComparingDouble(column -> column.z));
        }
    }

    public boolean step(EntityPlayer player) {
        if (done) {
            return true;
        }
        try {
            if (preparing) {
                if (chunks.step()) {
                    prepared.add(preparingKey);
                    preparing = false;
                }
                return false;
            }
            int remaining = CHECKS_PER_TICK;
            while (columnIndex < columns.size() && remaining > 0) {
                Column column = columns.get(columnIndex);
                int chunkX = floor(column.x) >> 4;
                int chunkZ = floor(column.z) >> 4;
                long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
                if (chunks != null && !prepared.contains(key)) {
                    chunks.reserve(chunkX, chunkZ, true);
                    preparingKey = key;
                    preparing = true;
                    return false;
                }
                if (heights == null) {
                    AxisAlignedBB area = AxisAlignedBB.getBoundingBoxFromPool(column.x - player.width / 2.0, 0,
                            column.z - player.width / 2.0, column.x + player.width / 2.0, 128,
                            column.z + player.width / 2.0);
                    if (!ArrivalInspector.loaded(world, area)) {
                        missingChunks = true;
                        columnIndex++;
                        continue;
                    }
                    heights = heights(column, player);
                    heightIndex = 0;
                    remaining--;
                }
                while (heightIndex < heights.size() && remaining-- > 0) {
                    if (++checks > MAX_CHECKS) {
                        return finish("budget_exhausted");
                    }
                    TeleportLocation position = new TeleportLocation(preferred.dimension, column.x,
                            heights.get(heightIndex++), column.z, preferred.yaw, preferred.pitch);
                    String rejection = ArrivalInspector.inspect(world, player, position, options.safety);
                    if (rejection == null) {
                        arrival = position;
                        done = true;
                        return true;
                    }
                    if (exact) {
                        return finish(rejection);
                    }
                    missingChunks |= rejection.equals("chunks_unavailable");
                }
                if (heightIndex >= heights.size()) {
                    columnIndex++;
                    heights = null;
                }
            }
            return columnIndex >= columns.size()
                    && finish(checks == 0 && missingChunks ? "chunks_unavailable" : "unsafe_destination");
        } catch (ChunkPreparation.WorkFailure exhausted) {
            if (exhausted.populationChanged) {
                throw exhausted;
            }
            return finish(exhausted.reason);
        }
    }

    private List<Double> heights(Column column, EntityPlayer player) {
        List<Double> result = new ArrayList<Double>();
        result.add(preferred.y);
        if (exact) {
            return result;
        }
        Comparator<Double> order = Comparator.comparingDouble((Double height) -> Math.abs(height - preferred.y))
                .thenComparingDouble(height -> height);
        TreeSet<Double> surfaces = new TreeSet<Double>(order);
        double half = player.width / 2.0;
        AxisAlignedBB probe = AxisAlignedBB.getBoundingBoxFromPool(column.x - half, 0, column.z - half, column.x + half,
                128, column.z + half);
        int minY = Math.max(0, floor(preferred.y - options.verticalRange) - 1);
        int maxY = Math.min(127, (int) Math.ceil(preferred.y + options.verticalRange));
        ArrayList<AxisAlignedBB> collisions = new ArrayList<AxisAlignedBB>();
        for (int x = floor(probe.minX); x <= floor(probe.maxX); x++) {
            for (int z = floor(probe.minZ); z <= floor(probe.maxZ); z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = Block.blocksList[world.getBlockId(x, y, z)];
                    if (block == null) {
                        continue;
                    }
                    collisions.clear();
                    block.getCollidingBoundingBoxes(world, x, y, z, probe, collisions);
                    for (AxisAlignedBB shape : collisions) {
                        double height = shape.maxY;
                        if (height >= 0 && height + player.height <= 128
                                && Math.abs(height - preferred.y) <= options.verticalRange && height != preferred.y) {
                            surfaces.add(height);
                            if (surfaces.size() > 512) {
                                surfaces.pollLast();
                            }
                        }
                    }
                }
            }
        }
        result.addAll(surfaces);
        return result;
    }

    private boolean finish(String reason) {
        done = true;
        failure = reason;
        return true;
    }

    public TeleportLocation arrival() {
        return arrival;
    }

    public String failure() {
        return failure;
    }

    public int checks() {
        return checks;
    }

    public int chunks() {
        return chunks == null ? 0 : chunks.acquiredCount();
    }

    private double distance(Column column) {
        return square(column.x - preferred.x) + square(column.z - preferred.z);
    }

    private static double square(double value) {
        return value * value;
    }

    private static int floor(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static final class Column {
        private final double x;
        private final double z;

        private Column(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}
