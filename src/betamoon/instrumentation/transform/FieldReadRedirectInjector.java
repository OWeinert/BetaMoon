package betamoon.instrumentation.transform;

import betamoon.instrumentation.api.FieldReadRedirectHookDefinition;
import betamoon.instrumentation.mapping.MappingResolver;
import betamoon.instrumentation.mapping.ResolvedField;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.registry.PlannedHook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Stack-preserving, mapped, idempotent field policies with exact match
 * validation.
 */
final class FieldReadRedirectInjector {
    private final MappingResolver mappings;

    FieldReadRedirectInjector(MappingResolver mappings) {
        this.mappings = mappings;
    }

    HookTransformOutcome apply(ClassNode node, PlannedHook planned) throws HookTransformException {
        FieldReadRedirectHookDefinition definition = (FieldReadRedirectHookDefinition) planned.getDefinition();
        for (RuntimeNamespace namespace : planned.getNamespaces()) {
            ResolvedMethod target = mappings.resolveMethod(definition.getTarget(), namespace);
            if (!node.name.equals(target.getOwner())) {
                continue;
            }
            ResolvedField field = mappings.resolveField(definition.getField(), namespace);
            ResolvedMethod handler = mappings.resolveMethod(definition.getHandler().getMethod(), namespace);
            if (!handler.getDescriptor().equals("(L" + field.getOwner() + ";)" + field.getDescriptor())) {
                throw new HookTransformException(definition.getId(),
                        "Field policy must preserve receiver and value type");
            }
            for (MethodNode method : node.methods) {
                if (!method.name.equals(target.getName()) || !method.desc.equals(target.getDescriptor())) {
                    continue;
                }
                FieldInsnNode original = null;
                int originals = 0;
                int redirects = 0;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode read = (FieldInsnNode) instruction;
                        if (read.getOpcode() == Opcodes.GETFIELD && read.owner.equals(field.getOwner())
                                && read.name.equals(field.getName()) && read.desc.equals(field.getDescriptor())) {
                            original = read;
                            originals++;
                        }
                    } else if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(handler.getOwner())
                                && call.name.equals(handler.getName()) && call.desc.equals(handler.getDescriptor())) {
                            redirects++;
                        }
                    }
                }
                if (originals == 0 && redirects == 1) {
                    return HookTransformOutcome.ALREADY_APPLIED;
                }
                if (originals != 1 || redirects != 0) {
                    throw new HookTransformException(definition.getId(), "Expected one field read; found " + originals
                            + " originals and " + redirects + " redirects");
                }
                method.instructions.set(original, new MethodInsnNode(Opcodes.INVOKESTATIC, handler.getOwner(),
                        handler.getName(), handler.getDescriptor(), false));
                return HookTransformOutcome.APPLIED;
            }
        }
        throw new HookTransformException(definition.getId(), "Target method not found");
    }
}
