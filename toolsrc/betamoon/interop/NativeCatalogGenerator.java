package betamoon.interop;

import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.mapping.TinyMappingSnapshot;
import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.ClassArchiveCatalog;
import betamoon.interop.catalog.ClassArchiveReader;
import betamoon.interop.catalog.NativeCatalogRecords;
import betamoon.interop.catalog.NativeClassInfo;
import betamoon.interop.catalog.NativeMemberInfo;
import betamoon.interop.mapping.MappingResolution;
import betamoon.interop.mapping.NativeMappingCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Build-time entry point. Never packaged and never run by the agent or game startup. */
public final class NativeCatalogGenerator {
    private final CatalogBundleWriter writer;
    private final TinyMappingSnapshot mappings;
    private final Map<RuntimeNamespace, ClassArchiveCatalog> archives;
    private final NativeMappingCatalog resolver;
    private final ClassArchiveCatalog namedServer;
    private final Map<String, Map<String, Integer>> resolutionCounts = new TreeMap<>();

    private NativeCatalogGenerator(Path output, TinyMappingSnapshot mappings,
            Map<RuntimeNamespace, ClassArchiveCatalog> archives, ClassArchiveCatalog namedServer) throws IOException {
        writer = new CatalogBundleWriter(output);
        this.mappings = mappings;
        this.archives = archives;
        this.namedServer = namedServer;
        resolver = new NativeMappingCatalog(mappings, archives.get(RuntimeNamespace.NAMED), namedServer, archives);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 9) {
            throw new IllegalArgumentException("Expected target, Tiny mappings, named/client/server JARs, "
                    + "source root, output directory, report directory, named server JAR");
        }
        Path mappingFile = Paths.get(args[1]);
        String mappingSha = CatalogFingerprint.sha256(mappingFile);
        TinyMappingSnapshot mappings;
        try (InputStream input = Files.newInputStream(mappingFile)) {
            mappings = TinyMappingResolver.readStrict(input).snapshot();
        }
        if (!mappingSha.equals(CatalogFingerprint.sha256(mappingFile))) {
            throw new IOException("Mappings changed during extraction");
        }
        Map<RuntimeNamespace, ClassArchiveCatalog> archives = new EnumMap<>(RuntimeNamespace.class);
        archives.put(RuntimeNamespace.NAMED, new ClassArchiveReader().read(Paths.get(args[2])));
        archives.put(RuntimeNamespace.CLIENT, new ClassArchiveReader().read(Paths.get(args[3])));
        archives.put(RuntimeNamespace.SERVER, new ClassArchiveReader().read(Paths.get(args[4])));
        ClassArchiveCatalog namedServer = new ClassArchiveReader().read(Paths.get(args[8]));
        NativeCatalogGenerator generator = new NativeCatalogGenerator(Paths.get(args[6]), mappings, archives,
                namedServer);
        SourceManifestGenerator sources = SourceManifestGenerator.scan("minecraft-client", Paths.get(args[5]));
        generator.generate(args[0], mappingSha, sources, Paths.get(args[7]));
    }

    private void generate(String target, String mappingSha, SourceManifestGenerator sources, Path reports)
            throws IOException {
        writeClassCatalogs();
        writeMappingRows();
        writeSymbols();
        writeSources(sources);
        Map<String, Object> coverage = coverage(sources);
        writer.document("coverage.json", coverage);
        Map<String, String> previous = CatalogDiffReport.readFingerprints(reports.resolve("fingerprints.json"));
        CatalogBundleWriter.writeDocument(reports.resolve("diff.json"),
                CatalogDiffReport.compare(previous, writer.getRecordHashes()));
        CatalogBundleWriter.writeDocument(reports.resolve("fingerprints.json"), writer.getRecordHashes());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", NativeCatalogRecords.SCHEMA_VERSION);
        manifest.put("extractorVersion", NativeCatalogRecords.EXTRACTOR_VERSION);
        manifest.put("target", target);
        manifest.put("javaTarget", 8);
        manifest.put("mappingSha256", mappingSha);
        Map<String, String> inputs = new TreeMap<>();
        for (Map.Entry<RuntimeNamespace, ClassArchiveCatalog> entry : archives.entrySet()) {
            inputs.put(entry.getKey().getMappingName(), entry.getValue().getContentSha256());
        }
        inputs.put("named-server", namedServer.getContentSha256());
        manifest.put("inputSha256", inputs);
        manifest.put("sourceRoots", Collections.singletonList("minecraft-client"));
        manifest.put("files", writer.getFileHashes());
        // Write the manifest last. Build/package tasks fail if any extraction step fails.
        writer.document("manifest.json", manifest);
        System.out.println("Native catalog generated: " + coverage);
    }

    private void writeClassCatalogs() throws IOException {
        for (Map.Entry<RuntimeNamespace, ClassArchiveCatalog> archive : archives.entrySet()) {
            Map<String, Map<String, Object>> records = new TreeMap<>();
            for (NativeClassInfo type : archive.getValue().getClasses().values()) {
                records.put(type.getInternalName(), NativeCatalogRecords.type(type));
            }
            writer.records("classes-" + archive.getKey().getMappingName() + ".jsonl", records);
        }
        Map<String, Map<String, Object>> serverRecords = new TreeMap<>();
        for (NativeClassInfo type : namedServer.getClasses().values()) {
            serverRecords.put(type.getInternalName(), NativeCatalogRecords.type(type));
        }
        writer.records("classes-named-server.jsonl", serverRecords);
    }

    private void writeMappingRows() throws IOException {
        Map<String, Map<String, Object>> records = new TreeMap<>();
        for (Map.Entry<String, TinyMappingSnapshot.ClassEntry> entry : mappings.getClasses().entrySet()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("name", entry.getKey());
            record.put("names", entry.getValue().getNames());
            List<Object> members = new ArrayList<>();
            for (TinyMappingSnapshot.MemberEntry member : entry.getValue().getMembers()) {
                Map<String, Object> memberRecord = new LinkedHashMap<>();
                memberRecord.put("kind", member.isMethod() ? "METHOD" : "FIELD");
                memberRecord.put("descriptor", member.getDescriptor());
                memberRecord.put("names", member.getNames());
                members.add(memberRecord);
            }
            record.put("members", members);
            records.put(entry.getKey(), record);
        }
        writer.records("tiny-mappings.jsonl", records);
    }

    private void writeSymbols() throws IOException {
        Map<String, Map<String, Object>> records = new TreeMap<>();
        Set<String> owners = new TreeSet<>(mappings.getClasses().keySet());
        owners.addAll(archives.get(RuntimeNamespace.NAMED).getClasses().keySet());
        owners.addAll(namedServer.getClasses().keySet());
        for (String owner : owners) {
            Map<String, Object> record = symbol(owner, null, null, null);
            records.put("class:" + owner, record);
            Map<String, Selector> selectors = selectors(owner);
            for (Selector selector : selectors.values()) {
                String kind = selector.method ? "METHOD" : "FIELD";
                records.put(kind + ":" + owner + ":" + selector.name + ":" + selector.descriptor,
                        symbol(owner, selector.name, selector.descriptor, selector.method));
            }
        }
        writer.records("symbols.jsonl", records);
    }

    private Map<String, Selector> selectors(String owner) {
        Map<String, Selector> selectors = new TreeMap<>();
        NativeClassInfo namedClass = archives.get(RuntimeNamespace.NAMED).getClasses().get(owner);
        if (namedClass != null) {
            for (NativeMemberInfo member : namedClass.getMembers().values()) {
                Selector selector = new Selector(member.getKind() != NativeMemberInfo.Kind.FIELD,
                        member.getName(), member.getDescriptor());
                selectors.put(selector.key(), selector);
            }
        }
        NativeClassInfo serverClass = namedServer.getClasses().get(owner);
        if (serverClass != null) {
            for (NativeMemberInfo member : serverClass.getMembers().values()) {
                Selector selector = new Selector(member.getKind() != NativeMemberInfo.Kind.FIELD,
                        member.getName(), member.getDescriptor());
                selectors.put(selector.key(), selector);
            }
        }
        TinyMappingSnapshot.ClassEntry row = mappings.getClasses().get(owner);
        if (row != null) {
            for (TinyMappingSnapshot.MemberEntry member : row.getMembers()) {
                Selector selector = new Selector(member.isMethod(), member.getNames().get("named"),
                        member.getDescriptor());
                selectors.put(selector.key(), selector);
            }
        }
        return selectors;
    }

    private Map<String, Object> symbol(String owner, String name, String descriptor, Boolean method) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("owner", owner);
        record.put("name", name);
        record.put("descriptor", descriptor);
        record.put("kind", method == null ? "CLASS" : method ? "METHOD" : "FIELD");
        Map<String, Object> sides = new TreeMap<>();
        for (RuntimeNamespace namespace : archives.keySet()) {
            MappingResolution resolution = method == null ? resolver.resolveClass(owner, namespace)
                    : resolver.resolveMember(owner, name, descriptor, method, namespace);
            Map<String, Object> side = new LinkedHashMap<>();
            side.put("state", resolution.getState().name());
            side.put("owner", resolution.getOwner());
            side.put("name", resolution.getName());
            side.put("descriptor", resolution.getDescriptor());
            side.put("detail", resolution.getDetail());
            side.put("mappingOwner", resolution.getMappingOwner());
            sides.put(namespace.getMappingName(), side);
            Map<String, Integer> counts = resolutionCounts.computeIfAbsent(namespace.getMappingName(),
                    key -> new TreeMap<>());
            String state = resolution.getState().name();
            counts.put(state, counts.getOrDefault(state, 0) + 1);
        }
        record.put("targets", sides);
        return record;
    }

    private void writeSources(SourceManifestGenerator sources) throws IOException {
        Map<String, Map<String, Object>> files = new TreeMap<>();
        for (Map<String, Object> record : sources.records()) {
            files.put((String) record.get("path"), record);
        }
        writer.records("source-files.jsonl", files);
        Map<String, Map<String, Object>> associations = new TreeMap<>();
        for (NativeClassInfo type : archives.get(RuntimeNamespace.NAMED).getClasses().values()) {
            associations.put(type.getInternalName(), sources.association(type));
        }
        writer.records("source-associations.jsonl", associations);
    }

    private Map<String, Object> coverage(SourceManifestGenerator sources) {
        Map<String, Object> coverage = new LinkedHashMap<>();
        Map<String, Object> classCounts = new TreeMap<>();
        Map<String, Object> memberCounts = new TreeMap<>();
        for (Map.Entry<RuntimeNamespace, ClassArchiveCatalog> archive : archives.entrySet()) {
            classCounts.put(archive.getKey().getMappingName(), archive.getValue().getClasses().size());
            int members = 0;
            for (NativeClassInfo type : archive.getValue().getClasses().values()) {
                members += type.getMembers().size();
            }
            memberCounts.put(archive.getKey().getMappingName(), members);
        }
        coverage.put("classes", classCounts);
        coverage.put("declaredMembers", memberCounts);
        classCounts.put("named-server", namedServer.getClasses().size());
        int serverNamedMembers = 0;
        for (NativeClassInfo type : namedServer.getClasses().values()) {
            serverNamedMembers += type.getMembers().size();
        }
        memberCounts.put("named-server", serverNamedMembers);
        coverage.put("mappingClassRows", mappings.getClasses().size());
        coverage.put("symbolResolution", resolutionCounts);
        coverage.put("sourceFiles", sources.records().size());
        Map<String, Integer> parseStates = new TreeMap<>();
        for (Map<String, Object> source : sources.records()) {
            String status = (String) source.get("parseStatus");
            parseStates.put(status, parseStates.getOrDefault(status, 0) + 1);
        }
        coverage.put("sourceParseStatus", parseStates);
        coverage.put("sourceBodiesIncluded", false);
        coverage.put("loadedState", "unknown");
        return coverage;
    }

    private static final class Selector {
        private final boolean method;
        private final String name;
        private final String descriptor;

        private Selector(boolean method, String name, String descriptor) {
            this.method = method;
            this.name = name;
            this.descriptor = descriptor;
        }

        private String key() {
            return (method ? "m:" : "f:") + name + ":" + descriptor;
        }
    }
}
