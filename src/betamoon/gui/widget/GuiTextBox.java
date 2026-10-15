package betamoon.gui.widget;

import net.minecraft.src.ChatAllowedCharacters;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.GuiTextField;

/**
 * Native Minecraft text field with bounded paste and a scrolling text viewport.
 * Owners forward logical ticks, keyboard input and mouse clicks just like
 * GuiTextField.
 */
public class GuiTextBox extends GuiTextField {
    private final FontRenderer font;
    private final int left;
    private final int top;
    private final int fieldWidth;
    private final int fieldHeight;
    private int limit;
    private int cursorTicks;

    public GuiTextBox(GuiScreen parent, FontRenderer font, int x, int y, int width, int height, int limit) {
        super(parent, font, x, y, width, height, "");
        this.font = font;
        left = x;
        top = y;
        fieldWidth = Math.max(0, width);
        fieldHeight = Math.max(0, height);
        setMaxStringLength(limit);
    }

    @Override
    public void setMaxStringLength(int limit) {
        this.limit = Math.max(0, limit);
        super.setMaxStringLength(this.limit);
        setText(getText());
    }

    @Override
    public void setText(String text) {
        String value = text == null ? "" : text;
        super.setText(limit > 0 && value.length() > limit ? value.substring(0, limit) : value);
    }

    @Override
    public void textboxKeyTyped(char character, int keyCode) {
        if (isEnabled && isFocused && character == 22) {
            appendText(GuiScreen.getClipboardString());
            return;
        }
        super.textboxKeyTyped(character, keyCode);
    }

    /** Filters clipboard text through the same character rules as native typing. */
    public void appendText(String value) {
        if (!isEnabled || !isFocused || value == null) {
            return;
        }
        StringBuilder result = new StringBuilder(getText());
        for (int i = 0; i < value.length() && (limit == 0 || result.length() < limit); i++) {
            char character = value.charAt(i);
            if (ChatAllowedCharacters.allowedCharacters.indexOf(character) >= 0) {
                result.append(character);
            }
        }
        setText(result.toString());
    }

    @Override
    public void updateCursorCounter() {
        super.updateCursorCounter();
        cursorTicks++;
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && !isFocused) {
            cursorTicks = 0;
        }
        super.setFocused(focused);
    }

    @Override
    public void drawTextBox() {
        if (font == null) {
            return;
        }
        int available = Math.max(0, fieldWidth - 8);
        if (font.getStringWidth(getText() + "_") <= available) {
            super.drawTextBox();
            return;
        }
        // Native fields have private, fixed geometry and do not clip long text.
        // Keep their exact frame, padding, colors and tick-based caret for overflow.
        drawRect(left - 1, top - 1, left + fieldWidth + 1, top + fieldHeight + 1, 0xFFA0A0A0);
        drawRect(left, top, left + fieldWidth, top + fieldHeight, 0xFF000000);
        String caret = isEnabled && isFocused && cursorTicks / 6 % 2 == 0 ? "_" : "";
        String visible = getText();
        int reserved = isEnabled && isFocused ? font.getStringWidth("_") : 0;
        while (!visible.isEmpty() && font.getStringWidth(visible) + reserved > available) {
            visible = isFocused ? visible.substring(1) : visible.substring(0, visible.length() - 1);
        }
        if (font.getStringWidth(visible + caret) <= available) {
            drawString(font, visible + caret, left + 4, top + (fieldHeight - 8) / 2, isEnabled ? 14737632 : 7368816);
        }
    }
}
