package betamoon.gui.api.screen;

import net.minecraft.src.GuiScreen;

@FunctionalInterface
public interface IGuiScreenFactory {
    GuiScreen create(GuiScreen parent);
}
