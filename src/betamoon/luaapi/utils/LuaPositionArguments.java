package betamoon.luaapi.utils;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Reads numeric coordinates or a named position table at one logical argument slot. */
public final class LuaPositionArguments {
    private static final int WORLD_LIMIT = 30000000;

    private LuaPositionArguments() {
    }

    public static Position cell(Varargs args, LuaValue receiver, int first, String path) {
        return read(args, receiver, first, path, true, false, -WORLD_LIMIT, WORLD_LIMIT, 0, 127, true);
    }

    public static Position cellCompatible(Varargs args, LuaValue receiver, int first, String path) {
        return read(args, receiver, first, path, true, false, -WORLD_LIMIT, WORLD_LIMIT, 0, 127, false);
    }

    public static Position cellCompatibleDefaultY(Varargs args, LuaValue receiver, int first, String path) {
        return read(args, receiver, first, path, true, false, -WORLD_LIMIT, WORLD_LIMIT, 0, 127, false, true);
    }

    public static Position integers(Varargs args, LuaValue receiver, int first, String path, int minY, int maxY) {
        return read(args, receiver, first, path, true, false, -WORLD_LIMIT, WORLD_LIMIT, minY, maxY, true);
    }

    public static Position integers(Varargs args, LuaValue receiver, int first, String path, int minXZ, int maxXZ,
            int minY, int maxY) {
        return read(args, receiver, first, path, true, false, minXZ, maxXZ, minY, maxY, true);
    }

    public static Position horizontal(Varargs args, LuaValue receiver, int first, String path) {
        return read(args, receiver, first, path, true, true, -WORLD_LIMIT, WORLD_LIMIT, 0, 0, true);
    }

    public static Position horizontal(Varargs args, LuaValue receiver, int first, String path, int minXZ, int maxXZ) {
        return read(args, receiver, first, path, true, true, minXZ, maxXZ, 0, 0, true);
    }

    public static Position horizontalCompatible(Varargs args, LuaValue receiver, int first, String path) {
        return read(args, receiver, first, path, true, true, -WORLD_LIMIT, WORLD_LIMIT, 0, 0, false);
    }

    public static Position vector(Varargs args, LuaValue receiver, int first, String path) {
        return read(args, receiver, first, path, false, false, 0, 0, 0, 0, true);
    }

    public static Position plane(Varargs args, LuaValue receiver, int first, String path) {
        return read(args, receiver, first, path, false, true, 0, 0, 0, 0, true);
    }

    private static Position read(Varargs args, LuaValue receiver, int first, String path, boolean integral,
            boolean horizontal, int minXZ, int maxXZ, int minY, int maxY, boolean strictTable) {
        return read(args, receiver, first, path, integral, horizontal, minXZ, maxXZ, minY, maxY, strictTable, false);
    }

    private static Position read(Varargs args, LuaValue receiver, int first, String path, boolean integral,
            boolean horizontal, int minXZ, int maxXZ, int minY, int maxY, boolean strictTable, boolean defaultY) {
        int offset = receiver != null && args.arg1() == receiver ? 1 : 0;
        LuaValue input = args.arg(first + offset);
        if (input.istable()) {
            if (strictTable) {
                LuaDeclarationValues.fields(input, path + ".position", "x", "y", "z");
            }
            double x = component(input.get("x"), path + ".position.x", integral, minXZ, maxXZ);
            double z = component(input.get("z"), path + ".position.z", integral, minXZ, maxXZ);
            LuaValue yValue = input.get("y");
            double y = horizontal
                    ? yValue.isnil() || !strictTable ? 0 : LuaDeclarationValues.number(yValue, path + ".position.y")
                    : defaultY && yValue.isnil() ? 0
                            : component(yValue, path + ".position.y", integral, minY, maxY);
            return new Position(x, y, z, first + 1, true);
        }
        double x = component(input, path + ".x", integral, minXZ, maxXZ);
        if (horizontal) {
            double z = component(args.arg(first + offset + 1), path + ".z", integral, minXZ, maxXZ);
            return new Position(x, 0, z, first + 2, false);
        }
        LuaValue yValue = args.arg(first + offset + 1);
        double y = defaultY && yValue.isnil() ? 0 : component(yValue, path + ".y", integral, minY, maxY);
        double z = component(args.arg(first + offset + 2), path + ".z", integral, minXZ, maxXZ);
        return new Position(x, y, z, first + 3, false);
    }

    private static double component(LuaValue value, String path, boolean integral, int minimum, int maximum) {
        if (integral) {
            return LuaDeclarationValues.integer(value, path, minimum, maximum);
        }
        return LuaDeclarationValues.number(value, path);
    }

    public static final class Position {
        public final double x;
        public final double y;
        public final double z;
        public final int nextArgument;
        public final boolean tableForm;

        private Position(double x, double y, double z, int nextArgument, boolean tableForm) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nextArgument = nextArgument;
            this.tableForm = tableForm;
        }

        public int xInt() {
            return (int) x;
        }

        public int yInt() {
            return (int) y;
        }

        public int zInt() {
            return (int) z;
        }
    }
}
