package betamoon.interop.runtime;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.HashMap;
import java.util.Map;
import java.util.IdentityHashMap;
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
    private final Thread thread = Thread.currentThread();
    private final String owner;
    private final long ownerGeneration;
    private final LuaCallbackScope callback;
    private final boolean mutable;
    private final Map<String, Object> roots;
    private final Map<Integer, Object> values = new HashMap<>();
    private final Map<Object, Integer> identities = new IdentityHashMap<>();
    private final long identity = IDENTITIES.incrementAndGet();
    private BooleanSupplier environment;
    private boolean active = true;
    private int nextValue;

    private NativeContext(NativeBindings bindings, String owner, LuaCallbackScope callback, boolean mutable,
            Map<String, Object> roots, BooleanSupplier environment) {
        bindings.requireThread();
        this.bindings = bindings;
        this.parent = CURRENT.get();
        this.owner = owner;
        this.ownerGeneration = ScriptResourceTracker.ownerGeneration(owner);
        this.callback = callback;
        this.mutable = mutable && (parent == null || parent.mutable);
        this.roots = new HashMap<>(roots);
        this.environment = environment;
        CURRENT.set(this);
    }

    public static NativeContext open(NativeBindings bindings, String owner, LuaCallbackScope callback, boolean mutable,
            Map<String, Object> roots, BooleanSupplier environment) {
        return new NativeContext(bindings, owner, callback, mutable, roots, environment);
    }

    public static NativeContext current() {
        NativeContext value = CURRENT.get();
        if (value == null) {
            throw new NativeAccessFailure("native_context_unavailable", "use an integrated event callback");
        }
        value.check();
        return value;
    }

    public NativeContext child() {
        check();
        return new NativeContext(bindings, owner, callback, mutable, roots, environment);
    }

    public void check() {
        bindings.requireThread();
        if (Thread.currentThread() != thread) {
            throw new NativeAccessFailure("wrong_thread", "native scope belongs to the client thread");
        }
        if (!active) {
            throw new NativeAccessFailure("expired_scope", "native views expire when their callback returns");
        }
        if (ScriptResourceTracker.ownerGeneration(owner) != ownerGeneration) {
            throw new NativeAccessFailure("stale_owner", owner);
        }
        String executing = LuaScriptRegistry.getCurrentScriptFile();
        if (owner != null && !owner.equals(executing)) {
            throw new NativeAccessFailure("wrong_owner", "native view belongs to another script");
        }
        callback.requireActive();
        NativeContext current = CURRENT.get();
        while (current != null && current != this) {
            current = current.parent;
        }
        if (current == null) {
            throw new NativeAccessFailure("inactive_scope", "native scope is not on the active context stack");
        }
        if (environment != null && !environment.getAsBoolean()) {
            throw new NativeAccessFailure("stale_environment", "world, player or screen identity changed");
        }
    }

    public void require(Effect effect, boolean direct) {
        check();
        if (effect == Effect.READ) {
            return;
        }
        if (!mutable || CURRENT.get() == null || !CURRENT.get().mutable) {
            throw new NativeAccessFailure("native_phase_denied", "native effects require a mutable native callback");
        }
        callback.requireMutable();
        LuaCallbackScope.requireGameplayWrite();
        LuaCallbackScope.requireUiStateWrite();
        if ((effect == Effect.UNKNOWN || effect == Effect.INITIALIZE) && !direct) {
            throw new NativeAccessFailure("direct_required", "unknown effects or initialization require direct()");
        }
    }

    public void prepare() {
        require(Effect.UNKNOWN, true);
    }

    public Object root(String name) {
        check();
        return roots.get(name);
    }

    public int retainScoped(Object value) {
        check();
        Integer previous = identities.get(value);
        if (previous != null) {
            return previous;
        }
        if (values.size() >= 16384) {
            throw new NativeAccessFailure("native_view_limit", "too many objects in one scope");
        }
        int identity = ++nextValue;
        values.put(identity, value);
        identities.put(value, identity);
        return identity;
    }

    public String identity(int value) {
        check();
        return "scope-" + identity + ":object-" + value;
    }

    public Object value(int identity) {
        check();
        Object value = values.get(identity);
        if (value == null) {
            throw new NativeAccessFailure("expired_scope", "native payload is unavailable");
        }
        return value;
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        if (Thread.currentThread() != thread || CURRENT.get() != this) {
            throw new NativeAccessFailure("native_scope_order", "close native scopes in stack order");
        }
        active = false;
        values.clear();
        identities.clear();
        roots.clear();
        environment = null;
        CURRENT.set(parent);
    }
}
