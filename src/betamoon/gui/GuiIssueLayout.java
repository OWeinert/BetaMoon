package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiRenderer;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.FontRenderer;

/** One measured format for popup and script-detail diagnostics. */
final class GuiIssueLayout {
    static final int PADDING = 3;
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
        for (ScriptIssue issue : GuiScriptIssueText.ordered(issues)) {
            Entry entry = Entry.prepare(font, issue, width);
            if (!entries.isEmpty()) {
                height += entryGap;
            }
            entries.add(entry);
            height += entry.height;
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
        final ScriptIssue issue;
        private final String heading;
        private final String location;
        private final String message;
        private final String outcome;
        private final int headingHeight;
        private final int locationHeight;
        private final int messageHeight;
        private final int height;

        private Entry(FontRenderer font, ScriptIssue issue, int width) {
            this.issue = issue;
            String title = GuiScriptIssueText.title(issue);
            heading = getSeverityLabel() + (title.isEmpty() ? "" : "\u00a77 \u00b7 \u00a7f" + title);
            location = GuiScriptIssueText.location(issue);
            message = GuiScriptIssueText.message(issue);
            outcome = GuiScriptIssueText.outcome(issue);
            // The native wrapper cannot advance when the line is narrower than a glyph.
            int innerWidth = Math.max(8, width - PADDING * 2);
            headingHeight = font.func_27277_a(heading, innerWidth) + 3;
            locationHeight = location.isEmpty() ? 0 : font.func_27277_a(location, innerWidth) + 3;
            messageHeight = wrappedHeight(font, message, innerWidth);
            height = PADDING * 2 + headingHeight + locationHeight + messageHeight
                    + (outcome.isEmpty() ? 0 : 4 + wrappedHeight(font, outcome, innerWidth));
        }

        static Entry prepare(FontRenderer font, ScriptIssue issue, int width) {
            return new Entry(font, issue, width);
        }

        int getHeight() {
            return height;
        }

        boolean isWarning() {
            return issue.isWarning();
        }

        String getSeverityLabel() {
            return issue.isWarning() ? "Warning" : "Error";
        }

        void draw(GuiContext context, int left, int top, int width, int severityColor) {
            int x = left + PADDING;
            int y = top + PADDING;
            int innerWidth = Math.max(8, width - PADDING * 2);
            context.getRenderer().drawWrappedText(heading, x, y, innerWidth, severityColor);
            y += headingHeight;
            if (!location.isEmpty()) {
                context.getRenderer().drawWrappedText(location, x, y, innerWidth,
                        context.getRenderer().getTheme().link);
                y += locationHeight;
            }
            drawLines(context, message, x, y, innerWidth, context.getRenderer().getTheme().textPrimary);
            if (!outcome.isEmpty()) {
                drawLines(context, outcome, x, y + messageHeight + 4, innerWidth,
                        context.getRenderer().getTheme().textMuted);
            }
        }

        private static int wrappedHeight(FontRenderer font, String text, int width) {
            int height = 0;
            for (String line : text.split("\\n", -1)) {
                height += line.isEmpty() ? GuiRenderer.lineHeight(font) : font.func_27277_a(line, width);
            }
            return height;
        }

        private static void drawLines(GuiContext context, String text, int x, int y, int width, int color) {
            for (String line : text.split("\\n", -1)) {
                context.getRenderer().drawWrappedText(line, x, y, width, color);
                y += line.isEmpty() ? context.getRenderer().lineHeight()
                        : context.getRenderer().wrappedTextHeight(line, width);
            }
        }
    }
}
