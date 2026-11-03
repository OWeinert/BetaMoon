package betamoon.gui.inventory;

import betamoon.gui.inventory.GuiInventoryProfile.Position;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ContainerPlayer;
import net.minecraft.src.GuiContainer;
import net.minecraft.src.GuiInventory;
import net.minecraft.src.IInventory;
import net.minecraft.src.Slot;
import net.minecraft.src.SlotCrafting;
import org.objectweb.asm.Type;

/**
 * Owns presentation coordinates only. Native inventory contents and
 * transactions are never written here.
 */
public final class GuiInventoryNativeState {
    private static Field panelWidth;
    private static Field panelHeight;
    private static Field slotInventory;
    private static Field slotIndex;
    private final GuiInventory screen;
    private final ContainerPlayer container;
    private final List<Slot> slots;
    private final List<Position> original;
    private final int width;
    private final int height;
    private final int window;
    private GuiInventoryLayout installed;

    public GuiInventoryNativeState(Minecraft client, GuiInventory screen) {
        if (screen.getClass() != GuiInventory.class || client.thePlayer == null
                || screen.inventorySlots != client.thePlayer.inventorySlots
                || screen.inventorySlots.getClass() != ContainerPlayer.class
                || screen.inventorySlots.slots.size() != 45) {
            throw new IllegalArgumentException("Unsupported native player inventory schema");
        }
        prepareFields();
        this.screen = screen;
        container = (ContainerPlayer) screen.inventorySlots;
        window = container.windowId;
        width = readInt(panelWidth, screen);
        height = readInt(panelHeight, screen);
        List<Slot> slots = new ArrayList<>();
        List<Position> original = new ArrayList<>();
        for (GuiInventorySchema.Slot semantic : GuiInventorySchema.SLOTS) {
            Object value = container.slots.get(semantic.nativeIndex);
            if (!(value instanceof Slot)) {
                throw new IllegalArgumentException("Unsupported native inventory slot: " + semantic.id);
            }
            Slot slot = (Slot) value;
            IInventory inventory = semantic.nativeIndex == 0
                    ? container.craftResult
                    : semantic.nativeIndex < 5 ? container.craftMatrix : client.thePlayer.inventory;
            int index = semantic.nativeIndex == 0
                    ? 0
                    : semantic.nativeIndex < 5
                            ? semantic.nativeIndex - 1
                            : semantic.nativeIndex < 9
                                    ? 44 - semantic.nativeIndex
                                    : semantic.nativeIndex < 36 ? semantic.nativeIndex : semantic.nativeIndex - 36;
            if (slot.slotNumber != semantic.nativeIndex || read(slotInventory, slot) != inventory
                    || readInt(slotIndex, slot) != index || semantic.nativeIndex == 0 && !(slot instanceof SlotCrafting)
                    || semantic.group.name.equals("armor") && !armorSlot(slot)) {
                throw new IllegalArgumentException(
                        "Native inventory slot does not match its semantic group: " + semantic.id);
            }
            slots.add(slot);
            original.add(new Position(slot.xDisplayPosition, slot.yDisplayPosition));
        }
        this.slots = Collections.unmodifiableList(slots);
        this.original = Collections.unmodifiableList(original);
    }

    public GuiInventoryLayout propose(GuiInventoryProfile profile) {
        return new GuiInventoryLayout(profile, width, height, original);
    }

    public GuiInventoryLayout installed() {
        return installed;
    }

    public boolean matches() {
        if (screen.inventorySlots != container || container.windowId != window
                || container.slots.size() != slots.size()) {
            return false;
        }
        int expectedWidth = installed == null ? width : installed.width;
        int expectedHeight = installed == null ? height : installed.height;
        if (readInt(panelWidth, screen) != expectedWidth || readInt(panelHeight, screen) != expectedHeight) {
            return false;
        }
        List<Position> expected = installed == null ? original : installed.slots;
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            Position position = expected.get(index);
            if (container.slots.get(index) != slot || slot.slotNumber != index || slot.xDisplayPosition != position.x
                    || slot.yDisplayPosition != position.y) {
                return false;
            }
        }
        return true;
    }

    public void install(GuiInventoryLayout layout) {
        if (!matches()) {
            throw new IllegalStateException("Native inventory geometry changed outside its presentation owner");
        }
        installed = layout;
        try {
            write(panelWidth, screen, layout.width);
            write(panelHeight, screen, layout.height);
            for (int index = 0; index < slots.size(); index++) {
                Slot slot = slots.get(index);
                Position position = layout.slots.get(index);
                slot.xDisplayPosition = position.x;
                slot.yDisplayPosition = position.y;
            }
        } catch (RuntimeException failure) {
            restore();
            throw failure;
        }
    }

    /**
     * Restore only coordinates that still equal our installed values; never
     * overwrite a foreign edit.
     */
    public boolean restore() {
        GuiInventoryLayout previous = installed;
        if (previous != null) {
            if (readInt(panelWidth, screen) == previous.width) {
                write(panelWidth, screen, width);
            }
            if (readInt(panelHeight, screen) == previous.height) {
                write(panelHeight, screen, height);
            }
            for (int index = 0; index < slots.size(); index++) {
                Slot slot = slots.get(index);
                Position own = previous.slots.get(index);
                Position nativePosition = original.get(index);
                if (slot.xDisplayPosition == own.x) {
                    slot.xDisplayPosition = nativePosition.x;
                }
                if (slot.yDisplayPosition == own.y) {
                    slot.yDisplayPosition = nativePosition.y;
                }
            }
            installed = null;
        }
        return matches();
    }

    private static void prepareFields() {
        if (panelWidth == null) {
            Field width = field(GuiContainer.class, "xSize", int.class);
            Field height = field(GuiContainer.class, "ySize", int.class);
            Field inventory = field(Slot.class, "inventory", IInventory.class);
            Field index = field(Slot.class, "slotIndex", int.class);
            panelWidth = width;
            panelHeight = height;
            slotInventory = inventory;
            slotIndex = index;
        }
    }

    private static boolean armorSlot(Slot slot) {
        NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        for (Class<?> type = slot.getClass(); type != null; type = type.getSuperclass()) {
            if (bindings.names.canonicalClass(type).equals("net/minecraft/src/SlotArmor")) {
                return true;
            }
        }
        return false;
    }

    private static Field field(Class<?> owner, String name, Class<?> type) {
        NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        if (bindings == null) {
            throw new IllegalStateException("Native inventory mappings unavailable");
        }
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == type
                    && bindings.names.fieldMatches(owner, field.getName(), Type.getDescriptor(type), name, "named")) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Unsupported native inventory field: " + name);
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Native inventory presentation access failed", failure);
        }
    }

    private static int readInt(Field field, Object owner) {
        try {
            return field.getInt(owner);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Native inventory presentation access failed", failure);
        }
    }

    private static void write(Field field, Object owner, int value) {
        try {
            field.setInt(owner, value);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Native inventory presentation access failed", failure);
        }
    }
}
