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

    public void waiting(String hookId, String target) {
        diagnostics.put(hookId, new HookDiagnostic(hookId, HookStatus.WAITING_FOR_TARGET,
            "Waiting for " + target));
    }

    public void applied(String hookId, String target) {
        diagnostics.put(hookId, new HookDiagnostic(hookId, HookStatus.APPLIED,
            "Applied to " + target));
    }

    public void failed(String hookId, String message) {
        diagnostics.put(hookId, new HookDiagnostic(hookId, HookStatus.FAILED, message));
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
