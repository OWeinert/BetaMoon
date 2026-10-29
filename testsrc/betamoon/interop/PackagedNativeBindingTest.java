package betamoon.interop;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * The isolated loader uses the actual mod artifact, never the runner's loose
 * main classes.
 */
public final class PackagedNativeBindingTest {
    private PackagedNativeBindingTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("Expected namespace, BetaMoon and game JARs");
        }
        List<URL> urls = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            File file = new File(args[i]);
            if (!file.isFile()) {
                throw new IllegalArgumentException("Missing runtime dependency: " + file);
            }
            urls.add(file.toURI().toURL());
        }
        urls.add(PackagedNativeBindingTest.class.getProtectionDomain().getCodeSource().getLocation());
        Path parent = Paths.get("build").toAbsolutePath().normalize().toRealPath();
        Path work = Files.createTempDirectory(parent, "native-game-binding-");
        try (URLClassLoader runtime = new URLClassLoader(urls.toArray(new URL[0]), null)) {
            try {
                runtime.loadClass("betamoon.luaapi.interop.NativeBindingGameScenario")
                        .getMethod("run", String.class, Path.class).invoke(null, args[0], work);
            } catch (InvocationTargetException exception) {
                throw new AssertionError("Isolated " + args[0] + " native binding failed", exception.getCause());
            }
        } finally {
            Path target = work.toRealPath();
            if (!target.getParent().equals(parent)
                    || !target.getFileName().toString().startsWith("native-game-binding-")) {
                throw new AssertionError("Unsafe game-binding fixture cleanup target");
            }
            try (Stream<Path> paths = Files.walk(target)) {
                for (Path path : (Iterable<Path>) paths.sorted(Collections.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("Native game binding passed: " + args[0] + " calls, mod overrides, signatures and events.");
    }
}
