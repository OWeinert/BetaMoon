package betamoon.instrumentation.api;

import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;

/**
 * Registration-time preflight without defining target classes before their
 * transformer is installed.
 */
public final class RequiredNativeHooks {
    private RequiredNativeHooks() {
    }

    public static void require(String feature, boolean allowPending, String... ids) {
        if (!AgentRuntime.isRegistered()) {
            throw new IllegalStateException(feature + " requires the BetaMoon Java agent");
        }
        for (String id : ids) {
            boolean available = false;
            for (HookDiagnostic diagnostic : AgentRuntime.getReport().snapshot()) {
                if (id.equals(diagnostic.getHookId())) {
                    HookStatus status = diagnostic.getStatus();
                    available = status == HookStatus.APPLIED || status == HookStatus.ALREADY_APPLIED
                            || allowPending && status == HookStatus.WAITING_FOR_TARGET;
                    break;
                }
            }
            if (!available) {
                throw new IllegalStateException(feature + " requires unavailable native hook: " + id);
            }
        }
    }
}
