package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Pure metadata queries. Pagination retains no snapshot, native object, loader or Lua callback. */
public final class NativeQueryService {
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList("kind", "name", "prefix", "glob", "package",
            "originId", "loaderId", "declaringType", "memberKind", "access", "names", "side", "loaded", "bound",
            "sourceAvailable", "includeSynthetic", "descriptor", "limit", "cursor"));

    private NativeQueryService() {
    }

    public static Map<String, Object> query(NativeIndexSnapshot snapshot, Map<String, Object> filter) {
        validate(filter);
        String kind = (String) filter.getOrDefault("kind", "classes");
        int limit = filter.containsKey("limit") ? CatalogValues.integer(filter, "limit") : 50;
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("native query limit must be between 1 and 200");
        }
        Map<String, Object> identity = new TreeMap<>(filter);
        identity.remove("cursor");
        String digest = CatalogFingerprint.sha256(CatalogJson.encode(identity).getBytes(StandardCharsets.UTF_8));
        List<Map<String, Object>> rows = snapshot.rows(kind);
        int start = cursor(snapshot.generation, digest, filter.get("cursor"));
        if (start < 0 || start > rows.size()) {
            throw new IllegalArgumentException("invalid_cursor");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int position = start;
        while (position < rows.size() && position - start < 100000) {
            Map<String, Object> row = rows.get(position);
            if (matches(row, filter)) {
                if (items.size() == limit) {
                    break;
                }
                items.add(row);
            }
            position++;
        }
        String next = position < rows.size() ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                (snapshot.generation + ":" + digest + ":" + position).getBytes(StandardCharsets.UTF_8)) : null;
        return CatalogValues.object(CatalogValues.freeze(CatalogValues.record("items", items, "nextCursor", next,
                "generation", snapshot.generation, "coverage", snapshot.coverage, "warnings", snapshot.warnings)));
    }

    public static Map<String, Object> describe(NativeIndexSnapshot snapshot, String name, String originId) {
        Map<String, Object> filter = CatalogValues.record("kind", "classes", "name", name, "limit", 2);
        if (originId != null) {
            filter.put("originId", originId);
        }
        List<?> items = (List<?>) query(snapshot, filter).get("items");
        if (items.size() > 1) {
            throw new IllegalArgumentException("ambiguous_origin");
        }
        return items.isEmpty() ? null : CatalogValues.object(items.get(0));
    }

    private static void validate(Map<String, Object> filter) {
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            if (!KEYS.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unknown native query filter: " + entry.getKey());
            }
            if (Arrays.asList("loaded", "bound", "sourceAvailable", "includeSynthetic").contains(entry.getKey())) {
                if (!(entry.getValue() instanceof Boolean)) {
                    throw new IllegalArgumentException("Expected native query boolean: " + entry.getKey());
                }
            } else if (!entry.getKey().equals("limit") && (!(entry.getValue() instanceof String)
                    || ((String) entry.getValue()).length() > 512)) {
                throw new IllegalArgumentException("Expected bounded native query string: " + entry.getKey());
            }
        }
        if (filter.containsKey("names") && !Arrays.asList("auto", "mapped", "runtime").contains(filter.get("names"))) {
            throw new IllegalArgumentException("Invalid native name domain");
        }
        if (filter.containsKey("side") && !Arrays.asList("client", "server").contains(filter.get("side"))) {
            throw new IllegalArgumentException("Invalid native side");
        }
        if (filter.containsKey("access")
                && !Arrays.asList("public", "protected", "private", "package").contains(filter.get("access"))) {
            throw new IllegalArgumentException("Invalid native member access filter");
        }
        if (filter.containsKey("memberKind") && !Arrays.asList("FIELD", "METHOD", "CONSTRUCTOR", "CLASS_INITIALIZER")
                .contains(filter.get("memberKind"))) {
            throw new IllegalArgumentException("Invalid native member kind");
        }
    }

    private static boolean matches(Map<String, Object> row, Map<String, Object> filter) {
        if (!Boolean.TRUE.equals(filter.get("includeSynthetic")) && row.get("modifiers") instanceof Number) {
            int flags = ((Number) row.get("modifiers")).intValue();
            if ((flags & 0x1000) != 0 || ("METHOD".equals(row.get("kind")) && (flags & 0x40) != 0)) {
                return false;
            }
        }
        String domain = (String) filter.getOrDefault("names", "auto");
        String primary = (String) row.getOrDefault(domain.equals("runtime") ? "runtimeName" : "name",
                row.getOrDefault("path", row.getOrDefault("id", "")));
        if (domain.equals("mapped") && !"mapped".equals(row.get("nameDomain"))) {
            return false;
        }
        String alternate = domain.equals("auto") ? (String) row.getOrDefault("runtimeName", primary) : primary;
        if (!nameMatches(primary, filter) && !nameMatches(alternate, filter)) {
            return false;
        }
        for (String key : Arrays.asList("originId", "declaringType", "access", "loaded", "bound", "sourceAvailable")) {
            Object actual = row.get(key);
            if (key.equals("originId") && actual == null) {
                actual = row.get("id");
            }
            if (filter.containsKey(key) && !filter.get(key).equals(actual)) {
                return false;
            }
        }
        Object descriptor = row.get(domain.equals("runtime") ? "runtimeDescriptor" : "descriptor");
        if (filter.containsKey("descriptor") && !filter.get("descriptor").equals(descriptor)
                && !(domain.equals("auto") && filter.get("descriptor").equals(row.get("runtimeDescriptor")))) {
            return false;
        }
        if (filter.containsKey("memberKind") && !filter.get("memberKind").equals(row.get("kind"))) {
            return false;
        }
        if (filter.containsKey("package")) {
            String owner = (String) row.getOrDefault("declaringType", primary);
            String prefix = (String) filter.get("package");
            if (!owner.startsWith(prefix.endsWith(".") ? prefix : prefix + ".")) {
                return false;
            }
        }
        if (filter.containsKey("side")) {
            String side = "server".equals(row.get("namespace")) ? "server" : "client";
            if (!side.equals(filter.get("side"))) {
                return false;
            }
        }
        return !filter.containsKey("loaderId") || contains(row.get("loaderIds"), filter.get("loaderId"))
                || contains(row.get("definingLoaderIds"), filter.get("loaderId"))
                || filter.get("loaderId").equals(row.get("id"));
    }

    private static boolean contains(Object values, Object value) {
        return values instanceof List && ((List<?>) values).contains(value);
    }

    private static boolean nameMatches(String name, Map<String, Object> filter) {
        return (!filter.containsKey("name") || filter.get("name").equals(name))
                && (!filter.containsKey("prefix") || name.startsWith((String) filter.get("prefix")))
                && (!filter.containsKey("glob") || glob(name, (String) filter.get("glob")));
    }

    static boolean glob(String text, String pattern) {
        int position = 0;
        int token = 0;
        int star = -1;
        int retry = 0;
        while (position < text.length()) {
            if (token < pattern.length() && (pattern.charAt(token) == '?' || pattern.charAt(token) == text.charAt(position))) {
                token++;
                position++;
            } else if (token < pattern.length() && pattern.charAt(token) == '*') {
                star = token++;
                retry = position;
            } else if (star >= 0) {
                token = star + 1;
                position = ++retry;
            } else {
                return false;
            }
        }
        while (token < pattern.length() && pattern.charAt(token) == '*') {
            token++;
        }
        return token == pattern.length();
    }

    private static int cursor(long generation, String digest, Object value) {
        if (value == null) {
            return 0;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode((String) value), StandardCharsets.UTF_8).split(":");
            if (parts.length != 3 || Long.parseLong(parts[0]) != generation || !parts[1].equals(digest)) {
                throw new IllegalArgumentException("stale_cursor");
            }
            return Integer.parseInt(parts[2]);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("stale_cursor", exception);
        }
    }
}
