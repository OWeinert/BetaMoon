package betamoon.gui.framework;

import betamoon.gui.presentation.GuiPresentationTree;

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
    private Runnable closed = () -> {
    };

    GuiLayer(GuiScene scene, GuiElement content, boolean modal, GuiElement opener) {
        this.scene = scene;
        this.content = content;
        this.modal = modal;
        this.opener = opener;
    }

    public boolean isActive() {
        return active;
    }

    public GuiPresentationTree document() {
        return content instanceof GuiLayerDocument ? ((GuiLayerDocument) content).document() : null;
    }

    public void onClosed(Runnable callback) {
        closed = callback;
    }

    public void replace(GuiPresentationTree replacement) {
        scene.replaceLayer(this, replacement);
    }

    void finishClose() {
        Runnable callback = closed;
        closed = () -> {
        };
        try {
            callback.run();
        } finally {
            GuiPresentationTree document = document();
            if (document != null) {
                document.close();
                ((GuiLayerDocument) content).replace(null);
            }
        }
    }

    @Override
    public void close() {
        if (active) {
            active = false;
            scene.closeLayer(this);
        }
    }
}
