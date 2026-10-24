package betamoon.entity.modifier;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Reuses the native scenarios in a separate JVM containing only the packaged
 * obfuscated runtime.
 */
public final class PackagedEntityMotionTest {
    private PackagedEntityMotionTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 3) {
            throw new IllegalArgumentException(
                    "Expected packaged mod, obfuscated client, resource client and libraries");
        }
        TinyMappingResolver mappings;
        try (JarFile jar = new JarFile(arguments[0]);
                InputStream input = jar
                        .getInputStream(jar.getJarEntry("betamoon/instrumentation/mapping/b1.7.3.tiny"))) {
            mappings = TinyMappingResolver.read(input);
        }
        Path classes = Paths.get(EntityMotionApiTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path fixtureDirectory = classes.resolve("betamoon/entity/modifier");
        Path fixtures = Paths.get("build/entity-motion-packaged-fixtures.jar").toAbsolutePath();
        Remapper remapper = new NativeRemapper(mappings);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(fixtures));
                Stream<Path> files = Files.list(fixtureDirectory)) {
            for (Path path : (Iterable<Path>) files.filter(PackagedEntityMotionTest::fixture)::iterator) {
                ClassWriter writer = new ClassWriter(0);
                new ClassReader(Files.readAllBytes(path)).accept(new ClassRemapper(writer, remapper), 0);
                output.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
                output.write(writer.toByteArray());
                output.closeEntry();
            }
            try (JarFile resources = new JarFile(arguments[2])) {
                for (JarEntry entry : Collections.list(resources.entries())) {
                    if (entry.isDirectory() || entry.getName().endsWith(".class")
                            || entry.getName().startsWith("META-INF/")) {
                        continue;
                    }
                    output.putNextEntry(new JarEntry(entry.getName()));
                    try (InputStream input = resources.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                    }
                    output.closeEntry();
                }
            }
        }
        List<String> command = new ArrayList<>();
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        command.add(Paths.get(System.getProperty("java.home"), "bin", executable).toString());
        command.add("-javaagent:" + arguments[0]);
        command.add("-Dbetamoon.runtime.side=client");
        command.add("-Dbetamoon.motion.requireObfuscated=true");
        command.add("-cp");
        List<String> runtime = new ArrayList<>();
        runtime.add(fixtures.toString());
        for (int i = 0; i < arguments.length; i++) {
            if (i != 2) {
                runtime.add(arguments[i]);
            }
        }
        command.add(String.join(File.pathSeparator, runtime));
        command.add("betamoon.entity.modifier.EntityMotionApiTest");
        int result = new ProcessBuilder(command).inheritIO().start().waitFor();
        if (result != 0) {
            throw new AssertionError("Packaged native movement scenarios failed: " + result);
        }
        System.out
                .println("Packaged movement passed: isolated obfuscated client, shipped agent and native Lua flight.");
    }

    private static boolean fixture(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".class")
                && (name.startsWith("EntityMotionApiTest") || name.startsWith("EntityPlayerFlightTest"));
    }

    private static final class NativeRemapper extends Remapper {
        private final TinyMappingResolver mappings;

        private NativeRemapper(TinyMappingResolver mappings) {
            this.mappings = mappings;
        }

        public String map(String name) {
            return mappings.resolveClass(new ClassRef(name), RuntimeNamespace.CLIENT);
        }

        public String mapFieldName(String owner, String name, String descriptor) {
            return member(owner, name, descriptor, false);
        }

        public String mapMethodName(String owner, String name, String descriptor) {
            return name.startsWith("<") ? name : member(owner, name, descriptor, true);
        }

        private String member(String owner, String name, String descriptor, boolean method) {
            String resolved = method
                    ? mappings.resolveMethod(new MethodRef(new ClassRef(owner), name, descriptor),
                            RuntimeNamespace.CLIENT).getName()
                    : mappings
                            .resolveField(new FieldRef(new ClassRef(owner), name, descriptor), RuntimeNamespace.CLIENT)
                            .getName();
            if (!resolved.equals(name)) {
                return resolved;
            }
            try {
                Class<?> type = Class.forName(owner.replace('/', '.'), false, getClass().getClassLoader());
                for (Class<?> parent : type.getInterfaces()) {
                    resolved = member(parent.getName().replace('.', '/'), name, descriptor, method);
                    if (!resolved.equals(name)) {
                        return resolved;
                    }
                }
                return type.getSuperclass() == null
                        ? name
                        : member(type.getSuperclass().getName().replace('.', '/'), name, descriptor, method);
            } catch (ClassNotFoundException ignored) {
                return name;
            }
        }
    }
}
