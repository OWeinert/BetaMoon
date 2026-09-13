package betamoon.gui;

import betamoon.gui.api.component.GuiTextClickable;
import betamoon.gui.api.component.IGuiAction;
import betamoon.gui.api.util.GuiText;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.FontRenderer;

/** Prepared issue text whose measurement is also used for drawing and links. */
final class GuiIssueLayout {
    private final List<Entry> entries;
    private final int height;

    private GuiIssueLayout(List<Entry> entries, int height) {
        this.entries = entries;
        this.height = height;
    }

    static GuiIssueLayout prepare(FontRenderer font, List<ScriptIssue> issues, int width, int entryGap) {
        if (font == null || issues == null || issues.isEmpty()) {
            return new GuiIssueLayout(Collections.emptyList(), 0);
        }

        List<Entry> entries = new ArrayList<>();
        int height = 0;
        for (int i = 0; i < issues.size(); i++) {
            Entry entry = Entry.prepare(font, issues.get(i), width);
            entries.add(entry);
            height += entry.height;
            if (i < issues.size() - 1) {
                height += entryGap;
            }
        }
        return new GuiIssueLayout(Collections.unmodifiableList(entries), height);
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
            int lineHeight = GuiText.getLineHeight(font);
            int height = 0;
            for (int i = 0; i < lines.length; i++) {
                if (i == 0) {
                    lineHeights[i] = Math.max(lineHeight,
                            GuiTextClickable.measureInlineHeight(font, lines[i], linkText, width));
                } else if (lines[i].length() == 0) {
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

        int draw(FontRenderer font, GuiTextClickable inlineHelper, List<GuiTextClickable> links, int left, int top,
                int width, int screenWidth, int screenHeight, int color, int mouseX, int mouseY, float partialTicks) {
            if (lines.length == 0) {
                return 0;
            }

            IGuiAction action = linkPath == null ? null : () -> IoUtils.openPath(linkPath);
            inlineHelper.drawInline(font, lines[0], linkText, action, left, top, width, screenWidth, screenHeight,
                    color, mouseX, mouseY, partialTicks, links);
            int y = top + lineHeights[0];
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].length() > 0) {
                    font.func_27278_a(lines[i], left, y, width, color);
                }
                y += lineHeights[i];
            }
            return height;
        }

        private static String buildLinkText(ScriptIssue issue) {
            if (issue == null || issue.getSourceFile() == null) {
                return null;
            }
            if (issue.getLine() > 0) {
                return issue.getSourceFile() + ":" + issue.getLine();
            }
            return issue.getSourceFile();
        }

        private static File resolveScriptFile(ScriptIssue issue) {
            if (issue == null || issue.getSourceFile() == null) {
                return null;
            }
            File scriptsDirectory = LuaModLoader.getLuaModsDir();
            if (scriptsDirectory == null) {
                return null;
            }

            File file = new File(scriptsDirectory, issue.getSourceFile());
            return file.isFile() ? file : null;
        }
    }
}
