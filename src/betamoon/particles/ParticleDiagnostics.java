package betamoon.particles;

import betamoon.luamodloader.LuaScriptErrors;
import java.util.HashSet;
import java.util.Set;

/**
 * Bounded owner-generation warnings for requests without a client presentation
 * route.
 */
public final class ParticleDiagnostics {
    private static final Set<String> ROUTES = new HashSet<String>();

    private ParticleDiagnostics() {
    }

    public static void unavailable(String owner, String reason) {
        if (ROUTES.add(owner + "\n" + reason)) {
            LuaScriptErrors.addWarning(owner, "Custom particle has no presentation route: " + reason);
        }
    }

    public static void clearOwner(String owner) {
        ROUTES.removeIf(key -> key.startsWith(owner + "\n"));
    }
}
