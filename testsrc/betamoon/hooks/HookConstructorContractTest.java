package betamoon.hooks;

import betamoon.luaapi.interop.NativeHookConstructedApiTest;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Actual Java 8 constructor lifecycle, independently typed targets and
 * initialized-this refusal.
 */
final class HookConstructorContractTest {
    private static final String TARGET = "fixture.hooks.ConstructionFixture";
    private static final String BASE = "fixture.hooks.ConstructionBase";
    private static final String DERIVED = "fixture.hooks.ConstructionDerived";
    private static final String INNER = TARGET + "$Inner";
    private static int checks;

    static int verify() throws Exception {
        declarationContracts();
        lifecycle(false);
        lifecycle(true);
        overwrittenReceiverLocal();
        uninitializedReturn();
        return checks;
    }

    private static String declaration(String id, String owner, String descriptor) {
        return "r:register({id='" + id + "',target=betamoon.native:type('" + owner + "'):constructor('" + descriptor
                + "'),at='constructed'},function() end);";
    }

    private static void declarationContracts() {
        String source = "local r=betamoon.hooks:createRegistry(); " + declaration("create", TARGET, "(I)V");
        for (String invalid : new String[]{source.replace("constructed", "before"),
                source.replace("at='constructed'", "at='constructed',effects='edit'"),
                source.replace("at='constructed'", "at='constructed',nativeErrors='recover'"),
                source.replace(":constructor('(I)V')", ":method('value','()I')")}) {
            try {
                HookDeclarations.evaluate("operations", "hooks.lua", invalid, 1, null, new HookDeclarations.Budget());
                throw new AssertionError("invalid constructor declaration accepted");
            } catch (RuntimeException expected) {
                require(expected.getMessage().contains("hook_file_invalid"),
                        "constructor metadata refuses unsupported edits/targets");
            }
        }
        HookDefinition table = HookDeclarations.evaluate("operations", "hooks.lua",
                "local r=betamoon.hooks:createRegistry(); r:register({id='table',target={class='" + TARGET
                        + "',constructor='(I)V'},at='constructed'},function() end)",
                1, null, new HookDeclarations.Budget()).get(0).definition;
        require(HookDefinition.transport(table.transport()).target.key().equals(table.target.key()),
                "constructor kind survives independent metadata transport");
    }

    private static void lifecycle(boolean stripped) throws Exception {
        String source = "local r=betamoon.hooks:createRegistry(); " + declaration("base", BASE, "(I)V")
                + declaration("inner", TARGET, "(I)V") + declaration("derived", TARGET, "(I)V")
                + declaration("failure", TARGET, "(I)V") + declaration("lua", TARGET, "(I)V")
                + declaration("outer", TARGET, "()V")
                + declaration("synthetic", INNER, "(Lfixture/hooks/ConstructionFixture;I)V");
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> {
            if (!stripped) {
                return bytes;
            }
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(bytes).accept(writer, ClassReader.SKIP_DEBUG);
            return writer.toByteArray();
        }, BASE, DERIVED, INNER)) {
            Class<?> type = fixture.load();
            Constructor<?> create = type.getConstructor(int.class);
            create.newInstance(1);
            require(fixture.preparation.sites(TARGET.replace('.', '/')).stream()
                    .allMatch(s -> s.status.equals("applied")),
                    "constructor envelopes applied without replaying dormant creation");
            require(fixture.preparation.status().stream().filter(row -> "HOOK_SITE".equals(row.get("kind")))
                    .allMatch(row -> "constructor".equals(row.get("executableKind"))
                            && "declaring_constructor_return".equals(row.get("completion"))
                            && ("applied".equals(row.get("status"))
                                    ? Collections.singletonList("constructed")
                                    : Collections.emptyList()).equals(row.get("installedCapabilities"))),
                    "constructor inspection advertises declaring observation only");
            List<String> order = new ArrayList<>();
            AtomicReference<HookInvocation> saved = new AtomicReference<>();
            fixture.subscribe("base", call -> {
                require(call.receiver() != null && call.exception() == null && call.result() == null,
                        "initialized receiver only, no fictional result/exception");
                order.add("base:" + call.argument(0));
            });
            fixture.subscribe("inner", call -> {
                saved.set(call);
                order.add("inner:" + call.argument(0));
            });
            fixture.subscribe("outer", call -> order.add("outer"));
            Object receiver = type.newInstance();
            require(order.equals(Arrays.asList("base:2", "inner:2", "outer")),
                    "this/super delegation emits exact declaring events once in native order");
            require(type.getField("outer").getInt(receiver) == 7, "outer constructor completes its native writes");
            order.clear();
            create.newInstance(0);
            require(order.equals(Arrays.asList("base:0", "inner:0")), "early normal return observes exact constructor");
            order.clear();
            Object originalFailure = type.getField("FAILURE").get(null);
            try {
                create.newInstance(-1);
                throw new AssertionError("throwing constructor fabricated an object");
            } catch (InvocationTargetException expected) {
                require(expected.getCause() == originalFailure, "native constructor exception identity preserved");
            }
            require(order.equals(Arrays.asList("base:-1")),
                    "completed base observed but throwing derived constructor is not");
            AtomicInteger derivedObservations = new AtomicInteger();
            fixture.subscribe("derived", call -> {
                if (call.receiver().getClass().getName().equals(DERIVED)) {
                    try {
                        require(call.receiver().getClass().getField("derived").getInt(call.receiver()) == 0,
                                "declaring constructor return is before derived initialization");
                        derivedObservations.incrementAndGet();
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                }
            });
            Object derived = Class.forName(DERIVED, true, fixture.loader).newInstance();
            require(derivedObservations.get() == 1 && derived.getClass().getField("derived").getInt(derived) == 42,
                    "native derived initialization follows observed declaring return");
            try {
                saved.get().receiver();
                throw new AssertionError("constructor call escaped callback");
            } catch (HookFailure expected) {
                require(expected.reason.equals("hook_call_expired"), "constructor receiver view expires");
            }
            AtomicInteger synthetic = new AtomicInteger();
            fixture.subscribe("synthetic", call -> {
                require(call.argumentCount() == 2 && call.argument(0) == receiver && call.argument(1).equals(5),
                        "synthetic enclosing parameter is explicit in exact descriptor");
                synthetic.incrementAndGet();
            });
            Class.forName(INNER, true, fixture.loader).getConstructor(type, int.class).newInstance(receiver, 5);
            require(synthetic.get() == 1, "inner class constructor observed");
            AtomicInteger failures = new AtomicInteger();
            fixture.subscribe("failure", call -> {
                failures.incrementAndGet();
                throw new IllegalStateException("observer must not break construction");
            });
            require(create.newInstance(1) != null && create.newInstance(1) != null && failures.get() == 1,
                    "failed constructor observer disables itself and preserves native instances");
            NativeHookConstructedApiTest.run(fixture.preparation, fixture.mappings, type, fixture.definition("lua"));
        }
    }

    private static void overwrittenReceiverLocal() throws Exception {
        String source = "local r=betamoon.hooks:createRegistry(); " + declaration("saved", TARGET, "(I)V");
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            for (MethodNode method : node.methods) {
                if (method.name.equals("<init>") && method.desc.equals("(I)V")) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction.getOpcode() == Opcodes.RETURN) {
                            InsnList overwritten = new InsnList();
                            overwritten.add(new InsnNode(Opcodes.ACONST_NULL));
                            overwritten.add(new VarInsnNode(Opcodes.ASTORE, 0));
                            method.instructions.insertBefore(instruction, overwritten);
                        }
                    }
                }
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        }, BASE)) {
            Class<?> type = fixture.load();
            AtomicReference<Object> observed = new AtomicReference<>();
            fixture.subscribe("saved", call -> observed.set(call.receiver()));
            Object created = type.getConstructor(int.class).newInstance(3);
            require(observed.get() == created,
                    "constructor observes original initialized receiver even after local zero is overwritten");
        }
    }

    private static void uninitializedReturn() throws Exception {
        String source = "local r=betamoon.hooks:createRegistry(); " + declaration("bad", TARGET, "()V");
        try (HookTestHarness fixture = new HookTestHarness(TARGET, source, bytes -> {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            for (MethodNode method : node.methods) {
                if (method.name.equals("<init>") && method.desc.equals("()V")) {
                    method.instructions.clear();
                    method.instructions.add(new InsnNode(Opcodes.RETURN));
                    method.tryCatchBlocks.clear();
                    method.localVariables = null;
                }
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        })) {
            fixture.transformOnly();
            require(fixture.preparation.sites(TARGET.replace('.', '/')).get(0).status.equals("match_failed"),
                    "return before this initialization refused before class definition");
            require(Arrays.equals(fixture.input, fixture.delivered),
                    "unsafe constructor preserves original engine bytes atomically");
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
