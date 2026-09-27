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

    /** Combines a captured base value with property layers in ascending precedence. */
    public interface ValueResolver<V> {
        V resolve(V baseValue, List<V> layerValues);
    }

    /** A stable property identity paired with its type-safe native adapter. */
    public static final class Property<T, V> {
        private final String name;
        private final PropertyAdapter<T, V> adapter;
        private final ValueResolver<V> resolver;

        public Property(String name, PropertyAdapter<T, V> adapter) {
            this(name, adapter, null);
        }

        public Property(String name, PropertyAdapter<T, V> adapter, ValueResolver<V> resolver) {
            if (name == null || adapter == null) {
                throw new IllegalArgumentException("Property name and adapter are required");
            }
            this.name = name;
            this.adapter = adapter;
            this.resolver = resolver == null ? winningValue() : resolver;
        }

        public String getName() {
            return name;
        }

        public V read(T target) {
            return adapter.read(target);
        }

        private static <V> ValueResolver<V> winningValue() {
            return new ValueResolver<V>() {
                public V resolve(V baseValue, List<V> layerValues) {
                    return layerValues.isEmpty() ? baseValue : layerValues.get(layerValues.size() - 1);
                }
            };
        }
    }

    /** Parsed, side-effect-free request used by an atomic override batch. */
    public static final class Request<T, V> {
        private final String targetKey;
        private final T target;
        private final Property<T, V> property;
        private final V value;
        private final int priority;

        private Request(String targetKey, T target, Property<T, V> property, V value, int priority) {
            this.targetKey = targetKey;
            this.target = target;
            this.property = property;
            this.value = value;
            this.priority = priority;
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
        private final ValueResolver<V> resolver;
        private final V baseValue;
        private final List<Layer<T, V>> layers = new ArrayList<>();

        private Slot(String key, T target, PropertyAdapter<T, V> adapter, ValueResolver<V> resolver) {
            this.key = key;
            this.target = target;
            this.adapter = adapter;
            this.resolver = resolver;
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
            List<V> values = new ArrayList<V>(layers.size());
            for (Layer<T, V> layer : layers) {
                values.add(layer.value);
            }
            adapter.write(target, resolver.resolve(baseValue, Collections.unmodifiableList(values)));
        }
    }

    private static final Map<String, Slot<?, ?>> SLOTS = new HashMap<>();
    private static long nextSequence;

    private OverrideManager() {
    }

    /** Adds an override layer owned by the currently loading Lua script. */
    public static synchronized <T, V> Layer<T, V> apply(String targetKey, T target, Property<T, V> property, V value,
            int priority) {
        List<Request<?, ?>> requests = new ArrayList<Request<?, ?>>(1);
        requests.add(request(targetKey, target, property, value, priority));
        return castLayer(applyAll(requests).get(0));
    }

    /** Creates a request without mutating native or registry state. */
    public static <T, V> Request<T, V> request(String targetKey, T target, Property<T, V> property, V value,
            int priority) {
        if (targetKey == null || target == null || property == null) {
            throw new IllegalArgumentException("Override target and property are required");
        }
        return new Request<T, V>(targetKey, target, property, value, priority);
    }

    /** Applies a parsed request batch atomically and tracks it as one owned resource. */
    public static synchronized List<Layer<?, ?>> applyAll(List<Request<?, ?>> requests) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Overrides can only be declared while a Lua script is loading.");
        }
        if (requests == null) {
            throw new IllegalArgumentException("Override requests are required");
        }
        final List<Layer<?, ?>> layers = new ArrayList<Layer<?, ?>>(requests.size());
        try {
            for (Request<?, ?> request : requests) {
                layers.add(applyRequest(request, owner));
            }
        } catch (RuntimeException error) {
            removeAll(layers);
            throw error;
        }
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                removeAll(layers);
            }
        });
        return Collections.unmodifiableList(new ArrayList<Layer<?, ?>>(layers));
    }

    private static <T, V> Layer<T, V> applyRequest(Request<T, V> request, String owner) {
        String slotKey = request.targetKey + "\n" + request.property.name;
        Slot<T, V> slot = getSlot(slotKey, request.target);
        if (slot == null) {
            slot = new Slot<T, V>(slotKey, request.target, request.property.adapter, request.property.resolver);
            SLOTS.put(slotKey, slot);
        }
        Layer<T, V> layer = new Layer<T, V>(slot, owner, request.value, request.priority, nextSequence++);
        try {
            slot.add(layer);
        } catch (RuntimeException error) {
            slot.layers.remove(layer);
            try {
                slot.apply();
            } catch (RuntimeException restoreError) {
                error.addSuppressed(restoreError);
            }
            if (slot.layers.isEmpty()) {
                SLOTS.remove(slotKey);
            }
            throw error;
        }
        return layer;
    }

    private static void removeAll(List<Layer<?, ?>> layers) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            layers.get(i).remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, V> Layer<T, V> castLayer(Layer<?, ?> layer) {
        return (Layer<T, V>) layer;
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
