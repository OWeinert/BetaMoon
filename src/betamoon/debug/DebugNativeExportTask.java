package betamoon.debug;

import java.util.ArrayList;
import java.util.List;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.discovery.NativeIndexSnapshot;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * One bounded worker; every input is detached before submission. No live game
 * or Lua access occurs here.
 */
public final class DebugNativeExportTask {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), action -> {
                Thread thread = new Thread(action, "BetaMoon native export");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                return thread;
            });
    private static DebugNativeExportTask active;
    static {
        WORKER.allowCoreThreadTimeOut(true);
    }
    private final DebugExportControl control = new Control();
    private final File root;
    private DebugExportDefinition definition;
    private final int totalFiles;
    private DebugExportSession prepared;
    private volatile boolean cancelRequested;
    private volatile boolean done;
    private volatile Thread running;
    private volatile String file = "Preparing native export";
    private volatile int completedFiles;
    private volatile DebugExportResult result;
    private volatile boolean published;

    private DebugNativeExportTask(File root, DebugExportDefinition definition) {
        this.root = root;
        this.definition = definition;
        totalFiles = definition.getFileNames().size();
    }

    static DebugNativeExportTask start(File root, NativeIndexSnapshot snapshot, Map<String, Object> status) {
        return start(root, snapshot, status, false);
    }

    static synchronized DebugNativeExportTask start(File root, NativeIndexSnapshot snapshot, Map<String, Object> status,
            boolean includeOtherCategories) {
        if (active != null) {
            return active;
        }
        Map<String, Object> captured = CatalogValues.object(CatalogValues.freeze(status));
        DebugExportDefinition source = DebugExportCatalog.find(DebugExportCatalog.NATIVE_ACCESS_ID);
        DebugExportDefinition definition = new DebugExportDefinition(source.getId(), source.getTitle(),
                source.getDescription(), false, new DebugNativeExporter(true, () -> snapshot, () -> captured, 100000),
                source.getFileNames().toArray(new String[0]));
        if (!includeOtherCategories || root == null) {
            return submit(root, definition);
        }
        DebugNativeExportTask task = new DebugNativeExportTask(root, definition);
        active = task;
        try {
            List<DebugExportDefinition> live = new ArrayList<>(DebugExportCatalog.completeDefinitions(true));
            live.removeIf(entry -> DebugExportCatalog.NATIVE_ACCESS_ID.equals(entry.getId()));
            // Live exporters finish on this client thread. Ownership then passes
            // exclusively to the worker.
            task.prepared = DebugExportSession.prepare(root, "complete", live, task.control);
            task.completedFiles = 0;
            WORKER.execute(task::execute);
        } catch (Exception failure) {
            active = null;
            if (task.prepared != null) {
                task.prepared.discard();
            }
            task.prepared = null;
            task.definition = null;
            task.result = DebugExportResult.failed(null, "Debug export preparation failed: " + failure.getMessage(), 0);
            task.done = true;
        }
        return task;
    }

    static synchronized DebugNativeExportTask submit(File root, DebugExportDefinition definition) {
        if (active != null) {
            return active;
        }
        DebugNativeExportTask task = new DebugNativeExportTask(root, definition);
        active = task;
        try {
            WORKER.execute(task::execute);
        } catch (RuntimeException rejected) {
            active = null;
            task.result = DebugExportResult.failed(null, "Native export worker is unavailable.", 0);
            task.definition = null;
            task.done = true;
        }
        return task;
    }

    private void execute() {
        running = Thread.currentThread();
        try {
            result = root == null
                    ? DebugExportResult.failed(null, "Debug export directory is unavailable.", 0)
                    : prepared == null
                            ? DebugExportSession.run(root, "native-access", Collections.singletonList(definition),
                                    control)
                            : prepared.execute(Collections.singletonList(definition));
        } finally {
            running = null;
            Thread.interrupted();
            definition = null;
            prepared = null;
            synchronized (DebugNativeExportTask.class) {
                if (active == this) {
                    active = null;
                }
                done = true;
            }
        }
    }

    public boolean isDone() {
        return done;
    }

    public DebugExportResult getResult() {
        return result;
    }

    public boolean isCancelled() {
        return cancelRequested && !published;
    }

    public String getProgress() {
        return cancelRequested
                ? "Cancelling native export..."
                : "Writing " + file + "\n" + completedFiles + " of " + totalFiles + " files complete.";
    }

    /**
     * Once atomic publication wins, cancellation cannot retract a completed export.
     */
    public synchronized boolean cancel() {
        if (published || done) {
            return false;
        }
        cancelRequested = true;
        Thread thread = running;
        if (thread != null) {
            thread.interrupt();
        }
        return true;
    }

    private final class Control implements DebugExportControl {
        @Override
        public void checkpoint() throws IOException {
            if (cancelRequested || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("native_export_cancelled");
            }
        }

        @Override
        public void fileStarted(String name) {
            file = name;
        }

        @Override
        public void fileCompleted() {
            completedFiles++;
        }

        @Override
        public void publish(File temporary, File destination) throws IOException {
            synchronized (DebugNativeExportTask.this) {
                checkpoint();
                DebugExportSession.moveDirectory(temporary, destination);
                published = true;
            }
        }
    }
}
