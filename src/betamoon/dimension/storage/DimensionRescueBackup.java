package betamoon.dimension.storage;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.UUID;

/**
 * Full save backup, sliced to at most 2 MiB and 128 directory entries per
 * logical tick.
 */
public final class DimensionRescueBackup implements AutoCloseable {
    private static final int BYTES_PER_TICK = 2 * 1024 * 1024;
    private final DimensionPaths paths;
    private final String directory = "betamoon/backups/rescue-" + UUID.randomUUID();
    private final Deque<Path> directories = new ArrayDeque<Path>();
    private final byte[] buffer = new byte[65536];
    private DirectoryStream<Path> listing;
    private Iterator<Path> entries;
    private FileInputStream input;
    private FileOutputStream output;
    private Path source;
    private FileTime sourceModified;
    private long sourceSize;
    private long sourceCopied;
    private long bytes;
    private int files;
    private int nodes;
    private boolean complete;

    public DimensionRescueBackup(DimensionPaths paths) throws IOException {
        this.paths = paths;
        Files.createDirectories(paths.resolve(directory));
        paths.resolve(directory);
        directories.add(paths.root());
    }

    public boolean step() throws IOException {
        if (complete) {
            return true;
        }
        int remaining = BYTES_PER_TICK;
        int visited = 0;
        while (remaining > 0 && visited < 128) {
            if (input != null) {
                int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count >= 0) {
                    output.write(buffer, 0, count);
                    remaining -= count;
                    sourceCopied += count;
                    bytes += count;
                    continue;
                }
                output.getFD().sync();
                closeFile();
                if (sourceCopied != sourceSize || Files.size(source) != sourceSize
                        || !Files.getLastModifiedTime(source).equals(sourceModified)) {
                    throw new IOException("The save changed while its rescue backup was being copied");
                }
                files++;
            }
            if (entries == null || !entries.hasNext()) {
                closeListing();
                if (directories.isEmpty()) {
                    String manifest = "BetaMoon complete rescue backup\nFiles: " + files + "\nBytes: " + bytes + "\n";
                    Path marker = paths.resolve(directory + "/backup.complete");
                    try (FileOutputStream stream = new FileOutputStream(marker.toFile())) {
                        stream.write(manifest.getBytes(StandardCharsets.UTF_8));
                        stream.getFD().sync();
                    }
                    complete = true;
                    return true;
                }
                listing = Files.newDirectoryStream(directories.removeFirst());
                entries = listing.iterator();
                visited++;
                continue;
            }
            Path next = entries.next();
            visited++;
            if (++nodes > 1000000) {
                throw new IOException("Save backup exceeds its one-million-entry budget");
            }
            String relative = paths.root().relativize(next).toString().replace('\\', '/');
            if (relative.equals("betamoon/backups")) {
                continue;
            }
            Path checked = paths.resolve(relative);
            if (Files.isDirectory(checked, LinkOption.NOFOLLOW_LINKS)) {
                directories.addLast(checked);
            } else if (Files.isRegularFile(checked, LinkOption.NOFOLLOW_LINKS)) {
                Path destination = paths.resolve(directory + "/" + relative);
                Files.createDirectories(destination.getParent());
                destination = paths.resolve(directory + "/" + relative);
                source = checked;
                sourceSize = Files.size(source);
                sourceModified = Files.getLastModifiedTime(source);
                sourceCopied = 0;
                input = new FileInputStream(source.toFile());
                output = new FileOutputStream(destination.toFile());
            } else {
                throw new IOException("Save backup encountered an unsupported filesystem entry: " + relative);
            }
        }
        return false;
    }

    public String directory() {
        return directory;
    }

    public long copiedBytes() {
        return bytes;
    }

    private void closeFile() throws IOException {
        try {
            if (input != null) {
                input.close();
            }
        } finally {
            input = null;
            if (output != null) {
                output.close();
                output = null;
            }
        }
    }

    private void closeListing() throws IOException {
        if (listing != null) {
            listing.close();
            listing = null;
            entries = null;
        }
    }

    public void close() throws IOException {
        try {
            closeFile();
        } finally {
            closeListing();
        }
    }
}
