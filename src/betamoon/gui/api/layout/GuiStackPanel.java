package betamoon.gui.api.layout;

import betamoon.gui.api.component.GuiContainer;
import betamoon.gui.api.component.IGuiComponent;
import java.util.ArrayList;
import java.util.List;

public final class GuiStackPanel extends GuiContainer {
    public static final int FILL = -1;

    private final List entries = new ArrayList();
    private EnumAxis axis = EnumAxis.VERTICAL;
    private int spacing = 4;
    private int padding = 0;

    public GuiStackPanel() {
    }

    public GuiStackPanel(EnumAxis axis) {
        if (axis != null) {
            this.axis = axis;
        }
    }

    public void setAxis(EnumAxis axis) {
        if (axis != null) {
            this.axis = axis;
        }
    }

    public void setSpacing(int spacing) {
        this.spacing = Math.max(0, spacing);
    }

    public void setPadding(int padding) {
        this.padding = Math.max(0, padding);
    }

    public IGuiComponent addItem(IGuiComponent child, int width, int height) {
        if (child == null) {
            return null;
        }
        addChild(child);
        entries.add(new Entry(child, width, height));
        return child;
    }

    public void removeChild(IGuiComponent child) {
        super.removeChild(child);
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = (Entry) entries.get(i);
            if (entry.child == child) {
                entries.remove(i);
            }
        }
    }

    public void clear() {
        super.clear();
        entries.clear();
    }

    public void layout(int screenWidth, int screenHeight) {
        int contentWidth = right - left;
        int contentHeight = bottom - top;
        if (entries.isEmpty()) {
            return;
        }
        if (axis == EnumAxis.VERTICAL) {
            layoutVertical(contentWidth, contentHeight);
        } else {
            layoutHorizontal(contentWidth, contentHeight);
        }
        super.layout(screenWidth, screenHeight);
    }

    private void layoutVertical(int contentWidth, int contentHeight) {
        int fixed = 0;
        int fillCount = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = (Entry) entries.get(i);
            if (entry.height == FILL) {
                fillCount++;
            } else {
                fixed += entry.height;
            }
        }
        int gaps = spacing * Math.max(0, entries.size() - 1);
        int remaining = Math.max(0, contentHeight - fixed - gaps - padding * 2);
        int fillSize = fillCount > 0 ? remaining / fillCount : 0;
        int x = left + padding;
        int y = top + padding;
        int availableWidth = Math.max(0, contentWidth - padding * 2);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = (Entry) entries.get(i);
            int height = entry.height == FILL ? fillSize : entry.height;
            int width = entry.width == FILL ? availableWidth : entry.width;
            entry.child.setBounds(x, y, x + width, y + height);
            y += height + spacing;
        }
    }

    private void layoutHorizontal(int contentWidth, int contentHeight) {
        int fixed = 0;
        int fillCount = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = (Entry) entries.get(i);
            if (entry.width == FILL) {
                fillCount++;
            } else {
                fixed += entry.width;
            }
        }
        int gaps = spacing * Math.max(0, entries.size() - 1);
        int remaining = Math.max(0, contentWidth - fixed - gaps - padding * 2);
        int fillSize = fillCount > 0 ? remaining / fillCount : 0;
        int x = left + padding;
        int y = top + padding;
        int availableHeight = Math.max(0, contentHeight - padding * 2);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = (Entry) entries.get(i);
            int width = entry.width == FILL ? fillSize : entry.width;
            int height = entry.height == FILL ? availableHeight : entry.height;
            entry.child.setBounds(x, y, x + width, y + height);
            x += width + spacing;
        }
    }

    private static final class Entry {
        private final IGuiComponent child;
        private final int width;
        private final int height;

        private Entry(IGuiComponent child, int width, int height) {
            this.child = child;
            this.width = width;
            this.height = height;
        }
    }
}
