package betamoon.interop.runtime;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.teleport.TeleportRuntime;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Entity;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ModLoader;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

/**
 * Captures actual client identities for explicitly audited callback boundaries.
 */
public final class NativeEventAccess {
    private NativeEventAccess() {
    }

    public static NativeContext open(String owner, LuaCallbackScope callback, Minecraft client, World world,
            EntityPlayer player) {
        NativeEntry entry = entry(callback, client, world, player);
        return entry == null ? null : entry.open(owner);
    }

    public static void configure(LuaCallbackScope callback, World world, Object subject) {
        NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        if (bindings == null || !bindings.onClientThread()) {
            return;
        }
        callback.nativeEntry(entry(callback, ModLoader.getMinecraftInstance(), world, subject));
    }

    public static NativeEntry interfaceEntry(LuaCallbackScope callback) {
        Minecraft client = ModLoader.getMinecraftInstance();
        NativeEntry entry = client == null ? null : entry(callback, client, client.theWorld, null);
        return entry == null ? null : entry.asInterface();
    }

    public static NativeEntry entry(LuaCallbackScope callback, Minecraft client, World world, Object subject) {
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
            if (currentPlayer != null && currentPlayer.worldObj == world) {
                roots.put("player", currentPlayer);
            }
            if (subject != null && subjectMatches(subject, world)) {
                roots.put("subject", subject);
            }
        }
        if (currentScreen != null) {
            roots.put("screen", currentScreen);
        }
        boolean mutable = callback.isMutable() && world != null && !world.multiplayerWorld && currentWorld == world
                && !TeleportRuntime.frozen();
        NativeEntry.Phase phase = callback.isBinding()
                ? NativeEntry.Phase.BINDING
                : world == null
                        ? NativeEntry.Phase.CLIENT
                        : mutable ? NativeEntry.Phase.GAMEPLAY : NativeEntry.Phase.QUERY;
        boolean hasSubject = roots.containsKey("subject");
        return new NativeEntry(bindings, callback, phase, mutable, roots,
                () -> client.theWorld == currentWorld && client.thePlayer == currentPlayer
                        && client.currentScreen == currentScreen && (!mutable || !TeleportRuntime.frozen())
                        && (!hasSubject || subjectMatches(subject, world)));
    }

    private static boolean subjectMatches(Object subject, World world) {
        if (subject instanceof Entity) {
            return ((Entity) subject).worldObj == world;
        }
        if (subject instanceof TileEntity) {
            return ((TileEntity) subject).worldObj == world;
        }
        return subject == null;
    }
}
