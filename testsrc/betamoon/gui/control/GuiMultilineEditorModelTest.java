package betamoon.gui.control;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Multiline editing state stays independent of viewport rendering and Lua
 * callbacks.
 */
public final class GuiMultilineEditorModelTest {
    public static void main(String[] args) {
        linesAndNormalization();
        preferredColumnAndSelection();
        controlledDraftsAndRollback();
        snapshotCompatibility();
        largeDocument();
        System.out.println("Multiline editor models passed: LF normalization, hard-line indexing, preferred columns, "
                + "selection, draft/acknowledgement, exact rollback and versioned snapshot compatibility.");
    }

    private static void linesAndNormalization() {
        GuiTextEditorModel editor = new GuiTextEditorModel("a\r\nb\rc", 5, true);
        require(editor.text().equals("a\nb\nc") && editor.lines().count() == 3,
                "Initial normalization did not precede length validation");
        GuiTextLines lines = editor.lines();
        require(lines.start(1) == 2 && lines.end(1) == 3 && lines.lineAt(1) == 0 && lines.lineAt(2) == 1,
                "LF boundaries assigned the wrong caret line");
        require(new GuiTextLines("").count() == 1 && new GuiTextLines("a\n").end(1) == 2,
                "Empty/trailing lines were lost");
        editor.selectAll();
        editor.insert("x\r\ny\rz!");
        require(editor.text().equals("x\ny\nz") && editor.caret() == 5, "Normalized paste exceeded its bound");
        editor.observe("1\r\n2");
        require(editor.conflict() && editor.committed().equals("1\n2"), "External text was not normalized");
        editor.cancel();
        require(editor.text().equals("1\n2") && editor.lines().count() == 2, "Cancel retained a stale line index");
        GuiTextEditorModel crossing = new GuiTextEditorModel("ab\ncd\n", 20, true);
        crossing.move(1, false);
        crossing.move(4, true);
        crossing.insert("X\r\n");
        require(crossing.text().equals("aX\nd\n") && crossing.caret() == 3 && crossing.lines().count() == 3,
                "Replacement across an LF boundary lost trailing content or caret position");
        String before = editor.text();
        expect(() -> editor.observe("too\r\nlong"));
        require(editor.text().equals(before) && editor.committed().equals(before),
                "An invalid external publication partially changed editor state");
        expect(() -> new GuiTextLines("a\rb"));
        expect(() -> lines.lineAt(6));
        expect(() -> lines.position(0, -1));
    }

    private static void preferredColumnAndSelection() {
        GuiTextEditorModel editor = new GuiTextEditorModel("abcdef\nx\nabcdef", 40, true);
        editor.move(5, false);
        editor.moveVertical(1, false);
        require(editor.caret() == 8, "Down did not clamp to the short line");
        editor.moveVertical(1, true);
        require(editor.caret() == 14 && editor.selectionStart() == 8 && editor.selectionEnd() == 14,
                "Preferred column or cross-line selection was lost");
        editor.moveVertical(-1, false);
        editor.moveLineBoundary(false, false);
        require(editor.caret() == 7, "Line Home used the document boundary");
        editor.moveVertical(1, false);
        require(editor.caret() == 9, "Line Home did not reset the preferred column");
        editor.moveLineBoundary(true, true);
        require(editor.selectionStart() == 9 && editor.selectionEnd() == 15, "Line End lost its selection anchor");
        editor.erase(true);
        require(editor.text().equals("abcdef\nx\n") && editor.lines().count() == 3,
                "Selection replacement retained stale hard-line boundaries");
    }

    private static void controlledDraftsAndRollback() {
        GuiTextEditorModel editor = new GuiTextEditorModel("12345\nx\n12345", 60, true);
        editor.move(4, false);
        editor.moveVertical(1, false);
        GuiTextEditorModel.Snapshot previous = editor.snapshot();
        GuiTextEditorModel copy = editor.copy();
        copy.moveVertical(1, false);
        require(copy.caret() == 12, "Copy lost preferred-column navigation state");
        editor.insert("!\r\n");
        editor.restoreSnapshot(previous);
        editor.moveVertical(1, false);
        require(editor.caret() == 12, "Exact rollback lost preferred-column state");
        editor.insert(" edit");
        editor.commit(true, true);
        require(editor.pending(), "Controlled multiline commit did not wait for acknowledgement");
        String draft = editor.text();
        editor.observe(draft.replace("\n", "\r\n"));
        require(!editor.pending() && editor.committed().equals(draft),
                "Normalized acknowledgement retained pending state");
        editor.insert(" local");
        GuiTextEditorModel replacement = new GuiTextEditorModel("changed\nsource", 60, true);
        require(replacement.restore(editor.snapshot(), true) && replacement.conflict()
                && replacement.text().equals(editor.text()), "External refresh discarded the multiline draft");
    }

    private static void snapshotCompatibility() {
        GuiTextEditorModel single = new GuiTextEditorModel("same", 40);
        Map<String, Object> legacy = single.snapshot().values();
        require(legacy.size() == 9 && legacy.get("schema").equals(1), "Single-line snapshot schema changed");
        GuiTextEditorModel multi = new GuiTextEditorModel("abcd\nx\nabcde", 40, true);
        multi.move(4, false);
        multi.moveVertical(1, false);
        Map<String, Object> state = multi.snapshot().values();
        require(state.size() == 11 && state.get("schema").equals(2), "Multiline snapshot was not versioned");
        GuiTextEditorModel restored = new GuiTextEditorModel("", 40, true);
        restored.restoreSnapshot(GuiTextEditorModel.Snapshot.read(state));
        restored.moveVertical(1, false);
        require(restored.caret() == 11, "Detached restoration lost the preferred column");
        require(!single.restore(multi.snapshot(), false) && !multi.restore(single.snapshot(), false),
                "Incompatible editor modes restored across each other");
        expect(() -> single.restoreSnapshot(multi.snapshot()));
        Map<String, Object> malformed = new LinkedHashMap<>(state);
        malformed.put("preferredColumn", 1.5);
        expect(() -> GuiTextEditorModel.Snapshot.read(malformed));
        malformed.put("preferredColumn", 4);
        malformed.put("draft", "not\rcanonical");
        expect(() -> GuiTextEditorModel.Snapshot.read(malformed));
        require(GuiTextEditorModel.Snapshot.read(legacy).values().equals(legacy), "Legacy snapshot round trip changed");
    }

    private static void largeDocument() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 32768; i++) {
            text.append("x\n");
        }
        GuiTextEditorModel editor = new GuiTextEditorModel(text.toString(), 65536, true);
        require(editor.lines().count() == 32769, "A bounded large document lost line boundaries");
        editor.moveVertical(Integer.MIN_VALUE, false);
        require(editor.caret() == 0, "Large negative movement overflowed the first line");
        editor.moveVertical(Integer.MAX_VALUE, false);
        require(editor.caret() == 65536, "Large positive movement overflowed the final empty line");
    }

    private static void expect(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Malformed editor input was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
