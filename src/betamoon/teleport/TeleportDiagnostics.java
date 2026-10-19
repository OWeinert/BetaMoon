package betamoon.teleport;

import betamoon.worldgen.spatial.GenerationValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded detached diagnostics. No players, worlds, inventories, callbacks or
 * script data are retained.
 */
public final class TeleportDiagnostics {
    private static final Map<String, Entry> RECENT = new LinkedHashMap<String, Entry>();

    private TeleportDiagnostics() {
    }

    public static synchronized void begin(String id, String owner, String source, String destination,
            String operation) {
        if (RECENT.size() == 64 && !RECENT.containsKey(id)) {
            RECENT.remove(RECENT.keySet().iterator().next());
        }
        Entry entry = new Entry();
        entry.values.put("id", id);
        entry.values.put("owner", owner);
        entry.values.put("sourceKey", source);
        entry.values.put("destinationKey", destination);
        entry.values.put("operation", operation);
        RECENT.put(id, entry);
    }

    public static synchronized void stage(String id, String state, String stage, int elapsed, int chunks, int checks) {
        Entry entry = RECENT.get(id);
        if (entry == null) {
            return;
        }
        entry.values.put("state", state);
        entry.values.put("stage", stage);
        entry.values.put("elapsedTicks", elapsed);
        entry.values.put("preparedChunks", chunks);
        entry.values.put("candidateChecks", checks);
        if (entry.stages.isEmpty() || !entry.stages.get(entry.stages.size() - 1).equals(stage)) {
            if (entry.stages.size() == 24) {
                entry.stages.remove(0);
            }
            entry.stages.add(stage);
        }
    }

    public static synchronized void finish(String id, Map<String, Object> result) {
        Entry entry = RECENT.get(id);
        if (entry != null) {
            entry.values.put("result", GenerationValues.freeze(result));
            entry.values.put("state", result.get("state"));
        }
    }

    public static synchronized List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Entry entry : RECENT.values()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>(entry.values);
            row.put("stages", Collections.unmodifiableList(new ArrayList<String>(entry.stages)));
            result.add(Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableList(result);
    }

    private static final class Entry {
        private final Map<String, Object> values = new LinkedHashMap<String, Object>();
        private final List<String> stages = new ArrayList<String>();
    }
}
