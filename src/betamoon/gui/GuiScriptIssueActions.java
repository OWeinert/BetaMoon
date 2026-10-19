package betamoon.gui;

import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import betamoon.luamodloader.ScriptIssueReports;
import java.awt.AWTError;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/** User-invoked diagnostic actions, with replaceable desktop services for headless verification. */
final class GuiScriptIssueActions {
    interface Clipboard {
        void write(String text) throws Exception;
    }

    interface ReportOpener {
        void open(Path path) throws Exception;
    }

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final Clipboard clipboard;
    private final ReportOpener opener;
    private final Supplier<Path> directory;
    private final LongSupplier clock;
    private String feedback = "";
    private String detail = "";
    private long feedbackUntil;
    private boolean failed;
    private Path report;
    private boolean exportedFeedback;

    GuiScriptIssueActions() {
        this(text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null),
                path -> {
                    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        throw new IOException("Opening reports is unavailable on this desktop");
                    }
                    Desktop.getDesktop().open(path.toFile());
                }, () -> Minecraft.getMinecraftDir().toPath().resolve("betamoon/reports"), System::currentTimeMillis);
    }

    GuiScriptIssueActions(Clipboard clipboard, ReportOpener opener, Supplier<Path> directory, LongSupplier clock) {
        this.clipboard = clipboard;
        this.opener = opener;
        this.directory = directory;
        this.clock = clock;
    }

    void copy(ScriptIssue issue) {
        copyText(GuiScriptIssueText.diagnostic(issue));
    }

    void copyAll(List<ScriptIssue> issues) {
        copyText(GuiScriptIssueText.issueList(issues));
    }

    private void copyText(String text) {
        try {
            clipboard.write(text);
            status("Copied to clipboard", "", false);
        } catch (Exception | LinkageError | AWTError error) {
            status("Copy failed", error.toString(), true);
        }
    }

    Path export(List<ScriptIssue> issues) {
        Path created = null;
        try {
            Path root = directory.get();
            Files.createDirectories(root);
            created = Files.createTempFile(root, "script-issues-" + FILE_TIME.format(LocalDateTime.now()) + "-", ".txt");
            Files.write(created, ScriptIssueReports.generate(issues, "All recorded errors and warnings; manual save")
                    .getBytes(StandardCharsets.UTF_8));
            report = created.toAbsolutePath();
            status("Report saved", report.toString(), false);
            exportedFeedback = true;
            return report;
        } catch (Exception | LinkageError | AWTError error) {
            if (created != null) {
                try {
                    Files.deleteIfExists(created);
                } catch (IOException | SecurityException cleanup) {
                    error.addSuppressed(cleanup);
                }
            }
            status("Export failed", error.toString(), true);
            return null;
        }
    }

    void openReport() {
        openReport(report);
    }

    boolean openReport(Path path) {
        if (path == null) {
            return false;
        }
        try {
            opener.open(path);
            if (failed()) {
                status("Report opened", path.toString(), false);
            }
            return true;
        } catch (Exception | LinkageError | AWTError error) {
            status("Could not open report", path + "\n" + error, true);
            return false;
        }
    }

    String feedback() {
        return clock.getAsLong() < feedbackUntil ? feedback : "";
    }

    String detail() {
        return detail;
    }

    boolean failed() {
        return !feedback().isEmpty() && failed;
    }

    Path report() {
        return report;
    }

    boolean reportLinkVisible() {
        return report != null && !failed() && (feedback().isEmpty() || exportedFeedback);
    }

    private void status(String message, String detail, boolean failed) {
        this.feedback = message;
        this.detail = detail;
        this.failed = failed;
        exportedFeedback = false;
        feedbackUntil = clock.getAsLong() + (failed ? 8000 : 2500);
    }
}
