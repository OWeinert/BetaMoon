package betamoon.luaapi.material;

import net.minecraft.src.EnumToolMaterial;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Installs declarative tool-material registration and lookup. */
public final class ToolMaterialApi {
    private ToolMaterialApi() {
    }

    public static void attach(LuaTable materials) {
        materials.set("tools", new MaterialRegistry(ToolMaterialApi::add));
    }

    private static LuaValue add(String name, LuaValue definition) {
        int harvestLevel = required(definition, "harvestLevel").checkint();
        int durability = required(definition, "durability").checkint();
        float efficiency = (float) required(definition, "efficiency").checkdouble();
        int damage = required(definition, "damage").checkint();
        EnumToolMaterial material = ToolMaterialRegistry.register(name, harvestLevel, durability, efficiency, damage);
        return LuaValue.userdataOf(material);
    }
}
