package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.widget.GuiButton;
import betamoon.io.IoUtils;
import betamoon.update.UpdateRelease;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ModLoader;

/** Persistent startup update card, anchored above Scripts by the main menu. */
final class GuiUpdateNotice extends GuiContainer {
    static final int CARD_WIDTH = 180;
    static final int CARD_HEIGHT = 56;
    private final GuiScreen parent;
    private final String installedVersion;
    private final GuiButton download = add(new GuiButton("", this::activate));
    private UpdateRelease release;
    private boolean compact;

    GuiUpdateNotice(GuiScreen parent, String installedVersion) {
        this.parent = parent;
        this.installedVersion = installedVersion;
        setVisible(false);
    }

    void setRelease(UpdateRelease release) {
        if (this.release == release) {
            return;
        }
        this.release = release;
        setVisible(release != null);
        updateLabel();
    }

    void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
            updateLabel();
        }
    }

    private void updateLabel() {
        download.setLabel(compact ? "!" : release == null ? "" : "View on " + release.getSourceName());
        requestLayout();
    }

    static Rect noticeBounds(int width, int height) {
        Rect card = Rect.fromPositionAndSize(10, height - 46 - CARD_HEIGHT, CARD_WIDTH, CARD_HEIGHT);
        // Reserve the entire vanilla button stack, including the optional Quit row.
        Rect vanillaButtons = Rect.fromPositionAndSize(width / 2 - 100, height / 4 + 48, 200, 104);
        if (card.getTop() < 100 || card.getRight() > width - 10 || !card.intersect(vanillaButtons).isEmpty()) {
            return Rect.fromPositionAndSize(10, height - 66, 20, 20);
        }
        return card;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        download.arrange(context,
                compact ? getBounds() : Rect.fromPositionAndSize(getLeft() + 6, getBottom() - 26, getWidth() - 12, 20));
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        if (compact || release == null) {
            return;
        }
        GuiRenderer renderer = context.getRenderer();
        renderer.drawRect(getBounds(), 0xFF777777);
        renderer.drawRect(getLeft() + 1, getTop() + 1, getRight() - 1, getBottom() - 1, 0xF51C1C1C);
        int centerX = getLeft() + getWidth() / 2;
        int y = getTop() + 6;
        renderer.drawScaledCenteredText("BetaMoon update available", centerX, y, 0xFFFFCC55, 1.0F);
        String versions = installedVersion + " -> " + release.getVersion();
        renderer.drawScaledCenteredText(renderer.trimToWidth(versions, getWidth() - 12), centerX, y + 12,
                0xFFFFFFFF, 1.0F);
    }

    @Override
    protected void renderAfterChildren(GuiContext context) {
        if (release == null) {
            return;
        }
        if (!compact) {
            GuiRenderer renderer = context.getRenderer();
            int x = download.getRight() - 13;
            int y = download.getTop() + 6;
            renderer.drawHorizontalLine(x, x + 6, y, 0xFFFFFFFF);
            renderer.drawVerticalLine(y, y + 6, x + 5, 0xFFFFFFFF);
            for (int step = 0; step < 5; step++) {
                renderer.drawRect(x + step, y + 5 - step, x + step + 1, y + 6 - step, 0xFFFFFFFF);
            }
        }
    }

    private void activate() {
        if (release == null) {
            return;
        }
        if (compact || !IoUtils.openWebPage(release.getPageUri())) {
            ModLoader.getMinecraftInstance()
                    .displayGuiScreen(new GuiUpdateDetails(parent, installedVersion, release, !compact));
        }
    }
}
