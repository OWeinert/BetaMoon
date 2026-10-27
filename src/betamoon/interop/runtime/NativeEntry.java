package betamoon.interop.runtime;

import betamoon.interop.binding.NativeBindings;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Explicit audited callback policy; it holds no owner or Lua closure. */
public final class NativeEntry {
    public enum Phase {
        CLIENT, GAMEPLAY, QUERY, BINDING, INTERFACE, HOOK
    }

    private final NativeBindings bindings;
    private final LuaCallbackScope callback;
    private final Phase phase;
    private final boolean mutable;
    private final Map<String, Object> roots;
    private final BooleanSupplier environment;

    public NativeEntry(NativeBindings bindings, LuaCallbackScope callback, Phase phase, boolean mutable,
            Map<String, Object> roots, BooleanSupplier environment) {
        this.bindings = bindings;
        this.callback = callback;
        this.phase = phase;
        this.mutable = mutable;
        this.roots = new HashMap<>(roots);
        this.environment = environment;
    }

    public NativeContext open(String owner) {
        callback.requireActive();
        return NativeContext.open(bindings, owner, callback, mutable, roots, environment, phase);
    }

    public NativeEntry asInterface() {
        return new NativeEntry(bindings, callback, Phase.INTERFACE, mutable, roots, environment);
    }

    public NativeEntry asHook() {
        return new NativeEntry(bindings, callback, Phase.HOOK, false, roots, environment);
    }
}
