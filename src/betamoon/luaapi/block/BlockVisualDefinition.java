package betamoon.luaapi.block;

import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luaapi.LuaApiUtils;
import betamoon.client.assets.AssetLocation;
import betamoon.resources.EnumTexAtlas;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.keys;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Static render settings and metadata-indexed texture/color overrides. */
public final class BlockVisualDefinition {
    public final int renderType;
    public final BlockBox bounds;
    public final int renderPass;
    private final Map<Integer, Variant> variants = new HashMap<Integer, Variant>();
    public final ModelAppearanceDeclaration appearance;
    private final Map<Integer, ModelAppearanceDeclaration> appearances = new HashMap<>();
    private final boolean subtypeVariants;
    private final int subtypeMask;

    public BlockVisualDefinition(LuaValue definition) {
        this(definition, BlockSubtypes.NONE);
    }

    public BlockVisualDefinition(LuaValue definition, BlockSubtypes subtypes) {
        appearance = ModelAppearanceDeclaration.optional(definition.get("appearance"));
        BlockBox shape = null;
        int type = 0;
        int pass = 0;
        LuaValue render = definition.get("render");
        String variantBy = render.isnil() || render.get("variantBy").isnil()
                ? "metadata"
                : string(render.get("variantBy"), "render.variantBy");
        if (!variantBy.equals("metadata") && !variantBy.equals("subtype")) {
            throw error("render.variantBy", "expected metadata or subtype");
        }
        subtypeVariants = variantBy.equals("subtype");
        if (subtypeVariants && !subtypes.enabled) {
            throw error("render.variantBy", "subtype selection requires declared subtypes");
        }
        subtypeMask = subtypes.mask;
        if (!render.isnil()) {
            fields(render, "render", "preset", "pass", "variants", "variantBy", "bounds");
            if (!render.get("bounds").isnil()) {
                shape = BlockBox.fromCoordinates(BlockShapeDefinition.box(render.get("bounds"), "render.bounds"));
            }
            String preset = render.get("preset").isnil() ? "cube" : string(render.get("preset"), "render.preset");
            if (preset.equals("cross")) {
                type = 1;
            } else if (!preset.equals("cube") && !preset.equals("cuboid")) {
                throw error("render.preset", "expected cube, cuboid, or cross");
            }
            pass = render.get("pass").isnil() ? 0 : integer(render.get("pass"), "render.pass", 0, 1);
            LuaValue declared = render.get("variants");
            if (!declared.isnil()) {
                if (!declared.istable()) {
                    throw error("render.variants", "expected a table indexed by metadata");
                }
                LuaValue key = LuaValue.NIL;
                while (!(key = declared.next(key).arg1()).isnil()) {
                    int metadata = integer(key, "render.variants metadata", 0, 15);
                    if (subtypeVariants && !subtypes.validItemMetadata(metadata)) {
                        throw error("render.variants[" + metadata + "]", "not valid subtype metadata");
                    }
                    variants.put(metadata, new Variant(declared.get(key)));
                    LuaValue appearanceValue = declared.get(key).get("appearance");
                    if (!appearanceValue.isnil()) {
                        appearances.put(metadata, ModelAppearanceDeclaration.optional(appearanceValue));
                    }
                }
            }
        }
        bounds = shape;
        renderType = type;
        renderPass = pass;
    }

    public Map<Integer, ModelAppearanceDeclaration> appearances() {
        return Collections.unmodifiableMap(appearances);
    }

    public void resolveTextures() {
        Map<String, Integer> resolved = new HashMap<String, Integer>();
        for (Variant variant : variants.values()) {
            for (int side = 0; side < 6; side++) {
                AssetLocation texture = variant.assets[side];
                if (texture != null) {
                    String key = texture.getCacheKey();
                    Integer index = resolved.get(key);
                    if (index == null) {
                        index = LuaApiUtils.registerTexture(EnumTexAtlas.BLOCKS, texture);
                        resolved.put(key, index);
                    }
                    variant.textures[side] = index;
                }
            }
        }
    }

    public boolean hasModels() {
        return appearance != null || appearances.values().stream().anyMatch(value -> value != null);
    }

    public boolean isDynamic() {
        return appearance != null && appearance.isDynamic()
                || appearances.values().stream().anyMatch(value -> value != null && value.isDynamic());
    }

    public int texture(int metadata, int side, int fallback) {
        Variant variant = variants.get(variantMetadata(metadata));
        return variant == null || variant.textures[side] < 0 ? fallback : variant.textures[side];
    }

    public int color(int metadata) {
        Variant variant = variants.get(variantMetadata(metadata));
        return variant == null ? 0xFFFFFF : variant.color;
    }

    public int variantMetadata(int metadata) {
        return subtypeVariants ? metadata & subtypeMask : metadata;
    }

    private static final class Variant {
        private final int[] textures = {-1, -1, -1, -1, -1, -1};
        private final AssetLocation[] assets = new AssetLocation[6];
        private final int color;

        private Variant(LuaValue definition) {
            fields(definition, "render.variant", "texture", "textures", "color", "appearance");
            if (!definition.get("texture").isnil()) {
                LuaValue texture = definition.get("texture");
                if (texture.isnumber()) {
                    Arrays.fill(textures, integer(texture, "render.variant.texture", 0, 255));
                } else {
                    Arrays.fill(assets, AssetInputs.texture(texture));
                }
            }
            LuaValue faces = definition.get("textures");
            if (!faces.isnil()) {
                for (String face : keys(faces, "render.variant.textures")) {
                    int side = BlockFaces.side(face, -1);
                    LuaValue texture = faces.get(face);
                    if (texture.isnumber()) {
                        textures[side] = integer(texture, "render.variant.textures." + face, 0, 255);
                        assets[side] = null;
                    } else {
                        assets[side] = AssetInputs.texture(texture);
                    }
                }
            }
            color = definition.get("color").isnil()
                    ? 0xFFFFFF
                    : integer(definition.get("color"), "render.variant.color", 0, 0xFFFFFF);
        }
    }
}
