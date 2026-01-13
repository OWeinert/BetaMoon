package betamoon.scriptloader;

import java.util.ArrayList;
import java.util.List;

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
    public static synchronized void add(String script, String message) {
        String scriptName = normalize(script, "Unknown");
        String detail = normalize(message, "Unknown error");
        entries.add(scriptName + ": " + detail);
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
}
