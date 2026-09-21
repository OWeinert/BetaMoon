package betamoon.entity;

import java.util.List;
import java.util.Set;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Entity;
import net.minecraft.src.MovingObjectPosition;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;

/** Finds the first block or entity intersected by one projectile movement segment. */
final class ProjectileCollision {
    private ProjectileCollision() {
    }

    static Hit trace(World world, Entity projectile, Entity owner, boolean excludeOwner, Set<Entity> ignored,
            Vec3D start, Vec3D end) {
        // Beta 1.7.3 advances its start vector while tracing blocks. Keep the original segment
        // for entity intersections and distance ordering.
        MovingObjectPosition block = world.func_28105_a(
                Vec3D.createVector(start.xCoord, start.yCoord, start.zCoord),
                Vec3D.createVector(end.xCoord, end.yCoord, end.zCoord), false, true);
        double nearest = block == null ? Double.POSITIVE_INFINITY : start.squareDistanceTo(block.hitVec);
        Hit result = block == null ? null : new Hit(block, null, block.hitVec);

        double dx = end.xCoord - start.xCoord;
        double dy = end.yCoord - start.yCoord;
        double dz = end.zCoord - start.zCoord;
        AxisAlignedBB area = projectile.boundingBox.addCoord(dx, dy, dz).expand(1, 1, 1);
        List nearby = world.getEntitiesWithinAABBExcludingEntity(projectile, area);
        for (Object candidateValue : nearby) {
            Entity candidate = (Entity) candidateValue;
            if (candidate instanceof LuaEntityPart) {
                LuaEntityPart part = (LuaEntityPart) candidate;
                if (!part.acceptsDamage()) {
                    continue;
                }
                Entity parent = part.parent();
                if (parent == projectile || excludeOwner && parent == owner) {
                    continue;
                }
            }
            if (candidate.isDead || ignored.contains(candidate) || !candidate.canBeCollidedWith()
                    || excludeOwner && candidate == owner) {
                continue;
            }
            MovingObjectPosition intersection = candidate.boundingBox.expand(0.3, 0.3, 0.3)
                    .func_1169_a(start, end);
            if (intersection == null) {
                continue;
            }
            double distance = start.squareDistanceTo(intersection.hitVec);
            if (distance < nearest) {
                nearest = distance;
                result = new Hit(intersection, candidate, intersection.hitVec);
            }
        }
        return result;
    }

    static final class Hit {
        final MovingObjectPosition nativeHit;
        final Entity entity;
        final Vec3D position;

        Hit(MovingObjectPosition nativeHit, Entity entity, Vec3D position) {
            this.nativeHit = nativeHit;
            this.entity = entity;
            this.position = position;
        }
    }
}
