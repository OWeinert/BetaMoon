package betamoon.instrumentation.transform;

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;

/**
 * Tracks NEW aliases until invokespecial initialization, without defining any
 * referenced class.
 */
final class HookOperandAnalyzer extends Analyzer<BasicValue> {
    HookOperandAnalyzer() {
        super(new Values());
    }

    static boolean uninitialized(BasicValue value) {
        return value instanceof Uninitialized || value == BasicValue.UNINITIALIZED_VALUE;
    }

    @Override
    protected Frame<BasicValue> newFrame(int locals, int stack) {
        return new InitializingFrame(locals, stack);
    }

    @Override
    protected Frame<BasicValue> newFrame(Frame<? extends BasicValue> source) {
        return new InitializingFrame(source);
    }

    private static final class InitializingFrame extends Frame<BasicValue> {
        InitializingFrame(int locals, int stack) {
            super(locals, stack);
        }

        InitializingFrame(Frame<? extends BasicValue> source) {
            super(source);
        }

        @Override
        public void execute(AbstractInsnNode instruction, Interpreter<BasicValue> interpreter)
                throws AnalyzerException {
            BasicValue initialized = null;
            if (instruction.getOpcode() == Opcodes.INVOKESPECIAL
                    && ((MethodInsnNode) instruction).name.equals("<init>")) {
                int index = getStackSize() - Type.getArgumentTypes(((MethodInsnNode) instruction).desc).length - 1;
                if (index >= 0 && getStack(index) instanceof Uninitialized) {
                    initialized = getStack(index);
                }
            }
            super.execute(instruction, interpreter);
            if (initialized != null) {
                BasicValue value = new BasicValue(initialized.getType());
                for (int index = 0; index < getLocals(); index++) {
                    if (getLocal(index).equals(initialized)) {
                        setLocal(index, value);
                    }
                }
                for (int index = 0; index < getStackSize(); index++) {
                    if (getStack(index).equals(initialized)) {
                        setStack(index, value);
                    }
                }
            }
        }
    }

    private static final class Values extends BasicInterpreter {
        private final Map<AbstractInsnNode, BasicValue> allocations = new HashMap<>();

        Values() {
            super(Opcodes.ASM9);
        }

        @Override
        public BasicValue newOperation(AbstractInsnNode instruction) throws AnalyzerException {
            if (instruction.getOpcode() == Opcodes.NEW) {
                return allocations.computeIfAbsent(instruction,
                        node -> new Uninitialized(Type.getObjectType(((TypeInsnNode) node).desc), node));
            }
            return super.newOperation(instruction);
        }

        @Override
        public BasicValue merge(BasicValue first, BasicValue second) {
            if (first instanceof Uninitialized || second instanceof Uninitialized) {
                return first.equals(second) ? first : BasicValue.UNINITIALIZED_VALUE;
            }
            return super.merge(first, second);
        }
    }

    private static final class Uninitialized extends BasicValue {
        private final AbstractInsnNode allocation;

        Uninitialized(Type type, AbstractInsnNode allocation) {
            super(type);
            this.allocation = allocation;
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof Uninitialized && ((Uninitialized) value).allocation == allocation;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(allocation);
        }
    }
}
