package betamoon.luaapi.block;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;

/** Surface category to which a declarative block may attach. */
public enum BlockAttachment {
    FLOOR("floor"), WALL("wall"), CEILING("ceiling");

    private final String luaName;

    BlockAttachment(String luaName) {
        this.luaName = luaName;
    }

    public static BlockAttachment forFace(BlockFace face) {
        return face == BlockFace.UP ? FLOOR : face == BlockFace.DOWN ? CEILING : WALL;
    }

    public static BlockAttachment parse(String name) {
        for (BlockAttachment attachment : values()) {
            if (attachment.luaName.equals(name)) {
                return attachment;
            }
        }
        throw error("placement.attachTo", "expected floor, wall, or ceiling");
    }
}
