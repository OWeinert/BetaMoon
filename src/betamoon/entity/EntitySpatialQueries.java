package betamoon.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Entity;
import net.minecraft.src.World;

/** Bounded spatial entity queries with a shared spherical result contract. */
public final class EntitySpatialQueries {
    private EntitySpatialQueries() {
    }

    public static List<Entity> nearby(World world, Entity excluded, double x, double y, double z,
            double radius, int limit) {
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius);
        List candidates = excluded == null
                ? world.getEntitiesWithinAABB(Entity.class, area)
                : world.getEntitiesWithinAABBExcludingEntity(excluded, area);
        List<Entity> result = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (Object value : candidates) {
            Entity candidate = (Entity) value;
            double dx = candidate.posX - x;
            double dy = candidate.posY - y;
            double dz = candidate.posZ - z;
            if (!candidate.isDead && dx * dx + dy * dy + dz * dz <= radiusSquared) {
                result.add(candidate);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }
}
