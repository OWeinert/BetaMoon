package betamoon.capability;

import org.luaj.vm2.LuaTable;

/** Opaque Lua registration reference for a capability contract. */
public final class CapabilityReference extends LuaTable {
    private final CapabilityDefinition definition;

    public CapabilityReference(CapabilityDefinition definition) {
        this.definition = definition;
        set("key", definition.key.toString());
    }

    public CapabilityDefinition definition() {
        return definition;
    }
}
