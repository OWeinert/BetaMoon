package betamoon.interop.client;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owner-bound client lifecycles and disposal, independent from gameplay-owned
 * native objects.
 */
public final class NativeClientSessions {
    final NativeBindings bindings;
    final Map<NativeClientSession, Boolean> sessions = new IdentityHashMap<>();
    private final Deque<Disposal> pending = new ArrayDeque<>();
    private int resourceSlots;
    private int activeResources;
    private int pendingActions;
    private long disposed;
    private long failures;
    private long cancelledActions;
    private boolean closed;

    public NativeClientSessions(NativeBindings bindings) {
        this.bindings = bindings;
    }

    public NativeClientSession create(String owner, Supplier<Map<String, Object>> roots) {
        bindings.requireThread();
        if (owner == null || owner.isEmpty() || owner.length() > 1024) {
            throw new NativeAccessFailure("invalid_client_owner", "client hosts require an owner");
        }
        if (NativeContext.hasFrame()) {
            NativeContext.current().requireClientResource();
        }
        String source = LuaScriptRegistry.getCurrentScriptFile();
        if (source != null && !owner.equals(source)) {
            throw new NativeAccessFailure("wrong_owner", "create client hosts for the current owner");
        }
        NativeClientSession result = new NativeClientSession(this, owner, roots);
        synchronized (this) {
            if (closed || sessions.size() >= 128) {
                throw new NativeAccessFailure("native_client_limit", "client session limit exceeded or session ended");
            }
            sessions.put(result, Boolean.TRUE);
        }
        result.track();
        return result;
    }

    public void observe() {
        bindings.requireThread();
        List<NativeClientSession> copy;
        synchronized (this) {
            copy = new ArrayList<>(sessions.keySet());
        }
        for (NativeClientSession session : copy) {
            session.observe();
        }
        if (!safeBoundary()) {
            return;
        }
        for (NativeClientSession session : copy) {
            session.flush();
        }
        drain();
    }

    public synchronized void requireCloseBoundary() {
        bindings.requireThread();
        if (resourceSlots > 0 && !NativeContext.clientCleanupAllowed()) {
            throw new NativeAccessFailure("native_phase_denied",
                    "close native client resources outside paint/query scopes");
        }
    }

    public void close() {
        bindings.requireThread();
        requireCloseBoundary();
        List<NativeClientSession> copy;
        synchronized (this) {
            closed = true;
            copy = new ArrayList<>(sessions.keySet());
        }
        for (NativeClientSession session : copy) {
            session.close();
        }
        drain();
    }

    public synchronized Map<String, Object> diagnostics() {
        return CatalogValues.record("sessions", sessions.size(), "resources", activeResources, "resourceSlots",
                resourceSlots, "pendingDisposals", pending.size(), "pendingActions", pendingActions, "disposed",
                disposed, "failures", failures, "cancelledActions", cancelledActions);
    }

    static boolean safeBoundary() {
        if (NativeContext.hasFrame() || LuaScriptRegistry.getCurrentScriptFile() != null) {
            return false;
        }
        try {
            LuaCallbackScope.requireNoBindingMutation();
            LuaCallbackScope.requireUiStateWrite();
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    synchronized void reserve() {
        if (closed || resourceSlots >= 512) {
            throw new NativeAccessFailure("native_client_limit",
                    "client resources are limited to 512 live/pending slots");
        }
        resourceSlots++;
    }

    synchronized void abandon() {
        resourceSlots--;
    }

    synchronized void publishedResource() {
        activeResources++;
    }

    <T> void defer(String owner, T value, Consumer<T> disposer, boolean published) {
        boolean closing;
        synchronized (this) {
            if (published) {
                activeResources--;
            }
            pending.addLast(new Disposal(owner, () -> disposer.accept(value)));
            closing = closed;
        }
        if (closing && !Thread.holdsLock(this)) {
            drain();
        }
    }

    synchronized void queuedAction() {
        pendingActions++;
    }

    synchronized void removedActions(int count, boolean cancelled) {
        pendingActions -= count;
        if (cancelled) {
            cancelledActions += count;
        }
    }

    void failure(String reason, String owner, String id, Throwable error) {
        synchronized (this) {
            failures++;
        }
        bindings.recordFailure(new NativeAccessFailure(reason, id, error)
                .explain(CatalogValues.record("owner", owner, "phase", "client_lifecycle")));
    }

    private void drain() {
        if (!bindings.onClientThread() || !NativeContext.clientCleanupAllowed()) {
            return;
        }
        for (int count = 0; count < 512; count++) {
            Disposal disposal = nextDisposal();
            if (disposal == null) {
                return;
            }
            try {
                ScriptExecution.withOwner(disposal.owner, () -> {
                    try (LuaCallbackScope callback = new LuaCallbackScope(false, false);
                            NativeContext ignored = NativeContext.denied(disposal.owner)) {
                        disposal.action.run();
                    }
                    return null;
                });
                markDisposed();
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable error) {
                failure("native_client_disposal_failed", disposal.owner, "client resource disposal", error);
            } finally {
                abandon();
            }
        }
    }

    private synchronized Disposal nextDisposal() {
        return pending.pollFirst();
    }

    private synchronized void markDisposed() {
        disposed++;
    }

    private static final class Disposal {
        private final String owner;
        private final Runnable action;

        private Disposal(String owner, Runnable action) {
            this.owner = owner;
            this.action = action;
        }
    }
}
