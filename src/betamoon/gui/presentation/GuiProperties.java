package betamoon.gui.presentation;

import betamoon.gui.animation.GuiAnimationValueError;
import betamoon.gui.animation.GuiEasing;
import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.luaapi.client.ui.GuiPropertyValues;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * The single capability/value contract for presentation declarations and
 * updates.
 */
public final class GuiProperties {
    private static final Set<String> COLORS = new LinkedHashSet<>(
            Arrays.asList("color", "tint", "iconTint", "backgroundTint"));
    private static final Map<String, Integer> NAMED_COLORS = namedColors();
    private final String type;
    private final Map<String, Object> baseline = new LinkedHashMap<>();
    private final Set<String> animatable = new LinkedHashSet<>();
    private final Map<String, Double> minimums = new LinkedHashMap<>();
    private final Map<String, Double> maximums = new LinkedHashMap<>();
    private final Map<String, GuiPropertyDescriptor> descriptors = new LinkedHashMap<>();

    /**
     * New retained documents use Java descriptors, without constructing Lua
     * declarations.
     */
    public GuiProperties(String type, Iterable<GuiPropertyDescriptor> properties) {
        this.type = type;
        for (GuiPropertyDescriptor descriptor : properties) {
            if (descriptors.put(descriptor.name, descriptor) != null) {
                throw new IllegalArgumentException("Duplicate GUI property: " + descriptor.name);
            }
            baseline.put(descriptor.name, descriptor.defaultValue);
            if (descriptor.animated) {
                animatable.add(descriptor.name);
            }
            if (descriptor.kind == GuiPropertyDescriptor.Kind.NUMBER
                    || descriptor.kind == GuiPropertyDescriptor.Kind.INTEGER) {
                minimums.put(descriptor.name, descriptor.minimum);
                maximums.put(descriptor.name, descriptor.maximum);
            }
        }
        if (descriptors.isEmpty()) {
            throw new IllegalArgumentException("A retained property catalog cannot be empty");
        }
    }

    public Map<String, GuiPropertyDescriptor> descriptors() {
        return Collections.unmodifiableMap(descriptors);
    }

    /**
     * Static Java declarations use exactly the same normalization as parsed values.
     */
    public GuiProperties withDefaults(Map<String, Object> values) {
        if (descriptors.isEmpty()) {
            throw new IllegalStateException("Static defaults require retained descriptors");
        }
        GuiProperties result = new GuiProperties(type, descriptors.values());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            GuiPropertyDescriptor property = descriptors.get(entry.getKey());
            if (property == null) {
                throw new IllegalArgumentException("Unsupported " + type + " property: " + entry.getKey());
            }
            result.baseline.put(entry.getKey(), property.normalize(entry.getValue()));
        }
        return result;
    }

    public GuiProperties(String type, GuiDocumentDefinition.Element element, LuaValue declaration) {
        this.type = type;
        numeric("x", declaration.get("x").optint(0), -Double.MAX_VALUE, Double.MAX_VALUE);
        numeric("y", declaration.get("y").optint(0), -Double.MAX_VALUE, Double.MAX_VALUE);
        numeric("translateX", 0, -Double.MAX_VALUE, Double.MAX_VALUE);
        numeric("translateY", 0, -Double.MAX_VALUE, Double.MAX_VALUE);
        numeric("scaleX", 1, 0, Double.MAX_VALUE);
        numeric("scaleY", 1, 0, Double.MAX_VALUE);
        numeric("originX", "group".equals(type) ? 0 : 0.5, 0, 1);
        numeric("originY", "group".equals(type) ? 0 : 0.5, 0, 1);
        baseline.put("visible", Boolean.TRUE);
        if (!("item".equals(type) || "tooltip".equals(type))) {
            numeric("opacity", 1, 0, 1);
        }
        if ("group".equals(type)) {
            if (!declaration.get("width").isnil() || !declaration.get("height").isnil()) {
                size("width", requiredDimension(declaration, "width"), 1, Double.MAX_VALUE);
                size("height", requiredDimension(declaration, "height"), 1, Double.MAX_VALUE);
                baseline.put("clip", Boolean.FALSE);
            }
            artwork("tint", 0xFFFFFF);
        } else if (element instanceof GuiDocumentDefinition.PanelElement) {
            GuiDocumentDefinition.PanelElement panel = (GuiDocumentDefinition.PanelElement) element;
            size("width", panel.width, 9, 256);
            size("height", panel.height, 9, 256);
            artwork("tint", panel.color);
        } else if (element instanceof GuiDocumentDefinition.ImageElement) {
            GuiDocumentDefinition.ImageElement image = (GuiDocumentDefinition.ImageElement) element;
            boolean slot = "slot".equals(type);
            size("width", image.width - (slot ? 2 : 0), 1, slot ? 254 : Double.MAX_VALUE);
            size("height", image.height - (slot ? 2 : 0), 1, slot ? 254 : Double.MAX_VALUE);
            artwork("tint", 0xFFFFFF);
        } else if (element instanceof GuiDocumentDefinition.ProgressElement) {
            GuiDocumentDefinition.ProgressElement progress = (GuiDocumentDefinition.ProgressElement) element;
            double maxWidth = progress.background == null
                    ? Integer.MAX_VALUE
                    : Math.floor(
                            Integer.MAX_VALUE * Math.min(1, progress.image.width / (double) progress.background.width));
            double maxHeight = progress.background == null
                    ? Integer.MAX_VALUE
                    : Math.floor(Integer.MAX_VALUE
                            * Math.min(1, progress.image.height / (double) progress.background.height));
            size("width", progress.image.width, 1, maxWidth);
            size("height", progress.image.height, 1, maxHeight);
            artwork("tint", 0xFFFFFF);
            if (progress.background != null) {
                artwork("backgroundTint", null);
            }
        } else if (element instanceof GuiDocumentDefinition.StateImageElement) {
            size("width", null, 1, Double.MAX_VALUE);
            size("height", null, 1, Double.MAX_VALUE);
            artwork("tint", 0xFFFFFF);
        } else if (element instanceof GuiDocumentDefinition.TextElement) {
            GuiDocumentDefinition.TextElement text = (GuiDocumentDefinition.TextElement) element;
            size("width", text.width, 0, Double.MAX_VALUE);
            baseline.put("text", text.text);
            artwork("color", text.color);
            baseline.put("align", text.align);
            baseline.put("shadow", text.shadow);
        } else if (element instanceof GuiDocumentDefinition.RectangleElement) {
            GuiDocumentDefinition.RectangleElement rectangle = (GuiDocumentDefinition.RectangleElement) element;
            size("width", rectangle.width, 1, Double.MAX_VALUE);
            size("height", rectangle.height, 1, Double.MAX_VALUE);
            artwork("color", rectangle.color);
        } else if (element instanceof GuiDocumentDefinition.TooltipElement) {
            GuiDocumentDefinition.TooltipElement tooltip = (GuiDocumentDefinition.TooltipElement) element;
            size("width", tooltip.width, 1, Double.MAX_VALUE);
            size("height", tooltip.height, 1, Double.MAX_VALUE);
        } else if (element instanceof GuiDocumentDefinition.ControlElement) {
            control((GuiDocumentDefinition.ControlElement) element);
        }
        for (String name : Arrays.asList("translateX", "translateY", "scaleX", "scaleY", "originX", "originY",
                "opacity", "tint", "backgroundTint", "iconTint", "clip", "width", "height")) {
            if (!declaration.get(name).isnil() && !supports(name)) {
                throw new LuaError("Unsupported " + type + " presentation property: " + name);
            }
        }
        for (String name : new LinkedHashSet<>(baseline.keySet())) {
            LuaValue value = declaration.get(name);
            if (!value.isnil()) {
                if (Arrays.asList("x", "y", "width", "height").contains(name)) {
                    double number = value.type() == LuaValue.TNUMBER ? value.todouble() : Double.NaN;
                    if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE
                            || number > Integer.MAX_VALUE) {
                        throw new LuaError("Static " + name + " requires an integer in the renderer's range.");
                    }
                }
                baseline.put(name, read(name, value));
            }
        }
        validateOrigins(baseline);
    }

    private void control(GuiDocumentDefinition.ControlElement control) {
        double minWidth = 1;
        double minHeight = 1;
        if (control.presentation == GuiDocumentDefinition.ControlElement.Presentation.CHECKBOX) {
            minWidth = 12;
            minHeight = 12;
        } else if (control.presentation == GuiDocumentDefinition.ControlElement.Presentation.CHOICE) {
            minWidth = 32;
            minHeight = 12;
        } else if (control.presentation == GuiDocumentDefinition.ControlElement.Presentation.SLIDER) {
            boolean vertical = "vertical".equals(control.orientation);
            minWidth = vertical ? 12 : 8;
            minHeight = vertical ? 8 : 12;
        }
        size("width", control.width, minWidth, Double.MAX_VALUE);
        size("height", control.height, minHeight, Double.MAX_VALUE);
        if (!control.visuals.isEmpty() || !control.styleTextures.isEmpty() || control.icon != null) {
            artwork("tint", 0xFFFFFF);
        }
        if (control.icon != null) {
            artwork("iconTint", null);
        }
        if (control.presentation != GuiDocumentDefinition.ControlElement.Presentation.SLIDER
                && control.presentation != GuiDocumentDefinition.ControlElement.Presentation.TEXT_BOX) {
            baseline.put("text", control.text);
        }
    }

    public Map<String, Object> baseline() {
        return Collections.unmodifiableMap(baseline);
    }

    public Set<String> animatable() {
        return Collections.unmodifiableSet(animatable);
    }

    public String canonical(String name) {
        return descriptors.isEmpty() && "panel".equals(type) && "color".equals(name) ? "tint" : name;
    }

    public GuiTransitionPolicy transition(LuaValue value) {
        if (!descriptors.isEmpty() || !"panel".equals(type) || !value.istable() || value.get("color").isnil()) {
            return GuiTransitionPolicy.parse(value, animatable());
        }
        LuaTable copy = new LuaTable();
        for (LuaValue key : value.checktable().keys()) {
            if (!key.equals(LuaValue.valueOf("color"))) {
                copy.set(key, value.get(key));
            }
        }
        if (value.get("tint").isnil()) {
            copy.set("tint", value.get("color"));
        }
        return GuiTransitionPolicy.parse(copy, animatable());
    }

    public boolean supports(String name) {
        return baseline.containsKey(canonical(name));
    }

    public Map<String, Object> read(LuaValue values) {
        if (!values.istable()) {
            throw new LuaError("bind/command result must be a property table.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (LuaValue key : values.checktable().keys()) {
            if (key.type() != LuaValue.TSTRING || !supports(key.tojstring())) {
                throw new LuaError("Unsupported GUI property: " + key.tojstring());
            }
        }
        // Catalog order is stable and determines request property ordering.
        for (String name : baseline.keySet()) {
            LuaValue value = values.get(name);
            if (descriptors.isEmpty() && "panel".equals(type) && "tint".equals(name) && value.isnil()) {
                value = values.get("color");
            }
            if (!value.isnil()) {
                result.put(name, read(name, value));
            }
        }
        validateOrigins(result);
        return result;
    }

    public Object read(String name, LuaValue value) {
        name = canonical(name);
        if (!supports(name)) {
            throw new LuaError("Unsupported GUI property: " + name);
        }
        if (!descriptors.isEmpty()) {
            return GuiPropertyValues.read(descriptors.get(name), value);
        }
        if ("color".equals(name) && value.type() == LuaValue.TNUMBER) {
            double number = value.todouble();
            if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE
                    || number > 0xFFFFFFFFL) {
                throw new ValueError("color requires an RGB/ARGB integer or supported named color.");
            }
            return Integer.valueOf((int) (long) number);
        }
        if (COLORS.contains(name)) {
            return Integer.valueOf(color(value));
        }
        if (minimums.containsKey(name)) {
            if (value.type() != LuaValue.TNUMBER || !Double.isFinite(value.todouble())) {
                throw new ValueError(name + " requires a finite number, not a numeric string.");
            }
            double number = value.todouble();
            if (number < minimums.get(name) || number > maximums.get(name)) {
                throw new ValueError(name + " must be in " + minimums.get(name) + ".." + maximums.get(name));
            }
            return Double.valueOf(number == 0 ? 0 : number);
        }
        if ("text".equals(name) || "align".equals(name)) {
            if (value.type() != LuaValue.TSTRING) {
                throw new ValueError(name + " must be a string.");
            }
            String text = value.tojstring();
            if ("align".equals(name) && !("left".equals(text) || "center".equals(text) || "right".equals(text))) {
                throw new ValueError("align must be left, center or right.");
            }
            return text;
        }
        if (!value.isboolean()) {
            throw new ValueError(name + " must be boolean.");
        }
        return Boolean.valueOf(value.toboolean());
    }

    public void validateOrigins(Map<String, Object> values) {
        if (descriptors.isEmpty() && "group".equals(type) && !baseline.containsKey("width")) {
            for (String name : Arrays.asList("originX", "originY")) {
                if (values.containsKey(name) && ((Number) values.get(name)).doubleValue() != 0) {
                    throw new ValueError("Dimensionless groups require originX/originY = 0.");
                }
            }
        }
    }

    public boolean affectsGeometry(String property) {
        if (!descriptors.isEmpty()) {
            Set<GuiPropertyDescriptor.Stage> stages = descriptors.get(property).stages;
            return stages.contains(GuiPropertyDescriptor.Stage.MEASURE)
                    || stages.contains(GuiPropertyDescriptor.Stage.ARRANGE)
                    || stages.contains(GuiPropertyDescriptor.Stage.HIT);
        }
        return !(COLORS.contains(property) || "shadow".equals(property));
    }

    public Object interpolate(String property, Object from, Object to, double p) {
        if (!descriptors.isEmpty()) {
            return descriptors.get(property).interpolate(from, to, p);
        }
        if (COLORS.contains(property)) {
            int a = ((Number) from).intValue();
            int b = ((Number) to).intValue();
            int result = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                double channel = (a >> shift & 255) + ((b >> shift & 255) - (a >> shift & 255)) * p;
                result |= (int) Math.round(Math.max(0, Math.min(255, channel))) << shift;
            }
            if ("color".equals(property) && ((a | b) >>> 24) != 0) {
                int alphaA = a >>> 24 == 0 ? 255 : a >>> 24;
                int alphaB = b >>> 24 == 0 ? 255 : b >>> 24;
                double alpha = alphaA + (alphaB - alphaA) * p;
                result |= (int) Math.round(Math.max(1, Math.min(255, alpha))) << 24;
            }
            return Integer.valueOf(result);
        }
        double a = ((Number) from).doubleValue();
        double b = ((Number) to).doubleValue();
        double difference = b - a;
        double value = Double.isFinite(difference) ? a + difference * p : a * (1 - p) + b * p;
        if (Double.isNaN(value)) {
            value = b >= a ? maximums.get(property) : minimums.get(property);
        }
        return Double.valueOf(Math.max(minimums.get(property), Math.min(maximums.get(property), value)));
    }

    private void numeric(String name, double value, double min, double max) {
        size(name, Double.valueOf(value), min, max);
    }

    private void size(String name, Number value, double min, double max) {
        baseline.put(name, value == null ? null : Double.valueOf(value.doubleValue()));
        animatable.add(name);
        minimums.put(name, min);
        maximums.put(name, "width".equals(name) || "height".equals(name) ? Math.min(max, Integer.MAX_VALUE) : max);
    }

    private void artwork(String name, Integer value) {
        baseline.put(name, value);
        animatable.add(name);
    }

    private static int requiredDimension(LuaValue declaration, String name) {
        double value = GuiEasing.number(declaration.get(name));
        if (value < 1 || value != Math.rint(value) || value > Integer.MAX_VALUE) {
            throw new LuaError("Group " + name + " requires a positive integer.");
        }
        return (int) value;
    }

    public static int color(LuaValue value) {
        if (value.type() == LuaValue.TSTRING && NAMED_COLORS.containsKey(value.tojstring().toLowerCase(Locale.ROOT))) {
            return NAMED_COLORS.get(value.tojstring().toLowerCase(Locale.ROOT));
        }
        double number = value.type() == LuaValue.TNUMBER ? value.todouble() : Double.NaN;
        if (!Double.isFinite(number) || number != Math.rint(number) || number < 0 || number > 0xFFFFFF) {
            throw new ValueError("GUI color requires an RGB integer in 0..0xFFFFFF or a supported named color.");
        }
        return (int) number;
    }

    private static Map<String, Integer> namedColors() {
        Map<String, Integer> values = new LinkedHashMap<>();
        String[] names = {"black", "dark_gray", "gray", "white", "red", "dark_red", "green", "dark_green", "yellow",
                "gold", "blue", "aqua"};
        int[] colors = {0, 0x555555, 0xAAAAAA, 0xFFFFFF, 0xFF5555, 0xAA0000, 0x55FF55, 0x00AA00, 0xFFFF55, 0xFFAA00,
                0x5555FF, 0x55FFFF};
        for (int i = 0; i < names.length; i++) {
            values.put(names[i], colors[i]);
        }
        return values;
    }

    /**
     * Dynamic value failures are recoverable; malformed property contracts are not.
     */
    public static final class ValueError extends GuiAnimationValueError {
        public ValueError(String message) {
            super(message);
        }
    }
}
