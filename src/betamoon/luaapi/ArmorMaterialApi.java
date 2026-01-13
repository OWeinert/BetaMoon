package betamoon.luaapi;

import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class ArmorMaterialApi {
    /**
     * Simple container for armor material metadata.
     */
    static final class ArmorMaterial {
        final String name;
        final int level;
        final int renderIndex;

        private ArmorMaterial(String name, int level, int renderIndex) {
            this.name = name;
            this.level = level;
            this.renderIndex = renderIndex;
        }
    }

    private ArmorMaterialApi() {
    }

    static void attach(LuaTable module) {
        module.set("createArmorMaterial", new CreateArmorMaterial());
    }

    private static final class CreateArmorMaterial extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            int level = args.checkint(2);
            if (level < 0) {
                throw new LuaError("ArmorMaterial: level must be 0 or higher.");
            }
            int renderIndex = ModLoader.AddArmor(name);
            return LuaValue.userdataOf(new ArmorMaterial(name, level, renderIndex));
        }
    }
}
