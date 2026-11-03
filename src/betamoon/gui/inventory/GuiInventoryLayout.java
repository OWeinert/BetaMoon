package betamoon.gui.inventory;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.inventory.GuiInventoryProfile.Position;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete pixel geometry is admitted before any native field is changed. */
public final class GuiInventoryLayout {
    public final int width;
    public final int height;
    public final List<Position> slots;
    public final Rect preview;

    public GuiInventoryLayout(GuiInventoryProfile profile, int nativeWidth, int nativeHeight,
            List<Position> nativeSlots) {
        width = profile.panelWidth == 0 ? nativeWidth : profile.panelWidth;
        height = profile.panelHeight == 0 ? nativeHeight : profile.panelHeight;
        if (width < 36 || height < 36 || width > 4096 || height > 4096 || nativeSlots.size() != 45) {
            throw new IllegalArgumentException("Unsupported native player inventory extent or slot schema");
        }
        List<Position> positions = new ArrayList<>();
        List<Rect> hitRegions = new ArrayList<>();
        for (GuiInventorySchema.Slot slot : GuiInventorySchema.SLOTS) {
            Position position = nativeSlots.get(slot.nativeIndex);
            Position group = profile.groups.get(slot.group.name);
            if (group != null) {
                position = new Position(group.x + (slot.index - 1) % slot.group.columns * 18,
                        group.y + (slot.index - 1) / slot.group.columns * 18);
            }
            position = profile.positions.getOrDefault(slot.id, position);
            Rect hit = Rect.fromPositionAndSize(position.x - 1, position.y - 1, 18, 18);
            if (hit.getRight() > width || hit.getBottom() > height) {
                throw new IllegalArgumentException("Native slot hit region leaves the inventory panel: " + slot.id);
            }
            for (Rect previous : hitRegions) {
                if (!hit.intersect(previous).isEmpty()) {
                    throw new IllegalArgumentException("Native inventory slot hit regions overlap: " + slot.id);
                }
            }
            hitRegions.add(hit);
            positions.add(position);
        }
        if (profile.preview.getRight() > width || profile.preview.getBottom() > height) {
            throw new IllegalArgumentException("Player preview leaves the inventory panel");
        }
        slots = Collections.unmodifiableList(positions);
        preview = profile.preview;
        if (profile.background.equals("native")) {
            if (width != 176 || height != 166) {
                throw new IllegalArgumentException("Native inventory artwork requires its 176x166 panel extent");
            }
            for (GuiInventorySchema.Slot slot : GuiInventorySchema.SLOTS) {
                Position position = slots.get(slot.nativeIndex);
                if (position.x != slot.defaultX() || position.y != slot.defaultY()) {
                    throw new IllegalArgumentException("Native inventory artwork requires its original slot positions");
                }
            }
        }
    }

    public Rect panel(int viewportWidth, int viewportHeight) {
        if (width > viewportWidth - 8 || height > viewportHeight - 8) {
            throw new IllegalArgumentException("Inventory profile does not fit this viewport");
        }
        return Rect.fromPositionAndSize((viewportWidth - width) / 2, (viewportHeight - height) / 2, width, height);
    }
}
