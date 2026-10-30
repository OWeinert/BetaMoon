package betamoon.gui.presentation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Immutable Java value contract. Lua conversion and legacy declarations live
 * outside this descriptor.
 */
public final class GuiPropertyDescriptor {
    public enum Kind {
        NUMBER, INTEGER, BOOLEAN, STRING, COLOR, STRING_LIST, DATA
    }

    public enum Stage {
        MEASURE, ARRANGE, PAINT, HIT, SEMANTICS
    }

    public final String name;
    public final Kind kind;
    public final Object defaultValue;
    public final double minimum;
    public final double maximum;
    public final boolean optional;
    public final boolean animated;
    public final Set<Stage> stages;
    public final Set<GuiCapability> requirements;

    public GuiPropertyDescriptor(String name, Kind kind, Object defaultValue, double minimum, double maximum,
            boolean optional, boolean animated, Set<Stage> stages, Set<GuiCapability> requirements) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]*") || kind == null || stages == null
                || requirements == null || !Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum
                || animated && !(kind == Kind.NUMBER || kind == Kind.INTEGER || kind == Kind.COLOR)) {
            throw new IllegalArgumentException("Invalid GUI property descriptor: " + name);
        }
        this.name = name;
        this.kind = kind;
        this.minimum = minimum;
        this.maximum = maximum;
        this.optional = optional;
        this.animated = animated;
        this.stages = Collections
                .unmodifiableSet(stages.isEmpty() ? EnumSet.noneOf(Stage.class) : EnumSet.copyOf(stages));
        this.requirements = Collections.unmodifiableSet(
                requirements.isEmpty() ? EnumSet.noneOf(GuiCapability.class) : EnumSet.copyOf(requirements));
        this.defaultValue = normalize(defaultValue);
    }

    public static GuiPropertyDescriptor number(String name, double value, double minimum, double maximum,
            Stage... stages) {
        EnumSet<Stage> affected = EnumSet.noneOf(Stage.class);
        Collections.addAll(affected, stages);
        return new GuiPropertyDescriptor(name, Kind.NUMBER, value, minimum, maximum, false, true, affected,
                Collections.emptySet());
    }

    public static GuiPropertyDescriptor value(String name, Kind kind, Object value, Stage... stages) {
        EnumSet<Stage> affected = EnumSet.noneOf(Stage.class);
        Collections.addAll(affected, stages);
        return new GuiPropertyDescriptor(name, kind, value, -Double.MAX_VALUE, Double.MAX_VALUE, false,
                kind == Kind.COLOR, affected, Collections.emptySet());
    }

    public Object normalize(Object value) {
        if (value == null && optional) {
            return null;
        }
        if (kind == Kind.DATA && value != null) {
            GuiPublishedData detached = new GuiPublishedData();
            detached.publish(Collections.singletonMap("value", value));
            return detached.snapshot().get("value");
        }
        if (kind == Kind.STRING && value instanceof String) {
            if (((String) value).length() > 65536) {
                throw invalid("text exceeds 65536 UTF-16 units");
            }
            return value;
        }
        if (kind == Kind.STRING_LIST && value instanceof List) {
            List<?> source = (List<?>) value;
            if (source.size() > 4096) {
                throw invalid("list exceeds 4096 entries");
            }
            List<String> result = new ArrayList<>();
            int units = 0;
            for (Object entry : source) {
                if (!(entry instanceof String) || ((String) entry).length() > 65536 - units) {
                    throw invalid("requires strings totaling at most 65536 UTF-16 units");
                }
                units += ((String) entry).length();
                result.add((String) entry);
            }
            return Collections.unmodifiableList(result);
        }
        if (kind == Kind.BOOLEAN && value instanceof Boolean) {
            return value;
        }
        if (kind == Kind.COLOR && value instanceof Integer) {
            return value;
        }
        if ((kind == Kind.NUMBER || kind == Kind.INTEGER) && value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number < minimum || number > maximum
                    || kind == Kind.INTEGER && number != Math.rint(number)) {
                throw invalid("requires " + kind.name().toLowerCase() + " within " + minimum + ".." + maximum);
            }
            return Double.valueOf(number == 0 ? 0 : number);
        }
        throw invalid("requires " + kind.name().toLowerCase() + (optional ? " or nil" : ""));
    }

    public Object interpolate(Object from, Object to, double progress) {
        if (!animated || !(from instanceof Number) || !(to instanceof Number)) {
            return progress >= 1 ? to : from;
        }
        if (kind == Kind.COLOR) {
            int first = ((Number) from).intValue();
            int last = ((Number) to).intValue();
            int color = 0;
            for (int shift = 0; shift <= 24; shift += 8) {
                double channel = (first >>> shift & 255) * (1 - progress) + (last >>> shift & 255) * progress;
                color |= (int) Math.round(Math.max(0, Math.min(255, channel))) << shift;
            }
            return color;
        }
        double first = ((Number) from).doubleValue();
        double last = ((Number) to).doubleValue();
        if (stages.contains(Stage.HIT) || stages.contains(Stage.MEASURE) || stages.contains(Stage.ARRANGE)) {
            // Geometry envelopes are validated at publication, including all simultaneous
            // tracks.
            // Non-monotone curves may reverse inside that envelope, but cannot escape it.
            progress = Math.max(0, Math.min(1, progress));
        }
        double value = first * (1 - progress) + last * progress;
        if (!Double.isFinite(value)) {
            value = first * (1 - Math.max(0, Math.min(1, progress))) + last * Math.max(0, Math.min(1, progress));
        }
        value = Math.max(minimum, Math.min(maximum, value));
        return kind == Kind.INTEGER ? Math.rint(value) : value;
    }

    private IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException(name + ": " + detail);
    }
}
