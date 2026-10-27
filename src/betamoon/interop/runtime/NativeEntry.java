package betamoon.interop.runtime;

import betamoon.interop.binding.NativeBindings;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Explicit audited callback policy; it holds no owner or Lua closure. */
public final class NativeEntry {
    public enum Phase {
        CLIENT, GAMEPLAY, QUERY, BINDING, INTERFACE, HOOK, CLIENT_PREPARATION, CLIENT_ACTION
    }

    private final NativeBindings bindings;
    private final LuaCallbackScope callback;
    private final Phase phase;
    private final NativePermissions permissions;
    private final Map<String, Object> roots;
    private final BooleanSupplier environment;

    public NativeEntry(NativeBindings bindings, LuaCallbackScope callback, Phase phase, boolean mutable,
            Map<String, Object> roots, BooleanSupplier environment) {
        this(bindings, callback, phase, NativePermissions.legacy(mutable), roots, environment);
    }

    public NativeEntry(NativeBindings bindings, LuaCallbackScope callback, Phase phase, NativePermissions permissions,
            Map<String, Object> roots, BooleanSupplier environment) {
        this.bindings = bindings;
        this.callback = callback;
        this.phase = phase;
        this.permissions = permissions;
        this.roots = new HashMap<>(roots);
        this.environment = environment;
    }

    public static NativeEntry clientPreparation(NativeBindings bindings, LuaCallbackScope callback,
            Map<String, Object> roots, BooleanSupplier environment) {
        return new NativeEntry(bindings, callback, Phase.CLIENT_PREPARATION, NativePermissions.CLIENT_PREPARATION,
                roots, environment);
    }

    public static NativeEntry clientAction(NativeBindings bindings, LuaCallbackScope callback,
            Map<String, Object> roots, BooleanSupplier environment) {
        return new NativeEntry(bindings, callback, Phase.CLIENT_ACTION, NativePermissions.CLIENT_ACTION, roots,
                environment);
    }

    public NativeContext open(String owner) {
        callback.requireActive();
        return NativeContext.open(bindings, owner, callback, permissions, roots, environment, phase);
    }

    public NativeEntry asInterface() {
        return new NativeEntry(bindings, callback, Phase.INTERFACE, permissions, roots, environment);
    }

    public NativeEntry asHook() {
        return new NativeEntry(bindings, callback, Phase.HOOK, NativePermissions.READ_ONLY, roots, environment);
    }
}
