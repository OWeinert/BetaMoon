package betamoon.instrumentation.agent;

import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.registry.BuiltinHookModules;
import betamoon.instrumentation.registry.ClassTransformPlan;
import betamoon.instrumentation.registry.HookRegistry;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.Map;

/** JVM entry point that installs the BetaMoon instrumentation framework. */
public final class BetaMoonAgent {
    public static final String STATUS_PROPERTY = AgentRuntime.STATUS_PROPERTY;
    private static final String MAPPINGS_RESOURCE =
        "/betamoon/instrumentation/mapping/b1.7.3.tiny";

    private BetaMoonAgent() {
    }

    public static void premain(String rawOptions, Instrumentation instrumentation) {
        AgentStatus currentStatus = AgentRuntime.getStatus();
        if (currentStatus == AgentStatus.INITIALIZING || currentStatus == AgentStatus.ACTIVE
            || currentStatus == AgentStatus.DEGRADED) {
            System.out.println("[BetaMoon Agent] Ignoring duplicate initialization; current status is "
                + currentStatus);
            return;
        }
        TransformationReport report = new TransformationReport();
        AgentRuntime.initialize(report);
        AgentOptions options = null;
        try {
            options = AgentOptions.parse(rawOptions);
            TinyMappingResolver mappings = loadMappings();
            HookRegistry registry = new HookRegistry(report);
            BuiltinHookModules.registerAll(registry);
            Map<String, ClassTransformPlan> plans = registry.freeze(mappings,
                RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT);
            instrumentation.addTransformer(new BetaMoonTransformer(plans, mappings, report,
                options.isStrict(), options.isDebug()), false);
            AgentRuntime.activate();
            System.out.println("[BetaMoon Agent] Instrumentation active with " + plans.size()
                + " target class name(s)");
        } catch (Throwable error) {
            String message = "Agent initialization failed: " + error.getMessage();
            AgentRuntime.fail(message);
            System.err.println("[BetaMoon Agent] " + message);
            error.printStackTrace(System.err);
            if (options != null && options.isStrict()) {
                throw new IllegalStateException(message, error);
            }
        }
    }

    public static boolean isRegistered() {
        return AgentRuntime.isRegistered();
    }

    public static AgentStatus getStatus() {
        return AgentRuntime.getStatus();
    }

    public static String getFailureMessage() {
        return AgentRuntime.getFailureMessage();
    }

    private static TinyMappingResolver loadMappings() throws Exception {
        InputStream input = BetaMoonAgent.class.getResourceAsStream(MAPPINGS_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Bundled mappings are missing: " + MAPPINGS_RESOURCE);
        }
        return TinyMappingResolver.read(input);
    }
}
