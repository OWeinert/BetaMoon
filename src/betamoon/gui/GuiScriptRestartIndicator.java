package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.luamodloader.NonReloadableScriptRegistry;

/** Restart-required marker used by the script list and detail header. */
final class GuiScriptRestartIndicator extends GuiElement {
    static final int DEFAULT_SIZE = 16;
    private static final String TEXTURE = "/resources/betamoon/gui/symbol_reload_blocked.png";

    private String sourceFileName;
    private boolean publishedVisible;
    private String publishedTooltip = "";

    void prepare(GuiContext context) {
        publishedVisible = sourceFileName != null && NonReloadableScriptRegistry.contains(sourceFileName);
        if (publishedVisible) {
            context.getRenderer().preparePackagedTexture(TEXTURE);
            String reason = NonReloadableScriptRegistry.reason(sourceFileName);
            String detail = reason == null || reason.isEmpty() ? "startup-only content" : reason;
            publishedTooltip = "Not hot-reloadable: " + detail + ". Restart Minecraft to apply changes.";
        }
    }

    @Override
    protected void updateElement(GuiContext context) {
        prepare(context);
    }

    GuiScriptRestartIndicator(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    boolean shouldRender() {
        return publishedVisible;
    }

    @Override
    protected void renderElement(GuiContext context) {
        if (!shouldRender()) {
            return;
        }
        context.getRenderer().drawPackagedTexture(TEXTURE, getBounds());
        if (isHovered(context)) {
            context.showTooltip(publishedTooltip);
        }
    }
}
