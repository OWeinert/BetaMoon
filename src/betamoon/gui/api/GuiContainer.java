package betamoon.gui.api;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.FontRenderer;

public class GuiContainer extends GuiComponentBase {
    private final List children = new ArrayList();

    public void addChild(GuiComponent child) {
        if (child == null) {
            return;
        }
        children.add(child);
    }

    public void removeChild(GuiComponent child) {
        children.remove(child);
    }

    public void clear() {
        children.clear();
    }

    public void layout(int screenWidth, int screenHeight) {
        for (int i = 0; i < children.size(); i++) {
            GuiComponent child = (GuiComponent) children.get(i);
            child.layout(screenWidth, screenHeight);
        }
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        for (int i = 0; i < children.size(); i++) {
            GuiComponent child = (GuiComponent) children.get(i);
            child.draw(font, mouseX, mouseY, partialTicks);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiComponent child = (GuiComponent) children.get(i);
            if (child.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiComponent child = (GuiComponent) children.get(i);
            if (child.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        boolean handled = false;
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiComponent child = (GuiComponent) children.get(i);
            if (child.mouseDragged(mouseX, mouseY, mouseDown)) {
                handled = true;
            }
        }
        return handled;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiComponent child = (GuiComponent) children.get(i);
            if (child.mouseScrolled(mouseX, mouseY, wheelDelta, shiftDown)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiComponent child = (GuiComponent) children.get(i);
            if (child.keyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }
}
