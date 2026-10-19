package betamoon.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Used exclusively by the file writer thread; owns only latest.log and runtime-*.log archives. */
final class RotatingLogFile implements AsyncLogFile.Destination {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
            .withZone(ZoneOffset.UTC);
    private final Path directory;
    private final Path latest;
    private final long maximumBytes;
    private final int archives;
    private final Clock clock;
    private BufferedWriter writer;
    private long bytes;

    RotatingLogFile(Path directory, long maximumBytes, int archives) throws IOException {
        this(directory, maximumBytes, archives, Clock.systemUTC());
    }

    RotatingLogFile(Path directory, long maximumBytes, int archives, Clock clock) throws IOException {
        this.directory = directory;
        this.latest = directory.resolve("latest.log");
        this.maximumBytes = maximumBytes;
        this.archives = archives;
        this.clock = clock;
        Files.createDirectories(directory);
        rotate();
    }

    @Override
    public void write(String text) throws IOException {
        int length = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > 0 && bytes + length > maximumBytes) {
            rotate();
        }
        writer.write(text);
        bytes += length;
    }

    private void rotate() throws IOException {
        close();
        if (Files.exists(latest)) {
            if (Files.size(latest) > 0) {
                String stem = "runtime-" + STAMP.format(clock.instant());
                Path target;
                int suffix = nextSuffix(stem);
                do {
                    target = directory.resolve(stem + "-" + String.format(Locale.ROOT, "%04d", suffix) + ".log");
                    suffix++;
                } while (Files.exists(target));
                Files.move(latest, target);
            }
        }
        trim();
        writer = Files.newBufferedWriter(latest, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        bytes = 0;
    }

    private int nextSuffix(String stem) throws IOException {
        int next = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                String name = path.getFileName().toString();
                if (name.startsWith(stem + "-") && name.endsWith(".log")) {
                    try {
                        next = Math.max(next, Integer.parseInt(name.substring(stem.length() + 1, name.length() - 4)) + 1);
                    } catch (NumberFormatException unrelated) {
                        // A similarly named, non-runtime file does not reserve a sequence number.
                    }
                }
            }
        }
        return next;
    }

    private void trim() throws IOException {
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString()
                    .matches("runtime-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}(-\\d+)?\\.log"))
                    .forEach(paths::add);
        }
        paths.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (int index = 0; index < paths.size() - archives; index++) {
            Files.delete(paths.get(index));
        }
    }

    @Override
    public void flush() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
