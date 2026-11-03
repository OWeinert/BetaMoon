package betamoon.gui.widget;

import betamoon.gui.control.GuiTextEditorModel;
import betamoon.gui.control.GuiTextEditKeys;
import org.lwjgl.input.Keyboard;
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
    private int offset;
    private GuiTextEditorModel editor;

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
        value = limit > 0 && value.length() > limit ? value.substring(0, limit) : value;
        editor = new GuiTextEditorModel(value, limit == 0 ? Integer.MAX_VALUE : limit);
        offset = 0;
        super.setText(value);
    }

    @Override
    public String getText() {
        return editor == null ? super.getText() : editor.text();
    }

    public GuiTextEditorModel editorModel() {
        return editor;
    }

    @Override
    public void textboxKeyTyped(char character, int keyCode) {
        if (!isEnabled || !isFocused || editor == null) {
            return;
        }
        boolean shift = Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        boolean control = Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        GuiTextEditKeys.apply(editor, character, keyCode, shift, control,
                code -> ChatAllowedCharacters.allowedCharacters.indexOf(code) >= 0);
        super.setText(editor.text());
        cursorTicks = 0;
    }

    /** Filters clipboard text through the same character rules as native typing. */
    public void appendText(String value) {
        if (isEnabled && isFocused && value != null && editor != null) {
            GuiTextEditKeys.insert(editor, value, code -> ChatAllowedCharacters.allowedCharacters.indexOf(code) >= 0);
            super.setText(editor.text());
        }
    }

    @Override
    public void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (button == 0 && isEnabled && isFocused && editor != null && font != null) {
            int index = offset;
            while (index < editor.text().length()
                    && font.getStringWidth(editor.text().substring(offset, index + 1)) < x - left - 4) {
                index++;
            }
            editor.move(index, false);
            cursorTicks = 0;
        }
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
        String text = getText();
        int caretPosition = editor.caret();
        offset = isFocused ? Math.min(offset, caretPosition) : 0;
        int low = offset;
        int high = caretPosition;
        String reserved = isFocused ? "_" : "";
        if (isFocused) {
            while (low < high) {
                int middle = low + (high - low) / 2;
                if (font.getStringWidth(text.substring(middle, caretPosition) + reserved) > available) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            offset = low;
        }
        low = offset;
        high = text.length();
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (font.getStringWidth(text.substring(offset, middle) + reserved) <= available) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        int end = low;
        drawRect(left - 1, top - 1, left + fieldWidth + 1, top + fieldHeight + 1, 0xFFA0A0A0);
        drawRect(left, top, left + fieldWidth, top + fieldHeight, 0xFF000000);
        int y = top + (fieldHeight - 8) / 2;
        int first = Math.max(offset, Math.min(end, editor.selectionStart()));
        int last = Math.max(first, Math.min(end, editor.selectionEnd()));
        if (isFocused && first != last) {
            drawRect(left + 4 + font.getStringWidth(text.substring(offset, first)), y - 1,
                    left + 4 + font.getStringWidth(text.substring(offset, last)), y + 9, 0x8855AAFF);
        }
        drawString(font, text.substring(offset, end), left + 4, y, isEnabled ? 14737632 : 7368816);
        if (isEnabled && isFocused && cursorTicks / 6 % 2 == 0) {
            int caretX = left + 4 + font.getStringWidth(text.substring(offset, caretPosition));
            drawString(font, "_", caretX, y, 14737632);
        }
    }
}
