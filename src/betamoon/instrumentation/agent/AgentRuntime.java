package betamoon.instrumentation.agent;

import betamoon.instrumentation.diagnostics.TransformationReport;

/** Shared agent state that is safe to query from the regular mod lifecycle. */
public final class AgentRuntime {
    public static final String STATUS_PROPERTY = "betamoon.agent.status";
    public static final String FAILURE_PROPERTY = "betamoon.agent.failure";

    private static volatile AgentStatus status = AgentStatus.NOT_LOADED;
    private static volatile String failureMessage;
    private static volatile TransformationReport report = new TransformationReport();

    private AgentRuntime() {
    }

    static void initialize(TransformationReport transformationReport) {
        report = transformationReport;
        failureMessage = null;
        System.clearProperty(FAILURE_PROPERTY);
        setStatus(AgentStatus.INITIALIZING);
    }

    static void activate() {
        setStatus(AgentStatus.ACTIVE);
    }

    public static void markDegraded(String message) {
        setFailureMessage(message);
        setStatus(AgentStatus.DEGRADED);
    }

    static void fail(String message) {
        setFailureMessage(message);
        setStatus(AgentStatus.FAILED);
    }

    public static AgentStatus getStatus() {
        AgentStatus current = status;
        if (current != AgentStatus.NOT_LOADED) {
            return current;
        }
        String property = System.getProperty(STATUS_PROPERTY);
        if (property != null) {
            try {
                return AgentStatus.valueOf(property);
            } catch (IllegalArgumentException ignored) {
                // A foreign or older agent value should behave as not loaded.
            }
        }
        return AgentStatus.NOT_LOADED;
    }

    public static String getFailureMessage() {
        String current = failureMessage;
        return current != null ? current : System.getProperty(FAILURE_PROPERTY);
    }

    public static TransformationReport getReport() {
        return report;
    }

    public static boolean isRegistered() {
        AgentStatus current = getStatus();
        if (current == AgentStatus.ACTIVE || current == AgentStatus.DEGRADED) {
            return true;
        }
        return false;
    }

    private static void setStatus(AgentStatus value) {
        status = value;
        System.setProperty(STATUS_PROPERTY, value.name());
    }

    private static void setFailureMessage(String message) {
        failureMessage = message;
        if (message == null) {
            System.clearProperty(FAILURE_PROPERTY);
        } else {
            System.setProperty(FAILURE_PROPERTY, message);
        }
    }
}
