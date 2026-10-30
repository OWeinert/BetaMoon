package betamoon.gui.drawing;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.presentation.GuiPresentationNode;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Map;

/**
 * A raw leaf reuses the prepared hit/layout adapter, with an exclusive explicit
 * raw painter.
 */
public final class GuiRawView extends GuiPaintView {
    private final GuiRawDrawing drawing;
    private final Supplier<Map<String, ?>> publishedData;

    public GuiRawView(Source hitSource, InputHandler input, Consumer<Throwable> failures, boolean focusable,
            GuiRawDrawing drawing) {
        this(hitSource, input, failures, focusable, drawing, null);
    }

    public GuiRawView(Source hitSource, InputHandler input, Consumer<Throwable> failures, boolean focusable,
            GuiRawDrawing drawing, Supplier<Map<String, ?>> publishedData) {
        super(new Source() {
            public GuiPaintProgram prepare(int width, int height, Map<String, Object> properties) {
                GuiPaintProgram program = hitSource.prepare(width, height, properties);
                if (program.commands() != 0) {
                    throw new IllegalArgumentException(
                            "A leaf must choose prepared painting or raw drawing exclusively");
                }
                return program;
            }

            public void accept(GuiPaintProgram program) {
                hitSource.accept(program);
            }
        }, input, failures, focusable);
        this.drawing = drawing;
        this.publishedData = publishedData;
    }

    @Override
    protected void updateElement(GuiContext context) {
        drawing.update();
        if (publishedData != null && drawing.available()) {
            drawing.publish(publishedData.get());
        }
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        if (drawing.failed()) {
            setEnabled(false);
        }
    }

    @Override
    protected void prepareVisualsElement(GuiContext context) {
        if (!drawing.failed()) {
            super.prepareVisualsElement(context);
        }
    }

    @Override
    protected boolean retainFrameProperties() {
        return true;
    }

    @Override
    protected void paintFrame(GuiContext context, GuiPaintProgram.Frame frame) {
        if (!drawing.draw(context, this, frame.properties, frame.width, frame.height)) {
            setEnabled(false);
        }
    }

    @Override
    public void close() {
        drawing.close();
        super.close();
    }
}
