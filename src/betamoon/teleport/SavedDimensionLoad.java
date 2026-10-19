package betamoon.teleport;

import betamoon.BetaMoonClient;
import betamoon.BetaMoonCommon;
import betamoon.dimension.DimensionRuntime;
import betamoon.dimension.DimensionSession;
import betamoon.dimension.DimensionTransition;
import betamoon.dimension.SavedDimensionInfo;
import betamoon.dimension.storage.DimensionRescueBackup;
import betamoon.gui.GuiDimensionRecovery;
import betamoon.gui.GuiDimensionRescueConfirmation;
import betamoon.gui.GuiTeleportProgress;
import betamoon.worldgen.creation.WorldGeneratorFallback;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.GuiSelectWorld;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.PlayerControllerSP;
import net.minecraft.src.World;

/**
 * Initial custom loads progress on logical menu ticks instead of native
 * synchronous spawn prewarming.
 */
public final class SavedDimensionLoad {
    private static Load active;

    private SavedDimensionLoad() {
    }

    public static int start(Minecraft minecraft, String folder, String name) {
        try {
            Path saves = new File(Minecraft.getMinecraftDir(), "saves").toPath().toAbsolutePath().normalize();
            Path directory = saves.resolve(folder).normalize();
            if (!directory.startsWith(saves) || directory.equals(saves)) {
                throw new IOException("Invalid world save folder");
            }
            if (!Files.isDirectory(directory)) {
                return 0;
            }
            SavedDimensionInfo saved = SavedDimensionInfo.inspect(directory);
            if (saved == null || !saved.deferred) {
                return 0;
            }
            minecraft.changeWorld1(null);
            ISaveHandler root = minecraft.getSaveLoader().getSaveLoader(folder, false);
            WorldGeneratorFallback.retainFor(root);
            DimensionSession session = DimensionSession.open(root, saved.worldInfo);
            active = new Load(minecraft, folder, name, saved, session, false);
            minecraft.displayGuiScreen(new GuiTeleportProgress(active.ticket, name, "Loading"));
        } catch (IOException | RuntimeException failure) {
            showRecovery(minecraft, folder, name, failure);
        }
        return 1;
    }

    public static boolean active() {
        return active != null;
    }

    public static int suppressScreenClose(Minecraft minecraft, GuiScreen requested) {
        return requested == null && (active != null || minecraft.currentScreen instanceof GuiDimensionRecovery) ? 1 : 0;
    }

    public static void tick(Minecraft minecraft) {
        Load load = active;
        if (load == null || load.minecraft != minecraft) {
            return;
        }
        try {
            if (load.ticket.cancellationRequested()) {
                load.release();
                load.ticket.finish("cancelled", null, null, false, false);
                active = null;
                minecraft.displayGuiScreen(load.parent);
                return;
            }
            if (minecraft.playerController == null) {
                minecraft.playerController = new PlayerControllerSP(minecraft);
            }
            World destination;
            EntityPlayerSP player;
            NBTTagCompound target;
            if (load.backup != null) {
                load.ticks++;
                if (load.rescue == null) {
                    load.ticket.update("preparing", "backup", load.ticks, 0, 0);
                    if (!load.backup.step()) {
                        return;
                    }
                    load.rescue = new DimensionRescueTask(load.session, load.saved,
                            world -> minecraft.playerController.createPlayer(world));
                }
                load.ticket.update("preparing", "destination", load.ticks, 0, 0);
                if (!load.rescue.step()) {
                    return;
                }
                load.ticket.update("committing", "saving", load.ticks, 0, 0);
                load.rescue.beginCommit();
                destination = load.rescue.world();
                player = (EntityPlayerSP) load.rescue.player();
                target = load.rescue.target();
            } else {
                boolean ready = advanceRestore(load.task, load.ticket, ++load.ticks);
                if (!ready) {
                    if (minecraft.currentScreen instanceof GuiTeleportProgress) {
                        ((GuiTeleportProgress) minecraft.currentScreen).showStage(load.task.stage(),
                                load.task.progressPercent());
                    }
                    return;
                }
                destination = load.task.world();
                player = (EntityPlayerSP) minecraft.playerController.createPlayer(destination);
                target = load.saved.player;
                load.ticket.update("committing", "switching", load.ticks, load.task.acquiredChunks(), 0);
            }
            // Never use the native spawn reader, which catches malformed player data and
            // continues.
            player.readFromNBT(target);
            player.dimension = destination.worldProvider.worldType;
            destination.getWorldInfo().setPlayerNBTTagCompound(null);
            minecraft.thePlayer = player;
            try (TeleportRuntime.NativeSwitch ignored = TeleportRuntime.beginNativeSwitch()) {
                minecraft.changeWorld(destination, "Loading dimension", player);
            }
            player.readFromNBT(target);
            if (load.rescue == null) {
                load.session.saveRootPlayer(player);
            } else {
                load.rescue.complete();
                DimensionTransition.mark(destination, "rescue", load.ticket.id);
            }
            DimensionRuntime.activate(destination);
            load.committed = true;
            load.ticket.finish(null, null, TeleportLocation.capture(player), load.rescue != null, false,
                    load.rescue == null ? "load" : "rescue", null);
            active = null;
            if (load.rescue != null) {
                load.backup.close();
                player.addChatMessage("Rescued to the Overworld. Backup: " + load.backup.directory());
            }
            minecraft.displayGuiScreen(null);
            BetaMoonClient client = BetaMoonClient.getInstance();
            if (client != null) {
                client.synchronizeTickLifecycle(minecraft);
            }
        } catch (Throwable failure) {
            if (load.committed) {
                BetaMoonCommon.LOGGER.warning("Saved dimension load notification failed: " + failure.toString());
                minecraft.currentScreen = null;
                return;
            }
            load.ticket.finish("load_failed", null, null, false, false);
            try {
                load.release();
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            active = null;
            try (TeleportRuntime.NativeSwitch ignored = TeleportRuntime.beginNativeSwitch()) {
                if (minecraft.theWorld != null) {
                    minecraft.changeWorld(null, "Returning to menu", null);
                }
            }
            recoveryScreen(minecraft, load.folder, load.name,
                    failure.getMessage() == null ? failure.toString() : failure.getMessage(), load.session.root);
        }
    }

    private static void showRecovery(Minecraft minecraft, String folder, String name, Throwable failure) {
        String detail = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        recoveryScreen(minecraft, folder, name, detail);
    }

    private static boolean advanceRestore(DimensionRestoreTask task, TeleportTicket ticket, int ticks)
            throws IOException {
        boolean ready = task.step();
        ticket.update("preparing", task.stage(), ticks, task.acquiredChunks(), 0);
        return ready;
    }

    public static void recoveryScreen(Minecraft minecraft, String folder, String name, String detail) {
        recoveryScreen(minecraft, folder, name, detail, null);
    }

    public static void recoveryScreen(Minecraft minecraft, String folder, String name, String detail,
            ISaveHandler consentRoot) {
        boolean rescuable = false;
        if (folder != null) {
            try {
                Path root = new File(Minecraft.getMinecraftDir(), "saves").toPath().resolve(folder);
                SavedDimensionInfo saved = SavedDimensionInfo.inspect(root);
                if (saved != null) {
                    TeleportJournal navigation = new TeleportJournal(saved.paths);
                    rescuable = !saved.key.isNative() || navigation.pending() || navigation.preparation() != null;
                }
            } catch (IOException | RuntimeException invalidSave) {
                // A diverged player/journal must be restored, never merged or rescued by
                // guessing.
            }
        }
        GuiDimensionRecovery[] warning = new GuiDimensionRecovery[1];
        Runnable resumeRescue = WorldGeneratorFallback.continueFor(consentRoot,
                () -> startRescue(minecraft, folder, name));
        Runnable rescue = rescuable
                ? () -> minecraft.displayGuiScreen(new GuiDimensionRescueConfirmation(warning[0], resumeRescue))
                : null;
        warning[0] = new GuiDimensionRecovery(new GuiSelectWorld(new GuiMainMenu()), detail,
                folder == null
                        ? null
                        : WorldGeneratorFallback.continueFor(consentRoot, () -> minecraft.startWorld(folder, name, 0)),
                rescue);
        minecraft.displayGuiScreen(warning[0]);
    }

    private static void startRescue(Minecraft minecraft, String folder, String name) {
        if (active != null) {
            return;
        }
        try {
            Path directory = new File(Minecraft.getMinecraftDir(), "saves").toPath().resolve(folder).normalize();
            SavedDimensionInfo saved = SavedDimensionInfo.inspect(directory);
            if (saved == null || saved.player == null) {
                throw new IOException("This save no longer requires custom-dimension rescue; retry loading it");
            }
            minecraft.changeWorld1(null);
            ISaveHandler root = minecraft.getSaveLoader().getSaveLoader(folder, false);
            WorldGeneratorFallback.retainFor(root);
            DimensionSession session = DimensionSession.open(root, saved.worldInfo);
            active = new Load(minecraft, folder, name, saved, session, true);
            minecraft.displayGuiScreen(new GuiTeleportProgress(active.ticket, name, "Rescuing player in"));
        } catch (IOException | RuntimeException failure) {
            showRecovery(minecraft, folder, name, failure);
        }
    }

    private static final class Load {
        private final Minecraft minecraft;
        private final String folder;
        private final String name;
        private final SavedDimensionInfo saved;
        private final DimensionSession session;
        private final DimensionRestoreTask task;
        private final DimensionRescueBackup backup;
        private DimensionRescueTask rescue;
        private final TeleportTicket ticket = new TeleportTicket();
        private final GuiScreen parent = new GuiSelectWorld(new GuiMainMenu());
        private int ticks;
        private boolean committed;

        private Load(Minecraft minecraft, String folder, String name, SavedDimensionInfo saved,
                DimensionSession session, boolean rescuing) throws IOException {
            this.minecraft = minecraft;
            this.folder = folder;
            this.name = name;
            this.saved = saved;
            this.session = session;
            TeleportDiagnostics.begin(ticket.id, "betamoon:load", saved.key.toString(),
                    rescuing ? "minecraft:overworld" : saved.key.toString(), rescuing ? "rescue" : "load");
            task = rescuing ? null : new DimensionRestoreTask(session, saved);
            backup = rescuing ? new DimensionRescueBackup(saved.paths) : null;
        }

        private void release() throws IOException {
            if (task != null) {
                task.release();
            }
            if (rescue != null) {
                rescue.release();
            }
            if (backup != null) {
                backup.close();
            }
        }
    }
}
