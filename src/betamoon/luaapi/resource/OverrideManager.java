package betamoon.luaapi.resource;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;

/**
 * Applies layered, script-owned property overrides to existing Minecraft
 * resources.
 *
 * <p>
 * The first layer captures the value that existed before BetaMoon touched the
 * property. Removing any layer recomputes the effective value from the
 * remaining layers, so unloading one script does not erase another script's
 * override.
 * </p>
 */
public final class OverrideManager {
    /** Reads and writes one supported property on a resource. */
    public interface PropertyAdapter<T, V> {
        V read(T target);

        void write(T target, V value);
    }

    /** A stable property identity paired with its type-safe native adapter. */
    public static final class Property<T, V> {
        private final String name;
        private final PropertyAdapter<T, V> adapter;

        public Property(String name, PropertyAdapter<T, V> adapter) {
            if (name == null || adapter == null) {
                throw new IllegalArgumentException("Property name and adapter are required");
            }
            this.name = name;
            this.adapter = adapter;
        }

        public String getName() {
            return name;
        }

        public V read(T target) {
            return adapter.read(target);
        }
    }

    /** A removable override returned to Lua through an override handle. */
    public static final class Layer<T, V> {
        private final Slot<T, V> slot;
        private final String owner;
        private final V value;
        private final int priority;
        private final long sequence;
        private boolean active = true;

        private Layer(Slot<T, V> slot, String owner, V value, int priority, long sequence) {
            this.slot = slot;
            this.owner = owner;
            this.value = value;
            this.priority = priority;
            this.sequence = sequence;
        }

        public synchronized void remove() {
            if (!active) {
                return;
            }
            active = false;
            OverrideManager.remove(this);
        }

        public synchronized boolean isActive() {
            return active;
        }

        public String getOwner() {
            return owner;
        }

        public V getValue() {
            return value;
        }
    }

    private static final class Slot<T, V> {
        private final String key;
        private final T target;
        private final PropertyAdapter<T, V> adapter;
        private final V baseValue;
        private final List<Layer<T, V>> layers = new ArrayList<>();

        private Slot(String key, T target, PropertyAdapter<T, V> adapter) {
            this.key = key;
            this.target = target;
            this.adapter = adapter;
            this.baseValue = adapter.read(target);
        }

        private void add(Layer<T, V> layer) {
            layers.add(layer);
            Collections.sort(layers, new Comparator<Layer<T, V>>() {
                public int compare(Layer<T, V> left, Layer<T, V> right) {
                    if (left.priority != right.priority) {
                        return left.priority < right.priority ? -1 : 1;
                    }
                    return left.sequence < right.sequence ? -1 : left.sequence == right.sequence ? 0 : 1;
                }
            });
            apply();
        }

        private void remove(Layer<T, V> layer) {
            layers.remove(layer);
            apply();
        }

        private void apply() {
            V value = layers.isEmpty() ? baseValue : layers.get(layers.size() - 1).value;
            adapter.write(target, value);
        }
    }

    private static final Map<String, Slot<?, ?>> SLOTS = new HashMap<>();
    private static long nextSequence;

    private OverrideManager() {
    }

    /** Adds an override layer owned by the currently loading Lua script. */
    public static synchronized <T, V> Layer<T, V> apply(String targetKey, T target, Property<T, V> property, V value,
            int priority) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Overrides can only be declared while a Lua script is loading.");
        }
        String slotKey = targetKey + "\n" + property.name;
        Slot<T, V> slot = getSlot(slotKey, target);
        if (slot == null) {
            slot = new Slot<>(slotKey, target, property.adapter);
            SLOTS.put(slotKey, slot);
        }
        final Layer<T, V> layer = new Layer<>(slot, owner, value, priority, nextSequence++);
        slot.add(layer);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                layer.remove();
            }
        });
        return layer;
    }

    private static synchronized <T, V> void remove(Layer<T, V> layer) {
        Slot<T, V> slot = layer.slot;
        slot.remove(layer);
        if (slot.layers.isEmpty()) {
            SLOTS.remove(slot.key);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, V> Slot<T, V> getSlot(String key, T target) {
        Slot<?, ?> slot = SLOTS.get(key);
        if (slot != null && slot.target != target) {
            throw new IllegalStateException("Override property key refers to multiple resource instances: " + key);
        }
        return (Slot<T, V>) slot;
    }
}
