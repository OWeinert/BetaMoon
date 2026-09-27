package betamoon.system;

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

/** Stable live Lua reference to a registered world service. */
public final class WorldServiceReference extends LuaTable {
    private final WorldServiceDefinition definition;

    public WorldServiceReference(WorldServiceDefinition definition) {
        this.definition = definition;
        set("key", definition.key.toString());
        set("override", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return override(arguments.arg(arguments.arg1() == WorldServiceReference.this ? 2 : 1));
            }
        });
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.isstring()) {
            String name = key.tojstring();
            if (name.equals("exists")) {
                return valueOf(WorldServiceRegistry.find(definition.key) == definition);
            }
            if (name.equals("owner")) {
                return valueOf(definition.owner);
            }
            if (name.equals("tickInterval")) {
                return valueOf(definition.tickInterval);
            }
        }
        return super.get(key);
    }

    public WorldServiceDefinition definition() {
        return definition;
    }

    public boolean matches(LuaValue query) {
        return (query.get("key").isnil() || definition.key.toString().equals(query.get("key").checkjstring()))
                && (query.get("owner").isnil() || definition.owner.equals(query.get("owner").checkjstring()));
    }

    public LuaValue override(LuaValue value) {
        if (WorldServiceRegistry.find(definition.key) != definition) {
            throw new LuaError("World service is no longer registered: " + definition.key);
        }
        if (!value.istable()) {
            throw new LuaError("World-service override expects a table.");
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
                    throw new LuaError("World-service tickInterval must be between 1 and 1200.");
                }
                requests.add(OverrideManager.request("worldService:" + definition.key, definition,
                        intervalProperty(), Integer.valueOf(interval), priority));
            } else if (name.equals("onLoad") || name.equals("onTick") || name.equals("onUnload")) {
                LuaOverrideLayers<LuaOverrideDefinition> layer = LuaOverrideLayers.single(
                        new LuaOverrideDefinition(name, changed));
                requests.add(OverrideManager.request("worldService:" + definition.key, definition,
                        callbackProperty(name), layer, priority));
            } else {
                throw new LuaError("Property '" + name + "' cannot be overridden on a world service.");
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

    private OverrideManager.Property<WorldServiceDefinition, Integer> intervalProperty() {
        return new OverrideManager.Property<WorldServiceDefinition, Integer>("tickInterval",
                new OverrideManager.PropertyAdapter<WorldServiceDefinition, Integer>() {
                    public Integer read(WorldServiceDefinition target) {
                        return Integer.valueOf(target.tickInterval);
                    }

                    public void write(WorldServiceDefinition target, Integer value) {
                        target.tickInterval = value.intValue();
                    }
                });
    }

    private OverrideManager.Property<WorldServiceDefinition, LuaOverrideLayers<LuaOverrideDefinition>>
            callbackProperty(String name) {
        LuaValue original;
        if (name.equals("onLoad")) {
            original = definition.onLoad;
        } else if (name.equals("onTick")) {
            original = definition.onTick;
        } else {
            original = definition.onUnload;
        }
        return LuaDefinitionCallbackLayers.property(name, original, (target, callback) -> {
            if (name.equals("onLoad")) {
                target.onLoad = callback;
            } else if (name.equals("onTick")) {
                target.onTick = callback;
            } else {
                target.onUnload = callback;
            }
        });
    }
}
