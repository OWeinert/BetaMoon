package betamoon.fluid.interaction;

/** Fixed neighbor direction relative to the fluid cell being replaced. */
public enum FluidFace {
    DOWN("down", 0, -1, 0),
    UP("up", 0, 1, 0),
    NORTH("north", 0, 0, -1),
    SOUTH("south", 0, 0, 1),
    WEST("west", -1, 0, 0),
    EAST("east", 1, 0, 0);

    private final String name;
    public final int x;
    public final int y;
    public final int z;

    FluidFace(String name, int x, int y, int z) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String value() {
        return name;
    }

    public static FluidFace parse(String name) {
        for (FluidFace face : values()) {
            if (face.name.equals(name)) {
                return face;
            }
        }
        throw new IllegalArgumentException("Unknown fluid interaction face '" + name + "'.");
    }
}
