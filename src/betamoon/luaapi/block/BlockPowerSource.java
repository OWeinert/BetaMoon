package betamoon.luaapi.block;

import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileDataType;
import betamoon.tileentity.TileEntityDefinition;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.TileEntity;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * One directional digital output, bound to a constant or declared state/data
 * field.
 */
final class BlockPowerSource {
    final boolean enabled;
    final boolean constant;
    final String stateField;
    final String dataField;
    final String[] sides;
    BlockPowerSource(LuaValue value, String path, BlockStateSchema state, boolean relative, LuaValue tile) {
        enabled = !value.isnil();
        String sf = null;
        String df = null;
        boolean c = false;
        String[] faces = BlockFaces.parse(LuaValue.NIL, path + ".sides", relative);
        if (value.isboolean()) {
            c = value.toboolean();
        } else if (value.type() == LuaValue.TSTRING) {
            df = value.tojstring();
        } else if (!value.isnil()) {
            fields(value, path, "state", "data", "sides");
            if (!value.get("state").isnil()) {
                sf = string(value.get("state"), path + ".state");
            }
            if (!value.get("data").isnil()) {
                df = string(value.get("data"), path + ".data");
            }
            if ((sf == null) == (df == null)) {
                throw error(path, "specify exactly one of state or data");
            }
            faces = BlockFaces.parse(value.get("sides"), path + ".sides", relative);
        }
        if (sf != null && (!state.has(sf) || !state.get(state.defaults, sf).isboolean())) {
            throw error(path, "state must reference a boolean field");
        }
        if (df != null) {
            if (tile.isnil()) {
                throw error(path, "data power requires a tileEntity");
            }
            TileEntityDefinition definition = TileEntityApi.tileHandle(tile).definition;
            TileEntityDefinition.Field field = definition.fields.get(df);
            if (field == null || !(field.type == TileDataType.INTEGER || field.type == TileDataType.BOOLEAN)) {
                throw error(path, "data must reference an integer or boolean field");
            }
        }
        constant = c;
        stateField = sf;
        dataField = df;
        sides = faces;
    }

    boolean value(IBlockAccess world, int x, int y, int z, int face, BlockDefinition def) {
        boolean allowed = false;
        for (String side : sides) {
            if (BlockFaces.side(side, def.facing(world.getBlockMetadata(x, y, z))) == face) {
                allowed = true;
            }
        }
        if (!allowed) {
            return false;
        }
        if (stateField != null) {
            return def.state.get(world.getBlockMetadata(x, y, z), stateField).toboolean();
        }
        if (dataField != null) {
            TileEntity tile = world.getBlockTileEntity(x, y, z);
            if (!(tile instanceof LuaTileEntity)) {
                return false;
            }
            Object value = ((LuaTileEntity) tile).getDataValue(dataField);
            return Boolean.TRUE.equals(value) || value instanceof Number && ((Number) value).intValue() > 0;
        }
        return constant;
    }
}
