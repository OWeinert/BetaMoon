package betamoon.event.context;

import betamoon.utils.ClassNameUtils;
import net.minecraft.src.GuiScreen;

public final class GuiEventCtx extends EventContext {
    private final GuiScreen previousScreen;
    private final GuiScreen currentScreen;

    public GuiEventCtx(net.minecraft.client.Minecraft minecraft, GuiScreen screen) {
        super(minecraft);
        this.previousScreen = null;
        this.currentScreen = screen;
    }

    public GuiEventCtx(net.minecraft.client.Minecraft minecraft, GuiScreen previousScreen, GuiScreen currentScreen) {
        super(minecraft);
        this.previousScreen = previousScreen;
        this.currentScreen = currentScreen;
    }

    public GuiScreen getScreen() {
        return currentScreen;
    }

    public GuiScreen getCurrentScreen() {
        return currentScreen;
    }

    public GuiScreen getPreviousScreen() {
        return previousScreen;
    }

    public String getGuiClassName() {
        return resolveName(currentScreen);
    }

    public String getCurrentScreenName() {
        return resolveName(currentScreen);
    }

    public String getPreviousScreenName() {
        return resolveName(previousScreen);
    }

    private static String resolveName(GuiScreen screen) {
        if (screen == null) {
            return null;
        }
        String simpleName = screen.getClass().getSimpleName();
        return ClassNameUtils.toUnobfuscated(simpleName);
    }
}
