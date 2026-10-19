package betamoon.logging;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptDiagnostics;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Routes one captured event to independently filtered console/file outputs and bounded report history. */
final class RuntimeLogHandler extends Handler {
    private static final int RECENT_CHARS = 131072;
    private static final int RECENT_ENTRIES = 200;
    private final Object configurationLock = new Object();
    private final Deque<Entry> recent = new ArrayDeque<>();
    private volatile LogLevel fileLevel = LogLevel.INFO;
    private volatile LogLevel consoleLevel = LogLevel.INFO;
    private volatile AsyncLogFile file;
    private Path directory;
    private int recentChars;
    private boolean configured;

    void configure(Path directory, LogLevel fileLevel, LogLevel consoleLevel) {
        synchronized (configurationLock) {
            configureOutputs(directory, fileLevel, consoleLevel);
        }
    }

    private void configureOutputs(Path directory, LogLevel fileLevel, LogLevel consoleLevel) {
        this.consoleLevel = consoleLevel;
        if (configured && directory.equals(this.directory) && fileLevel == this.fileLevel) {
            return;
        }
        AsyncLogFile previous;
        synchronized (this) {
            previous = file;
            file = null;
        }
        if (previous != null) {
            previous.close();
        }
        this.fileLevel = fileLevel;
        this.directory = directory;
        if (fileLevel != LogLevel.OFF) {
            AsyncLogFile next = new AsyncLogFile(() -> new RotatingLogFile(directory, 4L * 1024 * 1024, 5),
                    1024, this::feedback);
            synchronized (this) {
                if (!configured) {
                    for (Entry entry : recent) {
                        if (fileLevel.accepts(entry.level)) {
                            next.offer(entry.text);
                        }
                    }
                }
                file = next;
            }
        }
        configured = true;
    }

    boolean enabled(LogLevel level) {
        return fileLevel.accepts(level.javaLevel()) || consoleLevel.accepts(level.javaLevel());
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || (!fileLevel.accepts(record.getLevel()) && !consoleLevel.accepts(record.getLevel()))) {
            return;
        }
        RuntimeLogRecord captured = record instanceof RuntimeLogRecord ? (RuntimeLogRecord) record : null;
        String text = LogText.format(record, captured == null ? LuaScriptRegistry.getCurrentScriptFile() : captured.owner,
                captured == null ? ScriptDiagnostics.loggingOperation() : captured.operation,
                captured == null ? Thread.currentThread().getName() : captured.thread);
        synchronized (this) {
            remember(record.getLevel(), text);
            if (consoleLevel.accepts(record.getLevel())) {
                PrintStream output = record.getLevel().intValue() >= Level.WARNING.intValue() ? System.err : System.out;
                output.print(text);
            }
            AsyncLogFile destination = file;
            if (destination != null && fileLevel.accepts(record.getLevel())) {
                destination.offer(text);
            }
        }
    }

    private synchronized void remember(Level level, String text) {
        recent.addLast(new Entry(level, text));
        recentChars += text.length();
        while (recentChars > RECENT_CHARS || recent.size() > RECENT_ENTRIES) {
            recentChars -= recent.removeFirst().text.length();
        }
    }

    synchronized List<String> recent() {
        List<String> snapshot = new ArrayList<>();
        for (Entry entry : recent) {
            snapshot.add(entry.text);
        }
        return snapshot;
    }

    private void feedback(LogRecord record) {
        String text = LogText.format(record, null, "file logging",
                Thread.currentThread().getName());
        remember(record.getLevel(), text);
        if (consoleLevel.accepts(record.getLevel())) {
            System.err.print(text);
        }
    }

    @Override
    public void flush() {
        // The writer flushes at least once per second; producers never perform disk I/O.
    }

    @Override
    public void close() {
        synchronized (configurationLock) {
            AsyncLogFile destination;
            synchronized (this) {
                destination = file;
                file = null;
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    private static final class Entry {
        private final Level level;
        private final String text;

        private Entry(Level level, String text) {
            this.level = level;
            this.text = text;
        }
    }
}
