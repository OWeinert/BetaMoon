package betamoon.logging;

import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptDiagnostics;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared side-neutral logging entry point. */
public final class RuntimeLogs {
    private static final RuntimeLogHandler HANDLER = new RuntimeLogHandler();
    private static final Logger LOGGER = createLogger();

    private RuntimeLogs() {
    }

    private static Logger createLogger() {
        Logger logger = Logger.getLogger("BetaMoon");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(HANDLER);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(HANDLER::close, "BetaMoon log shutdown"));
        } catch (IllegalStateException | SecurityException unavailable) {
            logger.warning("Automatic log shutdown flushing is unavailable: " + unavailable);
        }
        return logger;
    }

    public static Logger logger() {
        return LOGGER;
    }

    public static void configure(Path directory, LogLevel fileLevel, LogLevel consoleLevel) {
        HANDLER.configure(directory.toAbsolutePath().normalize(), fileLevel, consoleLevel);
    }

    public static boolean enabled(LogLevel level) {
        return HANDLER.enabled(level);
    }

    public static void log(LogLevel level, String owner, String message) {
        if (enabled(level)) {
            LOGGER.log(new RuntimeLogRecord(level.javaLevel(), message,
                    owner == null ? LuaScriptRegistry.getCurrentScriptFile() : owner,
                    ScriptDiagnostics.loggingOperation(), Thread.currentThread().getName()));
        }
    }

    public static void issue(ScriptIssue issue) {
        LogLevel level = issue.isWarning() ? LogLevel.WARN : LogLevel.ERROR;
        if (enabled(level)) {
            ScriptDiagnostics.Occurrence occurrence = issue.getOccurrence();
            RuntimeLogRecord record = new RuntimeLogRecord(level.javaLevel(), issue.getDetail()
                    + (issue.getStackTrace().isEmpty() ? "" : "\n" + issue.getStackTrace()), issue.getScriptSource(),
                    occurrence.operation, occurrence.thread);
            record.setMillis(occurrence.timestampMillis);
            LOGGER.log(record);
        }
    }

    public static List<String> recent() {
        return HANDLER.recent();
    }
}
