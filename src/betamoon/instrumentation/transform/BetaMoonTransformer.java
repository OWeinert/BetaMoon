package betamoon.instrumentation.transform;

import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.MappingResolver;
import betamoon.instrumentation.registry.ClassTransformPlan;
import betamoon.instrumentation.registry.PlannedHook;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** Applies immutable transformation plans as JVM classes are defined. */
public final class BetaMoonTransformer implements ClassFileTransformer {
    private final Map<String, ClassTransformPlan> plans;
    private final TransformationReport report;
    private final AroundMethodInjector aroundInjector;
    private final CallRedirectInjector callRedirectInjector;
    private final boolean strict;
    private final boolean debug;

    public BetaMoonTransformer(Map<String, ClassTransformPlan> plans, MappingResolver mappings,
            TransformationReport report, boolean strict, boolean debug) {
        this.plans = plans;
        this.report = report;
        this.aroundInjector = new AroundMethodInjector(mappings);
        this.callRedirectInjector = new CallRedirectInjector(mappings);
        this.strict = strict;
        this.debug = debug;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null || classfileBuffer == null) {
            return null;
        }
        ClassTransformPlan plan = plans.get(className);
        if (plan == null) {
            return null;
        }

        List<HookResult> results = new ArrayList<HookResult>();
        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(classfileBuffer).accept(classNode, 0);
            boolean modified = false;
            for (PlannedHook hook : plan.getHooks()) {
                HookTransformOutcome outcome = hook.getDefinition() instanceof CallRedirectHookDefinition
                        ? callRedirectInjector.apply(classNode, hook)
                        : aroundInjector.apply(classNode, hook);
                results.add(new HookResult(hook, outcome));
                modified |= outcome == HookTransformOutcome.APPLIED;
            }
            if (!modified) {
                recordResults(results, className);
                return null;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            byte[] transformed = writer.toByteArray();
            recordResults(results, className);
            if (debug) {
                System.out
                        .println("[BetaMoon Agent] Transformed " + className + " with " + results.size() + " hook(s)");
            }
            return transformed;
        } catch (Throwable error) {
            String message = "Failed to transform " + className + ": " + error.getMessage();
            for (PlannedHook hook : plan.getHooks()) {
                report.failed(hook.getDefinition().getId(), message);
            }
            AgentRuntime.markDegraded(message);
            System.err.println("[BetaMoon Agent] " + message);
            error.printStackTrace(System.err);
            if (strict) {
                IllegalClassFormatException failure = new IllegalClassFormatException(message);
                failure.initCause(error);
                throw failure;
            }
            return null;
        }
    }

    private void recordResults(List<HookResult> results, String target) {
        for (HookResult result : results) {
            String hookId = result.hook.getDefinition().getId();
            if (result.outcome == HookTransformOutcome.APPLIED) {
                report.applied(hookId, target);
            } else if (result.outcome == HookTransformOutcome.ALREADY_APPLIED) {
                report.alreadyApplied(hookId, target);
            } else {
                report.noMatch(hookId, target);
            }
        }
    }

    private static final class HookResult {
        private final PlannedHook hook;
        private final HookTransformOutcome outcome;

        private HookResult(PlannedHook hook, HookTransformOutcome outcome) {
            this.hook = hook;
            this.outcome = outcome;
        }
    }
}
