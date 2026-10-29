package betamoon.interop.client;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.interop.runtime.NativePermissions;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Java-host boundary for preparation, paint and authorized input; no Lua
 * scheduling/purity API is added.
 */
public final class NativeClientSession implements AutoCloseable {
    private final NativeClientSessions manager;
    private final String owner;
    private final long generation;
    private final ScriptResourceTracker.OwnerGeneration ownerState;
    private final Snapshot environment;
    private final ScriptResourceTracker.Cleanup cleanup = this::close;
    private final Map<Long, Resource<?>> resources = new HashMap<>();
    private final Map<Long, Action> actions = new HashMap<>();
    private final Deque<Action> pending = new ArrayDeque<>();
    private volatile Supplier<Map<String, Object>> roots;
    private volatile boolean closed;
    private boolean tracked;
    private long nextIdentity;

    NativeClientSession(NativeClientSessions manager, String owner, Supplier<Map<String, Object>> roots) {
        this.manager = manager;
        this.owner = owner;
        this.roots = roots;
        ownerState = ScriptResourceTracker.ownerToken(owner);
        generation = ownerState.current();
        Map<String, Object> current = roots.get();
        if (current == null || current.get("client") == null) {
            throw new NativeAccessFailure("native_client_unavailable", "a client host requires an actual client root");
        }
        environment = new Snapshot(current);
    }

    void track() {
        ScriptExecution.withOwner(owner, () -> {
            ScriptResourceTracker.track(cleanup);
            return null;
        });
        tracked = true;
        if (!ownerState.matches(generation)) {
            close();
            throw new NativeAccessFailure("stale_owner", "client host owner changed during creation");
        }
    }

    public boolean active() {
        manager.bindings.requireThread();
        if (closed) {
            return false;
        }
        try {
            check();
            return true;
        } catch (NativeAccessFailure unavailable) {
            return false;
        }
    }

    public void prepare(Runnable initializer) {
        boolean[] started = {false};
        try {
            enter(NativeEntry.Phase.CLIENT_PREPARATION, NativePermissions.CLIENT_PREPARATION, true, () -> {
                started[0] = true;
                initializer.run();
            });
        } catch (VirtualMachineError | ThreadDeath fatal) {
            close();
            throw fatal;
        } catch (RuntimeException | Error failure) {
            if (started[0]) {
                manager.failure("native_client_preparation_failed", owner, "client preparation", failure);
                close();
            }
            throw failure;
        }
    }

    public void paint(Runnable painter) {
        enter(NativeEntry.Phase.QUERY, NativePermissions.READ_ONLY, false, painter);
    }

    public void input(Consumer<NativeClientActionPermit> callback) {
        NativeClientActionPermit permit = new NativeClientActionPermit(this);
        try {
            enter(NativeEntry.Phase.CLIENT_ACTION, NativePermissions.CLIENT_ACTION, true,
                    () -> callback.accept(permit));
        } finally {
            permit.active = false;
        }
    }

    public <T> NativeClientResource<T> resource(Supplier<T> factory, Consumer<T> disposer) {
        NativeContext context = current();
        context.requireClientResource();
        if (factory == null || disposer == null) {
            throw new NativeAccessFailure("invalid_client_resource", "resource factory and disposer are required");
        }
        synchronized (manager) {
            if (resources.size() >= 256) {
                throw new NativeAccessFailure("native_client_limit", "a client host supports at most 256 resources");
            }
        }
        manager.reserve();
        T value = null;
        boolean acquired = false;
        boolean published = false;
        try {
            value = factory.get();
            acquired = value != null;
            context.check();
            check();
            if (!acquired) {
                throw new NativeAccessFailure("native_client_resource_unavailable",
                        "resource preparation returned no value");
            }
            synchronized (manager) {
                if (closed || resources.size() >= 256) {
                    throw new NativeAccessFailure("native_client_limit",
                            "client host closed or resource limit exceeded during preparation");
                }
                NativeClientResource<T> handle = new NativeClientResource<>(this, ++nextIdentity);
                resources.put(handle.identity, new Resource<>(value, disposer));
                manager.publishedResource();
                published = true;
                return handle;
            }
        } finally {
            if (!published) {
                if (acquired) {
                    manager.defer(owner, value, disposer, false);
                } else {
                    manager.abandon();
                }
            }
        }
    }

    /**
     * Java hosts register concrete known commands; Lua authors cannot declare
     * arbitrary methods safe.
     */
    public NativeClientAction action(String id, Runnable operation, boolean terminal) {
        current().requireClientResource();
        if (id == null || id.isEmpty() || id.length() > 128 || operation == null) {
            throw new NativeAccessFailure("invalid_client_action", "use a named prepared Java host command");
        }
        synchronized (manager) {
            if (closed || actions.size() >= 128) {
                throw new NativeAccessFailure("native_client_limit", "client host closed or action limit exceeded");
            }
            long identity = ++nextIdentity;
            actions.put(identity, new Action(id, operation, terminal));
            return new NativeClientAction(this, identity);
        }
    }

    public void submit(NativeClientAction action, NativeClientActionPermit permit) {
        current().requireClientAction();
        if (permit == null || !permit.active || permit.thread != Thread.currentThread() || permit.session.get() != this
                || action == null || action.session.get() != this) {
            throw new NativeAccessFailure("native_client_action_denied",
                    "submit only during this host's authorized input");
        }
        synchronized (manager) {
            Action prepared = actions.get(action.identity);
            if (closed || prepared == null || permit.submitted >= 64 || pending.size() >= 128) {
                throw new NativeAccessFailure("native_client_action_denied",
                        "action expired or input/queue limit exceeded");
            }
            permit.submitted++;
            pending.addLast(prepared);
            manager.queuedAction();
        }
    }

    @SuppressWarnings("unchecked")
    <T> T use(NativeClientResource<T> handle) {
        current();
        synchronized (manager) {
            Resource<?> entry = resources.get(handle.identity);
            if (closed || entry == null) {
                throw new NativeAccessFailure("native_client_resource_closed", "prepared resource is no longer active");
            }
            return (T) entry.value;
        }
    }

    void release(NativeClientResource<?> handle) {
        synchronized (manager) {
            if (closed || !resources.containsKey(handle.identity)) {
                return;
            }
        }
        NativeContext context = current();
        try {
            context.requireClientResource();
        } catch (NativeAccessFailure denied) {
            if (!denied.reason.equals("native_phase_denied")) {
                throw denied;
            }
            context.requireClientAction();
        }
        synchronized (manager) {
            Resource<?> entry = resources.remove(handle.identity);
            if (entry != null) {
                entry.defer(manager, owner);
            }
        }
    }

    void observe() {
        if (!closed) {
            active();
        }
    }

    void flush() {
        synchronized (manager) {
            if (pending.isEmpty()) {
                return;
            }
        }
        if (!NativeClientSessions.safeBoundary() || !active()) {
            return;
        }
        for (int count = 0; count < 64; count++) {
            Action action;
            synchronized (manager) {
                action = pending.pollFirst();
                if (action != null) {
                    manager.removedActions(1, false);
                }
            }
            if (action == null) {
                return;
            }
            try {
                if (action.terminal) {
                    // A validated navigation/close command executes at a fresh Java lifecycle
                    // boundary.
                    // New-screen preparation cannot inherit an invalidated old-screen native frame.
                    check();
                    manager.urgentCleanup();
                    try (LuaCallbackScope scope = new LuaCallbackScope(false, true)) {
                        action.operation.run();
                    }
                    close();
                    return;
                }
                enter(NativeEntry.Phase.CLIENT_ACTION, NativePermissions.CLIENT_ACTION, true, () -> {
                    NativeContext.current().requireClientAction();
                    action.operation.run();
                });
            } catch (VirtualMachineError | ThreadDeath fatal) {
                close();
                throw fatal;
            } catch (Throwable failure) {
                manager.failure("native_client_action_failed", owner, action.id, failure);
                close();
                return;
            }
        }
    }

    private void enter(NativeEntry.Phase phase, NativePermissions permissions, boolean uiMutable, Runnable callback) {
        check();
        String source = LuaScriptRegistry.getCurrentScriptFile();
        if (source != null && !source.equals(owner)) {
            throw new NativeAccessFailure("wrong_owner", "client host belongs to another script");
        }
        ScriptExecution.withOwner(owner, () -> {
            Map<String, Object> captured = currentRoots();
            try (LuaCallbackScope scope = new LuaCallbackScope(false, uiMutable);
                    NativeContext context = NativeContext.open(manager.bindings, owner, scope, permissions, captured,
                            this::validEnvironment, phase)) {
                if (phase == NativeEntry.Phase.CLIENT_PREPARATION) {
                    context.requireClientResource();
                } else if (phase == NativeEntry.Phase.CLIENT_ACTION) {
                    context.requireClientAction();
                } else {
                    context.check();
                }
                callback.run();
                context.check();
                check();
            }
            return null;
        });
    }

    private NativeContext current() {
        check();
        NativeContext context = NativeContext.current();
        if (context.bindings != manager.bindings || !owner.equals(context.owner())) {
            throw new NativeAccessFailure("wrong_owner", "use the resource/command through its owning client host");
        }
        return context;
    }

    private void check() {
        manager.bindings.requireThread();
        if (closed) {
            throw new NativeAccessFailure("native_client_resource_closed", "client host is closed");
        }
        if (!ownerState.matches(generation)) {
            close();
            throw new NativeAccessFailure("stale_owner", "client host owner was unloaded or reloaded");
        }
        if (!validEnvironment()) {
            close();
            throw new NativeAccessFailure("stale_environment", "client, screen, world or player identity changed");
        }
    }

    private boolean validEnvironment() {
        Supplier<Map<String, Object>> provider = roots;
        if (closed || provider == null) {
            return false;
        }
        try {
            return environment.matches(provider.get());
        } catch (VirtualMachineError | ThreadDeath fatal) {
            close();
            throw fatal;
        } catch (Throwable failure) {
            manager.failure("native_client_environment_failed", owner, "client environment", failure);
            close();
            return false;
        }
    }

    private Map<String, Object> currentRoots() {
        check();
        return new LinkedHashMap<>(roots.get());
    }

    @Override
    public void close() {
        List<Resource<?>> released;
        synchronized (manager) {
            if (closed) {
                return;
            }
            closed = true;
            roots = null;
            manager.retire(this);
            manager.removedActions(pending.size(), true);
            pending.clear();
            actions.clear();
            released = new ArrayList<>(resources.values());
            resources.clear();
            for (Resource<?> entry : released) {
                entry.defer(manager, owner);
            }
        }
        if (tracked) {
            ScriptResourceTracker.untrack(owner, cleanup);
            tracked = false;
        }
    }

    private static final class Snapshot {
        private final Map<String, WeakReference<Object>> values = new HashMap<>();

        private Snapshot(Map<String, Object> roots) {
            for (Map.Entry<String, Object> entry : roots.entrySet()) {
                values.put(entry.getKey(), entry.getValue() == null ? null : new WeakReference<>(entry.getValue()));
            }
        }

        private boolean matches(Map<String, Object> roots) {
            if (roots == null || !roots.keySet().equals(values.keySet())) {
                return false;
            }
            for (Map.Entry<String, WeakReference<Object>> entry : values.entrySet()) {
                Object current = roots.get(entry.getKey());
                WeakReference<Object> reference = entry.getValue();
                if (reference == null ? current != null : reference.get() == null || reference.get() != current) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class Resource<T> {
        private final T value;
        private final Consumer<T> disposer;

        private Resource(T value, Consumer<T> disposer) {
            this.value = value;
            this.disposer = disposer;
        }

        private void defer(NativeClientSessions manager, String owner) {
            manager.defer(owner, value, disposer, true);
        }
    }

    private static final class Action {
        private final String id;
        private final Runnable operation;
        private final boolean terminal;

        private Action(String id, Runnable operation, boolean terminal) {
            this.id = id;
            this.operation = operation;
            this.terminal = terminal;
        }
    }
}
