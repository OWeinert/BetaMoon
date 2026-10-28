package betamoon.instrumentation.transform;

import betamoon.hooks.HookDispatcher;
import betamoon.hooks.HookFailure;
import betamoon.hooks.HookPreparation;
import betamoon.hooks.HookOverrideMappings;
import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.diagnostics.NativeDiagnosticEvidence;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

/**
 * Atomic user group applied to already-valid engine output, with byte-backed
 * verification.
 */
final class ScriptHookTransformer {
    private static final String DISPATCHER = "betamoon/hooks/HookDispatcher";
    private static final String ENTRY = "betamoon/hooks/HookDispatcher$Entry";
    private static final Type OBJECT = Type.getType(Object.class);
    private static final Type OBJECTS = Type.getType(Object[].class);
    private static final String PREFIX = "(ILjava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;";
    private final HookPreparation preparation;

    ScriptHookTransformer(HookPreparation preparation) {
        this.preparation = preparation;
    }

    byte[] transform(ClassLoader loader, String name, byte[] input) {
        if (preparation == null || preparation.sites(name).isEmpty()) {
            return input;
        }
        List<HookPreparation.Site> sites = preparation.sites(name);
        String observedInput = CatalogFingerprint.sha256(input);
        for (HookPreparation.Site site : sites) {
            if (!site.status.equals("applied")) {
                site.inputHash = observedInput;
            }
        }
        try {
            requireDispatcher(loader);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            Set<String> present = new HashSet<>();
            for (MethodNode method : node.methods) {
                present.add(node.name + "." + method.name + method.desc);
            }
            List<HookPreparation.Site> installed = new ArrayList<>();
            for (HookPreparation.Site site : sites) {
                HookDispatcher.requireLoader(site.id, loader);
                if (!present.contains(site.target.key()) && !site.alternatives.isEmpty()
                        && site.alternatives.stream().allMatch(group -> group.stream().anyMatch(present::contains))) {
                    site.status = "namespace_absent";
                    continue;
                }
                if (site.status.equals("target_loaded_too_early")) {
                    throw new HookFailure("target_loaded_too_early", site.target.key());
                }
                List<MethodNode> matches = new ArrayList<>();
                for (MethodNode method : node.methods) {
                    if (method.name.equals(site.target.method) && method.desc.equals(site.target.descriptor)) {
                        matches.add(method);
                    }
                }
                site.methodMatches = matches.size();
                if (matches.size() != 1) {
                    throw new HookFailure("match_failed",
                            site.target.key() + ": expected 1 method; got " + matches.size());
                }
                MethodNode target = matches.get(0);
                String mappingOwner = site.target.mappingOwner;
                if (mappingOwner != null && ((target.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0
                        || !new HierarchyClassWriter(loader, node).isAssignable(mappingOwner, node.name)
                        || !HookOverrideMappings.visibleVirtual(mappingOwner, target.name, target.desc, loader))) {
                    throw new HookFailure("mapping_conflict",
                            "observed class does not implement the mapped virtual ancestor");
                }
                if (site.target.constructor()) {
                    ScriptConstructorTransformer.apply(node, target, site);
                    installed.add(site);
                    continue;
                }
                List<ScriptOperationPlanner.Selected> operations = ScriptOperationPlanner.discover(node, target, site,
                        loader);
                int[] savedParameters = instrument(node, target, site);
                ScriptOperationTransformer.apply(node, target, site, operations, savedParameters);
                if (site.around) {
                    around(node, target, site);
                }
                installed.add(site);
            }
            HierarchyClassWriter writer = new HierarchyClassWriter(loader, node);
            node.accept(writer);
            byte[] output = writer.toByteArray();
            ClassNode verified = new ClassNode();
            new ClassReader(output).accept(verified, 0);
            for (MethodNode method : verified.methods) {
                if (installed.stream().anyMatch(
                        site -> (site.target.method.equals(method.name) || method.name.equals(site.helperName))
                                && site.target.descriptor.equals(method.desc))) {
                    new Analyzer<BasicValue>(new ResourceVerifier(verified, writer)).analyze(verified.name, method);
                }
            }
            for (HookPreparation.Site site : installed) {
                site.inputHash = CatalogFingerprint.sha256(input);
                site.outputHash = CatalogFingerprint.sha256(output);
                site.status = "applied";
                HookDispatcher.applied(site.id, loader);
            }
            preparation.changed();
            return output;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable error) {
            String reason = error instanceof HookFailure ? ((HookFailure) error).reason : "transformation_failed";
            for (HookPreparation.Site site : sites) {
                if (!site.status.equals("applied")) {
                    site.status = reason;
                }
                preparation.diagnose(String.join(",", site.owners), site.target.key(), reason,
                        "User group rejected; valid engine bytes retained: " + NativeDiagnosticEvidence.message(error));
            }
            return input;
        }
    }

    private int[] instrument(ClassNode owner, MethodNode method, HookPreparation.Site site) {
        if (method.name.startsWith("<")
                || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED)) != 0) {
            throw new HookFailure("match_failed", "unsupported method flags: " + site.target.key());
        }
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            int opcode = instruction.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                returns.add(instruction);
            }
            if (opcode == Opcodes.JSR || opcode == Opcodes.RET || instruction instanceof MethodInsnNode
                    && ((MethodInsnNode) instruction).owner.equals(DISPATCHER)) {
                throw new HookFailure("match_failed",
                        "legacy subroutine or existing script envelope: " + site.target.key());
            }
        }
        site.normalReturns = returns.size();
        site.structureAnalyzed = true;
        site.fingerprint = CatalogFingerprint.sha256(("envelope:" + site.envelopeVersion() + ":" + site.target.key()
                + ":flags:" + method.access + ":normalReturns:" + returns.size()
                + (site.points.isEmpty() ? "" : ":operations:" + site.operationFingerprint()))
                .getBytes(StandardCharsets.UTF_8));
        Type[] parameters = Type.getArgumentTypes(method.desc);
        Type result = Type.getReturnType(method.desc);
        boolean instance = (method.access & Opcodes.ACC_STATIC) == 0;
        int[] originalParameters = new int[parameters.length];
        int[] savedParameters = new int[parameters.length];
        int parameterLocal = instance ? 1 : 0;
        for (int index = 0; index < parameters.length; index++) {
            originalParameters[index] = parameterLocal;
            savedParameters[index] = method.maxLocals;
            parameterLocal += parameters[index].getSize();
            method.maxLocals += parameters[index].getSize();
        }
        int entryLocal = method.maxLocals++;
        int returnLocal = method.maxLocals;
        method.maxLocals += Math.max(1, result.getSize());
        int throwableLocal = method.maxLocals++;
        LabelNode bodyStart = label();
        LabelNode bodyEnd = label();
        LabelNode normalReturn = label();
        LabelNode handler = label();

        MethodNode entryCode = new MethodNode();
        InstructionAdapter entry = new InstructionAdapter(entryCode);
        for (int index = 0; index < parameters.length; index++) {
            entry.load(originalParameters[index], parameters[index]);
            entry.store(savedParameters[index], parameters[index]);
        }
        LabelNode dormant = label();
        active(entry, site.id, 0, owner.name);
        entry.ifeq(dormant.getLabel());
        packed(entry, site.id, owner.name, instance, parameters, originalParameters);
        entry.invokestatic(DISPATCHER, "before", PREFIX + ")L" + ENTRY + ";", false);
        entry.store(entryLocal, OBJECT);
        int local = instance ? 1 : 0;
        for (int index = 0; index < parameters.length; index++) {
            entry.load(entryLocal, OBJECT);
            entry.getfield(ENTRY, "arguments", "[Ljava/lang/Object;");
            entry.iconst(index);
            entry.aload(OBJECT);
            unbox(entry, parameters[index]);
            entry.store(local, parameters[index]);
            entry.load(local, parameters[index]);
            entry.store(savedParameters[index], parameters[index]);
            local += parameters[index].getSize();
        }
        entry.load(entryLocal, OBJECT);
        entry.getfield(ENTRY, "shortCircuit", "Z");
        entry.ifeq(dormant.getLabel());
        if (result.getSort() != Type.VOID) {
            entry.load(entryLocal, OBJECT);
            entry.getfield(ENTRY, "result", "Ljava/lang/Object;");
            unbox(entry, result);
        }
        entry.areturn(result);
        entryCode.instructions.add(dormant);
        entryCode.instructions.add(bodyStart);
        method.instructions.insert(entryCode.instructions);

        for (AbstractInsnNode instruction : returns) {
            MethodNode replacement = new MethodNode();
            InstructionAdapter adapter = new InstructionAdapter(replacement);
            if (result.getSort() != Type.VOID) {
                adapter.store(returnLocal, result);
            }
            replacement.instructions.add(new JumpInsnNode(Opcodes.GOTO, normalReturn));
            method.instructions.insertBefore(instruction, replacement.instructions);
            method.instructions.remove(instruction);
        }
        method.instructions.add(bodyEnd);
        method.instructions.add(normalReturn);
        MethodNode exitCode = new MethodNode();
        InstructionAdapter exit = new InstructionAdapter(exitCode);
        LabelNode noAfter = label();
        active(exit, site.id, 1, owner.name);
        exit.ifeq(noAfter.getLabel());
        packed(exit, site.id, owner.name, instance, parameters, savedParameters);
        if (result.getSort() == Type.VOID) {
            exit.aconst(null);
        } else {
            exit.load(returnLocal, result);
            box(exit, result);
        }
        exit.invokestatic(DISPATCHER, "after", PREFIX + "Ljava/lang/Object;)Ljava/lang/Object;", false);
        if (result.getSort() == Type.VOID) {
            exit.pop();
        } else {
            unbox(exit, result);
            exit.store(returnLocal, result);
        }
        exitCode.instructions.add(noAfter);
        if (result.getSort() != Type.VOID) {
            exit.load(returnLocal, result);
        }
        exit.areturn(result);
        exitCode.instructions.add(handler);
        exit.store(throwableLocal, OBJECT);
        LabelNode rethrow = label();
        active(exit, site.id, 2, owner.name);
        exit.ifeq(rethrow.getLabel());
        packed(exit, site.id, owner.name, instance, parameters, savedParameters);
        exit.load(throwableLocal, OBJECT);
        exit.invokestatic(DISPATCHER, "error", PREFIX + "Ljava/lang/Throwable;)V", false);
        exitCode.instructions.add(rethrow);
        exit.load(throwableLocal, OBJECT);
        exit.athrow();
        method.instructions.add(exitCode.instructions);
        method.tryCatchBlocks.add(new TryCatchBlockNode(bodyStart, bodyEnd, handler, "java/lang/Throwable"));
        return savedParameters;
    }

    private static void around(ClassNode owner, MethodNode method, HookPreparation.Site site) {
        if ((owner.access & Opcodes.ACC_INTERFACE) != 0) {
            throw new HookFailure("match_failed", "around extraction does not support interface default methods");
        }
        String helperName = "betamoon$hook$body$" + site.id;
        Set<String> names = new HashSet<>();
        for (MethodNode existing : owner.methods) {
            names.add(existing.name);
        }
        while (names.contains(helperName)) {
            helperName += "$";
        }
        site.helperName = helperName;
        boolean instance = (method.access & Opcodes.ACC_STATIC) == 0;
        MethodNode helper = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC | (instance ? 0 : Opcodes.ACC_STATIC), helperName,
                method.desc, null, method.exceptions.toArray(new String[0]));
        helper.instructions = method.instructions;
        helper.tryCatchBlocks = method.tryCatchBlocks;
        helper.localVariables = method.localVariables;
        helper.visibleLocalVariableAnnotations = method.visibleLocalVariableAnnotations;
        helper.invisibleLocalVariableAnnotations = method.invisibleLocalVariableAnnotations;
        helper.maxLocals = method.maxLocals;
        helper.maxStack = method.maxStack;
        owner.methods.add(helper);

        method.instructions = new InsnList();
        method.tryCatchBlocks = new ArrayList<>();
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        Type[] parameters = Type.getArgumentTypes(method.desc);
        Type result = Type.getReturnType(method.desc);
        int[] locals = new int[parameters.length];
        int local = instance ? 1 : 0;
        for (int index = 0; index < parameters.length; index++) {
            locals[index] = local;
            local += parameters[index].getSize();
        }
        method.maxLocals = local;
        InstructionAdapter code = new InstructionAdapter(method);
        LabelNode dormant = label();
        active(code, site.id, 3, owner.name);
        code.ifeq(dormant.getLabel());
        packed(code, site.id, owner.name, instance, parameters, locals);
        code.aconst(helperName);
        code.invokestatic(DISPATCHER, "around", PREFIX + "Ljava/lang/String;)Ljava/lang/Object;", false);
        if (result.getSort() == Type.VOID) {
            code.pop();
        } else {
            unbox(code, result);
        }
        code.areturn(result);
        method.instructions.add(dormant);
        if (instance) {
            code.load(0, OBJECT);
        }
        for (int index = 0; index < parameters.length; index++) {
            code.load(locals[index], parameters[index]);
        }
        if (instance) {
            code.invokespecial(owner.name, helperName, method.desc, false);
        } else {
            code.invokestatic(owner.name, helperName, method.desc, false);
        }
        code.areturn(result);
    }

    private static void requireDispatcher(ClassLoader loader) {
        try {
            if (loader != null && HookDispatcher.protocol().equals(
                    Class.forName(HookDispatcher.class.getName(), false, loader).getMethod("protocol").invoke(null))) {
                return;
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new HookFailure("dispatcher_not_visible", "target loader cannot link the agent dispatcher", error);
        }
        throw new HookFailure("dispatcher_not_visible", "target loader cannot link the agent dispatcher");
    }

    private static LabelNode label() {
        LabelNode node = new LabelNode();
        node.getLabel().info = node;
        return node;
    }

    private static void active(InstructionAdapter code, int site, int phase, String owner) {
        code.iconst(site);
        code.iconst(phase);
        code.aconst(Type.getObjectType(owner));
        code.invokestatic(DISPATCHER, "active", "(IILjava/lang/Class;)Z", false);
    }

    private static void packed(InstructionAdapter code, int site, String owner, boolean instance, Type[] parameters,
            int[] locals) {
        code.iconst(site);
        code.aconst(Type.getObjectType(owner));
        if (instance) {
            code.load(0, OBJECT);
        } else {
            code.aconst(null);
        }
        code.iconst(parameters.length);
        code.newarray(OBJECT);
        for (int index = 0; index < parameters.length; index++) {
            code.dup();
            code.iconst(index);
            code.load(locals[index], parameters[index]);
            box(code, parameters[index]);
            code.astore(OBJECT);
        }
    }

    private static Type wrapper(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return Type.getType(Boolean.class);
            case Type.BYTE:
                return Type.getType(Byte.class);
            case Type.CHAR:
                return Type.getType(Character.class);
            case Type.SHORT:
                return Type.getType(Short.class);
            case Type.INT:
                return Type.getType(Integer.class);
            case Type.FLOAT:
                return Type.getType(Float.class);
            case Type.LONG:
                return Type.getType(Long.class);
            case Type.DOUBLE:
                return Type.getType(Double.class);
            default:
                return type;
        }
    }

    static void box(InstructionAdapter code, Type type) {
        if (type.getSort() >= Type.BOOLEAN && type.getSort() <= Type.DOUBLE) {
            Type boxed = wrapper(type);
            code.invokestatic(boxed.getInternalName(), "valueOf",
                    "(" + type.getDescriptor() + ")" + boxed.getDescriptor(), false);
        }
    }

    static void unbox(InstructionAdapter code, Type type) {
        Type boxed = wrapper(type);
        code.checkcast(boxed);
        if (type.getSort() >= Type.BOOLEAN && type.getSort() <= Type.DOUBLE) {
            code.invokevirtual(boxed.getInternalName(), type.getClassName() + "Value", "()" + type.getDescriptor(),
                    false);
        }
    }

    private static final class ResourceVerifier extends SimpleVerifier {
        private final HierarchyClassWriter hierarchy;

        ResourceVerifier(ClassNode node, HierarchyClassWriter hierarchy) {
            super(Opcodes.ASM9, Type.getObjectType(node.name),
                    node.superName == null ? null : Type.getObjectType(node.superName), null,
                    (node.access & Opcodes.ACC_INTERFACE) != 0);
            this.hierarchy = hierarchy;
        }

        @Override
        protected boolean isInterface(Type type) {
            return hierarchy.isInterface(type.getInternalName());
        }

        @Override
        protected Type getSuperClass(Type type) {
            String parent = hierarchy.superName(type.getInternalName());
            return parent == null ? null : Type.getObjectType(parent);
        }

        @Override
        protected boolean isAssignableFrom(Type target, Type value) {
            return target.equals(value) || target.getSort() >= Type.ARRAY && value.getSort() >= Type.ARRAY
                    && hierarchy.isAssignable(target.getInternalName(), value.getInternalName());
        }

        @Override
        protected boolean isSubTypeOf(BasicValue value, BasicValue expected) {
            Type actualType = value.getType();
            Type expectedType = expected.getType();
            if (value.equals(expected)) {
                return true;
            }
            if (actualType == null || expectedType == null || expectedType.getSort() < Type.ARRAY) {
                return false;
            }
            if (actualType.equals(Type.getObjectType("null"))) {
                return true;
            }
            return actualType.getSort() >= Type.ARRAY
                    && (isAssignableFrom(expectedType, actualType) || isInterface(expectedType));
        }

        @Override
        protected Class<?> getClass(Type type) {
            throw new HookFailure("transformation_failed",
                    "reflection is forbidden during bytecode verification: " + type);
        }
    }
}
