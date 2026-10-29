package betamoon.gui.control;

/**
 * Bounded selection replacement shared by legacy container and retained
 * editors. Offsets use UTF-16 units.
 */
public final class GuiTextEditOperations {
    private GuiTextEditOperations() {
    }

    public static Result replace(String value, int start, int end, String insertion, int maximumLength) {
        if (value == null || insertion == null || maximumLength < 0 || start < 0 || end < start
                || end > value.length()) {
            throw new IllegalArgumentException("Invalid text selection or length limit");
        }
        int capacity = (int) Math.min(maximumLength,
                Math.min(Integer.MAX_VALUE, (long) value.length() - (end - start) + insertion.length()));
        StringBuilder result = new StringBuilder(capacity);
        append(result, value, 0, start, maximumLength);
        append(result, insertion, 0, insertion.length(), maximumLength);
        append(result, value, end, value.length(), maximumLength);
        int caret = (int) Math.min(result.length(), (long) start + insertion.length());
        return new Result(result.toString(), caret);
    }

    private static void append(StringBuilder output, String value, int start, int end, int maximumLength) {
        int count = Math.min(end - start, maximumLength - output.length());
        output.append(value, start, start + count);
    }

    public static final class Result {
        public final String text;
        public final int caret;

        private Result(String text, int caret) {
            this.text = text;
            this.caret = caret;
        }
    }
}
