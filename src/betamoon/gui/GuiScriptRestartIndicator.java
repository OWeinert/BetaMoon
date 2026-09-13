package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.luamodloader.NonReloadableScriptRegistry;

/** Restart-required marker used by the script list and detail header. */
final class GuiScriptRestartIndicator extends GuiElement {
    static final int DEFAULT_SIZE = 16;
    private static final String TEXTURE = "/resources/betamoon/gui/symbol_reload_blocked.png";

    private String sourceFileName;

    GuiScriptRestartIndicator(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    boolean shouldRender() {
        return sourceFileName != null && NonReloadableScriptRegistry.contains(sourceFileName);
    }

    @Override
    protected void renderElement(GuiContext context) {
        if (!shouldRender()) {
            return;
        }
        context.getRenderer().drawPackagedTexture(TEXTURE, getBounds());
        if (isHovered(context)) {
            String reason = NonReloadableScriptRegistry.reason(sourceFileName);
            String detail = reason == null || reason.isEmpty() ? "startup-only content" : reason;
            context.showTooltip("Not hot-reloadable: " + detail + ". Restart Minecraft to apply changes.");
        }
    }
}
