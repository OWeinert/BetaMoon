package betamoon.luaapi.client.ui;

import betamoon.client.assets.AssetLocation;
import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.graphics.GraphicsMesh;
import betamoon.gui.graphics.GuiResourceDefinitions;
import betamoon.gui.graphics.GuiResourceDefinitions.Buffer;
import betamoon.gui.graphics.GuiResourceDefinitions.Drawable;
import betamoon.gui.graphics.GuiResourceDefinitions.Font;
import betamoon.gui.graphics.GuiResourceDefinitions.Image;
import betamoon.gui.graphics.GuiResourceDefinitions.Mesh;
import betamoon.gui.graphics.GuiResourceDefinitions.Program;
import betamoon.gui.graphics.GuiResourceDefinitions.Resource;
import betamoon.gui.graphics.GuiResourceDefinitions.Target;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.gui.style.GuiBitmapFont;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import org.lwjgl.opengl.GL11;

/**
 * Resource grammar is parsed with the declaration owner; preparation never
 * revisits mutable Lua tables.
 */
public final class GuiResourceParser {
    private long bytes;

    public GuiResourceDefinitions parse(LuaValue value, String path) {
        if (value.isnil()) {
            return GuiResourceDefinitions.EMPTY;
        }
        bytes = 0;
        LuaDeclarationValues.fields(value, path, "images", "meshes", "buffers", "programs", "targets", "drawables",
                "fonts");
        Map<String, Resource> resources = new LinkedHashMap<>();
        for (String category : new String[]{"images", "meshes", "buffers", "programs", "targets", "drawables",
                "fonts"}) {
            LuaValue definitions = value.get(category);
            if (definitions.isnil()) {
                continue;
            }
            for (String name : LuaDeclarationValues.keys(definitions, path + "." + category)) {
                GuiComponentDefinition.name(name);
                if (name.equals("native") || resources.containsKey(name) || resources.size() >= 128) {
                    throw LuaDeclarationValues.error(path,
                            "resources require at most 128 unique names; native is reserved");
                }
                String resourcePath = path + "." + category + "." + name;
                try {
                    resources.put(name, resource(category, definitions.get(name), resourcePath));
                } catch (IllegalArgumentException invalid) {
                    throw LuaDeclarationValues.error(resourcePath, invalid.getMessage());
                }
                bytes += name.length() * 2L + 256;
                if (bytes > 4 * 1024 * 1024) {
                    throw LuaDeclarationValues.error(path, "resource metadata exceeds 4 MiB");
                }
            }
        }
        try {
            return new GuiResourceDefinitions(resources, bytes);
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    private Resource resource(String category, LuaValue value, String path) {
        switch (category) {
            case "images": {
                LuaDeclarationValues.fields(value, path, "source", "nearest");
                AssetLocation location = AssetInputs.texture(value.get("source"));
                if (location.getNativeTexture() != null && location.getNativeTexture().isCell()) {
                    throw LuaDeclarationValues.error(path + ".source",
                            "use a whole image; drawable regions select atlas cells");
                }
                bytes += value.get("source").tojstring().length() * 2L + 256;
                return new Image(location, LuaDeclarationValues.bool(value.get("nearest"), path + ".nearest", true));
            }
            case "meshes": {
                LuaDeclarationValues.fields(value, path, "mode", "vertices");
                return new Mesh(
                        new GraphicsMesh(mode(value.get("mode"), path), vertices(value.get("vertices"), path, 32768)));
            }
            case "buffers": {
                LuaDeclarationValues.fields(value, path, "mode", "stride", "vertices");
                int mode = mode(value.get("mode"), path);
                int stride = integer(value, "stride", path, 2, 2, 4);
                float[] vertices = vertices(value.get("vertices"), path, 65536);
                int primitive = mode == GL11.GL_TRIANGLES ? 3 : mode == GL11.GL_LINES ? 2 : 4;
                if (stride != 2 && stride != 4 || vertices.length % (stride * primitive) != 0) {
                    throw LuaDeclarationValues.error(path, "buffer requires stride 2 or 4 and complete primitives");
                }
                return new Buffer(mode, stride, vertices);
            }
            case "programs": {
                LuaDeclarationValues.fields(value, path, "vertex", "fragment", "uniforms");
                String vertex = text(value.get("vertex"), path + ".vertex", 262144);
                String fragment = text(value.get("fragment"), path + ".fragment", 262144);
                Map<String, Integer> uniforms = new LinkedHashMap<>();
                if (!value.get("uniforms").isnil()) {
                    for (String name : LuaDeclarationValues.keys(value.get("uniforms"), path + ".uniforms")) {
                        int size = LuaDeclarationValues.integer(value.get("uniforms").get(name),
                                path + ".uniforms." + name, -1, 16);
                        if (uniforms.size() >= 64 || !name.matches("[A-Za-z_][A-Za-z0-9_.\\[\\]]{0,127}")
                                || !(size == -1 || size >= 1 && size <= 4 || size == 16)) {
                            throw LuaDeclarationValues.error(path + ".uniforms",
                                    "uniforms require at most 64 names with vector sizes 1..4, matrix 16 or integer -1");
                        }
                        uniforms.put(name, size);
                    }
                }
                bytes += uniforms.size() * 256L;
                return new Program(vertex, fragment, uniforms);
            }
            case "targets": {
                LuaDeclarationValues.fields(value, path, "width", "height", "depth");
                int width = LuaDeclarationValues.integer(value.get("width"), path + ".width", 1, 4096);
                int height = LuaDeclarationValues.integer(value.get("height"), path + ".height", 1, 4096);
                if ((long) width * height > 4194304) {
                    throw LuaDeclarationValues.error(path, "target exceeds 4194304 pixels");
                }
                return new Target(width, height, LuaDeclarationValues.bool(value.get("depth"), path + ".depth", false));
            }
            case "drawables": {
                LuaDeclarationValues.fields(value, path, "image", "color", "x", "y", "width", "height", "border");
                String image = value.get("image").isnil()
                        ? null
                        : LuaDeclarationValues.string(value.get("image"), path + ".image");
                if (image == null && value.get("color").isnil() || image != null && !value.get("color").isnil()) {
                    throw LuaDeclarationValues.error(path, "drawable chooses an image or solid color");
                }
                int color = image != null
                        ? 0xFFFFFFFF
                        : (Integer) GuiPropertyValues.read(GuiPropertyDescriptor.value("color", Kind.COLOR, 0xFFFFFFFF),
                                value.get("color"));
                return new Drawable(image, color, integer(value, "x", path, 0, 0, 8192),
                        integer(value, "y", path, 0, 0, 8192), integer(value, "width", path, 0, 0, 8192),
                        integer(value, "height", path, 0, 0, 8192), integer(value, "border", path, 0, 0, 4096));
            }
            case "fonts": {
                LuaDeclarationValues.fields(value, path, "image", "lineHeight", "fallback", "glyphs");
                String image = LuaDeclarationValues.string(value.get("image"), path + ".image");
                int lineHeight = LuaDeclarationValues.integer(value.get("lineHeight"), path + ".lineHeight", 1, 512);
                int fallback = codepoint(value.get("fallback"), path + ".fallback");
                int length = LuaDeclarationValues.length(value.get("glyphs"), path + ".glyphs");
                if (length < 1 || length > 4096) {
                    throw LuaDeclarationValues.error(path, "font requires 1..4096 glyphs");
                }
                Map<Integer, GuiBitmapFont.Glyph> glyphs = new LinkedHashMap<>();
                for (int index = 1; index <= length; index++) {
                    LuaValue glyph = value.get("glyphs").get(index);
                    String glyphPath = path + ".glyphs[" + index + "]";
                    LuaDeclarationValues.fields(glyph, glyphPath, "codepoint", "x", "y", "width", "height", "advance",
                            "offsetX", "offsetY");
                    int point = codepoint(glyph.get("codepoint"), glyphPath + ".codepoint");
                    GuiBitmapFont.Glyph compiled = new GuiBitmapFont.Glyph(integer(glyph, "x", glyphPath, 0, 0, 8192),
                            integer(glyph, "y", glyphPath, 0, 0, 8192), integer(glyph, "width", glyphPath, 0, 0, 8192),
                            integer(glyph, "height", glyphPath, 0, 0, 8192),
                            LuaDeclarationValues.integer(glyph.get("advance"), glyphPath + ".advance", 0, 1024),
                            integer(glyph, "offsetX", glyphPath, 0, -1024, 1024),
                            integer(glyph, "offsetY", glyphPath, 0, -1024, 1024));
                    if (glyphs.put(point, compiled) != null) {
                        throw LuaDeclarationValues.error(glyphPath, "duplicate codepoint");
                    }
                }
                if (!glyphs.containsKey(fallback)) {
                    throw LuaDeclarationValues.error(path + ".fallback", "fallback glyph is required");
                }
                bytes += glyphs.size() * 128L;
                return new Font(image, lineHeight, fallback, glyphs);
            }
            default:
                throw LuaDeclarationValues.error(path, "unknown resource category");
        }
    }

    private float[] vertices(LuaValue value, String path, int maximum) {
        int length = LuaDeclarationValues.length(value, path + ".vertices");
        if (length < 1 || length > maximum) {
            throw LuaDeclarationValues.error(path, "vertex value budget exceeded");
        }
        float[] result = new float[length];
        for (int index = 0; index < length; index++) {
            double number = LuaDeclarationValues.number(value.get(index + 1), path + ".vertices[" + (index + 1) + "]");
            if (Math.abs(number) > 1e6) {
                throw LuaDeclarationValues.error(path, "vertex coordinates exceed +/-1000000");
            }
            result[index] = (float) number;
        }
        bytes += length * 4L;
        return result;
    }

    private String text(LuaValue value, String path, int maximum) {
        String result = LuaDeclarationValues.string(value, path);
        if (result.isEmpty() || result.length() > maximum) {
            throw LuaDeclarationValues.error(path, "text length exceeds resource limits");
        }
        bytes += result.getBytes(StandardCharsets.UTF_8).length;
        return result;
    }

    private static int integer(LuaValue record, String name, String path, int fallback, int minimum, int maximum) {
        return record.get(name).isnil()
                ? fallback
                : LuaDeclarationValues.integer(record.get(name), path + "." + name, minimum, maximum);
    }

    private static int mode(LuaValue value, String path) {
        String mode = value.isnil() ? "triangles" : LuaDeclarationValues.string(value, path + ".mode");
        switch (mode) {
            case "triangles":
                return GL11.GL_TRIANGLES;
            case "lines":
                return GL11.GL_LINES;
            case "quads":
                return GL11.GL_QUADS;
            default:
                throw LuaDeclarationValues.error(path + ".mode", "expected triangles, lines or quads");
        }
    }

    private static int codepoint(LuaValue value, String path) {
        int point = LuaDeclarationValues.integer(value, path, 0, 0x10FFFF);
        if (point >= 0xD800 && point <= 0xDFFF) {
            throw LuaDeclarationValues.error(path, "surrogate is not a Unicode codepoint");
        }
        return point;
    }
}
