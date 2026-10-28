package betamoon.interop.runtime;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.query.NativeDataBudget;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Audited entry scope. Closing clears native payloads even if Lua retains every
 * view it received.
 */
public final class NativeContext implements AutoCloseable {
    public enum Effect {
        READ, WRITE, UNKNOWN, INITIALIZE
    }

    private static final ThreadLocal<NativeContext> CURRENT = new ThreadLocal<>();
    private static final AtomicLong IDENTITIES = new AtomicLong();
    public final NativeBindings bindings;
    private final NativeContext parent;
    public final NativeDataBudget dataBudget;
    private final Thread thread = Thread.currentThread();
    private final String owner;
    private final long ownerGeneration;
    private final ScriptResourceTracker.OwnerGeneration ownerState;
    private final LuaCallbackScope callback;
    private final boolean mutable;
    private final NativePermissions permissions;
    private final boolean readable;
    public final NativeEntry.Phase phase;
    private Map<String, Object> roots;
    private List<Object> values;
    private Map<Object, Integer> identities;
    private Map<Integer, NativeOwnedValue> ownedValues;
    private Map<NativeOwnedValue, NativeContext> ownedScopes;
    private final NativeOwnedValue lease;
    private final long identity = IDENTITIES.incrementAndGet();
    private BooleanSupplier environment;
    private boolean active = true;
    private int nextValue;

    private NativeContext(NativeBindings bindings, String owner, LuaCallbackScope callback, NativePermissions requested,
            Map<String, Object> roots, BooleanSupplier environment, NativeEntry.Phase phase) {
        if (bindings != null) {
            bindings.requireThread();
        }
        this.bindings = bindings;
        this.parent = CURRENT.get();
        dataBudget = parent == null ? new NativeDataBudget() : parent.dataBudget;
        int depth = 0;
        for (NativeContext entry = parent; entry != null; entry = entry.parent) {
            if (++depth >= 16) {
                throw new NativeAccessFailure("native_depth_limit", "native callback nesting exceeds 16");
            }
        }
        this.owner = owner;
        this.ownerState = ScriptResourceTracker.ownerToken(owner);
        this.ownerGeneration = ownerState.current();
        this.callback = callback;
        NativePermissions permitted = requested;
        if (requested.allows(NativePermissions.Capability.GAMEPLAY) && !callback.isMutable()) {
            permitted = permitted.intersect(NativePermissions.READ_ONLY);
        }
        permissions = parent == null ? permitted : permitted.intersect(parent.permissions);
        this.mutable = permissions.allows(NativePermissions.Capability.GAMEPLAY);
        this.readable = bindings != null && permissions.allows(NativePermissions.Capability.READ)
                && (parent == null || parent.readable);
        this.phase = phase;
        this.lease = null;
        this.roots = NativeRoots.copyOf(roots);
        this.environment = environment;
        CURRENT.set(this);
    }

    private NativeContext(NativeContext source, NativeOwnedValue lease) {
        source.check();
        bindings = source.bindings;
        parent = source;
        dataBudget = source.dataBudget;
        owner = source.owner;
        ownerGeneration = source.ownerGeneration;
        ownerState = source.ownerState;
        callback = source.callback;
        mutable = source.mutable;
        permissions = source.permissions;
        readable = source.readable;
        phase = source.phase;
        roots = source.roots;
        environment = source.environment;
        this.lease = lease;
    }

    public static NativeContext open(NativeBindings bindings, String owner, LuaCallbackScope callback, boolean mutable,
            Map<String, Object> roots, BooleanSupplier environment) {
        return open(bindings, owner, callback, mutable, roots, environment,
                mutable ? NativeEntry.Phase.GAMEPLAY : NativeEntry.Phase.QUERY);
    }

    public static NativeContext open(NativeBindings bindings, String owner, LuaCallbackScope callback, boolean mutable,
            Map<String, Object> roots, BooleanSupplier environment, NativeEntry.Phase phase) {
        return open(bindings, owner, callback, NativePermissions.legacy(mutable), roots, environment, phase);
    }

    public static NativeContext open(NativeBindings bindings, String owner, LuaCallbackScope callback,
            NativePermissions permissions, Map<String, Object> roots, BooleanSupplier environment,
            NativeEntry.Phase phase) {
        return new NativeContext(bindings, owner, callback, permissions, roots, environment, phase);
    }

    /**
     * Unknown/declaration callbacks must not inherit an enclosing native execution
     * scope.
     */
    public static NativeContext denied(String owner) {
        return new NativeContext(null, owner, null, NativePermissions.NONE, Collections.emptyMap(), null, null);
    }

    public static NativeContext current() {
        NativeContext value = CURRENT.get();
        if (value == null) {
            throw new NativeAccessFailure("native_context_unavailable", "use an integrated callback");
        }
        value.check();
        return value;
    }

    /**
     * Integration capture only; validation remains mandatory before accessing roots
     * or granting effects.
     */
    public static NativeContext captureCaller() {
        return CURRENT.get();
    }

    public static boolean hasFrame() {
        return CURRENT.get() != null;
    }

    /**
     * The hook native scope is read-only; test the enclosing edit authority before
     * opening it.
     */
    public static boolean hookEditsAllowed() {
        NativeContext current = CURRENT.get();
        return current == null
                ? cleanupAllowed()
                : current.active && current.readable && current.mutable && cleanupAllowed();
    }

    public static NativeEntry interfaceEntry(LuaCallbackScope callback) {
        NativeContext parent = current();
        return new NativeEntry(parent.bindings, callback, NativeEntry.Phase.INTERFACE,
                callback.isMutable() && parent.mutable, parent.roots, parent.environment);
    }

    public NativeContext child() {
        check();
        return new NativeContext(bindings, owner, callback, permissions, roots, environment, phase);
    }

    public String owner() {
        check();
        return owner;
    }

    /**
     * Same-package lifecycle bookkeeping only, after the caller has checked this
     * context.
     */
    String ownerIdentity() {
        return owner;
    }

    public boolean isRoot(Object value) {
        check();
        for (NativeContext entry = this; entry != null; entry = entry.parent) {
            for (Object root : entry.roots.values()) {
                if (root == value) {
                    return true;
                }
            }
        }
        return false;
    }

    public NativeContext ownedScope(NativeOwnedValue value) {
        check();
        int depth = 0;
        for (NativeContext entry = this; entry != null && entry.lease != null; entry = entry.parent) {
            if (entry.lease == value) {
                return this;
            }
            if (++depth >= 16) {
                throw new NativeAccessFailure("native_lease_limit", "operation depends on more than 16 owned values");
            }
        }
        if (ownedScopes == null) {
            ownedScopes = new IdentityHashMap<>();
        }
        NativeContext result = ownedScopes.get(value);
        if (result == null) {
            result = new NativeContext(this, value);
            ownedScopes.put(value, result);
        }
        return result;
    }

    public NativeContext inheritOwnership(NativeContext source) {
        NativeContext result = this;
        for (NativeContext entry = source; entry != null && entry.lease != null; entry = entry.parent) {
            result = result.ownedScope(entry.lease);
        }
        return result;
    }

    public NativeContext ownershipContext(int identity) {
        Object object = value(identity);
        NativeOwnedValue token = ownedValue(identity);
        if (token == null) {
            token = bindings.owned.find(object);
            if (token != null) {
                trackOwned(identity, token);
            }
        }
        return token == null ? this : ownedScope(token);
    }

    public void trackOwnership(Object value, NativeOwnedValue token) {
        check();
        for (NativeContext scope = this; scope != null; scope = scope.parent) {
            Integer identity = scope.identities == null ? null : scope.identities.get(value);
            if (identity != null) {
                scope.trackOwned(identity, token);
            }
        }
    }

    public static boolean cleanupAllowed() {
        NativeContext current = CURRENT.get();
        if (current != null && (!current.mutable || !current.readable)) {
            return false;
        }
        int depth = 0;
        for (NativeContext entry = current; entry != null; entry = entry.parent) {
            if (++depth >= 16) {
                return false;
            }
        }
        try {
            LuaCallbackScope.requireGameplayWrite();
            LuaCallbackScope.requireUiStateWrite();
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    /**
     * Trusted client disposers do not invoke Lua and may retire an already
     * invalidated owner's resources.
     */
    public static boolean clientCleanupAllowed() {
        NativeContext current = CURRENT.get();
        if (current != null
                && (!current.active || !current.permissions.allows(NativePermissions.Capability.CLIENT_RESOURCE))) {
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

    public Map<String, Object> describe() {
        check();
        boolean effects = mutable && CURRENT.get().mutable && cleanupAllowed();
        return CatalogValues.record("phase", phase.name().toLowerCase(Locale.ROOT), "side", "client", "lifetime",
                lease == null ? "callback" : "owned_borrow", "reads", true, "gameplayWrites", effects,
                "directInitialization", effects && permissions.allows(NativePermissions.Capability.INITIALIZATION),
                "directUnknownCalls", effects && permissions.allows(NativePermissions.Capability.UNKNOWN_EFFECT),
                "attachments", effects, "hookEdits", false, "bindingPreparation",
                permitsClientCapability(NativePermissions.Capability.BINDING_PREPARATION), "clientResources",
                permitsClientCapability(NativePermissions.Capability.CLIENT_RESOURCE), "clientActions",
                permitsClientCapability(NativePermissions.Capability.CLIENT_ACTION));
    }

    private boolean permitsClientCapability(NativePermissions.Capability capability) {
        try {
            requireCapability(capability);
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    public Map<String, Object> failureContext() {
        return CatalogValues.record("owner", owner, "ownerGeneration", ownerGeneration, "phase",
                phase == null ? "metadata" : phase.name().toLowerCase(Locale.ROOT), "scopeActive", active,
                "capabilities", permissions.describe());
    }

    public static void recordFailure(NativeAccessFailure failure) {
        for (NativeContext entry = CURRENT.get(); entry != null; entry = entry.parent) {
            if (entry.bindings != null) {
                failure.explain(entry.failureContext());
                entry.bindings.recordFailure(failure);
                return;
            }
        }
    }

    public void check() {
        if (bindings == null) {
            throw new NativeAccessFailure("native_context_unavailable", "this callback permits native metadata only");
        }
        bindings.requireThread();
        if (!active) {
            throw new NativeAccessFailure("expired_scope", "native views expire when their callback returns");
        }
        if (!ownerState.matches(ownerGeneration)) {
            throw new NativeAccessFailure("stale_owner", owner);
        }
        String executing = LuaScriptRegistry.getCurrentScriptFile();
        if (owner != null && !owner.equals(executing)) {
            throw new NativeAccessFailure("wrong_owner", "native view belongs to another script");
        }
        callback.requireActive();
        NativeContext current = CURRENT.get();
        if (current != this) {
            NativeContext anchor = this;
            while (anchor.lease != null) {
                anchor = anchor.parent;
            }
            while (current != null && current != anchor) {
                current = current.parent;
            }
            if (current == null) {
                throw new NativeAccessFailure("inactive_scope", "native scope is not on the active context stack");
            }
        }
        if (!readable || !CURRENT.get().readable) {
            throw new NativeAccessFailure("native_phase_denied", "an enclosing callback permits native metadata only");
        }
        if (environment != null && !environment.getAsBoolean()) {
            throw new NativeAccessFailure("stale_environment", "world, player or screen identity changed");
        }
        for (NativeContext ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
            if (!ancestor.active || !ancestor.ownerState.matches(ancestor.ownerGeneration)) {
                throw new NativeAccessFailure("stale_owner", "an enclosing callback was invalidated");
            }
            if (ancestor.environment != null && !ancestor.environment.getAsBoolean()) {
                throw new NativeAccessFailure("stale_environment", "an enclosing callback's environment changed");
            }
        }
        if (lease != null) {
            lease.use(parent);
        }
    }

    public void require(Effect effect, boolean direct) {
        check();
        if (effect == Effect.READ) {
            return;
        }
        NativePermissions.Capability capability = effect == Effect.WRITE
                ? NativePermissions.Capability.GAMEPLAY
                : effect == Effect.UNKNOWN
                        ? NativePermissions.Capability.UNKNOWN_EFFECT
                        : NativePermissions.Capability.INITIALIZATION;
        checkCapability(capability);
        callback.requireMutable();
        LuaCallbackScope.requireUiStateWrite();
        if ((effect == Effect.UNKNOWN || effect == Effect.INITIALIZE) && !direct) {
            throw new NativeAccessFailure("direct_required", "unknown effects or initialization require direct()");
        }
    }

    public void prepare() {
        requireCapability(NativePermissions.Capability.BINDING_PREPARATION);
    }

    public void requireClientResource() {
        requireCapability(NativePermissions.Capability.CLIENT_RESOURCE);
    }

    public void requireClientAction() {
        requireCapability(NativePermissions.Capability.CLIENT_ACTION);
    }

    private void requireCapability(NativePermissions.Capability capability) {
        check();
        checkCapability(capability);
        LuaCallbackScope.requireNoBindingMutation();
        LuaCallbackScope.requireUiStateWrite();
    }

    /**
     * Called only inside an already checked admission; no native/user code occurs
     * between these steps.
     */
    private void checkCapability(NativePermissions.Capability capability) {
        if (!permissions.allows(capability) || !CURRENT.get().permissions.allows(capability)) {
            throw new NativeAccessFailure("native_phase_denied",
                    "native entry does not permit " + capability.name().toLowerCase(Locale.ROOT));
        }
    }

    public Object root(String name) {
        check();
        return roots.get(name);
    }

    public int retainScoped(Object value) {
        check();
        if (identities == null) {
            identities = new IdentityHashMap<>();
            values = new ArrayList<>();
        }
        Integer previous = identities.get(value);
        if (previous != null) {
            return previous;
        }
        if (values.size() >= 16384) {
            throw new NativeAccessFailure("native_view_limit", "too many objects in one scope");
        }
        int identity = ++nextValue;
        values.add(value);
        identities.put(value, identity);
        NativeOwnedValue token = bindings.owned.find(value);
        for (NativeContext ancestor = parent; token == null && ancestor != null; ancestor = ancestor.parent) {
            Integer previousIdentity = ancestor.identities == null ? null : ancestor.identities.get(value);
            token = previousIdentity == null ? null : ancestor.ownedValue(previousIdentity);
        }
        if (token != null) {
            trackOwned(identity, token);
        }
        return identity;
    }

    private NativeOwnedValue ownedValue(int identity) {
        return ownedValues == null ? null : ownedValues.get(identity);
    }

    private void trackOwned(int identity, NativeOwnedValue token) {
        if (ownedValues == null) {
            ownedValues = new HashMap<>();
        }
        ownedValues.put(identity, token);
    }

    public String identity(int value) {
        check();
        return "scope-" + identity + ":object-" + value;
    }

    public Object value(int identity) {
        check();
        Object value = values == null || identity <= 0 || identity > values.size() ? null : values.get(identity - 1);
        if (value == null) {
            throw new NativeAccessFailure("expired_scope", "native payload is unavailable");
        }
        NativeOwnedValue token = ownedValue(identity);
        if (token != null) {
            token.use(this);
        }
        return value;
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        if (Thread.currentThread() != thread || lease == null && CURRENT.get() != this) {
            throw new NativeAccessFailure("native_scope_order", "close native scopes in stack order");
        }
        active = false;
        if (ownedScopes != null) {
            for (NativeContext scope : ownedScopes.values()) {
                scope.close();
            }
        }
        ownedScopes = null;
        values = null;
        identities = null;
        ownedValues = null;
        roots = Collections.emptyMap();
        environment = null;
        if (lease == null) {
            CURRENT.set(parent);
        }
    }
}
