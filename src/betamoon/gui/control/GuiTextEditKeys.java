package betamoon.gui.control;

import java.util.function.IntPredicate;
import org.lwjgl.input.Keyboard;

/**
 * Shared plain single-line editing; hosts retain their own Enter/Escape and
 * commit policies.
 */
public final class GuiTextEditKeys {
    public enum Result {
        UNHANDLED, NAVIGATION, EDIT
    }

    public static Result apply(GuiTextEditorModel editor, char character, int key, boolean shift, boolean control,
            IntPredicate allowed) {
        if (control && key == Keyboard.KEY_A) {
            editor.selectAll();
        } else if (control && (key == Keyboard.KEY_C || key == Keyboard.KEY_X)) {
            GuiClipboard.write(editor.text().substring(editor.selectionStart(), editor.selectionEnd()));
            if (key == Keyboard.KEY_X) {
                editor.insert("");
                return Result.EDIT;
            }
        } else if (control && key == Keyboard.KEY_V || character == 22) {
            insert(editor, GuiClipboard.read(editor.maximumLength()), allowed);
            return Result.EDIT;
        } else if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) {
            editor.move(editor.caret() + (key == Keyboard.KEY_LEFT ? -1 : 1), shift);
        } else if (key == Keyboard.KEY_HOME || key == Keyboard.KEY_END) {
            editor.move(key == Keyboard.KEY_HOME ? 0 : editor.text().length(), shift);
        } else if (key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE) {
            editor.erase(key == Keyboard.KEY_BACK);
            return Result.EDIT;
        } else if (character >= 32 && character != 127 && allowed.test(character)) {
            editor.insert(String.valueOf(character));
            return Result.EDIT;
        } else {
            return Result.UNHANDLED;
        }
        return Result.NAVIGATION;
    }

    public static void insert(GuiTextEditorModel editor, String text, IntPredicate allowed) {
        StringBuilder filtered = new StringBuilder(Math.min(editor.maximumLength(), text.length()));
        for (int index = 0; index < text.length() && filtered.length() < editor.maximumLength(); index++) {
            char character = text.charAt(index);
            if (character >= 32 && character != 127 && allowed.test(character)) {
                filtered.append(character);
            }
        }
        editor.insert(filtered.toString());
    }

    private GuiTextEditKeys() {
    }
}
