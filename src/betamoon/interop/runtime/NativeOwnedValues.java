package betamoon.interop.runtime;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.hosts.NativeHostShape;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.LuaScriptErrors;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Owner/generation cleanup without retaining Lua closures in handles or
 * entries.
 */
public final class NativeOwnedValues {
    private final NativeBindings bindings;
    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private final Map<Object, NativeOwnedValue> identities = new IdentityHashMap<>();
    private final Map<String, OwnerCleanup> trackedOwners = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<String> expiredOwners = new ConcurrentLinkedQueue<>();
    private final Deque<Entry> pending = new ArrayDeque<>();
    private Object observedWorld;
    private Object observedPlayer;
    private boolean worldDirty;
    private int publishedActive = -1;
    private int publishedPending = -1;
    private long publishedDisposed = -1;
    private long publishedFailures = -1;
    private int urgentPending;
    private int publishingSuspended;
    private volatile long oldestQueuedNanos;
    private long nextIdentity;
    private long disposed;
    private long failures;
    private boolean closed;
    private volatile Map<String, Object> status = CatalogValues.record("active", 0, "pendingDisposals", 0, "disposed",
            0L, "disposalFailures", 0L);

    public NativeOwnedValues(NativeBindings bindings) {
        this.bindings = bindings;
    }

    public NativeOwnedValue retain(NativeContext context, Object value, String lifetime, NativeMemberPlan disposer) {
        return retain(context, value, lifetime, disposer, false, false);
    }

    public NativeOwnedValue retainGeneratedHost(NativeContext context, Object value, String lifetime,
            NativeMemberPlan disposer, NativeHostShape shape) {
        shape.requireInstance(value);
        return retain(context, value, lifetime, disposer, true, shape.clientScreen(bindings));
    }

    private NativeOwnedValue retain(NativeContext context, Object value, String lifetime, NativeMemberPlan disposer,
            boolean generatedHost, boolean clientResource) {
        if (clientResource) {
            context.requireClientResource();
        } else {
            context.require(NativeContext.Effect.WRITE, true);
        }
        if (closed || context.bindings != bindings) {
            throw new NativeAccessFailure("wrong_native_session", "owned values belong to one native session");
        }
        expireOwners();
        String owner = context.owner();
        if (owner == null) {
            throw new NativeAccessFailure("native_owner_required", "retention requires an owned script callback");
        }
        if (!lifetime.equals("owner") && !lifetime.equals("world")) {
            throw new NativeAccessFailure("invalid_native_lifetime", "choose owner or world explicitly");
        }
        if (context.isRoot(value) || !generatedHost && bindings.borrowedRootType(value.getClass())) {
            throw new NativeAccessFailure("borrowed_root",
                    "live client/world/entity/tile/screen roots cannot be owned");
        }
        if (identities.containsKey(value)) {
            throw new NativeAccessFailure("already_owned", "reuse the existing owned handle");
        }
        long generation = ScriptResourceTracker.ownerGeneration(owner);
        String tracking = owner + ":" + generation;
        OwnerCleanup ownerCleanup = trackedOwners.get(tracking);
        int count = ownerCleanup == null ? 0 : ownerCleanup.entries.size();
        if (entries.size() >= 2048 || count >= 256 || pending.size() >= 2048) {
            throw new NativeAccessFailure("native_owned_limit", "native ownership registry is full");
        }
        Object world = lifetime.equals("world") ? context.root("world") : null;
        Object player = lifetime.equals("world") ? context.root("player") : null;
        if (lifetime.equals("world") && world == null) {
            throw new NativeAccessFailure("world_unavailable", "world lifetime requires a current world root");
        }
        Method method = disposer == null ? null : disposerMethod(disposer, value);
        NativeOwnedValue token = new NativeOwnedValue(this, ++nextIdentity, owner, generation, lifetime,
                bindings.reference(value.getClass()));
        context.trackOwnership(value, token);
        if (ownerCleanup == null) {
            ScriptResourceTracker.Cleanup cleanup = () -> {
                expiredOwners.add(tracking);
                if (bindings.onClientThread()) {
                    expireOwners();
                }
            };
            ownerCleanup = new OwnerCleanup(owner, generation, cleanup);
            trackedOwners.put(tracking, ownerCleanup);
            ScriptResourceTracker.track(cleanup);
        }
        Entry entry = new Entry(token, value, method, world, player, clientResource, ownerCleanup);
        entries.put(token.identity, entry);
        ownerCleanup.entries.put(token.identity, entry);
        identities.put(value, token);
        worldDirty |= lifetime.equals("world");
        publish();
        return token;
    }

    public NativeOwnedValue find(Object value) {
        bindings.requireThread();
        return identities.get(value);
    }

    /**
     * Java-owned resources can disable dispatch before their native payload is
     * dropped.
     */
    public void onInvalidation(NativeOwnedValue token, Consumer<String> action) {
        bindings.requireThread();
        Entry entry = entries.get(token.identity);
        if (entry == null || entry.token != token || entry.invalidation != null) {
            throw new NativeAccessFailure("owned_value_closed", "cannot install a resource lifecycle");
        }
        entry.invalidation = action;
    }

    public void invalidate(NativeOwnedValue token, String reason) {
        bindings.requireThread();
        Entry entry = entries.get(token.identity);
        if (entry != null && entry.token == token) {
            invalidate(entry, reason);
        }
    }

    Object use(NativeOwnedValue token, NativeContext context) {
        context.check();
        owner(token, context);
        Entry entry = entries.get(token.identity);
        if (entry == null) {
            throw new NativeAccessFailure("owned_value_closed", token.state);
        }
        if (!token.state.equals("active")) {
            throw new NativeAccessFailure("owned_value_closed", token.state);
        }
        if (token.lifetime.equals("world")) {
            Object world = context.root("world");
            if (world == null) {
                throw new NativeAccessFailure("world_unavailable", "use this handle in its world callback");
            }
            if (entry.world != world || entry.player != context.root("player")) {
                invalidate(entry, "stale_world");
                throw new NativeAccessFailure("stale_world", "owned world/player identity changed");
            }
        }
        return entry.value;
    }

    void release(NativeOwnedValue token, NativeContext context, boolean direct) {
        context.check();
        owner(token, context);
        Entry entry = entries.get(token.identity);
        if (entry != null && entry.clientResource) {
            if (!direct) {
                throw new NativeAccessFailure("direct_required",
                        "release generated client hosts through direct access");
            }
            try {
                context.requireClientResource();
            } catch (NativeAccessFailure denied) {
                if (!denied.reason.equals("native_phase_denied")) {
                    throw denied;
                }
                context.requireClientAction();
            }
        } else {
            context.require(NativeContext.Effect.UNKNOWN, direct);
        }
        if (entry != null) {
            invalidate(entry, "released");
        }
        drain();
    }

    /**
     * Invoked at the client lifecycle boundary before dispatching the next
     * callbacks.
     */
    public void observe(Object world, Object player) {
        bindings.requireThread();
        expireOwners();
        if (worldDirty || observedWorld != world || observedPlayer != player) {
            observedWorld = world;
            observedPlayer = player;
            worldDirty = false;
            for (Entry entry : reverseEntries()) {
                if (entry.token.lifetime.equals("world") && (entry.world != world || entry.player != player)) {
                    invalidate(entry, "stale_world");
                }
            }
        }
        drain();
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>(status);
        long oldest = oldestQueuedNanos;
        result.put("oldestPendingMillis", oldest == 0 ? 0L : (System.nanoTime() - oldest) / 1000000L);
        return result;
    }

    public void close() {
        bindings.requireThread();
        closed = true;
        observedWorld = null;
        observedPlayer = null;
        for (Entry entry : reverseEntries()) {
            invalidate(entry, "session_closed");
        }
        drain();
        if (!pending.isEmpty()) {
            failures += pending.size();
            for (Entry entry : pending) {
                LuaScriptErrors.add(entry.token.owner,
                        "Native disposer could not run before session close: " + entry.token.type.name());
                entry.value = null;
            }
            pending.clear();
        }
        for (OwnerCleanup cleanup : new ArrayList<>(trackedOwners.values())) {
            ScriptResourceTracker.untrack(cleanup.owner, cleanup.action);
        }
        trackedOwners.clear();
        expiredOwners.clear();
        publish();
    }

    private void owner(NativeOwnedValue token, NativeContext context) {
        if (context.bindings != bindings) {
            throw new NativeAccessFailure("wrong_native_session", "owned value belongs to another session");
        }
        if (!token.owner.equals(context.ownerIdentity())) {
            throw new NativeAccessFailure("wrong_owner", "owned value belongs to another script");
        }
        if (!token.generationMatches()) {
            Entry entry = entries.get(token.identity);
            if (entry != null) {
                invalidate(entry, "stale_owner");
            }
            throw new NativeAccessFailure("stale_owner", "owned value belongs to an unloaded generation");
        }
    }

    public void checkOwner(NativeOwnedValue token, NativeContext context) {
        context.check();
        owner(token, context);
    }

    private void invalidate(Entry entry, String reason) {
        if (entries.remove(entry.token.identity) == null) {
            return;
        }
        identities.remove(entry.value);
        entry.ownerCleanup.entries.remove(entry.token.identity);
        entry.token.state = reason;
        if (entry.invalidation != null) {
            Consumer<String> action = entry.invalidation;
            entry.invalidation = null;
            action.accept(reason);
        }
        if (entry.disposer != null) {
            entry.queuedNanos = System.nanoTime();
            pending.addLast(entry);
            if (entry.clientResource) {
                urgentPending++;
            }
        } else {
            entry.value = null;
        }
        publish();
    }

    private void drain() {
        if (!bindings.onClientThread()) {
            return;
        }
        expireOwners();
        if (pending.isEmpty() || !NativeContext.cleanupAllowed()) {
            return;
        }
        NativeCleanupSlice slice = new NativeCleanupSlice(closed || urgentPending > 0);
        while (!pending.isEmpty() && slice.next()) {
            Entry entry = pending.removeFirst();
            if (entry.clientResource) {
                urgentPending--;
            }
            try (NativeContext ignored = NativeContext.denied(entry.token.owner)) {
                ScriptExecution.withOwner(entry.token.owner, () -> {
                    try {
                        entry.disposer.invoke(entry.value);
                    } catch (InvocationTargetException exception) {
                        throw new NativeAccessFailure("native_disposal_failed", entry.token.type.name(),
                                exception.getCause());
                    } catch (IllegalAccessException exception) {
                        throw new NativeAccessFailure("access_denied", entry.token.type.name(), exception);
                    }
                    return null;
                });
                disposed++;
            } catch (RuntimeException | LinkageError exception) {
                disposalFailure(entry,
                        exception instanceof NativeAccessFailure && exception.getCause() != null
                                ? exception.getCause()
                                : exception);
            } finally {
                entry.value = null;
                publish();
            }
        }
        publish();
    }

    private void expireOwners() {
        if (expiredOwners.isEmpty()) {
            return;
        }
        publishingSuspended++;
        try {
            String tracking;
            while ((tracking = expiredOwners.poll()) != null) {
                OwnerCleanup cleanup = trackedOwners.remove(tracking);
                if (cleanup != null) {
                    List<Entry> owned = new ArrayList<>(cleanup.entries.values());
                    Collections.reverse(owned);
                    for (Entry entry : owned) {
                        invalidate(entry, "stale_owner");
                    }
                }
            }
        } finally {
            publishingSuspended--;
            publish();
        }
    }

    private List<Entry> reverseEntries() {
        List<Entry> result = new ArrayList<>(entries.values());
        Collections.reverse(result);
        return result;
    }

    private void publish() {
        if (publishingSuspended > 0) {
            return;
        }
        oldestQueuedNanos = pending.isEmpty() ? 0L : pending.peekFirst().queuedNanos;
        if (publishedActive == entries.size() && publishedPending == pending.size() && publishedDisposed == disposed
                && publishedFailures == failures) {
            return;
        }
        publishedActive = entries.size();
        publishedPending = pending.size();
        publishedDisposed = disposed;
        publishedFailures = failures;
        status = CatalogValues.record("active", entries.size(), "pendingDisposals", pending.size(), "disposed",
                disposed, "disposalFailures", failures);
    }

    private static final class OwnerCleanup {
        private final String owner;
        private final long generation;
        private final ScriptResourceTracker.Cleanup action;
        private final Map<Long, Entry> entries = new LinkedHashMap<>();

        private OwnerCleanup(String owner, long generation, ScriptResourceTracker.Cleanup action) {
            this.owner = owner;
            this.generation = generation;
            this.action = action;
        }
    }

    private void disposalFailure(Entry entry, Throwable failure) {
        failures++;
        if (failure instanceof VirtualMachineError) {
            throw (VirtualMachineError) failure;
        }
        if (failure instanceof ThreadDeath) {
            throw (ThreadDeath) failure;
        }
        LuaScriptErrors.add(entry.token.owner, "Native disposer failed for " + entry.token.type.name(),
                LuaScriptErrors.Outcome.UNKNOWN, failure);
    }

    private static Method disposerMethod(NativeMemberPlan plan, Object value) {
        if (!(plan.member instanceof Method) || plan.isStatic || plan.parameters.length != 0
                || plan.valueType != Void.TYPE || !plan.member.getDeclaringClass().isInstance(value)) {
            throw new NativeAccessFailure("invalid_native_disposer", "use an exact instance method ()V");
        }
        return (Method) plan.member;
    }

    private static final class Entry {
        private final NativeOwnedValue token;
        private Object value;
        private long queuedNanos;
        private final Method disposer;
        private final Object world;
        private final Object player;
        private Consumer<String> invalidation;
        private final boolean clientResource;
        private final OwnerCleanup ownerCleanup;

        private Entry(NativeOwnedValue token, Object value, Method disposer, Object world, Object player,
                boolean clientResource, OwnerCleanup ownerCleanup) {
            this.ownerCleanup = ownerCleanup;
            this.token = token;
            this.value = value;
            this.disposer = disposer;
            this.world = world;
            this.player = player;
            this.clientResource = clientResource;
        }
    }
}
