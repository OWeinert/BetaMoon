package betamoon.gui.framework;

/**
 * One scene-owned portal/layer; closing restores a still-eligible opener
 * exactly once.
 */
public final class GuiLayer implements AutoCloseable {
    final GuiElement content;
    final GuiElement opener;
    final boolean modal;
    private final GuiScene scene;
    private boolean active = true;

    GuiLayer(GuiScene scene, GuiElement content, boolean modal, GuiElement opener) {
        this.scene = scene;
        this.content = content;
        this.modal = modal;
        this.opener = opener;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void close() {
        if (active) {
            active = false;
            scene.closeLayer(this);
        }
    }
}
