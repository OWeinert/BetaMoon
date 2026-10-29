package betamoon.instrumentation.transform;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookPreparation;
import betamoon.interop.catalog.CatalogFingerprint;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Only exact normal returns of a verified initialized declaring constructor
 * enter observation.
 */
final class ScriptConstructorTransformer {
    private static final String DISPATCHER = "betamoon/hooks/HookDispatcher";
    private static final Type OBJECT = Type.getType(Object.class);

    private ScriptConstructorTransformer() {
    }

    static void apply(ClassNode owner, MethodNode method, HookPreparation.Site site) throws Exception {
        if (!method.name.equals("<init>") || !method.desc.endsWith(")V") || (method.access
                & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED)) != 0
                || !site.points.isEmpty() || site.around) {
            throw new HookFailure("match_failed", "constructed requires an ordinary declaring constructor");
        }
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            int opcode = instruction.getOpcode();
            if (opcode == Opcodes.RETURN) {
                returns.add(instruction);
            }
            if (opcode == Opcodes.JSR || opcode == Opcodes.RET || instruction instanceof MethodInsnNode
                    && ((MethodInsnNode) instruction).owner.equals(DISPATCHER)) {
                throw new HookFailure("match_failed", "legacy subroutine or existing constructor envelope");
            }
        }
        site.normalReturns = returns.size();
        site.structureAnalyzed = true;
        site.fingerprint = CatalogFingerprint.sha256(
                ("envelope:5:" + site.target.key() + ":flags:" + method.access + ":normalReturns:" + returns.size()
                        + ":completion:declaring_constructor_return").getBytes(StandardCharsets.UTF_8));
        Type[] parameters = Type.getArgumentTypes(method.desc);
        int savedThis = method.maxLocals++;
        int[] savedParameters = new int[parameters.length];
        int parameter = 1;
        MethodNode capture = new MethodNode();
        InstructionAdapter entry = new InstructionAdapter(capture);
        // Copying uninitialized-this into a local is legal. It never enters Java/Lua
        // until
        // the verifier proves the initializing invokespecial has completed on this
        // path.
        entry.load(0, OBJECT);
        entry.store(savedThis, OBJECT);
        for (int index = 0; index < parameters.length; index++) {
            savedParameters[index] = method.maxLocals;
            method.maxLocals += parameters[index].getSize();
            entry.load(parameter, parameters[index]);
            entry.store(savedParameters[index], parameters[index]);
            parameter += parameters[index].getSize();
        }
        if (method.maxLocals > 65535) {
            throw new HookFailure("match_failed", "constructor observation exceeds JVM local-variable limits");
        }
        method.instructions.insert(capture.instructions);
        // Analyzer needs additional stack room for the newly inserted local copies.
        method.maxStack = Math.max(method.maxStack, 2);
        AbstractInsnNode[] instructions = method.instructions.toArray();
        Frame<BasicValue>[] frames = new HookOperandAnalyzer(true).analyze(owner.name, method);
        for (int index = 0; index < instructions.length; index++) {
            AbstractInsnNode instruction = instructions[index];
            if (instruction.getOpcode() != Opcodes.RETURN) {
                continue;
            }
            Frame<BasicValue> frame = frames[index];
            if (frame == null) {
                continue;
            }
            BasicValue receiver = frame.getLocal(savedThis);
            if (HookOperandAnalyzer.uninitialized(receiver) || receiver.getType() == null
                    || !receiver.getType().equals(Type.getObjectType(owner.name))) {
                throw new HookFailure("match_failed",
                        "constructor return has no proven initialized declaring receiver");
            }
            MethodNode notification = new MethodNode();
            InstructionAdapter code = new InstructionAdapter(notification);
            LabelNode dormant = new LabelNode();
            dormant.getLabel().info = dormant;
            code.iconst(site.id);
            code.iconst(1);
            code.aconst(Type.getObjectType(owner.name));
            code.invokestatic(DISPATCHER, "active", "(IILjava/lang/Class;)Z", false);
            code.ifeq(dormant.getLabel());
            code.iconst(site.id);
            code.aconst(Type.getObjectType(owner.name));
            code.load(savedThis, OBJECT);
            code.iconst(parameters.length);
            code.newarray(OBJECT);
            for (int argument = 0; argument < parameters.length; argument++) {
                code.dup();
                code.iconst(argument);
                code.load(savedParameters[argument], parameters[argument]);
                ScriptHookTransformer.box(code, parameters[argument]);
                code.astore(OBJECT);
            }
            code.aconst(null);
            code.invokestatic(DISPATCHER, "after",
                    "(ILjava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    false);
            code.pop();
            notification.instructions.add(dormant);
            method.instructions.insertBefore(instruction, notification.instructions);
        }
    }
}
