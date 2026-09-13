package betamoon.luaapi.block;

import java.util.Arrays;
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

    public BlockVisualDefinition(LuaValue definition) {
        BlockBox shape = null;
        int type = 0;
        int pass = 0;
        LuaValue render = definition.get("render");
        if (!render.isnil()) {
            fields(render, "render", "preset", "pass", "variants", "bounds");
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
                    variants.put(metadata, new Variant(declared.get(key)));
                }
            }
        }
        bounds = shape;
        renderType = type;
        renderPass = pass;
    }

    public int texture(int metadata, int side, int fallback) {
        Variant variant = variants.get(metadata);
        return variant == null || variant.textures[side] < 0 ? fallback : variant.textures[side];
    }

    public int color(int metadata) {
        Variant variant = variants.get(metadata);
        return variant == null ? 0xFFFFFF : variant.color;
    }

    private static final class Variant {
        private final int[] textures = {-1, -1, -1, -1, -1, -1};
        private final int color;

        private Variant(LuaValue definition) {
            fields(definition, "render.variant", "texture", "textures", "color");
            if (!definition.get("texture").isnil()) {
                Arrays.fill(textures, integer(definition.get("texture"), "render.variant.texture", 0, 255));
            }
            LuaValue faces = definition.get("textures");
            if (!faces.isnil()) {
                for (String face : keys(faces, "render.variant.textures")) {
                    textures[BlockFaces.side(face, -1)] = integer(faces.get(face), "render.variant.textures." + face, 0,
                            255);
                }
            }
            color = definition.get("color").isnil()
                    ? 0xFFFFFF
                    : integer(definition.get("color"), "render.variant.color", 0, 0xFFFFFF);
        }
    }
}
