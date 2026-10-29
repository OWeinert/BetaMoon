package betamoon.interop.cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-confined metadata LRU. Admission/lifetime accounting must never depend
 * on eviction.
 */
public final class NativeLookupCache<K, V> extends LinkedHashMap<K, V> {
    private final int maximum;

    public NativeLookupCache(int maximum) {
        super(Math.min(64, maximum), 0.75F, true);
        if (maximum < 1) {
            throw new IllegalArgumentException("positive cache capacity required");
        }
        this.maximum = maximum;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> entry) {
        return size() > maximum;
    }
}
