package betamoon.gui;

import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import betamoon.luamodloader.ScriptIssueReports;
import java.util.ArrayList;
import java.util.List;

/** Presentation text is compact; clipboard/export text retains the original diagnostic in full. */
final class GuiScriptIssueText {
    private GuiScriptIssueText() {
    }

    static List<ScriptIssue> ordered(List<ScriptIssue> issues) {
        List<ScriptIssue> result = new ArrayList<>(issues);
        result.sort((first, second) -> Boolean.compare(first.isWarning(), second.isWarning()));
        return result;
    }

    static String title(ScriptIssue issue) {
        String title = issue.getScriptDisplayName();
        return title.equals(issue.getSourceFile()) ? "" : title;
    }

    static String location(ScriptIssue issue) {
        if (issue.getSourceFile() == null) {
            return "";
        }
        return issue.getSourceFile() + (issue.getLine() > 0 ? ":" + issue.getLine() : "");
    }

    static String message(ScriptIssue issue) {
        String text = issue.getDetail();
        // Strip only exact, leading metadata already displayed above. Preserve embedded locations and stack frames.
        for (int attempt = 0; attempt < 4; attempt++) {
            String previous = text;
            if (text.startsWith(issue.getScriptName() + ": ")) {
                text = text.substring(issue.getScriptName().length() + 2);
            }
            String location = location(issue);
            if (!location.isEmpty()) {
                String prefix = text.startsWith("@") ? "@" + location : location;
                if (text.startsWith(prefix) && text.length() > prefix.length()) {
                    char next = text.charAt(prefix.length());
                    if (next == ':' || Character.isWhitespace(next)) {
                        text = text.substring(prefix.length() + (next == ':' ? 1 : 0)).trim();
                    }
                }
            }
            if (text.equals(previous)) {
                break;
            }
        }
        String marker = "@" + location(issue);
        int position = text.indexOf(marker);
        int newline = text.indexOf('\n');
        if (marker.length() > 1 && position > 0 && (newline < 0 || position < newline)
                && text.substring(0, position).trim().endsWith(":")) {
            int end = position + marker.length();
            if (end < text.length() && (text.charAt(end) == ':' || Character.isWhitespace(text.charAt(end)))) {
                if (text.charAt(end) == ':') {
                    end++;
                }
                text = text.substring(0, position) + text.substring(end).trim();
            }
        }
        return text;
    }

    static String outcome(ScriptIssue issue) {
        String outcome = ScriptIssueReports.outcome(issue);
        return "unavailable".equals(outcome) ? "" : outcome;
    }

    static String diagnostic(ScriptIssue issue) {
        StringBuilder text = new StringBuilder(issue.isWarning() ? "WARNING" : "ERROR");
        text.append(" | ").append(issue.getScriptName()).append('\n');
        if (!location(issue).isEmpty()) {
            text.append("Source: ").append(location(issue)).append('\n');
        }
        if (!outcome(issue).isEmpty()) {
            text.append("Status: ").append(outcome(issue)).append('\n');
        }
        return text.append(issue.getMessage()).toString();
    }

    static String issueList(List<ScriptIssue> issues) {
        StringBuilder text = new StringBuilder();
        for (ScriptIssue issue : ordered(issues)) {
            text.append(diagnostic(issue)).append("\n\n");
        }
        return text.toString();
    }
}
