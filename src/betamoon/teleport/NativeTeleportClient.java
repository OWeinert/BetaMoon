package betamoon.teleport;

import betamoon.BetaMoonClient;
import betamoon.BetaMoonCommon;
import betamoon.dimension.DimensionPlayerState;
import betamoon.dimension.DimensionQueries;
import betamoon.dimension.DimensionSession;
import betamoon.event.Events;
import betamoon.event.context.TeleportEventCtx;
import betamoon.gui.GuiScreenSuspension;
import betamoon.gui.GuiTeleportProgress;
import java.io.IOException;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagDouble;
import net.minecraft.src.NBTTagFloat;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.World;

/**
 * Checked adaptation of native rebinding, retaining controller/render/effect
 * setup and the original player.
 */
final class NativeTeleportClient implements TeleportClientAccess {
    private final Minecraft minecraft;
    private GuiScreenSuspension screen;
    private DimensionSession session;
    private GuiTeleportProgress loading;

    NativeTeleportClient(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public World world() {
        return minecraft.theWorld;
    }

    public EntityPlayer player() {
        return minecraft.thePlayer;
    }

    public void beginPreparation(TeleportTicket ticket, String destinationName) {
        try {
            session = DimensionQueries.session(minecraft.theWorld);
        } catch (IOException exception) {
            throw new IllegalStateException("save_failed: root save is unavailable", exception);
        }
        loading = new GuiTeleportProgress(ticket, destinationName);
        screen = new GuiScreenSuspension(minecraft, loading);
    }

    public IProgressUpdate loadingProgress() {
        return loading;
    }

    public void showPreparation(String stage, int percent) {
        if (loading != null) {
            loading.showStage(stage, percent);
        }
    }

    public void closeContainer() {
        if (screen != null) {
            screen.closeSuspended();
        }
    }

    public void finishPreparation(boolean success) {
        if (screen != null) {
            screen.finish(success);
            screen = null;
        }
        loading = null;
    }

    public void switchWorld(World destination, EntityPlayer player, NBTTagCompound target) {
        boolean crossDimension = minecraft.theWorld != destination;
        if (crossDimension) {
            minecraft.theWorld.setEntityDead(player);
            player.isDead = false;
        }
        player.setWorld(destination);
        player.dimension = destination.worldProvider.worldType;
        NBTTagList position = target.getTagList("Pos");
        NBTTagList rotation = target.getTagList("Rotation");
        player.setPositionAndRotation(((NBTTagDouble) position.tagAt(0)).doubleValue,
                ((NBTTagDouble) position.tagAt(1)).doubleValue, ((NBTTagDouble) position.tagAt(2)).doubleValue,
                ((NBTTagFloat) rotation.tagAt(0)).floatValue, ((NBTTagFloat) rotation.tagAt(1)).floatValue);
        if (crossDimension) {
            destination.getWorldInfo().setPlayerNBTTagCompound(null);
            try (TeleportRuntime.NativeSwitch ignored = TeleportRuntime.beginNativeSwitch()) {
                minecraft.changeWorld(destination, loading == null ? "Entering dimension" : loading.getTitle(), player);
            }
        }
        // Apply once after native controller setup, which can otherwise change
        // inventory/position.
        player.readFromNBT(target);
        NBTTagList motion = target.getTagList("Motion");
        player.motionX = ((NBTTagDouble) motion.tagAt(0)).doubleValue;
        player.motionY = ((NBTTagDouble) motion.tagAt(1)).doubleValue;
        player.motionZ = ((NBTTagDouble) motion.tagAt(2)).doubleValue;
        player.timeUntilPortal = 10;
        DimensionPlayerState.clearPortalCharge(player);
        destination.updateEntityWithOptionalForce(player, false);
        minecraft.renderViewEntity = minecraft.thePlayer;
    }

    public EntityPlayer createRespawnPlayer(World destination) {
        EntityPlayer replacement = minecraft.playerController.createPlayer(destination);
        replacement.entityId = minecraft.thePlayer.entityId;
        return replacement;
    }

    public void prepareRespawnPlayer(EntityPlayer replacement, TeleportLocation arrival) {
        TeleportClientAccess.super.prepareRespawnPlayer(replacement, arrival);
        minecraft.playerController.flipPlayer(replacement);
    }

    public void switchRespawnPlayer(World destination, EntityPlayer replacement, NBTTagCompound target) {
        World source = minecraft.theWorld;
        source.setEntityDead(minecraft.thePlayer);
        minecraft.renderViewEntity = null;
        minecraft.thePlayer = (EntityPlayerSP) replacement;
        switchWorld(destination, replacement, target);
        if (source == destination) {
            destination.entityJoinedWorld(replacement);
            minecraft.playerController.func_6473_b(replacement);
        }
        replacement.func_6420_o();
    }

    public void publish(Map<String, Object> result) {
        BetaMoonClient client = BetaMoonClient.getInstance();
        if (client != null) {
            client.synchronizeTickLifecycle(minecraft);
        }
        TeleportEventCtx event = new TeleportEventCtx(minecraft, result);
        if (Boolean.TRUE.equals(result.get("success"))) {
            if (result.get("fallbackReason") != null && minecraft.thePlayer != null) {
                minecraft.thePlayer.addChatMessage("Respawn destination unavailable (" + result.get("fallbackReason")
                        + "). Used a safe fallback.");
            }
            Events.TELEPORT_COMPLETED.publish(event);
        } else {
            Events.TELEPORT_FAILED.publish(event);
        }
    }

    public void recovery(String detail) {
        if (screen != null) {
            screen.abandonForRecovery();
            screen = null;
        }
        loading = null;
        if (session == null && minecraft.theWorld != null) {
            try {
                session = DimensionQueries.session(minecraft.theWorld);
            } catch (IOException failure) {
                BetaMoonCommon.LOGGER.warning("Recovery could not inspect the root session: " + failure.toString());
            }
        }
        try (TeleportRuntime.NativeSwitch ignored = TeleportRuntime.beginNativeSwitch()) {
            minecraft.changeWorld(null, "Returning to menu", null);
        }
        String folder = session == null ? null : session.paths.root().getFileName().toString();
        SavedDimensionLoad.recoveryScreen(minecraft, folder, folder, detail, session == null ? null : session.root);
    }
}
