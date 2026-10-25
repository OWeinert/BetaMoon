package betamoon.debug;

import betamoon.interop.NativeDiscoveryTest;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.discovery.NativeDiscoveryService;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.discovery.NativeOriginInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Real export-session checks for native profiles, valid structured output and explicit truncation. */
public final class NativeDebugExportTest {
    private NativeDebugExportTest() {
    }

    public static void run() throws Exception {
        Path parent = Paths.get("build").toRealPath();
        Path work = Files.createTempDirectory(parent, "native-debug-export-");
        try {
            Path jar = work.resolve("fixture.jar");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
                for (String name : new String[] {"fixture/First", "fixture/Second"}) {
                    ClassWriter writer = new ClassWriter(0);
                    writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
                    writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            "message", "Ljava/lang/String;", null, "Grüße \"quoted\"").visitEnd();
                    writer.visitEnd();
                    output.putNextEntry(new ZipEntry(name + ".class"));
                    output.write(writer.toByteArray());
                    output.closeEntry();
                }
            }
            NativeLoaderRegistry.Capture capture = new NativeLoaderRegistry.Capture(Collections.singletonList(
                    new NativeOriginInput("fixture", jar, Collections.emptyList(), Collections.emptyList(), false,
                            "mods_candidate")), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), false);
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
                        require(text.contains("\"recordType\":\"completion\"")
                                && text.contains("\"truncated\":false"), "Missing completeness footer");
                    } else if (filename.endsWith(".json")) {
                        require(CatalogJson.decode(text) instanceof Map, "Structured export has a prose header");
                    }
                }
                require(read(full, "native_members.jsonl").contains("Grüße \\\"quoted\\\""),
                        "Native Unicode/escaping or compiled constants were lost");
                DebugExportResult limited = DebugExportSession.run(work.resolve("exports").toFile(), "limited",
                        Collections.singletonList(definition(true, snapshot, 1)));
                require(limited.getStatus() == DebugExportResult.Status.INCOMPLETE,
                        "Truncated native export claimed completeness");
                require(read(limited, "native_classes.jsonl").contains("\"truncated\":true"),
                        "Truncation missing from structured output");
                verifyStructuredFailure(work, compact);
            }
        } finally {
            Path resolved = work.toRealPath();
            require(resolved.getParent().equals(parent) && resolved.getFileName().toString().startsWith("native-debug-export-"),
                    "Unsafe export fixture cleanup");
            try (Stream<Path> paths = Files.walk(resolved)) {
                for (Path path : (Iterable<Path>) paths.sorted(Collections.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
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
                new DebugNativeExporter(detailed, () -> snapshot, limit), declared.getFileNames().toArray(new String[0]));
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
