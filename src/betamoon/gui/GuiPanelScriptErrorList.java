package betamoon.gui;

import betamoon.gui.api.component.EnumScrollMode;
import betamoon.gui.api.component.GuiTextClickable;
import betamoon.gui.api.component.IGuiAction;
import betamoon.gui.api.component.GuiScrollPanel;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import betamoon.io.IoUtils;
import betamoon.scriptloader.LuaModLoader;
import betamoon.scriptloader.LuaScriptErrors;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.FontRenderer;

/**
 * Scrollable list of script errors and warnings with clickable links.
 */
final class GuiPanelScriptErrorList extends GuiScrollPanel {
    private static final int ENTRY_GAP = 10;
    private static final int ENTRY_SEPARATOR_OFFSET = 4;
    private static final int CONTENT_PADDING = 6;
    private static final int MIN_REMAINDER_WIDTH = 10;

    private final List linkTexts = new ArrayList();
    private final GuiTextClickable inlineHelper = new GuiTextClickable();
    private int screenWidth;
    private int screenHeight;
    private int displayWidth;
    private int displayHeight;

    /**
     * Creates a vertical scroll list for script issues.
     */
    GuiPanelScriptErrorList() {
        super(EnumScrollMode.VERTICAL);
    }

    /**
     * Updates display metrics required for scissor clipping.
     */
    void setDisplayMetrics(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
    }

    /**
     * Draws the scrollable list of errors and warnings.
     */
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        List entries = LuaScriptErrors.getEntries();
        int contentWidth = Math.max(0, right - left - CONTENT_PADDING);
        int contentHeight = measureContentHeight(font, entries, contentWidth);
        updateScrollContentSize(contentWidth, contentHeight);

        int y = top - getScrollOffsetY();
        int scissorTop = top - 2;
        int scissorBottom = bottom + 2;
        linkTexts.clear();
        // Clip to the scroll panel so off-screen entries are not drawn.
        GuiUtils.beginScissor(left, scissorTop, right, scissorBottom, screenWidth, screenHeight, displayWidth, displayHeight);
        for (int i = 0; i < entries.size(); i++) {
            LuaScriptErrors.ScriptIssue issue = (LuaScriptErrors.ScriptIssue) entries.get(i);
            String entry = issue.getMessage();
            int color = issue.isWarning() ? GuiColors.TEXT_WARNING : GuiColors.TEXT_ERROR;
            int entryY = y;
            String linkText = buildLinkText(issue);
            File linkPath = resolveScriptFile(issue);
            IGuiAction action = linkPath == null ? null : () -> IoUtils.openPath(linkPath);
            int entryHeight = drawEntry(font, entry, linkText, action, left + CONTENT_PADDING, entryY,
                contentWidth, screenWidth, screenHeight, color, mouseX, mouseY, partialTicks, linkTexts);
            y = entryY + entryHeight + ENTRY_GAP;
            if (i < entries.size() - 1) {
                int lineY = entryY + entryHeight + ENTRY_SEPARATOR_OFFSET;
                GuiUtils.drawHorizontalLine(left + CONTENT_PADDING, right - CONTENT_PADDING, lineY, GuiUtils.COLOR_LIST_SEPERATOR);
            }
        }
        GuiUtils.endScissor();

        drawScrollbar(contentHeight);
    }

    /**
     * Routes mouse clicks to any inline links before the scroll panel.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = 0; i < linkTexts.size(); i++) {
            GuiTextClickable link = (GuiTextClickable) linkTexts.get(i);
            if (link.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Computes total scroll height for all entries.
     */
    private int measureContentHeight(FontRenderer font, List entries, int contentWidth) {
        if (font == null || entries == null || entries.isEmpty()) {
            return 0;
        }
        int height = 0;
        for (int i = 0; i < entries.size(); i++) {
            LuaScriptErrors.ScriptIssue issue = (LuaScriptErrors.ScriptIssue) entries.get(i);
            String entry = issue.getMessage();
            String linkText = buildLinkText(issue);
            int entryHeight = measureEntryHeight(font, entry, linkText, contentWidth);
            height += entryHeight + ENTRY_GAP;
        }
        if (height > 0) {
            height -= ENTRY_GAP;
        }
        return height;
    }

    /**
     * Measures the height of a single entry with optional inline link text.
     */
    private int measureEntryHeight(FontRenderer font, String fullText, String linkText, int maxWidth) {
        if (font == null || fullText == null) {
            return 0;
        }
        String[] lines = splitLines(fullText);
        if (lines.length == 0) {
            return 0;
        }
        int height = 0;
        int lineHeight = GuiText.getLineHeight(font);
        int firstLineHeight = measureInlineLineHeight(font, lines[0], linkText, maxWidth);
        height += firstLineHeight > 0 ? firstLineHeight : lineHeight;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) {
                height += lineHeight;
            } else {
                height += font.func_27277_a(line, maxWidth);
            }
        }
        return height;
    }

    private String trimInlineRemainder(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        int index = 0;
        while (index < value.length()) {
            char ch = value.charAt(index);
            if (ch == ' ' || ch == '\t') {
                index++;
                continue;
            }
            break;
        }
        return value.substring(index);
    }

    private int measureInlineLineHeight(FontRenderer font, String fullText, String linkText, int maxWidth) {
        if (linkText == null || linkText.length() == 0) {
            return font.func_27277_a(fullText, maxWidth);
        }
        int linkIndex = fullText.indexOf(linkText);
        if (linkIndex < 0) {
            return font.func_27277_a(fullText, maxWidth);
        }
        String remainder = trimInlineRemainder(fullText.substring(linkIndex + linkText.length()));
        int linkWidth = font.getStringWidth(linkText);
        int linkHeight = GuiText.getLineHeight(font);
        int remainderWidth = Math.max(MIN_REMAINDER_WIDTH, maxWidth - linkWidth - 4);
        int remainderHeight = remainder.length() > 0 ? font.func_27277_a(remainder, remainderWidth) : 0;
        return Math.max(linkHeight, remainderHeight);
    }

    private int drawEntry(FontRenderer font, String fullText, String linkText, IGuiAction action, int left,
        int top, int maxWidth, int screenWidth, int screenHeight, int textColor, int mouseX, int mouseY,
        float partialTicks, List linkSink) {
        if (font == null || fullText == null) {
            return 0;
        }
        String[] lines = splitLines(fullText);
        if (lines.length == 0) {
            return 0;
        }
        int lineHeight = GuiText.getLineHeight(font);
        int y = top;
        int firstLineHeight = inlineHelper.drawInline(font, lines[0], linkText, action, left, y, maxWidth,
            screenWidth, screenHeight, textColor, mouseX, mouseY, partialTicks, linkSink);
        int usedHeight = Math.max(firstLineHeight, lineHeight);
        y += usedHeight;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) {
                y += lineHeight;
                usedHeight += lineHeight;
                continue;
            }
            font.func_27278_a(line, left, y, maxWidth, textColor);
            int lineHeightUsed = font.func_27277_a(line, maxWidth);
            y += lineHeightUsed;
            usedHeight += lineHeightUsed;
        }
        return usedHeight;
    }

    private String[] splitLines(String value) {
        if (value == null) {
            return new String[0];
        }
        return value.split("\\n", -1);
    }

    /**
     * Builds the clickable link label for a script issue.
     */
    private String buildLinkText(LuaScriptErrors.ScriptIssue issue) {
        if (issue == null || issue.getSourceFile() == null) {
            return null;
        }
        if (issue.getLine() > 0) {
            return issue.getSourceFile() + ":" + issue.getLine();
        }
        return issue.getSourceFile();
    }

    /**
     * Resolves the actual script file for a link, if it exists on disk.
     */
    private File resolveScriptFile(LuaScriptErrors.ScriptIssue issue) {
        if (issue == null || issue.getSourceFile() == null) {
            return null;
        }
        File scriptsDir = LuaModLoader.getLuaModsDir();
        if (scriptsDir == null) {
            return null;
        }
        File file = new File(scriptsDir, issue.getSourceFile());
        if (file.isFile()) {
            return file;
        }
        return null;
    }
}
