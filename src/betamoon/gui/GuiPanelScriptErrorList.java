package betamoon.gui;

import betamoon.gui.api.component.EnumScrollMode;
import betamoon.gui.api.component.GuiComponentBase;
import betamoon.gui.api.component.GuiTextClickable;
import betamoon.gui.api.component.IGuiAction;
import betamoon.gui.api.component.GuiScrollPanel;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.FontRenderer;

/**
 * Scrollable list of script errors and warnings with clickable links.
 */
final class GuiPanelScriptErrorList extends GuiComponentBase {
    private static final int ENTRY_GAP = 10;
    private static final int ENTRY_SEPARATOR_OFFSET = 4;
    private static final int CONTENT_PADDING = 6;
    private static final int MIN_REMAINDER_WIDTH = 10;

    private final List linkTexts = new ArrayList();
    private final GuiTextClickable inlineHelper = new GuiTextClickable();
    private final ErrorListContent content = new ErrorListContent();
    private final GuiScrollPanel scrollPanel = new GuiScrollPanel(content, EnumScrollMode.VERTICAL);
    private int screenWidth;
    private int screenHeight;

    /**
     * Creates a vertical scroll list for script issues.
     */
    GuiPanelScriptErrorList() {
    }

    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        scrollPanel.setBounds(left, top, right, bottom);
    }

    public void layout(int screenWidth, int screenHeight) {
        scrollPanel.layout(screenWidth, screenHeight);
    }

    /**
     * Updates display metrics required for scissor clipping.
     */
    void setDisplayMetrics(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        scrollPanel.setDisplayMetrics(screenWidth, screenHeight, displayWidth, displayHeight);
    }

    /**
     * Draws the scrollable list of errors and warnings.
     */
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        List entries = LuaScriptErrors.getEntries();
        int contentWidth = Math.max(0, right - left - CONTENT_PADDING);
        int contentHeight = measureContentHeight(font, entries, contentWidth);
        scrollPanel.setContentSize(Math.max(0, right - left), contentHeight);
        scrollPanel.draw(font, mouseX, mouseY, partialTicks);
    }

    /**
     * Routes mouse clicks to any inline links before the scroll panel.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return scrollPanel.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return scrollPanel.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        return scrollPanel.mouseDragged(mouseX, mouseY, mouseDown);
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        return scrollPanel.mouseScrolled(mouseX, mouseY, wheelDelta, shiftDown);
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return scrollPanel.keyTyped(typedChar, keyCode);
    }

    private final class ErrorListContent extends GuiComponentBase {
        public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
            List entries = LuaScriptErrors.getEntries();
            int contentWidth = Math.max(0, right - left - CONTENT_PADDING);
            int y = top;
            linkTexts.clear();
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
                    GuiUtils.drawHorizontalLine(left + CONTENT_PADDING, right - CONTENT_PADDING, lineY,
                        GuiUtils.COLOR_LIST_SEPERATOR);
                }
            }
        }

        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            for (int i = 0; i < linkTexts.size(); i++) {
                GuiTextClickable link = (GuiTextClickable) linkTexts.get(i);
                if (link.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }
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
