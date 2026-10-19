package betamoon.logging;

import java.util.Locale;
import java.util.logging.Level;

/** Public severity names, independent of java.util.logging's legacy names. */
public enum LogLevel {
    TRACE(Level.FINEST), DEBUG(Level.FINE), INFO(Level.INFO), WARN(Level.WARNING), ERROR(Level.SEVERE), OFF(Level.OFF);

    private final Level javaLevel;

    LogLevel(Level javaLevel) {
        this.javaLevel = javaLevel;
    }

    public Level javaLevel() {
        return javaLevel;
    }

    public boolean accepts(Level level) {
        return this != OFF && level != Level.OFF && level.intValue() >= javaLevel.intValue();
    }

    public static LogLevel parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public static LogLevel fromJava(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return ERROR;
        }
        if (level.intValue() >= Level.WARNING.intValue()) {
            return WARN;
        }
        if (level.intValue() >= Level.INFO.intValue()) {
            return INFO;
        }
        return level.intValue() >= Level.FINE.intValue() ? DEBUG : TRACE;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
