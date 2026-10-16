package betamoon.instrumentation.hooks.fluid;

import betamoon.fluid.FluidEcologyPolicy;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.FluidState;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.EnumCreatureType;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.Material;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;

/**
 * Environment-specific identity gates; synthetic materials never escape their
 * native call site.
 */
public final class FluidEcologyCallbacks {
    private FluidEcologyCallbacks() {
    }

    private static FluidEcologyPolicy custom(int block) {
        FluidRegistration fluid = FluidRegistry.instance().getByBlockId(block);
        return fluid == null || fluid.definition().isVanilla() ? null : fluid.definition().ecology();
    }

    public static int falling(World world, int x, int y, int z) {
        FluidEcologyPolicy policy = custom(world.getBlockId(x, y, z));
        return policy == null ? 0 : policy.fallingBlocksPassThrough ? 2 : 1;
    }

    public static boolean fallingResult(int captured, boolean original) {
        return captured == 0 ? original : captured == 2;
    }

    public static Material farmland(World world, int x, int y, int z) {
        FluidEcologyPolicy policy = custom(world.getBlockId(x, y, z));
        return policy == null
                ? world.getBlockMaterial(x, y, z)
                : policy.hydratesFarmland ? Material.water : Material.rock;
    }

    public static Material reeds(World world, int x, int y, int z) {
        FluidEcologyPolicy policy = custom(world.getBlockId(x, y, z));
        return policy == null ? world.getBlockMaterial(x, y, z) : policy.supportsReeds ? Material.water : Material.rock;
    }

    public static Material habitat(World world, int x, int y, int z) {
        FluidEcologyPolicy policy = custom(world.getBlockId(x, y, z));
        return policy == null
                ? world.getBlockMaterial(x, y, z)
                : policy.aquaticHabitat ? Material.water : Material.lava;
    }

    public static int pass() {
        return 0;
    }

    public static boolean habitatResult(EnumCreatureType creature, World world, int x, int y, int z, boolean original) {
        FluidEcologyPolicy policy = custom(world.getBlockId(x, y, z));
        if (policy == null) {
            return original;
        }
        return creature.getCreatureMaterial() == Material.water && policy.aquaticHabitat
                && !world.isBlockNormalCube(x, y + 1, z);
    }

    public static int pathBlock(IBlockAccess world, int x, int y, int z) {
        int block = world.getBlockId(x, y, z);
        FluidEcologyPolicy policy = custom(block);
        if (policy == null) {
            return block;
        }
        if (policy.pathfinding.equals("passable")) {
            return 0;
        }
        if (policy.pathfinding.equals("water")) {
            return Block.waterStill.blockID;
        }
        if (policy.pathfinding.equals("lava")) {
            return Block.lavaStill.blockID;
        }
        return Block.obsidian.blockID;
    }

    public static void sponge(World world, int x, int y, int z) {
        if (world.multiplayerWorld) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int px = x + dx;
                    int py = y + dy;
                    int pz = z + dz;
                    if (py < 0 || py > 127 || !world.blockExists(px, py, pz)) {
                        continue;
                    }
                    FluidEcologyPolicy policy = custom(world.getBlockId(px, py, pz));
                    if (policy != null && policy.absorbedBySponge) {
                        world.setBlockWithNotify(px, py, pz, 0);
                    }
                }
            }
        }
    }

    public static boolean boat(World world, AxisAlignedBB box, Material material) {
        if (material != Material.water) {
            return world.isAABBInMaterial(box, material);
        }
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX + 1);
        int minY = MathHelper.floor_double(box.minY);
        int maxY = MathHelper.floor_double(box.maxY + 1);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ + 1);
        if ((long) (maxX - minX) * (maxY - minY) * (maxZ - minZ) > 4096) {
            throw new IllegalStateException("Boat fluid query exceeds its cell budget");
        }
        for (int x = minX; x < maxX; x++) {
            for (int y = Math.max(0, minY); y < Math.min(128, maxY); y++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (!world.blockExists(x, y, z)) {
                        continue;
                    }
                    int id = world.getBlockId(x, y, z);
                    FluidEcologyPolicy policy = custom(id);
                    if (policy == null
                            ? id == 0 || Block.blocksList[id].blockMaterial != Material.water
                            : !policy.boatsFloat) {
                        continue;
                    }
                    int metadata = world.getBlockMetadata(x, y, z);
                    double surface = policy == null
                            ? y + 1 - (metadata < 8 ? metadata / 8.0 : 0)
                            : y + FluidState.of(metadata).surfaceHeight();
                    if (surface >= box.minY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
