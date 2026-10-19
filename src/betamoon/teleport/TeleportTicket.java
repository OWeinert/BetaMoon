package betamoon.teleport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Retainable request status. A terminal ticket retains only detached data. */
public final class TeleportTicket {
    public final String id = UUID.randomUUID().toString();
    private String state = "queued";
    private boolean cancellationRequested;
    private int elapsed;
    private int chunks;
    private int checks;
    private String stage = "queued";
    private Map<String, Object> result;

    public synchronized String state() {
        return state;
    }

    public synchronized Map<String, Object> progress() {
        Map<String, Object> progress = new LinkedHashMap<String, Object>();
        progress.put("id", id);
        progress.put("state", state);
        progress.put("stage", stage);
        progress.put("elapsedTicks", elapsed);
        progress.put("preparedChunks", chunks);
        progress.put("candidateChecks", checks);
        return Collections.unmodifiableMap(progress);
    }

    public synchronized Map<String, Object> result() {
        return result;
    }

    public synchronized boolean cancel() {
        if (state.equals("cancelled")) {
            return true;
        }
        if (!canCancel()) {
            return false;
        }
        cancellationRequested = true;
        return true;
    }

    public synchronized boolean canCancel() {
        return state.equals("queued") || state.equals("preparing");
    }

    synchronized boolean cancellationRequested() {
        return cancellationRequested;
    }

    synchronized void update(String state, String stage, int elapsed, int chunks, int checks) {
        if (result != null) {
            throw new IllegalStateException("Cannot update a terminal teleport ticket");
        }
        this.state = state;
        this.stage = stage;
        this.elapsed = elapsed;
        this.chunks = chunks;
        this.checks = checks;
        TeleportDiagnostics.stage(id, state, stage, elapsed, chunks, checks);
    }

    synchronized void finish(String reason, TeleportLocation source, TeleportLocation target, boolean fallback,
            boolean remembered) {
        finish(reason, source, target, fallback, remembered, "teleport", null);
    }

    synchronized void finish(String reason, TeleportLocation source, TeleportLocation target, boolean fallback,
            boolean remembered, String operation, String fallbackReason) {
        if (result != null) {
            return;
        }
        boolean success = reason == null;
        state = success
                ? "succeeded"
                : "cancelled".equals(reason) || "owner_unloaded".equals(reason) || "session_changed".equals(reason)
                        ? "cancelled"
                        : "failed";
        stage = state;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("success", success);
        result.put("state", state);
        if (reason != null) {
            result.put("reason", reason);
        }
        result.put("source", source == null ? null : Collections.unmodifiableMap(source.describe()));
        result.put("destination", target == null ? null : Collections.unmodifiableMap(target.describe()));
        result.put("usedFallback", fallback);
        result.put("returnRemembered", remembered);
        result.put("operation", operation);
        if (fallbackReason != null) {
            result.put("fallbackReason", fallbackReason);
        }
        this.result = Collections.unmodifiableMap(result);
        TeleportDiagnostics.finish(id, this.result);
    }
}
