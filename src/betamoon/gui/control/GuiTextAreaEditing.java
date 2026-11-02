package betamoon.gui.control;

import betamoon.gui.framework.GuiInputEvent;
import java.util.function.Supplier;
import org.lwjgl.input.Keyboard;

/**
 * Plain textarea keyboard proposals; clipboard writes are returned for admitted
 * execution.
 */
public final class GuiTextAreaEditing {
    public final GuiTextEditorModel editor;
    public boolean upstream;
    public int preferredX;
    public boolean handled = true;
    public boolean cancel;
    public String clipboardWrite;

    private GuiTextAreaEditing(GuiTextEditorModel editor, boolean upstream, int preferredX) {
        this.editor = editor.copy();
        this.upstream = upstream;
        this.preferredX = preferredX;
    }

    public static GuiTextAreaEditing key(GuiTextEditorModel editor, GuiTextAreaLayout layout, boolean upstream,
            int preferredX, GuiInputEvent event, int pageRows, Supplier<String> clipboard) {
        GuiTextAreaEditing result = new GuiTextAreaEditing(editor, upstream, preferredX);
        result.apply(layout, event, pageRows, clipboard);
        return result;
    }

    private void apply(GuiTextAreaLayout layout, GuiInputEvent event, int pageRows, Supplier<String> clipboard) {
        int key = event.getKeyCode();
        boolean shift = event.isShiftDown();
        if (event.isControlDown()) {
            switch (key) {
                case Keyboard.KEY_A:
                    editor.selectAll();
                    break;
                case Keyboard.KEY_C:
                    if (editor.selectionStart() == editor.selectionEnd()) {
                        return;
                    }
                    clipboardWrite = editor.text().substring(editor.selectionStart(), editor.selectionEnd());
                    return;
                case Keyboard.KEY_X:
                    if (editor.selectionStart() == editor.selectionEnd()) {
                        return;
                    }
                    clipboardWrite = editor.text().substring(editor.selectionStart(), editor.selectionEnd());
                    editor.insert("");
                    break;
                case Keyboard.KEY_V:
                    String pasted = clipboard.get();
                    if (pasted.isEmpty()) {
                        return;
                    }
                    editor.insert(pasted);
                    break;
                case Keyboard.KEY_HOME:
                case Keyboard.KEY_END:
                    editor.move(key == Keyboard.KEY_HOME ? 0 : editor.text().length(), shift);
                    break;
                default:
                    handled = false;
                    return;
            }
            upstream = false;
            preferredX = -1;
            return;
        }
        int row = layout.rowAt(editor.caret(), upstream);
        switch (key) {
            case Keyboard.KEY_LEFT:
            case Keyboard.KEY_RIGHT:
                int position = !shift && editor.selectionStart() != editor.selectionEnd()
                        ? key == Keyboard.KEY_LEFT ? editor.selectionStart() : editor.selectionEnd()
                        : key == Keyboard.KEY_LEFT ? layout.previous(editor.caret()) : layout.next(editor.caret());
                editor.move(position, shift);
                upstream = false;
                break;
            case Keyboard.KEY_UP:
            case Keyboard.KEY_DOWN:
            case Keyboard.KEY_PRIOR:
            case Keyboard.KEY_NEXT:
                int delta = key == Keyboard.KEY_UP
                        ? -1
                        : key == Keyboard.KEY_DOWN
                                ? 1
                                : key == Keyboard.KEY_PRIOR ? -Math.max(1, pageRows) : Math.max(1, pageRows);
                preferredX = preferredX < 0 ? layout.xAt(row, editor.caret()) : preferredX;
                int target = Math.max(0, Math.min(layout.rows.size() - 1, row + delta));
                editor.move(layout.positionAt(target, preferredX), shift);
                upstream = editor.caret() == layout.rows.get(target).end;
                return;
            case Keyboard.KEY_HOME:
            case Keyboard.KEY_END:
                GuiTextAreaLayout.Row line = layout.rows.get(row);
                editor.move(key == Keyboard.KEY_HOME ? line.start : line.end, shift);
                upstream = key == Keyboard.KEY_END;
                break;
            case Keyboard.KEY_BACK:
            case Keyboard.KEY_DELETE:
                if (editor.selectionStart() == editor.selectionEnd()) {
                    editor.move(
                            key == Keyboard.KEY_BACK ? layout.previous(editor.caret()) : layout.next(editor.caret()),
                            true);
                }
                editor.insert("");
                upstream = false;
                break;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                editor.insert("\n");
                upstream = false;
                break;
            case Keyboard.KEY_ESCAPE:
                editor.cancel();
                upstream = false;
                cancel = true;
                break;
            default:
                char character = event.getTypedChar();
                if (character < 32 || character == 127) {
                    handled = false;
                    return;
                }
                editor.insert(String.valueOf(character));
                upstream = false;
                break;
        }
        preferredX = -1;
    }
}
