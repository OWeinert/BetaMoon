package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.NativeCatalogRecords;
import betamoon.interop.catalog.NativeClassInfo;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Verified build metadata. Name aliases are hints for the actual game origin, never classloader authority. */
public final class BundledNativeCatalog {
    private static final String ROOT = "/betamoon/interop/catalog/b1.7.3/";
    public final String namespace;
    public final String mappingSha256;
    public final String inputFingerprint;
    public final Map<String, NativeClassInfo> classes;
    public final Map<String, String> classNames;
    public final Map<String, Map<String, Object>> memberNames;
    public final List<Map<String, Object>> sourceFiles;
    private final Map<String, Object> manifest;

    public BundledNativeCatalog(String namespace) throws IOException {
        this.namespace = namespace;
        manifest = CatalogValues.object(CatalogJson.decode(new String(resource("manifest.json"), StandardCharsets.UTF_8)));
        if (CatalogValues.integer(manifest, "schemaVersion") != NativeCatalogRecords.SCHEMA_VERSION) {
            throw new IOException("bundled_catalog_schema_mismatch");
        }
        mappingSha256 = CatalogValues.text(manifest, "mappingSha256");
        inputFingerprint = (String) CatalogValues.object(manifest.get("inputSha256")).get(namespace);
        Map<String, NativeClassInfo> types = new TreeMap<>();
        if (namespace.equals("named") || namespace.equals("client") || namespace.equals("server")) {
            for (Map<String, Object> row : records("classes-" + namespace + ".jsonl")) {
                NativeClassInfo type = NativeCatalogRecords.readType(row);
                types.put(type.getInternalName(), type);
            }
        }
        classes = Collections.unmodifiableMap(types);
        Map<String, String> names = new HashMap<>();
        Map<String, Map<String, Object>> members = new HashMap<>();
        for (Map<String, Object> row : records("symbols.jsonl")) {
            Object selected = CatalogValues.object(row.get("targets")).get(namespace);
            if (!(selected instanceof Map)) {
                continue;
            }
            Map<String, Object> side = CatalogValues.object(selected);
            String state = CatalogValues.text(side, "state");
            if (!state.startsWith("VERIFIED_")) {
                continue;
            }
            String runtimeOwner = CatalogValues.text(side, "owner");
            if ("CLASS".equals(row.get("kind"))) {
                names.put(runtimeOwner, CatalogValues.text(row, "owner"));
            } else {
                members.put(memberKey(runtimeOwner, (String) side.get("name"), (String) side.get("descriptor")),
                        CatalogValues.object(CatalogValues.freeze(row)));
            }
        }
        classNames = Collections.unmodifiableMap(names);
        memberNames = Collections.unmodifiableMap(members);
        sourceFiles = Collections.unmodifiableList(records("source-files.jsonl"));
    }

    public static String memberKey(String owner, String name, String descriptor) {
        return owner + ":" + name + ":" + descriptor;
    }

    private List<Map<String, Object>> records(String name) throws IOException {
        byte[] bytes = resource(name);
        Object expected = CatalogValues.object(manifest.get("files")).get(name);
        if (!CatalogFingerprint.sha256(bytes).equals(expected)) {
            throw new IOException("bundled_catalog_hash_mismatch: " + name);
        }
        List<Map<String, Object>> records = new ArrayList<>();
        try (BufferedReader reader = CatalogValues.utf8(new ByteArrayInputStream(bytes))) {
            long[] remaining = {32L * 1024 * 1024};
            String line;
            while ((line = CatalogValues.line(reader, 2 * 1024 * 1024, remaining)) != null) {
                if (records.size() >= 100000) {
                    throw new IOException("bundled_catalog_record_limit");
                }
                records.add(CatalogValues.object(CatalogValues.freeze(CatalogJson.decode(line))));
            }
        }
        return records;
    }

    private byte[] resource(String name) throws IOException {
        try (InputStream input = BundledNativeCatalog.class.getResourceAsStream(ROOT + name)) {
            if (input == null) {
                throw new IOException("bundled_catalog_missing: " + name);
            }
            return CatalogValues.bytes(input, 32 * 1024 * 1024);
        }
    }
}
