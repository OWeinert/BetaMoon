package betamoon.luaapi.fluid;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Tracks advanced definitions which must be bound before a script load pass ends. */
public final class FluidDefinitionTracker {
    private static final Map<LuaFluidDefinition, String> PENDING = new IdentityHashMap<>();
    private static boolean active;

    private FluidDefinitionTracker() {
    }

    public static synchronized void beginLoadPass() {
        PENDING.clear();
        active = true;
    }

    static synchronized void track(LuaFluidDefinition definition) {
        if (active) {
            PENDING.put(definition, definition.draft().owner());
        }
    }

    static synchronized void published(LuaFluidDefinition definition) {
        PENDING.remove(definition);
    }

    public static synchronized List<String> finishLoadPass() {
        List<String> errors = new ArrayList<String>();
        for (Map.Entry<LuaFluidDefinition, String> entry : PENDING.entrySet()) {
            errors.add(entry.getValue() + ": fluid definition '" + entry.getKey().draft().key()
                    + "' was not bound to one flowing and one still block before registration ended.");
        }
        PENDING.clear();
        active = false;
        return errors;
    }
}
