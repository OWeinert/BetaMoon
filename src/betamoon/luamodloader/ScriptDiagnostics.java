package betamoon.luamodloader;

import betamoon.config.BetaMoonConfig;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Captures occurrence-time context without retaining worlds, Lua values, or exception objects. */
public final class ScriptDiagnostics {
    public interface ClientContext {
        Map<String, String> occurrence();

        Map<String, List<String>> environment();
    }

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final Map<Throwable, Occurrence> FAILURES = new WeakHashMap<>();
    private static volatile ClientContext client;
    private static volatile BetaMoonConfig config;

    private ScriptDiagnostics() {
    }

    public static void initializeClient(ClientContext context) {
        client = context;
    }

    static void configure(BetaMoonConfig value) {
        config = value;
    }

    public static Scope scope(String phase, String operation) {
        return new Scope(phase, operation, null);
    }

    /** Cheap logging context; never collects world, screen, or entity diagnostics. */
    public static String loggingOperation() {
        for (Scope scope = CURRENT.get(); scope != null; scope = scope.previous) {
            if (!"callback".equals(scope.operation) && !"unavailable".equals(scope.operation)) {
                return scope.operation;
            }
        }
        return null;
    }

    /** The supplier runs only when an issue occurs, while the callback's world is still available. */
    public static Scope gameContext(Supplier<Map<String, String>> supplier) {
        Scope previous = CURRENT.get();
        return new Scope(null, previous == null ? null : previous.operation, supplier);
    }

    static void remember(Throwable error) {
        Occurrence occurrence = capture();
        synchronized (FAILURES) {
            FAILURES.put(error, occurrence);
        }
    }

    static Occurrence occurrence(Throwable error) {
        if (error != null) {
            synchronized (FAILURES) {
                Occurrence captured = FAILURES.remove(error);
                if (captured != null) {
                    return captured;
                }
            }
        }
        return capture();
    }

    private static Occurrence capture() {
        Scope scope = CURRENT.get();
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        ScriptMod script = owner == null ? null : LuaScriptRegistry.findByFile(owner);
        return new Occurrence(System.currentTimeMillis(), uptime(),
                scope == null ? "runtime" : scope.phase,
                scope == null ? "unavailable" : scope.operation, owner, script,
                Thread.currentThread().getName(), scope != null && scope.game != null
                        ? safeOccurrence(scope.game) : clientOccurrence());
    }

    static Map<String, String> clientOccurrence() {
        ClientContext provider = client;
        if (provider == null) {
            return Collections.emptyMap();
        }
        return safeOccurrence(provider::occurrence);
    }

    private static Map<String, String> safeOccurrence(Supplier<Map<String, String>> supplier) {
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(supplier.get()));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return Collections.singletonMap("Game context", "unavailable (" + failure.getClass().getSimpleName() + ")");
        }
    }

    static Map<String, List<String>> clientEnvironment() {
        ClientContext provider = client;
        if (provider == null) {
            return Collections.singletonMap("Client environment", Collections.singletonList("unavailable"));
        }
        try {
            return provider.environment();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return Collections.singletonMap("Client environment", Collections.singletonList(
                    "unavailable (" + failure.getClass().getSimpleName() + ")"));
        }
    }

    static Map<String, String> settings() {
        BetaMoonConfig value = config;
        return value == null ? Collections.singletonMap("Configuration", "unavailable") : value.diagnosticSettings();
    }

    static long uptime() {
        try {
            return ManagementFactory.getRuntimeMXBean().getUptime();
        } catch (RuntimeException | LinkageError failure) {
            return -1;
        }
    }

    /** Optional diagnostic collectors must not conceal VM termination or resource exhaustion. */
    public static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError) {
            throw (VirtualMachineError) failure;
        }
        if (failure instanceof ThreadDeath) {
            throw (ThreadDeath) failure;
        }
    }

    static String scriptState(ScriptMod script) {
        if (script == null) {
            return "unavailable";
        }
        if (script.isFailed()) {
            return "failed";
        }
        return script.isLoaded() ? "loaded" : "pending";
    }

    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final String phase;
        private final String operation;
        private final Supplier<Map<String, String>> game;
        private boolean closed;

        private Scope(String phase, String operation, Supplier<Map<String, String>> game) {
            previous = CURRENT.get();
            this.phase = phase == null ? previous == null ? "runtime" : previous.phase : phase;
            this.operation = operation == null ? "unavailable" : operation;
            this.game = game == null && previous != null ? previous.game : game;
            CURRENT.set(this);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (previous == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(previous);
                }
            }
        }
    }

    public static final class Occurrence {
        public final long timestampMillis;
        public final long uptimeMillis;
        public final String phase;
        public final String operation;
        public final String owner;
        public final String scriptDisplayName;
        public final String scriptVersion;
        public final String scriptState;
        public final String thread;
        public final Map<String, String> game;

        private Occurrence(long timestampMillis, long uptimeMillis, String phase, String operation, String owner,
                ScriptMod script, String thread, Map<String, String> game) {
            this.timestampMillis = timestampMillis;
            this.uptimeMillis = uptimeMillis;
            this.phase = phase;
            this.operation = operation;
            this.owner = owner;
            scriptDisplayName = script == null ? null : script.getDisplayName();
            scriptVersion = script == null ? null : script.getVersion();
            scriptState = ScriptDiagnostics.scriptState(script);
            this.thread = thread;
            this.game = game;
        }
    }
}
