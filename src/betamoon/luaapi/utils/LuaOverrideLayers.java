package betamoon.luaapi.utils;

import betamoon.luaapi.resource.OverrideManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable ordered callback definitions used as one effective property value. */
public final class LuaOverrideLayers<D extends LuaOverrideActionDefinition> {
    private final List<D> definitions;

    private LuaOverrideLayers(List<D> definitions) {
        this.definitions = Collections.unmodifiableList(new ArrayList<D>(definitions));
    }

    public static <D extends LuaOverrideActionDefinition> LuaOverrideLayers<D> single(D definition) {
        return new LuaOverrideLayers<D>(Collections.singletonList(definition));
    }

    public static <D extends LuaOverrideActionDefinition> OverrideManager.ValueResolver<LuaOverrideLayers<D>> resolver() {
        return new OverrideManager.ValueResolver<LuaOverrideLayers<D>>() {
            public LuaOverrideLayers<D> resolve(LuaOverrideLayers<D> base,
                    List<LuaOverrideLayers<D>> layerValues) {
                List<D> combined = new ArrayList<D>();
                if (base != null) {
                    combined.addAll(base.definitions);
                }
                for (LuaOverrideLayers<D> layer : layerValues) {
                    if (layer != null) {
                        combined.addAll(layer.definitions);
                    }
                }
                return combined.isEmpty() ? null : new LuaOverrideLayers<D>(combined);
            }
        };
    }

    public List<D> definitions() {
        return definitions;
    }

    /** Concatenates already ordered layer groups without exposing a mutable list. */
    public static <D extends LuaOverrideActionDefinition> LuaOverrideLayers<D> concat(
            LuaOverrideLayers<D> first, LuaOverrideLayers<D> second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        List<D> combined = new ArrayList<D>(first.definitions);
        combined.addAll(second.definitions);
        return new LuaOverrideLayers<D>(combined);
    }
}
