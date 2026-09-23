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

    private DebugExportSession(File root, String runName, ZonedDateTime generatedAt) throws IOException {
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
        long started = System.nanoTime();
        DebugExportSession session;
        try {
            ZonedDateTime now = ZonedDateTime.now();
            String runName = DIRECTORY_TIME.format(now) + "_" + sanitize(suffix);
            session = new DebugExportSession(root, runName, now);
        } catch (Exception error) {
            return DebugExportResult.failed(root, message(error), elapsed(started));
        }
        return session.execute(definitions);
    }

    DebugExportResult execute(List<DebugExportDefinition> definitions) {
        for (DebugExportDefinition definition : definitions) {
            current = new MutableCategory(definition);
            categories.add(current);
            try {
                definition.getExporter().export(this);
                current.verifyExpectedFiles();
            } catch (Exception error) {
                current.failure = message(error);
                BetaMoonCommon.LOGGER.warning("Debug export failed for " + definition.getId() + ": "
                        + current.failure);
            }
        }
        current = null;

        boolean incomplete = hasFailures();
        String finalName = runName + (incomplete ? "_incomplete" : "");
        try {
            writeManifest(incomplete);
            File finalDirectory = unique(root, finalName);
            moveDirectory(temporaryDirectory, finalDirectory);
            return new DebugExportResult(incomplete ? DebugExportResult.Status.INCOMPLETE
                    : DebugExportResult.Status.COMPLETE, finalDirectory, freezeCategories(), null,
                    elapsed(startedNanos));
        } catch (Exception error) {
            return DebugExportResult.failed(temporaryDirectory, message(error), elapsed(startedNanos));
        }
    }

    DebugExportResult.FileResult writeTextFile(String name, TextContent content) throws Exception {
        if (current == null) {
            throw new IllegalStateException("A debug export file requires an active category");
        }
        if (!current.definition.getFileNames().contains(name)) {
            throw new IllegalArgumentException("Exporter produced an undeclared file: " + name);
        }
        if (!current.producedNames.add(name)) {
            throw new IllegalStateException("Exporter produced the same file twice: " + name);
        }
        validateFileName(name);

        File body = new File(temporaryDirectory, name + ".body");
        File output = new File(temporaryDirectory, name);
        int records;
        try {
            try (BufferedWriter writer = writer(body)) {
                records = content.write(writer);
            }
            try (BufferedWriter writer = writer(output);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            Files.newInputStream(body.toPath()), StandardCharsets.UTF_8))) {
                writer.write("BetaMoon debug export\n");
                writer.write("Category: " + current.definition.getTitle() + "\n");
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
        return result;
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
                writer.write("Category: " + category.definition.getTitle() + " [" + category.definition.getId()
                        + "]\n");
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
            result.add(new DebugExportResult.CategoryResult(category.definition.getId(),
                    category.definition.getTitle(), category.files, category.warnings, category.failure));
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

    private static BufferedWriter writer(File file) throws IOException {
        return new LfBufferedWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()),
                StandardCharsets.UTF_8));
    }

    private static void moveDirectory(File source, File destination) throws IOException {
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
        private final DebugExportDefinition definition;
        private final List<DebugExportResult.FileResult> files = new ArrayList<DebugExportResult.FileResult>();
        private final List<String> warnings = new ArrayList<String>();
        private final Set<String> producedNames = new LinkedHashSet<String>();
        private String failure;

        private MutableCategory(DebugExportDefinition definition) {
            this.definition = definition;
        }

        private void verifyExpectedFiles() {
            for (String expected : definition.getFileNames()) {
                if (!producedNames.contains(expected)) {
                    throw new IllegalStateException("Exporter did not produce declared file: " + expected);
                }
            }
        }
    }

    /** BufferedWriter with platform-independent line endings. */
    private static final class LfBufferedWriter extends BufferedWriter {
        private LfBufferedWriter(Writer writer) {
            super(writer);
        }

        @Override
        public void newLine() throws IOException {
            write('\n');
        }
    }
}
