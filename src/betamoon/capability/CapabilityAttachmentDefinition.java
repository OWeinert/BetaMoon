package betamoon.capability;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.block.BlockFaces;
import betamoon.tileentity.LuaTileEntity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/** One tile type's implementation of a reusable capability contract. */
public final class CapabilityAttachmentDefinition {
    public final CapabilityDefinition capability;
    public final Map<String, Object> config;
    public final Map<String, LuaValue> operations;
    public final Map<String, Object> ports;

    public CapabilityAttachmentDefinition(CapabilityDefinition capability, Map<String, Object> config,
            Map<String, LuaValue> operations, Map<String, Object> ports) {
        this.capability = capability;
        this.config = Collections.unmodifiableMap(new LinkedHashMap<>(config));
        this.operations = Collections.unmodifiableMap(new LinkedHashMap<>(operations));
        this.ports = Collections.unmodifiableMap(new LinkedHashMap<>(ports));
    }

    public Object port(String face) {
        Object value = ports.get(face);
        return value == null && !ports.containsKey(face) ? "default" : value;
    }

    public Object port(String face, LuaTileEntity tile) {
        if (face == null || ports.containsKey(face)) {
            return port(face);
        }
        if (tile == null || tile.worldObj == null) {
            return "default";
        }
        BlockDefinition block = BlockCallbackRegistry.get(
                tile.worldObj.getBlockId(tile.xCoord, tile.yCoord, tile.zCoord));
        if (block == null) {
            return "default";
        }
        int facing = block.facing(tile.worldObj.getBlockMetadata(tile.xCoord, tile.yCoord, tile.zCoord));
        int requested = BlockFaces.side(face, -1);
        String[] relative = {"front", "back", "left", "right"};
        for (String candidate : relative) {
            if (ports.containsKey(candidate) && BlockFaces.side(candidate, facing) == requested) {
                return ports.get(candidate);
            }
        }
        return "default";
    }
}
