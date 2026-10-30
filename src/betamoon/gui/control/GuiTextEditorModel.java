package betamoon.gui.control;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
        restore(previous.snapshot(), true);
    }

    public Snapshot snapshot() {
        return new Snapshot(maximumLength, committed, draft, caret, anchor, conflict, invalid, pending);
    }

    /**
     * Compatible local edits survive while a controlled editor observes its current
     * external value.
     */
    public boolean restore(Snapshot previous, boolean externallyControlled) {
        if (maximumLength != previous.maximumLength) {
            return false;
        }
        if (!externallyControlled) {
            committed = previous.committed;
            draft = previous.draft;
            caret = previous.caret;
            anchor = previous.anchor;
            conflict = previous.conflict;
            invalid = previous.invalid;
            pending = false;
            return true;
        }
        boolean dirty = !previous.draft.equals(previous.committed);
        if (!dirty || committed.equals(previous.draft)) {
            draft = committed;
            caret = Math.min(previous.caret, draft.length());
            anchor = committed.equals(previous.draft) ? previous.anchor : -1;
            conflict = false;
            invalid = false;
            pending = false;
        } else {
            draft = previous.draft;
            caret = previous.caret;
            anchor = previous.anchor;
            invalid = previous.invalid;
            conflict = previous.conflict || !committed.equals(previous.committed);
            pending = previous.pending && !conflict;
        }
        return true;
    }

    /**
     * Immutable detached state; it contains no live control, callback or clipboard
     * access.
     */
    public static final class Snapshot {
        public final int maximumLength;
        public final String committed;
        public final String draft;
        public final int caret;
        public final int anchor;
        public final boolean conflict;
        public final boolean invalid;
        public final boolean pending;

        private Snapshot(int maximumLength, String committed, String draft, int caret, int anchor, boolean conflict,
                boolean invalid, boolean pending) {
            this.maximumLength = maximumLength;
            this.committed = committed;
            this.draft = draft;
            this.caret = caret;
            this.anchor = anchor;
            this.conflict = conflict;
            this.invalid = invalid;
            this.pending = pending;
        }

        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("schema", 1);
            values.put("maximumLength", maximumLength);
            values.put("committed", committed);
            values.put("draft", draft);
            values.put("caret", caret);
            values.put("anchor", anchor);
            values.put("conflict", conflict);
            values.put("invalid", invalid);
            values.put("pending", pending);
            return Collections.unmodifiableMap(values);
        }

        public static Snapshot read(Map<String, Object> values) {
            if (values.size() != 9 || integer(values, "schema", 1, 1) != 1) {
                throw new IllegalArgumentException("Invalid editor snapshot schema or fields");
            }
            int maximum = integer(values, "maximumLength", 0, Integer.MAX_VALUE);
            String committed = text(values, "committed", maximum);
            String draft = text(values, "draft", maximum);
            return new Snapshot(maximum, committed, draft, integer(values, "caret", 0, draft.length()),
                    integer(values, "anchor", -1, draft.length()), flag(values, "conflict"), flag(values, "invalid"),
                    flag(values, "pending"));
        }

        private static int integer(Map<String, Object> values, String name, int minimum, int maximum) {
            Object value = values.get(name);
            double number = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
            if (!Double.isFinite(number) || number != Math.rint(number) || number < minimum || number > maximum) {
                throw new IllegalArgumentException("Invalid editor snapshot " + name);
            }
            return (int) number;
        }

        private static String text(Map<String, Object> values, String name, int maximum) {
            Object value = values.get(name);
            if (!(value instanceof String) || ((String) value).length() > maximum) {
                throw new IllegalArgumentException("Invalid editor snapshot " + name);
            }
            return (String) value;
        }

        private static boolean flag(Map<String, Object> values, String name) {
            Object value = values.get(name);
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Invalid editor snapshot " + name);
            }
            return (Boolean) value;
        }
    }
}
