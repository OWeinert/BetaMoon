package betamoon.gui;

import betamoon.BetaMoonClient;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.widget.GuiButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;

/**
 * Engine-owned recovery content in the same menu scene as script contributions.
 */
public final class GuiMenuBuiltinViews extends GuiContainer {
    private final GuiButton scripts;
    private final GuiUpdateNotice update;

    public GuiMenuBuiltinViews(Minecraft client, GuiScreen parent, boolean main) {
        scripts = add(new GuiButton("Scripts", 90, () -> client.displayGuiScreen(new GuiScreenScripts(parent))));
        update = main ? add(new GuiUpdateNotice(parent, BetaMoonClient.getInstance().version())) : null;
    }

    public void prepare() {
        if (update != null) {
            update.setRelease(BetaMoonClient.getInstance().getAvailableUpdate());
        }
    }

    public List<Rect> reservations(int width, int height) {
        List<Rect> result = new ArrayList<>();
        result.add(Rect.fromPositionAndSize(10, height - 40, 90, 20));
        if (update != null) {
            result.add(GuiUpdateNotice.noticeBounds(width, height));
        }
        return result;
    }

    @Override
    protected GuiFrameContent sampleFrameContent() {
        return (x, y) -> false;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        scripts.arrange(context, Rect.fromPositionAndSize(10, getBottom() - 40, 90, 20));
        if (update != null) {
            Rect bounds = GuiUpdateNotice.noticeBounds(getWidth(), getHeight());
            update.setCompact(bounds.getWidth() < GuiUpdateNotice.CARD_WIDTH);
            update.arrange(context, bounds);
        }
    }
}
