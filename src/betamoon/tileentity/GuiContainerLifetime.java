package betamoon.tileentity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;

/**
 * One actual native container identity, shared by legacy input and retained
 * documents.
 */
final class GuiContainerLifetime {
    private final GuiLuaContainer screen;
    private final Minecraft client;
    private final LuaTileEntity tile;
    private final LuaContainer backend;
    private final ContainerGuiDefinition definition;
    private final EntityPlayer player;
    private final World world;
    private final List<Object> slots;
    private final int window;
    private final int x;
    private final int y;
    private final int z;
    private boolean closed;

    GuiContainerLifetime(GuiLuaContainer screen, Minecraft client, LuaTileEntity tile, LuaContainer backend,
            ContainerGuiDefinition definition, EntityPlayer player) {
        this.screen = screen;
        this.client = client;
        this.tile = tile;
        this.backend = backend;
        this.definition = definition;
        this.player = player;
        world = tile.worldObj;
        slots = new ArrayList<>(backend.slots);
        window = backend.windowId;
        x = tile.xCoord;
        y = tile.yCoord;
        z = tile.zCoord;
    }

    boolean available() {
        if (closed || !backend.controls().isActive()) {
            return false;
        }
        // Java-only legacy previews do not own a live player/container window.
        if (player == null) {
            return true;
        }
        if (client.currentScreen != screen || screen.inventorySlots != backend || client.theWorld != world
                || client.thePlayer != player || player.worldObj != world || player.health <= 0 || player.isDead
                || player.craftingInventory != backend || backend.windowId != window
                || backend.slots.size() != slots.size() || tile.worldObj != world || tile.xCoord != x
                || tile.yCoord != y || tile.zCoord != z) {
            return false;
        }
        for (int index = 0; index < slots.size(); index++) {
            if (backend.slots.get(index) != slots.get(index)) {
                return false;
            }
        }
        ContainerDefinition container = definition.container;
        return TileEntityRegistry.getContainer(container.name) == container
                && TileEntityRegistry.getTileEntity(container.tileEntity.name) == container.tileEntity
                && TileEntityRegistry.getGui(definition.name) != null && world != null
                && player.getDistanceSq(x + .5, y + .5, z + .5) <= 64 && world.blockExists(x, y, z)
                && world.getBlockTileEntity(x, y, z) == tile;
    }

    boolean ownsBackend() {
        return player != null && client.thePlayer == player && player.craftingInventory == backend
                && backend.windowId == window && client.theWorld == world && player.worldObj == world;
    }

    boolean preview() {
        return player == null;
    }

    boolean worldAvailable() {
        return available() && !preview() && !world.multiplayerWorld;
    }

    void close() {
        closed = true;
    }
}
