package betamoon.hooks;

import betamoon.interop.binding.NativeAccessFailure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.LuaValue;

/**
 * Explicit file/attempt authority. There is no process-wide current collector.
 */
public final class HookFileCollectionContext implements AutoCloseable {
    private static final ThreadLocal<HookFileCollectionContext> CURRENT = new ThreadLocal<>();
    public final String owner;
    public final String file;
    public final long attempt;
    public final boolean runtime;
    private final Thread thread = Thread.currentThread();
    private final List<Registration> entries = new ArrayList<>();
    private boolean created;
    private boolean active = true;
    private boolean sealed;
    private LuaValue registry;
    private final HookFileCollectionContext previous;
    private final String sourceHash;

    public HookFileCollectionContext(String owner, String file, long attempt, boolean runtime) {
        this(owner, file, attempt, runtime, "");
    }

    public HookFileCollectionContext(String owner, String file, long attempt, boolean runtime, String sourceHash) {
        this.owner = owner;
        this.file = file;
        this.attempt = attempt;
        this.runtime = runtime;
        this.sourceHash = sourceHash;
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public void createRegistry(LuaValue registry) {
        check();
        if (created) {
            throw new HookFailure("hook_registry_already_created", file);
        }
        created = true;
        this.registry = registry;
    }

    public HookDefinition register(LuaValue definition, LuaValue callback) {
        check();
        if (!created || registry == null) {
            throw new HookFailure("hook_registry_context_mismatch", file);
        }
        if (!callback.isfunction() || entries.size() >= 64) {
            throw new HookFailure("hook_file_invalid", file + ": callback function required; limit 64 hooks/package");
        }
        HookDefinition parsed;
        try {
            parsed = HookDefinition.read(owner, file, definition);
        } catch (IllegalArgumentException | NativeAccessFailure error) {
            throw new HookFailure("hook_file_invalid", file + ": " + error.getMessage(), error);
        }
        parsed = parsed.withSourceHash(sourceHash);
        for (Registration entry : entries) {
            if (entry.definition.id.equals(parsed.id)) {
                throw new HookFailure("hook_file_invalid", file + ": duplicate hook id " + parsed.id);
            }
        }
        entries.add(new Registration(parsed, runtime ? callback : null));
        return parsed;
    }

    public List<Registration> seal() {
        check();
        if (!created) {
            throw new HookFailure("hook_registry_missing", file);
        }
        sealed = true;
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    private void check() {
        if (Thread.currentThread() != thread) {
            throw new HookFailure("hook_registry_context_mismatch", file + ": wrong evaluation thread");
        }
        if (!active || sealed) {
            throw new HookFailure("hook_registry_closed", file + ": attempt " + attempt);
        }
        if (CURRENT.get() != this) {
            throw new HookFailure("hook_registry_context_mismatch", file + ": another file/attempt is evaluating");
        }
    }

    @Override
    public void close() {
        active = false;
        entries.clear();
        registry = null;
        if (CURRENT.get() == this) {
            HookFileCollectionContext restore = previous;
            while (restore != null && !restore.active) {
                restore = restore.previous;
            }
            if (restore == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(restore);
            }
        }
    }

    public static final class Registration {
        public final HookDefinition definition;
        public final LuaValue callback;

        Registration(HookDefinition definition, LuaValue callback) {
            this.definition = definition;
            this.callback = callback;
        }
    }
}
