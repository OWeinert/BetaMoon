package betamoon.gui.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic native player slots, independent of controller transaction IDs and
 * item contents.
 */
public final class GuiInventorySchema {
    public static final class Group {
        public final String name;
        public final int first;
        public final int count;
        public final int columns;
        public final int x;
        public final int y;

        private Group(String name, int first, int count, int columns, int x, int y) {
            this.name = name;
            this.first = first;
            this.count = count;
            this.columns = columns;
            this.x = x;
            this.y = y;
        }
    }

    public static final class Slot {
        public final String id;
        public final Group group;
        public final int index;
        public final int nativeIndex;

        private Slot(Group group, int index) {
            this.group = group;
            this.index = index + 1;
            nativeIndex = group.first + index;
            id = group.name + "/" + this.index;
        }

        public int defaultX() {
            return group.x + (index - 1) % group.columns * 18;
        }

        public int defaultY() {
            return group.y + (index - 1) / group.columns * 18;
        }
    }

    public static final Map<String, Group> GROUPS;
    public static final List<Slot> SLOTS;
    public static final Map<String, Slot> BY_ID;
    static {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Group group : new Group[]{new Group("output", 0, 1, 1, 144, 36), new Group("crafting", 1, 4, 2, 88, 26),
                new Group("armor", 5, 4, 1, 8, 8), new Group("storage", 9, 27, 9, 8, 84),
                new Group("hotbar", 36, 9, 9, 8, 142)}) {
            groups.put(group.name, group);
        }
        List<Slot> slots = new ArrayList<>();
        Map<String, Slot> byId = new LinkedHashMap<>();
        for (Group group : groups.values()) {
            for (int index = 0; index < group.count; index++) {
                Slot slot = new Slot(group, index);
                slots.add(slot);
                byId.put(slot.id, slot);
            }
        }
        GROUPS = Collections.unmodifiableMap(groups);
        SLOTS = Collections.unmodifiableList(slots);
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private GuiInventorySchema() {
    }
}
