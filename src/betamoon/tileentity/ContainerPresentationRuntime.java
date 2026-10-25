package betamoon.tileentity;

import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationRuntime;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.ToIntFunction;

/**
 * Supplies the container adapter while keeping per-instance presentation in the
 * shared engine.
 */
public final class ContainerPresentationRuntime extends GuiPresentationRuntime {
    public ContainerPresentationRuntime(ContainerGuiDefinition definition, LuaTileEntity tile,
            ContainerControlRuntime controls, ToIntFunction<String> textWidth) {
        this(definition, tile, controls, textWidth, () -> System.nanoTime() / 1e9);
    }

    public ContainerPresentationRuntime(ContainerGuiDefinition definition, LuaTileEntity tile,
            ContainerControlRuntime controls, ToIntFunction<String> textWidth, DoubleSupplier clock) {
        this(definition, new ContainerPresentationHost(definition, Objects.requireNonNull(tile, "GUI tile"),
                Objects.requireNonNull(controls, "GUI controls")), textWidth, clock);
    }

    private ContainerPresentationRuntime(ContainerGuiDefinition definition, ContainerPresentationHost host,
            ToIntFunction<String> textWidth, DoubleSupplier clock) {
        super(definition, host, textWidth, clock);
    }

    public ContainerPresentationRuntime reload(ContainerGuiDefinition replacement) {
        return (ContainerPresentationRuntime) super.reload(replacement);
    }

    @Override
    protected GuiPresentationRuntime createReplacement(GuiDocumentDefinition replacement,
            GuiPresentationHost replacementHost) {
        return new ContainerPresentationRuntime((ContainerGuiDefinition) replacement,
                (ContainerPresentationHost) replacementHost, textWidth, clock);
    }
}
