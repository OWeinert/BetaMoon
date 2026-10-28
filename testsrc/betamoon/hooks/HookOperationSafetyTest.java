package betamoon.hooks;

import com.sun.management.ThreadMXBean;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import betamoon.luamodloader.HookPackages;

/**
 * Mismatched/unsafe bytes retain engine input; dormant points remain
 * allocation-free.
 */
final class HookOperationSafetyTest {
    private static final String TARGET = "fixture.hooks.OperationFixture";
    private static final String INTERNAL = TARGET.replace('.', '/');
    private static int checks;

    static int verify() throws Throwable {
        declarations();
        mismatch();
        finalWrite();
        uninitialized();
        workerAndSnapshots();
        subscriptionSnapshot();
        narrowFieldValues();
        allocation();
        return checks;
    }

    private static String source(String kind, String method, String descriptor, String name, String memberDescriptor,
            int ordinal, int exact) {
        return "local r=betamoon.hooks:createRegistry(); " + HookOperationContractTest.point("point", method,
                descriptor, kind, TARGET, name, memberDescriptor, ordinal, exact);
    }

    private static void declarations() {
        String valid = source("invoke_after", "math", "(I)I", "scalar", "(I)I", 0, 1);
        String[] invalid = {valid.replace("matches={exact=1}", ""), valid.replace("ordinal=0", "ordinal=-1"),
                valid.replace("ordinal=0", "ordinal=1"), valid.replace("exact=1", "exact=0"),
                valid.replace("invoke_after", "monitor"), valid.replace("scalar", "<init>"),
                valid.replace("effects='edit'", "effects='edit',nativeErrors='recover'")};
        for (String text : invalid) {
            try {
                HookDeclarations.evaluate("operations", "hooks.lua", text, 1, null, new HookDeclarations.Budget());
                throw new AssertionError("incomplete/unsupported operation metadata accepted");
            } catch (RuntimeException expected) {
                require(expected.getMessage().contains("hook_file_invalid"), "precise operation metadata refusal");
            }
        }
    }

    private static void mismatch() throws Exception {
        String source = source("invoke_after", "two", "(Lfixture/hooks/OperationFixture;I)I", "scalar", "(I)I", 1, 2);
        source = source.replace("local r=betamoon.hooks:createRegistry(); ",
                "local r=betamoon.hooks:createRegistry(); " + "r:register({id='whole',target={class='" + TARGET
                        + "',method='math',descriptor='(I)I'},at='before'},function() end); ");
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> {
            ClassNode node = node(bytes);
            MethodNode method = method(node, "two");
            InsnList extra = new InsnList();
            extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
            extra.add(new InsnNode(Opcodes.ICONST_0));
            extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, INTERNAL, "scalar", "(I)I", false));
            extra.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(extra);
            return bytes(node);
        })) {
            fixture.load();
            HookPreparation.Site site = fixture.preparation.sites(INTERNAL).stream()
                    .filter(value -> value.target.method.equals("two")).findFirst().get();
            require(site.status.equals("match_failed") && site.points.values().iterator().next().observed == 3,
                    "foreign instruction-count change refuses exact match");
            require(Arrays.equals(fixture.input, fixture.delivered), "count mismatch preserves all engine bytes");
        }
    }

    private static void finalWrite() throws Exception {
        String source = source("field_write", "assignFinal", "()V", "CONSTANT", "I", 0, 1);
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> {
            ClassNode node = node(bytes);
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "assignFinal", "()V", null,
                    null);
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, INTERNAL, "CONSTANT", "I"));
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(method);
            return bytes(node);
        })) {
            fixture.transformOnly();
            require(fixture.preparation.sites(INTERNAL).get(0).status.equals("match_failed"), "final write rejected");
            require(Arrays.equals(fixture.input, fixture.delivered), "final-write refusal preserves engine bytes");
        }
    }

    private static void uninitialized() throws Exception {
        String source = source("invoke_after", "unsafe", "()I", "scalar", "(I)I", 0, 1);
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> {
            ClassNode node = node(bytes);
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "unsafe", "()I", null, null);
            method.instructions.add(new TypeInsnNode(Opcodes.NEW, INTERNAL));
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, INTERNAL, "scalar", "(I)I", false));
            method.instructions.add(new InsnNode(Opcodes.IRETURN));
            node.methods.add(method);
            return bytes(node);
        })) {
            fixture.transformOnly();
            require(fixture.preparation.sites(INTERNAL).get(0).status.equals("match_failed"),
                    "uninitialized receiver never enters a callback");
            require(Arrays.equals(fixture.input, fixture.delivered),
                    "uninitialized refusal does not publish partial bytecode");
        }
    }

    private static void workerAndSnapshots() throws Exception {
        String source = source("invoke_before", "two", "(Lfixture/hooks/OperationFixture;I)I", "scalar", "(I)I", 1, 2);
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> bytes)) {
            Class<?> target = fixture.load();
            Object receiver = target.newInstance();
            Method method = target.getMethod("two", target, int.class);
            AtomicInteger calls = new AtomicInteger();
            HookDispatcher.Subscription before = fixture.subscribe("point", call -> calls.incrementAndGet());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    method.invoke(receiver, receiver, 1);
                } catch (InvocationTargetException expected) {
                    failure.set(expected.getCause());
                } catch (ReflectiveOperationException unexpected) {
                    failure.set(unexpected);
                }
            });
            worker.start();
            worker.join();
            require(failure.get() instanceof HookCallbackFailure && calls.get() == 0,
                    "worker never enters Lua or queues edits");
            List<String> errors = new ArrayList<>();
            Files.write(fixture.hookFile, source.replace("ordinal=1", "ordinal=0").getBytes(StandardCharsets.UTF_8));
            try {
                fixture.preparation.preflight(
                        HookPackages.discover(fixture.instance.resolve("betamoon/scripts").toFile(), errors).get(0));
                throw new AssertionError("new operation site accepted during reload");
            } catch (HookFailure expected) {
                require(expected.reason.equals("restart_required"), "new operation envelope needs restart");
            }
            require(method.invoke(receiver, receiver, 1).equals(86) && calls.get() == 1,
                    "rejected preflight retains old callbacks");
            before.close();
        }
    }

    private static void subscriptionSnapshot() throws Exception {
        String source = "local r=betamoon.hooks:createRegistry(); "
                + HookOperationContractTest.point("entry", "two", "(Lfixture/hooks/OperationFixture;I)I",
                        "invoke_before", TARGET, "scalar", "(I)I", 1, 2)
                + HookOperationContractTest.point("exit", "two", "(Lfixture/hooks/OperationFixture;I)I", "invoke_after",
                        TARGET, "scalar", "(I)I", 1, 2);
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> bytes)) {
            Class<?> target = fixture.load();
            Object receiver = target.newInstance();
            Method method = target.getMethod("two", target, int.class);
            AtomicInteger oldCalls = new AtomicInteger();
            AtomicInteger newCalls = new AtomicInteger();
            HookDispatcher.Subscription old = fixture.subscribe("exit", call -> oldCalls.incrementAndGet());
            AtomicInteger entries = new AtomicInteger();
            fixture.subscribe("entry", call -> {
                if (entries.getAndIncrement() == 0) {
                    old.close();
                    try {
                        fixture.subscribe("exit", completed -> {
                            newCalls.incrementAndGet();
                            completed.result((Integer) completed.result() + 1);
                        });
                    } catch (ClassNotFoundException failure) {
                        throw new AssertionError("fixture type became unavailable", failure);
                    }
                }
            });
            require(method.invoke(receiver, receiver, 1).equals(86) && oldCalls.get() == 0 && newCalls.get() == 0,
                    "closed after callbacks are skipped; newly activated callbacks wait for the next operation");
            require(method.invoke(receiver, receiver, 1).equals(87) && newCalls.get() == 1,
                    "next operation captures the replacement callback exactly once");
        }
    }

    private static void narrowFieldValues() throws Exception {
        for (Object[] value : new Object[][]{{"writeSmall", "(B)V", "small", "B", (byte) 0, byte.class, 300},
                {"writeBoolean", "(Z)V", "enabled", "Z", false, boolean.class, 2},
                {"writeShort", "(S)V", "medium", "S", (short) 0, short.class, 65539},
                {"writeChar", "(C)V", "character", "C", (char) 0, char.class, -1}}) {
            String source = source("field_write", (String) value[0], (String) value[1], (String) value[2],
                    (String) value[3], 0, 1);
            try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> {
                ClassNode node = node(bytes);
                MethodNode method = method(node, (String) value[0]);
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.ILOAD) {
                        method.instructions.set(instruction, new LdcInsnNode(value[6]));
                    }
                }
                return bytes(node);
            })) {
                Class<?> target = fixture.load();
                Object receiver = target.newInstance();
                Method store = target.getMethod((String) value[0], (Class<?>) value[5]);
                store.invoke(receiver, value[4]);
                Object baseline = target.getField((String) value[2]).get(receiver);
                AtomicReference<Object> observed = new AtomicReference<>();
                fixture.subscribe("point", call -> observed.set(call.argument(0)));
                store.invoke(receiver, value[4]);
                Object after = target.getField((String) value[2]).get(receiver);
                require(baseline.equals(after) && baseline.equals(observed.get()),
                        "narrow field transport must preserve original JVM store semantics: " + value[3] + " baseline="
                                + baseline + " after=" + after + " observed=" + observed.get());
            }
        }
    }

    private static void allocation() throws Throwable {
        String source = "local r=betamoon.hooks:createRegistry(); " + HookOperationContractTest.point("point", "math",
                "(I)I", "invoke_after", "java.lang.Math", "abs", "(I)I", 0, 1);
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> bytes)) {
            Class<?> target = fixture.load();
            Object receiver = target.newInstance();
            MethodHandle call = MethodHandles.publicLookup()
                    .findVirtual(target, "math", MethodType.methodType(int.class, int.class)).bindTo(receiver);
            int sum = 0;
            for (int index = 0; index < 100000; index++) {
                sum += (int) call.invokeExact(-2);
            }
            ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            memory.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().getId();
            memory.getThreadAllocatedBytes(thread);
            // Fixed batches distinguish steady-state call allocation from one-time VM/linker
            // bookkeeping. Per-call allocation cannot disappear in a zero-byte batch.
            long[] batches = new long[3];
            long allocated = Long.MAX_VALUE;
            for (int batch = 0; batch < batches.length; batch++) {
                long start = memory.getThreadAllocatedBytes(thread);
                for (int index = 0; index < 100000; index++) {
                    sum += (int) call.invokeExact(-2);
                }
                batches[batch] = memory.getThreadAllocatedBytes(thread) - start;
                allocated = Math.min(allocated, batches[batch]);
            }
            System.out.println("Dormant operation allocations per 100000 calls: " + Arrays.toString(batches)
                    + "; steady-state=" + allocated + " bytes; sum=" + sum);
            require(allocated == 0 && sum > 0, "dormant operation guard allocated " + allocated + " bytes");
        }
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().get();
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
