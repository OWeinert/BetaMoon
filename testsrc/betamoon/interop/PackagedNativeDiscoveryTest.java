package betamoon.interop;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Exercises the shipped discovery code with obfuscated game classes and no named game parent loader. */
public final class PackagedNativeDiscoveryTest {
    private PackagedNativeDiscoveryTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(arguments.length >= 3, "Expected packaged mod, reobfuscated game and original game JARs");
        URL[] urls = new URL[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            require(new File(arguments[i]).isFile(), "Missing packaged dependency: " + arguments[i]);
            urls[i] = new File(arguments[i]).toURI().toURL();
        }
        Path parent = Paths.get("build").toAbsolutePath().normalize().toRealPath();
        Path work = Files.createTempDirectory(parent, "native-packaged-");
        try (URLClassLoader runtime = new URLClassLoader(urls, null)) {
            verify(runtime, work);
        } finally {
            Path target = work.toRealPath();
            require(target.getParent().equals(parent) && target.getFileName().toString().startsWith("native-packaged-"),
                    "Unsafe packaged fixture cleanup target");
            try (Stream<Path> paths = Files.walk(target)) {
                for (Path path : (Iterable<Path>) paths.sorted(Collections.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("Packaged discovery passed: isolated obfuscated namespace, origins and mapped queries.");
    }

    private static void verify(URLClassLoader runtime, Path work) throws Exception {
        TinyMappingResolver mappings;
        try (InputStream input = runtime.getResourceAsStream("betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            require(input != null, "Packaged mappings are missing");
            mappings = TinyMappingResolver.readStrict(input);
        }
        List<Class<?>> roots = new ArrayList<>();
        Map<String, String> sentinels = new LinkedHashMap<>();
        for (String name : new String[] {"net/minecraft/src/World", "net/minecraft/src/EntityPlayer",
                "net/minecraft/client/Minecraft"}) {
            String binary = mappings.resolveClass(new ClassRef(name), RuntimeNamespace.CLIENT).replace('/', '.');
            Class<?> type = Class.forName(binary, false, runtime);
            roots.add(type);
            sentinels.put(name, type.getName());
        }
        try {
            runtime.loadClass("net.minecraft.src.World");
            throw new AssertionError("Packaged fixture inherited a named World");
        } catch (ClassNotFoundException expected) {
        }
        Class<?> mappingType = runtime.loadClass("betamoon.instrumentation.mapping.TinyMappingResolver");
        Object childMappings;
        try (InputStream input = runtime.getResourceAsStream("betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            childMappings = mappingType.getMethod("readStrict", InputStream.class).invoke(null, input);
        }
        String namespace = (String) runtime.loadClass("betamoon.interop.mapping.NativeNamespaceDetector")
                .getMethod("detect", Map.class, mappingType).invoke(null, sentinels, childMappings);
        require(namespace.equals("client"), "Production identities selected the wrong namespace: " + namespace);

        Class<?> registryType = runtime.loadClass("betamoon.interop.discovery.NativeLoaderRegistry");
        Object registry = registryType.getConstructor().newInstance();
        Object captured = registryType.getMethod("capture", List.class, List.class, List.class,
                Boolean.TYPE, Path.class, Boolean.TYPE).invoke(registry, roots, Collections.emptyList(), roots,
                        false, null, false);
        Class<?> captureType = captured.getClass();
        List<Object> origins = new ArrayList<>();
        for (Object origin : (List<?>) captureType.getField("origins").get(captured)) {
            if (origin.getClass().getField("gameOrigin").getBoolean(origin)) {
                origins.add(origin);
            }
        }
        require(origins.size() == 1, "World code source was not identified uniquely");
        // Other launcher/dependency candidates are covered by isolated fixtures; keep this acceptance game-specific.
        Object capture = captureType.getConstructor(List.class, List.class, List.class, List.class, Boolean.TYPE)
                .newInstance(origins, captureType.getField("loaders").get(captured),
                        captureType.getField("loaded").get(captured), Collections.emptyList(), false);
        Class<?> serviceType = runtime.loadClass("betamoon.interop.discovery.NativeDiscoveryService");
        Object service = serviceType.getConstructor(Path.class).newInstance(work.resolve("cache"));
        try {
            Object ticket = serviceType.getMethod("request", captureType, String.class, List.class)
                    .invoke(service, capture, namespace, Collections.emptyList());
            Method status = ticket.getClass().getMethod("status");
            long deadline = System.nanoTime() + 30000000000L;
            while (((Map<?, ?>) status.invoke(ticket)).get("status").equals("pending")) {
                serviceType.getMethod("publishReady").invoke(service);
                require(System.nanoTime() < deadline, "Packaged discovery timed out");
                Thread.sleep(5);
            }
            require(((Map<?, ?>) status.invoke(ticket)).get("status").equals("complete"),
                    "Packaged scan failed: " + status.invoke(ticket));
            Object snapshot = serviceType.getMethod("snapshot").invoke(service);
            Class<?> queryType = runtime.loadClass("betamoon.interop.discovery.NativeQueryService");
            Method queryMethod = queryType.getMethod("query", snapshot.getClass(), Map.class);
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("name", "net.minecraft.src.World");
            Map<?, ?> page = (Map<?, ?>) queryMethod.invoke(null, snapshot, filter);
            List<?> items = (List<?>) page.get("items");
            require(items.size() == 1, "Readable World alias was not indexed: " + page);
            Map<?, ?> world = (Map<?, ?>) items.get(0);
            require(world.get("runtimeName").equals(roots.get(0).getName()), "Alias refers to the wrong runtime name");
            require(Boolean.TRUE.equals(world.get("loaded")), "Observed World was not marked loaded");
            require(Boolean.FALSE.equals(world.get("bound")), "Metadata discovery bound a native type");
            filter.put("kind", "members");
            filter.put("name", "getBlockId");
            filter.put("declaringType", "net.minecraft.src.World");
            filter.put("descriptor", "(III)I");
            page = (Map<?, ?>) queryMethod.invoke(null, snapshot, filter);
            require(((List<?>) page.get("items")).size() == 1, "Mapped World.getBlockId was not discoverable");
            require(((Number) ((Map<?, ?>) page.get("coverage")).get("failedOrigins")).intValue() == 0,
                    "Packaged game origin failed metadata extraction");
        } finally {
            serviceType.getMethod("close").invoke(service);
            registryType.getMethod("close").invoke(registry);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
