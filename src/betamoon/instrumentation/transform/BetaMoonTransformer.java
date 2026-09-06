package betamoon.instrumentation.transform;

import betamoon.instrumentation.agent.AgentRuntime;
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
    private final boolean strict;
    private final boolean debug;

    public BetaMoonTransformer(Map<String, ClassTransformPlan> plans, MappingResolver mappings,
        TransformationReport report, boolean strict, boolean debug) {
        this.plans = plans;
        this.report = report;
        this.aroundInjector = new AroundMethodInjector(mappings);
        this.strict = strict;
        this.debug = debug;
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null || classfileBuffer == null) {
            return null;
        }
        ClassTransformPlan plan = plans.get(className);
        if (plan == null) {
            return null;
        }

        List<PlannedHook> attempted = new ArrayList<PlannedHook>();
        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(classfileBuffer).accept(classNode, 0);
            boolean modified = false;
            for (PlannedHook hook : plan.getHooks()) {
                attempted.add(hook);
                modified |= aroundInjector.apply(classNode, hook);
            }
            if (!modified) {
                markApplied(attempted, className + " (already transformed)");
                return null;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            byte[] transformed = writer.toByteArray();
            markApplied(attempted, className);
            if (debug) {
                System.out.println("[BetaMoon Agent] Transformed " + className + " with "
                    + attempted.size() + " hook(s)");
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

    private void markApplied(List<PlannedHook> hooks, String target) {
        for (PlannedHook hook : hooks) {
            report.applied(hook.getDefinition().getId(), target);
        }
    }
}
