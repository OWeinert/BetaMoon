package betamoon.teleport;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.worldgen.spatial.GenerationValues;
import betamoon.worldgen.terrain.TerrainBlockState;
import java.util.Map;
import net.minecraft.src.Block;

/**
 * Explicit bounded non-destructive fallback. Runtime plans validate every cell
 * before editing.
 */
public final class TeleportPlatformDefinition {
    public final TerrainBlockState block;
    public final int width;
    public final int depth;
    public final int clearHeight;
    public final int y;

    public TeleportPlatformDefinition(Map<String, Object> value, String path) {
        GenerationValues.fields(value, path, "type", "block", "width", "depth", "clearHeight", "y");
        if (value.containsKey("type")
                && !GenerationValues.string(value.get("type"), path + ".type").equals("platform")) {
            throw GenerationValues.error(path + ".type", "expected platform");
        }
        block = TerrainBlockState.read(GenerationValues.required(value, "block", path), path + ".block");
        width = integer(value, "width", 5, 3, 9, path);
        depth = integer(value, "depth", 1, 1, 3, path);
        clearHeight = integer(value, "clearHeight", 3, 2, 4, path);
        y = integer(value, "y", 64, depth - 1, 127 - clearHeight, path);
        Block nativeBlock = Block.blocksList[block.id];
        BlockDefinition custom = BlockCallbackRegistry.get(block.id);
        if ((width & 1) == 0 || width * width * (depth + clearHeight) > 512 || nativeBlock == null
                || Block.isBlockContainer[block.id] || !nativeBlock.blockMaterial.isSolid()
                || nativeBlock.blockMaterial.getIsLiquid() || ArrivalInspector.hazard(block.id)
                || (custom == null ? !nativeBlock.isOpaqueCube() : !custom.shapes.hasFullCubeCollision())) {
            throw GenerationValues.error(path,
                    "platform requires an odd width, at most 512 cells and safe solid support");
        }
    }

    private static int integer(Map<String, Object> value, String field, int fallback, int min, int max, String path) {
        return value.containsKey(field)
                ? GenerationValues.integer(value.get(field), path + "." + field, min, max)
                : fallback;
    }
}
