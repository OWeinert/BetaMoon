package betamoon.instrumentation.agent;

/** Overall lifecycle state of the BetaMoon instrumentation agent. */
public enum AgentStatus {
    NOT_LOADED,
    INITIALIZING,
    ACTIVE,
    DEGRADED,
    FAILED
}
