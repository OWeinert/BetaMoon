package betamoon.luaapi.tileentity;

import betamoon.tileentity.ContainerControlDefinition;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.GuiConditionOperator;
import betamoon.tileentity.TileDataType;
import betamoon.tileentity.TileEntityDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Parses container-owned session fields and control behavior. */
final class ContainerControlParser {
    private static final int MAX_SESSION_FIELDS = 64;
    private static final int MAX_CONTROLS = 128;
    private static final int MAX_TEXT_LENGTH = 1024;

    private ContainerControlParser() {
    }

    static Map<String, ContainerDefinition.SessionField> parseSession(LuaValue declarations) {
        if (declarations.isnil()) {
            return Collections.emptyMap();
        }
        requireTable(declarations, "container.session");
        Map<String, ContainerDefinition.SessionField> result = new LinkedHashMap<String, ContainerDefinition.SessionField>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = declarations.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            if (!key.isstring() || !next.arg(2).istable()) {
                throw new LuaError("container.session must map names to field definitions.");
            }
            String name = key.checkjstring();
            validateName(name, "container.session");
            if (result.size() >= MAX_SESSION_FIELDS) {
                throw new LuaError("container.session may contain at most " + MAX_SESSION_FIELDS + " fields.");
            }
            LuaValue field = next.arg(2);
            TileDataType type = TileDataType.parse(required(field, "type").checkjstring());
            Object defaultValue = type.defaultValue(field.get("default"));
            if (defaultValue instanceof Number && !Double.isFinite(((Number) defaultValue).doubleValue())) {
                throw new LuaError("container.session." + name + ".default must be finite.");
            }
            int maximumLength = field.get("maxLength").optint(type == TileDataType.STRING ? 256 : 0);
            if (type == TileDataType.STRING && (maximumLength < 1 || maximumLength > MAX_TEXT_LENGTH)) {
                throw new LuaError("container.session." + name + ".maxLength must be between 1 and "
                        + MAX_TEXT_LENGTH + ".");
            }
            if (defaultValue instanceof String && ((String) defaultValue).length() > maximumLength) {
                throw new LuaError("container.session." + name + ".default exceeds maxLength.");
            }
            result.put(name, new ContainerDefinition.SessionField(name, type, defaultValue, maximumLength));
        }
        return result;
    }

    static Map<String, ContainerControlDefinition> parseControls(LuaValue declarations, TileEntityDefinition tile,
            Map<String, ContainerDefinition.SessionField> session) {
        if (declarations.isnil()) {
            return Collections.emptyMap();
        }
        requireTable(declarations, "container.controls");
        Map<String, ContainerControlDefinition> result = new LinkedHashMap<String, ContainerControlDefinition>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = declarations.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            if (!key.isstring() || !next.arg(2).istable()) {
                throw new LuaError("container.controls must map names to control definitions.");
            }
            String name = key.checkjstring();
            validateName(name, "container.controls");
            if (result.size() >= MAX_CONTROLS) {
                throw new LuaError("container.controls may contain at most " + MAX_CONTROLS + " controls.");
            }
            result.put(name, parseControl(name, next.arg(2), tile, session));
        }
        return result;
    }

    private static ContainerControlDefinition parseControl(String name, LuaValue value, TileEntityDefinition tile,
            Map<String, ContainerDefinition.SessionField> session) {
        String path = "container.controls." + name;
        ContainerControlDefinition.Type type;
        try {
            type = ContainerControlDefinition.Type.valueOf(required(value, "type").checkjstring().toUpperCase());
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ".type must be action, toggle, number, text, choice, or custom.");
        }
        Binding binding = binding(value.get("bind"), path, type, tile, session);
        double minimum = type == ContainerControlDefinition.Type.NUMBER
                ? required(value, "minimum").checkdouble() : 0.0D;
        double maximum = type == ContainerControlDefinition.Type.NUMBER
                ? required(value, "maximum").checkdouble() : 1.0D;
        double step = value.get("step").optdouble(1.0D);
        double pageStep = value.get("pageStep").optdouble(step * 10.0D);
        if (type == ContainerControlDefinition.Type.NUMBER
                && (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(step)
                        || !Double.isFinite(pageStep) || maximum <= minimum || step <= 0.0D || pageStep <= 0.0D)) {
            throw new LuaError(path + " requires finite minimum < maximum and positive step/pageStep.");
        }
        if (type == ContainerControlDefinition.Type.NUMBER && binding.type == TileDataType.INTEGER
                && !(integer(minimum) && integer(maximum) && integer(step) && integer(pageStep))) {
            throw new LuaError(path + " uses an integer binding, so minimum, maximum, step, and pageStep"
                    + " must be integers.");
        }
        int maximumLength = value.get("maxLength").optint(256);
        if (type == ContainerControlDefinition.Type.TEXT
                && (maximumLength < 1 || maximumLength > MAX_TEXT_LENGTH)) {
            throw new LuaError(path + ".maxLength must be between 1 and " + MAX_TEXT_LENGTH + ".");
        }
        List<Object> choices = type == ContainerControlDefinition.Type.CHOICE
                ? choices(required(value, "values"), path + ".values") : Collections.emptyList();
        validateBindingType(path, type, binding.type, choices);
        choices = normalizeChoices(binding.type, choices);
        return new ContainerControlDefinition(name, type, binding.source, binding.field,
                minimum, maximum, step, pageStep, maximumLength, value.get("wrap").optboolean(true), choices,
                condition(value.get("enabledWhen"), tile, session, path + ".enabledWhen"),
                callback(value.get("beforeChange"), path + ".beforeChange"),
                callback(value.get("onActivate"), path + ".onActivate"),
                callback(value.get("onChange"), path + ".onChange"),
                callback(value.get("onEdit"), path + ".onEdit"),
                callback(value.get("onCommit"), path + ".onCommit"),
                callback(value.get("onInput"), path + ".onInput"));
    }

    private static Binding binding(LuaValue value, String path, ContainerControlDefinition.Type type,
            TileEntityDefinition tile, Map<String, ContainerDefinition.SessionField> session) {
        if ((type == ContainerControlDefinition.Type.ACTION || type == ContainerControlDefinition.Type.CUSTOM)
                && !value.isnil()) {
            throw new LuaError(path + ".bind is unsupported for action and custom controls.");
        }
        if (value.isnil()) {
            if (type == ContainerControlDefinition.Type.ACTION || type == ContainerControlDefinition.Type.CUSTOM) {
                return new Binding(ContainerControlDefinition.Source.NONE, null, null);
            }
            throw new LuaError(path + ".bind is required for value controls.");
        }
        requireTable(value, path + ".bind");
        String data = value.get("data").optjstring(null);
        String sessionName = value.get("session").optjstring(null);
        if ((data == null) == (sessionName == null)) {
            throw new LuaError(path + ".bind requires exactly one of data or session.");
        }
        if (data != null) {
            TileEntityDefinition.Field field = tile.fields.get(data);
            if (field == null) {
                throw new LuaError(path + ".bind references unknown tile data: " + data);
            }
            return new Binding(ContainerControlDefinition.Source.DATA, data, field.type);
        }
        ContainerDefinition.SessionField field = session.get(sessionName);
        if (field == null) {
            throw new LuaError(path + ".bind references unknown session data: " + sessionName);
        }
        return new Binding(ContainerControlDefinition.Source.SESSION, sessionName, field.type);
    }

    private static void validateBindingType(String path, ContainerControlDefinition.Type control, TileDataType field,
            List<Object> choices) {
        if (control == ContainerControlDefinition.Type.ACTION || control == ContainerControlDefinition.Type.CUSTOM) {
            return;
        }
        boolean valid = control == ContainerControlDefinition.Type.TOGGLE && field == TileDataType.BOOLEAN
                || control == ContainerControlDefinition.Type.NUMBER
                        && (field == TileDataType.INTEGER || field == TileDataType.NUMBER)
                || control == ContainerControlDefinition.Type.TEXT && field == TileDataType.STRING
                || control == ContainerControlDefinition.Type.CHOICE && choiceType(field, choices);
        if (!valid) {
            throw new LuaError(path + ".bind has an incompatible field type.");
        }
    }

    private static boolean choiceType(TileDataType type, List<Object> values) {
        for (Object value : values) {
            if (!type.accepts(value) && !(type == TileDataType.NUMBER && value instanceof Number)) {
                return false;
            }
        }
        return true;
    }

    private static List<Object> normalizeChoices(TileDataType type, List<Object> values) {
        if (type != TileDataType.NUMBER) {
            return values;
        }
        List<Object> normalized = new ArrayList<Object>();
        for (Object value : values) {
            normalized.add(Double.valueOf(((Number) value).doubleValue()));
        }
        return normalized;
    }

    private static List<Object> choices(LuaValue value, String path) {
        requireTable(value, path);
        if (value.length() < 2 || value.length() > 64) {
            throw new LuaError(path + " must contain between 2 and 64 values.");
        }
        List<Object> result = new ArrayList<Object>();
        for (int index = 1; index <= value.length(); index++) {
            Object entry = scalar(value.get(index), path + "[" + index + "]");
            if (result.contains(entry)) {
                throw new LuaError(path + " contains a duplicate value.");
            }
            result.add(entry);
        }
        return result;
    }

    private static ContainerGuiDefinition.Condition condition(LuaValue value, TileEntityDefinition tile,
            Map<String, ContainerDefinition.SessionField> session, String path) {
        if (value.isnil()) {
            return null;
        }
        requireTable(value, path);
        LuaValue all = value.get("all");
        LuaValue any = value.get("any");
        if (!all.isnil() || !any.isnil()) {
            if (!all.isnil() && !any.isnil()) {
                throw new LuaError(path + " cannot contain both all and any.");
            }
            LuaValue children = all.isnil() ? any : all;
            if (!children.istable() || children.length() == 0) {
                throw new LuaError(path + " group must be a non-empty array.");
            }
            List<ContainerGuiDefinition.Condition> parsed = new ArrayList<ContainerGuiDefinition.Condition>();
            for (int index = 1; index <= children.length(); index++) {
                parsed.add(condition(children.get(index), tile, session, path + "[" + index + "]"));
            }
            return new ContainerGuiDefinition.Condition(null,
                    all.isnil() ? GuiConditionOperator.ANY : GuiConditionOperator.ALL, null, parsed);
        }
        String data = value.get("data").optjstring(value.get("field").optjstring(null));
        String sessionName = value.get("session").optjstring(null);
        if ((data == null) == (sessionName == null)) {
            throw new LuaError(path + " requires exactly one of data/field or session.");
        }
        TileDataType type;
        ContainerGuiDefinition.Condition.Source source;
        String field;
        if (data != null) {
            TileEntityDefinition.Field definition = tile.fields.get(data);
            if (definition == null) {
                throw new LuaError(path + " references unknown tile data: " + data);
            }
            type = definition.type;
            source = ContainerGuiDefinition.Condition.Source.DATA;
            field = data;
        } else {
            ContainerDefinition.SessionField definition = session.get(sessionName);
            if (definition == null) {
                throw new LuaError(path + " references unknown session data: " + sessionName);
            }
            type = definition.type;
            source = ContainerGuiDefinition.Condition.Source.SESSION;
            field = sessionName;
        }
        String[] comparisons = {"equals", "notEquals", "greaterThan", "greaterOrEqual", "lessThan", "lessOrEqual"};
        String found = null;
        Object expected = null;
        for (String comparison : comparisons) {
            if (!value.get(comparison).isnil()) {
                if (found != null) {
                    throw new LuaError(path + " requires exactly one comparison.");
                }
                found = comparison;
                expected = scalar(value.get(comparison), path + "." + comparison);
            }
        }
        if (found == null) {
            throw new LuaError(path + " requires a comparison.");
        }
        GuiConditionOperator operator = GuiConditionOperator.fromLua(found);
        if (operator.isOrdered() && !(type == TileDataType.INTEGER || type == TileDataType.NUMBER)) {
            throw new LuaError(path + " ordered comparisons require numeric data.");
        }
        return new ContainerGuiDefinition.Condition(source, field, operator, expected, null);
    }

    private static Object scalar(LuaValue value, String path) {
        if (value.isboolean()) {
            return Boolean.valueOf(value.toboolean());
        }
        if (value.isint()) {
            return Integer.valueOf(value.toint());
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number)) {
                throw new LuaError(path + " must be finite.");
            }
            return Double.valueOf(number);
        }
        if (value.isstring()) {
            return value.tojstring();
        }
        throw new LuaError(path + " must be a boolean, number, or string.");
    }

    private static LuaValue callback(LuaValue value, String path) {
        if (!value.isnil() && !value.isfunction()) {
            throw new LuaError(path + " must be a function.");
        }
        return value;
    }

    private static boolean integer(double value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE && value == Math.rint(value);
    }

    private static void validateName(String name, String path) {
        if (!name.matches("[a-z][a-zA-Z0-9_]{0,63}")) {
            throw new LuaError(path + " contains invalid name: " + name);
        }
    }

    private static LuaValue required(LuaValue table, String name) {
        LuaValue value = table.get(name);
        if (value.isnil()) {
            throw new LuaError("Definition requires '" + name + "'.");
        }
        return value;
    }

    private static void requireTable(LuaValue value, String path) {
        if (!value.istable()) {
            throw new LuaError(path + " must be a table.");
        }
    }

    private static final class Binding {
        private final ContainerControlDefinition.Source source;
        private final String field;
        private final TileDataType type;

        private Binding(ContainerControlDefinition.Source source, String field, TileDataType type) {
            this.source = source;
            this.field = field;
            this.type = type;
        }
    }
}
