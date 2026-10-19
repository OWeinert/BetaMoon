package betamoon.dimension;

import betamoon.BetaMoonClient;
import betamoon.BetaMoonCommon;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.dimension.storage.DimensionSaveFlush;
import betamoon.luaapi.item.ItemUseHandler;
import betamoon.system.WorldServiceRuntime;
import betamoon.teleport.ChunkPreparation;
import betamoon.teleport.DimensionCleanupTask;
import betamoon.teleport.TeleportDiagnostics;
import betamoon.teleport.TeleportJournal;
import betamoon.teleport.TeleportLocation;
import betamoon.teleport.TeleportRuntime;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.World;

/**
 * Preserves the native portal algorithm while journaling its unpredictable
 * final destination.
 */
public final class DimensionNativePortal {
    private static boolean active;

    private DimensionNativePortal() {
    }

    public static boolean active() {
        return active;
    }

    public static int dispatch(Minecraft minecraft) {
        if (active) {
            return 0;
        }
        if (TeleportRuntime.busy() || DimensionIdentity.isCustom(minecraft.theWorld)) {
            return 1;
        }
        DimensionRuntime.WorldBinding binding = DimensionRuntime.binding(minecraft.theWorld);
        if (binding == null || minecraft.theWorld.multiplayerWorld) {
            return 0;
        }
        active = true;
        String id = UUID.randomUUID().toString();
        DimensionKey source = DimensionIdentity.key(minecraft.theWorld);
        DimensionKey target = source.equals(DimensionKey.NETHER) ? DimensionKey.OVERWORLD : DimensionKey.NETHER;
        TeleportDiagnostics.begin(id, "betamoon:native_portal", source.toString(), target.toString(), "native_portal");
        try {
            transfer(minecraft, binding.session, id);
            terminal(id, true);
        } catch (Throwable failure) {
            terminal(id, false);
            BetaMoonCommon.LOGGER.warning("Native portal requires recovery: " + failure.toString());
            TeleportRuntime.recover(minecraft, "The native portal could not finish safely. " + failure.getMessage());
        } finally {
            active = false;
        }
        return 1;
    }

    private static void terminal(String id, boolean success) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("state", success ? "succeeded" : "failed");
        result.put("success", success);
        if (!success) {
            result.put("reason", "recovery_required");
        }
        TeleportDiagnostics.finish(id, result);
    }

    private static void transfer(Minecraft minecraft, DimensionSession session, String id) throws IOException {
        World sourceWorld = minecraft.theWorld;
        EntityPlayer player = minecraft.thePlayer;
        TeleportLocation departure = TeleportLocation.capture(player);
        TeleportDiagnostics.stage(id, "preparing", "saving_source", 0, 0, 0);
        WorldServiceRuntime.prepareToLeave(sourceWorld);
        DimensionSaveFlush.flush(sourceWorld, session);
        NBTTagCompound source = new NBTTagCompound();
        player.writeToNBT(source);
        session.savePlayerSnapshot(source);
        DimensionKey targetKey = departure.dimension.equals(DimensionKey.NETHER)
                ? DimensionKey.OVERWORLD
                : DimensionKey.NETHER;
        if (session.navigation.hasCleanup(targetKey.toString())) {
            World target = DimensionRuntime.create(session, targetKey);
            DimensionCleanupTask cleanup = new DimensionCleanupTask(target, session, new ChunkPreparation(target, 81));
            int steps = 0;
            while (!cleanup.step()) {
                if (++steps > 1200) {
                    throw new IOException("Native portal destination cleanup exceeded its work budget");
                }
            }
            DimensionRuntime.releaseInactive(target);
        }
        NBTTagCompound pending = DimensionNbt.copy(source, TeleportJournal.PLAYER_BYTES);
        session.navigation.stamp(pending, session.navigation.revision() + 1, id);
        // The final native portal position is unknown until its own search/build
        // algorithm finishes.
        // Until then only the durable source can be selected during recovery.
        session.navigation.begin(id, "betamoon:native_portal", "default", "none", null, departure, source, pending,
                new NBTTagList());
        TeleportDiagnostics.stage(id, "committing", "native_portal", 0, 0, 0);
        minecraft.usePortal();
        if (minecraft.thePlayer != player || minecraft.theWorld == null || minecraft.theWorld == sourceWorld
                || player.worldObj != minecraft.theWorld || !DimensionIdentity.key(minecraft.theWorld).isNative()) {
            throw new IOException("Native portal did not finish with a coherent native player/world");
        }
        ItemUseHandler.worldChanged(player, sourceWorld, minecraft.theWorld);
        DimensionPlayerState.read(player, source);
        NBTTagCompound target = new NBTTagCompound();
        player.writeToNBT(target);
        session.navigation.stamp(target, session.navigation.revision() + 1, id);
        session.navigation.replacePendingTarget(id, target);
        DimensionPlayerState.read(player, target);
        TeleportDiagnostics.stage(id, "committing", "saving_target", 0, 0, 0);
        DimensionSaveFlush.flush(minecraft.theWorld, session);
        session.savePlayerSnapshot(target);
        session.navigation.complete(id);
        DimensionRuntime.activate(minecraft.theWorld);
        DimensionTransition.mark(minecraft.theWorld, "native_portal", id);
        active = false;
        BetaMoonClient client = BetaMoonClient.getInstance();
        if (client != null) {
            try {
                client.synchronizeTickLifecycle(minecraft);
            } catch (Throwable notificationFailure) {
                BetaMoonCommon.LOGGER
                        .warning("Native portal lifecycle notification failed: " + notificationFailure.toString());
            }
        }
    }
}
