package betamoon.gui.container;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDocumentMount;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.Arrays;
import java.util.Collections;

/**
 * One scene projection for legacy input and a panel-local retained document.
 */
public final class GuiContainerSurface extends GuiContainer {
    private GuiElement legacy;
    private GuiDocumentMount document;
    private Rect panel = Rect.EMPTY;

    public void legacy(GuiElement element) {
        legacy = element;
        reconcile();
    }

    public void document(GuiDocumentMount mount) {
        document = mount;
        reconcile();
    }

    public void panel(Rect bounds) {
        panel = bounds;
        requestLayout();
    }

    private void reconcile() {
        reconcileChildren(legacy == null
                ? Collections.emptyList()
                : document == null ? Collections.singletonList(legacy) : Arrays.asList(legacy, document));
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        if (legacy != null) {
            legacy.arrange(context, getBounds());
        }
        if (document != null) {
            document.arrange(context, panel);
        }
    }
}
