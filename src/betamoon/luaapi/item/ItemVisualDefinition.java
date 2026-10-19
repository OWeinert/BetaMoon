package betamoon.luaapi.item;

import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luaapi.LuaApiUtils;
import betamoon.client.assets.AssetLocation;
import betamoon.resources.EnumTexAtlas;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;

/**
 * Metadata-indexed item icons and tints, with the native item's normal icon as
 * fallback.
 */
public final class ItemVisualDefinition {
    private final Map<Integer, Variant> variants = new HashMap<Integer, Variant>();
    private final Map<Integer, ModelAppearanceDeclaration> appearances = new HashMap<>();

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
            fields(declaration, "render.variant", "icon", "texture", "color", "appearance");
            LuaValue appearance = declaration.get("appearance");
            if (!appearance.isnil()) {
                appearances.put(metadata, ModelAppearanceDeclaration.optional(appearance));
            }
            int icon = declaration.get("icon").isnil()
                    ? -1
                    : integer(declaration.get("icon"), "render.variant.icon", 0, 255);
            int color = declaration.get("color").isnil()
                    ? 0xFFFFFF
                    : integer(declaration.get("color"), "render.variant.color", 0, 0xFFFFFF);
            AssetLocation texture = declaration.get("texture").isnil()
                    ? null
                    : AssetInputs.texture(declaration.get("texture"));
            variants.put(metadata, new Variant(icon, color, texture));
        }
    }

    public Map<Integer, ModelAppearanceDeclaration> appearances() {
        return Collections.unmodifiableMap(appearances);
    }

    public Set<Integer> metadataValues() {
        return Collections.unmodifiableSet(variants.keySet());
    }

    public boolean hasModels() {
        return appearances.values().stream().anyMatch(value -> value != null);
    }

    public void resolveTextures() {
        Map<String, Integer> resolved = new HashMap<String, Integer>();
        for (Variant variant : variants.values()) {
            if (variant.texture != null) {
                String key = variant.texture.getCacheKey();
                Integer icon = resolved.get(key);
                if (icon == null) {
                    icon = LuaApiUtils.registerTexture(EnumTexAtlas.ITEMS, variant.texture);
                    resolved.put(key, icon);
                }
                variant.icon = icon;
            }
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
        private int icon;
        private final int color;
        private final AssetLocation texture;

        private Variant(int icon, int color, AssetLocation texture) {
            this.icon = icon;
            this.color = color;
            this.texture = texture;
        }
    }
}
