package betamoon.hooks;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeQueryService;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import betamoon.luamodloader.HookPackages;
import java.io.ByteArrayInputStream;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Agent/ASM/LuaJ-only bootstrap and real JVM verification, with no Minecraft
 * dependency.
 */
public final class HookContractTest {
    private static final String TARGET = "fixture.hooks.HookTargetFixture";
    private static int checks;

    public static void main(String[] args) throws Throwable {
        try {
            Class.forName("net.minecraft.src.World", false, HookContractTest.class.getClassLoader());
            throw new AssertionError("Minecraft leaked into the bootstrap test");
        } catch (ClassNotFoundException expected) {
        }
        checks += HookPackageTest.verify();
        checks += HookMappingTest.verify();
        declarations();
        transformations();
        checks += HookEngineCompositionTest.verify();
        checks += HookAroundContractTest.verify();
        checks += HookOperationContractTest.verify();
        checks += HookOperationSafetyTest.verify();
        checks += HookConstructorContractTest.verify();
        checks += HookPerformanceContractTest.verify();
        checks += HookBufferContractTest.verify();
        checks += HookTimingContractTest.verify();
        checks += HookScaleProfile.verify();
        System.out.println(
                "Hook contracts passed: " + checks + " assertions; isolated declarations and verified JVM dispatch.");
    }

    private static void declarations() {
        List<HookFileCollectionContext.Registration> one = evaluate("local r=betamoon.hooks:createRegistry(); "
                + registration("observe", "calculate", "(IJDLjava/lang/Object;)I", "before", "observe", 0));
        require(one.size() == 1 && one.get(0).callback == null, "local registry captured without return/early closure");
        require(evaluate("local r=betamoon.hooks:createRegistry(); return {unrelated=true}").isEmpty(),
                "empty registry");
        failure("hook_registry_missing", "return {}");
        failure("hook_registry_already_created", "betamoon.hooks:createRegistry(); betamoon.hooks:createRegistry()");
        failure("declaration_failed", "require('main')");
        failure("declaration_failed", "io.open('forbidden')");
        failure("forbidden_phase", "betamoon.native:type('" + TARGET + "'):method('voidValue','()V'):call(nil)");
        failure("preparation_budget_exceeded", "while true do end");
        failure("preparation_budget_exceeded", "pcall(function() while true do end end)");
        failure("preparation_budget_exceeded", "xpcall(function() while true do end end,function() end)");
        failure("preparation_budget_exceeded", "table.remove({1},-2147483648)");
        failure("preparation_budget_exceeded", "table.insert({1},2147483647,2)");
        failure("preparation_budget_exceeded", "table.concat({'a'},'',1,2147483647)");
        failure("preparation_budget_exceeded", "local t={}; for i=1,5000 do table.insert(t,i) end");
        failure("preparation_budget_exceeded", "local function f() return 1+f() end; f()");
        failure("preparation_budget_exceeded", "local x='x'; for i=1,25 do x=x..x end");
        failure("hook_file_invalid", "local r=betamoon.hooks:createRegistry(); r:register({id='a'},function() end)");
        require(evaluate(
                "local r=betamoon.hooks:createRegistry(); local function noop() end; " + "for i=1,100 do noop() end")
                .isEmpty(), "bounded sequential helpers do not consume recursion depth");
        require(evaluate("local r=betamoon.hooks:createRegistry(); local t=table.pack('a','b'); "
                + "table.insert(t,2,'c'); assert(table.remove(t,2)=='c'); "
                + "assert(table.concat(t,'-')=='a-b'); assert(table.remove({})==nil)").isEmpty(),
                "audited table helpers preserve ordinary declaration use");
        List<HookFileCollectionContext.Registration> symbolic = evaluate("local r=betamoon.hooks:createRegistry(); "
                + "r:register({id='observe',target=betamoon.native:type('" + TARGET + "'):method('calculate',"
                + "'(IJDLjava/lang/Object;)I'),at='before'},function() error('must not execute') end)");
        require(one.get(0).definition.metadataKey().equals(symbolic.get(0).definition.metadataKey()),
                "equivalent target forms");
        HookDefinition declared = evaluate("local r=betamoon.hooks:createRegistry(); local T=betamoon.native:type('"
                + TARGET + "'); local M=T:method('calculate','(IJDLjava/lang/Object;)I',{declaringType=T}); "
                + "local d={id='snapshot',target=M,at='before'}; r:register(d,function() end); "
                + "d.id='changed'; d.at='error'").get(0).definition;
        require(declared.id.equals("snapshot") && declared.at.equals("before"), "definition metadata snapshot");
        require(HookDefinition.transport(declared.transport()).target.key().equals(declared.target.key()),
                "cross-loader metadata preserves declaring selector");
        HookFileCollectionContext context = new HookFileCollectionContext("owner", "file", 1, false);
        LuaValue registry = HookLuaValue.facade(context).get("createRegistry").call();
        context.seal();
        try {
            registry.get("register").call(LuaValue.NIL, LuaValue.NIL);
            throw new AssertionError("sealed registry accepted registration");
        } catch (RuntimeException expected) {
            require(expected.getMessage().contains("hook_registry_closed"), "sealed registry error");
        } finally {
            context.close();
        }
        try (HookFileCollectionContext first = new HookFileCollectionContext("owner", "first.lua", 1, false)) {
            LuaValue firstFactory = HookLuaValue.facade(first).get("createRegistry");
            try (HookFileCollectionContext second = new HookFileCollectionContext("owner", "second.lua", 2, false)) {
                try {
                    firstFactory.call();
                    throw new AssertionError("foreign file factory accepted");
                } catch (RuntimeException expected) {
                    require(expected.getMessage().contains("hook_registry_context_mismatch"),
                            "cross-file context check");
                }
                HookLuaValue.facade(second).get("createRegistry").call();
                second.seal();
            }
            firstFactory.call();
            first.seal();
        }
    }

    private static void transformations() throws Exception {
        Path instance = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "hook-contract-");
        Path root = Files.createDirectories(instance.resolve("betamoon/scripts/owner/hooks"));
        Files.write(root.getParent().resolve("betamoon.mod.json"),
                "{\"name\":\"Hook fixture\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(root.getParent().resolve("main.lua"),
                "error('entrypoint executed in preparation')".getBytes(StandardCharsets.UTF_8));
        Files.write(root.getParent().resolve(HookPackages.FILE_NAME),
                "[\"hooks/target.lua\"]".getBytes(StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder("local r=betamoon.hooks:createRegistry(); ");
        text.append(registration("entry", "calculate", "(IJDLjava/lang/Object;)I", "before", "edit", 10));
        text.append(registration("after", "calculate", "(IJDLjava/lang/Object;)I", "after", "edit", 10));
        text.append(registration("error", "calculate", "(IJDLjava/lang/Object;)I", "error", "observe", 10));
        String[] names = {"longValue", "doubleValue", "floatValue", "booleanValue", "byteValue", "charValue",
                "shortValue", "objectValue", "voidValue", "onlyThrows", "privateMethod", "arrayValue"};
        String[] descriptors = {"(J)J", "(D)D", "(F)F", "(Z)Z", "(B)B", "(C)C", "(S)S",
                "(Ljava/lang/Object;)Ljava/lang/Object;", "()V", "()I", "()I", "([I)[I"};
        for (int index = 0; index < names.length; index++) {
            text.append(registration(names[index], names[index], descriptors[index], "after", "observe", 0));
        }
        Files.write(root.resolve("target.lua"), text.toString().getBytes(StandardCharsets.UTF_8));
        System.setProperty("betamoon.instanceDir", instance.toString());
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        HookPreparation prepared = HookPreparation.prepare(mappings);
        require(System.getProperty("betamoon.hookFixture.initialized") == null,
                "preparation did not initialize target");
        List<String> failures = new ArrayList<>();
        HookPackages.Snapshot snapshot = HookPackages.discover(instance.resolve("betamoon/scripts").toFile(), failures)
                .get(0);
        require(failures.isEmpty(), "shared package discovery");
        List<HookFileCollectionContext.Registration> definitions = prepared.preflight(snapshot);
        BetaMoonTransformer transformer = new BetaMoonTransformer(Collections.emptyMap(), mappings,
                new TransformationReport(), false, false);
        byte[] original;
        try (java.io.InputStream input = HookContractTest.class.getClassLoader()
                .getResourceAsStream(TARGET.replace('.', '/') + ".class")) {
            original = read(input);
        }
        ClassLoader loader = new ClassLoader(HookContractTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(TARGET)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> existing = findLoadedClass(name);
                if (existing != null) {
                    return existing;
                }
                try {
                    byte[] transformed = transformer.transform(this, name.replace('.', '/'), null, null, original);
                    require(transformed != null, "user-only target transformed");
                    return defineClass(name, transformed, 0, transformed.length);
                } catch (Exception error) {
                    throw new ClassNotFoundException(name, error);
                }
            }
        };
        Class<?> target = Class.forName(TARGET, false, loader);
        require(prepared.status().stream().filter(row -> "HOOK_SITE".equals(row.get("kind")))
                .allMatch(row -> "applied".equals(row.get("status"))), "all method sites applied");
        Map<String, Object> inspected = prepared.status().stream()
                .filter(row -> "HOOK_SITE".equals(row.get("kind"))
                        && row.get("target").toString().endsWith(".calculate(IJDLjava/lang/Object;)I"))
                .findFirst().get();
        require(((Number) ((Map<?, ?>) inspected.get("matchCounts")).get("methods")).intValue() == 1,
                "hook inspection exact method count");
        require(((List<?>) inspected.get("installedCapabilities")).contains("result_edit"),
                "hook inspection installed editing capability");
        require(((List<?>) inspected.get("namespaceCandidates")).contains("named"),
                "hook inspection resolved namespace");
        require(Boolean.FALSE.equals(((Map<?, ?>) inspected.get("provenance")).get("laterTransformersObserved")),
                "hook inspection does not claim later transformer visibility");
        require(prepared.status().stream().filter(row -> "HOOK_DECLARATION".equals(row.get("kind")))
                .allMatch(row -> !((List<?>) row.get("targets")).isEmpty()), "declarations link to exact sites");
        String inspectedTarget = (String) inspected.get("target");
        prepared.diagnose("owner,other", inspectedTarget, "match_failed", "inspection fixture");
        Map<String, Object> linked = prepared.status().stream()
                .filter(row -> "HOOK_DIAGNOSTIC".equals(row.get("kind")) && inspectedTarget.equals(row.get("target")))
                .findFirst().get();
        require(((List<?>) linked.get("owners")).contains("owner"), "group diagnostics retain exact owner links");
        require("inspect_hook_site".equals(((Map<?, ?>) linked.get("guidance")).get("code")),
                "group diagnostics include actionable guidance");
        NativeIndexSnapshot evidence = NativeIndexSnapshot.empty().withHooks(prepared.status(), prepared.revision(),
                "available");
        List<?> query = (List<?>) NativeQueryService
                .query(evidence, CatalogValues.record("kind", "hooks", "owner", "owner", "name", inspectedTarget))
                .get("items");
        require(query.size() >= 2, "existing query finds shared sites and their linked refusals by owner");
        require(query.stream().allMatch(row -> "captured_hook_status".equals(((Map<?, ?>) row).get("evidence"))),
                "query labels captured evidence without a runtime activation claim");
        require(System.getProperty("betamoon.hookFixture.initialized") == null, "load does not initialize target");
        Object receiver = target.newInstance();
        Method method = target.getMethod("calculate", int.class, long.class, double.class, Object.class);
        require(method.invoke(receiver, 1, 2L, 3.0, new Object()).equals(6), "dormant native behavior");
        HookDispatcher.clientThread();
        HookDefinition before = definitions.get(0).definition;
        int site = prepared.targets(before).get(0).id;
        AtomicReference<HookInvocation> retained = new AtomicReference<>();
        HookDispatcher.Subscription entry = HookDispatcher.subscribe(site, target, before, call -> {
            retained.set(call);
            call.argument(0, 4);
        });
        require(method.invoke(receiver, 1, 2L, 3.0, new Object()).equals(6), "staged subscription dormant");
        HookDispatcher.activate(Collections.singletonList(entry));
        require(method.invoke(receiver, 1, 2L, 3.0, new Object()).equals(9), "wide argument local edit");
        try {
            retained.get().argument(0);
            throw new AssertionError("call escaped");
        } catch (HookFailure expected) {
            require(expected.reason.equals("hook_call_expired"), "callback view expires");
        }
        HookDispatcher.Subscription after = HookDispatcher.subscribe(site, target, definitions.get(1).definition,
                call -> call.result((Integer) call.result() * 2));
        HookDispatcher.activate(Collections.singletonList(after));
        require(method.invoke(receiver, 1, 2L, 3.0, new Object()).equals(18), "normal result transform");
        entry.close();
        AtomicInteger observed = new AtomicInteger();
        HookDispatcher.Subscription errors = HookDispatcher.subscribe(site, target, definitions.get(2).definition,
                call -> {
                    require(call.exception().getMessage().equals("native-body-failure"), "native exception identity");
                    observed.incrementAndGet();
                    throw new IllegalStateException("observer-failure");
                });
        HookDispatcher.activate(Collections.singletonList(errors));
        try {
            method.invoke(receiver, -1, 2L, 3.0, null);
            throw new AssertionError("native exception swallowed");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() instanceof IllegalArgumentException, "original exception preserved");
        }
        require(observed.get() == 1 && errors.failures == 1, "error observer failed once and disabled");
        after.close();
        HookDispatcher.Subscription cancel = HookDispatcher.subscribe(site, target, before, call -> call.result(55));
        HookDispatcher.activate(Collections.singletonList(cancel));
        int count = target.getField("bodyCalls").getInt(null);
        require(method.invoke(receiver, 1, 2L, 3.0, null).equals(55), "typed early result");
        require(target.getField("bodyCalls").getInt(null) == count, "short circuit skips body");
        cancel.close();
        require(method.invoke(receiver, 1, 2L, 3.0, null).equals(3), "cleanup restores dormant behavior");
        for (int index = 0; index < 8; index++) {
            Class<?> primitive = new Class<?>[]{long.class, double.class, float.class, boolean.class, byte.class,
                    char.class, short.class, Object.class}[index];
            Object value = new Object[]{Long.MAX_VALUE, -0.0D, Float.NaN, true, (byte) -128, '\uFFFF', (short) -32768,
                    null}[index];
            HookDefinition definition = definitions.get(index + 3).definition;
            HookDispatcher.Subscription observation = HookDispatcher.subscribe(prepared.targets(definition).get(0).id,
                    target, definition,
                    call -> require(java.util.Objects.equals(call.result(), value), "exact return representation"));
            HookDispatcher.activate(Collections.singletonList(observation));
            require(java.util.Objects.equals(target.getMethod(names[index], primitive).invoke(null, value), value),
                    "typed return " + names[index]);
            observation.close();
        }
        typedEdits(prepared, target, receiver);
        composition(prepared, target, receiver, method, site);
        betamoon.luaapi.interop.NativeHookApiTest.run(prepared, mappings, target, receiver, method);
        reload(prepared, instance, root, text.toString(), definitions.size());
    }

    private static void typedEdits(HookPreparation prepared, Class<?> target, Object receiver) throws Exception {
        String[] names = {"longValue", "doubleValue", "floatValue", "booleanValue", "byteValue", "charValue",
                "shortValue", "objectValue", "arrayValue"};
        String[] descriptors = {"(J)J", "(D)D", "(F)F", "(Z)Z", "(B)B", "(C)C", "(S)S",
                "(Ljava/lang/Object;)Ljava/lang/Object;", "([I)[I"};
        Class<?>[] types = {long.class, double.class, float.class, boolean.class, byte.class, char.class, short.class,
                Object.class, int[].class};
        Object[] original = {1L, 1D, 1F, true, (byte) 1, 'a', (short) 1, new Object(), new int[]{1}};
        Object[] changed = {Long.MIN_VALUE, -0.0D, Float.POSITIVE_INFINITY, false, (byte) 127, '\uFFFF', (short) -32768,
                "changed", new int[]{2, 3}};
        for (int index = 0; index < names.length; index++) {
            Method method = target.getMethod(names[index], types[index]);
            Object expected = changed[index];
            for (String operation : new String[]{"argument", "early", "after"}) {
                String at = operation.equals("after") ? "after" : "before";
                HookDefinition definition = evaluate("local r=betamoon.hooks:createRegistry(); "
                        + registration("typed_" + operation, names[index], descriptors[index], at, "edit", 0))
                        .get(0).definition;
                HookDispatcher.Subscription subscription = HookDispatcher
                        .subscribe(prepared.targets(definition).get(0).id, target, definition, call -> {
                            if (operation.equals("argument")) {
                                call.argument(0, expected);
                            } else {
                                call.result(expected);
                            }
                        });
                HookDispatcher.activate(Collections.singletonList(subscription));
                require(java.util.Objects.equals(method.invoke(null, new Object[]{original[index]}), expected),
                        "typed " + operation + " " + names[index]);
                subscription.close();
            }
        }
        HookDefinition nullable = evaluate("local r=betamoon.hooks:createRegistry(); "
                + registration("array_null", "arrayValue", "([I)[I", "before", "edit", 0)).get(0).definition;
        HookDispatcher.Subscription nullResult = HookDispatcher.subscribe(prepared.targets(nullable).get(0).id, target,
                nullable, call -> call.result(null));
        HookDispatcher.activate(Collections.singletonList(nullResult));
        require(target.getMethod("arrayValue", int[].class).invoke(null, new int[]{1}) == null,
                "null array early result");
        nullResult.close();
        AtomicInteger privateCalls = new AtomicInteger();
        HookDefinition privateTarget = evaluate("local r=betamoon.hooks:createRegistry(); "
                + registration("private_observer", "privateMethod", "()I", "after", "observe", 0)).get(0).definition;
        HookDispatcher.Subscription privateObserver = HookDispatcher.subscribe(
                prepared.targets(privateTarget).get(0).id, target, privateTarget,
                call -> privateCalls.incrementAndGet());
        HookDispatcher.activate(Collections.singletonList(privateObserver));
        Method privateMethod = target.getDeclaredMethod("privateMethod");
        privateMethod.setAccessible(true);
        require(privateMethod.invoke(receiver).equals(7) && privateCalls.get() == 1,
                "private declared method observation");
        privateObserver.close();
    }

    private static void composition(HookPreparation prepared, Class<?> target, Object receiver, Method method, int site)
            throws Exception {
        List<String> order = new ArrayList<>();
        HookDefinition highEntry = definition("high_entry", "before", "edit", 10);
        HookDefinition lowEntry = definition("low_entry", "before", "edit", 0);
        HookDefinition highReturn = definition("high_return", "after", "edit", 10);
        HookDefinition lowReturn = definition("low_return", "after", "edit", 0);
        List<HookDispatcher.Subscription> subscriptions = Arrays
                .asList(HookDispatcher.subscribe(site, target, highEntry, call -> {
                    order.add("H");
                    call.argument(0, (Integer) call.argument(0) + 10);
                }), HookDispatcher.subscribe(site, target, lowEntry, call -> {
                    order.add("L");
                    call.argument(0, (Integer) call.argument(0) * 2);
                }), HookDispatcher.subscribe(site, target, highReturn, call -> {
                    order.add("h");
                    call.result((Integer) call.result() + 1);
                }), HookDispatcher.subscribe(site, target, lowReturn, call -> {
                    order.add("l");
                    call.result((Integer) call.result() * 3);
                }));
        HookDispatcher.activate(subscriptions);
        require(method.invoke(receiver, 1, 2L, 3.0, null).equals(73), "composed entry/result edits");
        require(order.equals(Arrays.asList("H", "L", "l", "h")), "priority and reverse return order");
        for (HookDispatcher.Subscription subscription : subscriptions) {
            subscription.close();
        }
        AtomicInteger errorCalls = new AtomicInteger();
        HookDispatcher.Subscription error = HookDispatcher.subscribe(site, target,
                definition("errors", "error", "observe", 0), call -> errorCalls.incrementAndGet());
        HookDispatcher.activate(Collections.singletonList(error));
        AtomicReference<HookInvocation> failedFrame = new AtomicReference<>();
        HookDispatcher.Subscription invalid = HookDispatcher.subscribe(site, target, highEntry, call -> {
            failedFrame.set(call);
            call.argument(0, 77);
            call.result("invalid int");
        });
        HookDispatcher.activate(Collections.singletonList(invalid));
        int bodies = target.getField("bodyCalls").getInt(null);
        invokeFailure(method, receiver, HookCallbackFailure.class);
        require(errorCalls.get() == 0 && target.getField("bodyCalls").getInt(null) == bodies,
                "invalid staged edits skip body and native error observers");
        try {
            failedFrame.get().argument(0);
            throw new AssertionError("failed callback frame survived");
        } catch (HookFailure expired) {
            require(expired.reason.equals("hook_call_expired"), "failure expires frame");
        }
        invalid.close();
        HookDispatcher.Subscription badReturn = HookDispatcher.subscribe(site, target, highReturn, call -> {
            throw new IllegalStateException("return-callback-failure");
        });
        HookDispatcher.activate(Collections.singletonList(badReturn));
        invokeFailure(method, receiver, HookCallbackFailure.class);
        require(errorCalls.get() == 0 && target.getField("bodyCalls").getInt(null) == bodies + 1,
                "return failure executes body once without native error observation");
        badReturn.close();
        AtomicInteger attempts = new AtomicInteger();
        HookDispatcher.Subscription observer = HookDispatcher.subscribe(site, target,
                definition("bad_observer", "before", "observe", 0), call -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("observation failure");
                });
        HookDispatcher.activate(Collections.singletonList(observer));
        require(method.invoke(receiver, 1, 2L, 3.0, null).equals(3), "observer failure preserves native behavior");
        method.invoke(receiver, 1, 2L, 3.0, null);
        require(attempts.get() == 1, "faulted observation disabled");
        AtomicInteger entered = new AtomicInteger();
        HookDispatcher.Subscription recursive = HookDispatcher.subscribe(site, target,
                definition("recursive", "before", "observe", 0), call -> {
                    entered.incrementAndGet();
                    try {
                        method.invoke(receiver, 1, 2L, 3.0, null);
                    } catch (ReflectiveOperationException failure) {
                        throw new RuntimeException(failure);
                    }
                });
        HookDispatcher.activate(Collections.singletonList(recursive));
        method.invoke(receiver, 1, 2L, 3.0, null);
        require(entered.get() == 1 && recursive.skipped == 1, "self observation skips and counts reentry");
        recursive.close();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        HookDispatcher.Subscription workerEdit = HookDispatcher.subscribe(site, target, highEntry,
                call -> entered.incrementAndGet());
        HookDispatcher.activate(Collections.singletonList(workerEdit));
        Thread worker = new Thread(() -> {
            try {
                method.invoke(receiver, 1, 2L, 3.0, null);
            } catch (InvocationTargetException failure) {
                workerFailure.set(failure.getCause());
            } catch (ReflectiveOperationException failure) {
                workerFailure.set(failure);
            }
        }, "unsupported-hook-worker");
        worker.start();
        worker.join();
        require(workerFailure.get() instanceof HookCallbackFailure && entered.get() == 1,
                "worker editing fails without entering callback or queueing");
        workerEdit.close();
        error.close();
        require(prepared.status().stream().anyMatch(row -> "callback_disabled".equals(row.get("status"))),
                "bounded failure diagnostics exported");
    }

    private static void reload(HookPreparation prepared, Path instance, Path root, String original, int initial)
            throws Exception {
        List<String> errors = new ArrayList<>();
        Path source = root.resolve("target.lua");
        Files.write(source, (original + "-- callback-only edit\n").getBytes(StandardCharsets.UTF_8));
        HookPackages.Snapshot compatible = HookPackages.discover(instance.resolve("betamoon/scripts").toFile(), errors)
                .get(0);
        require(prepared.preflight(compatible).size() == initial, "callback/comment changes compatible");
        Files.write(source,
                (original + registration("another", "calculate", "(IJDLjava/lang/Object;)I", "after", "observe", 5))
                        .getBytes(StandardCharsets.UTF_8));
        HookPackages.Snapshot extra = HookPackages.discover(instance.resolve("betamoon/scripts").toFile(), errors)
                .get(0);
        require(prepared.preflight(extra).size() == initial + 1, "new hook on owner's prepared target compatible");
        Files.write(source,
                (original + registration("new_around", "calculate", "(IJDLjava/lang/Object;)I", "around", "edit", 0))
                        .getBytes(StandardCharsets.UTF_8));
        HookPackages.Snapshot newShape = HookPackages.discover(instance.resolve("betamoon/scripts").toFile(), errors)
                .get(0);
        try {
            prepared.preflight(newShape);
            throw new AssertionError("new around shape accepted without restart");
        } catch (HookFailure expected) {
            require(expected.reason.equals("restart_required"),
                    "old envelope needs restart to acquire around capability");
        }
        Files.write(source, (original + registration("new_target", "synchronizedMethod", "()I", "before", "observe", 0))
                .getBytes(StandardCharsets.UTF_8));
        HookPackages.Snapshot changed = HookPackages.discover(instance.resolve("betamoon/scripts").toFile(), errors)
                .get(0);
        try {
            prepared.preflight(changed);
            throw new AssertionError("new target accepted at reload");
        } catch (HookFailure expected) {
            require(expected.reason.equals("restart_required"), "new target requires restart");
        }
        Files.write(source, original.getBytes(StandardCharsets.UTF_8));
    }

    private static HookDefinition definition(String id, String at, String effects, int priority) {
        return evaluate("local r=betamoon.hooks:createRegistry(); "
                + registration(id, "calculate", "(IJDLjava/lang/Object;)I", at, effects, priority)).get(0).definition;
    }

    private static void invokeFailure(Method method, Object receiver, Class<?> expected) throws Exception {
        try {
            method.invoke(receiver, 1, 2L, 3.0, null);
            throw new AssertionError("expected " + expected.getName());
        } catch (InvocationTargetException failure) {
            require(expected.isInstance(failure.getCause()), "strict callback exception: " + failure.getCause());
        }
    }

    private static byte[] read(java.io.InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static List<HookFileCollectionContext.Registration> evaluate(String text) {
        return HookDeclarations.evaluate("owner", "hooks.lua", text, 1, null, new HookDeclarations.Budget());
    }

    private static void failure(String reason, String text) {
        try {
            evaluate(text);
            throw new AssertionError("Expected " + reason);
        } catch (RuntimeException expected) {
            require(expected.getMessage().contains(reason), "precise failure " + reason + ": " + expected);
        }
    }

    private static String registration(String id, String method, String descriptor, String at, String effects,
            int priority) {
        return "r:register({id='" + id + "',target={class='" + TARGET + "',method='" + method + "',descriptor='"
                + descriptor + "'},at='" + at + "',effects='" + effects + "',priority=" + priority
                + "},function() error('early callback executed') end); ";
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
