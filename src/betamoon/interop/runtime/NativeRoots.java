package betamoon.interop.runtime;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns an immutable root capture once; nested entries can safely share it until
 * their individual close.
 */
final class NativeRoots extends AbstractMap<String, Object> {
    private final Map<String, Object> values;

    private NativeRoots(Map<String, Object> roots) {
        values = Collections.unmodifiableMap(new HashMap<>(roots));
    }

    static Map<String, Object> copyOf(Map<String, Object> roots) {
        if (roots.isEmpty()) {
            return Collections.emptyMap();
        }
        return roots instanceof NativeRoots ? roots : new NativeRoots(roots);
    }

    @Override
    public Object get(Object key) {
        return values.get(key);
    }

    @Override
    public Collection<Object> values() {
        return values.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return values.entrySet();
    }
}
