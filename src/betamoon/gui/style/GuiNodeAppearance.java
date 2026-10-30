package betamoon.gui.style;

/**
 * Node-local appearance references; composition computes inherited themes
 * separately from control state.
 */
public final class GuiNodeAppearance {
    public static final GuiNodeAppearance DEFAULT = new GuiNodeAppearance(null, null, GuiStyleValues.EMPTY);
    public final String theme;
    public final String template;
    public final GuiStyleValues style;
    public GuiNodeAppearance(String theme, String template, GuiStyleValues style) {
        if (theme != null && (theme.isEmpty() || theme.length() > 255)
                || template != null && (template.isEmpty() || template.length() > 128) || style == null) {
            throw new IllegalArgumentException("Invalid node appearance references");
        }
        this.theme = theme;
        this.template = template;
        this.style = style;
    }
}
