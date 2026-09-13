package betamoon.luaapi.block;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;

/** Minecraft's six native block faces with their public names and offsets. */
public enum BlockFace {
    DOWN(0, "down", 0, -1, 0), UP(1, "up", 0, 1, 0), NORTH(2, "north", 0, 0, -1), SOUTH(3, "south", 0, 0, 1), WEST(4,
            "west", -1, 0, 0), EAST(5, "east", 1, 0, 0);

    public final int nativeSide;
    public final String luaName;
    public final int xOffset;
    public final int yOffset;
    public final int zOffset;

    BlockFace(int nativeSide, String luaName, int xOffset, int yOffset, int zOffset) {
        this.nativeSide = nativeSide;
        this.luaName = luaName;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
    }

    public BlockFace opposite() {
        return fromNative(nativeSide ^ 1);
    }

    public static BlockFace fromNative(int side) {
        if (side < 0 || side >= values().length) {
            throw error("direction", "native side must be between 0 and 5: " + side);
        }
        return values()[side];
    }

    public static BlockFace resolve(String name, int facing) {
        for (BlockFace face : values()) {
            if (face.luaName.equals(name)) {
                return face;
            }
        }
        if (facing >= NORTH.nativeSide) {
            BlockFace front = fromNative(facing);
            if (name.equals("front")) {
                return front;
            }
            if (name.equals("back")) {
                return front.opposite();
            }
            if (name.equals("left")) {
                return front.left();
            }
            if (name.equals("right")) {
                return front.left().opposite();
            }
        }
        throw error("direction", "unknown direction or relative direction without facing: " + name);
    }

    private BlockFace left() {
        switch (this) {
            case NORTH:
                return WEST;
            case SOUTH:
                return EAST;
            case WEST:
                return SOUTH;
            case EAST:
                return NORTH;
            default:
                throw error("direction", "relative horizontal direction requires horizontal facing");
        }
    }
}
