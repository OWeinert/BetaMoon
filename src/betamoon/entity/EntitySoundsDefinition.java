package betamoon.entity;

import betamoon.assets.AssetKey;
import betamoon.luaapi.audio.SoundEventReference;
import betamoon.luaapi.audio.SoundEvents;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Semantic one-shot sound bindings with optional playback and cadence overrides. */
public final class EntitySoundsDefinition {
    public enum Event {
        AMBIENT("ambient"),
        HURT("hurt"),
        DEATH("death"),
        STEP("step"),
        IMPACT("impact"),
        PICKUP("pickup");

        public final String field;

        Event(String field) {
            this.field = field;
        }
    }

    public final Map<Event, Binding> bindings;

    public EntitySoundsDefinition(LuaValue value) {
        fields(value, "entity.sounds", "ambient", "hurt", "death", "step", "impact", "pickup");
        Map<Event, Binding> parsed = new EnumMap<>(Event.class);
        for (Event event : Event.values()) {
            Binding binding = Binding.read(value.get(event.field), event);
            if (binding != null) {
                parsed.put(event, binding);
            }
        }
        bindings = Collections.unmodifiableMap(parsed);
    }

    public Binding get(Event event) {
        return bindings.get(event);
    }

    public boolean ticks() {
        return bindings.containsKey(Event.AMBIENT) || bindings.containsKey(Event.STEP);
    }

    public static final class Binding {
        public final AssetKey event;
        public final float volume;
        public final float pitch;
        public final float range;
        public final int intervalMin;
        public final int intervalMax;
        public final double stepDistance;

        private Binding(AssetKey event, float volume, float pitch, float range,
                int intervalMin, int intervalMax, double stepDistance) {
            this.event = event;
            this.volume = volume;
            this.pitch = pitch;
            this.range = range;
            this.intervalMin = intervalMin;
            this.intervalMax = intervalMax;
            this.stepDistance = stepDistance;
        }

        private static Binding read(LuaValue value, Event semantic) {
            if (value.isnil() || value == LuaValue.FALSE) {
                return null;
            }
            if (!value.istable() || value instanceof SoundEventReference) {
                return defaults(SoundEvents.requireKey(value, "entity.sounds." + semantic.field), semantic);
            }
            String path = "entity.sounds." + semantic.field;
            fields(value, path, "event", "volume", "pitch", "range", "intervalTicks", "distance");
            AssetKey event = SoundEvents.requireKey(required(value, "event"), path + ".event");
            float volume = optional(value.get("volume"), 0, 1, path + ".volume");
            float pitch = optional(value.get("pitch"), 0.01, 4, path + ".pitch");
            float range = optional(value.get("range"), 0.01, 1024, path + ".range");
            int min = 80;
            int max = 160;
            LuaValue interval = value.get("intervalTicks");
            if (!interval.isnil()) {
                if (semantic != Event.AMBIENT) {
                    throw error(path + ".intervalTicks", "is only valid for ambient sounds");
                }
                if (interval.istable()) {
                    fields(interval, path + ".intervalTicks", "min", "max");
                    min = integer(required(interval, "min"), path + ".intervalTicks.min", 1, 72000);
                    max = integer(required(interval, "max"), path + ".intervalTicks.max", 1, 72000);
                    if (min > max) {
                        throw error(path + ".intervalTicks", "min must not exceed max");
                    }
                } else {
                    min = max = integer(interval, path + ".intervalTicks", 1, 72000);
                }
            }
            double distance = 1;
            if (!value.get("distance").isnil()) {
                if (semantic != Event.STEP) {
                    throw error(path + ".distance", "is only valid for step sounds");
                }
                distance = number(value.get("distance"), path + ".distance");
                if (distance < 0.1 || distance > 16) {
                    throw error(path + ".distance", "expected 0.1..16 blocks");
                }
            }
            return new Binding(event, volume, pitch, range, min, max, distance);
        }

        private static Binding defaults(AssetKey event, Event semantic) {
            return new Binding(event, Float.NaN, Float.NaN, Float.NaN, 80, 160, 1);
        }

        private static float optional(LuaValue value, double min, double max, String path) {
            if (value.isnil()) {
                return Float.NaN;
            }
            double parsed = number(value, path);
            if (parsed < min || parsed > max) {
                throw error(path, "expected " + min + ".." + max);
            }
            return (float) parsed;
        }
    }
}
