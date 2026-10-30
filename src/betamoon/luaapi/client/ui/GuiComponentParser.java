package betamoon.luaapi.client.ui;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.gui.presentation.GuiPropertyDescriptor.Stage;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * Declarative component/schema parsing has no host, native root or
 * rendering-resource access.
 */
public final class GuiComponentParser {
    public static GuiComponentDefinition parse(String owner, LuaValue value) {
        String path = "ui.component";
        LuaDeclarationValues.fields(value, path, "key", "props", "state", "events", "slots", "exports", "requires",
                "dependencies", "build", "paint", "rawDraw", "hit", "input", "focusable", "label", "size");
        String key = LuaDeclarationValues.string(value.get("key"), path + ".key");
        path = "ui.component[" + key + "]";
        Map<String, GuiComponentSchema> events = new LinkedHashMap<>();
        if (!value.get("events").isnil()) {
            for (String name : boundedKeys(value.get("events"), path + ".events", 64)) {
                events.put(name, schema(value.get("events").get(name), path + ".events." + name, false));
            }
        }
        Map<String, GuiComponentDefinition.Slot> slots = new LinkedHashMap<>();
        if (!value.get("slots").isnil()) {
            for (String name : boundedKeys(value.get("slots"), path + ".slots", 32)) {
                LuaValue slot = value.get("slots").get(name);
                String slotPath = path + ".slots." + name;
                LuaDeclarationValues.fields(slot, slotPath, "min", "max");
                slots.put(name,
                        new GuiComponentDefinition.Slot(optionalInteger(slot.get("min"), slotPath + ".min", 0, 0, 256),
                                optionalInteger(slot.get("max"), slotPath + ".max", 256, 0, 256)));
            }
        }
        Map<String, String> exports = new LinkedHashMap<>();
        if (!value.get("exports").isnil()) {
            for (String name : boundedKeys(value.get("exports"), path + ".exports", 64)) {
                exports.put(name,
                        LuaDeclarationValues.string(value.get("exports").get(name), path + ".exports." + name));
            }
        }
        Set<GuiCapability> requirements = EnumSet.noneOf(GuiCapability.class);
        for (String name : strings(value.get("requires"), path + ".requires", 32)) {
            try {
                requirements.add(GuiCapability.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException invalid) {
                throw LuaDeclarationValues.error(path + ".requires", "unknown host capability: " + name);
            }
        }
        if (!value.get("paint").isnil()) {
            requirements.add(GuiCapability.PREPARED_DRAWING);
        }
        if (!value.get("rawDraw").isnil()) {
            requirements.add(GuiCapability.RAW_DRAWING);
        }
        boolean focusable = LuaDeclarationValues.bool(value.get("focusable"), path + ".focusable", false);
        if (focusable || !value.get("input").isnil()) {
            requirements.add(GuiCapability.INPUT);
        }
        try {
            return new GuiComponentDefinition(key, owner, schema(value.get("props"), path + ".props", false),
                    schema(value.get("state"), path + ".state", true), events, slots, exports, requirements,
                    strings(value.get("dependencies"), path + ".dependencies", 128),
                    function(value.get("build"), path + ".build"), function(value.get("paint"), path + ".paint"),
                    function(value.get("rawDraw"), path + ".rawDraw"), function(value.get("hit"), path + ".hit"),
                    function(value.get("input"), path + ".input"), focusable,
                    value.get("label").isnil() ? key : LuaDeclarationValues.string(value.get("label"), path + ".label"),
                    size(value.get("size"), path + ".size", value.get("build").isnil()));
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    public static GuiComponentSchema schema(LuaValue value, String path, boolean state) {
        if (value.isnil()) {
            return GuiComponentSchema.EMPTY;
        }
        Map<String, GuiComponentSchema.Field> fields = new LinkedHashMap<>();
        for (String name : boundedKeys(value, path, 64)) {
            String fieldPath = path + "." + name;
            LuaValue declaration = value.get(name);
            LuaDeclarationValues.fields(declaration, fieldPath, "type", "default", "min", "max", "optional", "required",
                    "animated");
            String type = LuaDeclarationValues.string(declaration.get("type"), fieldPath + ".type");
            Kind kind;
            switch (type) {
                case "number":
                    kind = Kind.NUMBER;
                    break;
                case "integer":
                    kind = Kind.INTEGER;
                    break;
                case "boolean":
                    kind = Kind.BOOLEAN;
                    break;
                case "string":
                    kind = Kind.STRING;
                    break;
                case "color":
                    kind = Kind.COLOR;
                    break;
                case "string_list":
                    kind = Kind.STRING_LIST;
                    break;
                case "data":
                    kind = Kind.DATA;
                    break;
                default:
                    throw LuaDeclarationValues.error(fieldPath + ".type", "unknown component value type: " + type);
            }
            boolean numeric = kind == Kind.NUMBER || kind == Kind.INTEGER;
            if (!numeric && (!declaration.get("min").isnil() || !declaration.get("max").isnil())) {
                throw LuaDeclarationValues.error(fieldPath, "only numeric schema fields accept min/max");
            }
            double minimum = declaration.get("min").isnil()
                    ? -1e6
                    : LuaDeclarationValues.number(declaration.get("min"), fieldPath + ".min");
            double maximum = declaration.get("max").isnil()
                    ? 1e6
                    : LuaDeclarationValues.number(declaration.get("max"), fieldPath + ".max");
            boolean optional = LuaDeclarationValues.bool(declaration.get("optional"), fieldPath + ".optional", false);
            boolean required = LuaDeclarationValues.bool(declaration.get("required"), fieldPath + ".required",
                    !state && declaration.get("default").isnil());
            boolean animated = LuaDeclarationValues.bool(declaration.get("animated"), fieldPath + ".animated", false);
            if (state && (required || animated)) {
                throw LuaDeclarationValues.error(fieldPath,
                        "state fields require defaults and cannot declare property animation");
            }
            Object baseline = optional && declaration.get("default").isnil() ? null : baseline(kind, minimum, maximum);
            try {
                GuiPropertyDescriptor descriptor = new GuiPropertyDescriptor(name, kind, baseline, minimum, maximum,
                        optional, animated, Collections.singleton(state ? Stage.SEMANTICS : Stage.PAINT),
                        Collections.emptySet());
                if (!declaration.get("default").isnil()) {
                    Object normalized = GuiPropertyValues.read(descriptor, declaration.get("default"));
                    descriptor = new GuiPropertyDescriptor(name, kind, normalized, minimum, maximum, optional, animated,
                            descriptor.stages, descriptor.requirements);
                }
                fields.put(name, new GuiComponentSchema.Field(descriptor, required));
            } catch (IllegalArgumentException invalid) {
                throw LuaDeclarationValues.error(fieldPath, invalid.getMessage());
            }
        }
        return new GuiComponentSchema(fields);
    }

    public static Set<String> strings(LuaValue value, String path, int maximum) {
        if (value.isnil()) {
            return Collections.emptySet();
        }
        int length = LuaDeclarationValues.length(value, path);
        if (length > maximum) {
            throw LuaDeclarationValues.error(path, "list exceeds its item budget");
        }
        Set<String> result = new LinkedHashSet<>();
        for (int index = 1; index <= length; index++) {
            String item = LuaDeclarationValues.string(value.get(index), path + "[" + index + "]");
            if (!result.add(item)) {
                throw LuaDeclarationValues.error(path, "duplicate list entry: " + item);
            }
        }
        return result;
    }

    private static List<String> boundedKeys(LuaValue value, String path, int maximum) {
        List<String> keys = LuaDeclarationValues.keys(value, path);
        if (keys.size() > maximum) {
            throw LuaDeclarationValues.error(path, "record exceeds " + maximum + " fields");
        }
        return keys;
    }

    private static GuiComponentDefinition.SizeHint size(LuaValue value, String path, boolean leaf) {
        if (value.isnil()) {
            return new GuiComponentDefinition.SizeHint(leaf ? 16 : 0, leaf ? 16 : 0, 0, 0);
        }
        LuaDeclarationValues.fields(value, path, "width", "height", "minWidth", "minHeight");
        return new GuiComponentDefinition.SizeHint(
                optionalInteger(value.get("width"), path + ".width", leaf ? 16 : 0, 0, 16384),
                optionalInteger(value.get("height"), path + ".height", leaf ? 16 : 0, 0, 16384),
                optionalInteger(value.get("minWidth"), path + ".minWidth", 0, 0, 16384),
                optionalInteger(value.get("minHeight"), path + ".minHeight", 0, 0, 16384));
    }

    private static Object baseline(Kind kind, double minimum, double maximum) {
        switch (kind) {
            case BOOLEAN:
                return Boolean.FALSE;
            case STRING:
                return "";
            case STRING_LIST:
                return Collections.emptyList();
            case DATA:
                return Collections.emptyMap();
            case COLOR:
                return Integer.valueOf(0xFFFFFFFF);
            case INTEGER:
                double lower = Math.ceil(minimum);
                double upper = Math.floor(maximum);
                if (lower > upper) {
                    throw new LuaError("Integer field bounds contain no integer");
                }
                return Double.valueOf(Math.max(lower, Math.min(upper, 0)));
            default:
                return Double.valueOf(Math.max(minimum, Math.min(maximum, 0)));
        }
    }

    private static LuaValue function(LuaValue value, String path) {
        if (!value.isnil() && !value.isfunction()) {
            throw LuaDeclarationValues.error(path, "expected a function");
        }
        return value;
    }

    private static int optionalInteger(LuaValue value, String path, int fallback, int minimum, int maximum) {
        return value.isnil() ? fallback : LuaDeclarationValues.integer(value, path, minimum, maximum);
    }

    private GuiComponentParser() {
    }
}
