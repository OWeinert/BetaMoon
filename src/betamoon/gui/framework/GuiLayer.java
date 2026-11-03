package betamoon.gui.framework;

import betamoon.gui.presentation.GuiPresentationTree;

/**
 * One scene-owned portal/layer; closing restores a still-eligible opener
 * exactly once.
 */
public final class GuiLayer implements AutoCloseable {
    interface Document {
        GuiPresentationTree document();

        void replace(GuiPresentationTree replacement);
    }
    final GuiElement content;
    final GuiElement opener;
    final boolean modal;
    final boolean dim;
    final boolean closeOnTab;
    private final GuiScene scene;
    private boolean active = true;
    private Runnable closed = () -> {
    };

    GuiLayer(GuiScene scene, GuiElement content, boolean modal, GuiElement opener, boolean dim, boolean closeOnTab) {
        this.scene = scene;
        this.content = content;
        this.modal = modal;
        this.opener = opener;
        this.dim = dim;
        this.closeOnTab = closeOnTab;
    }

    public boolean isActive() {
        return active;
    }

    public GuiPresentationTree document() {
        return content instanceof Document ? ((Document) content).document() : null;
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
                ((Document) content).replace(null);
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
