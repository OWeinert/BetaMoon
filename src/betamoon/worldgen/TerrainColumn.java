package betamoon.worldgen;

import net.minecraft.src.Block;
import net.minecraft.src.BlockContainer;
import net.minecraft.src.Material;

/** Lazily sampled, attempt-local facts for one world column. */
final class TerrainColumn {
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private final FeatureContext context;
    private final int x;
    private final int z;
    private int worldSurface = UNKNOWN;
    private int solidSurface = UNKNOWN;
    private int oceanFloor = UNKNOWN;
    private int fluidSurface = UNKNOWN;

    TerrainColumn(FeatureContext context, int x, int z) {
        this.context = context;
        this.x = x;
        this.z = z;
    }

    int sample(TerrainSurface surface) {
        switch (surface) {
            case WORLD_SURFACE:
                return worldSurface();
            case SOLID_SURFACE:
                return solidSurface();
            case OCEAN_FLOOR:
                return oceanFloor();
            case FLUID_SURFACE:
                return fluidSurface();
            default:
                return -1;
        }
    }

    int worldSurface() {
        if (worldSurface == UNKNOWN) {
            worldSurface = context.rawSurfaceHeight(x, z);
        }
        return worldSurface;
    }

    int solidSurface() {
        if (solidSurface == UNKNOWN) {
            solidSurface = scanSolid();
        }
        return solidSurface;
    }

    int oceanFloor() {
        if (oceanFloor == UNKNOWN) {
            oceanFloor = scanSolid();
        }
        return oceanFloor;
    }

    int fluidSurface() {
        if (fluidSurface != UNKNOWN) {
            return fluidSurface;
        }
        int start = worldSurface();
        if (start < 0) {
            fluidSurface = -1;
            return fluidSurface;
        }
        int top = Math.min(WorldGenLimits.MAX_HEIGHT, Math.max(WorldGenLimits.MIN_HEIGHT, start - 1));
        int block;
        do {
            block = context.blockId(x, top, z);
            if (block < 0) {
                fluidSurface = -1;
                return fluidSurface;
            }
            if (isFluid(block)) {
                fluidSurface = top + 1;
                return fluidSurface;
            }
            top--;
        } while (top >= WorldGenLimits.MIN_HEIGHT && isReplaceable(block));
        fluidSurface = -1;
        return fluidSurface;
    }

    private int scanSolid() {
        int start = worldSurface();
        if (start < 0) {
            return -1;
        }
        for (int y = Math.min(WorldGenLimits.MAX_HEIGHT, start - 1); y >= WorldGenLimits.MIN_HEIGHT; y--) {
            int block = context.blockId(x, y, z);
            if (block < 0) {
                return -1;
            }
            if (isStructural(block)) {
                return y + 1;
            }
        }
        return WorldGenLimits.MIN_HEIGHT;
    }

    static boolean isFluid(int blockId) {
        Material material = material(blockId);
        return material == Material.water || material == Material.lava;
    }

    static boolean isWater(int blockId) {
        return material(blockId) == Material.water;
    }

    static boolean isLava(int blockId) {
        return material(blockId) == Material.lava;
    }

    static boolean isReplaceable(int blockId) {
        if (blockId == 0) {
            return true;
        }
        Material material = material(blockId);
        return material == Material.air || material == Material.plants || material == Material.leaves
                || material == Material.snow || material == Material.fire || material == Material.circuits;
    }

    static boolean isStructural(int blockId) {
        if (blockId <= 0 || blockId >= Block.blocksList.length || Block.blocksList[blockId] == null) {
            return false;
        }
        Material material = Block.blocksList[blockId].blockMaterial;
        return material.getIsSolid() && material != Material.leaves && material != Material.snow && !isFluid(blockId);
    }

    static boolean isOrdinaryTerrain(int blockId) {
        if (blockId <= 0 || blockId >= Block.blocksList.length || Block.blocksList[blockId] == null) {
            return false;
        }
        Material material = Block.blocksList[blockId].blockMaterial;
        return material == Material.rock || material == Material.ground || material == Material.sand
                || material == Material.snow || blockId == Block.grass.blockID || blockId == Block.gravel.blockID
                || blockId == Block.ice.blockID;
    }

    static boolean isTerrainMaterial(int blockId) {
        return isStructural(blockId) && !(Block.blocksList[blockId] instanceof BlockContainer);
    }

    private static Material material(int blockId) {
        return blockId <= 0 || blockId >= Block.blocksList.length || Block.blocksList[blockId] == null
                ? Material.air
                : Block.blocksList[blockId].blockMaterial;
    }
}
