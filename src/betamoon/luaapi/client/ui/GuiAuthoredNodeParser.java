package betamoon.luaapi.client.ui;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.component.GuiAuthoredNode;
import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.component.GuiComponentLimits;
import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.widget.GuiBuiltInViews;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Parses immutable host-independent declarations, including content-placement
 * tokens returned to builders.
 */
public final class GuiAuthoredNodeParser {
    private final GuiComponentRegistry components;
    private final Set<LuaTable> ancestors = Collections.newSetFromMap(new IdentityHashMap<LuaTable, Boolean>());
    private int count;
    private GuiComponentLimits.Values budget;

    public GuiAuthoredNodeParser(GuiComponentRegistry components) {
        this.components = components;
    }

    public GuiAuthoredNode parse(String owner, LuaValue root, String path) {
        count = 0;
        ancestors.clear();
        budget = new GuiComponentLimits.Values(4 * 1024 * 1024, 262144);
        return node(owner, root, path, 0);
    }

    private GuiAuthoredNode node(String owner, LuaValue value, String path, int depth) {
        if (!value.istable() || depth >= 32 || ++count > 4096) {
            throw LuaDeclarationValues.error(path, "node must be a table within 32 levels/4096 nodes");
        }
        LuaTable table = value.checktable();
        if (!ancestors.add(table)) {
            throw LuaDeclarationValues.error(path, "node declarations cannot contain cycles");
        }
        try {
            String type = optionalString(value.get("type"), path + ".type");
            String component = optionalString(value.get("component"), path + ".component");
            String slot = optionalString(value.get("slot"), path + ".slot");
            if ((type == null ? 0 : 1) + (component == null ? 0 : 1) + (slot == null ? 0 : 1) != 1) {
                throw LuaDeclarationValues.error(path, "select exactly one type, component or content slot");
            }
            String id = identity(value.get("id"), path + ".id");
            String key = identity(value.get("key"), path + ".key");
            if (slot != null) {
                LuaDeclarationValues.fields(value, path, "slot", "index", "key");
                GuiComponentDefinition.name(slot);
                int index = value.get("index").isnil()
                        ? 0
                        : LuaDeclarationValues.integer(value.get("index"), path + ".index", 1, 256);
                return new GuiAuthoredNode(owner, null, null, slot, index, null, key, null, Collections.emptyMap(),
                        Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), LuaValue.NIL,
                        GuiTransitionPolicy.DEFAULT, false);
            }
            GuiProperties properties;
            GuiComponentDefinition definition = null;
            try {
                if (component != null) {
                    definition = components.getRequired(component);
                    component = definition.key;
                }
                properties = GuiBuiltInViews.properties(type == null ? "overlay" : type);
            } catch (IllegalArgumentException invalid) {
                throw LuaDeclarationValues.error(path, invalid.getMessage());
            }
            Set<String> allowed = new LinkedHashSet<>(properties.baseline().keySet());
            allowed.addAll(Arrays.asList("id", "key", "type", "component", "children", "slots", "props", "on", "bind",
                    "transition"));
            for (String name : LuaDeclarationValues.keys(value, path)) {
                if (!allowed.contains(name)) {
                    throw LuaDeclarationValues.error(path + "." + name, "unknown node field");
                }
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String name : properties.baseline().keySet()) {
                if (!value.get(name).isnil()) {
                    values.put(name, properties.read(name, value.get(name)));
                }
            }
            properties = properties.withDefaults(values);
            Map<String, Object> props = definition == null
                    ? Collections.emptyMap()
                    : readDeclaredProps(definition.props, value.get("props"), path + ".props");
            if (definition != null) {
                definition.props.normalize(props);
            }
            budget.add(properties.baseline());
            budget.add(props);
            if (definition == null && (!value.get("props").isnil() || !value.get("slots").isnil())) {
                throw LuaDeclarationValues.error(path, "props and slots belong to component instances");
            }
            if (definition != null && !value.get("children").isnil()) {
                throw LuaDeclarationValues.error(path + ".children", "component content uses named slots");
            }
            Map<String, List<GuiAuthoredNode>> slots = new LinkedHashMap<>();
            if (!value.get("slots").isnil()) {
                for (String name : LuaDeclarationValues.keys(value.get("slots"), path + ".slots")) {
                    GuiComponentDefinition.Slot contract = definition.slots.get(name);
                    if (contract == null) {
                        throw LuaDeclarationValues.error(path + ".slots." + name, "unknown component slot");
                    }
                    List<GuiAuthoredNode> content = children(owner, value.get("slots").get(name),
                            path + ".slots." + name, depth + 1);
                    if (content.size() < contract.minimum || content.size() > contract.maximum) {
                        throw LuaDeclarationValues.error(path + ".slots." + name,
                                "content count is outside slot bounds");
                    }
                    slots.put(name, content);
                }
            }
            if (definition != null) {
                for (Map.Entry<String, GuiComponentDefinition.Slot> entry : definition.slots.entrySet()) {
                    if (!slots.containsKey(entry.getKey())) {
                        if (entry.getValue().minimum > 0) {
                            throw LuaDeclarationValues.error(path + ".slots." + entry.getKey(),
                                    "required content is missing");
                        }
                        slots.put(entry.getKey(), Collections.emptyList());
                    }
                }
            }
            Map<String, LuaValue> handlers = handlers(value.get("on"), path + ".on");
            if (definition != null) {
                for (String name : handlers.keySet()) {
                    if (!definition.events.containsKey(name)) {
                        throw LuaDeclarationValues.error(path + ".on." + name, "component does not declare this event");
                    }
                }
            } else {
                for (String name : handlers.keySet()) {
                    if (!Arrays.asList("activate", "change", "commit", "cancel").contains(name)) {
                        throw LuaDeclarationValues.error(path + ".on." + name, "unknown built-in semantic event");
                    }
                }
            }
            LuaValue binding = value.get("bind");
            if (!binding.isnil() && !binding.isfunction()) {
                throw LuaDeclarationValues.error(path + ".bind", "expected a function");
            }
            return new GuiAuthoredNode(owner, type, component, null, 0, id, key, properties, props, slots,
                    children(owner, value.get("children"), path + ".children", depth + 1), handlers, binding,
                    value.get("transition").isnil()
                            ? GuiTransitionPolicy.DEFAULT
                            : transition(properties, definition, value.get("transition")),
                    !value.get("transition").isnil());
        } finally {
            ancestors.remove(table);
        }
    }

    private List<GuiAuthoredNode> children(String owner, LuaValue value, String path, int depth) {
        if (value.isnil()) {
            return Collections.emptyList();
        }
        int length = LuaDeclarationValues.length(value, path);
        if (length > 4096) {
            throw LuaDeclarationValues.error(path, "too many child declarations");
        }
        List<GuiAuthoredNode> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (int index = 1; index <= length; index++) {
            GuiAuthoredNode child = node(owner, value.get(index), path + "[" + index + "]", depth);
            String key = child.key == null ? child.id : child.key;
            if (key != null && !keys.add(key)) {
                throw LuaDeclarationValues.error(path, "duplicate sibling identity: " + key);
            }
            result.add(child);
        }
        return result;
    }

    public static GuiTransitionPolicy transition(GuiProperties geometry, GuiComponentDefinition component,
            LuaValue value) {
        Set<String> animated = new LinkedHashSet<>(geometry.animatable());
        if (component != null) {
            for (Map.Entry<String, GuiComponentSchema.Field> field : component.props.fields().entrySet()) {
                if (field.getValue().descriptor.animated) {
                    animated.add(field.getKey());
                }
            }
        }
        return GuiTransitionPolicy.parse(value, animated);
    }

    public static Map<String, Object> readProps(GuiComponentSchema schema, LuaValue value, String path) {
        try {
            return schema.normalize(readDeclaredProps(schema, value, path));
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    public static Map<String, Object> readDeclaredProps(GuiComponentSchema schema, LuaValue value, String path) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (!value.isnil()) {
            for (String name : LuaDeclarationValues.keys(value, path)) {
                GuiComponentSchema.Field field = schema.fields().get(name);
                if (field == null) {
                    throw LuaDeclarationValues.error(path + "." + name, "unknown component property");
                }
                properties.put(name, GuiPropertyValues.read(field.descriptor, value.get(name)));
            }
        }
        return Collections.unmodifiableMap(properties);
    }

    private static Map<String, LuaValue> handlers(LuaValue value, String path) {
        if (value.isnil()) {
            return Collections.emptyMap();
        }
        Map<String, LuaValue> handlers = new LinkedHashMap<>();
        for (String name : LuaDeclarationValues.keys(value, path)) {
            LuaValue function = value.get(name);
            if (!function.isfunction()) {
                throw LuaDeclarationValues.error(path + "." + name, "expected a function");
            }
            handlers.put(name, function);
        }
        return handlers;
    }

    private static String identity(LuaValue value, String path) {
        String result = optionalString(value, path);
        if (result != null && !result.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")) {
            throw LuaDeclarationValues.error(path,
                    "identity requires 1..128 stable letters, digits, dots, dashes or underscores");
        }
        return result;
    }

    private static String optionalString(LuaValue value, String path) {
        return value.isnil() ? null : LuaDeclarationValues.string(value, path);
    }
}
