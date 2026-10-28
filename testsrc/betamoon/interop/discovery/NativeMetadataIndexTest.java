package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogValues;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Large detached tables: exact indexing must preserve full-scan matching and
 * pagination semantics.
 */
final class NativeMetadataIndexTest {
    static void verify() throws Exception {
        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        List<Map<String, Object>> classes = new ArrayList<>();
        List<Map<String, Object>> members = new ArrayList<>();
        tables.put("classes", classes);
        tables.put("members", members);
        for (int type = 0; type < 5000; type++) {
            String owner = "fixture.Type" + type;
            String origin = "origin" + type % 4;
            classes.add(CatalogValues.record("id", "c" + type, "name", owner, "runtimeName", "runtime.T" + type,
                    "nameDomain", "mapped", "originId", origin, "modifiers", 1));
            for (int member = 0; member < 8; member++) {
                members.add(CatalogValues.record("id", owner + ":" + member, "name", "method" + member, "runtimeName",
                        "m" + member, "descriptor", "()Lfixture/Type" + type + ";", "runtimeDescriptor",
                        "()Lruntime/T" + type + ";", "declaringType", owner, "originId", origin, "kind", "METHOD",
                        "modifiers", member == 7 ? 4097 : 1, "nameDomain", "mapped"));
            }
        }
        Constructor<NativeIndexSnapshot> constructor = NativeIndexSnapshot.class.getDeclaredConstructor(long.class,
                String.class, String.class, Map.class, Map.class, List.class,
                betamoon.interop.source.NativeSourceIndex.class);
        constructor.setAccessible(true);
        NativeIndexSnapshot snapshot = constructor.newInstance(17L, "named", "fixture", tables,
                CatalogValues.record("ready", true), Collections.emptyList(), null);
        classes.clear();
        members.clear();
        require(snapshot.rows("members").size() == 40000, "Snapshot aliases mutable input tables");
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(CatalogValues.record("kind", "classes", "name", "fixture.Type4999"));
        filters.add(CatalogValues.record("kind", "classes", "name", "runtime.T4999", "names", "runtime"));
        filters.add(CatalogValues.record("kind", "classes", "name", "missing"));
        filters.add(CatalogValues.record("kind", "classes", "originId", "origin2", "prefix", "fixture.Type1"));
        filters.add(CatalogValues.record("kind", "members", "declaringType", "fixture.Type3999"));
        filters.add(CatalogValues.record("kind", "members", "descriptor", "()Lruntime/T3999;", "names", "runtime"));
        filters.add(CatalogValues.record("kind", "members", "name", "method4", "originId", "origin3"));
        filters.add(
                CatalogValues.record("kind", "members", "originId", "origin1", "declaringType", "fixture.Type3999"));
        filters.add(CatalogValues.record("kind", "members", "glob", "method?", "includeSynthetic", true));
        Method matches = NativeQueryService.class.getDeclaredMethod("matches", Map.class, Map.class);
        matches.setAccessible(true);
        for (Map<String, Object> filter : filters) {
            filter.put("limit", 137);
            List<Object> expected = new ArrayList<>();
            for (Map<String, Object> row : snapshot.rows((String) filter.get("kind"))) {
                if ((boolean) matches.invoke(null, row, filter)) {
                    expected.add(row.get("id"));
                }
            }
            List<Object> actual = new ArrayList<>();
            Map<String, Object> current = new LinkedHashMap<>(filter);
            do {
                Map<String, Object> page = NativeQueryService.query(snapshot, current);
                for (Object item : (List<?>) page.get("items")) {
                    actual.add(((Map<?, ?>) item).get("id"));
                }
                if (page.get("nextCursor") == null) {
                    break;
                }
                current.put("cursor", page.get("nextCursor"));
            } while (true);
            require(actual.equals(expected), "Index changed full-scan results/order: " + filter);
        }
        List<Map<String, Object>> small = snapshot.candidates("members",
                CatalogValues.record("declaringType", "fixture.Type4999"));
        require(small.size() == 8, "Exact owner lookup scanned unrelated members");
        try {
            small.clear();
            throw new AssertionError("Mutable index rows");
        } catch (UnsupportedOperationException expected) {
        }
        Map<String, Object> row = snapshot.rows("classes").get(0);
        Map<String, Object> page = NativeQueryService.query(snapshot, CatalogValues.record("name", row.get("name")));
        require(((List<?>) page.get("items")).get(0) == row, "Query recopied immutable catalog rows");
        require(snapshot.withHooks(Collections.emptyList(), 4, "available")
                .candidates("members", CatalogValues.record("declaringType", "fixture.Type4999")).size() == 8,
                "Hook overlay lost base indexes");
        BundledNativeCatalog first = new BundledNativeCatalog("named");
        BundledNativeCatalog second = new BundledNativeCatalog("named");
        require(first.classes == second.classes && first.classNames == second.classNames
                && first.sourceFiles == second.sourceFiles, "Verified bundled parse was repeated");
        Thread.currentThread().interrupt();
        try {
            constructor.newInstance(18L, "named", "fixture",
                    Collections.singletonMap("classes", snapshot.rows("classes")), Collections.emptyMap(),
                    Collections.emptyList(), null);
            throw new AssertionError("Cancelled index build completed");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            require(expected.getCause() instanceof java.util.concurrent.CancellationException,
                    "Wrong cancellation failure");
        } finally {
            Thread.interrupted();
        }
        System.out.println(
                "Metadata indexes passed: 5000 classes/40000 members, full-scan parity, immutable rows, bounded lookup and cancellation.");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
