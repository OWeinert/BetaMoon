package betamoon.luamodloader;

import betamoon.logging.RuntimeLogs;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks script load errors and warnings for UI display.
 */
public final class LuaScriptErrors {
    public enum Outcome {
        UNKNOWN, CALLBACK_DISABLED, BINDING_SUSPENDED, APPEARANCE_RETAINED, LOAD_FAILED, ACTIVE_SCRIPTS_RETAINED
    }

    private static final List<ScriptIssue> entries = new ArrayList<>();
    private static boolean ignored;
    private static long revision;

    private LuaScriptErrors() {
    }

    /**
     * Clears any previous load errors and resets the ignore flag.
     */
    public static synchronized void clear() {
        entries.clear();
        ignored = false;
        revision++;
    }

    /**
     * Adds a script error entry, normalizing missing values.
     *
     * @param script
     *            script name or file name
     * @param message
     *            error description
     */
    public static synchronized ScriptIssue add(String script, String message) {
        return add(script, message, Outcome.UNKNOWN);
    }

    public static synchronized ScriptIssue add(String script, String message, Outcome outcome) {
        return add(script, message, outcome, null);
    }

    public static synchronized ScriptIssue add(String script, String message, Outcome outcome, Throwable error) {
        String scriptName = normalize(script, "Unknown");
        String detail = normalize(message, "Unknown error");
        ScriptIssue issue = new ScriptIssue(scriptName, detail, false, outcome, error);
        entries.add(issue);
        revision++;
        RuntimeLogs.issue(issue);
        return issue;
    }

    /**
     * Adds a script warning entry, normalizing missing values.
     *
     * @param script
     *            script name or file name
     * @param message
     *            warning description
     */
    public static synchronized ScriptIssue addWarning(String script, String message) {
        return addWarning(script, message, null);
    }

    public static synchronized ScriptIssue addWarning(String script, String message, Throwable error) {
        String scriptName = normalize(script, "Unknown");
        String detail = normalize(message, "Unknown warning");
        ScriptIssue issue = new ScriptIssue(scriptName, detail, true, Outcome.UNKNOWN, error);
        entries.add(issue);
        revision++;
        RuntimeLogs.issue(issue);
        return issue;
    }

    public static synchronized long getRevision() {
        return revision;
    }

    public static synchronized boolean hasErrorFor(String displayName, String sourceFile) {
        for (ScriptIssue issue : entries) {
            if (!issue.isWarning() && matchesScript(issue, displayName, sourceFile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when a warning exists for the provided script names.
     *
     * @param displayName
     *            script display name or mod name
     * @param sourceFile
     *            script file name
     * @return true when warnings exist for the script
     */
    public static synchronized boolean hasWarningFor(String displayName, String sourceFile) {
        for (int i = 0; i < entries.size(); i++) {
            ScriptIssue issue = entries.get(i);
            if (issue.isWarning() && matchesScript(issue, displayName, sourceFile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns issue entries for the provided script names.
     *
     * @param displayName
     *            script display name or mod name
     * @param sourceFile
     *            script file name
     * @return list of ScriptIssue entries
     */
    public static synchronized List<ScriptIssue> getIssuesFor(String displayName, String sourceFile) {
        List<ScriptIssue> issues = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ScriptIssue issue = entries.get(i);
            if (matchesScript(issue, displayName, sourceFile)) {
                issues.add(issue);
            }
        }
        return issues;
    }

    /**
     * Returns a snapshot of the current script issues.
     *
     * @return list of script issues
     */
    public static synchronized List<ScriptIssue> getEntries() {
        return new ArrayList<>(entries);
    }

    /** Returns the issues included in an automatic startup or reload notification. */
    public static synchronized List<ScriptIssue> getNotificationEntries(boolean showWarnings) {
        List<ScriptIssue> visible = new ArrayList<>();
        for (ScriptIssue issue : entries) {
            if (showWarnings || !issue.isWarning()) {
                visible.add(issue);
            }
        }
        return visible;
    }

    /**
     * Counts errors in the current issue set without including warnings.
     *
     * @return number of error entries
     */
    public static synchronized int getErrorCount() {
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).isWarning()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns true when the popup should be shown to the user.
     *
     * @return true when visible issues exist and have not been ignored
     */
    public static synchronized boolean shouldShowPopup(boolean showWarnings) {
        return !ignored && !getNotificationEntries(showWarnings).isEmpty();
    }

    /**
     * Marks the current error set as ignored for the session.
     */
    public static synchronized void ignore() {
        ignored = true;
    }

    /**
     * Trims user-facing strings while preserving a reasonable fallback.
     *
     * @param value
     *            raw value to normalize
     * @param fallback
     *            text when the value is null or empty
     * @return normalized non-empty string
     */
    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static boolean matchesScript(ScriptIssue issue, String displayName, String sourceFile) {
        if (issue == null) {
            return false;
        }
        String scriptName = issue.getScriptName();
        if (scriptName != null) {
            if (displayName != null && scriptName.equals(displayName)) {
                return true;
            }
            if (sourceFile != null && scriptName.equals(sourceFile)) {
                return true;
            }
        }
        String issueFile = issue.getSourceFile();
        return sourceFile != null && sourceFile.equals(issueFile);
    }

    /**
     * Parsed script issue with optional source file/line info.
     */
    public static final class ScriptIssue {
        private final String message;
        private final String detail;
        private final Outcome outcome;
        private final boolean warning;
        private final String scriptName;
        private final String sourceFile;
        private final int line;
        private final ScriptDiagnostics.Occurrence occurrence;
        private final String scriptState;
        private final String scriptVersion;
        private final String scriptDisplayName;
        private final String scriptSource;
        private final String stackTrace;

        /** Describes a registry failure without mutating the recorded issue collection. */
        public static ScriptIssue describe(String script, String message, boolean warning) {
            return new ScriptIssue(normalize(script, "Unknown"), normalize(message, "Unknown error"), warning,
                    warning ? Outcome.UNKNOWN : Outcome.LOAD_FAILED, null);
        }

        private ScriptIssue(String scriptName, String detail, boolean warning, Outcome outcome, Throwable error) {
            this.warning = warning;
            this.scriptName = scriptName;
            this.detail = detail;
            this.outcome = outcome == null ? Outcome.UNKNOWN : outcome;
            this.message = scriptName + ": " + detail;
            occurrence = ScriptDiagnostics.occurrence(error);
            ScriptMod mod = LuaScriptRegistry.findByFile(scriptName);
            if (mod == null && occurrence.owner != null) {
                mod = LuaScriptRegistry.findByFile(occurrence.owner);
            }
            if (mod == null) {
                for (ScriptMod candidate : LuaScriptRegistry.getEntries()) {
                    if (scriptName.equals(candidate.getDisplayName())) {
                        mod = candidate;
                        break;
                    }
                }
            }
            boolean capturedOwner = occurrence.scriptDisplayName != null
                    && (scriptName.equals(occurrence.owner) || scriptName.equals(occurrence.scriptDisplayName));
            if (capturedOwner) {
                scriptState = occurrence.scriptState;
                scriptVersion = occurrence.scriptVersion;
                scriptDisplayName = occurrence.scriptDisplayName;
                scriptSource = occurrence.owner;
            } else {
                scriptState = ScriptDiagnostics.scriptState(mod);
                scriptVersion = mod == null ? "unavailable" : mod.getVersion();
                scriptDisplayName = mod == null ? scriptName : mod.getDisplayName();
                scriptSource = mod == null ? scriptName : mod.getSourceFileName();
            }
            stackTrace = trace(error);
            String file = null;
            int lineValue = -1;
            if (scriptName != null && (scriptName.endsWith(".lua") || scriptName.endsWith(".zip"))) {
                file = scriptName;
            }
            // Extract "file.lua:line" from the detail when present.
            int luaIndex = detail.indexOf(".lua:");
            if (luaIndex >= 0) {
                int marker = detail.lastIndexOf('@', luaIndex);
                int fileStart = marker >= 0 && detail.substring(marker, luaIndex).indexOf('\n') < 0
                        ? marker + 1 : detail.lastIndexOf(' ', luaIndex) + 1;
                if (marker < 0 && scriptName.endsWith(".lua") && detail.startsWith(scriptName + ":")) {
                    fileStart = 0;
                }
                String fileCandidate = detail.substring(fileStart, luaIndex + 4);
                while (fileCandidate.startsWith("@") || fileCandidate.startsWith("(")) {
                    fileCandidate = fileCandidate.substring(1);
                }
                int lineStart = luaIndex + 5;
                int lineEnd = lineStart;
                while (lineEnd < detail.length()) {
                    char ch = detail.charAt(lineEnd);
                    if (ch < '0' || ch > '9') {
                        break;
                    }
                    lineEnd++;
                }
                if (lineEnd > lineStart) {
                    try {
                        lineValue = Integer.parseInt(detail.substring(lineStart, lineEnd));
                        file = fileCandidate;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            this.sourceFile = file;
            this.line = lineValue;
        }

        /**
         * Returns the formatted message to display.
         */
        public String getMessage() {
            return message;
        }

        public String getDetail() {
            return detail;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public ScriptDiagnostics.Occurrence getOccurrence() {
            return occurrence;
        }

        public String getScriptState() {
            return scriptState;
        }

        public String getScriptVersion() {
            return scriptVersion;
        }

        public String getScriptDisplayName() {
            return scriptDisplayName;
        }

        public String getScriptSource() {
            return scriptSource;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        private static String trace(Throwable error) {
            if (error == null) {
                return "";
            }
            try {
                StringWriter text = new StringWriter();
                error.printStackTrace(new PrintWriter(text));
                return text.toString();
            } catch (Throwable failure) {
                ScriptDiagnostics.rethrowFatal(failure);
                return "Stack trace unavailable (" + failure.getClass().getSimpleName() + ")";
            }
        }

        /**
         * Returns true when this issue is a warning.
         */
        public boolean isWarning() {
            return warning;
        }

        /**
         * Returns the script name used for this issue.
         */
        public String getScriptName() {
            return scriptName;
        }

        /**
         * Returns the source file name when detected.
         */
        public String getSourceFile() {
            return sourceFile;
        }

        /**
         * Returns the 1-based line number when detected, or -1.
         */
        public int getLine() {
            return line;
        }
    }
}
