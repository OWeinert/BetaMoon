package betamoon.luaapi.client.ui;

import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.graphics.GraphicsExpression;
import betamoon.gui.graphics.GraphicsMesh;
import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiFontFace;
import betamoon.luaapi.utils.LuaDeclarationValues;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.lwjgl.opengl.GL11;

/**
 * Strict Lua-to-Java paint compilation. Resource resolution must use already
 * prepared host leases.
 */
public final class GuiPaintCompiler {
    public interface Resources {
        GuiFontFace font(String key);

        GuiDrawable image(String key);
    }

    private static final GuiPropertyDescriptor COLOR = GuiPropertyDescriptor.value("color", Kind.COLOR, 0xFFFFFFFF);
    private final GuiProperties properties;
    private final Resources resources;

    public GuiPaintCompiler(GuiProperties properties, Resources resources) {
        this.properties = properties;
        this.resources = resources;
    }

    public GuiPaintProgram compile(LuaValue commands, String path) {
        dense(commands, path, 1024);
        GuiPaintProgram.Builder builder = new GuiPaintProgram.Builder();
        for (int index = 1; index <= commands.length(); index++) {
            String commandPath = path + "[" + index + "]";
            LuaValue command = commands.get(index);
            if (!command.istable()) {
                throw LuaDeclarationValues.error(commandPath, "expected a paint command table");
            }
            String operation = LuaDeclarationValues.string(command.get("op"), commandPath + ".op");
            try {
                compile(builder, command, operation, commandPath);
            } catch (IllegalArgumentException invalid) {
                throw LuaDeclarationValues.error(commandPath, invalid.getMessage());
            }
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    private void compile(GuiPaintProgram.Builder builder, LuaValue command, String operation, String path) {
        switch (operation) {
            case "rect":
                fields(command, path, "x", "y", "width", "height", "color");
                builder.rectangle(expression(command.get("x"), path + ".x", 0),
                        expression(command.get("y"), path + ".y", 0),
                        expression(command.get("width"), path + ".width", null),
                        expression(command.get("height"), path + ".height", null), color(command, path));
                return;
            case "line":
                fields(command, path, "x1", "y1", "x2", "y2", "width", "color");
                builder.line(expression(command.get("x1"), path + ".x1", 0),
                        expression(command.get("y1"), path + ".y1", 0),
                        expression(command.get("x2"), path + ".x2", null),
                        expression(command.get("y2"), path + ".y2", null),
                        expression(command.get("width"), path + ".width", 1), color(command, path));
                return;
            case "mesh":
                fields(command, path, "mode", "vertices", "color");
                String mode = optionalString(command.get("mode"), path + ".mode", "triangles");
                int primitive = "triangles".equals(mode)
                        ? GL11.GL_TRIANGLES
                        : "lines".equals(mode) ? GL11.GL_LINES : "quads".equals(mode) ? GL11.GL_QUADS : -1;
                double[] values = coordinates(command.get("vertices"), path + ".vertices", 32768);
                float[] vertices = new float[values.length];
                for (int index = 0; index < vertices.length; index++) {
                    vertices[index] = (float) values[index];
                }
                builder.mesh(new GraphicsMesh(primitive, vertices), color(command, path));
                return;
            case "ring":
                fields(command, path, "x", "y", "inner", "outer", "start", "sweep", "segments", "color");
                builder.ring(expression(command.get("x"), path + ".x", 0), expression(command.get("y"), path + ".y", 0),
                        expression(command.get("inner"), path + ".inner", 0),
                        expression(command.get("outer"), path + ".outer", null),
                        expression(command.get("start"), path + ".start", 0),
                        expression(command.get("sweep"), path + ".sweep", 360),
                        command.get("segments").isnil()
                                ? 64
                                : LuaDeclarationValues.integer(command.get("segments"), path + ".segments", 3, 512),
                        color(command, path));
                return;
            case "text":
                fields(command, path, "text", "font", "x", "y", "color", "shadow");
                builder.text(LuaDeclarationValues.string(command.get("text"), path + ".text"),
                        resources.font(optionalString(command.get("font"), path + ".font", "native")),
                        expression(command.get("x"), path + ".x", 0), expression(command.get("y"), path + ".y", 0),
                        color(command, path),
                        command.get("shadow").isnil() || booleanValue(command.get("shadow"), path + ".shadow"));
                return;
            case "image":
                fields(command, path, "image", "x", "y", "width", "height");
                builder.image(resources.image(LuaDeclarationValues.string(command.get("image"), path + ".image")),
                        expression(command.get("x"), path + ".x", 0), expression(command.get("y"), path + ".y", 0),
                        expression(command.get("width"), path + ".width", null),
                        expression(command.get("height"), path + ".height", null));
                return;
            case "pushTransform":
                fields(command, path, "x", "y", "scaleX", "scaleY", "rotation");
                builder.pushTransform(expression(command.get("x"), path + ".x", 0),
                        expression(command.get("y"), path + ".y", 0),
                        expression(command.get("scaleX"), path + ".scaleX", 1),
                        expression(command.get("scaleY"), path + ".scaleY", 1),
                        expression(command.get("rotation"), path + ".rotation", 0));
                return;
            case "popTransform":
                fields(command, path);
                builder.popTransform();
                return;
            case "pushClip":
                fields(command, path, "x", "y", "width", "height");
                builder.pushClip(expression(command.get("x"), path + ".x", 0),
                        expression(command.get("y"), path + ".y", 0),
                        expression(command.get("width"), path + ".width", null),
                        expression(command.get("height"), path + ".height", null));
                return;
            case "popClip":
                fields(command, path);
                builder.popClip();
                return;
            case "hitRect":
                fields(command, path, "name", "x", "y", "width", "height");
                builder.hitRectangle(name(command, path), expression(command.get("x"), path + ".x", 0),
                        expression(command.get("y"), path + ".y", 0),
                        expression(command.get("width"), path + ".width", null),
                        expression(command.get("height"), path + ".height", null));
                return;
            case "hitCircle":
                fields(command, path, "name", "x", "y", "radius");
                builder.hitCircle(name(command, path), expression(command.get("x"), path + ".x", 0),
                        expression(command.get("y"), path + ".y", 0),
                        expression(command.get("radius"), path + ".radius", null));
                return;
            case "hitPolygon":
                fields(command, path, "name", "vertices");
                builder.hitPolygon(name(command, path), coordinates(command.get("vertices"), path + ".vertices", 512));
                return;
            default:
                throw LuaDeclarationValues.error(path + ".op", "unknown prepared paint operation: " + operation);
        }
    }

    private GraphicsExpression expression(LuaValue value, String path, Number fallback) {
        if (value.isnil() && fallback != null) {
            return GraphicsExpression.constant(fallback.doubleValue());
        }
        if (value.type() == LuaValue.TNUMBER) {
            return GraphicsExpression.constant(LuaDeclarationValues.number(value, path));
        }
        LuaDeclarationValues.fields(value, path, "source", "property", "factor", "offset");
        String source = LuaDeclarationValues.string(value.get("source"), path + ".source");
        double factor = value.get("factor").isnil()
                ? 1
                : LuaDeclarationValues.number(value.get("factor"), path + ".factor");
        double offset = value.get("offset").isnil()
                ? 0
                : LuaDeclarationValues.number(value.get("offset"), path + ".offset");
        if ("property".equals(source)) {
            String name = LuaDeclarationValues.string(value.get("property"), path + ".property");
            GuiPropertyDescriptor descriptor = properties.descriptors().get(name);
            if (descriptor == null || !(descriptor.kind == Kind.NUMBER || descriptor.kind == Kind.INTEGER)) {
                throw LuaDeclarationValues.error(path, "requires a declared numeric property");
            }
            return GraphicsExpression.property(name, descriptor.minimum, descriptor.maximum, factor, offset);
        }
        if (!value.get("property").isnil()) {
            throw LuaDeclarationValues.error(path + ".property", "only property sources accept a property name");
        }
        if ("width".equals(source)) {
            return GraphicsExpression.width(factor, offset);
        }
        if ("height".equals(source)) {
            return GraphicsExpression.height(factor, offset);
        }
        throw LuaDeclarationValues.error(path + ".source", "expected width, height or property");
    }

    private static int color(LuaValue command, String path) {
        LuaValue value = command.get("color");
        try {
            return (Integer) GuiPropertyValues.read(COLOR, value.isnil() ? LuaValue.valueOf("#FFFFFFFF") : value);
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path + ".color", invalid.getMessage());
        }
    }

    private static String optionalString(LuaValue value, String path, String fallback) {
        return value.isnil() ? fallback : LuaDeclarationValues.string(value, path);
    }

    private static String name(LuaValue command, String path) {
        return LuaDeclarationValues.string(command.get("name"), path + ".name");
    }

    private static void fields(LuaValue command, String path, String... names) {
        String[] allowed = new String[names.length + 1];
        allowed[0] = "op";
        System.arraycopy(names, 0, allowed, 1, names.length);
        LuaDeclarationValues.fields(command, path, allowed);
    }

    private static boolean booleanValue(LuaValue value, String path) {
        if (!value.isboolean()) {
            throw LuaDeclarationValues.error(path, "expected a boolean");
        }
        return value.toboolean();
    }

    private static double[] coordinates(LuaValue value, String path, int maximum) {
        dense(value, path, maximum);
        double[] result = new double[value.length()];
        for (int index = 0; index < result.length; index++) {
            result[index] = LuaDeclarationValues.number(value.get(index + 1), path + "[" + (index + 1) + "]");
        }
        return result;
    }

    private static void dense(LuaValue value, String path, int maximum) {
        if (!value.istable() || value.length() > maximum) {
            throw LuaDeclarationValues.error(path, "expected a bounded dense list");
        }
        int length = value.length();
        if (value.checktable().keys().length != length) {
            throw LuaDeclarationValues.error(path, "expected a dense list");
        }
        for (LuaValue key : value.checktable().keys()) {
            if (!key.isinttype() || key.toint() < 1 || key.toint() > length) {
                throw LuaDeclarationValues.error(path, "expected a dense list");
            }
        }
    }
}
