package betamoon.luaapi.tileentity;

import betamoon.data.DataField;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.resources.LuaTextureResources;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.GuiAnchor;
import betamoon.tileentity.GuiConditionOperator;
import betamoon.tileentity.ProgressDirection;
import betamoon.tileentity.TileEntityDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
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
        if (!value.get("image").isnil()) {
            texture = customTexture(value.get("image"));
        } else if (builtinName != null) {
            texture = builtinBackground(builtinName, rows);
        } else if (style == null) {
            texture = builtinBackground(preset, rows);
        }
        if (style == null && ("minecraft:chest".equals(preset) || "minecraft:chest".equalsIgnoreCase(builtinName))) {
            style = "chest";
        }
        if (style != null && !"minecraft".equals(style) && !"chest".equals(style)) {
            throw new LuaError("background.style must be 'minecraft'.");
        }
        return new ContainerGuiDefinition.Background(texture, style, value.get("drawSlotFrames").toboolean(), rows);
    }

    static ContainerGuiDefinition.Label parseLabel(LuaValue value, String defaultText, int defaultX, int defaultY,
            int defaultWidth) {
        if (value.isboolean() && !value.toboolean()) {
            return null;
        }
        if (value.isnil()) {
            return label(defaultText, defaultX, defaultY, defaultWidth, 0x404040, "left", false);
        }
        if (value.isstring()) {
            return label(value.checkjstring(), defaultX, defaultY, defaultWidth, 0x404040, "left", false);
        }
        if (!value.istable()) {
            throw new LuaError("GUI label must be text, a table, or false.");
        }
        return label(value.get("text").optjstring(defaultText), value.get("x").optint(defaultX),
                value.get("y").optint(defaultY), value.get("width").optint(defaultWidth),
                color(value.get("color"), 0x404040), alignment(value.get("align").optjstring("left")),
                value.get("shadow").toboolean());
    }

    private static ContainerGuiDefinition.Label label(String text, int x, int y, int width, int color, String align,
            boolean shadow) {
        return new ContainerGuiDefinition.Label(text, x, y, width, color, align, shadow);
    }

    static void parseElements(LuaValue definitions, ContainerDefinition container,
            List<ContainerGuiDefinition.Element> output, int offsetX, int offsetY,
            ContainerGuiDefinition.Condition inherited) {
        TileEntityDefinition tile = container.tileEntity;
        if (!definitions.istable()) {
            throw new LuaError("elements must be a list.");
        }
        for (int i = 1; i <= definitions.length(); i++) {
            LuaValue value = definitions.get(i);
            if (!value.istable()) {
                throw new LuaError("GUI elements must be definition tables.");
            }
            String type = requiredString(value, "type").toLowerCase();
            int x = value.get("x").optint(0) + offsetX;
            int y = value.get("y").optint(0) + offsetY;
            ContainerGuiDefinition.Condition condition = combine(inherited,
                    parseCondition(value.get("visibleWhen"), container));
            if ("group".equals(type)) {
                parseElements(requiredTable(value, "elements"), container, output, x, y, condition);
                continue;
            }
            GuiAnchor anchor = anchor(value.get("anchor").optjstring("top_left"));
            int layer = layer(value.get("layer").optjstring("content"));
            List<String> tooltip = tooltip(value.get("tooltip"));
            if ("image".equals(type)) {
                ContainerGuiDefinition.Texture image = elementTexture(value, "image", false);
                int width = value.get("width").optint(image.width);
                int height = value.get("height").optint(image.height);
                positive(width, height, "Image");
                output.add(new ContainerGuiDefinition.ImageElement(x, y, layer, anchor, condition, tooltip, image,
                        width, height));
            } else if ("text".equals(type)) {
                String field = value.get("value").optjstring(null);
                if (field != null) {
                    requireGuiField(tile, field);
                }
                if (field == null && value.get("text").isnil()) {
                    throw new LuaError("Text element requires 'text' or 'value'.");
                }
                output.add(new ContainerGuiDefinition.TextElement(x, y, layer, anchor, condition, tooltip,
                        value.get("text").optjstring(null), field, value.get("format").optjstring(null),
                        alignment(value.get("align").optjstring("left")), value.get("width").optint(0),
                        color(value.get("color"), 0x404040), value.get("shadow").toboolean()));
            } else if ("progress".equals(type)) {
                String field = requiredString(value, "value");
                requireGuiField(tile, field);
                LuaValue maxValue = required(value, "maximum");
                int maximum = maxValue.isnumber() ? maxValue.checkint() : 0;
                // LuaJ numbers also satisfy isstring(); distinguish actual field names from
                // constants.
                String maximumField = maxValue.type() == LuaValue.TSTRING ? maxValue.checkjstring() : null;
                if (maximumField != null) {
                    requireGuiField(tile, maximumField);
                }
                if (maximumField == null && maximum <= 0) {
                    throw new LuaError("Progress maximum must be positive.");
                }
                output.add(new ContainerGuiDefinition.ProgressElement(x, y, layer, anchor, condition, tooltip, field,
                        maximumField, maximum, direction(value.get("direction").optjstring("left_to_right")),
                        Math.max(0, value.get("minimumPixels").optint(0)), value.get("hideWhenEmpty").toboolean(),
                        elementTexture(value, "image", false), elementTexture(value, "background", true)));
            } else if ("state_image".equals(type)) {
                String field = requiredString(value, "value");
                requireGuiField(tile, field);
                Map<String, ContainerGuiDefinition.Texture> states = new HashMap<>();
                LuaValue stateDefs = value.get("states");
                if (!stateDefs.isnil()) {
                    readStates(stateDefs, states);
                }
                if (!value.get("whenTrue").isnil()) {
                    states.put("true", customTexture(value.get("whenTrue")));
                }
                if (!value.get("whenFalse").isnil()) {
                    states.put("false", customTexture(value.get("whenFalse")));
                }
                if (states.isEmpty()) {
                    throw new LuaError("state_image requires states or whenTrue/whenFalse.");
                }
                ContainerGuiDefinition.Texture fallback = value.get("default").isnil()
                        ? null
                        : customTexture(value.get("default"));
                output.add(new ContainerGuiDefinition.StateImageElement(x, y, layer, anchor, condition, tooltip, field,
                        states, fallback));
            } else if ("rectangle".equals(type)) {
                int width = requiredInt(value, "width");
                int height = requiredInt(value, "height");
                positive(width, height, "Rectangle");
                output.add(new ContainerGuiDefinition.RectangleElement(x, y, layer, anchor, condition, tooltip, width,
                        height, color(required(value, "color"), 0)));
            } else if ("tooltip".equals(type)) {
                if (tooltip.isEmpty()) {
                    tooltip = tooltip(required(value, "text"));
                }
                int width = requiredInt(value, "width");
                int height = requiredInt(value, "height");
                positive(width, height, "Tooltip");
                output.add(new ContainerGuiDefinition.TooltipElement(x, y, layer, anchor, condition, tooltip, width,
                        height));
            } else if ("item".equals(type)) {
                String slot = value.get("slot").optjstring(null);
                ItemStack item = null;
                if (slot != null && !tile.slots.containsKey(slot)) {
                    throw new LuaError("Unknown item element slot: " + slot);
                }
                if (!value.get("item").isnil()) {
                    item = LuaApiUtils.readItemStack(value.get("item"), true, "GUI item");
                }
                if ((slot == null) == (item == null)) {
                    throw new LuaError("Item element requires either 'slot' or 'item'.");
                }
                output.add(new ContainerGuiDefinition.ItemElement(x, y, layer, anchor, condition, tooltip, slot, item,
                        value.get("showCount").optboolean(true)));
            } else if ("button".equals(type) || "toggle".equals(type) || "slider".equals(type)
                    || "text_box".equals(type) || "choice".equals(type) || "interactive".equals(type)) {
                String controlName = requiredString(value, "control");
                betamoon.tileentity.ContainerControlDefinition control = container.controls.get(controlName);
                if (control == null) {
                    throw new LuaError("Unknown container control: " + controlName);
                }
                betamoon.tileentity.ContainerControlDefinition.Type expected = controlType(type);
                if (control.type != expected) {
                    throw new LuaError("GUI element type '" + type + "' is incompatible with control '"
                            + controlName + "'.");
                }
                int width = value.get("width").optint("toggle".equals(type) ? 20 : 100);
                int height = value.get("height").optint(20);
                positive(width, height, "Interactive element");
                String orientation = value.get("orientation").optjstring("horizontal").toLowerCase();
                if (!("horizontal".equals(orientation) || "vertical".equals(orientation))) {
                    throw new LuaError("Interactive element orientation must be horizontal or vertical.");
                }
                Map<String, ContainerGuiDefinition.Texture> visuals = new HashMap<>();
                LuaValue visualDefinitions = value.get("visuals");
                if (!visualDefinitions.isnil()) {
                    readControlStates(visualDefinitions, visuals);
                }
                output.add(new ContainerGuiDefinition.ControlElement(x, y, layer, anchor, condition, tooltip,
                        controlName, control.type, width, height, value.get("text").optjstring(null), orientation,
                        value.get("focusable").optboolean(true),
                        value.get("captureDrag").optboolean("slider".equals(type) || "interactive".equals(type)),
                        visuals));
            } else {
                throw new LuaError("Unsupported GUI element type: " + type);
            }
        }
    }

    /** Rejects fixed-size elements that cannot appear inside the GUI canvas. */
    static void validateBounds(List<ContainerGuiDefinition.Element> elements, int guiWidth, int guiHeight) {
        for (int i = 0; i < elements.size(); i++) {
            ContainerGuiDefinition.Element element = elements.get(i);
            int width = fixedWidth(element);
            int height = fixedHeight(element);
            if (width <= 0 || height <= 0) {
                continue;
            }
            int x = anchoredX(element.anchor, element.x, width, guiWidth);
            int y = anchoredY(element.anchor, element.y, height, guiHeight);
            if (x < 0 || y < 0 || x + width > guiWidth || y + height > guiHeight) {
                throw new LuaError("GUI element at " + element.x + ", " + element.y + " is outside the layout bounds.");
            }
            if (element instanceof ContainerGuiDefinition.ControlElement) {
                validateInteractiveOverlap(elements, i, x, y, width, height, guiWidth, guiHeight);
            }
        }
    }

    private static void validateInteractiveOverlap(List<ContainerGuiDefinition.Element> elements, int index,
            int x, int y, int width, int height, int guiWidth, int guiHeight) {
        ContainerGuiDefinition.Element current = elements.get(index);
        for (int previousIndex = 0; previousIndex < index; previousIndex++) {
            ContainerGuiDefinition.Element previous = elements.get(previousIndex);
            if (!(previous instanceof ContainerGuiDefinition.ControlElement) || previous.layer != current.layer) {
                continue;
            }
            int previousWidth = fixedWidth(previous);
            int previousHeight = fixedHeight(previous);
            int previousX = anchoredX(previous.anchor, previous.x, previousWidth, guiWidth);
            int previousY = anchoredY(previous.anchor, previous.y, previousHeight, guiHeight);
            if (x < previousX + previousWidth && x + width > previousX
                    && y < previousY + previousHeight && y + height > previousY) {
                throw new LuaError("Interactive GUI elements overlap in the same layer at "
                        + current.x + ", " + current.y + ".");
            }
        }
    }

    private static int fixedWidth(ContainerGuiDefinition.Element element) {
        if (element instanceof ContainerGuiDefinition.ImageElement) {
            return ((ContainerGuiDefinition.ImageElement) element).width;
        }
        if (element instanceof ContainerGuiDefinition.ProgressElement) {
            return ((ContainerGuiDefinition.ProgressElement) element).image.width;
        }
        if (element instanceof ContainerGuiDefinition.RectangleElement) {
            return ((ContainerGuiDefinition.RectangleElement) element).width;
        }
        if (element instanceof ContainerGuiDefinition.TooltipElement) {
            return ((ContainerGuiDefinition.TooltipElement) element).width;
        }
        if (element instanceof ContainerGuiDefinition.ItemElement) {
            return 16;
        }
        if (element instanceof ContainerGuiDefinition.ControlElement) {
            return ((ContainerGuiDefinition.ControlElement) element).width;
        }
        return 0;
    }

    private static int fixedHeight(ContainerGuiDefinition.Element element) {
        if (element instanceof ContainerGuiDefinition.ImageElement) {
            return ((ContainerGuiDefinition.ImageElement) element).height;
        }
        if (element instanceof ContainerGuiDefinition.ProgressElement) {
            return ((ContainerGuiDefinition.ProgressElement) element).image.height;
        }
        if (element instanceof ContainerGuiDefinition.RectangleElement) {
            return ((ContainerGuiDefinition.RectangleElement) element).height;
        }
        if (element instanceof ContainerGuiDefinition.TooltipElement) {
            return ((ContainerGuiDefinition.TooltipElement) element).height;
        }
        if (element instanceof ContainerGuiDefinition.ItemElement) {
            return 16;
        }
        if (element instanceof ContainerGuiDefinition.ControlElement) {
            return ((ContainerGuiDefinition.ControlElement) element).height;
        }
        return 0;
    }

    private static betamoon.tileentity.ContainerControlDefinition.Type controlType(String type) {
        if ("button".equals(type)) {
            return betamoon.tileentity.ContainerControlDefinition.Type.ACTION;
        }
        if ("toggle".equals(type)) {
            return betamoon.tileentity.ContainerControlDefinition.Type.TOGGLE;
        }
        if ("slider".equals(type)) {
            return betamoon.tileentity.ContainerControlDefinition.Type.NUMBER;
        }
        if ("text_box".equals(type)) {
            return betamoon.tileentity.ContainerControlDefinition.Type.TEXT;
        }
        if ("choice".equals(type)) {
            return betamoon.tileentity.ContainerControlDefinition.Type.CHOICE;
        }
        return betamoon.tileentity.ContainerControlDefinition.Type.CUSTOM;
    }

    private static int anchoredX(GuiAnchor anchor, int x, int width, int guiWidth) {
        return anchor.positionX(x, width, guiWidth);
    }

    private static int anchoredY(GuiAnchor anchor, int y, int height, int guiHeight) {
        return anchor.positionY(y, height, guiHeight);
    }

    private static void readStates(LuaValue definitions, Map<String, ContainerGuiDefinition.Texture> states) {
        if (!definitions.istable()) {
            throw new LuaError("state_image.states must be a table.");
        }
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = definitions.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return;
            }
            states.put(key.tojstring(), customTexture(next.arg(2)));
        }
    }

    private static void readControlStates(LuaValue definitions,
            Map<String, ContainerGuiDefinition.Texture> states) {
        readStates(definitions, states);
        String allowed = "normal hovered pressed focused disabled selected invalid";
        for (String state : states.keySet()) {
            if (!(" " + allowed + " ").contains(" " + state + " ")) {
                throw new LuaError("Unknown interactive control visual state: " + state);
            }
        }
    }

    private static ContainerGuiDefinition.Condition parseCondition(LuaValue value, ContainerDefinition container) {
        TileEntityDefinition tile = container.tileEntity;
        if (value.isnil()) {
            return null;
        }
        if (!value.istable()) {
            throw new LuaError("visibleWhen must be a condition table.");
        }
        LuaValue all = value.get("all");
        LuaValue any = value.get("any");
        if (!all.isnil() || !any.isnil()) {
            if (!all.isnil() && !any.isnil()) {
                throw new LuaError("A condition cannot contain both all and any.");
            }
            LuaValue childrenValue = all.isnil() ? any : all;
            if (!childrenValue.istable() || childrenValue.length() == 0) {
                throw new LuaError("Condition groups cannot be empty.");
            }
            List<ContainerGuiDefinition.Condition> children = new ArrayList<>();
            for (int i = 1; i <= childrenValue.length(); i++) {
                children.add(parseCondition(childrenValue.get(i), container));
            }
            GuiConditionOperator operator = all.isnil() ? GuiConditionOperator.ANY : GuiConditionOperator.ALL;
            return new ContainerGuiDefinition.Condition(null, operator, null, children);
        }
        String field = value.get("field").optjstring(null);
        String sessionField = value.get("session").optjstring(null);
        if ((field == null) == (sessionField == null)) {
            throw new LuaError("A condition requires exactly one of field or session.");
        }
        ContainerGuiDefinition.Condition.Source source;
        DataField.Type fieldType;
        if (sessionField != null) {
            ContainerDefinition.SessionField definition = container.session.get(sessionField);
            if (definition == null) {
                throw new LuaError("Unknown GUI session field: " + sessionField);
            }
            field = sessionField;
            fieldType = DataField.Type.valueOf(definition.type.name());
            source = ContainerGuiDefinition.Condition.Source.SESSION;
        } else {
            requireGuiField(tile, field);
            fieldType = tile.fields.get(field).schema.type;
            source = ContainerGuiDefinition.Condition.Source.DATA;
        }
        String[] names = {"equals", "notEquals", "greaterThan", "greaterOrEqual", "lessThan", "lessOrEqual"};
        String found = null;
        Object expected = null;
        for (int i = 0; i < names.length; i++) {
            if (!value.get(names[i]).isnil()) {
                if (found != null) {
                    throw new LuaError("A condition must contain exactly one comparison.");
                }
                found = names[i];
                expected = javaValue(value.get(names[i]));
            }
        }
        if (found == null) {
            throw new LuaError("A condition requires a comparison.");
        }
        GuiConditionOperator operator = GuiConditionOperator.fromLua(found);
        if (operator.isOrdered()) {
            if (!(expected instanceof Number)
                    || !(fieldType == DataField.Type.INTEGER || fieldType == DataField.Type.NUMBER)) {
                throw new LuaError("Ordered GUI comparisons require a numeric field and a number.");
            }
        }
        return new ContainerGuiDefinition.Condition(source, field, operator, expected, null);
    }

    private static ContainerGuiDefinition.Condition combine(ContainerGuiDefinition.Condition left,
            ContainerGuiDefinition.Condition right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        List<ContainerGuiDefinition.Condition> children = new ArrayList<>();
        children.add(left);
        children.add(right);
        return new ContainerGuiDefinition.Condition(null, GuiConditionOperator.ALL, null, children);
    }

    private static void requireGuiField(TileEntityDefinition tile, String name) {
        TileEntityDefinition.Field field = tile.fields.get(name);
        if (field == null) {
            throw new LuaError("Unknown GUI data field: " + name);
        }
        if (!field.sync) {
            throw new LuaError("GUI data field '" + name + "' must use sync = true.");
        }
    }

    private static ContainerGuiDefinition.Texture elementTexture(LuaValue value, String field, boolean optional) {
        LuaValue path = value.get(field);
        String builtinName = "image".equals(field) ? "builtin" : field + "Builtin";
        LuaValue builtin = value.get(builtinName);
        if (!path.isnil() && !builtin.isnil()) {
            throw new LuaError("Use either " + field + " or " + builtinName + ".");
        }
        if (!path.isnil()) {
            return customTexture(path);
        }
        if (!builtin.isnil()) {
            return builtinSprite(builtin.checkjstring());
        }
        if (optional) {
            return null;
        }
        throw new LuaError("GUI element requires '" + field + "' or '" + builtinName + "'.");
    }

    private static ContainerGuiDefinition.Texture customTexture(LuaValue path) {
        int border = 0;
        LuaValue asset = path;
        if (path.istable() && !path.get("texture").isnil()) {
            asset = path.get("texture");
            border = path.get("border").optint(0);
        }
        String resource = LuaTextureResources.register(AssetInputs.texture(asset));
        ScriptResourceTracker.track(() -> LuaTextureResources.release(resource));
        int[] dimensions = LuaTextureResources.dimensions(resource);
        if (border < 0 || border > 0 && border * 2 >= Math.min(dimensions[0], dimensions[1])) {
            throw new LuaError("Nine-slice border must fit inside half of the texture dimensions.");
        }
        return new ContainerGuiDefinition.Texture(resource, 0, 0, dimensions[0], dimensions[1], dimensions[0],
                dimensions[1], border);
    }

    private static ContainerGuiDefinition.Texture builtinBackground(String name, int rows) {
        name = name.toLowerCase();
        if ("minecraft:furnace".equals(name)) {
            return atlas("/gui/furnace.png", 0, 0, 176, 166);
        }
        if ("minecraft:crafting".equals(name)) {
            return atlas("/gui/crafting.png", 0, 0, 176, 166);
        }
        if ("minecraft:dispenser".equals(name)) {
            return atlas("/gui/trap.png", 0, 0, 176, 166);
        }
        if ("minecraft:inventory".equals(name)) {
            return atlas("/gui/inventory.png", 0, 0, 176, 166);
        }
        if ("minecraft:container".equals(name) || "minecraft:chest".equals(name)) {
            return atlas("/gui/container.png", 0, 0, 176, "minecraft:chest".equals(name) ? 114 + rows * 18 : 166);
        }
        throw new LuaError("Unknown GUI preset or built-in background: " + name);
    }

    private static ContainerGuiDefinition.Texture builtinSprite(String name) {
        name = name.toLowerCase();
        if ("minecraft:furnace_flame".equals(name)) {
            return atlas("/gui/furnace.png", 176, 0, 14, 14);
        }
        if ("minecraft:furnace_arrow".equals(name)) {
            return atlas("/gui/furnace.png", 176, 14, 24, 16);
        }
        if ("minecraft:crafting_arrow".equals(name)) {
            return atlas("/gui/crafting.png", 89, 34, 24, 17);
        }
        if ("minecraft:slot".equals(name)) {
            return atlas("/gui/container.png", 7, 17, 18, 18);
        }
        if ("minecraft:output_slot".equals(name)) {
            return atlas("/gui/crafting.png", 119, 30, 26, 26);
        }
        throw new LuaError("Unknown built-in GUI sprite: " + name);
    }

    private static ContainerGuiDefinition.Texture atlas(String path, int u, int v, int width, int height) {
        return new ContainerGuiDefinition.Texture(path, u, v, width, height, 256, 256);
    }

    private static List<String> tooltip(LuaValue value) {
        List<String> lines = new ArrayList<>();
        if (value.isnil()) {
            return lines;
        }
        if (value.isstring()) {
            lines.add(value.checkjstring());
            return lines;
        }
        if (!value.istable()) {
            throw new LuaError("tooltip must be text or a list of text lines.");
        }
        for (int i = 1; i <= value.length(); i++) {
            lines.add(value.get(i).checkjstring());
        }
        return lines;
    }

    private static String alignment(String value) {
        if ("left".equals(value) || "center".equals(value) || "right".equals(value)) {
            return value;
        }
        throw new LuaError("Text alignment must be left, center, or right.");
    }

    private static GuiAnchor anchor(String value) {
        GuiAnchor anchor = GuiAnchor.fromLua(value);
        if (anchor != null) {
            return anchor;
        }
        throw new LuaError("Unknown GUI anchor: " + value);
    }

    private static int layer(String value) {
        if ("background".equals(value)) {
            return 0;
        }
        if ("content".equals(value)) {
            return 1;
        }
        if ("foreground".equals(value)) {
            return 2;
        }
        throw new LuaError("GUI layer must be background, content, or foreground.");
    }

    private static ProgressDirection direction(String value) {
        ProgressDirection direction = ProgressDirection.fromLua(value);
        if (direction != null) {
            return direction;
        }
        throw new LuaError("Unknown progress direction: " + value);
    }

    private static int color(LuaValue value, int fallback) {
        if (value.isnil()) {
            return fallback;
        }
        if (value.isnumber()) {
            return value.checkint();
        }
        String name = value.checkjstring().toLowerCase();
        if ("black".equals(name)) {
            return 0x000000;
        }
        if ("dark_gray".equals(name)) {
            return 0x404040;
        }
        if ("gray".equals(name)) {
            return 0x808080;
        }
        if ("white".equals(name)) {
            return 0xFFFFFF;
        }
        if ("red".equals(name)) {
            return 0xFF5555;
        }
        if ("dark_red".equals(name)) {
            return 0xAA0000;
        }
        if ("green".equals(name)) {
            return 0x55FF55;
        }
        if ("dark_green".equals(name)) {
            return 0x00AA00;
        }
        if ("yellow".equals(name)) {
            return 0xFFFF55;
        }
        if ("gold".equals(name)) {
            return 0xFFAA00;
        }
        if ("blue".equals(name)) {
            return 0x5555FF;
        }
        if ("aqua".equals(name)) {
            return 0x55FFFF;
        }
        throw new LuaError("Unknown GUI color: " + name);
    }

    private static Object javaValue(LuaValue value) {
        if (value.isboolean()) {
            return Boolean.valueOf(value.checkboolean());
        }
        if (value.isnumber()) {
            return Double.valueOf(value.checkdouble());
        }
        if (value.isstring()) {
            return value.checkjstring();
        }
        throw new LuaError("Condition values must be numbers, booleans, or strings.");
    }

    private static void positive(int width, int height, String type) {
        if (width <= 0 || height <= 0) {
            throw new LuaError(type + " dimensions must be positive.");
        }
    }

    private static LuaValue required(LuaValue table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) {
            throw new LuaError("Definition requires '" + key + "'.");
        }
        return value;
    }

    private static LuaValue requiredTable(LuaValue table, String key) {
        LuaValue value = required(table, key);
        if (!value.istable()) {
            throw new LuaError(key + " must be a table.");
        }
        return value;
    }

    private static String requiredString(LuaValue table, String key) {
        return required(table, key).checkjstring();
    }

    private static int requiredInt(LuaValue table, String key) {
        return required(table, key).checkint();
    }
}
