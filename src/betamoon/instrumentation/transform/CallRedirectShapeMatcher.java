package betamoon.instrumentation.transform;

import betamoon.instrumentation.api.CallRedirectShape;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Validates the whole semantic call set before any native instruction is
 * changed.
 */
final class CallRedirectShapeMatcher {
    static boolean matches(List<CallRedirectShape> shapes, List<MethodInsnNode> originals, int redirects) {
        if (shapes.isEmpty()) {
            return true;
        }
        int expected = 0;
        for (CallRedirectShape shape : shapes) {
            expected += shape.occurrences;
        }
        if (originals.isEmpty()) {
            return redirects == expected;
        }
        if (redirects != 0 || originals.size() != expected) {
            return false;
        }
        int[] counts = new int[shapes.size()];
        for (MethodInsnNode call : originals) {
            int matched = -1;
            for (int index = 0; index < shapes.size(); index++) {
                if (matches(shapes.get(index), call)) {
                    if (matched != -1) {
                        return false;
                    }
                    matched = index;
                }
            }
            if (matched == -1 || ++counts[matched] > shapes.get(matched).occurrences) {
                return false;
            }
        }
        for (int index = 0; index < shapes.size(); index++) {
            if (counts[index] != shapes.get(index).occurrences) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(CallRedirectShape shape, MethodInsnNode call) {
        AbstractInsnNode current = call.getPrevious();
        for (int index = shape.tail.size() - 1; index >= 0; index--) {
            while (current != null && current.getOpcode() < 0) {
                current = current.getPrevious();
            }
            if (current == null || !matches(shape.tail.get(index), current)) {
                return false;
            }
            current = current.getPrevious();
        }
        return true;
    }

    private static boolean matches(CallRedirectShape.Instruction expected, AbstractInsnNode instruction) {
        switch (expected.kind) {
            case LOCAL_INTEGER:
                return instruction instanceof VarInsnNode && instruction.getOpcode() == Opcodes.ILOAD;
            case OPCODE:
                return instruction.getOpcode() == expected.value;
            case INTEGER:
                if (instruction.getOpcode() >= Opcodes.ICONST_M1 && instruction.getOpcode() <= Opcodes.ICONST_5) {
                    return instruction.getOpcode() - Opcodes.ICONST_0 == expected.value;
                }
                if (instruction instanceof IntInsnNode
                        && (instruction.getOpcode() == Opcodes.BIPUSH || instruction.getOpcode() == Opcodes.SIPUSH)) {
                    return ((IntInsnNode) instruction).operand == expected.value;
                }
                return instruction instanceof LdcInsnNode && ((LdcInsnNode) instruction).cst instanceof Integer
                        && ((Integer) ((LdcInsnNode) instruction).cst).intValue() == expected.value;
            default:
                return false;
        }
    }

    private CallRedirectShapeMatcher() {
    }
}
