package betamoon.instrumentation;

import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.MethodRef;
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

/**
 * Standalone assertions for mapping and transformation behavior without JUnit.
 */
public final class InstrumentationTransformTest {
    private static final String PLAYER_CONTROLLER = "net/minecraft/src/PlayerController";
    private static final String MINECRAFT_DESCRIPTOR = "Lnet/minecraft/client/Minecraft;";
    private static final String BREAK_DESCRIPTOR = "(IIII)Z";
    private static final String PLACE_DESCRIPTOR = "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;Lnet/minecraft/src/ItemStack;IIII)Z";

    private InstrumentationTransformTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected paths to mappings.tiny, runtime client JAR, and runtime server JAR");
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
        verifyRuntimeServer(mappings, args[2]);
        verifyEarlyReturnExecution(mappings);
        verifyFrozenRedirectAndNoMatch(mappings);
        System.out.println("BetaMoon instrumentation transformation tests passed.");
    }

    public static int captureDecision(int decision) {
        return decision;
    }

    public static boolean completeDecision(boolean original, int decision) {
        return decision == 0 ? original : decision > 0;
    }

    public static String completeObjectDecision(Object original, int decision) {
        return decision == 0 ? (String) original : "replacement";
    }

    private static void verifyEarlyReturnExecution(TinyMappingResolver mappings) throws Exception {
        String targetName = "fixture/GuardedAction";
        String callbackOwner = "betamoon/instrumentation/InstrumentationTransformTest";
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        registry.register(betamoon.instrumentation.api.AroundHookDefinition
                .builder("test:guard",
                        new betamoon.instrumentation.api.MethodRef(
                                new betamoon.instrumentation.api.ClassRef(targetName), "run", "(I)Z"))
                .capture(betamoon.instrumentation.api.HandlerRef.of(callbackOwner, "captureDecision", "(I)I"),
                        betamoon.instrumentation.api.ValueBinding.argument(0))
                .onReturn(betamoon.instrumentation.api.HandlerRef.of(callbackOwner, "completeDecision", "(ZI)Z"),
                        betamoon.instrumentation.api.ValueBinding.returnValue(),
                        betamoon.instrumentation.api.ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
        BetaMoonTransformer transformer = new BetaMoonTransformer(registry.freeze(mappings, RuntimeNamespace.NAMED),
                mappings, report, true, false);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, targetName, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        org.objectweb.asm.MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run",
                "(I)Z", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, targetName, "calls", "I");
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD);
        method.visitFieldInsn(Opcodes.PUTSTATIC, targetName, "calls", "I");
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        final byte[] transformed = transformer.transform(null, targetName, null, null, writer.toByteArray());
        class Loader extends ClassLoader {
            Class<?> loadFixture() {
                return defineClass("fixture.GuardedAction", transformed, 0, transformed.length);
            }
        }
        Class<?> fixture = new Loader().loadFixture();
        java.lang.reflect.Method run = fixture.getMethod("run", int.class);
        require(Boolean.TRUE.equals(run.invoke(null, 0)), "Pass did not execute original method");
        require(Boolean.FALSE.equals(run.invoke(null, -1)), "Deny did not return false");
        require(Boolean.TRUE.equals(run.invoke(null, 1)), "Handled did not return true");
        require(fixture.getField("calls").getInt(null) == 1, "An intercepted call executed original side effects");
        require(transformer.transform(null, targetName, null, null, transformed) == null, "Guard was not idempotent");
        require(report.snapshot().get(0).getStatus() == HookStatus.ALREADY_APPLIED,
                "An idempotent transform must be reported separately from a new application");
        verifyObjectEarlyReturn(mappings);
    }

    private static void verifyObjectEarlyReturn(TinyMappingResolver mappings) throws Exception {
        String targetName = "fixture/GuardedObjectAction";
        String callbackOwner = "betamoon/instrumentation/InstrumentationTransformTest";
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        registry.register(betamoon.instrumentation.api.AroundHookDefinition
                .builder("test:object_guard",
                        new betamoon.instrumentation.api.MethodRef(
                                new betamoon.instrumentation.api.ClassRef(targetName), "run",
                                "(I)Ljava/lang/String;"))
                .capture(betamoon.instrumentation.api.HandlerRef.of(callbackOwner, "captureDecision", "(I)I"),
                        betamoon.instrumentation.api.ValueBinding.argument(0))
                .onReturn(betamoon.instrumentation.api.HandlerRef.of(callbackOwner, "completeObjectDecision",
                                "(Ljava/lang/Object;I)Ljava/lang/String;"),
                        betamoon.instrumentation.api.ValueBinding.returnValue(),
                        betamoon.instrumentation.api.ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
        BetaMoonTransformer transformer = new BetaMoonTransformer(registry.freeze(mappings, RuntimeNamespace.NAMED),
                mappings, report, true, false);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, targetName, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        org.objectweb.asm.MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run",
                "(I)Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, targetName, "calls", "I");
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD);
        method.visitFieldInsn(Opcodes.PUTSTATIC, targetName, "calls", "I");
        method.visitLdcInsn("original");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        final byte[] transformed = transformer.transform(null, targetName, null, null, writer.toByteArray());
        class Loader extends ClassLoader {
            Class<?> loadFixture() {
                return defineClass("fixture.GuardedObjectAction", transformed, 0, transformed.length);
            }
        }
        Class<?> fixture = new Loader().loadFixture();
        java.lang.reflect.Method run = fixture.getMethod("run", int.class);
        require("original".equals(run.invoke(null, 0)), "Object pass did not execute original method");
        require("replacement".equals(run.invoke(null, 1)), "Object early return was not replaced");
        require(fixture.getField("calls").getInt(null) == 1,
                "An object-return interception executed original side effects");
    }

    private static void verifyFrozenRedirectAndNoMatch(TinyMappingResolver mappings) throws Exception {
        ClassRef targetOwner = new ClassRef("fixture/NoRedirectTarget");
        MethodRef target = new MethodRef(targetOwner, "run", "()V");
        MethodRef invocation = new MethodRef(new ClassRef("fixture/RedirectService"), "call", "()V");
        HandlerRef handler = HandlerRef.of("fixture/RedirectCallbacks", "call", "(Lfixture/RedirectService;)V");
        CallRedirectHookDefinition singleMethod = new CallRedirectHookDefinition("test:no_redirect", target, invocation,
                handler);
        CallRedirectHookDefinition allMethods = singleMethod.inAllMethods();
        require(singleMethod != allMethods && !singleMethod.appliesToAllMethods() && allMethods.appliesToAllMethods(),
                "Selecting all-method redirection must return a new immutable definition");
        require(allMethods.inAllMethods() == allMethods, "Repeating an immutable all-method selection must be stable");

        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        registry.register(allMethods);
        Map<String, ClassTransformPlan> plans = registry.freeze(mappings, RuntimeNamespace.NAMED);
        ClassTransformPlan plan = plans.get(targetOwner.getInternalName());
        require(plan != null && plan.getHooks().size() == 1, "Frozen redirect plan is missing its hook");
        expectUnsupported(() -> plan.getHooks().get(0).getNamespaces().clear(),
                "Frozen hook namespaces must be immutable");

        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, targetOwner.getInternalName(), null, "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        BetaMoonTransformer transformer = new BetaMoonTransformer(plans, mappings, report, true, false);
        require(transformer.transform(null, targetOwner.getInternalName(), null, null, writer.toByteArray()) == null,
                "An optional all-method redirect without a call site must leave the class unchanged");
        require(report.snapshot().get(0).getStatus() == HookStatus.NO_MATCH,
                "An absent optional redirect must be distinguished from applied and idempotent hooks");
    }

    private static void expectUnsupported(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (UnsupportedOperationException expected) {
            // Expected immutable view.
        }
    }

    private static void verifyNamespace(TinyMappingResolver mappings, RuntimeNamespace namespace) throws Exception {
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        BuiltinHookModules.registerAll(registry);
        Map<String, ClassTransformPlan> plans = registry.freeze(mappings, RuntimeNamespace.NAMED,
                RuntimeNamespace.CLIENT);

        betamoon.instrumentation.api.ClassRef owner = new betamoon.instrumentation.api.ClassRef(PLAYER_CONTROLLER);
        ResolvedField mcField = mappings
                .resolveField(new betamoon.instrumentation.api.FieldRef(owner, "mc", MINECRAFT_DESCRIPTOR), namespace);
        ResolvedMethod broken = mappings.resolveMethod(
                new betamoon.instrumentation.api.MethodRef(owner, "sendBlockRemoved", BREAK_DESCRIPTOR), namespace);
        ResolvedMethod placed = mappings.resolveMethod(
                new betamoon.instrumentation.api.MethodRef(owner, "sendPlaceBlock", PLACE_DESCRIPTOR), namespace);

        byte[] fixture = createFixture(broken, placed, mcField, mappings, namespace);
        BetaMoonTransformer transformer = new BetaMoonTransformer(plans, mappings, report, true, false);
        byte[] transformed = transformer.transform(null, broken.getOwner(), null, null, fixture);
        require(transformed != null, "Transformer returned no bytes for " + namespace);
        require(countCallbackCalls(transformed, "beforeBlockBroken") == 1,
                "Missing block-break entry callback for " + namespace);
        require(countCallbackCalls(transformed, "afterBlockBroken") == 1,
                "Missing block-break return callback for " + namespace);
        require(countCallbackCalls(transformed, "beforeBlockPlaced") == 1,
                "Missing block-placement entry callback for " + namespace);
        require(countCallbackCalls(transformed, "afterBlockPlaced") == 2,
                "Missing block-placement return callback for " + namespace);

        List<HookDiagnostic> diagnostics = report.snapshot();
        require(diagnostics.size() > 10, "Expected content callback hook diagnostics");
        for (HookDiagnostic diagnostic : diagnostics) {
            if (diagnostic.getHookId().startsWith("betamoon:content_callback_overrides")) {
                require(diagnostic.getStatus() != HookStatus.FAILED, "Content callback transform failed");
                continue;
            }
            HookStatus expected = (diagnostic.getHookId().startsWith("betamoon:lua_texture_resource")
                    || diagnostic.getHookId().startsWith("betamoon:model_render")
                    || diagnostic.getHookId().startsWith("betamoon:entity_lifecycle")
                    || diagnostic.getHookId().startsWith("betamoon:entity_natural_spawn")
                    || diagnostic.getHookId().equals("betamoon:block_break_guard")
                    || diagnostic.getHookId().equals("betamoon:block_power")
                    || diagnostic.getHookId().equals("betamoon:block_display_tick")
                    || diagnostic.getHookId().equals("betamoon:item_harvest")
                    || diagnostic.getHookId().equals("betamoon:furnace_fuel"))
                            ? HookStatus.WAITING_FOR_TARGET
                            : HookStatus.APPLIED;
            require(diagnostic.getStatus() == expected,
                    diagnostic.getHookId() + " has an unexpected status in " + namespace);
        }
    }

    private static void verifyRuntimeClient(TinyMappingResolver mappings, String clientJarPath) throws Exception {
        betamoon.instrumentation.api.ClassRef owner = new betamoon.instrumentation.api.ClassRef(PLAYER_CONTROLLER);
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
        Map<String, ClassTransformPlan> plans = registry.freeze(mappings, RuntimeNamespace.NAMED,
                RuntimeNamespace.CLIENT);
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
        require(placementReturns == 6,
                "Expected six instrumented runtime placement return paths, found " + placementReturns);
        require(transformer.transform(null, runtimeOwner, null, null, transformed) == null,
                "Applying the same hooks twice must be a no-op");

        betamoon.instrumentation.api.ClassRef renderEngine = new betamoon.instrumentation.api.ClassRef(
                "net/minecraft/src/RenderEngine");
        String renderEngineOwner = mappings.resolveClass(renderEngine, RuntimeNamespace.CLIENT);
        byte[] renderEngineOriginal = readClass(clientJarPath, renderEngineOwner);
        byte[] renderEngineTransformed = transformer.transform(null, renderEngineOwner, null, null,
                renderEngineOriginal);
        require(renderEngineTransformed != null, "Runtime RenderEngine was not transformed");
        require(countCallbackCalls(renderEngineTransformed, "openTexture") >= 12,
                "Runtime RenderEngine must resolve virtual resources in initial loads and refresh paths");
        require(countCallbackCalls(renderEngineTransformed, "beforeRefresh") == 1,
                "Runtime RenderEngine is missing the asset refresh entry callback");
        require(countCallbackCalls(renderEngineTransformed, "uploadLuaTexture") == 0,
                "Cached texture lookups must not upload Lua textures again");

        String survivalOwner = mappings.resolveClass(
                new betamoon.instrumentation.api.ClassRef("net/minecraft/src/PlayerControllerSP"),
                RuntimeNamespace.CLIENT);
        byte[] survival = transformer.transform(null, survivalOwner, null, null,
                readClass(clientJarPath, survivalOwner));
        require(survival != null && countCallbackCalls(survival, "before") == 1, "Missing survival break guard");
        require(transformer.transform(null, survivalOwner, null, null, survival) == null,
                "Survival guard must be idempotent");

        String[][] additionalTargets = {{"net/minecraft/src/World", "emission"}, {"forge/ForgeHooks", "permission"},
                {"net/minecraft/src/ItemRenderer", "held"}, {"net/minecraft/src/RenderItem", "gui"},
                {"net/minecraft/src/EntityRenderer", "frame"}, {"net/minecraft/src/Chunk", "chunkLoaded"},
                {"net/minecraft/src/RenderGlobal", "worldModels"},
                {"net/minecraft/src/TileEntityFurnace", "result"},
                {"net/minecraft/src/SpawnerAnimals", "after"}};
        for (String[] target : additionalTargets) {
            String targetOwner = mappings.resolveClass(new betamoon.instrumentation.api.ClassRef(target[0]),
                    RuntimeNamespace.CLIENT);
            byte[] result = transformer.transform(null, targetOwner, null, null, readClass(clientJarPath, targetOwner));
            int expectedCallbacks = target[0].equals("net/minecraft/src/RenderGlobal") ? 2
                    : target[0].equals("net/minecraft/src/SpawnerAnimals") ? 3
                            : target[0].equals("net/minecraft/src/TileEntityFurnace") ? 7 : 1;
            int callbackCount = result == null ? 0 : countCallbackCalls(result, target[1]);
            require(result != null && callbackCount == expectedCallbacks,
                    "Missing runtime hook for " + target[0] + " (transformed=" + (result != null)
                            + ", callbacks=" + callbackCount + ")");
            if (target[0].equals("net/minecraft/src/Chunk")) {
                require(countCallbackCalls(result, "chunkUnloaded") == 2,
                        "Missing model or entity chunk unload bridge");
                require(countCallbackCalls(result, "chunkChanged") >= 2, "Missing chunk edit bridges");
            }
            if (target[0].equals("net/minecraft/src/RenderItem")) {
                require(countCallbackCalls(result, "ground") == 1, "Missing dropped model render callback");
            }
            if (target[0].equals("net/minecraft/src/World")) {
                ClassNode world = new ClassNode();
                new ClassReader(result).accept(world, 0);
                int redirects = 0;
                for (MethodNode method : world.methods) {
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode) instruction;
                            if (call.owner.equals("betamoon/luaapi/block/BlockDisplayTickOverrides")
                                    && call.name.equals("display")) {
                                redirects++;
                            }
                        }
                    }
                }
                require(redirects == 1, "Missing obfuscated world display dispatch redirect");
            }
            require(transformer.transform(null, targetOwner, null, null, result) == null,
                    "Runtime hook must be idempotent: " + target[0]);
        }

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

    private static void verifyRuntimeServer(TinyMappingResolver mappings, String serverJarPath) throws Exception {
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        BuiltinHookModules.registerForSide(registry, betamoon.runtime.RuntimeSide.DEDICATED_SERVER);
        Map<String, ClassTransformPlan> plans = registry.freeze(mappings, RuntimeNamespace.NAMED,
                RuntimeNamespace.SERVER);
        BetaMoonTransformer transformer = new BetaMoonTransformer(plans, mappings, report, true, false);

        String tracker = mappings.resolveClass(new ClassRef("net/minecraft/src/EntityTracker"),
                RuntimeNamespace.SERVER);
        byte[] transformedTracker = transformer.transform(null, tracker, null, null,
                readClass(serverJarPath, tracker));
        require(transformedTracker != null && countCallbackCalls(transformedTracker, "track") == 1,
                "Runtime server EntityTracker is missing custom registration");

        String entry = mappings.resolveClass(new ClassRef("net/minecraft/src/EntityTrackerEntry"),
                RuntimeNamespace.SERVER);
        byte[] transformedEntry = transformer.transform(null, entry, null, null,
                readClass(serverJarPath, entry));
        require(transformedEntry != null && countCallbackCalls(transformedEntry, "custom") == 1
                && countCallbackCalls(transformedEntry, "spawn") >= 2,
                "Runtime server EntityTrackerEntry is missing custom spawn selection");
    }

    private static byte[] readClass(String jarPath, String owner) throws Exception {
        ZipFile jar = new ZipFile(jarPath);
        try {
            ZipEntry entry = jar.getEntry(owner + ".class");
            require(entry != null, "Runtime JAR does not contain " + owner + ".class");
            InputStream input = jar.getInputStream(entry);
            try {
                return readFully(input, (int) entry.getSize());
            } finally {
                input.close();
            }
        } finally {
            jar.close();
        }
    }

    private static byte[] createFixture(ResolvedMethod broken, ResolvedMethod placed, ResolvedField mcField,
            TinyMappingResolver mappings, RuntimeNamespace namespace) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_6;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = broken.getOwner();
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, mcField.getName(),
                mcField.getDescriptor(), null, null));
        node.methods.add(booleanMethod(broken));
        node.methods.add(booleanMethod(placed));
        betamoon.instrumentation.api.ClassRef controller = new betamoon.instrumentation.api.ClassRef(PLAYER_CONTROLLER);
        ResolvedMethod use = mappings.resolveMethod(
                new betamoon.instrumentation.api.MethodRef(controller, "sendUseItem",
                        "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;Lnet/minecraft/src/ItemStack;)Z"),
                namespace);
        node.methods.add(booleanMethod(use));
        ResolvedMethod entity = mappings.resolveMethod(new betamoon.instrumentation.api.MethodRef(controller,
                "interactWithEntity", "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/Entity;)V"), namespace);
        MethodNode entityMethod = new MethodNode(Opcodes.ACC_PUBLIC, entity.getName(), entity.getDescriptor(), null,
                null);
        entityMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(entityMethod);
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
            for (AbstractInsnNode instruction = method.instructions
                    .getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC && methodName.equals(call.name)
                            && call.owner.startsWith("betamoon/instrumentation/hooks/")) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countReturns(byte[] bytecode, ResolvedMethod target) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!target.getName().equals(method.name) || !target.getDescriptor().equals(method.desc)) {
                continue;
            }
            int count = 0;
            for (AbstractInsnNode instruction = method.instructions
                    .getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() == Opcodes.IRETURN) {
                    count++;
                }
            }
            return count;
        }
        return 0;
    }

    private static byte[] removeFirstCallbackCall(byte[] bytecode, String methodName) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions
                    .getFirst(); instruction != null; instruction = instruction.getNext()) {
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
