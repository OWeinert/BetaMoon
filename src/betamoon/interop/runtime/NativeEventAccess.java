package betamoon.interop.runtime;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.teleport.TeleportRuntime;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.World;

/**
 * First audited native entry: the existing public event dispatch boundary.
 * Other callbacks default closed.
 */
public final class NativeEventAccess {
    private NativeEventAccess() {
    }

    public static NativeContext open(String owner, LuaCallbackScope callback, Minecraft client, World world,
            EntityPlayer player) {
        NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        if (bindings == null || client == null || !bindings.onClientThread()) {
            return null;
        }
        World currentWorld = client.theWorld;
        EntityPlayer currentPlayer = client.thePlayer;
        GuiScreen currentScreen = client.currentScreen;
        Map<String, Object> roots = new HashMap<>();
        roots.put("client", client);
        if (world != null && currentWorld == world) {
            roots.put("world", world);
            if (player != null && player.worldObj == world && currentPlayer == player) {
                roots.put("player", player);
                roots.put("subject", player);
            }
        }
        if (currentScreen != null) {
            roots.put("screen", currentScreen);
        }
        boolean mutable = callback.isMutable() && world != null && !world.multiplayerWorld && currentWorld == world
                && !TeleportRuntime.frozen();
        return NativeContext.open(bindings, owner, callback, mutable, roots,
                () -> client.theWorld == currentWorld && client.thePlayer == currentPlayer
                        && client.currentScreen == currentScreen && (!mutable || !TeleportRuntime.frozen()));
    }
}
