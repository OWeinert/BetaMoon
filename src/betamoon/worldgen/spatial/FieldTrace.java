package betamoon.worldgen.spatial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-query bounded graph observations, carried explicitly rather than in
 * global instrumentation state.
 */
public final class FieldTrace {
    private static final int MAX_SAMPLES = 512;
    private final List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
    private boolean truncated;

    void record(String node, double x, double y, double z, double value) {
        if (samples.size() >= MAX_SAMPLES) {
            truncated = true;
            return;
        }
        Map<String, Object> sample = new LinkedHashMap<String, Object>();
        sample.put("node", node);
        sample.put("x", x);
        sample.put("y", y);
        sample.put("z", z);
        sample.put("value", value);
        samples.add(Collections.unmodifiableMap(sample));
    }

    public List<Map<String, Object>> samples() {
        return Collections.unmodifiableList(samples);
    }

    public boolean truncated() {
        return truncated;
    }
}
