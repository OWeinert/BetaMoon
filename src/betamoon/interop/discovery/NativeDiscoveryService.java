package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.source.NativeSourceAttachment;
import betamoon.interop.source.NativeSourceIndex;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * One worker and an atomic client-boundary publication. Owner tickets are
 * leases, not callback captures.
 */
public final class NativeDiscoveryService implements AutoCloseable {
    private final Thread clientThread = Thread.currentThread();
    private final NativeOriginCache cache;
    private final ThreadPoolExecutor worker;
    private final List<Ticket> tickets = new ArrayList<>();
    private volatile NativeIndexSnapshot published = NativeIndexSnapshot.empty();
    private Future<CompletedScan> pending;
    private long nextGeneration = 1;
    private long pendingGeneration;
    private String lastFailure;
    private Long lastScanNanos;
    private boolean closed;

    public NativeDiscoveryService(Path cacheRoot) {
        this(cacheRoot, action -> {
            Thread thread = new Thread(action, "BetaMoon native metadata");
            thread.setDaemon(true);
            return thread;
        });
    }

    NativeDiscoveryService(Path cacheRoot, ThreadFactory factory) {
        cache = new NativeOriginCache(cacheRoot);
        worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), factory);
    }

    public NativeIndexSnapshot snapshot() {
        return published;
    }

    public Ticket request(NativeLoaderRegistry.Capture capture, String namespace,
            List<NativeSourceAttachment> attachments) {
        requireClient();
        if (closed) {
            throw new IllegalStateException("native_discovery_closed");
        }
        if (tickets.size() >= 256) {
            throw new IllegalStateException("scan_ticket_limit");
        }
        if (pending == null) {
            pendingGeneration = nextGeneration++;
            lastFailure = null;
            final long generation = pendingGeneration;
            List<NativeSourceAttachment> sources = Collections.unmodifiableList(new ArrayList<>(attachments));
            pending = worker.submit(() -> {
                long started = System.nanoTime();
                NativeIndexSnapshot snapshot = scan(generation, capture, namespace, sources);
                return new CompletedScan(snapshot, System.nanoTime() - started);
            });
        }
        Ticket ticket = new Ticket(pendingGeneration);
        tickets.add(ticket);
        return ticket;
    }

    private NativeIndexSnapshot scan(long generation, NativeLoaderRegistry.Capture capture, String namespace,
            List<NativeSourceAttachment> attachments) throws IOException {
        List<String> warnings = new ArrayList<>(capture.warnings);
        BundledNativeCatalog bundled = null;
        try {
            bundled = new BundledNativeCatalog(namespace);
        } catch (IOException | RuntimeException exception) {
            NativeOriginCache.warn(warnings, "bundled_catalog_unavailable: " + exception.getMessage());
        }
        NativeSourceIndex sources = new NativeSourceIndex(attachments, cache,
                bundled == null ? Collections.emptyList() : bundled.sourceFiles, warnings);
        NativeIndexSnapshot.Builder builder = new NativeIndexSnapshot.Builder(generation, namespace, bundled, capture,
                sources);
        for (NativeOriginInput origin : capture.origins) {
            OriginMetadataScanner.interrupted();
            try {
                NativeOriginCache.Result result = cache.load(origin.path,
                        origin.gameOrigin && bundled != null ? bundled.classes : null,
                        origin.gameOrigin && bundled != null ? bundled.inputFingerprint : null, warnings);
                builder.add(origin, result);
            } catch (IOException | RuntimeException exception) {
                OriginMetadataScanner.interrupted();
                String reason = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                builder.failed(origin, reason);
                NativeOriginCache.warn(warnings, "origin_scan_failed: " + origin.id + ": " + reason);
            }
        }
        return builder.build(warnings);
    }

    public boolean publishReady() {
        requireClient();
        if (pending == null || !pending.isDone()) {
            return false;
        }
        try {
            CompletedScan completed = pending.get();
            published = completed.snapshot;
            lastScanNanos = completed.nanos;
            for (Ticket ticket : tickets) {
                ticket.complete("complete", null);
            }
            return true;
        } catch (Exception exception) {
            lastFailure = "scan_failed: " + exception.getMessage();
            for (Ticket ticket : tickets) {
                ticket.complete("failed", lastFailure);
            }
            return false;
        } finally {
            pending = null;
            tickets.clear();
        }
    }

    public Map<String, Object> status() {
        requireClient();
        return CatalogValues.record("status",
                closed
                        ? "closed"
                        : pending != null
                                ? "scanning"
                                : lastFailure != null ? "failed" : published.generation == 0 ? "not_started" : "ready",
                "lastFailure", lastFailure, "generation", published.generation, "pendingGeneration",
                pending == null ? null : pendingGeneration, "coverage", published.coverage, "warnings",
                published.warnings, "work", CatalogValues.record("runningScans", worker.getActiveCount(), "queuedScans",
                        worker.getQueue().size(), "lastScanNanos", lastScanNanos));
    }

    public void requireClientAccess() {
        requireClient();
    }

    private void requireClient() {
        if (Thread.currentThread() != clientThread) {
            throw new IllegalStateException("wrong_thread");
        }
    }

    @Override
    public void close() {
        requireClient();
        closed = true;
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        for (Ticket ticket : tickets) {
            ticket.complete("cancelled", "native_discovery_closed");
        }
        tickets.clear();
        worker.shutdownNow();
        published = NativeIndexSnapshot.empty();
    }

    public final class Ticket implements AutoCloseable {
        public final long generation;
        private String status = "pending";
        private String reason;

        private Ticket(long generation) {
            this.generation = generation;
        }

        private void complete(String status, String reason) {
            if (this.status.equals("pending")) {
                this.status = status;
                this.reason = reason;
            }
        }

        public Map<String, Object> status() {
            requireClient();
            return CatalogValues.record("status", status, "generation", generation, "reason", reason);
        }

        @Override
        public void close() {
            requireClient();
            complete("cancelled", null);
            tickets.remove(this);
            if (tickets.isEmpty() && pending != null) {
                pending.cancel(true);
                pending = null;
                // A cancelled queued Future is otherwise retained until the previous scan
                // finishes.
                worker.purge();
            }
        }
    }

    private static final class CompletedScan {
        private final NativeIndexSnapshot snapshot;
        private final long nanos;

        private CompletedScan(NativeIndexSnapshot snapshot, long nanos) {
            this.snapshot = snapshot;
            this.nanos = nanos;
        }
    }
}
