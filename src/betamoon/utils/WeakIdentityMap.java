package betamoon.utils;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weak keys compared by object identity, independent of mutable native entity
 * IDs/equals.
 */
public final class WeakIdentityMap<K, V> {
    private final ReferenceQueue<K> collected = new ReferenceQueue<K>();
    private final Map<Key<K>, V> entries = new HashMap<Key<K>, V>();

    public synchronized V get(K key) {
        drain();
        return entries.get(new Key<K>(key, null));
    }

    public synchronized void put(K key, V value) {
        drain();
        entries.put(new Key<K>(key, collected), value);
    }

    public synchronized List<V> values() {
        drain();
        return new ArrayList<V>(entries.values());
    }

    private void drain() {
        Object key;
        while ((key = collected.poll()) != null) {
            entries.remove(key);
        }
    }

    private static final class Key<K> extends WeakReference<K> {
        private final int hash;

        private Key(K value, ReferenceQueue<K> queue) {
            super(value, queue);
            if (value == null) {
                throw new NullPointerException("Identity map key");
            }
            hash = System.identityHashCode(value);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Key && get() != null && get() == ((Key<?>) other).get();
        }
    }
}
