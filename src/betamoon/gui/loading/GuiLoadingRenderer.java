package betamoon.gui.loading;

import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.src.LoadingScreenRenderer;
import net.minecraft.src.ScaledResolution;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * Temporarily adapts the standard blocking renderer; native abort behavior
 * remains delegated.
 */
public final class GuiLoadingRenderer extends LoadingScreenRenderer implements AutoCloseable {
    private final Minecraft client;
    private final LoadingScreenRenderer previous;
    private final GuiLoadingPainter painter;
    private final GuiLoadingStatus status;
    private final LongSupplier clock;
    private final Runnable present;
    private long lastFrame;
    private boolean closed;

    public GuiLoadingRenderer(Minecraft client, GuiLoadingPainter painter, GuiLoadingStatus status) {
        this(client, painter, status, System::currentTimeMillis, Display::update);
    }

    GuiLoadingRenderer(Minecraft client, GuiLoadingPainter painter, GuiLoadingStatus status, LongSupplier clock,
            Runnable present) {
        super(client);
        if (client.loadingScreen == null || client.loadingScreen.getClass() != LoadingScreenRenderer.class) {
            throw new IllegalArgumentException("Only the standard native loading renderer can be adapted");
        }
        this.client = client;
        previous = client.loadingScreen;
        this.painter = painter;
        this.status = status;
        this.clock = clock;
        this.present = present;
        lastFrame = clock.getAsLong();
        client.loadingScreen = this;
    }

    @Override
    public void printText(String title) {
        previous.printText(title);
        if (client.running) {
            status.title = title;
        }
    }

    @Override
    public void func_594_b(String ignored) {
        previous.func_594_b(ignored);
    }

    @Override
    public void func_597_c(String title) {
        previous.func_597_c(title);
        if (client.running) {
            status.title = title;
        }
    }

    @Override
    public void displayLoadingString(String message) {
        if (closed || !client.running) {
            previous.displayLoadingString(message);
            return;
        }
        status.message = message;
        lastFrame = 0;
        setLoadingProgress(-1);
        lastFrame = 0;
    }

    @Override
    public void setLoadingProgress(int progress) {
        if (closed || !client.running) {
            previous.setLoadingProgress(progress);
            return;
        }
        status.progress(progress);
        long now = clock.getAsLong();
        if (now - lastFrame < 20) {
            return;
        }
        lastFrame = now;
        ScaledResolution resolution = new ScaledResolution(client.gameSettings, client.displayWidth,
                client.displayHeight);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        painter.paint(resolution.getScaledWidth(), resolution.getScaledHeight());
        present.run();
        Thread.yield();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (client.loadingScreen == this) {
            client.loadingScreen = previous;
        }
    }
}
