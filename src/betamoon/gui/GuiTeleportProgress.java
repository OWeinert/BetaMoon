package betamoon.gui;

import betamoon.gui.loading.GuiLoadingAppearance;
import betamoon.gui.loading.GuiLoadingPainter;
import betamoon.gui.loading.GuiLoadingRenderer;
import betamoon.gui.loading.GuiLoadingResources;
import betamoon.gui.loading.GuiLoadingStatus;
import betamoon.teleport.TeleportTicket;
import java.util.Map;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.LoadingScreenRenderer;
import org.lwjgl.opengl.Display;

/**
 * Full-screen native loading presentation for queued and blocking transfer
 * work.
 */
public final class GuiTeleportProgress extends GuiScreen implements IProgressUpdate {
    private final TeleportTicket ticket;
    private final String title;
    private final GuiLoadingStatus status;
    private final GuiLoadingAppearance override;
    private final GuiLoadingAppearance dimension;
    private GuiLoadingResources resources;
    private GuiLoadingPainter painter;
    private GuiLoadingRenderer blocking;
    private String observedStage = "";
    private String nativeMessage;
    private boolean presentationClosed;

    public GuiTeleportProgress(TeleportTicket ticket, String destination) {
        this(ticket, destination, "Entering");
    }

    public GuiTeleportProgress(TeleportTicket ticket, String destination, String title) {
        this(ticket, destination, title, null, null);
    }

    public GuiTeleportProgress(TeleportTicket ticket, String destination, String title, GuiLoadingAppearance override,
            GuiLoadingAppearance dimension) {
        this.ticket = ticket;
        this.title = title + " " + destination;
        status = new GuiLoadingStatus(this.title);
        this.override = override;
        this.dimension = dimension;
    }

    @Override
    public void initGui() {
        if (resources != null || presentationClosed || override == null && dimension == null || mc.loadingScreen == null
                || mc.loadingScreen.getClass() != LoadingScreenRenderer.class) {
            return;
        }
        resources = new GuiLoadingResources(mc, override, dimension);
        painter = new GuiLoadingPainter(mc, resources, status);
        if (painter.styled()) {
            blocking = new GuiLoadingRenderer(mc, painter, status);
        }
    }

    /**
     * The transfer backend, rather than native screen replacement, owns disposal.
     */
    public void closePresentation() {
        if (presentationClosed) {
            return;
        }
        presentationClosed = true;
        try {
            if (blocking != null) {
                blocking.close();
            }
        } finally {
            if (resources != null) {
                resources.close();
            }
            blocking = null;
            painter = null;
        }
    }

    public String getTitle() {
        return title;
    }

    public void showStage(String stage, int progress) {
        observedStage = stage;
        status.message = stageMessage(stage);
        status.progress(progress);
        drawBlockingProgress();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Map<String, Object> progress = ticket.progress();
        String stage = (String) progress.get("stage");
        if (!observedStage.equals(stage)) {
            observedStage = stage;
            status.message = stageMessage(stage);
            status.progress(-1);
        }
        if (painter != null) {
            painter.paint(width, height);
            return;
        }
        drawBackground(0);
        drawCenteredString(fontRenderer, fit(title), width / 2, height / 2 - 20, 0xFFFFFF);
        drawCenteredString(fontRenderer, fit(status.message), width / 2, height / 2 + 4, 0xFFFFFF);
        if (status.percent() >= 0) {
            int left = width / 2 - 50;
            int top = height / 2 + 16;
            drawRect(left, top, left + 100, top + 2, 0xFF808080);
            if (status.percent() > 0) {
                drawRect(left, top, left + status.percent(), top + 2, 0xFF80FF80);
            }
        }
    }

    private String fit(String text) {
        if (fontRenderer.getStringWidth(text) <= width - 24) {
            return text;
        }
        int length = text.length();
        while (length > 0 && fontRenderer.getStringWidth(text.substring(0, length) + "...") > width - 24) {
            length--;
        }
        return text.substring(0, length) + "...";
    }

    @Override
    public void func_594_b(String text) {
        displayLoadingString(text);
    }

    @Override
    public void displayLoadingString(String text) {
        status.message = text;
        status.progress(-1);
        drawBlockingProgress();
    }

    @Override
    public void setLoadingProgress(int progress) {
        status.progress(progress);
        drawBlockingProgress();
    }

    private void drawBlockingProgress() {
        if (mc == null || mc.loadingScreen == null || !Display.isCreated()) {
            return;
        }
        // The native renderer updates the display while synchronous save I/O runs.
        // Normal GUI frames draw the same presentation without swapping buffers.
        String message = status.message;
        int percent = status.percent();
        boolean shared = mc.loadingScreen == blocking;
        mc.loadingScreen.printText(shared ? title : fit(title));
        if (!message.equals(nativeMessage)) {
            mc.loadingScreen.displayLoadingString(shared ? message : fit(message));
            nativeMessage = message;
        }
        mc.loadingScreen.setLoadingProgress(percent);
    }

    private static String stageMessage(String stage) {
        switch (stage) {
            case "backup":
                return "Backing up your save";
            case "recovery":
                return "Restoring saved terrain";
            case "spawn":
            case "destination":
                return "Preparing terrain";
            case "arrival":
                return "Checking the destination";
            case "bed":
                return "Checking respawn position";
            case "saving":
                return "Saving terrain";
            case "saving_player":
                return "Saving player data";
            case "switching":
                return "Entering dimension";
            case "finalizing":
                return "Finishing teleportation";
            default:
                return "Preparing destination";
        }
    }

    @Override
    protected void keyTyped(char character, int key) {
        // Loading owns input until completion; Escape must not dismiss it.
    }
}
