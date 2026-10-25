package betamoon.interop;

import betamoon.interop.catalog.CatalogJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Build report comparing semantic record fingerprints, independently of archive timestamps or ZIP order. */
public final class CatalogDiffReport {
    private CatalogDiffReport() {
    }

    public static Map<String, String> readFingerprints(Path file) throws IOException {
        Map<String, String> fingerprints = new TreeMap<>();
        if (!Files.exists(file)) {
            return fingerprints;
        }
        if (Files.size(file) > 16 * 1024 * 1024) {
            throw new IOException("Fingerprint report size limit exceeded");
        }
        Object decoded;
        try {
            decoded = CatalogJson.decode(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid previous fingerprint report", exception);
        }
        if (!(decoded instanceof Map)) {
            throw new IOException("Expected a fingerprint object");
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) decoded).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)
                    || !((String) entry.getValue()).matches("[0-9a-f]{64}")) {
                throw new IOException("Invalid fingerprint entry");
            }
            fingerprints.put((String) entry.getKey(), (String) entry.getValue());
        }
        return fingerprints;
    }

    public static Map<String, Object> compare(Map<String, String> previous, Map<String, String> current) {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        int unchanged = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(current).entrySet()) {
            String old = previous.get(entry.getKey());
            if (old == null) {
                added.add(entry.getKey());
            } else if (!old.equals(entry.getValue())) {
                changed.add(entry.getKey());
            } else {
                unchanged++;
            }
        }
        for (String key : new TreeMap<>(previous).keySet()) {
            if (!current.containsKey(key)) {
                removed.add(key);
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("added", added);
        report.put("removed", removed);
        report.put("changed", changed);
        report.put("unchanged", unchanged);
        return report;
    }
}
