package betamoon.luaapi.material;

import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class ArmorMaterialApi {
    /**
     * Simple container for armor material metadata.
     */
    public static final class ArmorMaterial {
        public final String name;
        public final int level;
        public final int renderIndex;

        ArmorMaterial(String name, int level, int renderIndex) {
            this.name = name;
            this.level = level;
            this.renderIndex = renderIndex;
        }
    }

    private ArmorMaterialApi() {
    }

    public static void attach(LuaTable materials) {
        materials.set("armor", new MaterialRegistry(ArmorMaterialApi::add));
    }

    private static LuaValue add(String name, LuaValue definition) {
        int protection = required(definition, "protection").checkint();
        return LuaValue.userdataOf(ArmorMaterialRegistry.register(name, protection));
    }
}
