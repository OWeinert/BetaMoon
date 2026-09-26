package betamoon.world.explosion;

import forge.ISpecialResistance;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.World;

/** Resolves vanilla and Forge position-aware explosion resistance safely. */
final class BlockExplosionResistance {
    private BlockExplosionResistance() {
    }

    static float at(World world, int x, int y, int z, double explosionX, double explosionY, double explosionZ,
            Entity source) {
        int id = world.getBlockId(x, y, z);
        if (id <= 0 || id >= Block.blocksList.length || Block.blocksList[id] == null) {
            return 0.0F;
        }
        Block block = Block.blocksList[id];
        float resistance = block instanceof ISpecialResistance
                ? ((ISpecialResistance) block).getSpecialExplosionResistance(
                        world, x, y, z, explosionX, explosionY, explosionZ, source)
                : block.getExplosionResistance(source);
        if (Float.isNaN(resistance) || resistance < 0.0F) {
            return 0.0F;
        }
        return resistance;
    }
}
