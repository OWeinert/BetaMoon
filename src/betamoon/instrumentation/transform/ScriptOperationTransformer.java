package betamoon.instrumentation.transform;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookOperationSite;
import betamoon.hooks.HookPreparation;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Spills only selected operands and preserves the original invocation/field
 * instruction and stack below it.
 */
final class ScriptOperationTransformer {
    private static final Type OBJECT = Type.getType(Object.class);
    private static final Type OBJECTS = Type.getType(Object[].class);
    private static final String DISPATCHER = "betamoon/hooks/HookDispatcher";

    private ScriptOperationTransformer() {
    }

    static void apply(ClassNode owner, MethodNode method, HookPreparation.Site site,
            List<ScriptOperationPlanner.Selected> selected, int[] enclosingLocals) {
        Type[] enclosingTypes = Type.getArgumentTypes(method.desc);
        boolean enclosingInstance = (method.access & Opcodes.ACC_STATIC) == 0;
        for (ScriptOperationPlanner.Selected operand : selected) {
            HookOperationSite shape = operand.before == null ? operand.after : operand.before;
            boolean field = !(operand.instruction instanceof MethodInsnNode);
            boolean write = shape.kind.equals("field_write");
            Type[] arguments = field
                    ? write ? new Type[]{Type.getType(shape.descriptor)} : new Type[0]
                    : Type.getArgumentTypes(shape.descriptor);
            Type result = field
                    ? write ? Type.VOID_TYPE : Type.getType(shape.descriptor)
                    : Type.getReturnType(shape.descriptor);
            int receiverLocal = shape.instance() ? allocate(method, OBJECT) : -1;
            int[] locals = new int[arguments.length];
            for (int index = 0; index < arguments.length; index++) {
                locals[index] = allocate(method, arguments[index]);
            }
            int frameLocal = allocate(method, OBJECTS);
            int resultLocal = result.getSort() == Type.VOID ? -1 : allocate(method, result);
            int before = operand.before == null ? -1 : operand.before.id;
            int after = operand.after == null ? -1 : operand.after.id;

            MethodNode prefix = new MethodNode();
            InstructionAdapter code = new InstructionAdapter(prefix);
            LabelNode dormant = label();
            LabelNode original = label();
            code.iconst(site.id);
            code.iconst(before);
            code.iconst(after);
            code.aconst(Type.getObjectType(owner.name));
            code.invokestatic(DISPATCHER, "activeOperation", "(IIILjava/lang/Class;)Z", false);
            code.ifeq(dormant.getLabel());
            for (int index = arguments.length - 1; index >= 0; index--) {
                code.store(locals[index], arguments[index]);
            }
            if (shape.instance()) {
                code.store(receiverLocal, OBJECT);
            }
            if (write && arguments[0].getSort() >= Type.BOOLEAN && arguments[0].getSort() <= Type.SHORT) {
                // JVM stores narrow their computational int; expose the actual field-typed
                // value to Lua.
                code.load(locals[0], arguments[0]);
                if (arguments[0].getSort() == Type.BOOLEAN) {
                    code.iconst(1);
                    code.and(Type.INT_TYPE);
                } else {
                    code.cast(Type.INT_TYPE, arguments[0]);
                }
                code.store(locals[0], arguments[0]);
            }
            code.iconst(site.id);
            code.iconst(before);
            code.iconst(after);
            code.aconst(Type.getObjectType(owner.name));
            if (enclosingInstance) {
                code.load(0, OBJECT);
            } else {
                code.aconst(null);
            }
            packed(code, enclosingTypes, enclosingLocals);
            if (shape.instance()) {
                code.load(receiverLocal, OBJECT);
            } else {
                code.aconst(null);
            }
            packed(code, arguments, locals);
            code.invokestatic(DISPATCHER, "beginOperation",
                    "(IIILjava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)[Ljava/lang/Object;",
                    false);
            code.store(frameLocal, OBJECTS);
            if (shape.instance()) {
                code.load(receiverLocal, OBJECT);
            }
            for (int index = 0; index < arguments.length; index++) {
                code.load(frameLocal, OBJECTS);
                code.iconst(0);
                code.aload(OBJECT);
                code.checkcast(OBJECTS);
                code.iconst(index);
                code.aload(OBJECT);
                ScriptHookTransformer.unbox(code, arguments[index]);
            }
            // The native operands are now on the stack. Clear temporary duplicate object
            // roots.
            if (shape.instance()) {
                clear(code, receiverLocal);
            }
            for (int index = 0; index < arguments.length; index++) {
                if (arguments[index].getSort() >= Type.ARRAY) {
                    clear(code, locals[index]);
                }
            }
            if (after < 0) {
                clear(code, frameLocal);
            }
            code.goTo(original.getLabel());
            prefix.instructions.add(dormant);
            clear(code, frameLocal);
            prefix.instructions.add(original);
            method.instructions.insertBefore(operand.instruction, prefix.instructions);

            if (after < 0) {
                continue;
            }
            MethodNode suffix = new MethodNode();
            InstructionAdapter finished = new InstructionAdapter(suffix);
            if (resultLocal >= 0) {
                finished.store(resultLocal, result);
            }
            LabelNode unchanged = label();
            finished.load(frameLocal, OBJECTS);
            finished.ifnull(unchanged.getLabel());
            finished.load(frameLocal, OBJECTS);
            if (resultLocal < 0) {
                finished.aconst(null);
            } else {
                finished.load(resultLocal, result);
                ScriptHookTransformer.box(finished, result);
            }
            finished.invokestatic(DISPATCHER, "finishOperation",
                    "([Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            if (resultLocal < 0) {
                finished.pop();
            } else {
                ScriptHookTransformer.unbox(finished, result);
                finished.store(resultLocal, result);
            }
            clear(finished, frameLocal);
            suffix.instructions.add(unchanged);
            if (resultLocal >= 0) {
                finished.load(resultLocal, result);
                if (result.getSort() >= Type.ARRAY) {
                    clear(finished, resultLocal);
                }
            }
            method.instructions.insert(operand.instruction, suffix.instructions);
        }
    }

    private static int allocate(MethodNode method, Type type) {
        int local = method.maxLocals;
        method.maxLocals += type.getSize();
        if (method.maxLocals > 65535) {
            throw new HookFailure("match_failed", "operation spills exceed JVM local-variable limits");
        }
        return local;
    }

    private static void packed(InstructionAdapter code, Type[] types, int[] locals) {
        code.iconst(types.length);
        code.newarray(OBJECT);
        for (int index = 0; index < types.length; index++) {
            code.dup();
            code.iconst(index);
            code.load(locals[index], types[index]);
            ScriptHookTransformer.box(code, types[index]);
            code.astore(OBJECT);
        }
    }

    private static void clear(InstructionAdapter code, int local) {
        code.aconst(null);
        code.store(local, OBJECT);
    }

    private static LabelNode label() {
        LabelNode node = new LabelNode();
        node.getLabel().info = node;
        return node;
    }
}
