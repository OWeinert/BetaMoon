package betamoon.debug;

import betamoon.interop.NativeDiscoveryTest;
import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.NativeAuthoringCatalog;
import betamoon.interop.discovery.NativeDiscoveryService;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.discovery.NativeOriginInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Real export-session checks for native profiles, valid structured output and
 * explicit truncation.
 */
public final class NativeDebugExportTest {
    private NativeDebugExportTest() {
    }

    public static void run() throws Exception {
        Path parent = Paths.get("build").toRealPath();
        Path work = Files.createTempDirectory(parent, "native-debug-export-");
        try {
            Path jar = work.resolve("fixture.jar");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
                for (String name : new String[]{"fixture/First", "fixture/Second"}) {
                    ClassWriter writer = new ClassWriter(0);
                    writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
                    writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "message",
                            "Ljava/lang/String;", null, "Grüße \"quoted\"").visitEnd();
                    writer.visitEnd();
                    output.putNextEntry(new ZipEntry(name + ".class"));
                    output.write(writer.toByteArray());
                    output.closeEntry();
                }
            }
            NativeLoaderRegistry.Capture capture = new NativeLoaderRegistry.Capture(
                    Collections.singletonList(new NativeOriginInput("fixture", jar, Collections.emptyList(),
                            Collections.emptyList(), false, "mods_candidate")),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
            try (NativeDiscoveryService service = new NativeDiscoveryService(work.resolve("cache"))) {
                NativeDiscoveryTest.await(service, service.request(capture, "named", Collections.emptyList()));
                NativeIndexSnapshot snapshot = service.snapshot();
                DebugExportDefinition compact = definition(false, snapshot, 100000);
                DebugExportDefinition detailed = definition(true, snapshot, 100000);
                DebugExportResult normal = DebugExportSession.run(work.resolve("exports").toFile(), "compact",
                        Collections.singletonList(compact));
                require(normal.getStatus() == DebugExportResult.Status.COMPLETE, "Compact native export failed");
                require(!normal.getDirectory().toPath().resolve("native_members.jsonl").toFile().exists(),
                        "Unchecked export wrote bulky native data");
                DebugExportResult full = DebugExportSession.run(work.resolve("exports").toFile(), "detailed",
                        Collections.singletonList(detailed));
                require(full.getStatus() == DebugExportResult.Status.COMPLETE, "Detailed native export failed");
                for (String filename : detailed.getFileNames()) {
                    String text = read(full, filename);
                    if (filename.endsWith(".jsonl")) {
                        for (String line : text.split("\n")) {
                            require(CatalogJson.decode(line) instanceof Map, "Invalid native JSONL");
                        }
                        if (!filename.equals(NativeAuthoringCatalog.RECORDS_FILE)) {
                            require(text.contains("\"recordType\":\"completion\"")
                                    && text.contains("\"truncated\":false"), "Missing completeness footer");
                        }
                    } else if (filename.endsWith(".json")) {
                        require(CatalogJson.decode(text) instanceof Map, "Structured export has a prose header");
                    }
                }
                require(read(full, "native_members.jsonl").contains("Grüße \\\"quoted\\\""),
                        "Native Unicode/escaping or compiled constants were lost");
                verifyAuthoring(full, snapshot);
                DebugExportResult limited = DebugExportSession.run(work.resolve("exports").toFile(), "limited",
                        Collections.singletonList(definition(true, snapshot, 1)));
                require(limited.getStatus() == DebugExportResult.Status.INCOMPLETE,
                        "Truncated native export claimed completeness");
                require(read(limited, "native_classes.jsonl").contains("\"truncated\":true"),
                        "Truncation missing from structured output");
                verifyAuthoring(limited, snapshot);
                DebugExportResult pending = DebugExportSession.run(work.resolve("exports").toFile(), "pending",
                        Collections.singletonList(definition(true, NativeIndexSnapshot.empty(), 100000)));
                require(pending.getStatus() == DebugExportResult.Status.INCOMPLETE && !pending.getDirectory().toPath()
                        .resolve(NativeAuthoringCatalog.MANIFEST_FILE).toFile().exists(),
                        "A pending index published a usable-looking authoring manifest");
                verifyStructuredFailure(work, compact);
                verifyAsync(work, snapshot);
            }
        } finally {
            Path resolved = work.toRealPath();
            require(resolved.getParent().equals(parent)
                    && resolved.getFileName().toString().startsWith("native-debug-export-"),
                    "Unsafe export fixture cleanup");
            try (Stream<Path> paths = Files.walk(resolved)) {
                for (Path path : (Iterable<Path>) paths.sorted(Collections.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void verifyAsync(Path work, NativeIndexSnapshot snapshot) throws Exception {
        Path exports = Files.createDirectories(work.resolve("async-exports"));
        Map<String, Object> status = CatalogValues.record("lastFailure", "captured failure");
        DebugNativeExportTask task = DebugNativeExportTask.start(exports.toFile(), snapshot, status);
        status.put("lastFailure", "later failure");
        await(task);
        require(task.getResult().getStatus() == DebugExportResult.Status.COMPLETE, "Async native export failed");
        require(read(task.getResult(), "native_failures.json").contains("captured failure")
                && !read(task.getResult(), "native_failures.json").contains("later failure"),
                "Worker read mutable caller status");
        verifyAuthoring(task.getResult(), snapshot);
        require(!task.cancel(), "Published export was retroactively cancelled");
        java.lang.reflect.Field definition = DebugNativeExportTask.class.getDeclaredField("definition");
        definition.setAccessible(true);
        require(definition.get(task) == null, "Completed task retained its catalog capture");

        java.util.concurrent.CountDownLatch writing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        DebugExportDefinition held = new DebugExportDefinition("held", "Held", "Fixture", false, session -> {
            session.writeStructuredFile("held.json", writer -> {
                writer.write("{\"incomplete\":");
                writing.countDown();
                release.await();
                writer.write("true}");
                return 1;
            });
        }, "held.json");
        Path cancelledRoot = Files.createDirectories(work.resolve("cancelled-exports"));
        DebugNativeExportTask cancelled = DebugNativeExportTask.submit(cancelledRoot.toFile(), held);
        try {
            require(writing.await(5, java.util.concurrent.TimeUnit.SECONDS), "Worker did not start");
            for (int index = 0; index < 100; index++) {
                require(DebugNativeExportTask.submit(cancelledRoot.toFile(), held) == cancelled, "Worker queue grew");
                require(!((List<?>) betamoon.interop.discovery.NativeQueryService
                        .query(snapshot, CatalogValues.record("kind", "classes", "limit", 1)).get("items")).isEmpty(),
                        "Client metadata queries stalled behind export");
            }
            require(cancelled.getProgress().contains("held.json") && cancelled.cancel(),
                    "Progress/cancellation unavailable");
            await(cancelled);
            require(cancelled.isCancelled() && cancelled.getResult().getDirectory() == null,
                    "Cancellation published a directory");
            try (Stream<Path> paths = Files.list(cancelledRoot)) {
                require(paths.count() == 0, "Cancelled export left temporary or usable-looking output");
            }
        } finally {
            release.countDown();
            cancelled.cancel();
            await(cancelled);
        }
        DebugNativeExportTask recovery = DebugNativeExportTask.start(exports.toFile(), snapshot,
                Collections.emptyMap());
        await(recovery);
        require(recovery.getResult().getStatus() == DebugExportResult.Status.COMPLETE,
                "Cancellation poisoned the next worker run");
    }

    private static void await(DebugNativeExportTask task) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (!task.isDone()) {
            require(System.nanoTime() < deadline, "Native export timed out: " + task.getProgress());
            Thread.sleep(1);
        }
    }

    private static void verifyAuthoring(DebugExportResult result, NativeIndexSnapshot snapshot) throws Exception {
        Map<String, Object> manifest = CatalogValues
                .object(CatalogJson.decode(read(result, NativeAuthoringCatalog.MANIFEST_FILE)));
        require(NativeAuthoringCatalog.FORMAT.equals(manifest.get("format"))
                && Boolean.TRUE.equals(manifest.get("transportComplete")), "Missing authoring handoff contract");
        Map<String, Object> diagnostics = CatalogValues.object(manifest.get("diagnostics"));
        byte[] failureBytes = Files.readAllBytes(result.getDirectory().toPath().resolve("native_failures.json"));
        require("native_failures.json".equals(diagnostics.get("path"))
                && ((Number) diagnostics.get("bytes")).longValue() == failureBytes.length
                && CatalogFingerprint.sha256(failureBytes).equals(diagnostics.get("sha256")),
                "Diagnostic companion is not bound to the catalog export");
        require("exported_snapshot".equals(CatalogValues
                .object(CatalogJson.decode(new String(failureBytes, StandardCharsets.UTF_8))).get("evidence")),
                "Export claims live diagnostic state");
        Map<String, Object> file = CatalogValues.object(((List<?>) manifest.get("files")).get(0));
        Path records = result.getDirectory().toPath().resolve(NativeAuthoringCatalog.RECORDS_FILE);
        require(CatalogFingerprint.sha256(records).equals(file.get("sha256")), "Authoring payload hash mismatch");
        require(((Number) file.get("bytes")).longValue() == Files.size(records), "UTF-8 byte count mismatch");
        Map<String, Object> counts = CatalogValues.object(manifest.get("counts"));
        int total = 0;
        for (String kind : NativeIndexSnapshot.KINDS) {
            require(((Number) counts.get(kind)).intValue() == snapshot.rows(kind).size(), "Lost catalog rows");
            total += snapshot.rows(kind).size();
        }
        require(read(result, NativeAuthoringCatalog.RECORDS_FILE).split("\n").length == total,
                "Authoring transport was truncated by the legacy debug row limit");
        // Retain only synthetic metadata for the independent Node reader
        // interoperability gate.
        Path directory = Paths.get("build", "native-authoring-fixture");
        Files.createDirectories(directory);
        for (String name : new String[]{NativeAuthoringCatalog.MANIFEST_FILE, NativeAuthoringCatalog.RECORDS_FILE,
                "native_failures.json"}) {
            Files.copy(result.getDirectory().toPath().resolve(name), directory.resolve(name),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void verifyStructuredFailure(Path work, DebugExportDefinition compact) throws Exception {
        DebugExportDefinition broken = new DebugExportDefinition("broken-structured", "Broken", "Fixture", true,
                session -> session.writeStructuredFile("broken.json", writer -> {
                    writer.write("{\"unfinished\":");
                    throw new IOException("expected structured failure");
                }), "broken.json");
        DebugExportResult result = DebugExportSession.run(work.resolve("exports").toFile(), "broken",
                Arrays.asList(broken, compact));
        require(result.getStatus() == DebugExportResult.Status.INCOMPLETE, "Structured failure was hidden");
        require(!result.getDirectory().toPath().resolve("broken.json").toFile().exists()
                && !result.getDirectory().toPath().resolve("broken.json.part").toFile().exists(),
                "Partial structured file was published");
        require(result.getDirectory().toPath().resolve("native_summary.txt").toFile().isFile(),
                "Structured failure stopped unrelated successful exports");
    }

    private static DebugExportDefinition definition(boolean detailed, NativeIndexSnapshot snapshot, int limit) {
        DebugExportDefinition declared = DebugExportCatalog.completeDefinitions(detailed).stream()
                .filter(definition -> definition.getId().equals(DebugExportCatalog.NATIVE_ACCESS_ID)).findFirst().get();
        return new DebugExportDefinition(declared.getId(), declared.getTitle(), declared.getDescription(), true,
                new DebugNativeExporter(detailed, () -> snapshot, limit),
                declared.getFileNames().toArray(new String[0]));
    }

    private static String read(DebugExportResult result, String filename) throws IOException {
        return new String(Files.readAllBytes(result.getDirectory().toPath().resolve(filename)), StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
