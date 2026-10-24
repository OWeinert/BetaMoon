package betamoon.instrumentation.transform;

import betamoon.instrumentation.api.FieldValueTransformHookDefinition;
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

/**
 * Exact mapped field-write anchors; stack types and arithmetic evaluation order
 * remain native.
 */
final class FieldValueTransformInjector {
    private final MappingResolver mappings;

    FieldValueTransformInjector(MappingResolver mappings) {
        this.mappings = mappings;
    }

    HookTransformOutcome apply(ClassNode node, PlannedHook planned) throws HookTransformException {
        FieldValueTransformHookDefinition definition = (FieldValueTransformHookDefinition) planned.getDefinition();
        for (RuntimeNamespace namespace : planned.getNamespaces()) {
            ResolvedMethod target = mappings.resolveMethod(definition.getTarget(), namespace);
            if (!node.name.equals(target.getOwner())) {
                continue;
            }
            ResolvedField field = mappings.resolveField(definition.getField(), namespace);
            ResolvedMethod handler = mappings.resolveMethod(definition.getHandler().getMethod(), namespace);
            String owner = mappings.resolveClass(definition.getAccessOwner(), namespace);
            Type valueType = Type.getType(field.getDescriptor());
            String expected = "(L" + target.getOwner() + ";" + valueType.getDescriptor() + ")"
                    + valueType.getDescriptor();
            if (!handler.getDescriptor().equals(expected)) {
                throw new HookTransformException(definition.getId(), "Value policy must preserve this/value types");
            }
            int opcode = definition.getArithmeticOpcode();
            if (opcode != -1 && !validArithmetic(opcode, valueType)) {
                throw new HookTransformException(definition.getId(), "Unsupported arithmetic/value type anchor");
            }
            for (MethodNode method : node.methods) {
                if (!method.name.equals(target.getName()) || !method.desc.equals(target.getDescriptor())) {
                    continue;
                }
                if ((method.access & Opcodes.ACC_STATIC) != 0) {
                    throw new HookTransformException(definition.getId(), "Value policies require an instance method");
                }
                List<AbstractInsnNode> anchors = new ArrayList<>();
                int applied = 0;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof FieldInsnNode)) {
                        continue;
                    }
                    FieldInsnNode write = (FieldInsnNode) instruction;
                    if (write.getOpcode() != Opcodes.PUTFIELD || !write.owner.equals(owner)
                            || !write.name.equals(field.getName()) || !write.desc.equals(field.getDescriptor())) {
                        continue;
                    }
                    AbstractInsnNode anchor = opcode == -1 ? write : previous(write);
                    if (anchor == null || opcode != -1 && anchor.getOpcode() != opcode) {
                        continue;
                    }
                    anchors.add(anchor);
                    AbstractInsnNode before = previous(anchor);
                    if (before instanceof MethodInsnNode && isHandler((MethodInsnNode) before, handler)) {
                        applied++;
                    }
                }
                if (anchors.size() != definition.getExpectedMatches()) {
                    throw new HookTransformException(definition.getId(), "Expected " + definition.getExpectedMatches()
                            + " anchored field values; found " + anchors.size());
                }
                if (applied == anchors.size()) {
                    return HookTransformOutcome.ALREADY_APPLIED;
                }
                if (applied != 0) {
                    throw new HookTransformException(definition.getId(), "Partially transformed field values");
                }
                int local = method.maxLocals;
                method.maxLocals += valueType.getSize();
                for (AbstractInsnNode anchor : anchors) {
                    InsnList policy = new InsnList();
                    policy.add(new VarInsnNode(valueType.getOpcode(Opcodes.ISTORE), local));
                    policy.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    policy.add(new VarInsnNode(valueType.getOpcode(Opcodes.ILOAD), local));
                    policy.add(new MethodInsnNode(Opcodes.INVOKESTATIC, handler.getOwner(), handler.getName(),
                            handler.getDescriptor(), false));
                    method.instructions.insertBefore(anchor, policy);
                }
                return HookTransformOutcome.APPLIED;
            }
        }
        throw new HookTransformException(definition.getId(), "Target method not found");
    }

    private static boolean validArithmetic(int opcode, Type type) {
        return type.getSort() == Type.DOUBLE
                && (opcode == Opcodes.DADD || opcode == Opcodes.DSUB || opcode == Opcodes.DMUL)
                || type.getSort() == Type.FLOAT
                        && (opcode == Opcodes.FADD || opcode == Opcodes.FSUB || opcode == Opcodes.FMUL);
    }

    private static boolean isHandler(MethodInsnNode call, ResolvedMethod handler) {
        return call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(handler.getOwner())
                && call.name.equals(handler.getName()) && call.desc.equals(handler.getDescriptor());
    }

    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        return previous;
    }
}
