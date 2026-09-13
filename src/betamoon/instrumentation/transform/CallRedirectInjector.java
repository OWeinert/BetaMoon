package betamoon.instrumentation.transform;

import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.mapping.MappingResolver;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.registry.PlannedHook;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Stack-preserving redirection; the static handler receives the original
 * virtual receiver first.
 */
final class CallRedirectInjector {
    private final MappingResolver mappings;

    CallRedirectInjector(MappingResolver mappings) {
        this.mappings = mappings;
    }

    HookTransformOutcome apply(ClassNode classNode, PlannedHook planned) throws HookTransformException {
        CallRedirectHookDefinition definition = (CallRedirectHookDefinition) planned.getDefinition();
        for (RuntimeNamespace namespace : planned.getNamespaces()) {
            ResolvedMethod target = mappings.resolveMethod(definition.getTarget(), namespace);
            if (!classNode.name.equals(target.getOwner())) {
                continue;
            }
            if (definition.appliesToAllMethods()) {
                return redirectAll(classNode, definition, namespace);
            }
            for (MethodNode method : classNode.methods) {
                if (method.name.equals(target.getName()) && method.desc.equals(target.getDescriptor())) {
                    return redirect(method, definition, namespace);
                }
            }
        }
        throw failure(definition, "Target method not found");
    }

    private HookTransformOutcome redirectAll(ClassNode classNode, CallRedirectHookDefinition definition,
            RuntimeNamespace namespace) throws HookTransformException {
        ResolvedMethod invocation = mappings.resolveMethod(definition.getInvocation(), namespace);
        ResolvedMethod handler = mappings.resolveMethod(definition.getHandler().getMethod(), namespace);
        validateHandler(definition, invocation, handler);

        List<MethodInsnNode> originals = new ArrayList<MethodInsnNode>();
        int redirects = 0;
        for (MethodNode method : classNode.methods) {
            CallCounts counts = findCalls(method, invocation, handler);
            originals.addAll(counts.originals);
            redirects += counts.redirects;
        }
        if (!originals.isEmpty() && redirects == 0) {
            for (MethodInsnNode call : originals) {
                replace(call, handler);
            }
            return HookTransformOutcome.APPLIED;
        }
        if (originals.isEmpty() && redirects > 0) {
            return HookTransformOutcome.ALREADY_APPLIED;
        }
        if (originals.isEmpty()) {
            return HookTransformOutcome.NO_MATCH;
        }
        throw failure(definition, "Found partial or conflicting instrumentation with " + originals.size()
                + " originals and " + redirects + " redirects");
    }

    private HookTransformOutcome redirect(MethodNode method, CallRedirectHookDefinition definition,
            RuntimeNamespace namespace) throws HookTransformException {
        ResolvedMethod invocation = mappings.resolveMethod(definition.getInvocation(), namespace);
        ResolvedMethod handler = mappings.resolveMethod(definition.getHandler().getMethod(), namespace);
        validateHandler(definition, invocation, handler);
        CallCounts counts = findCalls(method, invocation, handler);
        if (counts.originals.isEmpty() && counts.redirects == 1) {
            return HookTransformOutcome.ALREADY_APPLIED;
        }
        if (counts.originals.size() != 1 || counts.redirects != 0) {
            throw failure(definition, "Expected one original call; found " + counts.originals.size() + " originals and "
                    + counts.redirects + " redirects");
        }
        replace(counts.originals.get(0), handler);
        return HookTransformOutcome.APPLIED;
    }

    private void validateHandler(CallRedirectHookDefinition definition, ResolvedMethod invocation,
            ResolvedMethod handler) throws HookTransformException {
        String expected = "(L" + invocation.getOwner() + ";" + invocation.getDescriptor().substring(1);
        if (!handler.getDescriptor().equals(expected)) {
            throw failure(definition, "Handler must preserve receiver and arguments");
        }
    }

    private CallCounts findCalls(MethodNode method, ResolvedMethod invocation, ResolvedMethod handler) {
        List<MethodInsnNode> originals = new ArrayList<MethodInsnNode>();
        int redirects = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (matches(call, invocation, Opcodes.INVOKEVIRTUAL)
                        || matches(call, invocation, Opcodes.INVOKEINTERFACE)) {
                    originals.add(call);
                } else if (matches(call, handler, Opcodes.INVOKESTATIC)) {
                    redirects++;
                }
            }
        }
        return new CallCounts(originals, redirects);
    }

    private void replace(MethodInsnNode original, ResolvedMethod handler) {
        original.owner = handler.getOwner();
        original.name = handler.getName();
        original.desc = handler.getDescriptor();
        original.setOpcode(Opcodes.INVOKESTATIC);
        original.itf = false;
    }

    private boolean matches(MethodInsnNode call, ResolvedMethod method, int opcode) {
        return call.getOpcode() == opcode && call.owner.equals(method.getOwner()) && call.name.equals(method.getName())
                && call.desc.equals(method.getDescriptor());
    }

    private HookTransformException failure(CallRedirectHookDefinition definition, String message) {
        return new HookTransformException(definition.getId(), message);
    }

    private static final class CallCounts {
        private final List<MethodInsnNode> originals;
        private final int redirects;

        private CallCounts(List<MethodInsnNode> originals, int redirects) {
            this.originals = originals;
            this.redirects = redirects;
        }
    }
}
