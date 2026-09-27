package betamoon.capability;

import betamoon.luaapi.resource.OverrideManager;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import betamoon.luaapi.utils.LuaOverrideLayers;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Stable live Lua reference for a capability contract and its operation behavior. */
public final class CapabilityReference extends LuaTable {
    private final CapabilityDefinition definition;

    public CapabilityReference(CapabilityDefinition definition) {
        this.definition = definition;
        set("key", definition.key.toString());
        set("override", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return override(arguments.arg(arguments.arg1() == CapabilityReference.this ? 2 : 1));
            }
        });
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.isstring()) {
            if (key.tojstring().equals("exists")) {
                return valueOf(CapabilityRegistry.find(definition.key) == definition);
            }
            if (key.tojstring().equals("owner")) {
                return valueOf(definition.owner);
            }
        }
        return super.get(key);
    }

    public CapabilityDefinition definition() {
        return definition;
    }

    public boolean matches(LuaValue query) {
        return (query.get("key").isnil() || definition.key.toString().equals(query.get("key").checkjstring()))
                && (query.get("owner").isnil() || definition.owner.equals(query.get("owner").checkjstring()));
    }

    public LuaValue override(LuaValue value) {
        if (CapabilityRegistry.find(definition.key) != definition) {
            throw new LuaError("Capability is no longer registered: " + definition.key);
        }
        if (!value.istable()) {
            throw new LuaError("Capability override expects a table.");
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
        LuaValue operations = changes.get("operations");
        if (operations.isnil() || !operations.istable()) {
            throw new LuaError("Capability override requires an operations table.");
        }
        int priority = value.get("priority").optint(0);
        List<OverrideManager.Request<?, ?>> requests = new ArrayList<OverrideManager.Request<?, ?>>();
        LuaValue operation = NIL;
        while (!(operation = operations.next(operation).arg1()).isnil()) {
            String name = operation.checkjstring();
            if (!definition.operations.containsKey(name)) {
                throw new LuaError("Capability does not declare operation: " + name);
            }
            LuaOverrideLayers<LuaOverrideDefinition> layer = LuaOverrideLayers.single(
                    new LuaOverrideDefinition("operations." + name, operations.get(operation)));
            OverrideManager.Property<CapabilityDefinition, LuaOverrideLayers<LuaOverrideDefinition>> property =
                    new OverrideManager.Property<CapabilityDefinition, LuaOverrideLayers<LuaOverrideDefinition>>(
                            "operations." + name, CapabilityOperationOverrides.adapter(name),
                            LuaOverrideLayers.resolver());
            requests.add(OverrideManager.request("capability:" + definition.key, definition, property, layer,
                    priority));
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
}
