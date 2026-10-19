package betamoon.gui;

import net.minecraft.src.GameSettings;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiControls;
import net.minecraft.src.GuiOptions;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Mouse;

/** Minecraft's key-binding editor with a tab for BetaMoon actions. */
public final class GuiTabbedControls extends GuiControls {
    static final int MINECRAFT_TAB = 900;
    static final int BETAMOON_TAB = 901;
    static final int TAB_Y = 29;
    static final int TAB_WIDTH = 100;
    private static final int BINDING_OFFSET = 20;

    private final GuiScreen parent;
    private final GameSettings settings;
    private int firstBindingRow;

    public GuiTabbedControls(GuiScreen parent, GameSettings settings) {
        super(parent, settings);
        this.parent = parent;
        this.settings = settings;
    }

    public static boolean isProtectedScreen(GuiScreen screen) {
        return screen instanceof GuiTabbedControls || screen instanceof GuiBetaMoonActions
                || screen instanceof GuiControls || screen instanceof GuiOptions;
    }

    @Override
    public void initGui() {
        controlList.clear();
        super.initGui();
        ((GuiButton) controlList.get(settings.keyBindings.length)).yPosition = height - 27;
        layoutBindings();
        controlList.add(new GuiButton(MINECRAFT_TAB, width / 2 - TAB_WIDTH - 2, TAB_Y, TAB_WIDTH, 20, "Minecraft"));
        controlList.add(new GuiButton(BETAMOON_TAB, width / 2 + 2, TAB_Y, TAB_WIDTH, 20, "BetaMoon"));
        ((GuiButton) controlList.get(controlList.size() - 2)).enabled = false;
    }

    private int visibleBindingRows() {
        int firstY = height / 6 + BINDING_OFFSET;
        return Math.max(1, (height - 27 - 20 - firstY) / 24 + 1);
    }

    private void layoutBindings() {
        int totalRows = (settings.keyBindings.length + 1) / 2;
        firstBindingRow = Math.max(0, Math.min(firstBindingRow, Math.max(0, totalRows - visibleBindingRows())));
        for (int index = 0; index < settings.keyBindings.length; index++) {
            GuiButton button = (GuiButton) controlList.get(index);
            int row = index / 2;
            boolean visible = row >= firstBindingRow && row < firstBindingRow + visibleBindingRows();
            button.enabled2 = visible;
            button.yPosition = visible ? height / 6 + BINDING_OFFSET + 24 * (row - firstBindingRow) : -1000;
        }
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        super.handleMouseInput();
        if (wheel != 0) {
            scrollBindingRows(wheel > 0 ? -1 : 1);
        }
    }

    void scrollBindingRows(int rows) {
        firstBindingRow += rows;
        layoutBindings();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BETAMOON_TAB) {
            mc.displayGuiScreen(new GuiBetaMoonActions(parent, settings));
            return;
        }
        if (button.id == MINECRAFT_TAB) {
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, screenTitle, width / 2, 10, 0xffffff);
        int labelsLeft = width / 2 - 155;
        for (int index = 0; index < settings.keyBindings.length; index++) {
            GuiButton button = (GuiButton) controlList.get(index);
            if (!button.enabled2) {
                continue;
            }
            int labelX = labelsLeft + index % 2 * 160 + 76;
            drawString(fontRenderer, settings.getKeyBindingDescription(index), labelX, button.yPosition + 7, 0xffffff);
        }
        int visibleBindings = Math.min(settings.keyBindings.length, visibleBindingRows() * 2);
        if (settings.keyBindings.length > visibleBindings) {
            int first = firstBindingRow * 2 + 1;
            int last = Math.min(settings.keyBindings.length, firstBindingRow * 2 + visibleBindings);
            drawCenteredString(fontRenderer,
                    "Keys " + first + "-" + last + " of " + settings.keyBindings.length + " - scroll to browse",
                    width / 2, height - 46, 0xcccccc);
        }
        for (Object entry : controlList) {
            ((GuiButton) entry).drawButton(mc, mouseX, mouseY);
        }
    }
}
