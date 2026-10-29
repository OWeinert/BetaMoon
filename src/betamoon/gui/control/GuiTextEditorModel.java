package betamoon.gui.control;

/**
 * Editor state survives layout, resizing and compatible view refresh. No
 * gameplay or rendering dependencies.
 */
public final class GuiTextEditorModel {
    private String committed;
    private String draft;
    private int caret;
    private int anchor = -1;
    private final int maximumLength;
    private boolean conflict;
    private boolean invalid;
    private boolean pending;

    public GuiTextEditorModel(String value, int maximumLength) {
        if (value == null || maximumLength < 0 || value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid initial editor value or length limit");
        }
        committed = value;
        draft = value;
        caret = value.length();
        this.maximumLength = maximumLength;
    }

    public String text() {
        return draft;
    }

    public String committed() {
        return committed;
    }

    public int caret() {
        return caret;
    }

    public int selectionStart() {
        return anchor < 0 ? caret : Math.min(anchor, caret);
    }

    public int selectionEnd() {
        return anchor < 0 ? caret : Math.max(anchor, caret);
    }

    public boolean conflict() {
        return conflict;
    }

    public boolean invalid() {
        return invalid;
    }

    public int maximumLength() {
        return maximumLength;
    }

    public boolean pending() {
        return pending;
    }

    public void move(int position, boolean selecting) {
        if (selecting && anchor < 0) {
            anchor = caret;
        } else if (!selecting) {
            anchor = -1;
        }
        caret = Math.max(0, Math.min(draft.length(), position));
    }

    public void selectAll() {
        anchor = 0;
        caret = draft.length();
    }

    public void insert(String text) {
        GuiTextEditOperations.Result result = GuiTextEditOperations.replace(draft, selectionStart(), selectionEnd(),
                text, maximumLength);
        draft = result.text;
        caret = result.caret;
        anchor = -1;
        invalid = false;
        pending = false;
    }

    public void erase(boolean backwards) {
        if (selectionStart() != selectionEnd()) {
            insert("");
        } else if (backwards && caret > 0) {
            anchor = caret - 1;
            insert("");
        } else if (!backwards && caret < draft.length()) {
            anchor = caret + 1;
            insert("");
        }
    }

    public void observe(String value) {
        if (value == null || value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid external editor value");
        }
        if (value.equals(draft)) {
            committed = value;
            conflict = false;
            invalid = false;
            pending = false;
            return;
        }
        if (draft.equals(committed)) {
            draft = value;
            caret = Math.min(caret, draft.length());
            anchor = -1;
            conflict = false;
            invalid = false;
        } else if (!value.equals(committed)) {
            conflict = true;
            pending = false;
        }
        committed = value;
    }

    public void commit(boolean accepted) {
        commit(accepted, false);
    }

    public void commit(boolean accepted, boolean externallyControlled) {
        invalid = !accepted;
        if (accepted) {
            if (!externallyControlled) {
                committed = draft;
            }
            pending = externallyControlled;
            conflict = false;
        }
    }

    public void cancel() {
        draft = committed;
        caret = Math.min(caret, draft.length());
        anchor = -1;
        conflict = false;
        invalid = false;
        pending = false;
    }

    public void retainFrom(GuiTextEditorModel previous) {
        if (maximumLength == previous.maximumLength) {
            boolean dirty = !previous.draft.equals(previous.committed);
            if (!dirty || committed.equals(previous.draft)) {
                caret = Math.min(previous.caret, draft.length());
                return;
            }
            draft = previous.draft;
            caret = previous.caret;
            anchor = previous.anchor;
            invalid = previous.invalid;
            conflict = previous.conflict || !committed.equals(previous.committed);
        }
    }
}
