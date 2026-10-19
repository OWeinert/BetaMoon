package betamoon.gui.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Parsed once per source change; render frames never inspect Lua tables. */
public final class GuiTransitionPolicy {
    public static final GuiTransitionPolicy IMMEDIATE = new GuiTransitionPolicy(null, Collections.emptyMap());
    public static final Options ENGINE = new Options(0.2, 0, GuiEasing.bezier(0, 0, 0.58, 1));
    public static final GuiTransitionPolicy DEFAULT = new GuiTransitionPolicy(ENGINE, Collections.emptyMap());
    private final Options defaults;
    private final Map<String, Options> properties;

    private GuiTransitionPolicy(Options defaults, Map<String, Options> properties) {
        this.defaults = defaults;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public Options forProperty(String property) {
        return properties.containsKey(property) ? properties.get(property) : defaults;
    }

    public static GuiTransitionPolicy parse(LuaValue value, Set<String> animatable) {
        if (value.isnil() || value == LuaValue.FALSE) {
            return IMMEDIATE;
        }
        if (!value.istable() || value.checktable().keys().length == 0) {
            throw new LuaError("transition must be false or a nonempty property-keyed table.");
        }
        Options defaults = options(value.get("default"), ENGINE);
        Map<String, Options> properties = new LinkedHashMap<>();
        for (LuaValue key : value.checktable().keys()) {
            if (key.type() != LuaValue.TSTRING) {
                throw new LuaError("transition keys must be property names.");
            }
            String name = key.tojstring();
            if (!"default".equals(name)) {
                if (!animatable.contains(name)) {
                    throw new LuaError("Unsupported transition property: " + name);
                }
                properties.put(name, options(value.get(key), defaults == null ? ENGINE : defaults));
            }
        }
        return new GuiTransitionPolicy(defaults, properties);
    }

    private static Options options(LuaValue value, Options inherited) {
        if (value.isnil() || value == LuaValue.FALSE) {
            return null;
        }
        if (!value.istable() || value.checktable().keys().length == 0) {
            throw new LuaError("Transition options must be a nonempty options table or false.");
        }
        for (LuaValue key : value.checktable().keys()) {
            String name = key.type() == LuaValue.TSTRING ? key.tojstring() : "";
            if (!("duration".equals(name) || "delay".equals(name) || "easing".equals(name))) {
                throw new LuaError("Unsupported transition option: " + key.tojstring());
            }
        }
        double duration = time(value.get("duration"), inherited.duration);
        double delay = time(value.get("delay"), inherited.delay);
        GuiEasing easing = value.get("easing").isnil() ? inherited.easing : GuiEasing.parse(value.get("easing"));
        return new Options(duration, delay, easing);
    }

    private static double time(LuaValue value, double fallback) {
        double result = value.isnil() ? fallback : GuiEasing.number(value);
        if (result < 0 || result > Long.MAX_VALUE / 2e9) {
            throw new GuiAnimationValueError("Transition timing must be nonnegative and representable by the clock.");
        }
        return result;
    }

    public static final class Options {
        public final double duration;
        public final double delay;
        public final GuiEasing easing;

        public Options(double duration, double delay, GuiEasing easing) {
            this.duration = duration;
            this.delay = delay;
            this.easing = easing;
        }
    }
}
