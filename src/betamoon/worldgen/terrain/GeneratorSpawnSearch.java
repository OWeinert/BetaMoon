package betamoon.worldgen.terrain;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.worldgen.spatial.FieldContext;
import net.minecraft.src.Block;
import net.minecraft.src.WorldChunkManager;

/** Bounded preview search. It cannot load or populate a world chunk. */
public final class GeneratorSpawnSearch {
    private GeneratorSpawnSearch() {
    }

    public static WorldGeneratorManifest.OriginalSpawn find(long seed, WorldGeneratorProfile profile,
            WorldChunkManager biomes) {
        for (int attempt = 0; attempt < 81; attempt++) {
            int x = (attempt % 9 - 4) * 32;
            int z = (attempt / 9 - 4) * 32;
            if (attempt == 0) {
                x = 0;
                z = 0;
            } else if (x == 0 && z == 0) {
                x = -128;
                z = -128;
            }
            FieldContext fields = new FieldContext(seed, 0, true, x, z,
                    biomes instanceof FieldContext.Environment ? (FieldContext.Environment) biomes : null,
                    profile.spatial);
            if (biomes instanceof ProfileWorldChunkManager) {
                ((ProfileWorldChunkManager) biomes).setFieldContext(fields);
            }
            GenerationContext context = new GenerationContext(fields, x >> 4, z >> 4,
                    biomes.loadBlockGeneratorData(null, (x >> 4) * 16, (z >> 4) * 16, 16, 16));
            ChunkBlockBuffer buffer = BetaMoonChunkProvider.generateRaw(profile, context);
            int localX = x & 15;
            int localZ = z & 15;
            for (int y = 125; y >= 0; y--) {
                int id = buffer.blockId(localX, y, localZ);
                if (safeGround(id) && buffer.blockId(localX, y + 1, localZ) == 0
                        && buffer.blockId(localX, y + 2, localZ) == 0) {
                    return new WorldGeneratorManifest.OriginalSpawn(x, y, z);
                }
            }
            if (!profile.requireSafeSpawn) {
                return new WorldGeneratorManifest.OriginalSpawn(x, 64, z);
            }
        }
        throw new IllegalStateException("Generator has no safe spawn in its bounded preview area; "
                + "provide suitable ground or an explicit spawn platform");
    }

    private static boolean harmful(int block) {
        return block == Block.cactus.blockID || block == Block.fire.blockID;
    }

    static boolean safeGround(int id) {
        Block block = Block.blocksList[id];
        if (block == null || harmful(id) || !block.blockMaterial.isSolid() || block.blockMaterial.getIsLiquid()) {
            return false;
        }
        BlockDefinition custom = BlockCallbackRegistry.get(id);
        return custom != null
                ? custom.shapes.hasFullCubeCollision()
                : block.isOpaqueCube() || id == Block.glass.blockID || id == Block.ice.blockID
                        || id == Block.leaves.blockID;
    }
}
