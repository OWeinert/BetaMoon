package betamoon.capability;

import betamoon.luaapi.resource.OverrideManager;
import betamoon.luaapi.utils.LuaOverrideCallback;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import betamoon.luaapi.utils.LuaOverrideLayers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Global layered behavior for already declared capability operations. */
public final class CapabilityOperationOverrides {
    private static final Map<String, LuaOverrideCallback> CALLBACKS = new ConcurrentHashMap<>();

    private CapabilityOperationOverrides() {
    }

    public static OverrideManager.PropertyAdapter<CapabilityDefinition, LuaOverrideLayers<LuaOverrideDefinition>>
            adapter(final String operation) {
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

    public static LuaValue invoke(CapabilityInstance instance, String operation, LuaTable context,
            LuaValue request, LuaValue original) {
        LuaOverrideCallback callback = CALLBACKS.get(key(instance.attachment.capability, operation));
        if (callback == null || !callback.isEnabled()) {
            return original.call(context, request);
        }
        return callback.invoke(context, LuaValue.varargsOf(new LuaValue[]{context, request}),
                () -> original.call(context, request), value -> value);
    }

    private static String key(CapabilityDefinition definition, String operation) {
        return definition.key + ":" + operation;
    }
}
