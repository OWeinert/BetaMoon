package betamoon.client.particles;

import java.util.ArrayList;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;

/**
 * Native block shape and axis clipping without walking, contact, fire, or
 * entity gameplay callbacks.
 */
final class ParticleCollision {
    private ParticleCollision() {
    }

    static void move(ParticleSprite particle, double x, double y, double z) {
        AxisAlignedBB bounds = particle.boundingBox.addCoord(x, y, z);
        ArrayList<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
        int minX = (int) Math.floor(bounds.minX);
        int maxX = (int) Math.floor(bounds.maxX + 1);
        int minY = Math.max(0, (int) Math.floor(bounds.minY) - 1);
        int maxY = Math.min(127, (int) Math.floor(bounds.maxY + 1));
        int minZ = (int) Math.floor(bounds.minZ);
        int maxZ = (int) Math.floor(bounds.maxZ + 1);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    int id = particle.worldObj.getBlockId(bx, by, bz);
                    if (id > 0) {
                        Block.blocksList[id].getCollidingBoundingBoxes(particle.worldObj, bx, by, bz, bounds, boxes);
                    }
                }
            }
        }
        double requestedY = y;
        for (AxisAlignedBB box : boxes) {
            y = box.calculateYOffset(particle.boundingBox, y);
        }
        particle.boundingBox.offset(0, y, 0);
        for (AxisAlignedBB box : boxes) {
            x = box.calculateXOffset(particle.boundingBox, x);
        }
        particle.boundingBox.offset(x, 0, 0);
        for (AxisAlignedBB box : boxes) {
            z = box.calculateZOffset(particle.boundingBox, z);
        }
        particle.boundingBox.offset(0, 0, z);
        particle.posX = (particle.boundingBox.minX + particle.boundingBox.maxX) * 0.5;
        particle.posY = particle.boundingBox.minY;
        particle.posZ = (particle.boundingBox.minZ + particle.boundingBox.maxZ) * 0.5;
        particle.onGround = y != requestedY && requestedY < 0;
    }
}
