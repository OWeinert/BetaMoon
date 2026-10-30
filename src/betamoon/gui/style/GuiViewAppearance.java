package betamoon.gui.style;

import betamoon.gui.presentation.GuiNodeDefinition;

/**
 * Resolved composition metadata for a view factory; no alternative view tree is
 * retained.
 */
public final class GuiViewAppearance {
    public final String owner;
    public final GuiNodeDefinition node;
    public final GuiNodeAppearance appearance;
    public final String theme;
    public GuiViewAppearance(String owner, GuiNodeDefinition node, GuiNodeAppearance appearance, String theme) {
        this.owner = owner;
        this.node = node;
        this.appearance = appearance;
        this.theme = theme;
    }
}
