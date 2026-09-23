package betamoon.system;

import org.luaj.vm2.LuaTable;

/** Opaque Lua reference to a registered world service. */
public final class WorldServiceReference extends LuaTable {
    private final WorldServiceDefinition definition;

    public WorldServiceReference(WorldServiceDefinition definition) {
        this.definition = definition;
        set("key", definition.key.toString());
    }

    public WorldServiceDefinition definition() {
        return definition;
    }
}
