package betamoon.entity.motion;

import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;
import net.minecraft.src.World;

/** Immediate loaded-only spatial-index maintenance without running another native tick. */
final class EntityChunkMembership {
    private EntityChunkMembership() {
    }

    static String checkSource(Entity entity) {
        return entity.addedToChunk && !loaded(entity.worldObj, entity.chunkCoordX, entity.chunkCoordZ)
                ? "unloaded_chunk" : null;
    }

    static void synchronize(Entity entity, World expectedWorld) {
        if (entity.isDead || !entity.addedToChunk || entity.worldObj != expectedWorld) {
            return;
        }
        int x = (int) Math.floor(entity.posX / 16);
        int y = Math.max(0, Math.min(7, (int) Math.floor(entity.posY / 16)));
        int z = (int) Math.floor(entity.posZ / 16);
        if (entity.chunkCoordX == x && entity.chunkCoordY == y && entity.chunkCoordZ == z) {
            return;
        }
        // Both endpoints were preflighted. Recheck because a native block-contact
        // callback may remove/unload a target while movement is taking place.
        if (!loaded(expectedWorld, x, z) || !loaded(expectedWorld, entity.chunkCoordX, entity.chunkCoordZ)) {
            return;
        }
        Chunk source = expectedWorld.getChunkFromChunkCoords(entity.chunkCoordX, entity.chunkCoordZ);
        Chunk destination = expectedWorld.getChunkFromChunkCoords(x, z);
        source.removeEntityAtIndex(entity, entity.chunkCoordY);
        destination.addEntity(entity);
    }

    private static boolean loaded(World world, int chunkX, int chunkZ) {
        long x = chunkX * 16L;
        long z = chunkZ * 16L;
        if (Math.abs(x) > EntitySpatialSafety.MAX_COORDINATE || Math.abs(z) > EntitySpatialSafety.MAX_COORDINATE) {
            return false;
        }
        return world.checkChunksExist((int) x, 0, (int) z, (int) x, 127, (int) z);
    }
}
