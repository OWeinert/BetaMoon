package betamoon.runtime;

/**
 * Detects the physical Minecraft process without loading side-specific classes.
 */
public final class RuntimeEnvironment {
    public static final String SIDE_PROPERTY = "betamoon.runtime.side";

    private static volatile RuntimeSide detectedSide;

    private RuntimeEnvironment() {
    }

    public static RuntimeSide side() {
        RuntimeSide current = detectedSide;
        if (current != null) {
            return current;
        }
        synchronized (RuntimeEnvironment.class) {
            if (detectedSide == null) {
                detectedSide = detect();
            }
            return detectedSide;
        }
    }

    public static boolean isClient() {
        return side() == RuntimeSide.CLIENT;
    }

    public static boolean isDedicatedServer() {
        return side() == RuntimeSide.DEDICATED_SERVER;
    }

    private static RuntimeSide detect() {
        String override = System.getProperty(SIDE_PROPERTY);
        if (override != null) {
            String normalized = override.trim().toLowerCase(java.util.Locale.ROOT);
            if ("client".equals(normalized)) {
                return RuntimeSide.CLIENT;
            }
            if ("server".equals(normalized) || "dedicated_server".equals(normalized)
                    || "dedicated-server".equals(normalized)) {
                return RuntimeSide.DEDICATED_SERVER;
            }
            if ("unknown".equals(normalized)) {
                return RuntimeSide.UNKNOWN;
            }
            throw new IllegalStateException("Unsupported " + SIDE_PROPERTY + " value: " + override);
        }

        boolean client = classExists("net.minecraft.client.Minecraft");
        boolean server = classExists("net.minecraft.server.MinecraftServer");
        if (client && !server) {
            return RuntimeSide.CLIENT;
        }
        if (server && !client) {
            return RuntimeSide.DEDICATED_SERVER;
        }
        return RuntimeSide.UNKNOWN;
    }

    private static boolean classExists(String name) {
        // Defining a hook target during premain would bypass the not-yet-installed
        // transformer.
        ClassLoader loader = RuntimeEnvironment.class.getClassLoader();
        return loader.getResource(name.replace('.', '/') + ".class") != null;
    }
}
