package betamoon.dimension;

import betamoon.assets.model.ModelJson;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.dimension.storage.DimensionRescueBackup;
import betamoon.dimension.storage.DimensionSaveFlush;
import betamoon.teleport.TeleportJournal;
import betamoon.teleport.ArrivalInspector;
import betamoon.teleport.ChunkPreparation;
import betamoon.teleport.DimensionCleanupTask;
import betamoon.teleport.DimensionRescueTask;
import betamoon.teleport.DimensionRestoreTask;
import betamoon.teleport.TeleportClientAccess;
import betamoon.teleport.TeleportLocation;
import betamoon.teleport.TeleportOptions;
import betamoon.teleport.TeleportOwner;
import betamoon.teleport.TeleportPlatformDefinition;
import betamoon.teleport.TeleportPlatformPlan;
import betamoon.teleport.TeleportService;
import betamoon.teleport.TeleportTicket;
import betamoon.teleport.SavedDimensionLoad;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.HeightProvider;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.PlacementConditions;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Block;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.Container;
import net.minecraft.src.ContainerChest;
import net.minecraft.src.InventoryBasic;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.Session;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import sun.misc.Unsafe;

/**
 * Real world/save/agent boundaries with an isolated client adapter and injected
 * commit failures.
 */
public final class TeleportServiceTest {
    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Initialize native content");
        Field initialized = ModLoader.class.getDeclaredField("hasInit");
        initialized.setAccessible(true);
        initialized.setBoolean(null, true);
        DimensionRuntimeTest.declarations();
        roundTrip();
        successfulContainerClose();
        respawn();
        respawnPolicies();
        restore();
        uncheckedVoidEntry();
        cancellations();
        interruptedCommit();
        durableFailureBoundaries();
        coordinateConversion();
        nativePortals();
        populationFailure();
        rescue();
        System.out
                .println("Teleport service passed: queued reversible transfer, inventory/health/history preservation, "
                        + "cancel/reload/session tokens, unsafe refusal and interrupted-commit recovery.");
    }

    private static void roundTrip() throws Exception {
        Client client = client();
        TeleportService service = new TeleportService(client);
        TeleportOwner owner = new TeleportOwner("test.lua");
        EntityPlayer player = client.player;
        player.health = 14;
        player.air = 144;
        player.fire = 32;
        NBTTagCompound initialPlayer = new NBTTagCompound();
        player.writeToNBT(initialPlayer);
        initialPlayer.setFloat("FallDistance", 5);
        player.readFromNBT(initialPlayer);
        player.inventory.currentItem = 3;
        player.inventory.mainInventory[3] = new ItemStack(Item.diamond, 7);
        World source = client.world;
        TeleportLocation departure = TeleportLocation.capture(player);
        TeleportTicket enter = accepted(
                service.request(player, owner, DimensionKey.parse("test:flat"), options("{}"), null));
        require(client.world == source && enter.state().equals("queued"),
                "Accepting a request cannot replace the active world");
        finish(service, enter);
        require(enter.state().equals("succeeded")
                && DimensionIdentity.key(client.world).equals(DimensionKey.parse("test:flat")),
                "Prepared entry must succeed");
        require(client.player == player && player.health == 14 && player.air == 144 && player.fire == 32
                && player.inventory.currentItem == 3 && player.inventory.mainInventory[3].stackSize == 7
                && player.inventory.mainInventory[3].itemID == Item.diamond.shiftedIndex
                && snapshot(player).getFloat("FallDistance") == 0,
                "Living transfer preserves native state and resets only the requested motion/fall fields");
        DimensionSession session = DimensionQueries.session(client.world);
        NBTTagCompound durable = DimensionNbt.readCompressed(session.paths.resolve("level.dat"), 1024 * 1024)
                .getCompoundTag("Data").getCompoundTag("Player");
        World preparingNative = DimensionRuntime.create(session, DimensionKey.NETHER);
        DimensionSaveFlush.flush(preparingNative, session);
        NBTTagCompound afterEmptyFlush = DimensionNbt.readCompressed(session.paths.resolve("level.dat"), 1024 * 1024)
                .getCompoundTag("Data").getCompoundTag("Player");
        require(DimensionNbt.fingerprint(durable, 512 * 1024)
                .equals(DimensionNbt.fingerprint(afterEmptyFlush, 512 * 1024)),
                "Flushing an empty native destination must never erase or replace the root player");
        require(session.navigation.previous(owner.name, "default") != null && session.navigation.revision() == 1,
                "A successful cross-dimension entry must durably remember the departure once");
        TeleportTicket leave = accepted(service.returnToPrevious(player, owner, options("{}", true), null));
        finish(service, leave);
        require(leave.state().equals("succeeded") && DimensionIdentity.key(client.world).equals(DimensionKey.OVERWORLD)
                && player.posX == departure.x && player.boundingBox.minY == departure.y && player.posZ == departure.z,
                "Return must restore the exact departure and reuse the actual player");
        require(session.navigation.previous(owner.name, "default") == null && session.navigation.revision() == 2,
                "Successful return consumes only its own route and cannot push a reverse route");
        require(client.events.size() == 2 && client.recovery == null && client.closes == 2,
                "One completion notification per committed transfer");
        TeleportTicket unsafe = accepted(service.request(player, owner, DimensionKey.OVERWORLD,
                options("{\"arrival\":{\"type\":\"exact\",\"x\":8.5,\"y\":63,\"z\":8.5},\"loadChunks\":false}"), null));
        int closes = client.closes;
        finish(service, unsafe);
        require(unsafe.result().get("reason").equals("unsafe_destination") && client.closes == closes
                && session.navigation.revision() == 2, "Failed arrival preserves the GUI, history and player revision");
        TeleportTicket callbackError = accepted(service.request(player, owner, DimensionKey.OVERWORLD,
                options("{\"arrival\":{\"type\":\"exact\",\"x\":8.5,\"y\":64,\"z\":8.5},\"loadChunks\":false}"),
                (result, world, current) -> {
                    throw new IllegalStateException("injected completion failure");
                }));
        finish(service, callbackError);
        require(callbackError.state().equals("succeeded") && client.recovery == null && !service.busy(),
                "Callback failure cannot undo a committed transfer or enter recovery");
    }

    private static void restore() throws Exception {
        Client client = client();
        TeleportService service = new TeleportService(client);
        finish(service, accepted(service.request(client.player, new TeleportOwner("test.lua"),
                DimensionKey.parse("test:flat"), options("{}"), null)));
        DimensionSession session = DimensionQueries.session(client.world);
        SavedDimensionInfo saved = SavedDimensionInfo.inspect(session.paths.root());
        require(saved.deferred && saved.key.equals(DimensionKey.parse("test:flat")),
                "Custom save preflight must select the bounded reopen path");
        DimensionRestoreTask restore = new DimensionRestoreTask(session, saved);
        TeleportTicket loading = new TeleportTicket();
        Method advance = SavedDimensionLoad.class.getDeclaredMethod("advanceRestore", DimensionRestoreTask.class,
                TeleportTicket.class, int.class);
        advance.setAccessible(true);
        int previous = 0;
        for (int ticks = 0; !(Boolean) advance.invoke(null, restore, loading, ticks); ticks++) {
            require(loading.progress().get("stage").equals(restore.stage()),
                    "Restore presentation must publish the post-step phase before an intervening GUI frame");
            int acquired = restore.acquiredChunks();
            require(acquired - previous <= 2 && ticks < 100,
                    "Reopening must acquire at most two chunks per logical tick");
            previous = acquired;
        }
        require(DimensionIdentity.key(restore.world()).equals(saved.key)
                && DimensionRuntime.isPreparing(restore.world()),
                "A restored destination must remain detached until player validation and activation");
        NBTTagCompound durable = DimensionNbt.readCompressed(session.paths.resolve("level.dat"), 1024 * 1024)
                .getCompoundTag("Data").getCompoundTag("Player");
        require(DimensionNbt.fingerprint(saved.player, 512 * 1024)
                .equals(DimensionNbt.fingerprint(durable, 512 * 1024)),
                "Preparing a saved world cannot rewrite the saved player's inventory or location");
        restore.release();
    }

    private static void successfulContainerClose() throws Exception {
        for (boolean custom : new boolean[]{false, true}) {
            Client client = client();
            client.nativeContainerClose = true;
            int[] closes = {0};
            client.player.craftingInventory = custom ? new Container() {
                public boolean isUsableByPlayer(EntityPlayer player) {
                    return true;
                }

                public void onCraftGuiClosed(EntityPlayer player) {
                    closes[0]++;
                    super.onCraftGuiClosed(player);
                }
            } : new ContainerChest(client.player.inventory, new InventoryBasic("Test", 27));
            client.player.inventory.setItemStack(new ItemStack(Item.diamond, 3));
            World source = client.world;
            TeleportService service = new TeleportService(client);
            TeleportTicket ticket = accepted(service.request(client.player, new TeleportOwner("container.lua"),
                    DimensionKey.parse("test:flat"), options("{}"), null));
            finish(service, ticket);
            require(ticket.state().equals("succeeded") && client.player.inventory.getItemStack() == null,
                    "Successful transfer resolves the native cursor before copying player state");
            int dropped = 0;
            for (Object entity : source.loadedEntityList) {
                if (entity instanceof EntityItem && ((EntityItem) entity).item.itemID == Item.diamond.shiftedIndex) {
                    dropped += ((EntityItem) entity).item.stackSize;
                }
            }
            require(dropped == 3 && (!custom || closes[0] == 1),
                    "Native and custom containers drop exactly one cursor stack in the source");
            DimensionSession session = DimensionQueries.session(client.world);
            NBTTagCompound durable = DimensionNbt.readCompressed(session.paths.resolve("level.dat"), 1024 * 1024)
                    .getCompoundTag("Data").getCompoundTag("Player");
            require(!durable.hasKey("BetaMoonCursor"), "A committed reopen cannot restore an already-dropped cursor");
        }
    }

    private static void respawn() throws Exception {
        Client client = client();
        TeleportService service = new TeleportService(client);
        TeleportOwner owner = new TeleportOwner("test.lua");
        finish(service,
                accepted(service.request(client.player, owner, DimensionKey.parse("test:flat"), options("{}"), null)));
        DimensionSession session = DimensionQueries.session(client.world);
        EntityPlayer deceased = client.player;
        long oldToken = DimensionPlayerState.runtimeToken(deceased, session);
        // Native death has already dropped/cleared inventory before the respawn button
        // is available.
        deceased.username = "test";
        deceased.inventory.mainInventory[0] = new ItemStack(Item.diamond, 4);
        deceased.health = 0;
        deceased.onDeath(null);
        require(deceased.width < 0.6 && deceased.height < 1,
                "The death fixture must use the native corpse geometry, not a living-player approximation");
        TeleportTicket respawn = accepted(
                service.respawn(deceased, new TeleportOwner("betamoon:native_respawn"), false));
        finish(service, respawn);
        require(respawn.state().equals("succeeded") && respawn.result().get("operation").equals("respawn")
                && DimensionIdentity.key(client.world).equals(DimensionKey.OVERWORLD),
                "Custom default death policy must resolve the real Overworld directly");
        require(client.player != deceased && client.player.health == 20 && !client.player.isDead
                && client.player.inventory.mainInventory[0] == null && client.replacements == 1,
                "Death must use one fresh native replacement without restoring dropped inventory");
        require(DimensionPlayerState.runtimeToken(client.player, session) != oldToken
                && session.navigation.previous(owner.name, "default") != null
                && session.navigation.respawnReturn() != null,
                "Replacement scopes expire while persistent return history and its arrival link survive death");
        require(session.navigation.revision() == 2 && !session.navigation.pending(),
                "Death uses the same durable revision bookkeeping as living transfer");
    }

    private static void respawnPolicies() throws Exception {
        Client local = client();
        TeleportService localService = new TeleportService(local);
        TeleportOwner owner = new TeleportOwner("test.lua");
        finish(localService, accepted(
                localService.request(local.player, owner, DimensionKey.parse("test:local"), options("{}"), null)));
        local.world.setBlockAndMetadata(14, 64, 8, Block.blockBed.blockID, 0);
        local.world.setBlockAndMetadata(14, 64, 9, Block.blockBed.blockID, 8);
        local.player.setPlayerSpawnCoordinate(new ChunkCoordinates(14, 64, 8));
        kill(local.player);
        finish(localService,
                accepted(localService.respawn(local.player, new TeleportOwner("betamoon:native_respawn"), false)));
        require(DimensionIdentity.key(local.world).equals(DimensionKey.parse("test:local"))
                && Math.abs(local.player.posX - 14) < 3
                && DimensionPlayerState.bed(local.player, DimensionKey.parse("test:local")).x == 14,
                "Local respawn must use and preserve its own valid remembered bed");
        require(ArrivalInspector.inspect(local.world, local.player, TeleportLocation.capture(local.player),
                "safe") == null,
                "Respawn must validate the full living replacement body, not the native shrunken corpse");

        Client returning = client();
        TeleportService returns = new TeleportService(returning);
        finish(returns, accepted(
                returns.request(returning.player, owner, DimensionKey.parse("test:cycle_a"), options("{}"), null)));
        finish(returns, accepted(
                returns.request(returning.player, owner, DimensionKey.parse("test:return"), options("{}"), null)));
        kill(returning.player);
        finish(returns,
                accepted(returns.respawn(returning.player, new TeleportOwner("betamoon:native_respawn"), false)));
        require(DimensionIdentity.key(returning.world).equals(DimensionKey.parse("test:cycle_a"))
                && DimensionQueries.session(returning.world).navigation.historyCounts().get("test.lua / default") == 2,
                "Return respawn must use the latest arrival's departure without following its respawn policy or consuming history");
        kill(returning.player);
        finish(returns,
                accepted(returns.respawn(returning.player, new TeleportOwner("betamoon:native_respawn"), false)));
        require(DimensionIdentity.key(returning.world).equals(DimensionKey.parse("test:cycle_b")),
                "A->B and B->A respawn policies must resolve one destination directly without recursion");

        Client missing = client();
        TeleportService missingService = new TeleportService(missing);
        finish(missingService, accepted(missingService.request(missing.player, owner,
                DimensionKey.parse("test:cycle_a"), options("{}"), null)));
        DimensionRegistry.retainOwners(Collections.<String>emptySet());
        kill(missing.player);
        TeleportTicket fallback = accepted(
                missingService.respawn(missing.player, new TeleportOwner("betamoon:native_respawn"), false));
        finish(missingService, fallback);
        require(fallback.state().equals("succeeded") && Boolean.TRUE.equals(fallback.result().get("usedFallback"))
                && DimensionIdentity.key(missing.world).equals(DimensionKey.OVERWORLD),
                "Removing an inactive death destination must preserve the active binding and fall back to the real Overworld");
        DimensionRuntimeTest.declarations();
    }

    private static void kill(EntityPlayer player) {
        player.username = "test";
        player.health = 0;
        player.onDeath(null);
    }

    private static void uncheckedVoidEntry() throws Exception {
        Client client = client();
        TeleportService service = new TeleportService(client);
        TeleportOwner owner = new TeleportOwner("test.lua");
        TeleportTicket ticket = accepted(service.request(client.player, owner, DimensionKey.parse("test:empty"),
                options("{\"safety\":\"unchecked\",\"arrival\":{\"type\":\"exact\",\"x\":5.25,\"y\":200,\"z\":-4.25}}"),
                null));
        finish(service, ticket);
        require(ticket.state().equals("succeeded") && client.player.boundingBox.minY == 200,
                "Deliberate unchecked exact entry must support void worlds and positions above build height");
        DimensionRuntime.WorldBinding binding = DimensionRuntime.binding(client.world);
        require(binding.save.metadata.initialized() && binding.save.metadata.anchorX() == 5
                && binding.save.metadata.anchorZ() == -5 && binding.save.metadata.entryPosition()[1] == 200,
                "Unchecked entry must preserve precise entry and floor-based immutable generation anchors");
        SavedDimensionInfo saved = SavedDimensionInfo.inspect(binding.session.paths.root());
        DimensionRestoreTask restore = new DimensionRestoreTask(binding.session, saved);
        for (int tick = 0; !restore.step(); tick++) {
            require(tick < 100, "Void world reopen must remain bounded");
        }
        require(DimensionIdentity.key(restore.world()).equals(DimensionKey.parse("test:empty")),
                "A deliberately unsafe saved position must reopen its exact dimension without implicit fallback");
        finish(service, accepted(service.returnToPrevious(client.player, owner, options("{}", true), null)));
        require(DimensionIdentity.key(client.world).equals(DimensionKey.OVERWORLD),
                "Unchecked first entry still requires a working exact return route");
    }

    private static void cancellations() throws Exception {
        Client client = client();
        TeleportService service = new TeleportService(client);
        TeleportOwner owner = new TeleportOwner("test.lua");
        World source = client.world;
        TeleportTicket cancelled = accepted(
                service.request(client.player, owner, DimensionKey.parse("test:flat"), options("{}"), null));
        require(cancelled.cancel(), "Queued ticket must accept cancellation");
        finish(service, cancelled);
        require(cancelled.state().equals("cancelled") && client.world == source && client.closes == 0,
                "Queued cancellation cannot close a GUI or switch worlds");
        int[] callbacks = {0};
        TeleportTicket unloaded = accepted(service.request(client.player, owner, DimensionKey.parse("test:flat"),
                options("{}"), (result, world, player) -> callbacks[0]++));
        service.tick();
        owner.close();
        finish(service, unloaded);
        require(unloaded.result().get("reason").equals("owner_unloaded") && callbacks[0] == 0,
                "An unloaded owner cannot receive stale completion callbacks");
        TeleportOwner replacement = new TeleportOwner("test.lua");
        TeleportTicket stalePlayer = accepted(
                service.request(client.player, replacement, DimensionKey.parse("test:flat"), options("{}"), null));
        EntityPlayer original = client.player;
        client.player = player(source);
        client.player.entityId = original.entityId;
        finish(service, stalePlayer);
        require(stalePlayer.result().get("reason").equals("session_changed"),
                "A replacement player cannot impersonate the queued player by reusing its native entity ID");
    }

    private static void interruptedCommit() throws Exception {
        Client client = client();
        TeleportService service = new TeleportService(client);
        client.failAfterSwitch = true;
        int[] callbacks = {0};
        TeleportTicket ticket = accepted(service.request(client.player, new TeleportOwner("test.lua"),
                DimensionKey.parse("test:flat"), options("{}"), (result, world, player) -> {
                    require(world == null && player == null, "Recovery callbacks cannot receive mutable stale actors");
                    callbacks[0]++;
                }));
        finish(service, ticket);
        DimensionSession session = DimensionQueries.session(client.world);
        require(ticket.state().equals("failed") && client.recovery != null && service.frozen()
                && session.navigation.pending(),
                "A failure after runtime switching must retain intent and enter recovery");
        NBTTagCompound saved = DimensionNbt.readCompressed(session.paths.resolve("level.dat"), 1024 * 1024)
                .getCompoundTag("Data").getCompoundTag("Player");
        require(session.navigation.recoverySide(saved).equals("source")
                && session.navigation.previous("test.lua", "default") == null,
                "A pre-completion crash must recover the durable source without consuming history");
        require(callbacks[0] == 1 && client.events.size() == 1,
                "Every accepted request must publish its terminal event and current-owner callback once, including recovery");
    }

    private static void durableFailureBoundaries() throws Exception {
        for (FailureBoundary boundary : FailureBoundary.values()) {
            Client client = client();
            client.player.health = 13;
            client.player.inventory.mainInventory[0] = new ItemStack(Item.coal, 7, 1);
            DimensionSession session = DimensionQueries.session(client.world);
            session.navigation.ensureSaved();
            DimensionSaveFlush.flush(client.world, session);
            session.savePlayerSnapshot(snapshot(client.player));
            client.failureBoundary = boundary;

            TeleportService service = new TeleportService(client);
            int[] completions = {0};
            TeleportTicket ticket = accepted(service.request(client.player, new TeleportOwner("boundary.lua"),
                    DimensionKey.parse("test:flat"), options("{}"), (result, world, player) -> completions[0]++));
            if (boundary == FailureBoundary.CANCEL_SCREEN) {
                ticket.cancel();
            }
            try {
                finish(service, ticket);
                require(client.failureInjected, "Boundary was not reached: " + boundary);
                require(completions[0] == 1 && client.events.size() == 1,
                        "Terminal observers must run once even after a boundary failure: " + boundary);
            } finally {
                client.restoreBlockedFile();
            }

            // Open a new journal and session from disk. Do not trust the failed task's in-memory state.
            boolean targetDurable = boundary == FailureBoundary.COMPLETION
                    || boundary == FailureBoundary.FINISH_SCREEN || boundary == FailureBoundary.PUBLISH;
            SavedDimensionInfo saved = SavedDimensionInfo.inspect(session.paths.root());
            require(saved.key.equals(targetDurable ? DimensionKey.parse("test:flat") : DimensionKey.OVERWORLD),
                    "Reopen selected the wrong durable side: " + boundary);
            Path saveRoot = session.paths.root();
            SaveOldDir reopenedSave = new SaveOldDir(saveRoot.getParent().toFile(),
                    saveRoot.getFileName().toString(), false);
            DimensionSession reopened = DimensionSession.open(reopenedSave, saved.worldInfo);
            DimensionRestoreTask restore = new DimensionRestoreTask(reopened, saved);
            try {
                for (int tick = 0; !restore.step(); tick++) {
                    require(tick < 150, "Boundary recovery did not finish: " + boundary);
                }
                require(!reopened.navigation.pending() && reopened.navigation.revision() == (targetDurable ? 1 : 0),
                        "Recovery must reconcile the durable revision exactly once: " + boundary);
                require((reopened.navigation.previous("boundary.lua", "default") != null) == targetDurable,
                        "Only a durable completed transfer may push return history: " + boundary);
                EntityPlayer restoredPlayer = player(restore.world());
                restoredPlayer.readFromNBT(saved.player);
                ItemStack stack = restoredPlayer.inventory.mainInventory[0];
                require(restoredPlayer.health == 13 && stack != null && stack.itemID == Item.coal.shiftedIndex
                        && stack.stackSize == 7 && stack.getItemDamage() == 1,
                        "Recovery must retain one exact inventory and health snapshot: " + boundary);
                TeleportJournal reopenedAgain = new TeleportJournal(reopened.paths);
                require(!reopenedAgain.pending() && reopenedAgain.revision() == (targetDurable ? 1 : 0),
                        "Reconciliation must itself survive a second reopen: " + boundary);
            } finally {
                restore.release();
            }
        }
    }

    private enum FailureBoundary {
        SOURCE_CHECKPOINT,
        DESTINATION_CHECKPOINT,
        SOURCE_PLAYER,
        INTENT,
        BEFORE_SWITCH,
        AFTER_SWITCH,
        TARGET_PLAYER,
        COMPLETION,
        FINISH_SCREEN,
        CANCEL_SCREEN,
        PUBLISH
    }

    private static void coordinateConversion() {
        TeleportLocation source = new TeleportLocation(DimensionKey.OVERWORLD, -12.25, 72, 3.75, 123, -20);
        TeleportLocation target = TeleportService.mapCoordinates(source, DimensionKey.NETHER, 1, 8);
        TeleportLocation back = TeleportService.mapCoordinates(target, DimensionKey.OVERWORLD, 8, 1);
        require(back.x == source.x && back.z == source.z && back.y == source.y && back.yaw == source.yaw,
                "Scale conversion must preserve negative fractions, Y and orientation without double scaling");
    }

    private static void rescue() throws Exception {
        Client client = client();
        client.player.health = 11;
        client.player.inventory.mainInventory[2] = new ItemStack(Item.diamond, 9);
        TeleportService service = new TeleportService(client);
        finish(service, accepted(service.request(client.player, new TeleportOwner("test.lua"),
                DimensionKey.parse("test:flat"), options("{}"), null)));
        DimensionSession oldSession = DimensionQueries.session(client.world);
        SavedDimensionInfo saved = SavedDimensionInfo.inspect(oldSession.paths.root());
        World unfinished = DimensionRuntime.create(oldSession, DimensionKey.parse("test:local"));
        DimensionRuntime.binding(unfinished).save.metadata.bootstrapAnchor(8, 64, 8);
        ChunkPreparation platformChunks = new ChunkPreparation(unfinished, 25);
        platformChunks.reserve(0, 0, true);
        while (!platformChunks.step()) {
            // The fixture uses the same sliced native preparation as runtime transfers.
        }
        DimensionRuntime.binding(unfinished).save.metadata.initialize(8.5, 64, 8.5, 0, 0);
        TeleportPlatformDefinition platform = new TeleportPlatformDefinition(
                ModelJson.read("{\"block\":1,\"width\":3,\"y\":70}".getBytes(StandardCharsets.UTF_8)), "platform");
        TeleportPlatformPlan undo = TeleportPlatformPlan.plan(unfinished, client.player,
                new TeleportLocation(DimensionKey.parse("test:local"), 8.5, 71, 8.5, 0, 0), platform);
        oldSession.navigation.preparePlatform(UUID.randomUUID().toString(), "test:dimension/local", undo.write());
        undo.apply(unfinished);
        DimensionSaveFlush.flush(unfinished, oldSession);
        byte[] extra = new byte[2500000];
        extra[extra.length - 1] = 73;
        Files.write(saved.paths.resolve("backup-test.bin"), extra);
        String backupDirectory;
        try (DimensionRescueBackup backup = new DimensionRescueBackup(saved.paths)) {
            long previous = 0;
            for (int ticks = 0; !backup.step(); ticks++) {
                require(backup.copiedBytes() - previous <= 2 * 1024 * 1024 && ticks < 300,
                        "Full rescue backups must be bounded per tick");
                previous = backup.copiedBytes();
            }
            backupDirectory = backup.directory();
        }
        require(Files.exists(saved.paths.resolve(backupDirectory + "/backup.complete"))
                && Arrays.equals(extra, Files.readAllBytes(saved.paths.resolve(backupDirectory + "/backup-test.bin"))),
                "Rescue must finish a complete byte-preserving save backup before changing player state");
        DimensionRegistry.retainOwners(Collections.<String>emptySet());
        DimensionSession session = DimensionSession.open(oldSession.root, saved.worldInfo);
        DimensionRescueTask rescue = new DimensionRescueTask(session, saved, TeleportServiceTest::player);
        for (int ticks = 0; !rescue.step(); ticks++) {
            require(ticks < 200, "Rescue must finish bounded real Overworld preparation");
        }
        require(DimensionIdentity.key(rescue.world()).equals(DimensionKey.OVERWORLD),
                "Missing custom definitions cannot prevent explicit evacuation to the real Overworld");
        rescue.beginCommit();
        require(session.navigation.pending(), "Evacuation must be journaled before the runtime switch");
        rescue.player().readFromNBT(rescue.target());
        rescue.complete();
        require(rescue.player().health == 11 && rescue.player().inventory.mainInventory[2].stackSize == 9
                && session.navigation.revision() == 2 && !session.navigation.pending()
                && session.navigation.previous("test.lua", "default") != null,
                "Explicit rescue must preserve one player's inventory, health and return history");
        NBTTagCompound original = DimensionNbt
                .readCompressed(saved.paths.resolve(backupDirectory + "/level.dat"), 1024 * 1024).getCompoundTag("Data")
                .getCompoundTag("Player");
        require(original.getString("BetaMoonDimension").equals("test:dimension/flat"),
                "The backup must retain the original custom location after evacuation commits");
        require(session.navigation.hasCleanup("test:dimension/local"),
                "Rescue with missing definitions must retain the unfinished platform undo record");
        DimensionRuntimeTest.declarations();
        World repaired = DimensionRuntime.create(session, DimensionKey.parse("test:local"));
        DimensionCleanupTask cleanup = new DimensionCleanupTask(repaired, session, new ChunkPreparation(repaired, 81));
        for (int tick = 0; !cleanup.step(); tick++) {
            require(tick < 100, "Restored-dependency cleanup must remain bounded");
        }
        require(repaired.getBlockId(8, 70, 8) == 0 && !session.navigation.hasCleanup("test:dimension/local"),
                "Restoring the dependency must roll back matching platform edits before the dimension can reactivate");
    }

    private static void populationFailure() throws Exception {
        boolean[] armed = {false};
        World[] source = {null};
        int[] marker = {0, 0, 0};
        try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication("population-fault.lua",
                "fault")) {
            WorldGenKey feature = WorldGenRegistry.addFeature("fault:feature/injected", "test_fault",
                    (context, origin, output) -> {
                        if (!armed[0]) {
                            return FeatureResult.rejected(FeatureResult.NO_CHANGES);
                        }
                        marker[0] = origin.x;
                        marker[1] = origin.y;
                        marker[2] = origin.z;
                        source[0].setBlockWithNotify(origin.x, origin.y, origin.z, Block.oreDiamond.blockID);
                        throw new IllegalStateException("injected failure after population wrote a block");
                    }, Collections.<WorldGenKey>emptyList(), 1, 1);
            WorldGenRegistry.addPlacement("fault:placement/injected", feature, GenerationStage.SURFACE_FEATURES,
                    Collections.singleton("test:dimension/flat"), new IntRange(1, 1), 0, 1,
                    HeightProvider.uniform(80, 80),
                    new PlacementConditions(null, false, null, null, null, 0, 15, Collections.<String>emptySet(),
                            Collections.<String>emptySet()),
                    Collections.<WorldGenKey>emptyList(), Collections.<WorldGenKey>emptyList(), 0, 42, 1, "chunk", 1);
            batch.validate();
            batch.publish();
        }
        DimensionRuntimeTest.declarations();
        Client client = client();
        TeleportService service = new TeleportService(client);
        TeleportOwner owner = new TeleportOwner("test.lua");
        finish(service,
                accepted(service.request(client.player, owner, DimensionKey.parse("test:flat"), options("{}"), null)));
        source[0] = client.world;
        client.player.inventory.setItemStack(new ItemStack(Item.diamond, 5));
        armed[0] = true;
        TeleportTicket ticket = accepted(service.request(client.player, owner, DimensionKey.parse("test:flat"),
                options("{\"arrival\":{\"type\":\"near\",\"x\":520.5,\"y\":64,\"z\":8.5}}"), null));
        finish(service, ticket);
        require(ticket.state().equals("failed") && service.frozen() && client.recovery != null,
                "A contained feature exception after terrain mutation must freeze into durable recovery");
        DimensionSession session = DimensionQueries.session(client.world);
        SavedDimensionInfo saved = SavedDimensionInfo.inspect(session.paths.root());
        DimensionRestoreTask restore = new DimensionRestoreTask(session, saved);
        for (int tick = 0; !restore.step(); tick++) {
            require(tick < 100, "Checkpoint restore must stay bounded");
        }
        EntityPlayer restored = player(restore.world());
        restored.readFromNBT(saved.player);
        require(restored.inventory.getItemStack() != null && restored.inventory.getItemStack().stackSize == 5,
                "Recovery must preserve the source container's cursor stack without merging inventories");
        require(restore.world().getBlockId(marker[0], marker[1], marker[2]) != Block.oreDiamond.blockID,
                "An interrupted population write must never replace the saved pre-population terrain checkpoint");
        WorldGenRegistry.retainOwners(Collections.<String>emptySet());
        DimensionRuntimeTest.declarations();
    }

    private static void nativePortals() throws Exception {
        Client fixture = client();
        Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        PortalMinecraft minecraft = (PortalMinecraft) ((Unsafe) unsafe.get(null))
                .allocateInstance(PortalMinecraft.class);
        fixture.world.setEntityDead(fixture.player);
        EntityPlayerSP player = new EntityPlayerSP(minecraft, fixture.world, new Session("test", "test"), 0);
        player.setLocationAndAngles(8.5, 64, 8.5, 90, 0);
        player.inventory.mainInventory[0] = new ItemStack(Item.diamond, 3);
        fixture.world.entityJoinedWorld(player);
        minecraft.theWorld = fixture.world;
        minecraft.thePlayer = player;
        DimensionSession session = DimensionQueries.session(fixture.world);
        minecraft.usePortal();
        require(minecraft.thePlayer == player && DimensionIdentity.key(minecraft.theWorld).equals(DimensionKey.NETHER)
                && session.navigation.revision() == 1 && !session.navigation.pending(),
                "The original native portal algorithm must retain its player and commit canonical Nether bookkeeping");
        NBTTagCompound nether = DimensionNbt.readCompressed(session.paths.resolve("level.dat"), 1024 * 1024)
                .getCompoundTag("Data").getCompoundTag("Player");
        require(nether.getString("BetaMoonDimension").equals("minecraft:nether")
                && nether.getInteger("Dimension") == -1,
                "Native portal saves must not retain stale source coordinates/identity");
        minecraft.usePortal();
        require(DimensionIdentity.key(minecraft.theWorld).equals(DimensionKey.OVERWORLD)
                && player.inventory.mainInventory[0].stackSize == 3 && session.navigation.revision() == 2
                && session.navigation.historyCounts().isEmpty(),
                "Native return portal search/build must remain independent of script return history");
    }

    /**
     * Runs the original portal/Teleporter code; only renderer/stat-screen rebinding
     * is replaced for headless tests.
     */
    private static final class PortalMinecraft extends Minecraft {
        private PortalMinecraft() {
            super(null, null, null, 320, 240, false);
        }

        public void changeWorld(World destination, String text, EntityPlayer player) {
            theWorld = destination;
            if (destination != null) {
                player.setWorld(destination);
                destination.getWorldInfo().setPlayerNBTTagCompound(null);
                destination.entityJoinedWorld(player);
            } else {
                thePlayer = null;
            }
        }

        public void displayGuiScreen(GuiScreen screen) {
            currentScreen = screen;
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable failure) {
            throw new AssertionError(failure);
        }
    }

    private static Client client() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-teleport-service-");
        SaveOldDir save = new SaveOldDir(directory.toFile(), "world", false);
        WorldInfo info = new WorldInfo(42, "world");
        info.setSpawn(8, 64, 8);
        save.saveWorldInfo(info);
        World world = new World(save, "world", 42, null);
        DimensionRuntime.activate(world);
        ChunkPreparation preparation = new ChunkPreparation(world, 25);
        preparation.reserve(0, 0, true);
        for (int tick = 0; !preparation.step(); tick++) {
            require(tick < 40, "Native fixture preparation must complete");
        }
        world.setBlockWithNotify(8, 63, 8, Block.stone.blockID);
        world.setBlockWithNotify(8, 64, 8, 0);
        world.setBlockWithNotify(8, 65, 8, 0);
        EntityPlayer player = player(world);
        player.setLocationAndAngles(8.5, 64, 8.5, 90, 0);
        world.entityJoinedWorld(player);
        return new Client(world, player);
    }

    private static NBTTagCompound snapshot(EntityPlayer player) {
        NBTTagCompound tag = new NBTTagCompound();
        player.writeToNBT(tag);
        return tag;
    }

    private static EntityPlayer player(World world) {
        return new EntityPlayer(world) {
            public void func_6420_o() {
            }
        };
    }

    private static TeleportTicket accepted(TeleportService.Acceptance acceptance) {
        require(acceptance.ticket != null, "Unexpected request refusal: " + acceptance.reason);
        return acceptance.ticket;
    }

    private static void finish(TeleportService service, TeleportTicket ticket) {
        for (int tick = 0; ticket.result() == null; tick++) {
            require(tick < 250, "Accepted request did not reach a terminal state");
            service.tick();
        }
    }

    private static TeleportOptions options(String json) throws Exception {
        return options(json, false);
    }

    private static TeleportOptions options(String json, boolean returning) throws Exception {
        Map<String, Object> values = ModelJson.read(json.getBytes(StandardCharsets.UTF_8));
        values.put("cooldownTicks", 0);
        return new TeleportOptions(values, returning);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Client implements TeleportClientAccess {
        private World world;
        private EntityPlayer player;
        private int closes;
        private final List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
        private String recovery;
        private boolean failAfterSwitch;
        private boolean nativeContainerClose;
        private int replacements;
        private FailureBoundary failureBoundary;
        private boolean failureInjected;
        private boolean savingCheckpoint;
        private int checkpointCount;
        private Path blockedFile;
        private Path blockedBackup;

        private Client(World world, EntityPlayer player) {
            this.world = world;
            this.player = player;
        }

        public World world() {
            return world;
        }

        public EntityPlayer player() {
            return player;
        }

        public void beginPreparation(TeleportTicket ticket, String name) {
        }

        public void showPreparation(String stage, int percent) {
            if ("saving".equals(stage)) {
                savingCheckpoint = true;
            } else if ("saving_player".equals(stage)) {
                if (failureBoundary == FailureBoundary.SOURCE_PLAYER) {
                    blockFile("level.dat");
                } else if (failureBoundary == FailureBoundary.INTENT) {
                    blockFile("betamoon/player_teleport.dat");
                }
            } else if ("switching".equals(stage) && failureBoundary == FailureBoundary.BEFORE_SWITCH) {
                injectFailure();
            } else if ("finalizing".equals(stage)) {
                if (failureBoundary == FailureBoundary.TARGET_PLAYER) {
                    blockFile("level.dat");
                } else if (failureBoundary == FailureBoundary.COMPLETION) {
                    blockFile("betamoon/player_teleport.dat");
                }
            }
        }

        public IProgressUpdate loadingProgress() {
            if (failureBoundary != FailureBoundary.SOURCE_CHECKPOINT
                    && failureBoundary != FailureBoundary.DESTINATION_CHECKPOINT) {
                return null;
            }
            return new IProgressUpdate() {
                public void func_594_b(String title) {
                }

                public void displayLoadingString(String message) {
                    if (!savingCheckpoint || !"Saving chunks".equals(message)) {
                        return;
                    }
                    checkpointCount++;
                    if (checkpointCount == 1 && failureBoundary == FailureBoundary.SOURCE_CHECKPOINT) {
                        blockFile("level.dat");
                    } else if (checkpointCount == 2 && failureBoundary == FailureBoundary.DESTINATION_CHECKPOINT) {
                        blockFile("betamoon/dimensions/" + DimensionKey.parse("test:flat").storagePath()
                                + "/dimension.dat");
                    }
                }

                public void setLoadingProgress(int percent) {
                }
            };
        }

        private void blockFile(String relative) {
            try {
                require(!failureInjected, "Failure must be injected once");
                blockedFile = DimensionQueries.session(world).paths.resolve(relative);
                blockedBackup = blockedFile.resolveSibling(blockedFile.getFileName() + ".audit-backup");
                Files.move(blockedFile, blockedBackup);
                Files.createDirectory(blockedFile);
                failureInjected = true;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void restoreBlockedFile() throws IOException {
            if (blockedBackup != null && Files.exists(blockedBackup)) {
                Files.delete(blockedFile);
                Files.move(blockedBackup, blockedFile);
            }
        }

        private void injectFailure() {
            failureInjected = true;
            throw new IllegalStateException("injected boundary failure: " + failureBoundary);
        }

        public void closeContainer() {
            closes++;
            if (nativeContainerClose) {
                player.craftingInventory.onCraftGuiClosed(player);
                player.craftingInventory = player.inventorySlots;
            }
        }

        public void finishPreparation(boolean success) {
            if (success && failureBoundary == FailureBoundary.FINISH_SCREEN
                    || !success && failureBoundary == FailureBoundary.CANCEL_SCREEN) {
                injectFailure();
            }
        }

        public void switchWorld(World destination, EntityPlayer player, NBTTagCompound target) {
            if (destination != world) {
                world.playerEntities.remove(player);
                world.loadedEntityList.remove(player);
            }
            world = destination;
            player.worldObj = destination;
            player.readFromNBT(target);
            if (!destination.playerEntities.contains(player)) {
                destination.entityJoinedWorld(player);
            }
            if (failAfterSwitch) {
                throw new IllegalStateException("injected failure after runtime switch");
            }
            if (failureBoundary == FailureBoundary.AFTER_SWITCH) {
                injectFailure();
            }
        }

        public EntityPlayer createRespawnPlayer(World destination) {
            replacements++;
            EntityPlayer replacement = TeleportServiceTest.player(destination);
            return replacement;
        }

        public void switchRespawnPlayer(World destination, EntityPlayer replacement, NBTTagCompound target) {
            world.setEntityDead(player);
            player = replacement;
            switchWorld(destination, replacement, target);
        }

        public void publish(Map<String, Object> result) {
            events.add(result);
            if (failureBoundary == FailureBoundary.PUBLISH) {
                injectFailure();
            }
        }

        public void recovery(String detail) {
            recovery = detail;
        }
    }
}
