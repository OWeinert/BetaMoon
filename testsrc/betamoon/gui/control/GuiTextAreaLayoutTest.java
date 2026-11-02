package betamoon.gui.control;

import betamoon.gui.framework.GuiInputEvent;
import org.lwjgl.input.Keyboard;

/** Layout and keyboard proposals use no desktop clipboard writes. */
public final class GuiTextAreaLayoutTest {
    public static void main(String[] args) {
        GuiTextAreaLayout layout = new GuiTextAreaLayout("WW ii\nend\n", 20, true, 9, GuiTextAreaLayoutTest::width);
        require(layout.rows.size() == 4 && layout.rows.get(0).end == 3 && layout.rows.get(1).start == 3,
                "Word wrapping lost whitespace or line boundaries");
        require(layout.rowAt(3, false) == 1 && layout.rowAt(3, true) == 0, "Soft boundary lost caret affinity");
        require(layout.positionAt(0, 3) == 0 && layout.positionAt(0, 5) == 1 && layout.xAt(1, 5) == 4,
                "Proportional hit positions disagreed with widths");
        require(layout.rows.get(2).newline && layout.rows.get(3).start == layout.text.length(),
                "LF or trailing empty row was lost");
        GuiTextAreaLayout tabs = new GuiTextAreaLayout("a\tb", 100, false, 9, GuiTextAreaLayoutTest::width);
        require(tabs.width == 25 && tabs.display(0, 3).equals("a    b"), "Tab paint/measurement disagreed");
        GuiTextAreaLayout unicode = new GuiTextAreaLayout("A\uD83D\uDE00B", 10, true, 9,
                glyph -> glyph.length() == 2 ? 8 : 4);
        require(unicode.rows.size() == 3 && unicode.rows.get(1).start == 1 && unicode.rows.get(1).end == 3
                && unicode.positionAt(1, 5) == 3 && unicode.previous(3) == 1, "Layout split a surrogate pair");
        int[] calls = {0};
        String text = new String(new char[65536]).replace('\0', 'x');
        GuiTextAreaLayout large = new GuiTextAreaLayout(text, 1, true, 9, glyph -> {
            calls[0]++;
            return 4;
        });
        require(calls[0] == 65536 && large.rows.size() == 65536, "Large wrapping lost its measurement bound");
        keyboard();
        System.out.println("Textarea layout passed: proportional wrap/hit geometry, LF/tab/Unicode, bounded "
                + "measurement and ordinary keyboard/clipboard proposals.");
    }

    private static int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) == 'i' ? 2 : text.charAt(i) == 'W' ? 8 : 4;
        }
        return width;
    }

    private static void keyboard() {
        GuiTextEditorModel editor = new GuiTextEditorModel("base", 40, true);
        GuiTextAreaLayout layout = new GuiTextAreaLayout(editor.text(), 100, true, 9, GuiTextAreaLayoutTest::width);
        GuiTextAreaEditing newline = GuiTextAreaEditing.key(editor, layout, false, -1,
                GuiInputEvent.keyTyped('\r', Keyboard.KEY_RETURN, false, false), 4, () -> "");
        require(newline.editor.text().equals("base\n") && !newline.editor.pending(), "Enter submitted instead of LF");
        require(!GuiTextAreaEditing.key(editor, layout, false, -1,
                GuiInputEvent.keyTyped('\r', Keyboard.KEY_RETURN, false, true), 4, () -> "").handled,
                "Textarea claimed an unconfigured submission shortcut");
        editor.selectAll();
        GuiTextAreaEditing copied = GuiTextAreaEditing.key(editor, layout, true, 12,
                GuiInputEvent.keyTyped('\0', Keyboard.KEY_C, false, true), 4, () -> "");
        require(copied.clipboardWrite.equals("base") && copied.editor.text().equals("base") && copied.upstream
                && copied.preferredX == 12, "Copy changed the draft or caret affinity");
        GuiTextAreaEditing cut = GuiTextAreaEditing.key(editor, layout, false, -1,
                GuiInputEvent.keyTyped('\0', Keyboard.KEY_X, false, true), 4, () -> "");
        require(cut.editor.text().isEmpty() && cut.clipboardWrite.equals("base"),
                "Cut did not stage its clipboard write");
        GuiTextAreaEditing pasted = GuiTextAreaEditing.key(editor, layout, false, -1,
                GuiInputEvent.keyTyped('\0', Keyboard.KEY_V, false, true), 4,
                () -> GuiClipboard.filter("a\r\nb\tc\u0001", 40, true));
        require(pasted.editor.text().equals("a\nb\tc"), "Paste lost normalized LF/tab filtering");
        GuiTextAreaEditing empty = GuiTextAreaEditing.key(editor, layout, true, 12,
                GuiInputEvent.keyTyped('\0', Keyboard.KEY_V, false, true), 4, () -> "");
        require(empty.editor.text().equals("base") && empty.editor.selectionEnd() == 4 && empty.upstream,
                "An unavailable/empty clipboard deleted the selection");
        editor.move(2, false);
        GuiTextAreaEditing noSelection = GuiTextAreaEditing.key(editor, layout, false, -1,
                GuiInputEvent.keyTyped('\0', Keyboard.KEY_X, false, true), 4, () -> "");
        require(noSelection.clipboardWrite == null && noSelection.editor.text().equals("base"),
                "Cut without a selection cleared the clipboard");
        require(GuiClipboard.filter("a\r\nb\tc", 40, false).equals("abc"), "Single-line clipboard behavior changed");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
