package betamoon.hooks;

import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import betamoon.luaapi.interop.NativeHookOperationApiTest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import java.io.ByteArrayInputStream;
import java.lang.instrument.IllegalClassFormatException;
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

/**
 * Actual Java 8 execution of selected operand edits, fields and JVM instruction
 * variants.
 */
public final class HookOperationContractTest {
    private static final String TARGET = "fixture.hooks.OperationFixture";
    private static final String TWO = "(Lfixture/hooks/OperationFixture;I)I";
    private static final String CONTRACT = "(Lfixture/hooks/OperationContract;I)I";
    private static final String WIDE = "(JDLjava/lang/Object;)J";
    private static int checks;

    static int verify() throws Exception {
        scenario(false);
        scenario(true);
        return checks;
    }

    private static void scenario(boolean stripped) throws Exception {
        String declarations = "local r=betamoon.hooks:createRegistry(); "
                + point("before", "two", TWO, "invoke_before", TARGET, "scalar", "(I)I", 1, 2)
                + point("after", "two", TWO, "invoke_after", TARGET, "scalar", "(I)I", 1, 2)
                + point("wide_before", "staticCall", WIDE, "invoke_before", TARGET, "wide", WIDE, 0, 1)
                + point("wide_after", "staticCall", WIDE, "invoke_after", TARGET, "wide", WIDE, 0, 1)
                + point("special", "special", "(I)I", "invoke_after", TARGET, "secret", "(I)I", 0, 1)
                + point("interface", "contract", CONTRACT, "invoke_after", "fixture.hooks.OperationContract", "value",
                        "(I)I", 0, 1)
                + point("read", "read", "()J", "field_read", TARGET, "counter", "J", 0, 1)
                + point("write", "write", "(J)V", "field_write", TARGET, "counter", "J", 0, 1)
                + point("static_read", "readShared", "()D", "field_read", TARGET, "shared", "D", 0, 1)
                + point("void", "voidCall", "(I)V", "invoke_after", TARGET, "voidTarget", "(I)V", 0, 1)
                + point("constructing", "constructing", "()Ljava/lang/String;", "invoke_after", TARGET, "text",
                        "()Ljava/lang/String;", 0, 1)
                + point("math", "math", "(I)I", "invoke_after", "java.lang.Math", "abs", "(I)I", 0, 1)
                + point("default_special", "useDefault", "(I)I", "invoke_after", "fixture.hooks.OperationContract",
                        "defaultValue", "(I)I", 0, 1)
                + "r:register({id='method_wrap',target={class='" + TARGET + "',method='two',descriptor='" + TWO
                + "'},at='around',effects='edit'},function() end);";
        Path instance = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "hook-operations-");
        Path root = Files.createDirectories(instance.resolve("betamoon/scripts/operations"));
        Files.write(root.resolve("betamoon.mod.json"),
                "{\"name\":\"Operation fixture\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("main.lua"), new byte[0]);
        Files.write(root.resolve("betamoon.hooks.json"), "[\"hooks.lua\"]".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("hooks.lua"), declarations.getBytes(StandardCharsets.UTF_8));
        System.setProperty("betamoon.instanceDir", instance.toString());
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        HookPreparation preparation = HookPreparation.prepare(mappings);
        BetaMoonTransformer transformer = new BetaMoonTransformer(Collections.emptyMap(), mappings,
                new TransformationReport(), false, false);
        byte[] original = Files
                .readAllBytes(Paths.get("build/classes/java/test/" + TARGET.replace('.', '/') + ".class"));
        if (stripped) {
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(original).accept(writer, ClassReader.SKIP_DEBUG);
            original = writer.toByteArray();
        }
        byte[] source = original;
        ClassLoader loader = new ClassLoader(HookOperationContractTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(TARGET)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) {
                    return loaded;
                }
                try {
                    byte[] bytes = transformer.transform(this, name.replace('.', '/'), null, null, source);
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IllegalClassFormatException failure) {
                    throw new ClassNotFoundException(name, failure);
                }
            }
        };
        Class<?> target = Class.forName(TARGET, true, loader);
        Object receiver = target.newInstance();
        Object other = target.newInstance();
        require(preparation.sites(TARGET.replace('.', '/')).stream().allMatch(site -> site.status.equals("applied")),
                "all operation shapes applied");
        HookDispatcher.clientThread();
        Method two = target.getMethod("two", target, int.class);
        List<HookDispatcher.Subscription> subscriptions = new ArrayList<>();
        AtomicReference<HookInvocation> retained = new AtomicReference<>();
        subscriptions.add(subscribe(preparation, target, declarations, "before", call -> {
            retained.set(call);
            require(call.argumentCount() == 1 && call.argument(0).equals(42),
                    "operation arguments exclude containing arguments");
            require(call.receiver() == other && call.operation().enclosingReceiver() == receiver,
                    "exact callee/enclosing receivers");
            require(call.operation().enclosingArgument(1).equals(1),
                    "containing arguments retain entry values after local reassignment");
            call.argument(0, 9);
        }));
        subscriptions.add(subscribe(preparation, target, declarations, "after", call -> {
            require(call.argument(0).equals(9) && call.result().equals(18),
                    "after receives actual edited invocation arguments/result");
            call.result(21);
        }));
        require(two.invoke(receiver, other, 1).equals(23), "selected second invocation only");
        require(events(target).equals(Arrays.asList("first:1", "callee:1", "second:42", "callee:9")),
                "argument evaluation order and one native invocation");
        try {
            retained.get().argument(0);
            throw new AssertionError("operation view survived");
        } catch (HookFailure expected) {
            require(expected.reason.equals("hook_call_expired"), "operation expires");
        }
        subscriptions.add(subscribe(preparation, target, declarations, "method_wrap",
                call -> call.result((Integer) call.proceed(call.argumentValues()) + 100)));
        require(two.invoke(receiver, other, 1).equals(123),
                "around helper retains operation envelopes and terminal composition");
        clear(subscriptions);
        subscriptions.add(subscribe(preparation, target, declarations, "wide_before", call -> {
            require(call.receiver() == null && call.argument(0).equals(4L) && call.argument(1).equals(2.5),
                    "static wide operands");
            call.argument(0, 8L);
        }));
        subscriptions.add(subscribe(preparation, target, declarations, "wide_after",
                call -> call.result((Long) call.result() + 1)));
        require(target.getMethod("staticCall", long.class, double.class, Object.class).invoke(receiver, 4L, 2.5, null)
                .equals(1012L), "wide slots and overload filtering");
        clear(subscriptions);
        subscriptions.add(subscribe(preparation, target, declarations, "special", call -> {
            require(call.operation().operation.opcode == Opcodes.INVOKESPECIAL, "special opcode retained");
            call.result((Integer) call.result() + 1);
        }));
        require(target.getMethod("special", int.class).invoke(receiver, 2).equals(6), "private special invocation");
        clear(subscriptions);
        AtomicInteger returned = new AtomicInteger();
        subscriptions.add(subscribe(preparation, target, declarations, "interface", call -> {
            returned.incrementAndGet();
            call.result((Integer) call.result() * 2);
        }));
        Class<?> contract = Class.forName("fixture.hooks.OperationContract", false, loader);
        Method invokeInterface = target.getMethod("contract", contract, int.class);
        require(invokeInterface.invoke(receiver, other, 1).equals(22), "interface result transform");
        try {
            invokeInterface.invoke(receiver, other, -1);
            throw new AssertionError("native failure swallowed");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() == target.getField("FAILURE").get(null), "native exception identity");
        }
        try {
            invokeInterface.invoke(receiver, null, 1);
            throw new AssertionError("null receiver fabricated success");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() instanceof NullPointerException, "original null check");
        }
        require(returned.get() == 1, "after points do not run on native exceptions");
        clear(subscriptions);
        target.getField("counter").setLong(receiver, 7L);
        subscriptions.add(
                subscribe(preparation, target, declarations, "read", call -> call.result((Long) call.result() + 5)));
        require(target.getMethod("read").invoke(receiver).equals(12L)
                && target.getField("counter").getLong(receiver) == 7L,
                "volatile read still performed before transform");
        clear(subscriptions);
        subscriptions.add(subscribe(preparation, target, declarations, "write", call -> {
            require(call.argument(0).equals(7L), "field-write typed incoming value");
            call.argument(0, 9L);
        }));
        target.getMethod("write", long.class).invoke(receiver, 7L);
        require(target.getField("counter").getLong(receiver) == 9L, "volatile field store uses validated replacement");
        clear(subscriptions);
        subscriptions.add(subscribe(preparation, target, declarations, "static_read",
                call -> call.result((Double) call.result() * 2)));
        require(target.getMethod("readShared").invoke(receiver).equals(2.5), "static wide field read");
        clear(subscriptions);
        AtomicInteger voidCalls = new AtomicInteger();
        subscriptions.add(subscribe(preparation, target, declarations, "void", call -> {
            require(call.result() == null && call.argument(0).equals(3),
                    "void native outcome has no fabricated result");
            voidCalls.incrementAndGet();
        }));
        target.getMethod("voidCall", int.class).invoke(receiver, 3);
        require(voidCalls.get() == 1, "void return observation once");
        clear(subscriptions);
        subscriptions.add(
                subscribe(preparation, target, declarations, "constructing", call -> call.result(call.result() + "!")));
        require(target.getMethod("constructing").invoke(receiver).equals("native!"),
                "uninitialized allocation below unrelated operands remains valid");
        clear(subscriptions);
        subscriptions.add(subscribe(preparation, target, declarations, "math", call -> call.result(99)));
        require(target.getMethod("math", int.class).invoke(receiver, -2).equals(99),
                "JDK referenced call without transforming JDK classes");
        clear(subscriptions);
        subscriptions.add(subscribe(preparation, target, declarations, "default_special",
                call -> call.result((Integer) call.result() + 1)));
        require(target.getMethod("useDefault", int.class).invoke(receiver, 1).equals(22),
                "interface default special call retains opcode semantics");
        clear(subscriptions);
        NativeHookOperationApiTest.run(preparation, mappings, target, receiver, other, two, declarations);
        System.out.println("Operation execution passed with strippedDebug=" + stripped);
    }

    private static HookDispatcher.Subscription subscribe(HookPreparation preparation, Class<?> target, String source,
            String id, HookDispatcher.Callback callback) {
        HookDefinition definition = HookDeclarations
                .evaluate("operations", "hooks.lua", source, 1, null, new HookDeclarations.Budget()).stream()
                .map(registration -> registration.definition).filter(value -> value.id.equals(id)).findFirst().get();
        HookDispatcher.Subscription subscription = HookDispatcher.subscribe(preparation.targets(definition).get(0).id,
                target, definition, callback);
        HookDispatcher.activate(Collections.singletonList(subscription));
        return subscription;
    }

    static String point(String id, String method, String descriptor, String kind, String owner, String name,
            String selectedDescriptor, int ordinal, int exact) {
        boolean field = kind.startsWith("field_");
        return "r:register({id='" + id + "',target={class='" + TARGET + "',method='" + method + "',descriptor='"
                + descriptor + "'},at={kind='" + kind + "'," + (field ? "field" : "method") + "={class='" + owner + "',"
                + (field ? "field" : "method") + "='" + name + "',descriptor='" + selectedDescriptor + "'},ordinal="
                + ordinal + ",matches={exact=" + exact + "}},effects='edit'},function() end);";
    }

    @SuppressWarnings("unchecked")
    private static List<String> events(Class<?> target) throws Exception {
        return (List<String>) target.getField("EVENTS").get(null);
    }

    private static void clear(List<HookDispatcher.Subscription> subscriptions) {
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
