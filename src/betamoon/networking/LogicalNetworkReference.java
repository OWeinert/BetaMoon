package betamoon.networking;

import betamoon.luaapi.resource.OverrideManager;
import betamoon.luaapi.utils.LuaDefinitionCallbackLayers;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import betamoon.luaapi.utils.LuaOverrideLayers;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Stable live Lua reference to a logical-network definition. */
public final class LogicalNetworkReference extends LuaTable {
    private final LogicalNetworkDefinition definition;

    public LogicalNetworkReference(LogicalNetworkDefinition definition) {
        this.definition = definition;
        set("key", definition.key.toString());
        set("override", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return override(arguments.arg(arguments.arg1() == LogicalNetworkReference.this ? 2 : 1));
            }
        });
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.isstring()) {
            String name = key.tojstring();
            if (name.equals("exists")) {
                return valueOf(LogicalNetworkRegistry.find(definition.key) == definition);
            }
            if (name.equals("owner")) {
                return valueOf(definition.owner);
            }
            if (name.equals("topology")) {
                return valueOf(definition.topology.name().toLowerCase());
            }
            if (name.equals("tickInterval")) {
                return valueOf(definition.tickInterval);
            }
        }
        return super.get(key);
    }

    public LogicalNetworkDefinition definition() {
        return definition;
    }

    public boolean matches(LuaValue query) {
        return (query.get("key").isnil() || definition.key.toString().equals(query.get("key").checkjstring()))
                && (query.get("owner").isnil() || definition.owner.equals(query.get("owner").checkjstring()))
                && (query.get("topology").isnil()
                        || definition.topology.name().equalsIgnoreCase(query.get("topology").checkjstring()));
    }

    public LuaValue override(LuaValue value) {
        if (LogicalNetworkRegistry.find(definition.key) != definition) {
            throw new LuaError("Logical network is no longer registered: " + definition.key);
        }
        if (!value.istable()) {
            throw new LuaError("Logical-network override expects a table.");
        }
        final LuaTable handle = new LuaTable();
        handle.set("target", this);
        LuaValue when = value.get("when");
        if (!when.isnil() && !when.get("owner").isnil()
                && !definition.owner.equals(when.get("owner").checkjstring())) {
            handle.set("active", FALSE);
            handle.set("reason", "target owner did not match");
            return handle;
        }
        LuaValue changes = value.get("changes");
        if (changes.isnil()) {
            changes = value;
        }
        int priority = value.get("priority").optint(0);
        List<OverrideManager.Request<?, ?>> requests = new ArrayList<OverrideManager.Request<?, ?>>();
        LuaValue field = NIL;
        while (!(field = changes.next(field).arg1()).isnil()) {
            String name = field.checkjstring();
            if (name.equals("when") || name.equals("changes") || name.equals("priority") || name.equals("target")) {
                continue;
            }
            LuaValue changed = changes.get(field);
            if (name.equals("tickInterval")) {
                int interval = changed.checkint();
                if (interval < 1 || interval > 1200) {
                    throw new LuaError("Logical-network tickInterval must be between 1 and 1200.");
                }
                requests.add(OverrideManager.request("logicalNetwork:" + definition.key, definition,
                        intervalProperty(), Integer.valueOf(interval), priority));
            } else if (name.equals("canConnect") || name.equals("onTick") || name.equals("onPulse")) {
                LuaOverrideLayers<LuaOverrideDefinition> layer = LuaOverrideLayers.single(
                        new LuaOverrideDefinition(name, changed));
                requests.add(OverrideManager.request("logicalNetwork:" + definition.key, definition,
                        callbackProperty(name), layer, priority));
            } else {
                throw new LuaError("Property '" + name + "' cannot be overridden on a logical network.");
            }
        }
        final List<OverrideManager.Layer<?, ?>> layers = OverrideManager.applyAll(requests);
        handle.set("active", TRUE);
        handle.set("remove", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                if (handle.get("active").toboolean()) {
                    for (int index = layers.size() - 1; index >= 0; index--) {
                        layers.get(index).remove();
                    }
                    handle.set("active", FALSE);
                }
                return NIL;
            }
        });
        return handle;
    }

    private OverrideManager.Property<LogicalNetworkDefinition, Integer> intervalProperty() {
        return new OverrideManager.Property<LogicalNetworkDefinition, Integer>("tickInterval",
                new OverrideManager.PropertyAdapter<LogicalNetworkDefinition, Integer>() {
                    public Integer read(LogicalNetworkDefinition target) {
                        return Integer.valueOf(target.tickInterval);
                    }

                    public void write(LogicalNetworkDefinition target, Integer value) {
                        target.tickInterval = value.intValue();
                    }
                });
    }

    private OverrideManager.Property<LogicalNetworkDefinition, LuaOverrideLayers<LuaOverrideDefinition>>
            callbackProperty(String name) {
        LuaValue original = name.equals("canConnect") ? definition.canConnect
                : name.equals("onTick") ? definition.onTick : definition.onPulse;
        return LuaDefinitionCallbackLayers.property(name, original, (target, callback) -> {
            if (name.equals("canConnect")) {
                target.canConnect = callback;
            } else if (name.equals("onTick")) {
                target.onTick = callback;
            } else {
                target.onPulse = callback;
            }
        });
    }
}
