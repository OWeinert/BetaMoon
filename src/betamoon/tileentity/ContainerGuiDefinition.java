package betamoon.tileentity;

import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiPresentationDefinition;
import java.util.List;

/** Immutable container host metadata around a shared visual document. */
public final class ContainerGuiDefinition extends GuiDocumentDefinition {
    public final ContainerDefinition container;
    public final Label playerInventoryLabel;
    public final boolean pauseGame;

    public ContainerGuiDefinition(String name, String owner, ContainerDefinition container, int width, int height,
            Label title, Label playerInventoryLabel, Background background, boolean pauseGame, List<Element> elements) {
        this(name, owner, container, width, height, title, playerInventoryLabel, background, pauseGame, elements, null);
    }

    public ContainerGuiDefinition(String name, String owner, ContainerDefinition container, int width, int height,
            Label title, Label playerInventoryLabel, Background background, boolean pauseGame, List<Element> elements,
            GuiPresentationDefinition presentation) {
        super(name, owner, width, height, title, background, elements, presentation);
        this.container = container;
        this.playerInventoryLabel = playerInventoryLabel;
        this.pauseGame = pauseGame;
    }
}
