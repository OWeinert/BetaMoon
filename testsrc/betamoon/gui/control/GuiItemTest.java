package betamoon.gui.control;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.Item;
import net.minecraft.src.Block;

/**
 * Descriptor and semantic-source admission cannot acquire inventory authority.
 */
public final class GuiItemTest {
    public static void run() {
        if (Block.stone == null) {
            throw new AssertionError("Native block registry did not initialize");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("item", Collections.singletonMap("id", Item.swordSteel.shiftedIndex));
        GuiItemModel descriptor = new GuiItemModel(values, false);
        if (descriptor.stack().stackSize != 1 || descriptor.hotbarSlot != 0) {
            throw new AssertionError("Cosmetic descriptor defaults changed");
        }
        values.clear();
        values.put("source", Collections.singletonMap("hotbarSlot", 9));
        if (new GuiItemModel(values, true).hotbarSlot != 9) {
            throw new AssertionError("One-based semantic slot changed");
        }
        refused(values, false);
        values.put("source", Collections.singletonMap("other", 1));
        refused(values, true);
        values.put("source", Collections.singletonMap("hotbarSlot", 1.5));
        refused(values, true);
        values.clear();
        values.put("item", Collections.singletonMap("count", 2));
        refused(values, true);
        values.put("item", Collections.singletonMap("id", -1));
        refused(values, true);
        if (new GuiItemModel(Collections.emptyMap(), false).stack() != null) {
            throw new AssertionError("Empty cosmetic item should remain empty");
        }
    }

    private static void refused(Map<String, Object> values, boolean hud) {
        try {
            new GuiItemModel(values, hud);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid item descriptor/source accepted: " + values);
    }
}
