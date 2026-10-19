package betamoon.worldgen.terrain;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luamodloader.LuaContentRegistry;
import java.util.Map;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaValue;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/** Resolved raw material identity and metadata, validated before generation. */
public final class TerrainBlockState {
    public final int id;
    public final int metadata;
    public final String identity;

    public TerrainBlockState(int id, int metadata) {
        ChunkBlockBuffer.validateState(id, metadata);
        this.id = id;
        this.metadata = metadata;
        String key = id == 0
                ? "minecraft:block/air"
                : LuaContentRegistry.findCanonicalKey("block", id, Block.blocksList[id]);
        identity = (key == null ? "native:block/" + id : key) + "@" + id + ":" + metadata;
    }

    public static TerrainBlockState read(Object value, String path) {
        return read(value, path, 0);
    }

    private static TerrainBlockState read(Object value, String path, int depth) {
        if (depth > 8) {
            throw error(path, "block-state nesting exceeds its declaration budget");
        }
        Object block = value;
        int metadata = 0;
        Map<String, Object> states = null;
        if (value instanceof Map) {
            Map<String, Object> object = object(value, path);
            fields(object, path, "block", "metadata", "state");
            block = required(object, "block", path);
            if (block instanceof Map) {
                TerrainBlockState nested = read(block, path + ".block", depth + 1);
                block = nested.id;
                metadata = nested.metadata;
            }
            metadata = object.containsKey("metadata")
                    ? integer(object.get("metadata"), path + ".metadata", 0, 15)
                    : metadata;
            states = object.containsKey("state") ? object(object.get("state"), path + ".state") : null;
        }
        int id = resolve(block, path + ".block");
        if (states != null) {
            BlockDefinition definition = BlockCallbackRegistry.get(id);
            if (definition == null) {
                throw error(path + ".state", "block has no declared state schema");
            }
            for (Map.Entry<String, Object> entry : states.entrySet()) {
                Object input = entry.getValue();
                LuaValue state = input instanceof Boolean
                        ? LuaValue.valueOf((Boolean) input)
                        : input instanceof Number
                                ? LuaValue.valueOf(((Number) input).doubleValue())
                                : LuaValue.valueOf(string(input, path + ".state." + entry.getKey()));
                metadata = definition.state.set(metadata, entry.getKey(), state);
            }
        }
        return new TerrainBlockState(id, metadata);
    }

    private static int resolve(Object value, String path) {
        if (value instanceof Number) {
            return integer(value, path, 0, 255);
        }
        String name = string(value, path);
        if (name.equals("minecraft:block/air") || name.equals("minecraft:air") || name.equals("air")) {
            return 0;
        }
        for (int id = 1; id < 256; id++) {
            Block block = Block.blocksList[id];
            if (block != null && name.equals(LuaContentRegistry.findCanonicalKey("block", id, block))) {
                return id;
            }
        }
        String nativeName = name.startsWith("minecraft:") ? name.substring(10) : name;
        if (nativeName.startsWith("block/")) {
            nativeName = nativeName.substring(6);
        }
        Integer nativeId = NativeTerrainBlocks.find(nativeName);
        if (nativeId != null) {
            return nativeId;
        }
        throw error(path, "unknown block: " + name);
    }

    public void set(ChunkBlockBuffer buffer, int x, int y, int z) {
        buffer.set(x, y, z, id, metadata);
    }
}
