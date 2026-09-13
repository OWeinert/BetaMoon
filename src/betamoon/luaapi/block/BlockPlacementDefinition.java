package betamoon.luaapi.block;

import net.minecraft.src.EntityLiving;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * Placement orientation and support rules, independent of callbacks and
 * rendering.
 */
public final class BlockPlacementDefinition {
    private final BlockStateSchema state;
    public final boolean horizontal;
    public final boolean solidSupport;
    public final boolean dropUnsupported;
    private final BlockAttachment[] attachments;

    public BlockPlacementDefinition(LuaValue definition, BlockStateSchema state) {
        this.state = state;
        LuaValue placement = definition;
        boolean facing = false;
        boolean support = false;
        boolean drop = false;
        BlockAttachment[] attach = BlockAttachment.values();
        if (!placement.isnil()) {
            fields(placement, "placement", "facing", "facingFrom", "attachTo", "requiresSolidSupport",
                    "dropWhenUnsupported");
            if (!placement.get("facing").isnil()) {
                if (!string(placement.get("facing"), "placement.facing").equals("horizontal")) {
                    throw error("placement.facing", "only horizontal is supported");
                }
                if (!state.has("facing")) {
                    throw error("placement.facing", "declare a facing enum in state");
                }
                state.requireEnumValues("facing", "north", "east", "south", "west");
                facing = true;
            }
            if (!placement.get("facingFrom").isnil()
                    && !string(placement.get("facingFrom"), "placement.facingFrom").equals("player")) {
                throw error("placement.facingFrom", "expected player");
            }
            support = bool(placement.get("requiresSolidSupport"), "placement.requiresSolidSupport", false);
            drop = bool(placement.get("dropWhenUnsupported"), "placement.dropWhenUnsupported", false);
            if (!placement.get("attachTo").isnil()) {
                LuaValue values = placement.get("attachTo");
                attach = new BlockAttachment[length(values, "placement.attachTo")];
                for (int i = 0; i < attach.length; i++) {
                    attach[i] = BlockAttachment.parse(string(values.get(i + 1), "placement.attachTo"));
                }
            }
            // Persist the attachment face whenever support can be on more than the floor.
            if ((support || drop) && !(attach.length == 1 && attach[0] == BlockAttachment.FLOOR)) {
                if (!state.has("attachedFace")) {
                    throw error("placement", "wall/ceiling support requires attachedFace state enum");
                }
            }
        }
        if (state.has("attachedFace")) {
            for (BlockFace face : BlockFace.values()) {
                for (BlockAttachment attachment : attach) {
                    if (attachment == BlockAttachment.forFace(face)) {
                        state.set(state.defaults, "attachedFace", LuaValue.valueOf(face.luaName));
                    }
                }
            }
        }
        horizontal = facing;
        solidSupport = support;
        dropUnsupported = drop;
        attachments = attach;
    }

    public boolean canPlace(World world, int x, int y, int z, int side) {
        BlockAttachment kind = BlockAttachment.forFace(BlockFace.fromNative(side));
        boolean allowed = false;
        for (BlockAttachment attachment : attachments) {
            if (attachment == kind) {
                allowed = true;
            }
        }
        if (!allowed) {
            return false;
        }
        BlockFace face = BlockFace.fromNative(side);
        if (solidSupport && !world.isBlockSolidOnSide(x - face.xOffset, y - face.yOffset, z - face.zOffset, side)) {
            return false;
        }
        return true;
    }

    public void placed(World world, int x, int y, int z, EntityLiving placer) {
        if (horizontal) {
            String[] facing = {"north", "east", "south", "west"};
            int index = (MathHelper.floor_double(placer.rotationYaw * 4D / 360D + .5D) & 3);
            world.setBlockMetadataWithNotify(x, y, z,
                    state.set(world.getBlockMetadata(x, y, z), "facing", LuaValue.valueOf(facing[index])));
        }
    }

}
