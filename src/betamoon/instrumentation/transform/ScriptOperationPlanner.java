package betamoon.instrumentation.transform;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookOperationSite;
import betamoon.hooks.HookOverrideMappings;
import betamoon.hooks.HookPoint;
import betamoon.hooks.HookPointSite;
import betamoon.hooks.HookPreparation;
import betamoon.interop.catalog.CatalogValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Discovers all exact operation matches on engine bytes before any user
 * envelope mutates the method.
 */
final class ScriptOperationPlanner {
    private ScriptOperationPlanner() {
    }

    static List<Selected> discover(ClassNode owner, MethodNode method, HookPreparation.Site site, ClassLoader loader)
            throws Exception {
        if (site.points.isEmpty()) {
            return Collections.emptyList();
        }
        if (method.name.startsWith("<")
                || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED)) != 0) {
            throw new HookFailure("match_failed", "operation points require an ordinary supported method");
        }
        AbstractInsnNode[] instructions = method.instructions.toArray();
        Map<AbstractInsnNode, Integer> indices = new HashMap<>();
        int opcodeIndex = 0;
        int normalReturns = 0;
        for (AbstractInsnNode instruction : instructions) {
            if (instruction.getOpcode() >= Opcodes.IRETURN && instruction.getOpcode() <= Opcodes.RETURN) {
                normalReturns++;
            }
            if (instruction.getOpcode() >= 0) {
                indices.put(instruction, opcodeIndex++);
            }
        }
        site.normalReturns = normalReturns;
        site.structureAnalyzed = true;
        Frame<BasicValue>[] frames = new HookOperandAnalyzer().analyze(owner.name, method);
        Map<AbstractInsnNode, Selected> selected = new LinkedHashMap<>();
        List<HookOperationSite> operations = new ArrayList<>();
        for (HookPointSite point : site.points.values()) {
            int observed = 0;
            AbstractInsnNode chosen = null;
            int chosenIndex = -1;
            List<Map<String, Object>> matches = new ArrayList<>();
            for (int index = 0; index < instructions.length; index++) {
                AbstractInsnNode instruction = instructions[index];
                if (!matches(point, instruction)) {
                    continue;
                }
                Map<String, Object> evidence = CatalogValues.record("ordinal", observed, "opcodeIndex",
                        indices.get(instruction), "opcode", instruction.getOpcode(), "selected",
                        observed == point.point.ordinal);
                if (matches.size() < 128) {
                    matches.add(evidence);
                } else if (observed == point.point.ordinal) {
                    matches.set(127, evidence);
                }
                if (observed == point.point.ordinal) {
                    chosen = instruction;
                    chosenIndex = index;
                }
                observed++;
            }
            point.observed = observed;
            point.matches = Collections.unmodifiableList(matches);
            if (observed != point.point.exact || chosen == null) {
                throw new HookFailure("match_failed", point.point.kind + " " + point.point.member.name + ": expected "
                        + point.point.exact + " matching operations; observed " + observed);
            }
            validateOperands(chosen, frames[chosenIndex]);
            if (point.point.kind.equals("field_write")) {
                FieldInsnNode field = (FieldInsnNode) chosen;
                int flags = fieldFlags(owner, field, loader);
                if ((flags & Opcodes.ACC_FINAL) != 0) {
                    throw new HookFailure("match_failed", "field-write points do not support final fields");
                }
            }
            Selected operand = selected.get(chosen);
            if (operand == null) {
                operand = new Selected(chosen);
                selected.put(chosen, operand);
            }
            HookOperationSite existing = point.point.before() ? operand.before : operand.after;
            if (existing == null) {
                HookPoint.Member member = member(chosen);
                existing = new HookOperationSite(operations.size(), point.point.kind, indices.get(chosen),
                        chosen.getOpcode(), member.owner, member.name, member.descriptor);
                operations.add(existing);
                if (point.point.before()) {
                    operand.before = existing;
                } else {
                    operand.after = existing;
                }
            }
            point.operationId = existing.id;
        }
        site.operations = Collections.unmodifiableList(operations);
        return new ArrayList<>(selected.values());
    }

    private static boolean matches(HookPointSite point, AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (point.point.field()) {
            if (!(instruction instanceof FieldInsnNode)
                    || point.point.before() != (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC)) {
                return false;
            }
        } else if (!(instruction instanceof MethodInsnNode) || ((MethodInsnNode) instruction).name.startsWith("<")) {
            return false;
        }
        HookPoint.Member actual = member(instruction);
        for (HookPoint.Member expected : point.alternatives) {
            if (actual.owner.equals(expected.owner) && actual.name.equals(expected.name)
                    && actual.descriptor.equals(expected.descriptor)) {
                return true;
            }
        }
        return false;
    }

    private static HookPoint.Member member(AbstractInsnNode instruction) {
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode method = (MethodInsnNode) instruction;
            return new HookPoint.Member(method.owner, method.name, method.desc);
        }
        FieldInsnNode field = (FieldInsnNode) instruction;
        return new HookPoint.Member(field.owner, field.name, field.desc);
    }

    private static void validateOperands(AbstractInsnNode instruction, Frame<BasicValue> frame) {
        if (frame == null) {
            throw new HookFailure("match_failed", "selected operation is unreachable");
        }
        int operands;
        int opcode = instruction.getOpcode();
        if (instruction instanceof MethodInsnNode) {
            operands = Type.getArgumentTypes(((MethodInsnNode) instruction).desc).length
                    + (opcode == Opcodes.INVOKESTATIC ? 0 : 1);
        } else {
            operands = (opcode == Opcodes.PUTFIELD || opcode == Opcodes.GETFIELD ? 1 : 0)
                    + (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC ? 1 : 0);
        }
        for (int index = frame.getStackSize() - operands; index < frame.getStackSize(); index++) {
            if (index < 0 || HookOperandAnalyzer.uninitialized(frame.getStack(index))) {
                throw new HookFailure("match_failed", "uninitialized values cannot enter an operation callback");
            }
        }
    }

    private static int fieldFlags(ClassNode owner, FieldInsnNode field, ClassLoader loader) {
        if (field.owner.equals(owner.name)) {
            for (FieldNode declared : owner.fields) {
                if (declared.name.equals(field.name) && declared.desc.equals(field.desc)) {
                    return declared.access;
                }
            }
            List<String> parents = new ArrayList<>(owner.interfaces);
            if (owner.superName != null) {
                parents.add(owner.superName);
            }
            for (String parent : parents) {
                Integer flags = HookOverrideMappings.findFieldAccessFlags(parent, field.name, field.desc, loader);
                if (flags != null) {
                    return flags;
                }
            }
            throw new HookFailure("match_failed", "referenced inherited field metadata is unavailable");
        }
        return HookOverrideMappings.fieldAccessFlags(field.owner, field.name, field.desc, loader);
    }

    static final class Selected {
        final AbstractInsnNode instruction;
        HookOperationSite before;
        HookOperationSite after;

        Selected(AbstractInsnNode instruction) {
            this.instruction = instruction;
        }
    }
}
