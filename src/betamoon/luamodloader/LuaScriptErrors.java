package betamoon.luamodloader;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks script load errors and warnings for UI display.
 */
public final class LuaScriptErrors {
    private static final List entries = new ArrayList();
    private static boolean ignored;

    private LuaScriptErrors() {
    }

    /**
     * Clears any previous load errors and resets the ignore flag.
     */
    public static synchronized void clear() {
        entries.clear();
        ignored = false;
    }

    /**
     * Adds a script error entry, normalizing missing values.
     *
     * @param script script name or file name
     * @param message error description
     */
    public static synchronized ScriptIssue add(String script, String message) {
        String scriptName = normalize(script, "Unknown");
        String detail = normalize(message, "Unknown error");
        ScriptIssue issue = new ScriptIssue(scriptName, detail, false);
        entries.add(issue);
        return issue;
    }

    /**
     * Adds a script warning entry, normalizing missing values.
     *
     * @param script script name or file name
     * @param message warning description
     */
    public static synchronized ScriptIssue addWarning(String script, String message) {
        String scriptName = normalize(script, "Unknown");
        String detail = normalize(message, "Unknown warning");
        ScriptIssue issue = new ScriptIssue(scriptName, detail, true);
        entries.add(issue);
        return issue;
    }

    /**
     * Returns true when a warning exists for the provided script names.
     *
     * @param displayName script display name or mod name
     * @param sourceFile script file name
     * @return true when warnings exist for the script
     */
    public static synchronized boolean hasWarningFor(String displayName, String sourceFile) {
        for (int i = 0; i < entries.size(); i++) {
            ScriptIssue issue = (ScriptIssue) entries.get(i);
            if (issue.isWarning() && matchesScript(issue, displayName, sourceFile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns issue entries for the provided script names.
     *
     * @param displayName script display name or mod name
     * @param sourceFile script file name
     * @return list of ScriptIssue entries
     */
    public static synchronized List getIssuesFor(String displayName, String sourceFile) {
        List issues = new ArrayList();
        for (int i = 0; i < entries.size(); i++) {
            ScriptIssue issue = (ScriptIssue) entries.get(i);
            if (matchesScript(issue, displayName, sourceFile)) {
                issues.add(issue);
            }
        }
        return issues;
    }

    /**
     * Returns a copy of formatted error entries.
     *
     * @return list of formatted error strings
     */
    public static synchronized List getEntries() {
        return new ArrayList(entries);
    }

    /**
     * Counts errors in the current issue set without including warnings.
     *
     * @return number of error entries
     */
    public static synchronized int getErrorCount() {
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (!((ScriptIssue) entries.get(i)).isWarning()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns true when the popup should be shown to the user.
     *
     * @return true when errors exist and have not been ignored
     */
    public static synchronized boolean shouldShowPopup() {
        return !ignored && !entries.isEmpty();
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
     * @param value raw value to normalize
     * @param fallback text when the value is null or empty
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
        private final boolean warning;
        private final String scriptName;
        private final String sourceFile;
        private final int line;

        private ScriptIssue(String scriptName, String detail, boolean warning) {
            this.warning = warning;
            this.scriptName = scriptName;
            this.message = scriptName + ": " + detail;
            String file = null;
            int lineValue = -1;
            if (scriptName != null && scriptName.endsWith(".lua")) {
                file = scriptName;
            }
            // Extract "file.lua:line" from the detail when present.
            int luaIndex = detail.indexOf(".lua:");
            if (luaIndex >= 0) {
                int fileStart = detail.lastIndexOf(' ', luaIndex);
                if (fileStart < 0) {
                    fileStart = 0;
                } else {
                    fileStart += 1;
                }
                String fileCandidate = detail.substring(fileStart, luaIndex + 4);
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
