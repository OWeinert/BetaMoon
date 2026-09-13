package betamoon.luaapi.item;

import net.minecraft.src.EnumToolMaterial;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * Parsed native tool material; kind and properties belong to ItemDeclaration.
 */
final class ToolDeclaration {
    final EnumToolMaterial material;

    ToolDeclaration(LuaValue definition) {
        LuaValue value = definition.get("material");
        if (value.isnil()) {
            throw new LuaError("Tool definition requires material.");
        }
        material = resolveToolMaterial(value);
    }

    private static EnumToolMaterial resolveToolMaterial(LuaValue value) {
        if (value.isuserdata()) {
            Object userdata = value.touserdata();
            if (userdata instanceof EnumToolMaterial) {
                return (EnumToolMaterial) userdata;
            }
        }
        if (value.isstring()) {
            String name = value.checkjstring();
            if (name.equalsIgnoreCase("diamond")) {
                name = "emerald";
            }
            try {
                return EnumToolMaterial.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new LuaError("Tool: unknown tool material: " + name);
            }
        }
        throw new LuaError("Tool: material must be a material userdata or a name string.");
    }

}
