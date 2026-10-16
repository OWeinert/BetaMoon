package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiRenderer;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.FontRenderer;

/**
 * Prepared issue text used consistently for measurement, rendering, and links.
 */
final class GuiIssueLayout {
    private final List<Entry> entries;
    private final int height;

    private GuiIssueLayout(List<Entry> entries, int height) {
        this.entries = entries;
        this.height = height;
    }

    static GuiIssueLayout prepare(FontRenderer font, List<ScriptIssue> issues, int width, int entryGap) {
        if (font == null || issues == null || issues.isEmpty()) {
            return new GuiIssueLayout(Collections.<Entry>emptyList(), 0);
        }
        List<Entry> prepared = new ArrayList<Entry>();
        int totalHeight = 0;
        for (int i = 0; i < issues.size(); i++) {
            Entry entry = Entry.prepare(font, issues.get(i), width);
            prepared.add(entry);
            totalHeight += entry.height;
            if (i < issues.size() - 1) {
                totalHeight += entryGap;
            }
        }
        return new GuiIssueLayout(Collections.unmodifiableList(prepared), totalHeight);
    }

    int getHeight() {
        return height;
    }

    List<Entry> getEntries() {
        return entries;
    }

    static final class Entry {
        private final String[] lines;
        private final String linkText;
        private final File linkPath;
        private final int[] lineHeights;
        private final int height;
        private final int linkHeight;
        private final boolean warning;
        private Rect linkBounds = Rect.EMPTY;

        private Entry(String[] lines, String linkText, File linkPath, int[] lineHeights, int height, int linkHeight,
                boolean warning) {
            this.lines = lines;
            this.linkText = linkText;
            this.linkPath = linkPath;
            this.lineHeights = lineHeights;
            this.height = height;
            this.linkHeight = linkHeight;
            this.warning = warning;
        }

        static Entry prepare(FontRenderer font, ScriptIssue issue, int width) {
            String message = issue == null || issue.getMessage() == null ? "" : issue.getMessage();
            String linkText = buildLinkText(issue);
            if (issue != null && issue.getScriptName().equals(issue.getSourceFile())) {
                String prefix = issue.getScriptName() + ": ";
                if (message.startsWith(prefix)) {
                    message = message.substring(prefix.length());
                }
            }
            if (linkText != null && message.startsWith(linkText)) {
                message = message.substring(linkText.length());
                if (message.startsWith(":")) {
                    message = message.substring(1);
                }
                message = trimLeadingSpace(message);
            }
            String[] lines = message.split("\\n", -1);
            int[] lineHeights = new int[lines.length];
            int lineHeight = GuiRenderer.lineHeight(font);
            int safeWidth = Math.max(1, width);
            int linkHeight = linkText == null ? 0 : font.func_27277_a(linkText, safeWidth) + 3;
            int height = lineHeight + 3 + linkHeight;
            for (int i = 0; i < lines.length; i++) {
                lineHeights[i] = lines[i].isEmpty() ? lineHeight : font.func_27277_a(lines[i], safeWidth);
                height += lineHeights[i];
            }
            return new Entry(lines, linkText, resolveScriptFile(issue), lineHeights, height, linkHeight,
                    issue != null && issue.isWarning());
        }

        int getHeight() {
            return height;
        }

        boolean isWarning() {
            return warning;
        }

        String getSeverityLabel() {
            return warning ? "Warning" : "Error";
        }

        void draw(GuiContext context, int left, int top, int width, int severityColor) {
            int safeWidth = Math.max(1, width);
            context.getRenderer().drawText(getSeverityLabel(), left, top, severityColor);
            int y = top + context.getRenderer().lineHeight() + 3;
            if (linkText != null) {
                linkBounds = Rect.fromPositionAndSize(left, y,
                        Math.min(safeWidth, context.getRenderer().textWidth(linkText)), Math.max(0, linkHeight - 3));
                boolean hovered = linkBounds.contains(context.getMouseX(), context.getMouseY())
                        && context.getRenderer().isPointVisible(context.getMouseX(), context.getMouseY());
                context.getRenderer().drawWrappedText(linkText, left, y, safeWidth,
                        hovered ? context.getRenderer().getTheme().linkHover : context.getRenderer().getTheme().link);
                y += linkHeight;
            } else {
                linkBounds = Rect.EMPTY;
            }
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].isEmpty()) {
                    context.getRenderer().drawWrappedText(lines[i], left, y, safeWidth,
                            context.getRenderer().getTheme().textPrimary);
                }
                y += lineHeights[i];
            }
        }

        boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0 || linkPath == null || !linkBounds.contains(mouseX, mouseY)) {
                return false;
            }
            IoUtils.openPath(linkPath);
            return true;
        }

        private static String trimLeadingSpace(String value) {
            int index = 0;
            while (index < value.length() && (value.charAt(index) == ' ' || value.charAt(index) == '\t')) {
                index++;
            }
            return value.substring(index);
        }

        private static String buildLinkText(ScriptIssue issue) {
            if (issue == null || issue.getSourceFile() == null) {
                return null;
            }
            return issue.getLine() > 0 ? issue.getSourceFile() + ":" + issue.getLine() : issue.getSourceFile();
        }

        private static File resolveScriptFile(ScriptIssue issue) {
            if (issue == null || issue.getSourceFile() == null || LuaModLoader.getLuaModsDir() == null) {
                return null;
            }
            String source = issue.getSourceFile();
            int archiveSeparator = source.indexOf("!/");
            if (archiveSeparator >= 0) {
                source = source.substring(0, archiveSeparator);
            }
            File file = new File(LuaModLoader.getLuaModsDir(), source);
            return file.isFile() ? file : null;
        }
    }
}
