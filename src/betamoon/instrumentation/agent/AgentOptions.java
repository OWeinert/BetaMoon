package betamoon.instrumentation.agent;

/** Parsed options supplied after the optional equals sign in {@code -javaagent}. */
public final class AgentOptions {
    private final boolean strict;
    private final boolean debug;

    private AgentOptions(boolean strict, boolean debug) {
        this.strict = strict;
        this.debug = debug;
    }

    public static AgentOptions parse(String rawOptions) {
        boolean strict = false;
        boolean debug = false;
        if (rawOptions != null && rawOptions.trim().length() > 0) {
            String[] entries = rawOptions.split(",");
            for (String entry : entries) {
                String[] pair = entry.trim().split("=", 2);
                String key = pair[0].trim();
                String value = pair.length == 2 ? pair[1].trim() : "true";
                if ("strict".equalsIgnoreCase(key)) {
                    strict = parseBoolean(key, value);
                } else if ("debug".equalsIgnoreCase(key)) {
                    debug = parseBoolean(key, value);
                } else if (key.length() > 0) {
                    throw new IllegalArgumentException("Unknown BetaMoon agent option: " + key);
                }
            }
        }
        return new AgentOptions(strict, debug);
    }

    public boolean isStrict() {
        return strict;
    }

    public boolean isDebug() {
        return debug;
    }

    private static boolean parseBoolean(String key, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Option " + key + " expects true or false");
    }
}
