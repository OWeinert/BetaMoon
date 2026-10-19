package betamoon.logging;

import betamoon.config.BetaMoonConfig;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luaapi.logging.LoggingApi;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptIssueReports;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Exercises actual output, Lua calls, asynchronous backpressure, failures, and owned-file retention. */
public final class RuntimeLoggingTest {
    private RuntimeLoggingTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("betamoon-logging-test-");
        configuration(root.resolve("instance"));
        filtering(root.resolve("filtering"));
        startupConcurrency(root.resolve("startup"));
        rotation(root.resolve("rotation"));
        backpressure();
        failures();
        periodicFlush();
        luaAndIssues(root.resolve("lua"));
        System.out.println("Runtime logging passed: levels, Lua ownership, issue coverage, rotation, and failures.");
    }

    private static void configuration(Path instance) throws Exception {
        Path configRoot = instance.resolve("config");
        BetaMoonConfig defaults = new BetaMoonConfig(configRoot.toFile());
        require(defaults.getFileMinLogLevel().getValue() == LogLevel.INFO
                && defaults.getConsoleMinLogLevel().getValue() == LogLevel.INFO, "Both defaults must be info");
        require(defaults.getLogDirectory().toPath().equals(instance.resolve("betamoon/logs")),
                "Logs must belong to this instance");
        Path config = configRoot.resolve("betamoon.cfg");
        String text = read(config).replace("logging.fileMinLevel=info", "logging.fileMinLevel=DEBUG")
                .replace("logging.consoleMinLevel=info", "logging.consoleMinLevel=invalid");
        Files.write(config, text.getBytes(StandardCharsets.UTF_8));
        BetaMoonConfig parsed = new BetaMoonConfig(configRoot.toFile());
        require(parsed.getFileMinLogLevel().getValue() == LogLevel.DEBUG
                && parsed.getConsoleMinLogLevel().getValue() == LogLevel.INFO,
                "Configuration must normalize valid levels and recover invalid levels");
        require(parsed.diagnosticSettings().get("logging.fileMinLevel").equals("debug"),
                "Reports must include effective thresholds");
        for (LogLevel threshold : LogLevel.values()) {
            for (LogLevel entry : LogLevel.values()) {
                boolean expected = threshold != LogLevel.OFF && entry != LogLevel.OFF
                        && entry.ordinal() >= threshold.ordinal();
                require(threshold.accepts(entry.javaLevel()) == expected, "Severity threshold: " + threshold + entry);
            }
        }
    }

    private static void filtering(Path directory) throws Exception {
        RuntimeLogHandler handler = new RuntimeLogHandler();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, "UTF-8");
                PrintStream err = new PrintStream(errors, true, "UTF-8")) {
            System.setOut(out);
            System.setErr(err);
            handler.publish(new LogRecord(Level.INFO, "bootstrap entry"));
            handler.configure(directory, LogLevel.DEBUG, LogLevel.ERROR);
            handler.configure(directory, LogLevel.DEBUG, LogLevel.ERROR);
            handler.publish(new LogRecord(Level.FINEST, "filtered trace"));
            handler.publish(new LogRecord(Level.FINE, "file debug"));
            LogRecord parameterized = new LogRecord(Level.INFO, "Value {0}");
            parameterized.setParameters(new Object[] {42});
            handler.publish(parameterized);
            LogRecord failure = new LogRecord(Level.SEVERE, "visible error");
            failure.setThrown(new IllegalStateException("cause retained"));
            handler.publish(failure);
        } finally {
            handler.close();
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        String file = read(directory.resolve("latest.log"));
        require(file.contains("bootstrap entry") && file.contains("file debug") && file.contains("Value 42")
                && file.contains("cause retained") && !file.contains("filtered trace"),
                "File threshold, startup replay, parameter formatting and stack traces must work together");
        require(output.toString("UTF-8").contains("bootstrap entry")
                && !output.toString("UTF-8").contains("file debug")
                && errors.toString("UTF-8").contains("visible error"), "Console threshold must be independent");
        require(occurrences(file, "bootstrap entry") == 1, "Repeated configure must not replay startup twice");
        for (int index = 0; index < 250; index++) {
            handler.publish(new LogRecord(Level.INFO, repeat("x", 2000)));
        }
        require(handler.recent().size() <= 200 && handler.recent().stream().mapToInt(String::length).sum() <= 131072,
                "Recent history must be bounded by count and total text");
        String large = LogText.format(new LogRecord(Level.INFO, repeat("x", 100000)), null, null, "test");
        require(large.length() <= LogText.MAX_ENTRY_CHARS && large.contains("truncated"),
                "Individual entries must be bounded and disclose truncation");
    }

    private static void rotation(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path unrelated = directory.resolve("script_err_keep.log");
        Files.write(unrelated, new byte[] {1});
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);
        try (RotatingLogFile file = new RotatingLogFile(directory, 80, 2, clock)) {
            for (int index = 0; index < 8; index++) {
                file.write("entry " + index + " " + repeat("x", 45) + "\n");
            }
        }
        try (RotatingLogFile restarted = new RotatingLogFile(directory, 80, 2, clock)) {
            restarted.write("new process\n");
        }
        List<Path> archives = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().startsWith("runtime-")).sorted().forEach(archives::add);
        }
        require(archives.size() == 2 && read(archives.get(1)).contains("entry 7")
                && read(directory.resolve("latest.log")).contains("new process") && Files.exists(unrelated),
                "Size/startup rotation must retain the newest owned archives and preserve diagnostic reports");
    }

    private static void startupConcurrency(Path directory) throws Exception {
        RuntimeLogHandler handler = new RuntimeLogHandler();
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch configuring = new CountDownLatch(1);
        CountDownLatch configured = new CountDownLatch(1);
        PrintStream original = System.out;
        Thread producer = new Thread(() -> handler.publish(new LogRecord(Level.INFO, "concurrent startup entry")));
        Thread configure = new Thread(() -> {
            configuring.countDown();
            handler.configure(directory, LogLevel.INFO, LogLevel.OFF);
            configured.countDown();
        });
        try (PrintStream blocked = new PrintStream(new ByteArrayOutputStream()) {
            @Override
            public void print(String text) {
                publishing.countDown();
                try {
                    release.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }) {
            System.setOut(blocked);
            producer.start();
            require(publishing.await(2, TimeUnit.SECONDS), "Producer should reach console output");
            configure.start();
            require(configuring.await(2, TimeUnit.SECONDS), "Configure should start");
            require(!configured.await(100, TimeUnit.MILLISECONDS),
                    "Startup replay must synchronize with in-flight publication");
        } finally {
            release.countDown();
            producer.join(2000);
            configure.join(2000);
            handler.close();
            System.setOut(original);
        }
        require(!producer.isAlive() && !configure.isAlive()
                && occurrences(read(directory.resolve("latest.log")), "concurrent startup entry") == 1,
                "Startup publication must reach the file exactly once without deadlocking");
    }

    private static void backpressure() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch flushed = new CountDownLatch(1);
        List<String> written = new CopyOnWriteArrayList<>();
        List<String> feedback = new CopyOnWriteArrayList<>();
        AsyncLogFile file = new AsyncLogFile(() -> new AsyncLogFile.Destination() {
            public void write(String text) throws IOException {
                writing.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new IOException("Test did not release the writer");
                    }
                } catch (InterruptedException interrupted) {
                    throw new IOException(interrupted);
                }
                written.add(text);
            }

            public void flush() {
                flushed.countDown();
            }

            public void close() {
            }
        }, 2, record -> feedback.add(record.getMessage()));
        try {
            file.offer("first\n");
            require(writing.await(2, TimeUnit.SECONDS), "Writer should begin processing");
            file.offer("second\n");
            file.offer("third\n");
            for (int index = 0; index < 100; index++) {
                file.offer("overflow\n");
            }
        } finally {
            release.countDown();
            file.close();
        }
        require(written.contains("first\n") && written.contains("second\n") && written.contains("third\n")
                && feedback.stream().anyMatch(value -> value.contains("100 log entries dropped"))
                && flushed.getCount() == 0, "Full queue must not block producers, must disclose losses, and drain on close");
    }

    private static void failures() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        List<String> feedback = new CopyOnWriteArrayList<>();
        AsyncLogFile file = new AsyncLogFile(() -> {
            throw new IOException("simulated disk failure");
        }, 2, record -> {
            feedback.add(record.getMessage());
            failed.countDown();
        });
        require(failed.await(2, TimeUnit.SECONDS), "Disk failures must be surfaced");
        file.offer("still safe\n");
        file.close();
        require(feedback.size() == 1 && feedback.get(0).contains("simulated disk failure"),
                "Failed file output must disable itself without recursive logging");

        CountDownLatch writeFailed = new CountDownLatch(1);
        AsyncLogFile diskFull = new AsyncLogFile(() -> new AsyncLogFile.Destination() {
            public void write(String text) throws IOException {
                throw new IOException("simulated full disk");
            }

            public void flush() {
            }

            public void close() {
            }
        }, 2, record -> {
            require(record.getLevel() == Level.SEVERE, "File failures must surface at error severity");
            writeFailed.countDown();
        });
        diskFull.offer("trigger write failure\n");
        require(writeFailed.await(2, TimeUnit.SECONDS), "Write failures must be surfaced, too");
        diskFull.close();
    }

    private static void periodicFlush() throws Exception {
        CountDownLatch flushed = new CountDownLatch(1);
        AsyncLogFile file = new AsyncLogFile(() -> new AsyncLogFile.Destination() {
            public void write(String text) {
            }

            public void flush() {
                flushed.countDown();
            }

            public void close() {
            }
        }, 2, record -> {
            throw new AssertionError(record.getMessage());
        });
        try {
            file.offer("buffered entry\n");
            require(flushed.await(3, TimeUnit.SECONDS), "Buffered output must flush while the application keeps running");
        } finally {
            file.close();
        }
    }

    private static void luaAndIssues(Path directory) throws Exception {
        RuntimeLogs.configure(directory, LogLevel.TRACE, LogLevel.OFF);
        List<LogRecord> recorded = new ArrayList<>();
        Handler observer = new Handler() {
            public void publish(LogRecord record) {
                recorded.add(record);
            }

            public void flush() {
            }

            public void close() {
            }
        };
        RuntimeLogs.logger().addHandler(observer);
        try {
            LuaScriptErrors.clear();
            Globals globals = JsePlatform.standardGlobals();
            globals.load(new BetaMoonModule("owner/main.lua"));
            LoggingApi.installPrint(globals, "owner/main.lua");
            ScriptExecution.invoke("caller.lua", "events.world_tick", globals.load(
                    "betamoon.log('info', 'direct call')\n"
                    + "for _, level in ipairs({'trace','debug','info','warn','error'}) do\n"
                    + "  betamoon.log[level](betamoon.log, 'method ' .. level)\nend\n"
                    + "print('tabs', 42, nil, true, setmetatable({}, {__tostring=function() return 'custom' end}))\n"),
                    LuaValue.NONE);
            require(LuaScriptErrors.getEntries().isEmpty(), "Logging warnings/errors must not create popup issues");
            for (String expression : Arrays.asList("betamoon.log('off', 'bad')", "betamoon.log('invalid','bad')",
                    "betamoon.log(3,'bad')", "betamoon.log('info',{})", "betamoon.log:info()",
                    "betamoon.log:info('one','two')")) {
                try {
                    globals.load(expression).call();
                    throw new AssertionError("Invalid call accepted: " + expression);
                } catch (LuaError expected) {
                    require(expected.getMessage().contains("Log") || expected.getMessage().contains("betamoon.log"),
                            "Invalid calls need clear diagnostics");
                }
            }
            Path scripts = directory.resolve("scripts");
            Files.createDirectories(scripts);
            Files.write(scripts.resolve("probe.lua"), ("name = 'Logging Environment Probe'\n"
                    + "function modInit()\n"
                    + "  print('real environment print')\n"
                    + "  betamoon.log('info', 'real environment call')\n"
                    + "end\n").getBytes(StandardCharsets.UTF_8));
            new LuaModLoader(scripts.toFile()).loadAndRun();
            require(LuaScriptErrors.getEntries().isEmpty(), "Ordinary script loading must install logging successfully");
            LuaScriptErrors.add("owner/main.lua", "runtime issue marker", LuaScriptErrors.Outcome.UNKNOWN,
                    new IllegalStateException("runtime cause"));
            LuaScriptErrors.addWarning("owner/main.lua", "warning issue marker");
            require(recorded.stream().filter(record -> record.getMessage().contains("runtime issue marker")).count() == 1
                    && recorded.stream().anyMatch(record -> record.getMessage().contains("runtime issue marker")
                            && record.getLevel() == Level.SEVERE)
                    && recorded.stream().anyMatch(record -> record.getMessage().equals("warning issue marker")
                            && record.getLevel() == Level.WARNING), "Recorded issues must emit exactly once at correct severity");
            String report = ScriptIssueReports.generate(LuaScriptErrors.getEntries(), "Logging test");
            require(report.contains("=== Recent log entries ===") && report.contains("direct call"),
                    "Full reports must include a bounded recent log excerpt");
            RuntimeLogs.configure(directory, LogLevel.OFF, LogLevel.OFF);
            int previous = recorded.size();
            LuaScriptErrors.add("owner/main.lua", "filtered issue marker");
            globals.load("betamoon.log:error('filtered explicit log')").call();
            require(recorded.size() == previous && LuaScriptErrors.getErrorCount() == 2,
                    "Disabled logging must not suppress Script Issues");
            String file = read(directory.resolve("latest.log"));
            require(file.contains("[owner/main.lua] [events.world_tick] direct call")
                    && file.contains("tabs\t42\tnil\ttrue\tcustom") && file.contains("runtime cause")
                    && file.contains("[probe.lua] [modInit] real environment print")
                    && file.contains("[probe.lua] [modInit] real environment call")
                    && !file.contains("filtered explicit log"), "Lua ownership, callback, print semantics and traces must persist");
        } finally {
            RuntimeLogs.logger().removeHandler(observer);
            RuntimeLogs.configure(directory, LogLevel.OFF, LogLevel.OFF);
            LuaScriptErrors.clear();
        }
    }

    private static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(value);
        }
        return text.toString();
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
