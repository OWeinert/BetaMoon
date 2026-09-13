package betamoon.luaapi.item;

import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;

/**
 * Metadata-indexed item icons and tints, with the wrapper's normal icon as
 * fallback.
 */
public final class ItemVisualDefinition {
    private final Map<Integer, Variant> variants = new HashMap<Integer, Variant>();

    public ItemVisualDefinition(LuaValue render) {
        if (render.isnil()) {
            return;
        }
        fields(render, "render", "variants");
        LuaValue declarations = render.get("variants");
        if (declarations.isnil()) {
            return;
        }
        declarations.checktable();
        LuaValue key = LuaValue.NIL;
        while (!(key = declarations.next(key).arg1()).isnil()) {
            int metadata = integer(key, "render.variants metadata", 0, 32767);
            LuaValue declaration = declarations.get(key);
            fields(declaration, "render.variant", "icon", "color");
            int icon = declaration.get("icon").isnil()
                    ? -1
                    : integer(declaration.get("icon"), "render.variant.icon", 0, 255);
            int color = declaration.get("color").isnil()
                    ? 0xFFFFFF
                    : integer(declaration.get("color"), "render.variant.color", 0, 0xFFFFFF);
            variants.put(metadata, new Variant(icon, color));
        }
    }

    public int icon(int metadata, int fallback) {
        Variant variant = variants.get(metadata);
        return variant == null || variant.icon < 0 ? fallback : variant.icon;
    }

    public int color(int metadata, int fallback) {
        Variant variant = variants.get(metadata);
        return variant == null ? fallback : variant.color;
    }

    private static final class Variant {
        private final int icon;
        private final int color;

        private Variant(int icon, int color) {
            this.icon = icon;
            this.color = color;
        }
    }
}
