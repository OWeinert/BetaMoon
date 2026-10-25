package betamoon.tileentity;

import betamoon.gui.presentation.GuiPresentationLayout;

/**
 * Validates a container document against its declared initial data and session
 * state.
 */
public final class ContainerGuiLayout {
    private ContainerGuiLayout() {
    }

    public static void validateDefinition(ContainerGuiDefinition definition) {
        GuiPresentationLayout.validateDefinition(definition, ContainerPresentationHost.defaults(definition));
    }
}
