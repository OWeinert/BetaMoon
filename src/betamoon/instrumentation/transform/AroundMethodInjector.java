package betamoon.instrumentation.transform;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.HookDefinition;
import betamoon.instrumentation.api.ValueBinding;
import betamoon.instrumentation.mapping.MappingResolver;
import betamoon.instrumentation.mapping.ResolvedField;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.registry.PlannedHook;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Applies the entry-capture and normal-return portions of an around hook. */
final class AroundMethodInjector {
    private final MappingResolver mappings;

    AroundMethodInjector(MappingResolver mappings) {
        this.mappings = mappings;
    }

    boolean apply(ClassNode classNode, PlannedHook plannedHook) throws HookTransformException {
        HookDefinition genericDefinition = plannedHook.getDefinition();
        if (!(genericDefinition instanceof AroundHookDefinition)) {
            throw failure(genericDefinition, "Unsupported hook definition type: "
                + genericDefinition.getClass().getName());
        }
        AroundHookDefinition definition = (AroundHookDefinition) genericDefinition;
        TargetMatch targetMatch = findTarget(classNode, plannedHook);
        ResolvedMethod captureHandler = mappings.resolveMethod(
            definition.getCaptureHandler().getMethod(), targetMatch.namespace);
        ResolvedMethod returnHandler = mappings.resolveMethod(
            definition.getReturnHandler().getMethod(), targetMatch.namespace);

        if ((targetMatch.method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw failure(definition, "Cannot inject into an abstract or native method: " + targetMatch.target);
        }
        if ("<init>".equals(targetMatch.method.name)) {
            throw failure(definition, "Constructor around-hooks are not supported");
        }

        Type captureType = Type.getReturnType(captureHandler.getDescriptor());
        if (captureType.getSort() == Type.VOID) {
            throw failure(definition, "Capture handler must return a value: " + captureHandler);
        }
        Type targetReturnType = Type.getReturnType(targetMatch.target.getDescriptor());
        Type returnHandlerType = Type.getReturnType(returnHandler.getDescriptor());
        if (returnHandlerType.getSort() != Type.VOID && !returnHandlerType.equals(targetReturnType)) {
            throw failure(definition, "Return handler must return void or the target return type "
                + targetReturnType + ": " + returnHandler);
        }
        List<AbstractInsnNode> returns = collectReturns(targetMatch.method, targetReturnType);
        if (returns.isEmpty()) {
            throw failure(definition, "Target method has no compatible normal return instruction: "
                + targetMatch.target);
        }

        int captureCalls = countHandlerCalls(targetMatch.method, captureHandler);
        int returnCalls = countHandlerCalls(targetMatch.method, returnHandler);
        if (captureCalls == 1 && returnCalls == returns.size()) {
            return false;
        }
        if (captureCalls != 0 || returnCalls != 0) {
            throw failure(definition, "Target contains partial or conflicting instrumentation (capture calls "
                + captureCalls + ", return calls " + returnCalls + ", return instructions " + returns.size()
                + ")");
        }

        validateBindings(definition, targetMatch, captureHandler, definition.getCaptureBindings(),
            null, captureType, false);
        validateBindings(definition, targetMatch, returnHandler, definition.getReturnBindings(),
            targetReturnType, captureType, true);

        int captureLocal = targetMatch.method.maxLocals;
        targetMatch.method.maxLocals += captureType.getSize();
        int returnLocal = -1;
        if (targetReturnType.getSort() != Type.VOID) {
            returnLocal = targetMatch.method.maxLocals;
            targetMatch.method.maxLocals += targetReturnType.getSize();
        }

        InsnList entry = new InsnList();
        emitBindings(definition, entry, definition.getCaptureBindings(), targetMatch, -1, captureLocal,
            targetReturnType, captureType);
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, captureHandler.getOwner(), captureHandler.getName(),
            captureHandler.getDescriptor(), false));
        entry.add(new VarInsnNode(captureType.getOpcode(Opcodes.ISTORE), captureLocal));
        targetMatch.method.instructions.insert(entry);

        for (AbstractInsnNode returnInstruction : returns) {
            InsnList exit = new InsnList();
            if (targetReturnType.getSort() != Type.VOID) {
                exit.add(new VarInsnNode(targetReturnType.getOpcode(Opcodes.ISTORE), returnLocal));
            }
            emitBindings(definition, exit, definition.getReturnBindings(), targetMatch, returnLocal, captureLocal,
                targetReturnType, captureType);
            exit.add(new MethodInsnNode(Opcodes.INVOKESTATIC, returnHandler.getOwner(), returnHandler.getName(),
                returnHandler.getDescriptor(), false));
            if (returnHandlerType.getSort() == Type.VOID && targetReturnType.getSort() != Type.VOID) {
                exit.add(new VarInsnNode(targetReturnType.getOpcode(Opcodes.ILOAD), returnLocal));
            }
            targetMatch.method.instructions.insertBefore(returnInstruction, exit);
        }
        return true;
    }

    private TargetMatch findTarget(ClassNode classNode, PlannedHook plannedHook) throws HookTransformException {
        HookDefinition definition = plannedHook.getDefinition();
        List<String> attempts = new ArrayList<String>();
        for (RuntimeNamespace namespace : plannedHook.getNamespaces()) {
            ResolvedMethod target = mappings.resolveMethod(definition.getTarget(), namespace);
            if (!classNode.name.equals(target.getOwner())) {
                continue;
            }
            List<MethodNode> matches = new ArrayList<MethodNode>();
            for (MethodNode method : classNode.methods) {
                if (target.getName().equals(method.name) && target.getDescriptor().equals(method.desc)) {
                    matches.add(method);
                }
            }
            attempts.add(namespace.getMappingName() + "=" + target + " (matches " + matches.size() + ")");
            if (definition.getMatchRequirement().accepts(matches.size()) && matches.size() == 1) {
                return new TargetMatch(namespace, target, matches.get(0));
            }
        }
        throw failure(definition, "Target match requirement " + definition.getMatchRequirement()
            + " was not met; attempted " + attempts);
    }

    private void validateBindings(HookDefinition definition, TargetMatch targetMatch, ResolvedMethod handler,
        List<ValueBinding> bindings, Type returnType, Type captureType, boolean returnPhase)
        throws HookTransformException {
        Type[] handlerArguments = Type.getArgumentTypes(handler.getDescriptor());
        if (handlerArguments.length != bindings.size()) {
            throw failure(definition, "Handler " + handler + " expects " + handlerArguments.length
                + " arguments but " + bindings.size() + " bindings were supplied");
        }
        for (int i = 0; i < handlerArguments.length; i++) {
            Type bindingType = bindingType(definition, bindings.get(i), targetMatch, returnType, captureType,
                returnPhase);
            if (!handlerArguments[i].equals(bindingType)) {
                throw failure(definition, "Binding " + i + " for " + handler + " produces " + bindingType
                    + " but the handler expects " + handlerArguments[i]);
            }
        }
    }

    private Type bindingType(HookDefinition definition, ValueBinding binding, TargetMatch targetMatch,
        Type returnType, Type captureType, boolean returnPhase) throws HookTransformException {
        boolean isStatic = (targetMatch.method.access & Opcodes.ACC_STATIC) != 0;
        switch (binding.getKind()) {
            case THIS:
                requireInstance(definition, isStatic, binding.getKind());
                return Type.getObjectType(targetMatch.target.getOwner());
            case ARGUMENT:
                Type[] arguments = Type.getArgumentTypes(targetMatch.target.getDescriptor());
                int argumentIndex = binding.getArgumentIndex();
                if (argumentIndex >= arguments.length) {
                    throw failure(definition, "Argument index " + argumentIndex + " is outside target descriptor "
                        + targetMatch.target.getDescriptor());
                }
                return arguments[argumentIndex];
            case INSTANCE_FIELD:
                requireInstance(definition, isStatic, binding.getKind());
                ResolvedField field = mappings.resolveField(binding.getField(), targetMatch.namespace);
                return Type.getType(field.getDescriptor());
            case RETURN_VALUE:
                if (!returnPhase || returnType == null || returnType.getSort() == Type.VOID) {
                    throw failure(definition, "Return-value binding is invalid for this callback");
                }
                return returnType;
            case CAPTURED_VALUE:
                if (!returnPhase) {
                    throw failure(definition, "Captured-value binding is only valid in the return callback");
                }
                return captureType;
            default:
                throw failure(definition, "Unsupported binding kind: " + binding.getKind());
        }
    }

    private void emitBindings(HookDefinition definition, InsnList output, List<ValueBinding> bindings,
        TargetMatch targetMatch,
        int returnLocal, int captureLocal, Type returnType, Type captureType) throws HookTransformException {
        for (ValueBinding binding : bindings) {
            switch (binding.getKind()) {
                case THIS:
                    output.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    break;
                case ARGUMENT:
                    Type argumentType = Type.getArgumentTypes(targetMatch.target.getDescriptor())[
                        binding.getArgumentIndex()];
                    output.add(new VarInsnNode(argumentType.getOpcode(Opcodes.ILOAD),
                        argumentLocal(targetMatch.method, targetMatch.target, binding.getArgumentIndex())));
                    break;
                case INSTANCE_FIELD:
                    ResolvedField field = mappings.resolveField(binding.getField(), targetMatch.namespace);
                    output.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    output.add(new FieldInsnNode(Opcodes.GETFIELD, field.getOwner(), field.getName(),
                        field.getDescriptor()));
                    break;
                case RETURN_VALUE:
                    output.add(new VarInsnNode(returnType.getOpcode(Opcodes.ILOAD), returnLocal));
                    break;
                case CAPTURED_VALUE:
                    output.add(new VarInsnNode(captureType.getOpcode(Opcodes.ILOAD), captureLocal));
                    break;
                default:
                    throw failure(definition, "Unsupported binding kind: " + binding.getKind());
            }
        }
    }

    private static int argumentLocal(MethodNode method, ResolvedMethod target, int requestedIndex) {
        int local = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        Type[] arguments = Type.getArgumentTypes(target.getDescriptor());
        for (int i = 0; i < requestedIndex; i++) {
            local += arguments[i].getSize();
        }
        return local;
    }

    private static List<AbstractInsnNode> collectReturns(MethodNode method, Type returnType) {
        List<AbstractInsnNode> returns = new ArrayList<AbstractInsnNode>();
        int expectedOpcode = returnType.getOpcode(Opcodes.IRETURN);
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {
            if (instruction.getOpcode() == expectedOpcode) {
                returns.add(instruction);
            }
        }
        return returns;
    }

    private static int countHandlerCalls(MethodNode method, ResolvedMethod handler) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && handler.getOwner().equals(call.owner)
                    && handler.getName().equals(call.name) && handler.getDescriptor().equals(call.desc)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void requireInstance(HookDefinition definition, boolean isStatic, ValueBinding.Kind kind)
        throws HookTransformException {
        if (isStatic) {
            throw failure(definition, kind + " cannot be bound from a static target method");
        }
    }

    private static HookTransformException failure(HookDefinition definition, String message) {
        return new HookTransformException(definition.getId(), message);
    }

    private static final class TargetMatch {
        private final RuntimeNamespace namespace;
        private final ResolvedMethod target;
        private final MethodNode method;

        private TargetMatch(RuntimeNamespace namespace, ResolvedMethod target, MethodNode method) {
            this.namespace = namespace;
            this.target = target;
            this.method = method;
        }
    }
}
