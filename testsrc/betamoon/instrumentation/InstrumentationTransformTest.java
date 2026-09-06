package betamoon.instrumentation;

import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.ResolvedField;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.registry.BuiltinHookModules;
import betamoon.instrumentation.registry.ClassTransformPlan;
import betamoon.instrumentation.registry.HookRegistry;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.instrument.IllegalClassFormatException;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Standalone assertions for mapping and transformation behavior without JUnit. */
public final class InstrumentationTransformTest {
    private static final String PLAYER_CONTROLLER = "net/minecraft/src/PlayerController";
    private static final String MINECRAFT_DESCRIPTOR = "Lnet/minecraft/client/Minecraft;";
    private static final String BREAK_DESCRIPTOR = "(IIII)Z";
    private static final String PLACE_DESCRIPTOR =
        "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;Lnet/minecraft/src/ItemStack;IIII)Z";

    private InstrumentationTransformTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected paths to mappings.tiny and the runtime client JAR");
        }
        TinyMappingResolver mappings;
        FileInputStream input = new FileInputStream(args[0]);
        try {
            mappings = TinyMappingResolver.read(input);
        } finally {
            input.close();
        }

        verifyNamespace(mappings, RuntimeNamespace.NAMED);
        verifyNamespace(mappings, RuntimeNamespace.CLIENT);
        verifyRuntimeClient(mappings, args[1]);
        System.out.println("BetaMoon instrumentation transformation tests passed.");
    }

    private static void verifyNamespace(TinyMappingResolver mappings, RuntimeNamespace namespace) throws Exception {
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        BuiltinHookModules.registerAll(registry);
        Map<String, ClassTransformPlan> plans = registry.freeze(mappings,
            RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT);

        betamoon.instrumentation.api.ClassRef owner =
            new betamoon.instrumentation.api.ClassRef(PLAYER_CONTROLLER);
        ResolvedField mcField = mappings.resolveField(new betamoon.instrumentation.api.FieldRef(owner, "mc",
            MINECRAFT_DESCRIPTOR), namespace);
        ResolvedMethod broken = mappings.resolveMethod(new betamoon.instrumentation.api.MethodRef(owner,
            "sendBlockRemoved", BREAK_DESCRIPTOR), namespace);
        ResolvedMethod placed = mappings.resolveMethod(new betamoon.instrumentation.api.MethodRef(owner,
            "sendPlaceBlock", PLACE_DESCRIPTOR), namespace);

        byte[] fixture = createFixture(broken, placed, mcField);
        BetaMoonTransformer transformer = new BetaMoonTransformer(plans, mappings, report, true, false);
        byte[] transformed = transformer.transform(null, broken.getOwner(), null, null, fixture);
        require(transformed != null, "Transformer returned no bytes for " + namespace);
        require(countCallbackCalls(transformed, "beforeBlockBroken") == 1,
            "Missing block-break entry callback for " + namespace);
        require(countCallbackCalls(transformed, "afterBlockBroken") == 1,
            "Missing block-break return callback for " + namespace);
        require(countCallbackCalls(transformed, "beforeBlockPlaced") == 1,
            "Missing block-placement entry callback for " + namespace);
        require(countCallbackCalls(transformed, "afterBlockPlaced") == 1,
            "Missing block-placement return callback for " + namespace);

        List<HookDiagnostic> diagnostics = report.snapshot();
        require(diagnostics.size() == 2, "Expected two hook diagnostics");
        for (HookDiagnostic diagnostic : diagnostics) {
            require(diagnostic.getStatus() == HookStatus.APPLIED,
                diagnostic.getHookId() + " was not marked applied in " + namespace);
        }
    }

    private static void verifyRuntimeClient(TinyMappingResolver mappings, String clientJarPath) throws Exception {
        betamoon.instrumentation.api.ClassRef owner =
            new betamoon.instrumentation.api.ClassRef(PLAYER_CONTROLLER);
        String runtimeOwner = mappings.resolveClass(owner, RuntimeNamespace.CLIENT);
        ZipFile clientJar = new ZipFile(clientJarPath);
        byte[] original;
        try {
            ZipEntry entry = clientJar.getEntry(runtimeOwner + ".class");
            require(entry != null, "Runtime client does not contain " + runtimeOwner + ".class");
            InputStream input = clientJar.getInputStream(entry);
            try {
                original = readFully(input, (int) entry.getSize());
            } finally {
                input.close();
            }
        } finally {
            clientJar.close();
        }

        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        BuiltinHookModules.registerAll(registry);
        Map<String, ClassTransformPlan> plans = registry.freeze(mappings,
            RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT);
        BetaMoonTransformer transformer = new BetaMoonTransformer(plans, mappings, report, true, false);
        byte[] transformed = transformer.transform(null, runtimeOwner, null, null, original);
        require(transformed != null, "Runtime PlayerController was not transformed");
        require(countCallbackCalls(transformed, "beforeBlockBroken") == 1,
            "Runtime PlayerController is missing the block-break entry callback");
        require(countCallbackCalls(transformed, "afterBlockBroken") == 1,
            "Runtime PlayerController is missing the block-break return callback");
        require(countCallbackCalls(transformed, "beforeBlockPlaced") == 1,
            "Runtime PlayerController is missing the placement entry callback");
        int placementReturns = countCallbackCalls(transformed, "afterBlockPlaced");
        require(placementReturns == 5,
            "Expected five instrumented runtime placement return paths, found " + placementReturns);
        require(transformer.transform(null, runtimeOwner, null, null, transformed) == null,
            "Applying the same hooks twice must be a no-op");

        byte[] partial = removeFirstCallbackCall(transformed, "afterBlockBroken");
        PrintStream originalError = System.err;
        try {
            System.setErr(new PrintStream(new java.io.ByteArrayOutputStream()));
            try {
                transformer.transform(null, runtimeOwner, null, null, partial);
                throw new AssertionError("Partial instrumentation must be rejected");
            } catch (IllegalClassFormatException expected) {
                require(expected.getMessage().contains("partial or conflicting instrumentation"),
                    "Unexpected partial-instrumentation failure: " + expected.getMessage());
            }
        } finally {
            System.setErr(originalError);
        }
    }

    private static byte[] createFixture(ResolvedMethod broken, ResolvedMethod placed, ResolvedField mcField) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_6;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = broken.getOwner();
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, mcField.getName(),
            mcField.getDescriptor(), null, null));
        node.methods.add(booleanMethod(broken));
        node.methods.add(booleanMethod(placed));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode booleanMethod(ResolvedMethod method) {
        MethodNode node = new MethodNode(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, null);
        node.instructions.add(new InsnNode(Opcodes.ICONST_1));
        node.instructions.add(new InsnNode(Opcodes.IRETURN));
        return node;
    }

    private static int countCallbackCalls(byte[] bytecode, String methodName) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC && methodName.equals(call.name)
                        && call.owner.startsWith("betamoon/instrumentation/hooks/block/")) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static byte[] removeFirstCallbackCall(byte[] bytecode, String methodName) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (methodName.equals(call.name)
                        && call.owner.startsWith("betamoon/instrumentation/hooks/block/")) {
                        method.instructions.remove(call);
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        node.accept(writer);
                        return writer.toByteArray();
                    }
                }
            }
        }
        throw new AssertionError("Callback not found: " + methodName);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static byte[] readFully(InputStream input, int initialSize) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(Math.max(32, initialSize));
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
