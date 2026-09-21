package betamoon.entity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Named states and reusable timer/state callbacks for any custom entity kind. */
public final class EntityBehaviorDefinition {
    public final Set<String> states;
    public final String initialState;
    public final LuaValue onTimer;
    public final LuaValue onStateEnter;
    public final LuaValue onStateExit;

    public EntityBehaviorDefinition(LuaValue value) {
        fields(value, "entity.behavior", "states", "initialState", "onTimer", "onStateEnter", "onStateExit");
        LuaValue stateValue = value.get("states");
        Set<String> parsed = new LinkedHashSet<>();
        if (!stateValue.isnil()) {
            LuaTable table = stateValue.checktable();
            if (table.length() > 64 || table.keys().length != table.length()) {
                throw error("entity.behavior.states", "expected an array of at most 64 states");
            }
            for (int index = 1; index <= table.length(); index++) {
                if (!parsed.add(name(table.get(index), "entity.behavior.states[" + index + "]"))) {
                    throw error("entity.behavior.states[" + index + "]", "duplicate state");
                }
            }
        }
        String initial = value.get("initialState").isnil()
                ? parsed.isEmpty() ? "" : parsed.iterator().next()
                : name(value.get("initialState"), "entity.behavior.initialState");
        if (!initial.isEmpty() && !parsed.contains(initial)) {
            throw error("entity.behavior.initialState", "must be listed in states");
        }
        states = Collections.unmodifiableSet(parsed);
        initialState = initial;
        onTimer = callback(value, "onTimer");
        onStateEnter = callback(value, "onStateEnter");
        onStateExit = callback(value, "onStateExit");
        if (states.isEmpty() && (!onStateEnter.isnil() || !onStateExit.isnil())) {
            throw error("entity.behavior", "state callbacks require states");
        }
        if (states.isEmpty() && onTimer.isnil()) {
            throw error("entity.behavior", "requires states or onTimer");
        }
    }

    public static String name(LuaValue value, String path) {
        String parsed = string(value, path);
        if (!parsed.matches("[a-z][a-z0-9_]{0,63}")) {
            throw error(path, "expected a lowercase identifier of at most 64 characters");
        }
        return parsed;
    }

    private static LuaValue callback(LuaValue value, String name) {
        LuaValue callback = value.get(name);
        if (!callback.isnil() && !callback.isfunction()) {
            throw error("entity.behavior." + name, "expected a function");
        }
        return callback;
    }
}
