package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.graphics.GraphicsStateGuard;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Clipped native-host region for an ordinary compiled document; owns no
 * separate input tree.
 */
public final class GuiDocumentMount extends GuiContainer {
    private GuiPresentationTree document;
    private boolean failed;
    private BooleanSupplier paintWhen = () -> true;
    private Consumer<Throwable> failure = error -> {
        throw new IllegalStateException("Attached document paint failed", error);
    };

    public void onFailure(Consumer<Throwable> failure) {
        this.failure = failure;
    }

    public boolean failed() {
        return failed;
    }

    /**
     * Paint-time invalidation only; diagnostics and disposal remain at update
     * boundaries.
     */
    public void invalidatePaint() {
        failed = true;
    }

    public void paintWhen(BooleanSupplier condition) {
        paintWhen = condition;
    }

    public void document(GuiPresentationTree document) {
        clear();
        this.document = document;
        failed = false;
        if (document != null) {
            add(document.root());
        }
    }

    public GuiPresentationTree document() {
        return document;
    }

    @Override
    protected boolean hitTestSelf() {
        return false;
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        return document == null ? Size.ZERO : document.root().measure(context, constraints);
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        if (document != null) {
            GuiElement root = document.root();
            root.measure(context, Constraints.upTo(getWidth(), getHeight()));
            root.arrange(context,
                    root instanceof GuiViewportLayout
                            ? ((GuiViewportLayout) root).viewportBounds(context, getBounds())
                            : getBounds());
        }
    }

    @Override
    protected Rect childInputClip() {
        return getBounds();
    }

    @Override
    protected void renderElement(GuiContext context) {
        if (failed || !paintWhen.getAsBoolean()) {
            return;
        }
        GuiRenderer renderer = context.getRenderer();
        int clips = renderer.clipDepth();
        try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
            renderer.pushClip(getBounds());
            try {
                super.renderElement(context);
            } finally {
                renderer.restoreClipDepth(clips);
            }
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable error) {
            failed = true;
            failure.accept(error);
        }
    }
}
