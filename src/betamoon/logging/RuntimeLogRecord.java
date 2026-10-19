package betamoon.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Immutable strings captured on the producer thread, without retaining game or Lua objects. */
final class RuntimeLogRecord extends LogRecord {
    final String owner;
    final String operation;
    final String thread;

    RuntimeLogRecord(Level level, String message, String owner, String operation, String thread) {
        super(level, message);
        this.owner = owner;
        this.operation = operation;
        this.thread = thread;
    }
}
