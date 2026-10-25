package betamoon.interop;

import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.discovery.NativeDiscoveryService;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.discovery.NativeOriginCache;
import betamoon.interop.discovery.NativeOriginInput;
import betamoon.interop.discovery.NativeQueryService;
import betamoon.interop.source.NativeSourceAttachment;
import betamoon.luaapi.interop.NativeDiscoveryApiTest;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Core N2 acceptance without Minecraft on the process classpath. */
public final class NativeDiscoveryTest {
    private NativeDiscoveryTest() {
    }

    public static void main(String[] args) throws Exception {
        try {
            Class.forName("net.minecraft.src.World");
            throw new AssertionError("Discovery fixtures must run without Minecraft");
        } catch (ClassNotFoundException expected) {
        }
        Path parent = Paths.get("build").toAbsolutePath().normalize().toRealPath();
        Path work = Files.createTempDirectory(parent, "native-discovery-");
        try {
            verify(work);
        } finally {
            Path resolved = work.toRealPath();
            require(resolved.getParent().equals(parent) && resolved.getFileName().toString().startsWith("native-discovery-"),
                    "Unsafe fixture cleanup target");
            try (Stream<Path> paths = Files.walk(resolved)) {
                for (Path path : (Iterable<Path>) paths.sorted(Collections.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("Native discovery passed: origins, cache recovery, sources, leases, pagination and Lua metadata.");
    }

    private static void verify(Path work) throws Exception {
        Path jar = work.resolve("game.jar");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("fixture/Known.class", type("fixture/Known", "value"));
        entries.put("mod_Test.class", type("mod_Test", "data"));
        entries.put("fixture/Other.class", type("fixture/Other", "data"));
        entries.put("nested/library.jar", new byte[] {1, 2, 3});
        zip(jar, entries);
        fails(() -> CatalogFingerprint.sha256(jar, 10), IOException.class);
        verifySourceArchiveBudget(work);
        Path duplicate = work.resolve("duplicate.jar");
        zip(duplicate, Collections.singletonMap("fixture/Known.class", type("fixture/Known", "alternative")));
        Path broken = work.resolve("broken.jar");
        Files.write(broken, new byte[] {0, 1, 2});
        Path sources = work.resolve("sources.jar");
        zip(sources, Collections.singletonMap("fixture/Known.java", ("package fixture;\r\nclass Known {\r\n"
                + "    String text = \"SECRET_BODY_Grüße\";\n}\n").getBytes(StandardCharsets.UTF_8)));

        NativeOriginCache cache = new NativeOriginCache(work.resolve("cache"));
        List<String> warnings = new ArrayList<>();
        NativeOriginCache.Result cold = cache.load(jar, null, null, warnings);
        require(!cold.cacheHit && cold.metadata.classes.size() == 3, "Cold archive extraction failed");
        require(cache.load(jar, null, null, warnings).cacheHit, "Unchanged content was not reused");
        Path moved = work.resolve("same-content.jar");
        Files.copy(jar, moved);
        require(cache.load(moved, null, null, warnings).cacheHit, "Moved identical content did not reuse metadata");
        Path cacheFile = work.resolve("cache/origins").resolve(cold.metadata.fingerprint).resolve("metadata-v1.json.gz");
        Files.write(cacheFile, new byte[] {0, 1, 2});
        require(!cache.load(jar, null, null, warnings).cacheHit, "Corrupt cache was not rebuilt");
        require(warnings.stream().anyMatch(message -> message.startsWith("cache_rebuilt:")), "Cache fault was hidden");
        require(System.getProperty("betamoon.native.fixture.initialized") == null, "Scanning initialized a class");

        Path directory = Files.createDirectory(work.resolve("directory"));
        Path bytecode = directory.resolve("Standalone.class");
        Files.write(bytecode, type("Standalone", "first"));
        String firstFingerprint = cache.load(directory, null, null, warnings).metadata.fingerprint;
        FileTime timestamp = Files.getLastModifiedTime(bytecode);
        Files.write(bytecode, type("Standalone", "other"));
        Files.setLastModifiedTime(bytecode, timestamp);
        require(!firstFingerprint.equals(cache.load(directory, null, null, warnings).metadata.fingerprint),
                "Directory identity trusted mtime instead of content");
        Files.write(directory.resolve("Extra.class"), type("Extra", "field"));
        require(cache.load(directory, null, null, warnings).metadata.classes.size() == 2,
                "Directory additions were missed");
        Files.delete(directory.resolve("Extra.class"));
        require(cache.load(directory, null, null, warnings).metadata.classes.size() == 1,
                "Directory removals were missed");
        verifyLoaders(work, jar, duplicate);

        List<NativeOriginInput> origins = Arrays.asList(
                new NativeOriginInput("game", jar, Collections.singletonList("loader-game"),
                        Collections.singletonList("mod_Test"), false, "loader_url"),
                new NativeOriginInput("duplicate", duplicate, Collections.emptyList(), Collections.emptyList(), false,
                        "mods_candidate"),
                new NativeOriginInput("broken", broken, Collections.emptyList(), Collections.emptyList(), false,
                        "mods_candidate"));
        NativeLoaderRegistry.Capture capture = new NativeLoaderRegistry.Capture(origins,
                Collections.singletonList(CatalogValues.record("id", "loader-game", "name", "fixture.Loader")),
                Collections.singletonList(CatalogValues.record("name", "fixture.Known", "originId", "game",
                        "loaderId", "loader-game")), Collections.emptyList(), false);
        List<NativeSourceAttachment> attachments = Collections.singletonList(new NativeSourceAttachment("fixture-src",
                sources, CatalogFingerprint.sha256(jar)));
        try (NativeDiscoveryService service = new NativeDiscoveryService(work.resolve("service-cache"))) {
            NativeDiscoveryService.Ticket cancelled = service.request(capture, "named", attachments);
            NativeDiscoveryService.Ticket retained = service.request(capture, "named", attachments);
            cancelled.close();
            require(service.snapshot().generation == 0, "Worker result was visible before client publication");
            await(service, retained);
            NativeIndexSnapshot snapshot = service.snapshot();
            require(snapshot.rows("classes").size() == 4, "An origin failure discarded successful metadata");
            require(Boolean.FALSE.equals(snapshot.coverage.get("knownOriginsComplete")), "Partial index claimed complete");
            require("cancelled".equals(cancelled.status().get("status")), "Cancelled lease was reactivated");
            require("complete".equals(retained.status().get("status")), "One cancellation stopped another lease");
            verifyQueries(snapshot);
            Map<String, Object> known = NativeQueryService.describe(snapshot, "fixture.Known", "game");
            require(Boolean.TRUE.equals(known.get("loaded")), "Observed class identity was lost");
            require(NativeQueryService.describe(snapshot, "mod_Test", null).get("loaded") == null,
                    "Missing agent census became a false unloaded assertion");
            require(Boolean.TRUE.equals(known.get("sourceAvailable")), "Source attachment not associated");
            Map<?, ?> source = (Map<?, ?>) ((List<?>) known.get("sourceMatches")).get(0);
            Map<String, Object> page = snapshot.sources.read((String) source.get("id"), 2, 1);
            require(page.get("lines").equals(Collections.singletonList("class Known {"))
                    && Integer.valueOf(3).equals(page.get("nextLine")), "Source paging is incorrect");
            fails(() -> snapshot.sources.read("../../arbitrary", 1, 1), IOException.class);
            fails(() -> snapshot.sources.read((String) source.get("id"), 1, 201), IllegalArgumentException.class);
            NativeDiscoveryApiTest.run(service, capture, attachments, (String) source.get("id"));
            zip(sources, Collections.singletonMap("fixture/Known.java", "changed".getBytes(StandardCharsets.UTF_8)));
            fails(() -> snapshot.sources.read((String) source.get("id"), 1, 1), IOException.class);

            Map<String, Object> filter = CatalogValues.record("limit", 1);
            String cursor = (String) NativeQueryService.query(snapshot, filter).get("nextCursor");
            NativeDiscoveryService.Ticket refresh = service.request(capture, "named", Collections.emptyList());
            await(service, refresh);
            filter.put("cursor", cursor);
            fails(() -> NativeQueryService.query(service.snapshot(), filter), IllegalArgumentException.class);
            require(snapshot.generation != service.snapshot().generation, "Refresh did not advance generation");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread wrong = new Thread(() -> {
                try {
                    service.publishReady();
                } catch (Throwable exception) {
                    failure.set(exception);
                }
            });
            wrong.start();
            wrong.join();
            require(failure.get() instanceof IllegalStateException, "Off-thread publication was accepted");
        }
        require(System.getProperty("betamoon.native.fixture.initialized") == null, "Metadata API initialized a target");
    }

    private static void verifyLoaders(Path work, Path first, Path second) throws Exception {
        Path mods = Files.createDirectory(work.resolve("mods"));
        Files.copy(second, mods.resolve("disabled.jar.disabled"));
        try (URLClassLoader one = new URLClassLoader(new URL[] {first.toUri().toURL()}, null);
                URLClassLoader two = new URLClassLoader(new URL[] {second.toUri().toURL()}, null);
                NativeLoaderRegistry registry = new NativeLoaderRegistry()) {
            Class<?> a = Class.forName("fixture.Known", false, one);
            Class<?> b = Class.forName("fixture.Known", false, two);
            require(!registry.id(one).equals(registry.id(two)), "Independent defining loaders share an identity");
            NativeLoaderRegistry.Capture capture = registry.capture(Collections.singletonList(a),
                    Collections.singletonList(b), Arrays.asList(a, b), true, mods, false);
            Path realFirst = first.toRealPath();
            Path realSecond = second.toRealPath();
            require(capture.origins.stream().anyMatch(origin -> origin.path.equals(realFirst) && origin.gameOrigin),
                    "Game code-source identity was not captured");
            require(capture.origins.stream().anyMatch(origin -> origin.path.equals(realSecond)
                    && origin.modEntries.contains("fixture.Known")), "Mod entry provenance was lost");
            require(capture.origins.stream().anyMatch(origin -> origin.path.getFileName().toString()
                    .equals("disabled.jar.disabled") && origin.loaderIds.isEmpty()),
                    "Disabled archive candidate was falsely attributed to a loader");
        }
    }

    private static void verifySourceArchiveBudget(Path work) throws Exception {
        Path archive = work.resolve("source-budget.jar");
        byte[] content = new byte[2 * 1024 * 1024];
        Arrays.fill(content, (byte) 'x');
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 33; i++) {
            entries.put("sources/File" + i + ".java", content);
        }
        zip(archive, entries);
        try {
            new NativeOriginCache(work.resolve("bounded-cache")).load(archive, null, null, new ArrayList<>());
            throw new AssertionError("Compressed source data exceeded the aggregate extraction budget");
        } catch (IOException expected) {
            require("origin_source_byte_limit".equals(expected.getMessage()),
                    "Wrong source-budget failure: " + expected);
        }
    }

    private static void verifyQueries(NativeIndexSnapshot snapshot) throws Exception {
        Map<String, Object> first = NativeQueryService.query(snapshot, CatalogValues.record("glob", "fixture.?nown"));
        require(((List<?>) first.get("items")).size() == 2, "Glob query lost duplicate origins");
        fails(() -> NativeQueryService.describe(snapshot, "fixture.Known", null), IllegalArgumentException.class);
        require(((List<?>) NativeQueryService.query(snapshot, CatalogValues.record("kind", "members", "access", "private"))
                .get("items")).size() == 4, "Private metadata was filtered from discovery");
        require(((List<?>) NativeQueryService.query(snapshot, CatalogValues.record("kind", "files", "name", "nested/library.jar"))
                .get("items")).size() == 1, "Nested archive entry missing from file inventory");
        fails(() -> NativeQueryService.query(snapshot, CatalogValues.record("unknown", true)), IllegalArgumentException.class);
        fails(() -> NativeQueryService.query(snapshot, CatalogValues.record("limit", 0)), IllegalArgumentException.class);
        Map<String, Object> filter = CatalogValues.record("limit", 1);
        Map<String, Object> page = NativeQueryService.query(snapshot, filter);
        filter.put("cursor", page.get("nextCursor"));
        Map<String, Object> next = NativeQueryService.query(snapshot, filter);
        require(!next.get("items").equals(page.get("items")), "Cursor repeated the same row");
        filter.put("prefix", "different");
        fails(() -> NativeQueryService.query(snapshot, filter), IllegalArgumentException.class);
        fails(() -> snapshot.rows("classes").get(0).put("name", "tampered"), UnsupportedOperationException.class);
    }

    public static void await(NativeDiscoveryService service, NativeDiscoveryService.Ticket ticket) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while ("pending".equals(ticket.status().get("status"))) {
            service.publishReady();
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Native scan did not complete");
            }
            Thread.sleep(5);
        }
        require("complete".equals(ticket.status().get("status")), "Scan failed: " + ticket.status());
    }

    private static byte[] type(String name, String field) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitSource("Known.java", null);
        writer.visitField(Opcodes.ACC_PRIVATE, field, "Lmissing/Dependency;", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        method.visitLdcInsn("betamoon.native.fixture.initialized");
        method.visitLdcInsn("yes");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void zip(Path path, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static void fails(Action action, Class<? extends Exception> expected) throws Exception {
        try {
            action.run();
        } catch (Exception exception) {
            if (expected.isInstance(exception)) {
                return;
            }
            throw exception;
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface Action {
        void run() throws Exception;
    }
}
