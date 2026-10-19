package betamoon.logging;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Nonblocking producer, bounded memory, and one daemon that exclusively owns file I/O. */
final class AsyncLogFile implements AutoCloseable {
    interface Destination extends AutoCloseable {
        void write(String text) throws IOException;

        void flush() throws IOException;

        void close() throws IOException;
    }

    interface Factory {
        Destination open() throws IOException;
    }

    private final ArrayBlockingQueue<String> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final Consumer<LogRecord> feedback;
    private final Thread worker;
    private volatile boolean closing;
    private volatile boolean failed;

    AsyncLogFile(Factory factory, int capacity, Consumer<LogRecord> feedback) {
        queue = new ArrayBlockingQueue<>(capacity);
        this.feedback = feedback;
        worker = new Thread(() -> run(factory), "BetaMoon log writer");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized void offer(String text) {
        if (!closing && !failed && !queue.offer(text)) {
            dropped.incrementAndGet();
        }
    }

    private void run(Factory factory) {
        try (Destination destination = factory.open()) {
            long lastFlush = System.nanoTime();
            while (!closing || !queue.isEmpty() || dropped.get() > 0) {
                String text = queue.poll(250, TimeUnit.MILLISECONDS);
                long lost = dropped.getAndSet(0);
                if (lost > 0) {
                    String message = lost + " log entries dropped because the file logging queue was full.";
                    LogRecord notice = new LogRecord(Level.WARNING, message);
                    destination.write(LogText.format(notice, null, "file logging", worker.getName()));
                    feedback.accept(notice);
                }
                if (text != null) {
                    destination.write(text);
                }
                if (System.nanoTime() - lastFlush >= TimeUnit.SECONDS.toNanos(1)) {
                    destination.flush();
                    lastFlush = System.nanoTime();
                }
            }
            destination.flush();
        } catch (IOException | RuntimeException failure) {
            synchronized (this) {
                failed = true;
                queue.clear();
            }
            feedback.accept(new LogRecord(Level.SEVERE, "File logging stopped: " + failure.toString()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            closing = true;
        }
        try {
            worker.join(2000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
