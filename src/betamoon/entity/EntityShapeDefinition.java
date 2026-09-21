package betamoon.entity;

import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Bounded axis-aligned gameplay shape in entity-local block coordinates. */
public final class EntityShapeDefinition {
    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;
    public final double sizeX;
    public final double sizeY;
    public final double sizeZ;

    public EntityShapeDefinition(LuaValue value, String path) {
        fields(value, path, "offset", "size");
        LuaValue offset = required(value, "offset");
        LuaValue size = required(value, "size");
        fields(offset, path + ".offset", "x", "y", "z");
        fields(size, path + ".size", "x", "y", "z");
        offsetX = bounded(required(offset, "x"), -16, 16, path + ".offset.x");
        offsetY = bounded(required(offset, "y"), -16, 16, path + ".offset.y");
        offsetZ = bounded(required(offset, "z"), -16, 16, path + ".offset.z");
        sizeX = bounded(required(size, "x"), 0.01, 16, path + ".size.x");
        sizeY = bounded(required(size, "y"), 0.01, 16, path + ".size.y");
        sizeZ = bounded(required(size, "z"), 0.01, 16, path + ".size.z");
    }

    private static double bounded(LuaValue value, double min, double max, String path) {
        double parsed = number(value, path);
        if (parsed < min || parsed > max) {
            throw error(path, "expected " + min + ".." + max);
        }
        return parsed;
    }
}
