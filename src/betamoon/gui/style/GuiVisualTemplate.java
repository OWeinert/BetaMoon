package betamoon.gui.style;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.widget.GuiStyledView;
import java.util.Set;

/**
 * Replaceable Java presentation; the retained view continues to own behavior,
 * focus, layout and hit routing.
 */
public interface GuiVisualTemplate {
    Set<String> parts();

    default void prepare(GuiContext context, GuiStyledView view) {
    }

    void paint(GuiContext context, GuiStyledView view, GuiControlStyle style);
}
