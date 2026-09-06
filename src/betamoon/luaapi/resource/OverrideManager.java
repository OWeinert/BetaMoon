package betamoon.luaapi.resource;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;

/**
 * Applies layered, script-owned property overrides to existing Minecraft resources.
 *
 * <p>The first layer captures the value that existed before BetaMoon touched the
 * property. Removing any layer recomputes the effective value from the remaining
 * layers, so unloading one script does not erase another script's override.</p>
 */
public final class OverrideManager {
    /** Reads and writes one supported property on a resource. */
    public interface PropertyAdapter {
        Object read(Object target);
        void write(Object target, Object value);
    }

    /** A removable override returned to Lua through an override handle. */
    public static final class Layer {
        private final Slot slot;
        private final String owner;
        private final Object value;
        private final int priority;
        private final long sequence;
        private boolean active = true;

        private Layer(Slot slot, String owner, Object value, int priority, long sequence) {
            this.slot = slot;
            this.owner = owner;
            this.value = value;
            this.priority = priority;
            this.sequence = sequence;
        }

        public synchronized void remove() {
            if (!active) return;
            active = false;
            slot.remove(this);
        }

        public boolean isActive() { return active; }
        public String getOwner() { return owner; }
        public Object getValue() { return value; }
    }

    private static final class Slot {
        private final String key;
        private final Object target;
        private final PropertyAdapter adapter;
        private final Object baseValue;
        private final List layers = new ArrayList();

        private Slot(String key, Object target, PropertyAdapter adapter) {
            this.key = key;
            this.target = target;
            this.adapter = adapter;
            this.baseValue = adapter.read(target);
        }

        private synchronized void add(Layer layer) {
            layers.add(layer);
            java.util.Collections.sort(layers, new java.util.Comparator() {
                public int compare(Object leftValue, Object rightValue) {
                    Layer left = (Layer) leftValue;
                    Layer right = (Layer) rightValue;
                    if (left.priority != right.priority) return left.priority < right.priority ? -1 : 1;
                    return left.sequence < right.sequence ? -1 : left.sequence == right.sequence ? 0 : 1;
                }
            });
            apply();
        }

        private synchronized void remove(Layer layer) {
            layers.remove(layer);
            apply();
            if (layers.isEmpty()) {
                synchronized (OverrideManager.class) { SLOTS.remove(key); }
            }
        }

        private void apply() {
            Object value = layers.isEmpty() ? baseValue : ((Layer) layers.get(layers.size() - 1)).value;
            adapter.write(target, value);
        }
    }

    private static final Map SLOTS = new HashMap();
    private static long nextSequence;

    private OverrideManager() {
    }

    /** Adds an override layer owned by the currently loading Lua script. */
    public static synchronized Layer apply(String targetKey, Object target, String property,
        Object value, int priority, PropertyAdapter adapter) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Overrides can only be declared while a Lua script is loading.");
        }
        String slotKey = targetKey + "\n" + property;
        Slot slot = (Slot) SLOTS.get(slotKey);
        if (slot == null) {
            slot = new Slot(slotKey, target, adapter);
            SLOTS.put(slotKey, slot);
        }
        final Layer layer = new Layer(slot, owner, value, priority, nextSequence++);
        slot.add(layer);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() { layer.remove(); }
        });
        return layer;
    }
}
