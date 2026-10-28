package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.NativeCatalogRecords;
import betamoon.interop.catalog.NativeClassInfo;
import betamoon.interop.catalog.NativeMemberInfo;
import betamoon.interop.diagnostics.NativeDiagnosticGuidance;
import betamoon.interop.source.NativeSourceIndex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One immutable, detached generation; every query/export sees exactly one
 * published instance.
 */
public final class NativeIndexSnapshot {
    public static final String[] KINDS = {"classes", "members", "origins", "loaders", "files", "sources", "hooks"};
    private static final NativeIndexSnapshot EMPTY = new NativeIndexSnapshot(0, "unknown", "unknown",
            Collections.emptyMap(), CatalogValues.record("ready", false, "runtimeUniverseComplete", false),
            Collections.singletonList("index_pending"), null);
    public final long generation;
    public final String namespace;
    public final String mappingSha256;
    public final Map<String, Object> coverage;
    public final List<String> warnings;
    public final NativeSourceIndex sources;
    public final long hookRevision;
    private final Map<String, List<Map<String, Object>>> tables;

    private NativeIndexSnapshot(long generation, String namespace, String mappingSha256,
            Map<String, List<Map<String, Object>>> tables, Map<String, Object> coverage, List<String> warnings,
            NativeSourceIndex sources) {
        this.generation = generation;
        this.namespace = namespace;
        this.mappingSha256 = mappingSha256;
        this.sources = sources;
        this.hookRevision = 0;
        this.coverage = CatalogValues.object(CatalogValues.freeze(coverage));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        Map<String, List<Map<String, Object>>> frozen = new LinkedHashMap<>();
        for (String kind : KINDS) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> row : tables.getOrDefault(kind, Collections.emptyList())) {
                rows.add(CatalogValues.object(CatalogValues.freeze(row)));
            }
            rows.sort(Comparator.comparing(NativeIndexSnapshot::sortKey));
            frozen.put(kind, Collections.unmodifiableList(rows));
        }
        this.tables = Collections.unmodifiableMap(frozen);
    }

    public List<Map<String, Object>> rows(String kind) {
        List<Map<String, Object>> rows = tables.get(kind);
        if (rows == null) {
            throw new IllegalArgumentException("Unknown native query kind: " + kind);
        }
        return rows;
    }

    /**
     * Shares the large immutable class tables; captures only the bounded hook
     * status separately.
     */
    public NativeIndexSnapshot withHooks(List<Map<String, Object>> hooks, long revision, String status) {
        return new NativeIndexSnapshot(this, hooks, revision, status);
    }

    private NativeIndexSnapshot(NativeIndexSnapshot base, List<Map<String, Object>> hooks, long revision,
            String status) {
        generation = base.generation;
        namespace = base.namespace;
        mappingSha256 = base.mappingSha256;
        sources = base.sources;
        warnings = base.warnings;
        hookRevision = revision;
        Map<String, Object> coverage = new LinkedHashMap<>(base.coverage);
        coverage.put("hooks", status);
        this.coverage = CatalogValues.object(CatalogValues.freeze(coverage));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> hook : hooks) {
            Map<String, Object> record = new LinkedHashMap<>(hook);
            record.put("runtimeNamespace", namespace);
            record.put("evidence", "captured_hook_status");
            if (!record.containsKey("guidance")) {
                record.put("guidance", NativeDiagnosticGuidance.forStatus((String) record.get("status")));
            }
            rows.add(CatalogValues.object(CatalogValues.freeze(record)));
        }
        rows.sort(Comparator.comparing(NativeIndexSnapshot::sortKey));
        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>(base.tables);
        tables.put("hooks", Collections.unmodifiableList(rows));
        this.tables = Collections.unmodifiableMap(tables);
    }

    /**
     * Execution metadata reports hook availability without building diagnostic
     * rows.
     */
    public NativeIndexSnapshot withHookAvailability(String status, long revision) {
        return new NativeIndexSnapshot(this, status, revision);
    }

    private NativeIndexSnapshot(NativeIndexSnapshot base, String status, long revision) {
        generation = base.generation;
        namespace = base.namespace;
        mappingSha256 = base.mappingSha256;
        sources = base.sources;
        warnings = base.warnings;
        tables = base.tables;
        hookRevision = revision;
        Map<String, Object> capturedCoverage = new LinkedHashMap<>(base.coverage);
        capturedCoverage.put("hooks", status);
        coverage = CatalogValues.object(CatalogValues.freeze(capturedCoverage));
    }

    public static NativeIndexSnapshot empty() {
        return EMPTY;
    }

    private static String sortKey(Map<String, Object> row) {
        return row.getOrDefault("declaringType", "") + ":" + row.getOrDefault("name", row.getOrDefault("path", ""))
                + ":" + row.getOrDefault("descriptor", "") + ":" + row.getOrDefault("originId", "") + ":"
                + row.getOrDefault("id", "");
    }

    public static final class Builder {
        private final long generation;
        private final String namespace;
        private final BundledNativeCatalog bundled;
        private final NativeLoaderRegistry.Capture capture;
        private final NativeSourceIndex sources;
        private final Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        private final Map<String, List<String>> loaded = new HashMap<>();
        private long estimatedBytes;
        private int failures;
        private int cacheHits;
        private int successes;
        private boolean limited;

        public Builder(long generation, String namespace, BundledNativeCatalog bundled,
                NativeLoaderRegistry.Capture capture, NativeSourceIndex sources) {
            this.generation = generation;
            this.namespace = namespace;
            this.bundled = bundled;
            this.capture = capture;
            this.sources = sources;
            for (String kind : KINDS) {
                tables.put(kind, new ArrayList<>());
            }
            tables.get("loaders").addAll(capture.loaders);
            tables.get("sources").addAll(sources.files());
            for (Map<String, Object> record : capture.loaded) {
                String key = record.get("originId") + ":" + record.get("name");
                List<String> loaders = loaded.computeIfAbsent(key, unused -> new ArrayList<>());
                String loader = (String) record.get("loaderId");
                if (!loaders.contains(loader)) {
                    loaders.add(loader);
                }
            }
        }

        public void add(NativeOriginInput input, NativeOriginCache.Result result) throws IOException {
            OriginMetadata metadata = result.metadata;
            long bytes = 0;
            int members = 0;
            for (NativeClassInfo type : metadata.classes.values()) {
                bytes += CatalogJson.encode(NativeCatalogRecords.type(type)).length() * 2L;
                members += type.getMembers().size();
            }
            if (estimatedBytes + bytes > 64L * 1024 * 1024
                    || tables.get("classes").size() + metadata.classes.size() > 30000
                    || tables.get("members").size() + members > 250000
                    || tables.get("files").size() + metadata.files.size() > 150000) {
                limited = true;
                throw new IOException("aggregate_index_limit");
            }
            estimatedBytes += bytes;
            tables.get("origins")
                    .add(CatalogValues.record("id", input.id, "name", input.path.getFileName().toString(), "path",
                            input.path.toString(), "loaderIds", input.loaderIds, "modEntries", input.modEntries,
                            "evidence", input.evidence, "fingerprint", metadata.fingerprint, "fingerprintKind",
                            metadata.fingerprintKind, "runtimeVisible", !input.loaderIds.isEmpty(), "status", "indexed",
                            "cacheHit", result.cacheHit, "classCount", metadata.classes.size()));
            for (NativeClassInfo type : metadata.classes.values()) {
                addType(input, metadata, type);
            }
            for (Map<String, Object> file : metadata.files) {
                Map<String, Object> row = new LinkedHashMap<>(file);
                row.put("id", input.id + ":" + file.get("path"));
                row.put("originId", input.id);
                row.put("loaderIds", input.loaderIds);
                tables.get("files").add(row);
            }
            successes++;
            if (result.cacheHit) {
                cacheHits++;
            }
        }

        public void failed(NativeOriginInput input, String reason) {
            failures++;
            tables.get("origins")
                    .add(CatalogValues.record("id", input.id, "name", input.path.getFileName().toString(), "path",
                            input.path.toString(), "loaderIds", input.loaderIds, "modEntries", input.modEntries,
                            "evidence", input.evidence, "status", "failed", "reason", reason));
        }

        private void addType(NativeOriginInput input, OriginMetadata metadata, NativeClassInfo type) {
            String canonical = input.gameOrigin && bundled != null
                    ? bundled.classNames.get(type.getInternalName())
                    : null;
            String name = (canonical == null ? type.getInternalName() : canonical).replace('/', '.');
            List<String> defining = loaded.getOrDefault(input.id + ":" + type.getInternalName().replace('/', '.'),
                    Collections.emptyList());
            Boolean observed = defining.isEmpty() ? capture.completeCensus ? Boolean.FALSE : null : Boolean.TRUE;
            List<Map<String, Object>> source = sources.find(name, type.getSourceFile(), metadata.fingerprint);
            boolean available = source.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("available"))
                    && !"source_mismatch".equals(row.get("confidence")));
            Map<String, Object> row = CatalogValues.record("id", input.id + ":" + type.getInternalName(), "name", name,
                    "runtimeName", type.getInternalName().replace('/', '.'), "canonicalName",
                    canonical == null ? null : canonical.replace('/', '.'), "nameDomain",
                    canonical == null ? "runtime" : "mapped", "namespace", namespace, "kind", type.getKind(), "access",
                    visibility(type.getAccess()), "modifiers", type.getAccess(), "originId", input.id, "loaderIds",
                    input.loaderIds, "definingLoaderIds", defining, "runtimeVisible", !input.loaderIds.isEmpty(),
                    "loaded", observed, "bound", false, "structureEvidence", "class_file", "runtimeStructureVerified",
                    false, "stateAsOfGeneration", generation, "sourceAvailable", available, "sourceFile",
                    type.getSourceFile(), "memberCount", type.getMembers().size(), "superName", type.getSuperName(),
                    "interfaces", type.getInterfaces(), "signature", type.getSignature(), "sourceMatches", source);
            tables.get("classes").add(row);
            for (NativeMemberInfo member : type.getMembers().values()) {
                Map<String, Object> alias = input.gameOrigin && bundled != null
                        ? bundled.memberNames.get(BundledNativeCatalog.memberKey(type.getInternalName(),
                                member.getName(), member.getDescriptor()))
                        : null;
                Map<String, Object> entry = NativeCatalogRecords.member(type, member);
                entry.put("id", input.id + ":" + type.getInternalName() + ":" + member.getKey());
                entry.put("name", alias == null ? member.getName() : alias.get("name"));
                entry.put("descriptor", alias == null ? member.getDescriptor() : alias.get("descriptor"));
                entry.put("runtimeName", member.getName());
                entry.put("runtimeDescriptor", member.getDescriptor());
                entry.put("declaringType", name);
                entry.put("originId", input.id);
                entry.put("loaderIds", input.loaderIds);
                entry.put("definingLoaderIds", defining);
                entry.put("access", visibility(member.getAccess()));
                entry.put("modifiers", member.getAccess());
                entry.put("loaded", observed);
                entry.put("bound", false);
                entry.put("nameDomain", alias == null ? "runtime" : "mapped");
                entry.put("namespace", namespace);
                entry.put("sourceAvailable", available);
                entry.put("effectConfidence", "unknown");
                tables.get("members").add(entry);
            }
        }

        public NativeIndexSnapshot build(List<String> warnings) {
            return new NativeIndexSnapshot(generation, namespace, bundled == null ? "unknown" : bundled.mappingSha256,
                    tables,
                    CatalogValues.record("ready", true, "knownOriginsComplete",
                            failures == 0 && !limited && !capture.warnings.contains("origin_count_limit"),
                            "runtimeUniverseComplete", false, "loadedCensusComplete", capture.completeCensus,
                            "loadedStateAsOfGeneration", generation, "indexedOrigins", successes, "failedOrigins",
                            failures, "cacheHits", cacheHits, "estimatedMetadataBytes", estimatedBytes, "indexLimited",
                            limited, "warningsMayBeTruncated", warnings.size() >= 64, "hooks", "not_implemented"),
                    warnings, sources);
        }
    }

    public static String visibility(int flags) {
        return (flags & 1) != 0 ? "public" : (flags & 4) != 0 ? "protected" : (flags & 2) != 0 ? "private" : "package";
    }
}
