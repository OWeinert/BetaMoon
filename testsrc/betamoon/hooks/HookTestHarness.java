package betamoon.hooks;

import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Shared bootstrap-only fixture preparation; never executes a Lua package
 * entrypoint.
 */
final class HookTestHarness implements AutoCloseable {
    final String targetName;
    final String source;
    final Path instance;
    final Path hookFile;
    final HookPreparation preparation;
    final TinyMappingResolver mappings;
    final ClassLoader loader;
    final byte[] input;
    byte[] delivered;
    private Class<?> target;
    private final BetaMoonTransformer transformer;
    private final List<HookDispatcher.Subscription> subscriptions = new ArrayList<>();

    HookTestHarness(String targetName, String source, UnaryOperator<byte[]> transformInput, String... related)
            throws Exception {
        this(targetName, source, transformInput, 1, related);
    }

    HookTestHarness(String targetName, String source, UnaryOperator<byte[]> transformInput, int owners,
            String... related) throws Exception {
        this.targetName = targetName;
        this.source = source;
        instance = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "hook-fixture-");
        Path root = Files.createDirectories(instance.resolve("betamoon/scripts/operations"));
        Files.write(root.resolve("betamoon.mod.json"),
                "{\"name\":\"Operation fixture\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("main.lua"), new byte[0]);
        Files.write(root.resolve("betamoon.hooks.json"), "[\"hooks.lua\"]".getBytes(StandardCharsets.UTF_8));
        hookFile = root.resolve("hooks.lua");
        Files.write(hookFile, source.getBytes(StandardCharsets.UTF_8));
        for (int index = 1; index < owners; index++) {
            Path extra = Files.createDirectories(instance.resolve("betamoon/scripts/operations" + index));
            Files.write(extra.resolve("betamoon.mod.json"),
                    "{\"name\":\"Scale fixture\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
            Files.write(extra.resolve("main.lua"), new byte[0]);
            Files.write(extra.resolve("betamoon.hooks.json"), "[\"hooks.lua\"]".getBytes(StandardCharsets.UTF_8));
            Files.write(extra.resolve("hooks.lua"), source.getBytes(StandardCharsets.UTF_8));
        }
        System.setProperty("betamoon.instanceDir", instance.toString());
        mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        preparation = HookPreparation.prepare(mappings);
        transformer = new BetaMoonTransformer(Collections.emptyMap(), mappings, new TransformationReport(), false,
                false);
        input = transformInput.apply(
                Files.readAllBytes(Paths.get("build/classes/java/test/" + targetName.replace('.', '/') + ".class")));
        Set<String> defined = new HashSet<>(Arrays.asList(related));
        defined.add(targetName);
        loader = new ClassLoader(HookTestHarness.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!defined.contains(name)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> existing = findLoadedClass(name);
                    if (existing != null) {
                        return existing;
                    }
                    try {
                        byte[] bytes;
                        if (name.equals(targetName)) {
                            bytes = transformOnly();
                        } else {
                            bytes = Files.readAllBytes(
                                    Paths.get("build/classes/java/test/" + name.replace('.', '/') + ".class"));
                            byte[] transformed = transformer.transform(this, name.replace('.', '/'), null, null, bytes);
                            if (transformed != null) {
                                bytes = transformed;
                            }
                        }
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (IllegalClassFormatException | IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
            }
        };
    }

    byte[] transformOnly() throws IllegalClassFormatException {
        delivered = transformer.transform(loader, targetName.replace('.', '/'), null, null, input);
        if (delivered == null) {
            delivered = input;
        }
        return delivered;
    }

    Class<?> load() throws ClassNotFoundException {
        if (target == null) {
            target = Class.forName(targetName, true, loader);
        }
        HookDispatcher.clientThread();
        return target;
    }

    HookDefinition definition(String id) {
        return definition("operations", id);
    }

    HookDefinition definition(String owner, String id) {
        return HookDeclarations.evaluate(owner, "hooks.lua", source, 1, null, new HookDeclarations.Budget()).stream()
                .map(registration -> registration.definition).filter(value -> value.id.equals(id)).findFirst().get();
    }

    HookDispatcher.Subscription subscribe(String id, HookDispatcher.Callback callback) throws ClassNotFoundException {
        return subscribe("operations", id, callback);
    }

    HookDispatcher.Subscription subscribe(String owner, String id, HookDispatcher.Callback callback)
            throws ClassNotFoundException {
        HookDefinition definition = definition(owner, id);
        load();
        HookPreparation.Site site = preparation.targets(definition).get(0);
        Class<?> anchor = Class.forName(site.target.owner.replace('/', '.'), false, loader);
        HookDispatcher.Subscription subscription = HookDispatcher.subscribe(site.id, anchor, definition, callback);
        HookDispatcher.activate(Collections.singletonList(subscription));
        subscriptions.add(subscription);
        return subscription;
    }

    @Override
    public void close() {
        for (HookDispatcher.Subscription subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();
    }
}
