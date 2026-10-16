package betamoon.instrumentation;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;
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
import betamoon.runtime.RuntimeSide;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
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
    private static final String PLACE_DESCRIPTOR = "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;"
            + "Lnet/minecraft/src/ItemStack;IIII)Z";

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
        verifyExceptionalCleanup(mappings);
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

    private static int cleanupCalls;
    private static boolean cleanupThrows;

    public static int captureCleanup() {
        return 17;
    }

    public static void completeCleanup(int state) {
        require(state == 17, "Cleanup lost its captured value");
        cleanupCalls++;
        if (cleanupThrows) {
            throw new IllegalArgumentException("cleanup_failure");
        }
    }

    private static void verifyExceptionalCleanup(TinyMappingResolver mappings) throws Exception {
        String targetName = "fixture/ExceptionalAction";
        String callbacks = "betamoon/instrumentation/InstrumentationTransformTest";
        HookRegistry registry = new HookRegistry(new TransformationReport());
        registry.register(
                AroundHookDefinition.builder("test:cleanup", new MethodRef(new ClassRef(targetName), "run", "(Z)V"))
                        .capture(HandlerRef.of(callbacks, "captureCleanup", "()I"))
                        .onReturn(HandlerRef.of(callbacks, "completeCleanup", "(I)V"), ValueBinding.capturedValue())
                        .cleanupOnException().build());
        BetaMoonTransformer transformer = new BetaMoonTransformer(registry.freeze(mappings, RuntimeNamespace.NAMED),
                mappings, new TransformationReport(), true, false);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, targetName, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(Z)V", null, null);
        method.visitCode();
        Label normal = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IFEQ, normal);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("fixture_failure");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>",
                "(Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.ATHROW);
        method.visitLabel(normal);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        byte[] transformed = transformer.transform(null, targetName, null, null, writer.toByteArray());
        class Loader extends ClassLoader {
            Class<?> fixture() {
                return defineClass("fixture.ExceptionalAction", transformed, 0, transformed.length);
            }
        }
        Method run = new Loader().fixture().getMethod("run", boolean.class);
        cleanupCalls = 0;
        run.invoke(null, false);
        require(cleanupCalls == 1, "Normal exit must clean up once");
        for (boolean failingCleanup : new boolean[]{false, true}) {
            cleanupThrows = failingCleanup;
            cleanupCalls = 0;
            try {
                run.invoke(null, true);
                throw new AssertionError("Original exception was swallowed");
            } catch (InvocationTargetException expected) {
                Throwable original = expected.getCause();
                require(original instanceof IllegalStateException && "fixture_failure".equals(original.getMessage()),
                        "Cleanup must preserve the original failure");
                require(original.getSuppressed().length == (failingCleanup ? 1 : 0),
                        "Cleanup failures must be retained as suppressed exceptions");
                require(cleanupCalls == 1, "Exceptional exit must clean up once");
            }
        }
        cleanupThrows = false;
        require(transformer.transform(null, targetName, null, null, transformed) == null,
                "Exceptional cleanup must remain idempotent");
    }

    private static void verifyEarlyReturnExecution(TinyMappingResolver mappings) throws Exception {
        String targetName = "fixture/GuardedAction";
        String callbackOwner = "betamoon/instrumentation/InstrumentationTransformTest";
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        registry.register(
                AroundHookDefinition.builder("test:guard", new MethodRef(new ClassRef(targetName), "run", "(I)Z"))
                        .capture(HandlerRef.of(callbackOwner, "captureDecision", "(I)I"), ValueBinding.argument(0))
                        .onReturn(HandlerRef.of(callbackOwner, "completeDecision", "(ZI)Z"), ValueBinding.returnValue(),
                                ValueBinding.capturedValue())
                        .skipWhenCapturedNonZero().build());
        BetaMoonTransformer transformer = new BetaMoonTransformer(registry.freeze(mappings, RuntimeNamespace.NAMED),
                mappings, report, true, false);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, targetName, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(I)Z", null, null);
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
        Method run = fixture.getMethod("run", int.class);
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
        registry.register(AroundHookDefinition
                .builder("test:object_guard", new MethodRef(new ClassRef(targetName), "run", "(I)Ljava/lang/String;"))
                .capture(HandlerRef.of(callbackOwner, "captureDecision", "(I)I"), ValueBinding.argument(0))
                .onReturn(
                        HandlerRef.of(callbackOwner, "completeObjectDecision",
                                "(Ljava/lang/Object;I)Ljava/lang/String;"),
                        ValueBinding.returnValue(), ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
        BetaMoonTransformer transformer = new BetaMoonTransformer(registry.freeze(mappings, RuntimeNamespace.NAMED),
                mappings, report, true, false);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, targetName, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run",
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
        Method run = fixture.getMethod("run", int.class);
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
        ClassRef receiver = new ClassRef("fixture/RedirectSubtype");
        CallRedirectHookDefinition inherited = singleMethod.withInvocationOwner(receiver);
        require(inherited != singleMethod && inherited.getInvocationOwner() == receiver,
                "An inherited-call redirect must retain its explicit bytecode receiver owner");
        require(inherited.inAllMethods().getInvocationOwner() == receiver,
                "All-method selection must preserve an inherited-call receiver owner");
        CallRedirectHookDefinition staticCalls = singleMethod.staticInvocation().allCallsInTarget();
        require(staticCalls.isStaticInvocation() && staticCalls.redirectsAllCallsInTarget(),
                "Static and all-call redirect selections are retained");
        require(staticCalls.inAllMethods().isStaticInvocation()
                && staticCalls.inAllMethods().redirectsAllCallsInTarget(),
                "Immutable redirect selections compose without losing prior options");

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
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
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

        ClassRef owner = new ClassRef(PLAYER_CONTROLLER);
        ResolvedField mcField = mappings.resolveField(new FieldRef(owner, "mc", MINECRAFT_DESCRIPTOR), namespace);
        ResolvedMethod broken = mappings.resolveMethod(new MethodRef(owner, "sendBlockRemoved", BREAK_DESCRIPTOR),
                namespace);
        ResolvedMethod placed = mappings.resolveMethod(new MethodRef(owner, "sendPlaceBlock", PLACE_DESCRIPTOR),
                namespace);

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
                    || diagnostic.getHookId().startsWith("betamoon:client_control")
                    || diagnostic.getHookId().startsWith("betamoon:tick_events")
                    || diagnostic.getHookId().startsWith("betamoon:worldgen_surface")
                    || diagnostic.getHookId().startsWith("betamoon:fluid_ecology")
                    || diagnostic.getHookId().startsWith("betamoon:regional_structure_chunk")
                    || diagnostic.getHookId().equals("betamoon:fluid_interactions")
                    || diagnostic.getHookId().startsWith("betamoon:fluid_contact")
                    || diagnostic.getHookId().startsWith("betamoon:fluid_camera")
                    || diagnostic.getHookId().startsWith("betamoon:particles")
                    || diagnostic.getHookId().startsWith("betamoon:block_schedule")
                    || diagnostic.getHookId().startsWith("betamoon:multiblock")
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
        ClassRef owner = new ClassRef(PLAYER_CONTROLLER);
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

        ClassRef renderEngine = new ClassRef("net/minecraft/src/RenderEngine");
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

        String effectsOwner = mappings.resolveClass(new ClassRef("net/minecraft/src/EffectRenderer"),
                RuntimeNamespace.CLIENT);
        byte[] effects = transformer.transform(null, effectsOwner, null, null, readClass(clientJarPath, effectsOwner));
        require(effects != null && countParticleCallbacks(effects, "tick") == 1
                && countParticleCallbacks(effects, "clear") == 1 && countParticleCallbacks(effects, "render") == 1,
                "Runtime particle renderer must contain one custom tick, clear, and render callback");
        require(transformer.transform(null, effectsOwner, null, null, effects) == null,
                "Particle renderer hooks must be idempotent");
        String globalOwner = mappings.resolveClass(new ClassRef("net/minecraft/src/RenderGlobal"),
                RuntimeNamespace.CLIENT);
        byte[] global = transformer.transform(null, globalOwner, null, null, readClass(clientJarPath, globalOwner));
        require(global != null && countParticleCallbacks(global, "spawn") == 1,
                "Runtime particle dispatch must contain exactly one custom-key interception");

        String survivalOwner = mappings.resolveClass(new ClassRef("net/minecraft/src/PlayerControllerSP"),
                RuntimeNamespace.CLIENT);
        byte[] survival = transformer.transform(null, survivalOwner, null, null,
                readClass(clientJarPath, survivalOwner));
        require(survival != null && countCallbackCalls(survival, "before") == 1, "Missing survival break guard");
        require(transformer.transform(null, survivalOwner, null, null, survival) == null,
                "Survival guard must be idempotent");

        verifyClientControlSeams(mappings, clientJarPath, transformer);
        verifyFluidInteractionSeam(mappings, clientJarPath, transformer, RuntimeNamespace.CLIENT);
        verifyFluidContactSeams(mappings, clientJarPath, transformer, RuntimeNamespace.CLIENT);
        verifyEcologySeams(mappings, clientJarPath, transformer, RuntimeNamespace.CLIENT);
        verifyFluidCameraSeams(mappings, clientJarPath, transformer);

        String[][] additionalTargets = {{"net/minecraft/src/World", "emission"}, {"forge/ForgeHooks", "permission"},
                {"net/minecraft/src/ItemRenderer", "held"}, {"net/minecraft/src/RenderItem", "gui"},
                {"net/minecraft/src/EntityRenderer", "frame"}, {"net/minecraft/src/Chunk", "chunkLoaded"},
                {"net/minecraft/src/RenderGlobal", "worldModels"}, {"net/minecraft/src/TileEntityFurnace", "result"},
                {"net/minecraft/src/SpawnerAnimals", "after"}};
        for (String[] target : additionalTargets) {
            String targetOwner = mappings.resolveClass(new ClassRef(target[0]), RuntimeNamespace.CLIENT);
            byte[] result = transformer.transform(null, targetOwner, null, null, readClass(clientJarPath, targetOwner));
            int expectedCallbacks = (target[0].equals("net/minecraft/src/RenderGlobal")
                    || target[0].equals("net/minecraft/src/Chunk"))
                            ? 2
                            : target[0].equals("net/minecraft/src/SpawnerAnimals")
                                    ? 3
                                    : target[0].equals("net/minecraft/src/TileEntityFurnace") ? 7 : 1;
            int callbackCount = result == null ? 0 : countCallbackCalls(result, target[1]);
            require(result != null && callbackCount == expectedCallbacks, "Missing runtime hook for " + target[0]
                    + " (transformed=" + (result != null) + ", callbacks=" + callbackCount + ")");
            if (target[0].equals("net/minecraft/src/Chunk")) {
                require(countScheduleCallbacks(result, "loaded") == 1
                        && countScheduleCallbacks(result, "unloaded") == 1,
                        "Missing repeating block schedule chunk bridges");
                require(countMultiblockCallbacks(result, "id") == 1 && countMultiblockCallbacks(result, "state") == 1
                        && countMultiblockCallbacks(result, "metadata") == 1
                        && countMultiblockCallbacks(result, "changed") >= 3
                        && countMultiblockCallbacks(result, "loaded") == 1
                        && countMultiblockCallbacks(result, "unloaded") == 1,
                        "Missing native multiblock mutation and availability observers");
                require(countCallbackCalls(result, "chunkUnloaded") == 2,
                        "Missing model or entity chunk unload bridge");
                require(countCallbackCalls(result, "chunkChanged") >= 2, "Missing chunk edit bridges");
            }
            if (target[0].equals("net/minecraft/src/RenderItem")) {
                require(countCallbackCalls(result, "ground") == 1, "Missing dropped model render callback");
            }
            if (target[0].equals("net/minecraft/src/World")) {
                require(countScheduleCallbacks(result, "retry") == 1,
                        "Native scheduled update processing must retry pending chunk admission");
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
            System.setErr(new PrintStream(new ByteArrayOutputStream()));
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

    private static void verifyClientControlSeams(TinyMappingResolver mappings, String clientJarPath,
            BetaMoonTransformer transformer) throws Exception {
        byte[] options = transformControlClass(mappings, clientJarPath, transformer, "net/minecraft/src/GuiOptions");
        MethodRef optionsControls = new MethodRef(new ClassRef("net/minecraft/src/GuiScreen"), "actionPerformed",
                "(Lnet/minecraft/src/GuiButton;)V").implementedBy(new ClassRef("net/minecraft/src/GuiOptions"));
        assertAroundCallbacks(mappings, options, optionsControls, "beforeOptionsControls", "afterOptionsControls");

        byte[] minecraft = transformControlClass(mappings, clientJarPath, transformer,
                "net/minecraft/client/Minecraft");
        assertAroundCallbacks(mappings, minecraft, "net/minecraft/client/Minecraft", "runTick", "()V", "beginInputTick",
                "finishInputTick");
        assertAroundCallbacks(mappings, minecraft, "net/minecraft/client/Minecraft", "runTick", "()V", "begin",
                "completed");
        require(countCallbackCalls(minecraft, "advanceWorld") == 1,
                "The native pause-guarded world update must use exactly one logical tick bridge");
        assertAroundCallbacks(mappings, minecraft, "net/minecraft/client/Minecraft", "clickMouse", "(I)V",
                "beforeAction", "afterAction");
        assertAroundCallbacks(mappings, minecraft, "net/minecraft/client/Minecraft", "func_6254_a", "(IZ)V",
                "beforeHeldBreaking", "afterHeldBreaking");
        require(countCallbackCalls(minecraft, "nextKeyboardEvent") == 1
                && countCallbackCalls(minecraft, "nextMouseEvent") == 1
                && countCallbackCalls(minecraft, "keyboardEventState") == 2
                && countCallbackCalls(minecraft, "mouseEventButtonState") == 4
                && countCallbackCalls(minecraft, "mouseEventWheel") == 1,
                "Queued gameplay input calls were not redirected exactly");

        byte[] player = transformControlClass(mappings, clientJarPath, transformer, "net/minecraft/src/EntityPlayerSP");
        assertAroundCallbacks(mappings, player, "net/minecraft/src/EntityPlayerSP", "handleKeyPress", "(IZ)V",
                "beforeMovementKey", "afterMovementKey");

        byte[] movement = transformControlClass(mappings, clientJarPath, transformer,
                "net/minecraft/src/MovementInputFromOptions");
        MethodRef updateMovement = new MethodRef(new ClassRef("net/minecraft/src/MovementInput"),
                "updatePlayerMoveState", "(Lnet/minecraft/src/EntityPlayer;)V")
                .implementedBy(new ClassRef("net/minecraft/src/MovementInputFromOptions"));
        assertAroundCallbacks(mappings, movement, updateMovement, "enterMovementIntent", "finishMovementIntent");

        byte[] renderer = transformControlClass(mappings, clientJarPath, transformer,
                "net/minecraft/src/EntityRenderer");
        require(countCallbackCalls(renderer, "applyLook") == 1,
                "The local-player look call was not redirected exactly once");
        assertAroundCallbacks(mappings, renderer, "net/minecraft/src/EntityRenderer", "getMouseOver", "(F)V",
                "beforeTargeting", "afterTargeting");
        assertAroundCallbacks(mappings, renderer, "net/minecraft/src/EntityRenderer", "orientCamera", "(F)V",
                "beforeCamera", "afterCamera");
        assertAroundCallbacks(mappings, renderer, "net/minecraft/src/EntityRenderer", "setupCameraTransform", "(FI)V",
                "beforeProjection", "afterProjection");

        byte[] gui = transformControlClass(mappings, clientJarPath, transformer, "net/minecraft/src/GuiScreen");
        require(countCallbackCalls(gui, "nextKeyboardEvent") == 1 && countCallbackCalls(gui, "nextMouseEvent") == 1
                && countCallbackCalls(gui, "keyboardEventState") == 1
                && countCallbackCalls(gui, "mouseEventButtonState") == 1,
                "Queued GUI input calls were not redirected exactly");
        assertAroundCallbacks(mappings, gui, "net/minecraft/src/GuiScreen", "handleKeyboardInput", "()V",
                "beforeGuiKeyboardInput", "afterGuiKeyboardInput");
        assertAroundCallbacks(mappings, gui, "net/minecraft/src/GuiScreen", "handleMouseInput", "()V",
                "beforeGuiMouseInput", "afterGuiMouseInput");
    }

    private static byte[] transformControlClass(TinyMappingResolver mappings, String clientJarPath,
            BetaMoonTransformer transformer, String namedOwner) throws Exception {
        String runtimeOwner = mappings.resolveClass(new ClassRef(namedOwner), RuntimeNamespace.CLIENT);
        byte[] transformed = transformer.transform(null, runtimeOwner, null, null,
                readClass(clientJarPath, runtimeOwner));
        require(transformed != null, "Client control seam did not transform " + namedOwner);
        require(transformer.transform(null, runtimeOwner, null, null, transformed) == null,
                "Client control seam must be idempotent: " + namedOwner);
        return transformed;
    }

    private static void assertAroundCallbacks(TinyMappingResolver mappings, byte[] transformed, String namedOwner,
            String methodName, String descriptor, String entryCallback, String returnCallback) {
        assertAroundCallbacks(mappings, transformed, new MethodRef(new ClassRef(namedOwner), methodName, descriptor),
                entryCallback, returnCallback);
    }

    private static void assertAroundCallbacks(TinyMappingResolver mappings, byte[] transformed, MethodRef method,
            String entryCallback, String returnCallback) {
        ResolvedMethod target = mappings.resolveMethod(method, RuntimeNamespace.CLIENT);
        require(countCallbackCalls(transformed, entryCallback) == 1,
                "Client control seam " + entryCallback + " did not match exactly once in " + target);
        int returns = countReturns(transformed, target);
        require(returns > 0 && countCallbackCalls(transformed, returnCallback) == returns,
                "Client control seam " + returnCallback + " did not cover every return in " + target);
    }

    private static void verifyRuntimeServer(TinyMappingResolver mappings, String serverJarPath) throws Exception {
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        BuiltinHookModules.registerForSide(registry, RuntimeSide.DEDICATED_SERVER);
        Map<String, ClassTransformPlan> plans = registry.freeze(mappings, RuntimeNamespace.NAMED,
                RuntimeNamespace.SERVER);
        BetaMoonTransformer transformer = new BetaMoonTransformer(plans, mappings, report, true, false);

        String tracker = mappings.resolveClass(new ClassRef("net/minecraft/src/EntityTracker"),
                RuntimeNamespace.SERVER);
        byte[] transformedTracker = transformer.transform(null, tracker, null, null, readClass(serverJarPath, tracker));
        require(transformedTracker != null && countCallbackCalls(transformedTracker, "track") == 1,
                "Runtime server EntityTracker is missing custom registration");

        String entry = mappings.resolveClass(new ClassRef("net/minecraft/src/EntityTrackerEntry"),
                RuntimeNamespace.SERVER);
        byte[] transformedEntry = transformer.transform(null, entry, null, null, readClass(serverJarPath, entry));
        require(transformedEntry != null && countCallbackCalls(transformedEntry, "custom") == 1
                && countCallbackCalls(transformedEntry, "spawn") >= 2,
                "Runtime server EntityTrackerEntry is missing custom spawn selection");
        verifyFluidInteractionSeam(mappings, serverJarPath, transformer, RuntimeNamespace.SERVER);
        verifyFluidContactSeams(mappings, serverJarPath, transformer, RuntimeNamespace.SERVER);
        verifyEcologySeams(mappings, serverJarPath, transformer, RuntimeNamespace.SERVER);
    }

    private static void verifyEcologySeams(TinyMappingResolver mappings, String jarPath,
            BetaMoonTransformer transformer, RuntimeNamespace namespace) throws Exception {
        for (String name : new String[]{"BlockSand", "BlockFarmland", "BlockReed", "BlockSponge", "EntityBoat",
                "Pathfinder", "SpawnerAnimals"}) {
            String owner = mappings.resolveClass(new ClassRef("net/minecraft/src/" + name), namespace);
            byte[] transformed = transformer.transform(null, owner, null, null, readClass(jarPath, owner));
            require(transformed != null, "Missing ecology seam: " + namespace + ":" + name);
            require(transformer.transform(null, owner, null, null, transformed) == null,
                    "Ecology seams must be idempotent");
        }
    }

    private static void verifyFluidContactSeams(TinyMappingResolver mappings, String jarPath,
            BetaMoonTransformer transformer, RuntimeNamespace namespace) throws Exception {
        for (String namedOwner : new String[]{"net/minecraft/src/Entity", "net/minecraft/src/EntityLiving",
                "net/minecraft/src/EntityItem"}) {
            String owner = mappings.resolveClass(new ClassRef(namedOwner), namespace);
            byte[] transformed = transformer.transform(null, owner, null, null, readClass(jarPath, owner));
            require(transformed != null, "Fluid contact did not transform " + namedOwner + " on " + namespace);
            require(transformer.transform(null, owner, null, null, transformed) == null,
                    "Fluid contact must be idempotent for " + namedOwner + " on " + namespace);
            if (namedOwner.endsWith("EntityItem")) {
                require(countCallbackCalls(transformed, "itemWaterMovement") > 0,
                        "Dropped item water movement must use fluid identity on " + namespace);
            } else if (namedOwner.endsWith("EntityLiving")) {
                require(countCallbackCalls(transformed, "drowning") == 1,
                        "Living entity drowning must route through fluid identity on " + namespace);
            } else {
                require(countCallbackCalls(transformed, "waterMovement") > 0
                        && countCallbackCalls(transformed, "lavaMovement") > 0
                        && countCallbackCalls(transformed, "afterEntityTick") > 0,
                        "Entity movement and fire contact hooks are incomplete on " + namespace);
            }
        }
    }

    private static void verifyFluidCameraSeams(TinyMappingResolver mappings, String jarPath,
            BetaMoonTransformer transformer) throws Exception {
        String[] targets = {"net/minecraft/src/EntityRenderer", "net/minecraft/src/ItemRenderer",
                "net/minecraft/src/GuiIngame"};
        String[] callbacks = {"fogColor", "overlayVisible", "showAirHud"};
        for (int index = 0; index < targets.length; index++) {
            String owner = mappings.resolveClass(new ClassRef(targets[index]), RuntimeNamespace.CLIENT);
            byte[] transformed = transformer.transform(null, owner, null, null, readClass(jarPath, owner));
            require(transformed != null && countCallbackCalls(transformed, callbacks[index]) > 0,
                    "Fluid camera hook " + callbacks[index] + " did not transform " + targets[index]);
            require(transformer.transform(null, owner, null, null, transformed) == null,
                    "Fluid camera hooks must be idempotent for " + targets[index]);
        }
    }

    private static void verifyFluidInteractionSeam(TinyMappingResolver mappings, String jarPath,
            BetaMoonTransformer transformer, RuntimeNamespace namespace) throws Exception {
        MethodRef method = new MethodRef(new ClassRef("net/minecraft/src/BlockFluid"), "checkForHarden",
                "(Lnet/minecraft/src/World;III)V");
        ResolvedMethod target = mappings.resolveMethod(method, namespace);
        String owner = target.getOwner();
        byte[] transformed = transformer.transform(null, owner, null, null, readClass(jarPath, owner));
        require(transformed != null && countCallbackCalls(transformed, "beforeHarden") == 1,
                "Runtime " + namespace + " BlockFluid is missing identity-aware hardening");
        require(countCallbackCalls(transformed, "complete") == countReturns(transformed, target),
                "Runtime " + namespace + " BlockFluid hardening does not cover its return path");
        require(transformer.transform(null, owner, null, null, transformed) == null,
                "Fluid interaction seam must be idempotent for " + namespace);
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
        ClassRef controller = new ClassRef(PLAYER_CONTROLLER);
        ResolvedMethod use = mappings.resolveMethod(
                new MethodRef(controller, "sendUseItem",
                        "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;Lnet/minecraft/src/ItemStack;)Z"),
                namespace);
        node.methods.add(booleanMethod(use));
        ResolvedMethod entity = mappings.resolveMethod(new MethodRef(controller, "interactWithEntity",
                "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/Entity;)V"), namespace);
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

    private static int countParticleCallbacks(byte[] bytecode, String methodName) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions
                    .getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (methodName.equals(call.name)
                            && "betamoon/instrumentation/hooks/particle/ParticleCallbacks".equals(call.owner)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countScheduleCallbacks(byte[] bytecode, String methodName) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (methodName.equals(call.name)
                            && call.owner.equals("betamoon/instrumentation/hooks/block/BlockScheduleCallbacks")) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countMultiblockCallbacks(byte[] bytecode, String methodName) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (methodName.equals(call.name) && call.owner
                            .equals("betamoon/instrumentation/hooks/multiblock/MultiblockChunkCallbacks")) {
                        count++;
                    }
                }
            }
        }
        return count;
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
                int opcode = instruction.getOpcode();
                if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
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
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(32, initialSize));
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
