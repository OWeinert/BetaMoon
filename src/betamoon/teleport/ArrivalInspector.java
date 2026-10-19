package betamoon.teleport;

import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import java.util.ArrayList;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;

/**
 * Actual native shape checks. Every read is preceded by a complete loaded-area
 * check.
 */
public final class ArrivalInspector {
    private ArrivalInspector() {
    }

    public static String inspect(World world, EntityPlayer player, TeleportLocation position, String safety) {
        double halfWidth = player.width / 2.0;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBoxFromPool(position.x - halfWidth, position.y,
                position.z - halfWidth, position.x + halfWidth, position.y + player.height, position.z + halfWidth);
        if (box.minX <= -30000000 || box.maxX >= 30000000 || box.minZ <= -30000000 || box.maxZ >= 30000000
                || !safety.equals("unchecked") && (box.minY < 0 || box.maxY > 128)) {
            return "out_of_bounds";
        }
        if (!loaded(world, box)) {
            return "chunks_unavailable";
        }
        if (safety.equals("unchecked")) {
            return null;
        }
        if (!world.getCollidingBoundingBoxes(player, box).isEmpty()) {
            return "unsafe_destination";
        }
        AxisAlignedBB support = AxisAlignedBB.getBoundingBoxFromPool(box.minX, box.minY - 0.0625, box.minZ, box.maxX,
                box.minY, box.maxZ);
        ArrayList<AxisAlignedBB> supports = new ArrayList<AxisAlignedBB>();
        boolean safe = safety.equals("safe");
        for (int x = floor(box.minX); x <= floor(box.maxX); x++) {
            for (int z = floor(box.minZ); z <= floor(box.maxZ); z++) {
                for (int y = Math.max(0, floor(support.minY)); y <= Math.min(127, floor(box.maxY)); y++) {
                    int id = world.getBlockId(x, y, z);
                    Block block = Block.blocksList[id];
                    if (safe && hazard(id)) {
                        return "unsafe_destination";
                    }
                    if (block != null && y <= floor(box.minY)) {
                        block.getCollidingBoundingBoxes(world, x, y, z, support, supports);
                    }
                }
            }
        }
        return supports.isEmpty() ? "unsafe_destination" : null;
    }

    public static boolean loaded(World world, AxisAlignedBB box) {
        int minX = floor(box.minX - 2) >> 4;
        int minZ = floor(box.minZ - 2) >> 4;
        int maxX = floor(box.maxX + 2) >> 4;
        int maxZ = floor(box.maxZ + 2) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!ChunkPreparation.loaded(world.getIChunkProvider(), x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean hazard(int id) {
        if (id == Block.fire.blockID || id == Block.cactus.blockID) {
            return true;
        }
        FluidRegistration fluid = FluidRegistry.instance().getByBlockId(id);
        if (fluid != null) {
            return !fluid.definition().safeTeleportArrival();
        }
        Block block = Block.blocksList[id];
        if (block != null && block.blockMaterial.getIsLiquid()) {
            return true;
        }
        BlockDefinition custom = BlockCallbackRegistry.get(id);
        return custom != null && custom.teleportHazard;
    }

    private static int floor(double coordinate) {
        return (int) Math.floor(coordinate);
    }
}
