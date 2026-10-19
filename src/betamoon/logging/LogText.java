package betamoon.logging;

import java.io.PrintWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/** Shared bounded, plain-text representation for the console, file, and report excerpt. */
final class LogText {
    static final int MAX_ENTRY_CHARS = 32768;
    private static final Formatter MESSAGES = new Formatter() {
        @Override
        public String format(LogRecord record) {
            return formatMessage(record);
        }
    };

    private LogText() {
    }

    static String format(LogRecord record, String owner, String operation, String thread) {
        StringBuilder text = new StringBuilder();
        text.append(Instant.ofEpochMilli(record.getMillis())).append(" [BetaMoon] ")
                .append(LogLevel.fromJava(record.getLevel()).name()).append(" [").append(label(thread)).append(']');
        if (owner != null) {
            text.append(" [").append(label(owner)).append(']');
        }
        if (operation != null && !"unavailable".equals(operation)) {
            text.append(" [").append(label(operation)).append(']');
        }
        text.append(' ').append(bound(MESSAGES.formatMessage(record))).append('\n');
        if (record.getThrown() != null) {
            BoundedWriter trace = new BoundedWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            text.append(trace.text);
            if (trace.truncated) {
                text.append("\n[Exception trace truncated.]\n");
            }
        }
        return bound(text.toString().replace("\r\n", "\n").replace('\r', '\n'));
    }

    static String bound(String text) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= MAX_ENTRY_CHARS) {
            return text;
        }
        int end = MAX_ENTRY_CHARS - 32;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "\n[Log entry truncated.]\n";
    }

    private static String label(String value) {
        return value.substring(0, Math.min(value.length(), 512)).replace('\n', ' ').replace('\r', ' ');
    }

    private static final class BoundedWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        private boolean truncated;

        @Override
        public void write(char[] value, int offset, int count) {
            int accepted = Math.min(count, MAX_ENTRY_CHARS - text.length());
            text.append(value, offset, accepted);
            truncated |= accepted < count;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
