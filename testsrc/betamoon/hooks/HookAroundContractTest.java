package betamoon.hooks;

import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.interop.catalog.ClassFileMetadataReader;
import betamoon.luaapi.interop.NativeHookAroundApiTest;
import java.lang.instrument.IllegalClassFormatException;
import java.io.IOException;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

/**
 * Real transformed Java 8 methods, including private helpers, exact errors and
 * recursive native calls.
 */
public final class HookAroundContractTest {
    private static final String TARGET = "fixture.hooks.HookTargetFixture";
    private static final String CALCULATE = "(IJDLjava/lang/Object;)I";
    private static int checks;
    private static HookPreparation preparation;
    private static Class<?> target;
    private static Object receiver;
    private static Method calculate;
    private static final List<HookDispatcher.Subscription> subscriptions = new ArrayList<>();

    static int verify() throws Exception {
        Path instance = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "hook-around-");
        String[][] signatures = {{"calculate", CALCULATE}, {"longValue", "(J)J"}, {"doubleValue", "(D)D"},
                {"floatValue", "(F)F"}, {"booleanValue", "(Z)Z"}, {"byteValue", "(B)B"}, {"charValue", "(C)C"},
                {"shortValue", "(S)S"}, {"objectValue", "(Ljava/lang/Object;)Ljava/lang/Object;"},
                {"arrayValue", "([I)[I"}, {"voidValue", "()V"}, {"privateMethod", "()I"}, {"recursive", "(I)I"},
                {"checked", "()I"}};
        for (String owner : new String[]{"owner", "other"}) {
            Path root = Files.createDirectories(instance.resolve("betamoon/scripts/" + owner));
            Files.write(root.resolve("betamoon.mod.json"),
                    "{\"name\":\"Around fixture\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
            Files.write(root.resolve("main.lua"), new byte[0]);
            Files.write(root.resolve("betamoon.hooks.json"), "[\"hooks.lua\"]".getBytes(StandardCharsets.UTF_8));
            StringBuilder source = new StringBuilder("local r=betamoon.hooks:createRegistry(); ");
            for (String[] signature : signatures) {
                source.append(registration(signature[0], signature[0], signature[1], "around", ""));
            }
            Files.write(root.resolve("hooks.lua"), source.toString().getBytes(StandardCharsets.UTF_8));
        }
        System.setProperty("betamoon.instanceDir", instance.toString());
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        preparation = HookPreparation.prepare(mappings);
        BetaMoonTransformer transformer = new BetaMoonTransformer(Collections.emptyMap(), mappings,
                new TransformationReport(), false, false);
        byte[] original = Files
                .readAllBytes(Paths.get("build/classes/java/test/" + TARGET.replace('.', '/') + ".class"));
        AtomicReference<byte[]> delivered = new AtomicReference<>();
        ClassLoader loader = new ClassLoader(HookAroundContractTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(TARGET) && !name.equals("fixture.hooks.HookOverrideFixture")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> existing = findLoadedClass(name);
                if (existing != null) {
                    return existing;
                }
                if (!name.equals(TARGET)) {
                    try {
                        byte[] subclass = Files.readAllBytes(
                                Paths.get("build/classes/java/test/" + name.replace('.', '/') + ".class"));
                        return defineClass(name, subclass, 0, subclass.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                try {
                    byte[] transformed = transformer.transform(this, name.replace('.', '/'), null, null, original);
                    delivered.set(transformed);
                    return defineClass(name, transformed, 0, transformed.length);
                } catch (IllegalClassFormatException failure) {
                    throw new ClassNotFoundException(name, failure);
                }
            }
        };
        target = Class.forName(TARGET, false, loader);
        require(new ClassFileMetadataReader().read(delivered.get()).getMembers().values().stream()
                .noneMatch(member -> member.getName().startsWith("betamoon$hook$body$")),
                "generated helpers stay out of ordinary native metadata");
        receiver = target.newInstance();
        calculate = target.getMethod("calculate", int.class, long.class, double.class, Object.class);
        HookDispatcher.clientThread();
        require(preparation.status().stream().filter(row -> "HOOK_SITE".equals(row.get("kind"))).allMatch(
                row -> "applied".equals(row.get("status")) && Integer.valueOf(3).equals(row.get("envelopeVersion"))),
                "all ordinary around shapes verified and installed");
        for (Method method : target.getDeclaredMethods()) {
            if (method.getName().startsWith("betamoon$hook$body$")) {
                require(method.isSynthetic() && Modifier.isPrivate(method.getModifiers()),
                        "helper is private/synthetic");
            }
        }
        require(invoke(1).equals(3), "dormant helper preserves native result");
        composition();
        limits();
        failures();
        recursion();
        allTypes();
        add("owner", "super_dispatch", "calculate", CALCULATE, "around", "",
                call -> call.result((Integer) call.proceed(call.argumentValues()) + 10));
        Object derived = Class.forName("fixture.hooks.HookOverrideFixture", true, loader).newInstance();
        require(calculate.invoke(derived, 1, 2L, 3.0, null).equals(1013),
                "exact extracted body does not redispatch to the subclass override");
        clear();
        NativeHookAroundApiTest.run(preparation, mappings, target, receiver, calculate);
        clear();
        System.out.println("Around hooks passed: " + checks
                + " assertions; continuation, native identity and exact helper dispatch.");
        return checks;
    }

    private static void composition() throws Exception {
        List<String> order = new ArrayList<>();
        AtomicInteger phases = new AtomicInteger();
        add("owner", "before", "calculate", CALCULATE, "before", "", call -> phases.incrementAndGet());
        add("owner", "after", "calculate", CALCULATE, "after", "", call -> phases.incrementAndGet());
        add("owner", "outer", "calculate", CALCULATE, "around", ",priority=30", call -> {
            order.add("outer+");
            call.result((Integer) call.proceed(call.argumentValues()) + 1);
            order.add("outer-");
        });
        add("owner", "same_owner", "calculate", CALCULATE, "around", ",priority=20", call -> {
            order.add("same+");
            call.result((Integer) call.proceed(call.argumentValues()) + 2);
            order.add("same-");
        });
        add("other", "inner", "calculate", CALCULATE, "around", ",priority=10", call -> {
            order.add("inner+");
            call.result((Integer) call.proceed(call.argumentValues()) + 3);
            order.add("inner-");
        });
        int bodies = bodies();
        require(invoke(1).equals(9) && bodies() == bodies + 1 && phases.get() == 2,
                "three wrappers and one terminal envelope");
        require(order.equals(Arrays.asList("outer+", "same+", "inner+", "inner-", "same-", "outer-")),
                "priority nesting");
        clear();
        add("owner", "replacement", "calculate", CALCULATE, "around", "", call -> call.result(91));
        bodies = bodies();
        require(invoke(1).equals(91) && bodies() == bodies, "zero continuation replaces body");
        clear();
        AtomicReference<HookInvocation> retained = new AtomicReference<>();
        add("owner", "edited", "calculate", CALCULATE, "around", "", call -> {
            retained.set(call);
            call.argument(0, 4);
            call.result(call.proceed(call.argumentValues()));
            try {
                call.proceed(call.argumentValues());
                throw new AssertionError("double continuation accepted");
            } catch (HookFailure expected) {
                require(expected.reason.equals("hook_continuation_used"), "double continuation precise refusal");
            }
        });
        require(invoke(1).equals(6), "validated current argument buffer continues");
        try {
            retained.get().proceed(new Object[]{1, 2L, 3.0, null});
            throw new AssertionError("escaped continuation accepted");
        } catch (HookFailure expected) {
            require(expected.reason.equals("hook_call_expired"), "continuation expires with callback");
        }
        clear();
    }

    private static void limits() throws Exception {
        for (int index = 0; index < 16; index++) {
            add("owner", "bounded_" + index, "calculate", CALCULATE, "around", "",
                    call -> call.result(call.proceed(call.argumentValues())));
        }
        require(invoke(1).equals(3), "sixteen same-owner callbacks compose within the limit");
        try {
            add("owner", "overflow", "calculate", CALCULATE, "around", "", call -> call.result(0));
            throw new AssertionError("unbounded same-owner around chain accepted");
        } catch (HookFailure expected) {
            require(expected.reason.equals("hook_recursion"), "around subscription bound is explicit");
        }
        clear();
    }

    private static void failures() throws Exception {
        RuntimeException nativeFailure = new IllegalStateException("exact native failure");
        target.getField("injectedFailure").set(null, nativeFailure);
        AtomicInteger errors = new AtomicInteger();
        add("owner", "error", "calculate", CALCULATE, "error", "", call -> errors.incrementAndGet());
        add("owner", "caught", "calculate", CALCULATE, "around", "", call -> {
            try {
                call.proceed(call.argumentValues());
            } catch (Throwable caught) {
                require(caught == nativeFailure, "continuation transports original native identity");
            }
            call.result(7);
        });
        require(failure(-2) == nativeFailure && errors.get() == 1,
                "caught native failure propagates by default; one observer");
        clear();
        add("owner", "recover", "calculate", CALCULATE, "around", ",nativeErrors='recover'", call -> {
            try {
                call.proceed(call.argumentValues());
            } catch (Throwable caught) {
                call.result(8);
            }
        });
        require(invoke(-2).equals(8), "explicit recovery validates fallback");
        require(preparation.status().stream().anyMatch(row -> "native_error_recovered".equals(row.get("status"))),
                "recovery diagnosed");
        clear();
        add("owner", "secondary", "calculate", CALCULATE, "around", ",nativeErrors='recover'", call -> {
            try {
                call.proceed(call.argumentValues());
            } finally {
                throw new IllegalArgumentException("secondary wrapper failure");
            }
        });
        require(failure(-2) == nativeFailure, "native failure remains primary over wrapper failure");
        clear();
        int bodies = bodies();
        add("owner", "invalid", "calculate", CALCULATE, "around", "", call -> call.result("invalid int"));
        require(failure(1) instanceof HookCallbackFailure && bodies() == bodies,
                "invalid zero-continuation result aborts");
        clear();
        add("owner", "late", "calculate", CALCULATE, "around", "", call -> {
            call.proceed(call.argumentValues());
            throw new IllegalArgumentException("late wrapper failure");
        });
        bodies = bodies();
        require(failure(1) instanceof HookCallbackFailure && bodies() == bodies + 1,
                "late wrapper failure never repeats body");
        clear();
        AtomicReference<HookDispatcher.Subscription> closing = new AtomicReference<>();
        closing.set(add("owner", "unload", "calculate", CALCULATE, "around", "", call -> {
            Object result = call.proceed(call.argumentValues());
            closing.get().close();
            call.result((Integer) result + 1000);
        }));
        require(invoke(1).equals(3), "invalidated wrapper preserves completed terminal outcome");
        clear();
        add("owner", "worker", "calculate", CALCULATE, "around", "",
                call -> call.result(call.proceed(call.argumentValues())));
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                invoke(1);
            } catch (InvocationTargetException failure) {
                workerFailure.set(failure.getCause());
            } catch (Exception failure) {
                workerFailure.set(failure);
            }
        });
        bodies = bodies();
        worker.start();
        worker.join();
        require(workerFailure.get() instanceof HookCallbackFailure && bodies() == bodies,
                "worker cannot enter continuation or body");
        clear();
        target.getField("injectedFailure").set(null, null);
        ThreadDeath fatal = new ThreadDeath();
        target.getField("injectedFatal").set(null, fatal);
        add("owner", "fatal", "calculate", CALCULATE, "around", ",nativeErrors='recover'", call -> {
            try {
                call.proceed(call.argumentValues());
            } catch (Throwable caught) {
                call.result(5);
            }
        });
        require(failure(-3) == fatal, "explicit recovery cannot swallow fatal thread/VM errors");
        clear();
        target.getField("injectedFatal").set(null, null);
        Exception checked = new Exception("checked native failure");
        target.getField("checkedFailure").set(null, checked);
        add("owner", "checked", "checked", "()I", "around", "",
                call -> call.result(call.proceed(call.argumentValues())));
        try {
            target.getMethod("checked").invoke(receiver);
            throw new AssertionError("checked native failure swallowed");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() == checked,
                    "checked Throwable is never replaced by reflection/transport wrapper");
        }
        clear();
    }

    private static void recursion() throws Exception {
        Method recursive = target.getMethod("recursive", int.class);
        AtomicInteger entered = new AtomicInteger();
        HookDispatcher.Subscription wrapper = add("owner", "recursive", "recursive", "(I)I", "around", "", call -> {
            entered.incrementAndGet();
            call.result(call.proceed(call.argumentValues()));
        });
        require(recursive.invoke(receiver, 3).equals(4) && entered.get() == 1 && wrapper.skipped == 3,
                "native recursion bypasses only the entered owner wrapper");
        clear();
        add("owner", "recursive_error", "recursive", "(I)I", "around", ",reentry='error'",
                call -> call.result(call.proceed(call.argumentValues())));
        try {
            recursive.invoke(receiver, 1);
            throw new AssertionError("reentry error policy ignored");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() instanceof HookCallbackFailure, "explicit reentry error");
        }
        clear();
    }

    private static void allTypes() throws Exception {
        Object[][] cases = {{"longValue", "(J)J", long.class, Long.MIN_VALUE},
                {"doubleValue", "(D)D", double.class, 2.5}, {"floatValue", "(F)F", float.class, 1.5f},
                {"booleanValue", "(Z)Z", boolean.class, true}, {"byteValue", "(B)B", byte.class, (byte) 4},
                {"charValue", "(C)C", char.class, 'a'}, {"shortValue", "(S)S", short.class, (short) 6},
                {"objectValue", "(Ljava/lang/Object;)Ljava/lang/Object;", Object.class, new Object()},
                {"arrayValue", "([I)[I", int[].class, new int[]{1, 2}}};
        for (Object[] entry : cases) {
            add("owner", "typed", (String) entry[0], (String) entry[1], "around", "",
                    call -> call.result(call.proceed(call.argumentValues())));
            Object actual = target.getMethod((String) entry[0], (Class<?>) entry[2]).invoke(null, entry[3]);
            require(actual.equals(entry[3]), "exact return/argument " + entry[0]);
            clear();
        }
        add("owner", "void", "voidValue", "()V", "around", "",
                call -> call.result(call.proceed(call.argumentValues())));
        int bodies = bodies();
        target.getMethod("voidValue").invoke(null);
        require(bodies() == bodies + 1, "void continuation executes once");
        clear();
        add("owner", "private", "privateMethod", "()I", "around", "",
                call -> call.result(call.proceed(call.argumentValues())));
        Method method = target.getDeclaredMethod("privateMethod");
        method.setAccessible(true);
        require(method.invoke(receiver).equals(7), "exact private helper dispatch");
        clear();
    }

    private static HookDispatcher.Subscription add(String owner, String id, String method, String descriptor, String at,
            String options, HookDispatcher.Callback callback) {
        HookDefinition definition = HookDeclarations.evaluate(owner, "hooks.lua",
                "local r=betamoon.hooks:createRegistry(); " + registration(id, method, descriptor, at, options), 1,
                null, new HookDeclarations.Budget()).get(0).definition;
        HookDispatcher.Subscription subscription = HookDispatcher.subscribe(preparation.targets(definition).get(0).id,
                target, definition, callback);
        subscriptions.add(subscription);
        HookDispatcher.activate(Collections.singletonList(subscription));
        return subscription;
    }

    private static String registration(String id, String method, String descriptor, String at, String options) {
        return "r:register({id='" + id + "',target={class='" + TARGET + "',method='" + method + "',descriptor='"
                + descriptor + "'},at='" + at + "',effects='" + (at.equals("error") ? "observe" : "edit") + "'"
                + options + "},function() end); ";
    }

    private static Object invoke(int value) throws Exception {
        return calculate.invoke(receiver, value, 2L, 3.0, null);
    }

    private static Throwable failure(int value) throws Exception {
        try {
            invoke(value);
            throw new AssertionError("expected around failure");
        } catch (InvocationTargetException expected) {
            return expected.getCause();
        }
    }

    private static int bodies() throws Exception {
        return target.getField("bodyCalls").getInt(null);
    }

    private static void clear() {
        for (HookDispatcher.Subscription subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
