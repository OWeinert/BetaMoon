package betamoon.instrumentation.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe record of registered, applied, and failed hooks. */
public final class TransformationReport {
    private final Map<String, HookDiagnostic> diagnostics = new ConcurrentHashMap<String, HookDiagnostic>();

    public void registered(String hookId) {
        diagnostics.put(hookId, new HookDiagnostic(hookId, HookStatus.REGISTERED, "Registered"));
    }

    public void registered(String hookId, String target, String matchRequirement, boolean required) {
        diagnostics.put(hookId, new HookDiagnostic(hookId, HookStatus.REGISTERED, "Registered", target,
                matchRequirement, required));
    }

    public void waiting(String hookId, String target) {
        update(hookId, HookStatus.WAITING_FOR_TARGET, "Waiting for " + target, target);
    }

    public void applied(String hookId, String target) {
        update(hookId, HookStatus.APPLIED, "Applied to " + target, target);
    }

    public void alreadyApplied(String hookId, String target) {
        update(hookId, HookStatus.ALREADY_APPLIED, "Already applied to " + target, target);
    }

    public void noMatch(String hookId, String target) {
        update(hookId, HookStatus.NO_MATCH, "No call site found in " + target, target);
    }

    public void failed(String hookId, String message) {
        update(hookId, HookStatus.FAILED, message, null);
    }

    private void update(String hookId, HookStatus status, String message, String target) {
        HookDiagnostic previous = diagnostics.get(hookId);
        diagnostics.put(hookId, new HookDiagnostic(hookId, status, message,
                target == null && previous != null ? previous.getTarget() : target,
                previous == null ? null : previous.getMatchRequirement(), previous != null && previous.isRequired()));
    }

    public boolean hasFailures() {
        for (HookDiagnostic diagnostic : diagnostics.values()) {
            if (diagnostic.getStatus() == HookStatus.FAILED) {
                return true;
            }
        }
        return false;
    }

    public List<HookDiagnostic> snapshot() {
        List<HookDiagnostic> snapshot = new ArrayList<HookDiagnostic>(diagnostics.values());
        Collections.sort(snapshot, new Comparator<HookDiagnostic>() {
            public int compare(HookDiagnostic left, HookDiagnostic right) {
                return left.getHookId().compareTo(right.getHookId());
            }
        });
        return Collections.unmodifiableList(snapshot);
    }
}
