package betamoon.gui;

import betamoon.teleport.TeleportTicket;
import java.util.Map;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.IProgressUpdate;
import org.lwjgl.opengl.Display;

/**
 * Full-screen native loading presentation for queued and blocking transfer
 * work.
 */
public final class GuiTeleportProgress extends GuiScreen implements IProgressUpdate {
    private final TeleportTicket ticket;
    private final String title;
    private String observedStage = "";
    private String message = "Preparing destination";
    private String nativeMessage;
    private int percent = -1;

    public GuiTeleportProgress(TeleportTicket ticket, String destination) {
        this(ticket, destination, "Entering");
    }

    public GuiTeleportProgress(TeleportTicket ticket, String destination, String title) {
        this.ticket = ticket;
        this.title = title + " " + destination;
    }

    public String getTitle() {
        return title;
    }

    public void showStage(String stage, int progress) {
        observedStage = stage;
        message = stageMessage(stage);
        percent = clamp(progress);
        drawBlockingProgress();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Map<String, Object> progress = ticket.progress();
        String stage = (String) progress.get("stage");
        if (!observedStage.equals(stage)) {
            observedStage = stage;
            message = stageMessage(stage);
            percent = -1;
        }
        drawBackground(0);
        drawCenteredString(fontRenderer, fit(title), width / 2, height / 2 - 20, 0xFFFFFF);
        drawCenteredString(fontRenderer, fit(message), width / 2, height / 2 + 4, 0xFFFFFF);
        if (percent >= 0) {
            int left = width / 2 - 50;
            int top = height / 2 + 16;
            drawRect(left, top, left + 100, top + 2, 0xFF808080);
            if (percent > 0) {
                drawRect(left, top, left + percent, top + 2, 0xFF80FF80);
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
        message = text;
        percent = -1;
        drawBlockingProgress();
    }

    @Override
    public void setLoadingProgress(int progress) {
        percent = clamp(progress);
        drawBlockingProgress();
    }

    private void drawBlockingProgress() {
        if (mc == null || mc.loadingScreen == null || !Display.isCreated()) {
            return;
        }
        // The native renderer updates the display while synchronous save I/O runs.
        // Normal GUI frames draw the same presentation without swapping buffers.
        mc.loadingScreen.printText(fit(title));
        if (!message.equals(nativeMessage)) {
            mc.loadingScreen.displayLoadingString(fit(message));
            nativeMessage = message;
        }
        mc.loadingScreen.setLoadingProgress(percent);
    }

    private static int clamp(int progress) {
        return progress < 0 ? -1 : Math.min(100, progress);
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
