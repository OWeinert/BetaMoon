package betamoon.entity.motion;

import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Entity;

/** Loaded-only bounds including the native collision query's block/entity margins. */
public final class EntitySpatialSafety {
    public static final double MAX_COORDINATE = 30000000;
    public static final long MAX_BLOCK_QUERIES = 32768;

    private EntitySpatialSafety() {
    }

    public static String check(Entity entity, AxisAlignedBB area) {
        if (entity.worldObj == null || entity.isDead) {
            return "entity_unavailable";
        }
        if (!finite(area) || area.minX < -MAX_COORDINATE || area.maxX > MAX_COORDINATE
                || area.minZ < -MAX_COORDINATE || area.maxZ > MAX_COORDINATE
                || area.minY < -128 || area.maxY > 4224) {
            return "out_of_bounds";
        }
        long width = (long) Math.floor(area.maxX + 1) - (long) Math.floor(area.minX);
        long depth = (long) Math.floor(area.maxZ + 1) - (long) Math.floor(area.minZ);
        long height = (long) Math.floor(area.maxY + 1) - (long) Math.floor(area.minY) + 1;
        if (width <= 0 || depth <= 0 || height <= 0 || width > MAX_BLOCK_QUERIES
                || depth > MAX_BLOCK_QUERIES || height > MAX_BLOCK_QUERIES
                || width * depth > MAX_BLOCK_QUERIES || width * depth * height > MAX_BLOCK_QUERIES) {
            return "work_limit";
        }
        // World collision also queries entities in a 0.25-block expansion; chunk lookup
        // itself searches a further two blocks for wide bodies. Include that footprint.
        int minX = (int) Math.floor(area.minX - 2.25);
        int maxX = (int) Math.floor(area.maxX + 2.25);
        int minZ = (int) Math.floor(area.minZ - 2.25);
        int maxZ = (int) Math.floor(area.maxZ + 2.25);
        if (!entity.worldObj.checkChunksExist(minX, 0, minZ, maxX, 127, maxZ)) {
            return "unloaded_chunk";
        }
        return null;
    }

    public static AxisAlignedBB swept(Entity entity, MotionVector delta) {
        AxisAlignedBB body = entity.boundingBox;
        double step = Math.max(0, entity.stepHeight);
        // Native movement can descend one block while sneak-guarding and try an
        // alternate stepped sweep. Block contact visits a one-block halo as well.
        return AxisAlignedBB.getBoundingBox(
                Math.min(body.minX, body.minX + delta.x) - 1,
                Math.min(body.minY, body.minY + delta.y) - 1,
                Math.min(body.minZ, body.minZ + delta.z) - 1,
                Math.max(body.maxX, body.maxX + delta.x) + 1,
                Math.max(body.maxY, body.maxY + delta.y) + step + 1,
                Math.max(body.maxZ, body.maxZ + delta.z) + 1);
    }

    private static boolean finite(AxisAlignedBB area) {
        return Double.isFinite(area.minX) && Double.isFinite(area.minY) && Double.isFinite(area.minZ)
                && Double.isFinite(area.maxX) && Double.isFinite(area.maxY) && Double.isFinite(area.maxZ)
                && area.minX <= area.maxX && area.minY <= area.maxY && area.minZ <= area.maxZ;
    }
}
