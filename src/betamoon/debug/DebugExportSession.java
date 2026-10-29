package betamoon.debug;

import betamoon.BetaMoonCommon;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Owns one isolated, UTF-8 debug export run and its manifest. */
final class DebugExportSession {
    interface TextContent {
        int write(BufferedWriter writer) throws Exception;
    }

    private static final DateTimeFormatter DIRECTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private final File root;
    private final ZonedDateTime generatedAt;
    private final long startedNanos;
    private final File temporaryDirectory;
    private final String runName;
    private final List<MutableCategory> categories = new ArrayList<MutableCategory>();
    private MutableCategory current;
    private final DebugExportControl control;

    private DebugExportSession(File root, String runName, ZonedDateTime generatedAt, DebugExportControl control)
            throws IOException {
        this.control = control;
        this.root = root;
        this.runName = runName;
        this.generatedAt = generatedAt;
        startedNanos = System.nanoTime();
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IOException("Debug export directory could not be created: " + root);
        }
        temporaryDirectory = unique(root, "." + runName + ".tmp");
        if (!temporaryDirectory.mkdir()) {
            throw new IOException("Debug export run directory could not be created: " + temporaryDirectory);
        }
    }

    static DebugExportResult run(File root, String suffix, List<DebugExportDefinition> definitions) {
        return run(root, suffix, definitions, null);
    }

    static DebugExportResult run(File root, String suffix, List<DebugExportDefinition> definitions,
            DebugExportControl control) {
        long started = System.nanoTime();
        DebugExportSession session;
        try {
            ZonedDateTime now = ZonedDateTime.now();
            String runName = DIRECTORY_TIME.format(now) + "_" + sanitize(suffix);
            session = new DebugExportSession(root, runName, now, control);
        } catch (Exception error) {
            return DebugExportResult.failed(root, message(error), elapsed(started));
        }
        return session.execute(definitions);
    }

    DebugExportResult execute(List<DebugExportDefinition> definitions) {
        try {
            return executeChecked(definitions);
        } catch (IOException cancelled) {
            cleanupTemporary();
            return DebugExportResult.failed(null, message(cancelled), elapsed(startedNanos));
        }
    }

    static DebugExportSession prepare(File root, String suffix, List<DebugExportDefinition> definitions,
            DebugExportControl control) throws IOException {
        ZonedDateTime now = ZonedDateTime.now();
        DebugExportSession session = new DebugExportSession(root, DIRECTORY_TIME.format(now) + "_" + sanitize(suffix),
                now, control);
        try {
            session.executeCategories(definitions);
            return session;
        } catch (IOException cancelled) {
            session.cleanupTemporary();
            throw cancelled;
        }
    }

    private void executeCategories(List<DebugExportDefinition> definitions) throws IOException {
        checkpoint();
        for (DebugExportDefinition definition : definitions) {
            current = new MutableCategory(definition);
            categories.add(current);
            try {
                definition.getExporter().export(this);
                current.verifyExpectedFiles();
            } catch (Exception error) {
                checkpoint();
                current.failure = message(error);
                BetaMoonCommon.LOGGER.warning("Debug export failed for " + definition.getId() + ": " + current.failure);
            }
            checkpoint();
        }
        current = null;
    }

    private DebugExportResult executeChecked(List<DebugExportDefinition> definitions) throws IOException {
        executeCategories(definitions);
        boolean incomplete = hasFailures();
        String finalName = runName + (incomplete ? "_incomplete" : "");
        try {
            checkpoint();
            writeManifest(incomplete);
            File finalDirectory = unique(root, finalName);
            if (control == null) {
                moveDirectory(temporaryDirectory, finalDirectory);
            } else {
                control.publish(temporaryDirectory, finalDirectory);
            }
            return new DebugExportResult(
                    incomplete ? DebugExportResult.Status.INCOMPLETE : DebugExportResult.Status.COMPLETE,
                    finalDirectory, freezeCategories(), null, elapsed(startedNanos));
        } catch (Exception error) {
            checkpoint();
            return DebugExportResult.failed(temporaryDirectory, message(error), elapsed(startedNanos));
        }
    }

    DebugExportResult.FileResult writeTextFile(String name, TextContent content) throws Exception {
        if (current == null) {
            throw new IllegalStateException("A debug export file requires an active category");
        }
        if (!current.fileNames.contains(name)) {
            throw new IllegalArgumentException("Exporter produced an undeclared file: " + name);
        }
        if (!current.producedNames.add(name)) {
            throw new IllegalStateException("Exporter produced the same file twice: " + name);
        }
        validateFileName(name);
        startedFile(name);

        File body = new File(temporaryDirectory, name + ".body");
        File output = new File(temporaryDirectory, name);
        int records;
        try {
            try (BufferedWriter writer = writer(body)) {
                records = content.write(writer);
            }
            try (BufferedWriter writer = writer(output);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(Files.newInputStream(body.toPath()), StandardCharsets.UTF_8))) {
                writer.write("BetaMoon debug export\n");
                writer.write("Category: " + current.title + "\n");
                writer.write("Records: " + records + "\n");
                writer.write("Generated: " + DISPLAY_TIME.format(generatedAt) + "\n\n");
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    writer.write(buffer, 0, read);
                }
            }
        } catch (Exception error) {
            Files.deleteIfExists(body.toPath());
            Files.deleteIfExists(output.toPath());
            current.producedNames.remove(name);
            throw error;
        }
        Files.deleteIfExists(body.toPath());
        DebugExportResult.FileResult result = new DebugExportResult.FileResult(name, records);
        current.files.add(result);
        completedFile();
        return result;
    }

    /**
     * Raw UTF-8 structured data with no prose prefix. Completion is recorded by the
     * same session machinery.
     */
    DebugExportResult.FileResult writeStructuredFile(String name, TextContent content) throws Exception {
        if (current == null || !current.fileNames.contains(name)) {
            throw new IllegalArgumentException("Structured exporter produced an undeclared file: " + name);
        }
        validateFileName(name);
        if (!current.producedNames.add(name)) {
            throw new IllegalStateException("Exporter produced the same file twice: " + name);
        }
        startedFile(name);
        File temporary = new File(temporaryDirectory, name + ".part");
        File output = new File(temporaryDirectory, name);
        int records;
        try {
            try (BufferedWriter writer = writer(temporary)) {
                records = content.write(writer);
            }
            try {
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), output.toPath());
            }
        } catch (Exception exception) {
            Files.deleteIfExists(temporary.toPath());
            Files.deleteIfExists(output.toPath());
            current.producedNames.remove(name);
            throw exception;
        }
        DebugExportResult.FileResult result = new DebugExportResult.FileResult(name, records);
        current.files.add(result);
        completedFile();
        return result;
    }

    private void checkpoint() throws IOException {
        if (control != null) {
            control.checkpoint();
        }
    }

    private void startedFile(String name) throws IOException {
        checkpoint();
        if (control != null) {
            control.fileStarted(name);
        }
    }

    private void completedFile() throws IOException {
        checkpoint();
        if (control != null) {
            control.fileCompleted();
        }
    }

    void discard() {
        cleanupTemporary();
    }

    private void cleanupTemporary() {
        File[] files = temporaryDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException cleanup) {
                    BetaMoonCommon.LOGGER.warning("Could not remove cancelled native export file: " + file);
                }
            }
        }
        try {
            Files.deleteIfExists(temporaryDirectory.toPath());
        } catch (IOException cleanup) {
            BetaMoonCommon.LOGGER.warning("Could not remove cancelled native export directory: " + temporaryDirectory);
        }
    }

    void incomplete(String reason) {
        if (current != null) {
            current.failure = reason;
        }
    }

    void warn(String warning) {
        if (current == null) {
            throw new IllegalStateException("A debug export warning requires an active category");
        }
        if (warning != null && !warning.trim().isEmpty() && !current.warnings.contains(warning)) {
            current.warnings.add(warning);
        }
    }

    private void writeManifest(boolean incomplete) throws IOException {
        File manifest = new File(temporaryDirectory, "manifest.txt");
        try (BufferedWriter writer = writer(manifest)) {
            writer.write("BetaMoon debug export\n");
            writer.write("Status: " + (incomplete ? "INCOMPLETE" : "COMPLETE") + "\n");
            writer.write("BetaMoon version: " + BetaMoonCommon.VERSION + "\n");
            writer.write("Minecraft target: Beta 1.7.3\n");
            writer.write("Generated: " + DISPLAY_TIME.format(generatedAt) + "\n");
            writer.write("Elapsed milliseconds: " + elapsed(startedNanos) + "\n\n");
            for (MutableCategory category : categories) {
                writer.write("Category: " + category.title + " [" + category.id + "]\n");
                writer.write("Status: " + (category.failure == null ? "complete" : "failed") + "\n");
                for (DebugExportResult.FileResult file : category.files) {
                    writer.write("File: " + file.getName() + " (" + file.getRecords() + " records)\n");
                }
                for (String warning : category.warnings) {
                    writer.write("Warning: " + DebugExportNames.escapeText(warning) + "\n");
                }
                if (category.failure != null) {
                    writer.write("Failure: " + DebugExportNames.escapeText(category.failure) + "\n");
                }
                writer.write("\n");
            }
        }
    }

    private List<DebugExportResult.CategoryResult> freezeCategories() {
        List<DebugExportResult.CategoryResult> result = new ArrayList<DebugExportResult.CategoryResult>();
        for (MutableCategory category : categories) {
            result.add(new DebugExportResult.CategoryResult(category.id, category.title, category.files,
                    category.warnings, category.failure));
        }
        return Collections.unmodifiableList(result);
    }

    private boolean hasFailures() {
        for (MutableCategory category : categories) {
            if (category.failure != null) {
                return true;
            }
        }
        return false;
    }

    private BufferedWriter writer(File file) throws IOException {
        return new LfBufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8), control);
    }

    static void moveDirectory(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), destination.toPath());
        }
    }

    private static File unique(File parent, String name) {
        File candidate = new File(parent, name);
        for (int suffix = 2; candidate.exists(); suffix++) {
            candidate = new File(parent, name + "_" + suffix);
        }
        return candidate;
    }

    private static String sanitize(String value) {
        String normalized = value == null ? "export" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return normalized.isEmpty() ? "export" : normalized;
    }

    private static void validateFileName(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")
                || name.equals("manifest.txt")) {
            throw new IllegalArgumentException("Invalid debug export file name: " + name);
        }
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1000000L;
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value;
    }

    private static final class MutableCategory {
        private final String id;
        private final String title;
        private final List<String> fileNames;
        private final List<DebugExportResult.FileResult> files = new ArrayList<DebugExportResult.FileResult>();
        private final List<String> warnings = new ArrayList<String>();
        private final Set<String> producedNames = new LinkedHashSet<String>();
        private String failure;

        private MutableCategory(DebugExportDefinition definition) {
            id = definition.getId();
            title = definition.getTitle();
            fileNames = definition.getFileNames();
        }

        private void verifyExpectedFiles() {
            for (String expected : fileNames) {
                if (!producedNames.contains(expected)) {
                    throw new IllegalStateException("Exporter did not produce declared file: " + expected);
                }
            }
        }
    }

    /** BufferedWriter with platform-independent line endings. */
    private static final class LfBufferedWriter extends BufferedWriter {
        private final DebugExportControl control;

        private LfBufferedWriter(Writer writer, DebugExportControl control) {
            super(writer);
            this.control = control;
        }

        @Override
        public void write(String value, int offset, int length) throws IOException {
            if (control != null) {
                control.checkpoint();
            }
            super.write(value, offset, length);
        }

        @Override
        public void write(char[] value, int offset, int length) throws IOException {
            if (control != null) {
                control.checkpoint();
            }
            super.write(value, offset, length);
        }

        @Override
        public void write(int value) throws IOException {
            if (control != null) {
                control.checkpoint();
            }
            super.write(value);
        }

        @Override
        public void newLine() throws IOException {
            write('\n');
        }
    }
}
