package betamoon.gui.style;

/**
 * Shared headless ARGB normalization for authored UI and prepared presentation
 * metadata.
 */
public final class GuiArgbColor {
    public static int read(Object value) {
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Double.isFinite(number) && number == Math.rint(number) && number >= Integer.MIN_VALUE
                    && number <= 0xFFFFFFFFL) {
                return (int) (long) number;
            }
        } else if (value instanceof String && ((String) value).matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            String hex = ((String) value).substring(1);
            return (int) Long.parseLong(hex, 16) | (hex.length() == 6 ? 0xFF000000 : 0);
        }
        throw new IllegalArgumentException("Expected an ARGB integer, #RRGGBB or #AARRGGBB color");
    }

    private GuiArgbColor() {
    }
}
