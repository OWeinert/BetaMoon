package betamoon.hooks;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.registry.HookRegistry;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.luamodloader.HookPackages;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Verifies combined engine/user paths, mapped aliases and atomic fallback with
 * real JVM execution.
 */
public final class HookEngineCompositionTest {
    private static final String TARGET = "fixture/hooks/EngineHookFixture";
    private static final String CALLBACKS = "betamoon/hooks/HookEngineCompositionTest";
    private static int engineEntries;
    private static int engineReturns;
    private static int engineCleanups;
    private static int checks;

    public static int entering(int value) {
        engineEntries++;
        return value == 7 ? 1 : 0;
    }

    public static int returning(int value) {
        engineReturns++;
        return value + 100;
    }

    public static int beginCleanup() {
        engineEntries++;
        return 0;
    }

    public static void endCleanup() {
        engineCleanups++;
    }

    static int verify() throws Exception {
        scenario(false, false, false);
        scenario(true, false, false);
        scenario(false, true, false);
        scenario(false, false, true);
        return checks;
    }

    private static void scenario(boolean client, boolean invalidGroup, boolean external) throws Exception {
        engineEntries = 0;
        engineReturns = 0;
        engineCleanups = 0;
        String mapping = "tiny\t2\t0\tnamed\tclient\tserver\nc\t" + TARGET + "\t" + TARGET + "\t" + TARGET
                + "\n\tm\t(I)I\tcompute\tc\tc\n\tm\t(Z)V\tcleanup\td\td\n";
        TinyMappingResolver mappings = TinyMappingResolver
                .readStrict(new ByteArrayInputStream(mapping.getBytes(StandardCharsets.UTF_8)));
        Path instance = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "hook-engine-");
        Path owner = Files.createDirectories(instance.resolve("betamoon/scripts/engine"));
        Files.write(owner.resolve("betamoon.mod.json"),
                "{\"name\":\"Engine fixture\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(owner.resolve("main.lua"), "error('entrypoint executed')".getBytes(StandardCharsets.UTF_8));
        Files.write(owner.resolve("betamoon.hooks.json"), "[\"hooks.lua\"]".getBytes(StandardCharsets.UTF_8));
        String source = "local r=betamoon.hooks:createRegistry(); "
                + declaration("entry", "compute", "(I)I", "before", "observe")
                + declaration("return", "compute", "(I)I", "after", "edit")
                + declaration("cleanup_return", "cleanup", "(Z)V", "after", "observe")
                + declaration("cleanup_error", "cleanup", "(Z)V", "error", "observe");
        if (invalidGroup) {
            source += declaration("blocked", "blocked", "()I", "before", "observe");
        }
        Files.write(owner.resolve("hooks.lua"), source.getBytes(StandardCharsets.UTF_8));
        System.setProperty("betamoon.instanceDir", instance.toString());
        HookPreparation preparation = HookPreparation.prepare(mappings);
        List<String> errors = new ArrayList<>();
        HookPackages.Snapshot snapshot = HookPackages.discover(instance.resolve("betamoon/scripts").toFile(), errors)
                .get(0);
        List<HookFileCollectionContext.Registration> definitions = preparation.preflight(snapshot);
        TransformationReport report = new TransformationReport();
        HookRegistry engine = new HookRegistry(report);
        engine.register(
                AroundHookDefinition.builder("fixture:compute", new MethodRef(new ClassRef(TARGET), "compute", "(I)I"))
                        .capture(HandlerRef.of(CALLBACKS, "entering", "(I)I"), ValueBinding.argument(0))
                        .onReturn(HandlerRef.of(CALLBACKS, "returning", "(I)I"), ValueBinding.returnValue())
                        .skipWhenCapturedNonZero().build());
        engine.register(
                AroundHookDefinition.builder("fixture:cleanup", new MethodRef(new ClassRef(TARGET), "cleanup", "(Z)V"))
                        .capture(HandlerRef.of(CALLBACKS, "beginCleanup", "()I"))
                        .onReturn(HandlerRef.of(CALLBACKS, "endCleanup", "()V")).cleanupOnException().build());
        BetaMoonTransformer transformer = new BetaMoonTransformer(
                engine.freeze(mappings, RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT), mappings, report, false,
                false);
        byte[] original;
        try (InputStream input = HookEngineCompositionTest.class.getResourceAsStream("/" + TARGET + ".class")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                bytes.write(buffer, 0, count);
            }
            original = bytes.toByteArray();
        }
        if (client) {
            ClassNode node = new ClassNode();
            new ClassReader(original).accept(node, 0);
            for (MethodNode method : node.methods) {
                if (method.name.equals("compute")) {
                    method.name = "c";
                } else if (method.name.equals("cleanup")) {
                    method.name = "d";
                }
            }
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            original = writer.toByteArray();
        }
        if (external) {
            original = replaceConstant(original, 10, 20);
        }
        byte[] input = original;
        AtomicReference<byte[]> delivered = new AtomicReference<>();
        ClassLoader loader = new ClassLoader(HookEngineCompositionTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(TARGET.replace('/', '.'))) {
                    return super.loadClass(name, resolve);
                }
                Class<?> found = findLoadedClass(name);
                if (found != null) {
                    return found;
                }
                try {
                    byte[] bytes = transformer.transform(this, TARGET, null, null, input);
                    if (external) {
                        bytes = replaceConstant(bytes, 20, 30);
                    }
                    delivered.set(bytes);
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (Exception error) {
                    throw new ClassNotFoundException(name, error);
                }
            }
        };
        Class<?> target = Class.forName(TARGET.replace('/', '.'), true, loader);
        Method compute = target.getMethod(client ? "c" : "compute", int.class);
        Method cleanup = target.getMethod(client ? "d" : "cleanup", boolean.class);
        if (invalidGroup) {
            require(compute.invoke(null, 7).equals(100), "invalid user group discarded valid engine early return");
            require(engineEntries == 1 && engineReturns == 1 && target.getField("bodies").getInt(null) == 0,
                    "engine fallback body/handler counts");
            require(preparation.sites(TARGET).stream().noneMatch(site -> site.status.equals("applied")),
                    "partial user group published");
            return;
        }
        HookDispatcher.clientThread();
        String namespace = client ? "client" : "named";
        AtomicInteger observations = new AtomicInteger();
        AtomicInteger returns = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        HookDefinition before = definitions.get(0).definition;
        int site = preparation.targets(before, namespace).get(0).id;
        List<HookDispatcher.Subscription> subscriptions = Arrays.asList(
                HookDispatcher.subscribe(site, target, before, call -> observations.incrementAndGet()),
                HookDispatcher.subscribe(site, target, definitions.get(1).definition, call -> {
                    returns.incrementAndGet();
                    call.result((Integer) call.result() + 1);
                }));
        HookDispatcher.activate(subscriptions);
        require(compute.invoke(null, 7).equals(101), "engine early return not observed/transformed exactly once");
        require(observations.get() == 1 && returns.get() == 1 && engineEntries == 1 && engineReturns == 1
                && target.getField("bodies").getInt(null) == 0, "combined early-return counts");
        require(compute.invoke(null, 1).equals(external ? 132 : 112), "native/engine/user/external return composition");
        if (external) {
            require(!preparation.targets(before, namespace).get(0).outputHash
                    .equals(CatalogFingerprint.sha256(delivered.get())),
                    "provenance claimed control of a later transformer");
        }
        for (HookDispatcher.Subscription subscription : subscriptions) {
            subscription.close();
        }
        HookDefinition early = HookDeclarations.evaluate("engine", "early.lua",
                "local r=betamoon.hooks:createRegistry(); " + declaration("short", "compute", "(I)I", "before", "edit")
                        .replace("effects='edit'", "effects='edit',priority=10"),
                5, null, new HookDeclarations.Budget()).get(0).definition;
        HookDispatcher.Subscription shortCircuit = HookDispatcher.subscribe(site, target, early,
                call -> call.result(55));
        HookDispatcher.activate(Collections.singletonList(shortCircuit));
        int entered = engineEntries;
        int completed = engineReturns;
        require(compute.invoke(null, 1).equals(55) && engineEntries == entered && engineReturns == completed,
                "script short circuit opened an engine scope or ran an ordinary return handler");
        shortCircuit.close();
        HookDefinition returned = definitions.get(2).definition;
        HookDefinition failed = definitions.get(3).definition;
        int cleanupSite = preparation.targets(returned, namespace).get(0).id;
        HookDispatcher.Subscription normal = HookDispatcher.subscribe(cleanupSite, target, returned,
                call -> returns.incrementAndGet());
        HookDispatcher.Subscription exceptional = HookDispatcher.subscribe(cleanupSite, target, failed, call -> {
            failures.incrementAndGet();
            throw new IllegalStateException("observer failure");
        });
        HookDispatcher.activate(Arrays.asList(normal, exceptional));
        int beforeReturns = returns.get();
        cleanup.invoke(null, false);
        require(engineCleanups == 1 && returns.get() == beforeReturns + 1, "normal engine cleanup/observation");
        try {
            cleanup.invoke(null, true);
            throw new AssertionError("native exceptional cleanup was swallowed");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() == target.getField("FAILURE").get(null), "original Throwable replaced");
        }
        require(engineCleanups == 2 && returns.get() == beforeReturns + 1 && failures.get() == 1,
                "exception cleanup/normal-return/error-observer separation");
        normal.close();
        exceptional.close();
        AtomicReference<List<HookDispatcher.Subscription>> removing = new AtomicReference<>();
        AtomicInteger expiredCalls = new AtomicInteger();
        HookDispatcher.Subscription remover = HookDispatcher.subscribe(site, target, early, call -> {
            call.argument(0, 99);
            for (HookDispatcher.Subscription entry : removing.get()) {
                entry.close();
            }
        });
        HookDispatcher.Subscription removed = HookDispatcher.subscribe(site, target, before,
                call -> expiredCalls.incrementAndGet());
        removing.set(Arrays.asList(remover, removed));
        HookDispatcher.activate(removing.get());
        require(compute.invoke(null, 1).equals(external ? 131 : 111) && expiredCalls.get() == 0,
                "unload during dispatch entered an expired callback or published its staged edits");
    }

    private static byte[] replaceConstant(byte[] input, int from, int to) {
        ClassNode node = new ClassNode();
        new ClassReader(input).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof IntInsnNode && ((IntInsnNode) instruction).operand == from) {
                    ((IntInsnNode) instruction).operand = to;
                    changed++;
                }
            }
        }
        require(changed == 1, "external transformer fixture changed the wrong instruction");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String declaration(String id, String method, String descriptor, String phase, String effects) {
        return "r:register({id='" + id + "',target={class='" + TARGET.replace('/', '.') + "',method='" + method
                + "',descriptor='" + descriptor + "'},at='" + phase + "',effects='" + effects + "'},function() end);";
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
