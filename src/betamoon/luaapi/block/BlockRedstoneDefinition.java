package betamoon.luaapi.block;

import net.minecraft.src.IBlockAccess;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;

/**
 * Validated redstone outputs; input notification state belongs to the registry.
 */
public final class BlockRedstoneDefinition {
    public final boolean configured;
    private final String[] connections;
    private final BlockPowerSource weak;
    private final BlockPowerSource strong;

    public BlockRedstoneDefinition(LuaValue def, BlockStateSchema state, BlockPlacementDefinition placement) {
        LuaValue redstone = def.get("redstone");
        configured = !redstone.isnil();
        connections = !configured || redstone.get("connections").isnil()
                ? null
                : BlockFaces.parse(redstone.get("connections"), "redstone.connections", placement.horizontal);
        if (configured) {
            fields(redstone, "redstone", "weakPower", "strongPower", "onNeighborChanged", "onInputChanged",
                    "connections");

            weak = new BlockPowerSource(redstone.get("weakPower"), "redstone.weakPower", state, placement.horizontal,
                    def.get("tileEntity"));
            strong = new BlockPowerSource(redstone.get("strongPower"), "redstone.strongPower", state,
                    placement.horizontal, def.get("tileEntity"));

        } else {
            weak = null;
            strong = null;
        }
    }

    public boolean providesPower() {
        return weak != null && weak.enabled || strong != null && strong.enabled;
    }

    public boolean power(IBlockAccess world, int x, int y, int z, int queriedSide, boolean direct,
            BlockDefinition definition) {
        BlockPowerSource source = direct ? strong : weak;
        // Vanilla asks with the direction from the receiver to the provider.
        return source != null && source.value(world, x, y, z, queriedSide ^ 1, definition);
    }

    public boolean connects(int wireDirection, int facing) {
        if (connections == null) {
            return providesPower();
        }
        // Forge uses the wire renderer's horizontal numbering, not block side numbers.
        int[] faces = {2, 5, 3, 4};
        if (wireDirection < 0 || wireDirection >= faces.length) {
            return false;
        }
        for (String connection : connections) {
            if (BlockFaces.side(connection, facing) == faces[wireDirection]) {
                return true;
            }
        }
        return false;
    }
}
