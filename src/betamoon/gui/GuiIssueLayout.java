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

/** Prepared issue text used consistently for measurement, rendering, and links. */
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
        private final boolean warning;
        private Rect linkBounds = Rect.EMPTY;

        private Entry(String[] lines, String linkText, File linkPath, int[] lineHeights, int height, boolean warning) {
            this.lines = lines;
            this.linkText = linkText;
            this.linkPath = linkPath;
            this.lineHeights = lineHeights;
            this.height = height;
            this.warning = warning;
        }

        static Entry prepare(FontRenderer font, ScriptIssue issue, int width) {
            String message = issue == null || issue.getMessage() == null ? "" : issue.getMessage();
            String[] lines = message.split("\\n", -1);
            String linkText = buildLinkText(issue);
            int[] lineHeights = new int[lines.length];
            int lineHeight = GuiRenderer.lineHeight(font);
            int height = 0;
            for (int i = 0; i < lines.length; i++) {
                if (i == 0) {
                    lineHeights[i] = Math.max(lineHeight, measureFirstLine(font, lines[i], linkText, width));
                } else if (lines[i].isEmpty()) {
                    lineHeights[i] = lineHeight;
                } else {
                    lineHeights[i] = font.func_27277_a(lines[i], width);
                }
                height += lineHeights[i];
            }
            return new Entry(lines, linkText, resolveScriptFile(issue), lineHeights, height,
                    issue != null && issue.isWarning());
        }

        int getHeight() {
            return height;
        }

        boolean isWarning() {
            return warning;
        }

        void draw(GuiContext context, int left, int top, int width, int color) {
            if (lines.length == 0) {
                linkBounds = Rect.EMPTY;
                return;
            }
            drawFirstLine(context, lines[0], left, top, width, color);
            int y = top + lineHeights[0];
            for (int i = 1; i < lines.length; i++) {
                if (!lines[i].isEmpty()) {
                    context.getRenderer().drawWrappedText(lines[i], left, y, width, color);
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

        private void drawFirstLine(GuiContext context, String fullText, int left, int top, int width, int color) {
            int linkIndex = linkText == null ? -1 : fullText.indexOf(linkText);
            if (linkIndex < 0) {
                linkBounds = Rect.EMPTY;
                context.getRenderer().drawWrappedText(fullText, left, top, width, color);
                return;
            }
            int linkWidth = context.getRenderer().textWidth(linkText);
            int linkHeight = context.getRenderer().lineHeight();
            linkBounds = Rect.fromPositionAndSize(left, top, linkWidth, linkHeight);
            boolean hovered = linkBounds.contains(context.getMouseX(), context.getMouseY());
            int linkColor = hovered ? context.getRenderer().getTheme().linkHover
                    : context.getRenderer().getTheme().link;
            context.getRenderer().drawText(linkText, left, top, linkColor);
            if (hovered) {
                context.getRenderer().drawHorizontalLine(left, left + linkWidth, top + linkHeight + 1,
                        context.getRenderer().getTheme().linkUnderline);
            }
            String remainder = trimLeadingSpace(fullText.substring(linkIndex + linkText.length()));
            if (!remainder.isEmpty()) {
                int remainderX = left + linkWidth + 4;
                context.getRenderer().drawWrappedText(remainder, remainderX, top,
                        Math.max(10, width - linkWidth - 4), color);
            }
        }

        private static int measureFirstLine(FontRenderer font, String fullText, String linkText, int width) {
            int linkIndex = linkText == null ? -1 : fullText.indexOf(linkText);
            if (linkIndex < 0) {
                return fullText.isEmpty() ? GuiRenderer.lineHeight(font) : font.func_27277_a(fullText, width);
            }
            String remainder = trimLeadingSpace(fullText.substring(linkIndex + linkText.length()));
            int remainderWidth = Math.max(10, width - font.getStringWidth(linkText) - 4);
            int remainderHeight = remainder.isEmpty() ? 0 : font.func_27277_a(remainder, remainderWidth);
            return Math.max(GuiRenderer.lineHeight(font), remainderHeight);
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
