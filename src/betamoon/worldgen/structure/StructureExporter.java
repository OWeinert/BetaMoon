package betamoon.worldgen.structure;

import betamoon.tileentity.LuaTileEntity;
import betamoon.worldgen.WorldGenLimits;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Captures a bounded loaded cuboid into readable BetaMoon structure JSON. */
public final class StructureExporter {
    private StructureExporter() {
    }

    public static String capture(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int originX, int originY, int originZ, boolean includeAir) {
        if (minX > maxX || minY > maxY || minZ > maxZ || minY < 0 || maxY > 127) {
            throw new IllegalArgumentException("Structure export bounds are invalid");
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (sizeX > WorldGenLimits.MAX_FEATURE_RADIUS * 2 + 1
                || sizeZ > WorldGenLimits.MAX_FEATURE_RADIUS * 2 + 1
                || volume > WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE) {
            throw new IllegalArgumentException("Structure export exceeds local-template size limits");
        }
        if (originX < minX || originX > maxX || originY < minY || originY > maxY
                || originZ < minZ || originZ > maxZ) {
            throw new IllegalArgumentException("Structure export origin must be inside the selection");
        }
        Map<String, Integer> paletteIndex = new LinkedHashMap<String, Integer>();
        List<State> palette = new ArrayList<State>();
        List<CapturedBlock> blocks = new ArrayList<CapturedBlock>();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!world.blockExists(x, y, z)) {
                        throw new IllegalArgumentException("Structure export selection contains an unloaded chunk");
                    }
                    int blockId = world.getBlockId(x, y, z);
                    if (blockId == 0 && !includeAir) {
                        continue;
                    }
                    int metadata = world.getBlockMetadata(x, y, z);
                    TileEntity tile = world.getBlockTileEntity(x, y, z);
                    LuaTable data = tile instanceof LuaTileEntity ? ((LuaTileEntity) tile).snapshotData() : null;
                    String identity = blockId + ":" + metadata + ":" + (data == null ? "" : data.tojstring());
                    Integer state = paletteIndex.get(identity);
                    if (state == null) {
                        state = Integer.valueOf(palette.size());
                        paletteIndex.put(identity, state);
                        palette.add(new State(blockId, metadata, data));
                    }
                    blocks.add(new CapturedBlock(x - minX, y - minY, z - minZ, state.intValue()));
                }
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"format\": \"betamoon_structure\",\n  \"size\": [")
                .append(sizeX).append(", ").append(sizeY).append(", ").append(sizeZ).append("],\n")
                .append("  \"origin\": [").append(originX - minX).append(", ").append(originY - minY)
                .append(", ").append(originZ - minZ).append("],\n  \"palette\": [\n");
        for (int index = 0; index < palette.size(); index++) {
            State state = palette.get(index);
            out.append("    { \"block\": ").append(state.blockId).append(", \"meta\": ")
                    .append(state.metadata);
            if (state.data != null && state.data.length() > 0) {
                out.append(", \"data\": ");
                json(out, state.data);
            }
            out.append(" }").append(index + 1 == palette.size() ? "\n" : ",\n");
        }
        out.append("  ],\n  \"blocks\": [\n");
        for (int index = 0; index < blocks.size(); index++) {
            CapturedBlock block = blocks.get(index);
            out.append("    { \"pos\": [").append(block.x).append(", ").append(block.y).append(", ")
                    .append(block.z).append("], \"state\": ").append(block.state).append(" }")
                    .append(index + 1 == blocks.size() ? "\n" : ",\n");
        }
        return out.append("  ],\n  \"markers\": []\n}\n").toString();
    }

    private static void json(StringBuilder out, LuaValue value) {
        if (value.isnil()) {
            out.append("null");
        } else if (value.isboolean()) {
            out.append(value.toboolean());
        } else if (value.isnumber()) {
            out.append(value.todouble());
        } else if (value.isstring()) {
            string(out, value.tojstring());
        } else if (value.istable()) {
            LuaTable table = (LuaTable) value;
            int length = table.length();
            boolean array = length > 0;
            LuaValue key = LuaValue.NIL;
            int count = 0;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                count++;
                if (!key.isint() || key.toint() < 1 || key.toint() > length) {
                    array = false;
                }
            }
            if (array && count == length) {
                out.append('[');
                for (int index = 1; index <= length; index++) {
                    if (index > 1) {
                        out.append(", ");
                    }
                    json(out, table.get(index));
                }
                out.append(']');
            } else {
                out.append('{');
                key = LuaValue.NIL;
                boolean first = true;
                while (true) {
                    Varargs next = table.next(key);
                    key = next.arg1();
                    if (key.isnil()) {
                        break;
                    }
                    if (!first) {
                        out.append(", ");
                    }
                    first = false;
                    string(out, key.tojstring());
                    out.append(": ");
                    json(out, next.arg(2));
                }
                out.append('}');
            }
        } else {
            string(out, value.tojstring());
        }
    }

    private static void string(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                out.append('\\').append(character);
            } else if (character == '\n') {
                out.append("\\n");
            } else if (character == '\r') {
                out.append("\\r");
            } else if (character == '\t') {
                out.append("\\t");
            } else if (character < 32) {
                out.append(String.format("\\u%04x", Integer.valueOf(character)));
            } else {
                out.append(character);
            }
        }
        out.append('"');
    }

    private static final class State {
        private final int blockId;
        private final int metadata;
        private final LuaTable data;

        private State(int blockId, int metadata, LuaTable data) {
            this.blockId = blockId;
            this.metadata = metadata;
            this.data = data;
        }
    }

    private static final class CapturedBlock {
        private final int x;
        private final int y;
        private final int z;
        private final int state;

        private CapturedBlock(int x, int y, int z, int state) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
        }
    }
}
