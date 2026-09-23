package betamoon.networking;

import org.luaj.vm2.LuaTable;

/** Opaque Lua reference to a logical-network definition. */
public final class LogicalNetworkReference extends LuaTable {
    private final LogicalNetworkDefinition definition;

    public LogicalNetworkReference(LogicalNetworkDefinition definition) {
        this.definition = definition;
        set("key", definition.key.toString());
    }

    public LogicalNetworkDefinition definition() {
        return definition;
    }
}
