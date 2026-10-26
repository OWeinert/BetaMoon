package betamoon.capability;

import betamoon.luaapi.resource.OverrideManager;
import betamoon.luaapi.utils.LuaOverrideCallback;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import betamoon.luaapi.utils.LuaOverrideLayers;
import betamoon.luamodloader.ScriptExecution;
import betamoon.tileentity.TileEntityDefinition;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Global layered behavior for already declared capability operations. */
public final class CapabilityOperationOverrides {
    private static final Map<String, LuaOverrideCallback> CALLBACKS = new ConcurrentHashMap<>();

    private CapabilityOperationOverrides() {
    }

    public static OverrideManager.PropertyAdapter<CapabilityDefinition, LuaOverrideLayers<LuaOverrideDefinition>> adapter(
            final String operation) {
        return new OverrideManager.PropertyAdapter<CapabilityDefinition, LuaOverrideLayers<LuaOverrideDefinition>>() {
            @SuppressWarnings("unchecked")
            public LuaOverrideLayers<LuaOverrideDefinition> read(CapabilityDefinition target) {
                LuaOverrideCallback callback = CALLBACKS.get(key(target, operation));
                return callback == null ? null : (LuaOverrideLayers<LuaOverrideDefinition>) callback.layers();
            }

            public void write(CapabilityDefinition target, LuaOverrideLayers<LuaOverrideDefinition> layers) {
                String key = key(target, operation);
                if (layers == null) {
                    CALLBACKS.remove(key);
                } else {
                    CALLBACKS.put(key, new LuaOverrideCallback(layers));
                }
            }
        };
    }

    public static LuaValue invoke(CapabilityInstance instance, String operation, LuaTable context, LuaValue request,
            LuaValue original) {
        TileEntityDefinition tile = instance.tile.getDefinition();
        String owner = tile == null ? instance.attachment.capability.owner : tile.owner;
        Supplier<LuaValue> base = () -> ScriptExecution
                .invoke(owner, "capability." + operation, original, LuaValue.varargsOf(context, request)).arg1();
        LuaOverrideCallback callback = CALLBACKS.get(key(instance.attachment.capability, operation));
        if (callback == null || !callback.isEnabled()) {
            return base.get();
        }
        return ScriptExecution.withOwner(owner, () -> callback.invoke(context,
                LuaValue.varargsOf(new LuaValue[]{context, request}), base, value -> value));
    }

    private static String key(CapabilityDefinition definition, String operation) {
        return definition.key + ":" + operation;
    }
}
