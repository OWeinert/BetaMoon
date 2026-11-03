package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.presentation.GuiPresentationTree;

/**
 * Stable scene portal preserves layer order while a document revision changes.
 */
final class GuiLayerDocument extends GuiContainer implements GuiViewportLayout, GuiLayer.Document {
    private GuiPresentationTree document;
    GuiLayerDocument(GuiPresentationTree document) {
        replace(document);
    }

    public GuiPresentationTree document() {
        return document;
    }

    public void replace(GuiPresentationTree replacement) {
        clear();
        document = replacement;
        if (replacement != null) {
            add(replacement.root());
        }
    }

    public Rect viewportBounds(GuiContext context, Rect viewport) {
        GuiElement root = document.root();
        return root instanceof GuiViewportLayout
                ? ((GuiViewportLayout) root).viewportBounds(context, viewport)
                : viewport;
    }
}
