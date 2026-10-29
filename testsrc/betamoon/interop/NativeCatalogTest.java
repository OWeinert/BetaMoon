package betamoon.interop;

import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.mapping.TinyMappingSnapshot;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.ClassArchiveCatalog;
import betamoon.interop.catalog.ClassArchiveReader;
import betamoon.interop.catalog.JvmDescriptors;
import betamoon.interop.catalog.NativeClassInfo;
import betamoon.interop.catalog.NativeMemberInfo;
import betamoon.interop.mapping.MappingResolution;
import betamoon.interop.mapping.NativeMappingCatalog;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Runs without Minecraft on its classpath, including a class with an observable static initializer. */
public final class NativeCatalogTest {
    private static final String INITIALIZED_PROPERTY = "betamoon.native.catalog.fixture.initialized";

    private NativeCatalogTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Expected mappings, named client, runtime client, named server, runtime server");
        }
        verifyDescriptorsAndJson();
        verifyStrictMappingInput();
        fails(() -> Class.forName("net.minecraft.src.World"), ClassNotFoundException.class);
        Path work = Files.createTempDirectory("betamoon-native-catalog-");
        try {
            verifyFixtureCatalogs(work);
            verifySourceManifest(work);
            verifyRealMappings(args);
        } finally {
            Path resolvedWork = work.toRealPath();
            Path temporaryRoot = Paths.get(System.getProperty("java.io.tmpdir")).toRealPath();
            require(resolvedWork.getParent().equals(temporaryRoot)
                    && resolvedWork.getFileName().toString().startsWith("betamoon-native-catalog-"),
                    "Refusing fixture cleanup outside the test's temporary directory");
            try (Stream<Path> paths = Files.walk(resolvedWork)) {
                for (Path path : (Iterable<Path>) paths.sorted(Collections.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("Native catalog tests passed: isolated metadata, mappings, sources, determinism.");
    }

    private static void verifyDescriptorsAndJson() throws Exception {
        JvmDescriptors.requireField("[[Ljava/lang/String;");
        JvmDescriptors.requireMethod("(I[DLfixture/World;)V");
        require("([La;)La;".equals(JvmDescriptors.remap("([Lfixture/World;)Lfixture/World;", name -> "a")),
                "Descriptor remapping lost an array/return component");
        for (String malformed : Arrays.asList("V", "[V", "L;", "Iextra", "Ljava.lang.String;")) {
            fails(() -> JvmDescriptors.requireField(malformed), IllegalArgumentException.class);
        }
        for (String malformed : Arrays.asList("I", "(V)V", "()VV", "(I", "(I)", "()[V")) {
            fails(() -> JvmDescriptors.requireMethod(malformed), IllegalArgumentException.class);
        }
        String oversizedParameters = "(" + String.join("", Collections.nCopies(128, "J")) + ")V";
        fails(() -> JvmDescriptors.requireMethod(oversizedParameters), IllegalArgumentException.class);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("z", Arrays.asList("\"\\\n\t\u0000\ud800", null, true));
        values.put("a", Long.MAX_VALUE);
        String encoded = CatalogJson.encode(values);
        Map<?, ?> decoded = (Map<?, ?>) CatalogJson.decode(encoded);
        require(Long.valueOf(Long.MAX_VALUE).equals(decoded.get("a")), "JSON lost exact long data");
        require(encoded.equals(CatalogJson.encode(decoded)), "JSON records do not round-trip canonically");
        require(encoded.startsWith("{\"a\":"), "JSON object order must be deterministic");
        for (String malformed : Arrays.asList("{\"a\":null,\"a\":2}", "[1,]", "01", "+1", "1e9999",
                "true false", "\"\\q\"", "\"\\uＦ000\"")) {
            fails(() -> CatalogJson.decode(malformed), IllegalArgumentException.class);
        }
        fails(() -> CatalogJson.encode(Double.NaN), IllegalArgumentException.class);
        String nested = String.join("", Collections.nCopies(34, "[")) + "0"
                + String.join("", Collections.nCopies(34, "]"));
        fails(() -> CatalogJson.decode(nested), IllegalArgumentException.class);
    }

    private static void verifyStrictMappingInput() throws Exception {
        String header = "tiny\t2\t0\tnamed\tclient\tserver\n";
        String duplicate = header + "c\tfixture/World\ta\ta\nc\tfixture/World\tb\tb\n";
        fails(() -> strict(duplicate), IOException.class);
        // Legacy instrumentation parsing still accepts its historical last-entry behavior.
        TinyMappingResolver.read(bytes(duplicate));
        fails(() -> strict(header + "c\tfixture/World\ta\ta\n\tf\tI\tx\ta\ta\n\tf\tI\tx\tb\tb\n"),
                IOException.class);
        fails(() -> strict(header + "\tm\t()V\ttest\ta\ta\n"), IOException.class);
        fails(() -> strict("tiny\t2\t0\tclient\tnamed\n"), IOException.class);
        TinyMappingSnapshot snapshot = strict(header + "c\tfixture/World\ta\t\n").snapshot();
        require("".equals(snapshot.getClasses().get("fixture/World").getNames().get("server")),
                "An empty target cell was converted to a fallback name");
        require(!snapshot.getClasses().containsKey("unknown/Type"), "An absent row must remain absent");
        fails(() -> snapshot.getClasses().clear(), UnsupportedOperationException.class);
        fails(() -> snapshot.getClasses().get("fixture/World").getNames().put("server", "x"),
                UnsupportedOperationException.class);
    }

    private static void verifyFixtureCatalogs(Path work) throws Exception {
        Path namedJar = work.resolve("named.jar");
        Path runtimeJar = work.resolve("runtime.jar");
        Map<String, byte[]> named = fixtureClasses(false);
        Map<String, byte[]> runtime = fixtureClasses(true);
        zip(namedJar, named);
        zip(runtimeJar, runtime);
        System.clearProperty(INITIALIZED_PROPERTY);
        ClassArchiveCatalog namedCatalog = new ClassArchiveReader().read(namedJar);
        ClassArchiveCatalog runtimeCatalog = new ClassArchiveReader().read(runtimeJar);
        require(System.getProperty(INITIALIZED_PROPERTY) == null, "Metadata scanning initialized a fixture class");
        NativeClassInfo type = namedCatalog.getClasses().get("fixture/World");
        require(type.findMember(false, "secret", "I").getAccess() == Opcodes.ACC_PRIVATE,
                "Private field metadata was dropped");
        require(type.findMember(true, "hidden", "()I").getAccess() == Opcodes.ACC_PROTECTED,
                "Protected method metadata was dropped");
        require(type.getSourceFile().equals("World.java"), "SourceFile metadata was lost");
        require(!type.findMember(true, "<clinit>", "()V").isPublicCandidate(), "Class initialization is not callable");
        NativeClassInfo inner = namedCatalog.getClasses().get("fixture/World$Nested");
        require((inner.getAccess() & Opcodes.ACC_PRIVATE) != 0 && "fixture/World".equals(inner.getOuterName()),
                "Nested visibility/ownership metadata was lost");
        fails(() -> type.getMembers().clear(), UnsupportedOperationException.class);
        fails(() -> type.getInterfaces().clear(), UnsupportedOperationException.class);

        String mappingText = fixtureMappings();
        TinyMappingSnapshot mappings = strict(mappingText).snapshot();
        Map<RuntimeNamespace, ClassArchiveCatalog> targets = new EnumMap<>(RuntimeNamespace.class);
        targets.put(RuntimeNamespace.CLIENT, runtimeCatalog);
        targets.put(RuntimeNamespace.SERVER, runtimeCatalog);
        NativeMappingCatalog resolver = new NativeMappingCatalog(mappings, namedCatalog, targets);
        expect(resolver.resolveClass("fixture/World", RuntimeNamespace.CLIENT),
                MappingResolution.State.VERIFIED_MAPPED);
        expect(resolver.resolveClass("fixture/Side", RuntimeNamespace.CLIENT), MappingResolution.State.SIDE_ABSENT);
        expect(resolver.resolveClass("unknown/Class", RuntimeNamespace.CLIENT), MappingResolution.State.NOT_CATALOGUED);
        MappingResolution inherited = resolver.resolveMember("fixture/World", "getBlock", "(III)I", true,
                RuntimeNamespace.CLIENT);
        expect(inherited, MappingResolution.State.VERIFIED_MAPPED);
        require("fixture/IWorld".equals(inherited.getMappingOwner()) && "b".equals(inherited.getName()),
                "Implementation method must use its interface mapping without changing the declaring owner");
        expect(resolver.resolveMember("fixture/World", "stable", "()I", true, RuntimeNamespace.CLIENT),
                MappingResolution.State.VERIFIED_UNCHANGED);
        expect(resolver.resolveMember("fixture/World", "shadow", "()I", true, RuntimeNamespace.CLIENT),
                MappingResolution.State.VERIFIED_UNCHANGED);
        expect(resolver.resolveMember("fixture/World", "unmapped", "()I", true, RuntimeNamespace.CLIENT),
                MappingResolution.State.MAPPING_MISSING);
        expect(resolver.resolveMember("fixture/World", "missingCell", "I", false, RuntimeNamespace.CLIENT),
                MappingResolution.State.SIDE_ABSENT);
        expect(resolver.resolveMember("fixture/World", "badMapping", "()I", true, RuntimeNamespace.CLIENT),
                MappingResolution.State.TARGET_MEMBER_MISSING);
        expect(resolver.resolveMember("fixture/World", "useSide", "(Lfixture/Side;)V", true, RuntimeNamespace.CLIENT),
                MappingResolution.State.DESCRIPTOR_UNAVAILABLE);
        MappingResolution mod = resolver.resolveMember("net/minecraft/src/UnobfuscatedMod", "inspect",
                "([Lfixture/World;Lmissing/Dependency;)Lfixture/World;", true, RuntimeNamespace.CLIENT);
        expect(mod, MappingResolution.State.VERIFIED_UNCHANGED);
        require("([La;Lmissing/Dependency;)La;".equals(mod.getDescriptor()),
                "Unobfuscated mod descriptors still need mapped Minecraft components");
        fails(() -> new ClassArchiveReader(1, 1024 * 1024, 1024 * 1024).read(namedJar), IOException.class);
        fails(() -> new ClassArchiveReader(100, 32, 1024).read(namedJar), IOException.class);
        Path badJar = work.resolve("bad.jar");
        zip(badJar, Collections.singletonMap("../escaped.class", named.values().iterator().next()));
        fails(() -> new ClassArchiveReader().read(badJar), IOException.class);
        zip(badJar, Collections.singletonMap("wrong/Name.class", named.values().iterator().next()));
        fails(() -> new ClassArchiveReader().read(badJar), IOException.class);
        fails(() -> new NativeMappingCatalog(strict(mappingText + "c\tfixture/Other\ta\tother\n").snapshot(),
                namedCatalog, targets), IllegalArgumentException.class);
        fails(() -> new NativeMappingCatalog(strict(mappingText.replace("\tm\t()I\thidden\th\th\n",
                "\tm\t()I\thidden\th\th\n\tm\t()I\tanother\th\th\n")).snapshot(), namedCatalog, targets),
                IllegalArgumentException.class);

        Path mappingFile = work.resolve("mappings.tiny");
        Files.write(mappingFile, mappingText.getBytes(StandardCharsets.UTF_8));
        Path sourceRoot = Files.createDirectory(work.resolve("generator-source"));
        Files.write(sourceRoot.resolve("World.java"),
                "package fixture; public class World {}".getBytes(StandardCharsets.UTF_8));
        Path first = work.resolve("first");
        Path second = work.resolve("second");
        Path reports = work.resolve("reports");
        generate(mappingFile, namedJar, runtimeJar, sourceRoot, first, reports);
        generate(mappingFile, namedJar, runtimeJar, sourceRoot, second, reports);
        try (Stream<Path> paths = Files.list(first)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                require(Arrays.equals(Files.readAllBytes(file), Files.readAllBytes(second.resolve(file.getFileName()))),
                        "Identical inputs produced different resource bytes: " + file.getFileName());
            }
        }
        Map<?, ?> diff = (Map<?, ?>) CatalogJson.decode(read(reports.resolve("diff.json")));
        require(((List<?>) diff.get("added")).isEmpty() && ((List<?>) diff.get("changed")).isEmpty()
                && ((List<?>) diff.get("removed")).isEmpty(), "Second generation reported spurious semantic changes");
        Map<String, Object> changed = CatalogDiffReport.compare(Collections.singletonMap("old", "1"),
                Collections.singletonMap("new", "2"));
        require(changed.get("added").equals(Collections.singletonList("new"))
                && changed.get("removed").equals(Collections.singletonList("old")),
                "Catalog diff lost additions/removals");
        require(System.getProperty(INITIALIZED_PROPERTY) == null, "Generation initialized a fixture class");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {runtimeJar.toUri().toURL()}, null)) {
            Class.forName("a", true, loader);
            require("initialized".equals(System.getProperty(INITIALIZED_PROPERTY)),
                    "Static-initializer fixture does not exercise a real observable effect");
        } finally {
            System.clearProperty(INITIALIZED_PROPERTY);
        }
    }

    private static void verifySourceManifest(Path work) throws Exception {
        Path root = Files.createDirectory(work.resolve("source"));
        require(SourceManifestGenerator.scan("empty", root).records().isEmpty(), "An empty source root must be valid");
        Files.write(root.resolve("Types.java"), ("package fixture; class Outer { class Nested {} "
                + "UnknownDependency value; void test() { String marker = \"SECRET_SOURCE_BODY\"; } } class Other {}")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("Broken.java"),
                "package fixture; class Broken { void }".getBytes(StandardCharsets.UTF_8));
        SourceManifestGenerator manifest = SourceManifestGenerator.scan("fixture", root);
        List<Map<String, Object>> records = manifest.records();
        require(records.size() == 2, "A parse error must not remove a file from source search");
        require(records.get(0).get("parseStatus").equals("parse_error"), "Malformed source must be labeled");
        require(records.get(1).get("declaredTypes").equals(Arrays.asList("fixture/Other", "fixture/Outer",
                "fixture/Outer$Nested")), "Top-level/nested source types were not indexed");
        String json = CatalogJson.encode(records);
        require(!json.contains("SECRET_SOURCE_BODY") && !json.contains(root.toString()),
                "A source body or absolute developer path leaked into distributable metadata");
    }

    private static void verifyRealMappings(String[] args) throws Exception {
        TinyMappingSnapshot mappings;
        try (InputStream input = Files.newInputStream(Paths.get(args[0]))) {
            mappings = TinyMappingResolver.readStrict(input).snapshot();
        }
        ClassArchiveCatalog named = new ClassArchiveReader().read(Paths.get(args[1]));
        ClassArchiveCatalog namedServer = new ClassArchiveReader().read(Paths.get(args[3]));
        Map<RuntimeNamespace, ClassArchiveCatalog> targets = new EnumMap<>(RuntimeNamespace.class);
        targets.put(RuntimeNamespace.CLIENT, new ClassArchiveReader().read(Paths.get(args[2])));
        targets.put(RuntimeNamespace.SERVER, new ClassArchiveReader().read(Paths.get(args[4])));
        NativeMappingCatalog resolver = new NativeMappingCatalog(mappings, named, namedServer, targets);
        for (RuntimeNamespace namespace : Arrays.asList(RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT,
                RuntimeNamespace.SERVER)) {
            MappingResolution block = resolver.resolveMember("net/minecraft/src/World", "getBlockId", "(III)I",
                    true, namespace);
            require(block.isVerified(), "Actual world block query did not map in " + namespace + ": "
                    + block.getState());
            if (namespace != RuntimeNamespace.NAMED) {
                require("net/minecraft/src/IBlockAccess".equals(block.getMappingOwner()),
                        "Interface mapping owner lost");
            }
        }
        expect(resolver.resolveClass("net/minecraft/src/GuiMainMenu", RuntimeNamespace.SERVER),
                MappingResolution.State.SIDE_ABSENT);
        boolean verifiedMod = false;
        for (NativeClassInfo type : named.getClasses().values()) {
            if (!type.getInternalName().startsWith("forge/")) {
                continue;
            }
            for (NativeMemberInfo member : type.getMembers().values()) {
                if (member.getKind() == NativeMemberInfo.Kind.METHOD
                        && member.getDescriptor().contains("Lnet/minecraft/src/World;")) {
                    MappingResolution result = resolver.resolveMember(type.getInternalName(), member.getName(),
                            member.getDescriptor(), true, RuntimeNamespace.CLIENT);
                    verifiedMod |= result.isVerified();
                }
            }
        }
        require(verifiedMod, "No real Forge method with a mapped World parameter verified");
    }

    private static void generate(Path mappings, Path named, Path runtime, Path source, Path output, Path reports)
            throws Exception {
        NativeCatalogGenerator.main(new String[] {"fixture", mappings.toString(), named.toString(), runtime.toString(),
                runtime.toString(), source.toString(), output.toString(), reports.toString(), named.toString()});
    }

    private static Map<String, byte[]> fixtureClasses(boolean runtime) {
        String world = runtime ? "a" : "fixture/World";
        String iface = runtime ? "i" : "fixture/IWorld";
        String side = runtime ? "o" : "fixture/Side";
        Map<String, byte[]> classes = new TreeMap<>();
        ClassWriter writer = begin(world, Opcodes.ACC_PUBLIC, new String[] {iface});
        writer.visitField(Opcodes.ACC_PRIVATE, runtime ? "x" : "secret", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "missingCell", "I", null, null).visitEnd();
        method(writer, runtime ? "b" : "getBlock", "(III)I", Opcodes.ACC_PUBLIC);
        method(writer, "stable", "()I", Opcodes.ACC_PUBLIC);
        method(writer, "shadow", "()I", Opcodes.ACC_PRIVATE);
        method(writer, runtime ? "h" : "hidden", "()I", Opcodes.ACC_PROTECTED);
        method(writer, runtime ? "q" : "unmapped", "()I", Opcodes.ACC_PUBLIC);
        method(writer, "badMapping", "()I", Opcodes.ACC_PUBLIC);
        method(writer, "useSide", "(L" + side + ";)V", Opcodes.ACC_PUBLIC);
        MethodVisitor initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn(INITIALIZED_PROPERTY);
        initializer.visitLdcInsn("initialized");
        initializer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();
        finish(classes, world, writer);
        writer = begin(iface, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, new String[0]);
        method(writer, runtime ? "b" : "getBlock", "(III)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
        method(writer, runtime ? "s" : "shadow", "()I", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
        finish(classes, iface, writer);
        finish(classes, side, begin(side, Opcodes.ACC_PUBLIC, new String[0]));
        String nested = world + "$Nested";
        writer = begin(nested, Opcodes.ACC_SUPER, new String[0]);
        writer.visitInnerClass(nested, world, "Nested", Opcodes.ACC_PRIVATE);
        finish(classes, nested, writer);
        String mod = "net/minecraft/src/UnobfuscatedMod";
        writer = begin(mod, Opcodes.ACC_PUBLIC, new String[0]);
        method(writer, "inspect", "([L" + world + ";Lmissing/Dependency;)L" + world + ";", Opcodes.ACC_PUBLIC);
        finish(classes, mod, writer);
        return classes;
    }

    private static ClassWriter begin(String name, int access, String[] interfaces) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, access, name, null, "java/lang/Object", interfaces);
        writer.visitSource("World.java", null);
        return writer;
    }

    private static void method(ClassWriter writer, String name, String descriptor, int access) {
        MethodVisitor method = writer.visitMethod(access, name, descriptor, null, null);
        if ((access & Opcodes.ACC_ABSTRACT) == 0) {
            method.visitCode();
            Type result = Type.getReturnType(descriptor);
            if (result.getSort() == Type.VOID) {
                method.visitInsn(Opcodes.RETURN);
            } else if (result.getSort() == Type.OBJECT || result.getSort() == Type.ARRAY) {
                method.visitInsn(Opcodes.ACONST_NULL);
                method.visitInsn(Opcodes.ARETURN);
            } else {
                method.visitInsn(Opcodes.ICONST_0);
                method.visitInsn(Opcodes.IRETURN);
            }
            int locals = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
            for (Type parameter : Type.getArgumentTypes(descriptor)) {
                locals += parameter.getSize();
            }
            method.visitMaxs(1, locals);
        }
        method.visitEnd();
    }

    private static void finish(Map<String, byte[]> classes, String name, ClassWriter writer) {
        writer.visitEnd();
        classes.put(name + ".class", writer.toByteArray());
    }

    private static String fixtureMappings() {
        return "tiny\t2\t0\tnamed\tclient\tserver\n"
                + "c\tfixture/World\ta\ta\n"
                + "\tf\tI\tsecret\tx\tx\n"
                + "\tf\tI\tmissingCell\t\t\n"
                + "\tm\t()I\thidden\th\th\n"
                + "\tm\t()I\tbadMapping\tmissing\tmissing\n"
                + "c\tfixture/IWorld\ti\ti\n"
                + "\tm\t(III)I\tgetBlock\tb\tb\n"
                + "\tm\t()I\tshadow\ts\ts\n"
                + "c\tfixture/Side\t\to\n"
                + "c\tfixture/World$Nested\ta$Nested\ta$Nested\n";
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

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static TinyMappingResolver strict(String text) throws IOException {
        return TinyMappingResolver.readStrict(bytes(text));
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void expect(MappingResolution resolution, MappingResolution.State state) {
        require(resolution.getState() == state, "Expected " + state + ", got " + resolution.getState());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void fails(CheckedAction action, Class<? extends Exception> expected) throws Exception {
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

    private interface CheckedAction {
        void run() throws Exception;
    }
}
