package betamoon.gui.inventory;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.interop.NativeClientScreenHost;
import betamoon.gui.inventory.GuiInventoryProfile.Position;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.discovery.NativeLoaderRegistry;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Block;
import net.minecraft.src.ContainerPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiContainer;
import net.minecraft.src.GuiInventory;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.PlayerController;
import net.minecraft.src.Slot;
import net.minecraft.src.StatBase;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import sun.misc.Unsafe;

/**
 * Headless native inventory/controller fixture; never starts Minecraft or a GL
 * context.
 */
public final class GuiInventoryNativeTest {
    public static void main(String[] args) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(GuiInventoryNativeTest.class.getClassLoader());
        TinyMappingResolver mappings;
        try (FileInputStream input = new FileInputStream("tools/rmcp/conf/mappings.tiny")) {
            mappings = TinyMappingResolver.read(input);
        }
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        Field installed = NativeDiscoveryRuntime.class.getDeclaredField("bindings");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, bindings);
        try {
            transactionsAndGeometry();
        } finally {
            installed.set(null, previous);
            bindings.close();
            loaders.close();
        }
        System.out.println("Headless native inventory passed: stable slot/window identities, hit coordinates, "
                + "left/right/shift transactions, crafting, armor, cursor/outside drops, restoration and host release.");
    }

    private static void transactionsAndGeometry() throws Exception {
        require(Block.stone.blockID > 0, "Initialize native blocks before player crafting recipes");
        Client client = allocate(Client.class);
        Player player = (Player) player(null);
        ContainerPlayer container = (ContainerPlayer) player.inventorySlots;
        container.windowId = 17;
        player.inventorySlots = container;
        player.craftingInventory = container;
        client.thePlayer = player;
        client.playerController = new PlayerController(client);
        GuiInventory screen = new GuiInventory(player);
        client.currentScreen = screen;
        screen.setWorldAndResolution(client, 320, 240);
        GuiInventoryNativeState state = new GuiInventoryNativeState(client, screen);
        GuiInventoryProfile profile = new GuiInventoryProfile("inventory-native-test", "test:moved", null, 0, 200, 200,
                Collections.singletonMap("hotbar", new Position(12, 178)), Collections.emptyMap(), "panel", null, true,
                true, Rect.EMPTY);
        GuiInventoryLayout layout = state.propose(profile);
        Slot first = container.getSlot(36);
        player.inventory.mainInventory[0] = new ItemStack(Block.dirt, 9);
        ItemStack original = first.getStack();
        state.install(layout);
        require(state.matches() && container.windowId == 17 && container.getSlot(36) == first
                && first.getStack() == original, "Presentation installation changed transaction identity or stacks");
        Method hit = GuiContainer.class.getDeclaredMethod("getSlotAtPosition", int.class, int.class);
        hit.setAccessible(true);
        Rect panel = layout.panel(320, 240);
        require(hit.invoke(screen, panel.getLeft() + 12, panel.getTop() + 178) == first,
                "Native hit testing disagrees with the displayed slot");
        require(hit.invoke(screen, panel.getLeft() + 8, panel.getTop() + 142) == null,
                "Native hit testing retained the old hotbar position");

        click(client, 36, 1, false);
        require(player.inventory.getItemStack().stackSize == 5 && first.getStack().stackSize == 4,
                "Right click did not split the native stack");
        click(client, 37, 1, false);
        require(container.getSlot(37).getStack().stackSize == 1 && player.inventory.getItemStack().stackSize == 4,
                "Right placement did not retain the native cursor stack");
        click(client, 36, 0, false);
        require(player.inventory.getItemStack() == null && first.getStack().stackSize == 8,
                "Left placement did not merge the native stack");
        click(client, 36, 0, true);
        require(first.getStack() == null && container.getSlot(9).getStack().stackSize == 8,
                "Shift click did not use the native storage transfer");

        player.inventory.setItemStack(new ItemStack(Block.dirt, 1));
        click(client, 5, 0, false);
        require(container.getSlot(5).getStack() == null && player.inventory.getItemStack() != null,
                "Presentation bypassed native armor validation");
        player.inventory.setItemStack(new ItemStack(Item.helmetSteel, 1));
        click(client, 5, 0, false);
        require(player.inventory.getItemStack() == null
                && container.getSlot(5).getStack().itemID == Item.helmetSteel.shiftedIndex,
                "A valid helmet could not enter the native armor slot");

        container.craftMatrix.setInventorySlotContents(0, new ItemStack(Block.wood, 1));
        require(container.craftResult.getStackInSlot(0).itemID == Block.planks.blockID,
                "Native crafting recipe publication changed");
        click(client, 0, 0, false);
        require(player.inventory.getItemStack().stackSize == 4 && container.craftMatrix.getStackInSlot(0) == null,
                "Native crafting did not consume exactly one ingredient");
        click(client, -999, 1, false);
        require(player.dropped == 1 && player.inventory.getItemStack().stackSize == 3,
                "Native outside right drop changed");
        require(state.restore() && container.windowId == 17 && player.dropped == 1
                && player.inventory.getItemStack().stackSize == 3,
                "Restoring presentation changed cursor contents or dropped items");

        state.install(layout);
        first.xDisplayPosition++;
        require(!state.matches(), "Foreign geometry edits were not detected");
        require(!state.restore() && first.xDisplayPosition == 13 && first.yDisplayPosition == 142,
                "Restoration overwrote a foreign edit or retained an owned coordinate");
        screen.onGuiClosed();
        require(player.dropped == 4 && player.inventory.getItemStack() == null,
                "Native close must dispose the cursor exactly once");
        require(!GuiInventoryRuntime.supported(new CustomInventory(player)),
                "Modded inventory subclasses must retain their own presentation");
        NativeClientScreenHost host = new NativeClientScreenHost(client, screen, "inventory-native-test");
        host.close();
        host.close();
        Field hostScreen = NativeClientScreenHost.class.getDeclaredField("screen");
        hostScreen.setAccessible(true);
        require(hostScreen.get(host) == null && !host.session().active(),
                "Expired client resource host retained its inventory screen");
    }

    private static void click(Minecraft client, int slot, int button, boolean shift) {
        client.playerController.func_27174_a(17, slot, button, shift, client.thePlayer);
    }

    public static EntityPlayerSP player(World world) throws Exception {
        require(Block.stone.blockID > 0, "Initialize native blocks before crafting");
        Player player = allocate(Player.class);
        player.inventory = new InventoryPlayer(player);
        player.inventorySlots = new ContainerPlayer(player.inventory, true);
        player.craftingInventory = player.inventorySlots;
        player.worldObj = world;
        player.health = 20;
        return player;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class CustomInventory extends GuiInventory {
        CustomInventory(Player player) {
            super(player);
        }
    }

    private static final class Player extends EntityPlayerSP {
        int dropped;

        private Player() {
            super(null, null, null, 0);
        }

        public void addStat(StatBase stat, int amount) {
        }

        public void dropPlayerItem(ItemStack stack) {
            dropped += stack.stackSize;
        }
    }

    private static final class Client extends Minecraft {
        private Client() {
            super(null, null, null, 320, 240, false);
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
        }
    }
}
