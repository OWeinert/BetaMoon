package betamoon.gui.api.screen;

import net.minecraft.src.GuiScreen;

public interface IGuiScreenFactory {
    GuiScreen create(GuiScreen parent);
}
