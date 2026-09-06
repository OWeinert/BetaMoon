package betamoon.luaapi.tileentity;

import betamoon.luaapi.LuaApiUtils;
import betamoon.resources.LuaTextureResources;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.TileEntityDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Converts declarative Lua GUI tables into immutable renderer definitions. */
final class ContainerGuiParser {
    private ContainerGuiParser() {
    }

    static ContainerGuiDefinition.Background parseBackground(LuaValue value, String preset, int rows) {
        String style = value.get("style").optjstring(null);
        String builtinName = value.get("builtin").optjstring(null);
        ContainerGuiDefinition.Texture texture = null;
        if (!value.get("image").isnil()) texture = customTexture(value.get("image").checkjstring());
        else if (builtinName != null) texture = builtinBackground(builtinName, rows);
        else if (style == null) texture = builtinBackground(preset, rows);
        if (style == null && ("minecraft:chest".equals(preset)
            || "minecraft:chest".equalsIgnoreCase(builtinName))) style = "chest";
        if (style != null && !"minecraft".equals(style) && !"chest".equals(style)) {
            throw new LuaError("background.style must be 'minecraft'.");
        }
        return new ContainerGuiDefinition.Background(texture, style,
            value.get("drawSlotFrames").toboolean(), rows);
    }

    static ContainerGuiDefinition.Label parseLabel(LuaValue value, String defaultText,
        int defaultX, int defaultY, int defaultWidth) {
        if (value.isboolean() && !value.toboolean()) return null;
        if (value.isnil()) return label(defaultText, defaultX, defaultY, defaultWidth, 0x404040, "left", false);
        if (value.isstring()) return label(value.checkjstring(), defaultX, defaultY, defaultWidth, 0x404040, "left", false);
        if (!value.istable()) throw new LuaError("GUI label must be text, a table, or false.");
        return label(value.get("text").optjstring(defaultText), value.get("x").optint(defaultX),
            value.get("y").optint(defaultY), value.get("width").optint(defaultWidth),
            color(value.get("color"), 0x404040), alignment(value.get("align").optjstring("left")),
            value.get("shadow").toboolean());
    }

    private static ContainerGuiDefinition.Label label(String text, int x, int y, int width,
        int color, String align, boolean shadow) {
        return new ContainerGuiDefinition.Label(text, x, y, width, color, align, shadow);
    }

    static void parseElements(LuaValue definitions, TileEntityDefinition tile, List output,
        int offsetX, int offsetY, ContainerGuiDefinition.Condition inherited) {
        if (!definitions.istable()) throw new LuaError("elements must be a list.");
        for (int i = 1; i <= definitions.length(); i++) {
            LuaValue value = definitions.get(i);
            if (!value.istable()) throw new LuaError("GUI elements must be definition tables.");
            String type = requiredString(value, "type").toLowerCase();
            int x = value.get("x").optint(0) + offsetX;
            int y = value.get("y").optint(0) + offsetY;
            ContainerGuiDefinition.Condition condition = combine(inherited,
                parseCondition(value.get("visibleWhen"), tile));
            if ("group".equals(type)) {
                parseElements(requiredTable(value, "elements"), tile, output, x, y, condition);
                continue;
            }
            String anchor = anchor(value.get("anchor").optjstring("top_left"));
            int layer = layer(value.get("layer").optjstring("content"));
            List tooltip = tooltip(value.get("tooltip"));
            if ("image".equals(type)) {
                ContainerGuiDefinition.Texture image = elementTexture(value, "image", false);
                int width = value.get("width").optint(image.width), height = value.get("height").optint(image.height);
                positive(width, height, "Image");
                output.add(new ContainerGuiDefinition.ImageElement(x, y, layer, anchor, condition, tooltip,
                    image, width, height));
            } else if ("text".equals(type)) {
                String field = value.get("value").optjstring(null);
                if (field != null) requireGuiField(tile, field);
                if (field == null && value.get("text").isnil()) throw new LuaError("Text element requires 'text' or 'value'.");
                output.add(new ContainerGuiDefinition.TextElement(x, y, layer, anchor, condition, tooltip,
                    value.get("text").optjstring(null), field, value.get("format").optjstring(null),
                    alignment(value.get("align").optjstring("left")), value.get("width").optint(0),
                    color(value.get("color"), 0x404040), value.get("shadow").toboolean()));
            } else if ("progress".equals(type)) {
                String field = requiredString(value, "value"); requireGuiField(tile, field);
                LuaValue maxValue = required(value, "maximum");
                int maximum = maxValue.isnumber() ? maxValue.checkint() : 0;
                String maximumField = maxValue.isstring() ? maxValue.checkjstring() : null;
                if (maximumField != null) requireGuiField(tile, maximumField);
                if (maximumField == null && maximum <= 0) throw new LuaError("Progress maximum must be positive.");
                output.add(new ContainerGuiDefinition.ProgressElement(x, y, layer, anchor, condition, tooltip,
                    field, maximumField, maximum, direction(value.get("direction").optjstring("left_to_right")),
                    Math.max(0, value.get("minimumPixels").optint(0)), value.get("hideWhenEmpty").toboolean(),
                    elementTexture(value, "image", false), elementTexture(value, "background", true)));
            } else if ("state_image".equals(type)) {
                String field = requiredString(value, "value"); requireGuiField(tile, field);
                Map states = new HashMap(); LuaValue stateDefs = value.get("states");
                if (!stateDefs.isnil()) readStates(stateDefs, states);
                if (!value.get("whenTrue").isnil()) states.put("true", customTexture(value.get("whenTrue").checkjstring()));
                if (!value.get("whenFalse").isnil()) states.put("false", customTexture(value.get("whenFalse").checkjstring()));
                if (states.isEmpty()) throw new LuaError("state_image requires states or whenTrue/whenFalse.");
                ContainerGuiDefinition.Texture fallback = value.get("default").isnil() ? null
                    : customTexture(value.get("default").checkjstring());
                output.add(new ContainerGuiDefinition.StateImageElement(x, y, layer, anchor, condition,
                    tooltip, field, states, fallback));
            } else if ("rectangle".equals(type)) {
                int width = requiredInt(value, "width"), height = requiredInt(value, "height");
                positive(width, height, "Rectangle");
                output.add(new ContainerGuiDefinition.RectangleElement(x, y, layer, anchor, condition,
                    tooltip, width, height, color(required(value, "color"), 0)));
            } else if ("tooltip".equals(type)) {
                if (tooltip.isEmpty()) tooltip = tooltip(required(value, "text"));
                int width = requiredInt(value, "width"), height = requiredInt(value, "height");
                positive(width, height, "Tooltip");
                output.add(new ContainerGuiDefinition.TooltipElement(x, y, layer, anchor, condition,
                    tooltip, width, height));
            } else if ("item".equals(type)) {
                String slot = value.get("slot").optjstring(null); ItemStack item = null;
                if (slot != null && !tile.slots.containsKey(slot)) throw new LuaError("Unknown item element slot: " + slot);
                if (!value.get("item").isnil()) item = LuaApiUtils.readItemStack(value.get("item"), true, "GUI item");
                if ((slot == null) == (item == null)) throw new LuaError("Item element requires either 'slot' or 'item'.");
                output.add(new ContainerGuiDefinition.ItemElement(x, y, layer, anchor, condition, tooltip,
                    slot, item, value.get("showCount").optboolean(true)));
            } else throw new LuaError("Unsupported GUI element type: " + type);
        }
    }

    /** Rejects fixed-size elements that cannot appear inside the GUI canvas. */
    static void validateBounds(List elements, int guiWidth, int guiHeight) {
        for (int i = 0; i < elements.size(); i++) {
            ContainerGuiDefinition.Element element = (ContainerGuiDefinition.Element) elements.get(i);
            int width = fixedWidth(element), height = fixedHeight(element);
            if (width <= 0 || height <= 0) continue;
            int x = anchoredX(element.anchor, element.x, width, guiWidth);
            int y = anchoredY(element.anchor, element.y, height, guiHeight);
            if (x < 0 || y < 0 || x + width > guiWidth || y + height > guiHeight) {
                throw new LuaError("GUI element at " + element.x + ", " + element.y + " is outside the layout bounds.");
            }
        }
    }

    private static int fixedWidth(ContainerGuiDefinition.Element element) {
        if (element instanceof ContainerGuiDefinition.ImageElement) return ((ContainerGuiDefinition.ImageElement) element).width;
        if (element instanceof ContainerGuiDefinition.ProgressElement) return ((ContainerGuiDefinition.ProgressElement) element).image.width;
        if (element instanceof ContainerGuiDefinition.RectangleElement) return ((ContainerGuiDefinition.RectangleElement) element).width;
        if (element instanceof ContainerGuiDefinition.TooltipElement) return ((ContainerGuiDefinition.TooltipElement) element).width;
        if (element instanceof ContainerGuiDefinition.ItemElement) return 16;
        return 0;
    }

    private static int fixedHeight(ContainerGuiDefinition.Element element) {
        if (element instanceof ContainerGuiDefinition.ImageElement) return ((ContainerGuiDefinition.ImageElement) element).height;
        if (element instanceof ContainerGuiDefinition.ProgressElement) return ((ContainerGuiDefinition.ProgressElement) element).image.height;
        if (element instanceof ContainerGuiDefinition.RectangleElement) return ((ContainerGuiDefinition.RectangleElement) element).height;
        if (element instanceof ContainerGuiDefinition.TooltipElement) return ((ContainerGuiDefinition.TooltipElement) element).height;
        if (element instanceof ContainerGuiDefinition.ItemElement) return 16;
        return 0;
    }

    private static int anchoredX(String anchor, int x, int width, int guiWidth) {
        if (anchor.endsWith("center") || "center".equals(anchor)) return (guiWidth - width) / 2 + x;
        if (anchor.endsWith("right")) return guiWidth - width + x;
        return x;
    }

    private static int anchoredY(String anchor, int y, int height, int guiHeight) {
        if ("center".equals(anchor)) return (guiHeight - height) / 2 + y;
        if (anchor.startsWith("bottom")) return guiHeight - height + y;
        return y;
    }

    private static void readStates(LuaValue definitions, Map states) {
        if (!definitions.istable()) throw new LuaError("state_image.states must be a table.");
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = definitions.next(key); key = next.arg1(); if (key.isnil()) return;
            states.put(key.tojstring(), customTexture(next.arg(2).checkjstring()));
        }
    }

    private static ContainerGuiDefinition.Condition parseCondition(LuaValue value, TileEntityDefinition tile) {
        if (value.isnil()) return null;
        if (!value.istable()) throw new LuaError("visibleWhen must be a condition table.");
        LuaValue all = value.get("all"), any = value.get("any");
        if (!all.isnil() || !any.isnil()) {
            if (!all.isnil() && !any.isnil()) throw new LuaError("A condition cannot contain both all and any.");
            LuaValue childrenValue = all.isnil() ? any : all;
            if (!childrenValue.istable() || childrenValue.length() == 0) throw new LuaError("Condition groups cannot be empty.");
            List children = new ArrayList();
            for (int i = 1; i <= childrenValue.length(); i++) children.add(parseCondition(childrenValue.get(i), tile));
            return new ContainerGuiDefinition.Condition(null, all.isnil() ? "any" : "all", null, children);
        }
        String field = requiredString(value, "field"); requireGuiField(tile, field);
        String[] names = { "equals", "notEquals", "greaterThan", "greaterOrEqual", "lessThan", "lessOrEqual" };
        String found = null; Object expected = null;
        for (int i = 0; i < names.length; i++) if (!value.get(names[i]).isnil()) {
            if (found != null) throw new LuaError("A condition must contain exactly one comparison.");
            found = names[i]; expected = javaValue(value.get(names[i]));
        }
        if (found == null) throw new LuaError("A condition requires a comparison.");
        if (!("equals".equals(found) || "notEquals".equals(found))) {
            TileEntityDefinition.Field definition = (TileEntityDefinition.Field) tile.fields.get(field);
            if (!(expected instanceof Number) || !"integer".equals(definition.type)) {
                throw new LuaError("Ordered GUI comparisons require a synced integer field and a number.");
            }
        }
        return new ContainerGuiDefinition.Condition(field, found, expected, null);
    }

    private static ContainerGuiDefinition.Condition combine(ContainerGuiDefinition.Condition left,
        ContainerGuiDefinition.Condition right) {
        if (left == null) return right; if (right == null) return left;
        List children = new ArrayList(); children.add(left); children.add(right);
        return new ContainerGuiDefinition.Condition(null, "all", null, children);
    }

    private static void requireGuiField(TileEntityDefinition tile, String name) {
        TileEntityDefinition.Field field = (TileEntityDefinition.Field) tile.fields.get(name);
        if (field == null) throw new LuaError("Unknown GUI data field: " + name);
        if (!field.sync) throw new LuaError("GUI data field '" + name + "' must use sync = true.");
    }

    private static ContainerGuiDefinition.Texture elementTexture(LuaValue value, String field, boolean optional) {
        LuaValue path = value.get(field);
        String builtinName = "image".equals(field) ? "builtin" : field + "Builtin";
        LuaValue builtin = value.get(builtinName);
        if (!path.isnil() && !builtin.isnil()) throw new LuaError("Use either " + field + " or " + builtinName + ".");
        if (!path.isnil()) return customTexture(path.checkjstring());
        if (!builtin.isnil()) return builtinSprite(builtin.checkjstring());
        if (optional) return null;
        throw new LuaError("GUI element requires '" + field + "' or '" + builtinName + "'.");
    }

    private static ContainerGuiDefinition.Texture customTexture(String path) {
        if (path.startsWith("/")) throw new LuaError("Use a Lua asset path or a named built-in sprite for an element image.");
        String resource = LuaTextureResources.register(path);
        int[] dimensions = LuaTextureResources.dimensions(resource);
        return new ContainerGuiDefinition.Texture(resource, 0, 0, dimensions[0], dimensions[1], dimensions[0], dimensions[1]);
    }

    private static ContainerGuiDefinition.Texture builtinBackground(String name, int rows) {
        name = name.toLowerCase();
        if ("minecraft:furnace".equals(name)) return atlas("/gui/furnace.png", 0, 0, 176, 166);
        if ("minecraft:crafting".equals(name)) return atlas("/gui/crafting.png", 0, 0, 176, 166);
        if ("minecraft:dispenser".equals(name)) return atlas("/gui/trap.png", 0, 0, 176, 166);
        if ("minecraft:inventory".equals(name)) return atlas("/gui/inventory.png", 0, 0, 176, 166);
        if ("minecraft:container".equals(name) || "minecraft:chest".equals(name))
            return atlas("/gui/container.png", 0, 0, 176, "minecraft:chest".equals(name) ? 114 + rows * 18 : 166);
        throw new LuaError("Unknown GUI preset or built-in background: " + name);
    }

    private static ContainerGuiDefinition.Texture builtinSprite(String name) {
        name = name.toLowerCase();
        if ("minecraft:furnace_flame".equals(name)) return atlas("/gui/furnace.png", 176, 0, 14, 14);
        if ("minecraft:furnace_arrow".equals(name)) return atlas("/gui/furnace.png", 176, 14, 24, 16);
        if ("minecraft:crafting_arrow".equals(name)) return atlas("/gui/crafting.png", 176, 0, 24, 17);
        if ("minecraft:slot".equals(name)) return atlas("/gui/container.png", 7, 17, 18, 18);
        if ("minecraft:output_slot".equals(name)) return atlas("/gui/crafting.png", 124, 35, 26, 26);
        throw new LuaError("Unknown built-in GUI sprite: " + name);
    }

    private static ContainerGuiDefinition.Texture atlas(String path, int u, int v, int width, int height) {
        return new ContainerGuiDefinition.Texture(path, u, v, width, height, 256, 256);
    }

    private static List tooltip(LuaValue value) {
        List lines = new ArrayList();
        if (value.isnil()) return lines;
        if (value.isstring()) { lines.add(value.checkjstring()); return lines; }
        if (!value.istable()) throw new LuaError("tooltip must be text or a list of text lines.");
        for (int i = 1; i <= value.length(); i++) lines.add(value.get(i).checkjstring());
        return lines;
    }

    private static String alignment(String value) {
        if ("left".equals(value) || "center".equals(value) || "right".equals(value)) return value;
        throw new LuaError("Text alignment must be left, center, or right.");
    }

    private static String anchor(String value) {
        String[] valid = { "top_left", "top_center", "top_right", "center", "bottom_left", "bottom_center", "bottom_right" };
        for (int i = 0; i < valid.length; i++) if (valid[i].equals(value)) return value;
        throw new LuaError("Unknown GUI anchor: " + value);
    }

    private static int layer(String value) {
        if ("background".equals(value)) return 0; if ("content".equals(value)) return 1;
        if ("foreground".equals(value)) return 2; throw new LuaError("GUI layer must be background, content, or foreground.");
    }

    private static String direction(String value) {
        if ("left_to_right".equals(value) || "right_to_left".equals(value)
            || "top_to_bottom".equals(value) || "bottom_to_top".equals(value)) return value;
        throw new LuaError("Unknown progress direction: " + value);
    }

    private static int color(LuaValue value, int fallback) {
        if (value.isnil()) return fallback;
        if (value.isnumber()) return value.checkint();
        String name = value.checkjstring().toLowerCase();
        if ("black".equals(name)) return 0x000000; if ("dark_gray".equals(name)) return 0x404040;
        if ("gray".equals(name)) return 0x808080; if ("white".equals(name)) return 0xFFFFFF;
        if ("red".equals(name)) return 0xFF5555; if ("dark_red".equals(name)) return 0xAA0000;
        if ("green".equals(name)) return 0x55FF55; if ("dark_green".equals(name)) return 0x00AA00;
        if ("yellow".equals(name)) return 0xFFFF55; if ("gold".equals(name)) return 0xFFAA00;
        if ("blue".equals(name)) return 0x5555FF; if ("aqua".equals(name)) return 0x55FFFF;
        throw new LuaError("Unknown GUI color: " + name);
    }

    private static Object javaValue(LuaValue value) {
        if (value.isboolean()) return Boolean.valueOf(value.checkboolean());
        if (value.isnumber()) return Double.valueOf(value.checkdouble());
        if (value.isstring()) return value.checkjstring();
        throw new LuaError("Condition values must be numbers, booleans, or strings.");
    }

    private static void positive(int width, int height, String type) {
        if (width <= 0 || height <= 0) throw new LuaError(type + " dimensions must be positive.");
    }
    private static LuaValue required(LuaValue table, String key) {
        LuaValue value = table.get(key); if (value.isnil()) throw new LuaError("Definition requires '" + key + "'."); return value;
    }
    private static LuaValue requiredTable(LuaValue table, String key) {
        LuaValue value = required(table, key); if (!value.istable()) throw new LuaError(key + " must be a table."); return value;
    }
    private static String requiredString(LuaValue table, String key) { return required(table, key).checkjstring(); }
    private static int requiredInt(LuaValue table, String key) { return required(table, key).checkint(); }
}
