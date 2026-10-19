package betamoon.teleport;

import betamoon.BetaMoonCommon;
import betamoon.dimension.DimensionBootstrap;
import betamoon.dimension.DimensionCapabilities;
import betamoon.dimension.DimensionGenerationBinding;
import betamoon.dimension.DimensionIdentity;
import betamoon.dimension.DimensionKey;
import betamoon.dimension.DimensionPlayerState;
import betamoon.dimension.DimensionQueries;
import betamoon.dimension.DimensionRuntime;
import betamoon.dimension.DimensionSession;
import betamoon.dimension.DimensionTransition;
import betamoon.dimension.storage.DimensionMetadataStore;
import betamoon.dimension.storage.DimensionSaveFlush;
import betamoon.dimension.storage.DimensionSaveHandler;
import betamoon.luaapi.item.ItemUseHandler;
import betamoon.system.WorldServiceRuntime;
import betamoon.worldgen.spatial.GenerationValues;
import betamoon.worldgen.terrain.ProfileWorldChunkManager;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.World;

/**
 * One queued local transfer, progressed only after callback/native iteration
 * has unwound.
 */
public final class TeleportService {
    private final TeleportClientAccess client;
    private final Map<DimensionSession, Long> cooldowns = new WeakHashMap<DimensionSession, Long>();
    private Task active;
    private long taskTicks;
    private boolean recovering;

    public TeleportService(TeleportClientAccess client) {
        this.client = client;
    }

    public Acceptance request(EntityPlayer player, TeleportOwner owner, DimensionKey target, TeleportOptions options,
            Completion completion) {
        return accept(player, owner, target, options, null, completion);
    }

    /**
     * Death has already applied native drops. Only the replacement, never the dead
     * inventory, is transferred.
     */
    public Acceptance respawn(EntityPlayer player, TeleportOwner owner, boolean ignoreBed) {
        if (client.world() == null || client.world().multiplayerWorld || player == null || player != client.player()
                || player.worldObj != client.world() || player.health > 0 && !player.isDead) {
            return Acceptance.refused("invalid_player");
        }
        if (busy()) {
            return Acceptance.refused("busy");
        }
        try {
            DimensionCapabilities.requireReady(DimensionIdentity.isCustom(player.worldObj));
            DimensionSession session = DimensionQueries.session(player.worldObj);
            if (session.navigation.pending() || session.navigation.preparation() != null) {
                return Acceptance.refused("recovery_required");
            }
            DimensionRuntime.attachNative(player.worldObj, session);
            session.navigation.ensureSaved();
            DimensionGenerationBinding binding = DimensionRuntime.generation(player.worldObj);
            DimensionKey target = DimensionIdentity.key(player.worldObj).equals(DimensionKey.NETHER)
                    ? DimensionKey.OVERWORLD
                    : DimensionIdentity.key(player.worldObj);
            TeleportLocation recorded = null;
            DimensionKey fallback = DimensionKey.OVERWORLD;
            if (binding != null) {
                if (binding.definition.respawnType.equals("dimension")) {
                    target = binding.definition.respawnDimension;
                } else if (binding.definition.respawnType.equals("return")) {
                    fallback = binding.definition.respawnDimension;
                    NBTTagCompound entry = session.navigation.respawnReturn();
                    recorded = entry == null ? null : TeleportLocation.read(entry.getCompoundTag("Location"));
                    target = recorded == null ? fallback : recorded.dimension;
                }
            }
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            values.put("maxChunks", 81);
            values.put("timeoutTicks", 1200);
            values.put("verticalRange", 127);
            values.put("rememberReturn", false);
            TeleportTicket ticket = new TeleportTicket();
            Task task = new Task(ticket, owner, session, DimensionPlayerState.runtimeToken(player, session),
                    player.worldObj, target, new TeleportOptions(values, false), null, false, null);
            task.respawning = true;
            TeleportDiagnostics.begin(ticket.id, owner.name, DimensionIdentity.key(player.worldObj).toString(),
                    target.toString(), "respawn");
            task.ignoreBed = ignoreBed;
            task.respawnReturn = recorded;
            if (!fallback.equals(target)) {
                task.respawnFallbacks.add(fallback);
            }
            if (!DimensionKey.OVERWORLD.equals(target) && !task.respawnFallbacks.contains(DimensionKey.OVERWORLD)) {
                task.respawnFallbacks.add(DimensionKey.OVERWORLD);
            }
            active = task;
            return new Acceptance(ticket, null);
        } catch (IOException | RuntimeException exception) {
            return Acceptance.refused(reason(exception));
        }
    }

    public Acceptance returnToPrevious(EntityPlayer player, TeleportOwner owner, TeleportOptions options,
            Completion completion) {
        try {
            String refusal = playerRefusal(player, options);
            if (refusal != null) {
                return Acceptance.refused(refusal);
            }
            DimensionSession session = DimensionQueries.session(player.worldObj);
            NBTTagCompound previous = session.navigation.previous(owner.name, options.channel);
            if (previous == null) {
                return Acceptance.refused("no_return_point");
            }
            TeleportLocation location = TeleportLocation.read(previous.getCompoundTag("Location"));
            return accept(player, owner, location.dimension, options, previous, completion);
        } catch (IOException | RuntimeException exception) {
            return Acceptance.refused(reason(exception));
        }
    }

    private Acceptance accept(EntityPlayer player, TeleportOwner owner, DimensionKey target, TeleportOptions options,
            NBTTagCompound returning, Completion completion) {
        String refusal = playerRefusal(player, options);
        if (refusal != null) {
            return Acceptance.refused(refusal);
        }
        if (!owner.active()) {
            return Acceptance.refused("owner_unloaded");
        }
        if (active != null || recovering) {
            return Acceptance.refused("busy");
        }
        try {
            DimensionSession session = DimensionQueries.session(player.worldObj);
            if (session.navigation.pending() || session.navigation.preparation() != null) {
                return Acceptance.refused("busy");
            }
            if (taskTicks < cooldowns.getOrDefault(session, 0L)) {
                return Acceptance.refused("cooldown");
            }
            if (!target.isNative() && session.availableBinding(target) == null) {
                return Acceptance.refused("unknown_dimension");
            }
            DimensionCapabilities.requireReady(!target.isNative() || DimensionIdentity.isCustom(player.worldObj));
            boolean remember = returning == null && (options.rememberReturn == null
                    ? !DimensionIdentity.key(player.worldObj).equals(target)
                    : options.rememberReturn);
            if (remember) {
                session.navigation.requireCapacity(owner.name, options.channel);
            }
            DimensionRuntime.attachNative(player.worldObj, session);
            session.navigation.ensureSaved();
            TeleportTicket ticket = new TeleportTicket();
            TeleportDiagnostics.begin(ticket.id, owner.name, DimensionIdentity.key(player.worldObj).toString(),
                    target.toString(), "teleport");
            active = new Task(ticket, owner, session, DimensionPlayerState.runtimeToken(player, session),
                    player.worldObj, target, options, returning, remember, completion);
            return new Acceptance(ticket, null);
        } catch (IOException | RuntimeException exception) {
            return Acceptance.refused(reason(exception));
        }
    }

    private String playerRefusal(EntityPlayer player, TeleportOptions options) {
        if (client.world() == null) {
            return "no_world";
        }
        if (client.world().multiplayerWorld) {
            return "unsupported_environment";
        }
        if (player == null || player != client.player() || player.worldObj != client.world()) {
            return "invalid_player";
        }
        if (player.isDead || player.health <= 0) {
            return "dead";
        }
        if (player.isPlayerSleeping()) {
            return "sleeping";
        }
        if (!options.dismount && (player.ridingEntity != null || player.riddenByEntity != null)) {
            return "mounted";
        }
        return null;
    }

    public void tick() {
        taskTicks++;
        Task task = active;
        if (task == null) {
            return;
        }
        try {
            if (!task.current()) {
                task.fail("session_changed");
            } else if (!task.owner.active()) {
                task.fail("owner_unloaded");
            } else if (task.ticket.cancellationRequested()) {
                task.fail("cancelled");
            } else if (++task.elapsed > task.options.timeoutTicks) {
                task.fail("timeout");
            } else {
                task.step();
            }
        } catch (Throwable failure) {
            if (task.ticket.result() != null) {
                BetaMoonCommon.LOGGER.warning("Teleport completion notification failed: " + failure.toString());
                return;
            }
            if (failure instanceof ChunkPreparation.WorkFailure
                    && ((ChunkPreparation.WorkFailure) failure).populationChanged) {
                if (task.destination != null && task.destination != task.sourceWorld) {
                    try {
                        task.discardFailedPopulation();
                        if (task.tryRespawnFallback(reason(failure))) {
                            return;
                        }
                        task.fail(reason(failure));
                        return;
                    } catch (Throwable cleanupFailure) {
                        recoverTask(task, "save_failed",
                                "Failed destination cleanup could not be recorded. " + cleanupFailure.getMessage());
                        return;
                    }
                }
                recoverTask(task, reason(failure),
                        "Terrain population failed. The saved source checkpoint has been kept. "
                                + "Retry loading after correcting the generator. " + failure.getMessage());
            } else if (task.committing) {
                recoverTask(task, reason(failure), "The transfer could not finish safely. " + failure.getMessage());
            } else {
                try {
                    if (task.tryRespawnFallback(reason(failure))) {
                        return;
                    }
                    task.fail(reason(failure));
                } catch (Throwable cleanupFailure) {
                    recoverTask(task, "save_failed",
                            "Preparation cleanup could not be saved. " + cleanupFailure.getMessage());
                }
            }
        }
    }

    private void recoverTask(Task task, String reason, String detail) {
        recovering = true;
        task.ticket.finish(reason, task.departure, task.arrival, task.usedFallback, false);
        active = null;
        try {
            client.recovery(detail);
        } catch (Throwable recoveryFailure) {
            BetaMoonCommon.LOGGER
                    .warning("Recovery screen failed; the operation remains frozen: " + recoveryFailure.toString());
        } finally {
            task.publishTerminal();
        }
    }

    public boolean busy() {
        return active != null || recovering;
    }

    public boolean frozen() {
        return recovering || active != null && !active.ticket.state().equals("queued");
    }

    public boolean recovering() {
        return recovering;
    }

    public void enterRecovery() {
        recovering = true;
    }

    public static TeleportLocation mapCoordinates(TeleportLocation source, DimensionKey target, double sourceScale,
            double targetScale) {
        double ratio = sourceScale / targetScale;
        return new TeleportLocation(target, source.x * ratio, source.y, source.z * ratio, source.yaw, source.pitch);
    }

    private static String reason(Throwable failure) {
        Throwable original = failure;
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            if (failure instanceof DimensionCapabilities.Unavailable) {
                BetaMoonCommon.LOGGER.warning(failure.getMessage());
                return "unsupported_environment";
            }
            if (failure instanceof ChunkPreparation.WorkFailure) {
                return ((ChunkPreparation.WorkFailure) failure).reason;
            }
            String message = failure.getMessage();
            if (message != null) {
                for (String code : new String[]{"unsupported_environment", "generation_mismatch", "save_locked",
                        "save_failed", "out_of_bounds", "chunks_unavailable", "unsafe_destination", "budget_exhausted",
                        "no_return_point", "return_history_full", "dimension_unavailable", "unknown_dimension"}) {
                    if (message.startsWith(code)) {
                        return code;
                    }
                }
                if (message.contains("session lock")) {
                    return "save_locked";
                }
            }
        }
        return original instanceof IOException ? "save_failed" : "internal_error";
    }

    private final class Task {
        private final TeleportTicket ticket;
        private final TeleportOwner owner;
        private final DimensionSession session;
        private final long playerToken;
        private final World sourceWorld;
        private DimensionKey target;
        private final TeleportOptions options;
        private final NBTTagCompound returning;
        private final boolean remember;
        private Completion completion;
        private int elapsed;
        private int phase;
        private World destination;
        private TeleportLocation departure;
        private TeleportLocation suggestion;
        private TeleportLocation arrival;
        private DimensionBootstrap bootstrap;
        private ArrivalSearch search;
        private ChunkPreparation chunks;
        private TeleportPlatformPlan platform;
        private boolean usedFallback;
        private boolean returnSpawnAttempted;
        private boolean platformAttempted;
        private boolean leavePrepared;
        private boolean committing;
        private boolean respawning;
        private boolean ignoreBed;
        private TeleportLocation respawnReturn;
        private final List<DimensionKey> respawnFallbacks = new ArrayList<DimensionKey>();
        private ChunkCoordinates respawnBed;
        private boolean bedArrival;
        private String fallbackReason;
        private TeleportLocation uncheckedInitialSpawn;
        private EntityPlayer respawnProbe;
        private DimensionCleanupTask cleanup;

        private Task(TeleportTicket ticket, TeleportOwner owner, DimensionSession session, long playerToken,
                World source, DimensionKey target, TeleportOptions options, NBTTagCompound returning, boolean remember,
                Completion completion) {
            this.ticket = ticket;
            this.owner = owner;
            this.session = session;
            this.playerToken = playerToken;
            sourceWorld = source;
            this.target = target;
            this.options = options;
            this.returning = returning;
            this.remember = remember;
            this.completion = completion;
        }

        private boolean current() {
            return client.world() == sourceWorld && client.player() != null
                    && DimensionPlayerState.runtimeToken(client.player(), session) == playerToken;
        }

        private void step() throws IOException {
            EntityPlayer player = client.player();
            if (phase == 0) {
                String refusal = respawning ? null : playerRefusal(player, options);
                if (refusal != null) {
                    fail(refusal);
                    return;
                }
                departure = TeleportLocation.capture(player);
                ticket.update("preparing", "destination", elapsed, 0, 0);
                Map<String, Object> description = DimensionQueries.describe(target);
                client.beginPreparation(ticket,
                        description == null ? target.toString() : (String) description.get("displayName"));
                if (respawning) {
                    DimensionSaveFlush.flush(sourceWorld, session, client.loadingProgress());
                    NBTTagCompound deceased = new NBTTagCompound();
                    player.writeToNBT(deceased);
                    session.savePlayerSnapshot(deceased);
                }
                phase = 1;
                return;
            }
            if (phase == 1) {
                destination = departure.dimension.equals(target)
                        ? sourceWorld
                        : DimensionRuntime.create(session, target);
                if (respawning) {
                    respawnProbe = client.createRespawnPlayer(destination);
                    if (respawnProbe == null || respawnProbe == client.player()
                            || respawnProbe.worldObj != destination) {
                        throw new IOException("internal_error: no detached replacement player for arrival validation");
                    }
                }
                chunks = options.loadChunks
                        ? new ChunkPreparation(destination, options.maxChunks, this::checkpointSource)
                        : null;
                if (session.navigation.hasCleanup(target.toString())) {
                    if (chunks == null) {
                        throw new IOException("chunks_unavailable: destination has pending platform cleanup");
                    }
                    cleanup = new DimensionCleanupTask(destination, session, chunks);
                    phase = 7;
                } else {
                    beginDestinationArrival();
                }
                return;
            }
            if (phase == 2) {
                if (!bootstrap.step()) {
                    progress("spawn");
                    return;
                }
                if (bootstrap.failure() != null) {
                    if (tryRespawnFallback(bootstrap.failure())) {
                        return;
                    }
                    fail(bootstrap.failure());
                    return;
                }
                TeleportOptions bootstrapOptions = bootstrapOptions();
                search = new ArrivalSearch(destination, bootstrapOptions, bootstrap.proposal(), false, 0, chunks);
                phase = 3;
                return;
            }
            if (phase == 3) {
                if (!search.step(arrivalActor())) {
                    progress("spawn");
                    return;
                }
                TeleportLocation entry = search.arrival();
                if (entry == null && bootstrap.fallback() != null) {
                    entry = buildPlatform(bootstrap.proposal(), bootstrap.fallback());
                }
                if (entry == null) {
                    if (tryRespawnFallback(search.failure())) {
                        return;
                    }
                    fail(search.failure());
                    return;
                }
                DimensionSaveHandler save = DimensionRuntime.binding(destination).save;
                save.metadata.initialize(entry.x, entry.y, entry.z, entry.yaw, entry.pitch);
                destination.setSpawnPoint(new ChunkCoordinates((int) Math.floor(entry.x), (int) Math.floor(entry.y),
                        (int) Math.floor(entry.z)));
                startArrival();
                return;
            }
            if (phase == 4) {
                if (!search.step(arrivalActor())) {
                    progress("arrival");
                    return;
                }
                arrival = search.arrival();
                if (arrival == null && respawning && (bedArrival || respawnReturn != null)) {
                    bedArrival = false;
                    respawnReturn = null;
                    suggestion = spawn();
                    search = new ArrivalSearch(destination, options, suggestion, false, options.searchRadius, chunks);
                    return;
                }
                if (arrival == null && returning != null && options.returnFallback && !returnSpawnAttempted) {
                    returnSpawnAttempted = true;
                    usedFallback = true;
                    suggestion = spawn();
                    search = new ArrivalSearch(destination, options, suggestion, false, options.searchRadius, chunks);
                    return;
                }
                if (arrival == null && !platformAttempted && options.platform != null) {
                    platformAttempted = true;
                    arrival = buildPlatform(suggestion, options.platform);
                }
                if (arrival == null) {
                    if (tryRespawnFallback(search.failure())) {
                        return;
                    }
                    fail(search.failure());
                    return;
                }
                if (uncheckedInitialSpawn != null) {
                    DimensionSaveHandler save = DimensionRuntime.binding(destination).save;
                    TeleportLocation initial = uncheckedInitialSpawn;
                    save.metadata.initialize(initial.x, initial.y, initial.z, initial.yaw, initial.pitch);
                    destination.setSpawnPoint(new ChunkCoordinates((int) Math.floor(initial.x),
                            DimensionMetadataStore.blockSpawnY(initial.y), (int) Math.floor(initial.z)));
                    uncheckedInitialSpawn = null;
                }
                phase = 5;
                progress("saving");
                return;
            }
            if (phase == 7) {
                if (!cleanup.step()) {
                    progress("recovery");
                    return;
                }
                cleanup = null;
                beginDestinationArrival();
                return;
            }
            if (phase == 6) {
                if (!chunks.step()) {
                    progress("bed");
                    return;
                }
                ChunkCoordinates usable = EntityPlayer.func_25060_a(destination, respawnBed);
                bedArrival = usable != null;
                suggestion = usable == null
                        ? spawn()
                        : new TeleportLocation(target, usable.x + 0.5, usable.y + 0.1, usable.z + 0.5, 0, 0);
                search = new ArrivalSearch(destination, options, suggestion, false, options.searchRadius, chunks);
                phase = 4;
                return;
            }
            commit(player);
        }

        private void beginDestinationArrival() throws IOException {
            DimensionRuntime.WorldBinding binding = DimensionRuntime.binding(destination);
            if (binding != null && binding.save != null && !binding.save.metadata.initialized()) {
                if (options.arrivalType.equals("exact") && options.safety.equals("unchecked")) {
                    prepareUncheckedBootstrap(binding.save);
                    startArrival();
                } else {
                    bootstrap = new DimensionBootstrap(destination);
                    phase = 2;
                }
            } else {
                startArrival();
            }
        }

        private void discardFailedPopulation() throws IOException {
            if (platform != null) {
                session.navigation.deferPendingCleanup(target.toString());
                platform = null;
            }
        }

        private void prepareUncheckedBootstrap(DimensionSaveHandler save) throws IOException {
            Map<String, Object> declared = DimensionRuntime.generation(destination).definition.spawn;
            uncheckedInitialSpawn = "fixed".equals(declared.get("type"))
                    ? new TeleportLocation(target, GenerationValues.number(declared.get("x"), "spawn.x"),
                            GenerationValues.number(declared.get("y"), "spawn.y"),
                            GenerationValues.number(declared.get("z"), "spawn.z"),
                            TeleportLocation.normalizeYaw(GenerationValues.number(declared, "yaw", 0, "spawn")),
                            (float) GenerationValues.number(declared, "pitch", 0, "spawn"))
                    : options.at(target, options.x, options.y, options.z, departure);
            save.metadata.bootstrapAnchor((int) Math.floor(uncheckedInitialSpawn.x),
                    DimensionMetadataStore.blockSpawnY(uncheckedInitialSpawn.y),
                    (int) Math.floor(uncheckedInitialSpawn.z));
            if (destination.getWorldChunkManager() instanceof ProfileWorldChunkManager) {
                ((ProfileWorldChunkManager) destination.getWorldChunkManager())
                        .setFieldContext(WorldGenerationRuntime.fieldContext(destination));
            }
        }

        private EntityPlayer arrivalActor() {
            return respawning ? respawnProbe : client.player();
        }

        private TeleportOptions bootstrapOptions() {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            values.put("searchRadius", 0);
            values.put("verticalRange", 127);
            values.put("loadChunks", options.loadChunks);
            values.put("maxChunks", options.maxChunks);
            return new TeleportOptions(values, false);
        }

        private void checkpointSource() {
            try {
                client.showPreparation("saving", -1);
                DimensionSaveFlush.flush(sourceWorld, session, client.loadingProgress());
                NBTTagCompound snapshot = new NBTTagCompound();
                client.player().writeToNBT(snapshot);
                session.savePlayerSnapshot(snapshot);
            } catch (IOException exception) {
                throw new UncheckedIOException("save_failed: source checkpoint could not be saved", exception);
            }
        }

        private void startArrival() throws IOException {
            if (respawning && respawnReturn != null) {
                suggestion = respawnReturn;
            } else if (respawning) {
                respawnBed = ignoreBed ? null : DimensionPlayerState.bed(client.player(), target);
                DimensionGenerationBinding binding = DimensionRuntime.generation(destination);
                if (binding != null && !binding.definition.environment.beds.equals("sleep")) {
                    respawnBed = null;
                }
                if (respawnBed != null) {
                    chunks.reserve(respawnBed.x >> 4, respawnBed.z >> 4, true);
                    phase = 6;
                    return;
                }
                suggestion = spawn();
            } else if (returning != null) {
                TeleportLocation recorded = TeleportLocation.read(returning.getCompoundTag("Location"));
                suggestion = options.at(target, recorded.x, recorded.y, recorded.z, recorded);
            } else if (options.arrivalType.equals("spawn")) {
                suggestion = spawn();
            } else if (options.arrivalType.equals("scaled")) {
                TeleportLocation scaled = mapCoordinates(departure, target, scale(sourceWorld), scale(destination));
                suggestion = options.at(target, scaled.x, scaled.y, scaled.z, departure);
            } else {
                suggestion = options.at(target, options.x, options.y, options.z, departure);
            }
            search = new ArrivalSearch(destination, options, suggestion,
                    returning == null && options.arrivalType.equals("exact"), options.searchRadius, chunks);
            phase = 4;
        }

        private boolean tryRespawnFallback(String failure) throws IOException {
            if (!respawning || committing || respawnFallbacks.isEmpty()
                    || !(failure.equals("unsafe_destination") || failure.equals("dimension_unavailable")
                            || failure.equals("unknown_dimension") || failure.equals("generation_mismatch")
                            || failure.equals("budget_exhausted") || failure.equals("chunks_unavailable")
                            || failure.equals("generation_failed"))) {
                return false;
            }
            if (platform != null) {
                platform.rollback(destination);
                DimensionSaveFlush.flush(destination, session);
                session.navigation.clearPreparation(ticket.id);
                platform = null;
            }
            target = respawnFallbacks.remove(0);
            respawnReturn = null;
            bedArrival = false;
            if (destination != null && destination != sourceWorld) {
                DimensionRuntime.releaseInactive(destination);
            }
            destination = null;
            chunks = null;
            search = null;
            bootstrap = null;
            respawnProbe = null;
            cleanup = null;
            usedFallback = true;
            fallbackReason = failure;
            phase = 1;
            return true;
        }

        private TeleportLocation spawn() {
            ChunkCoordinates spawn = destination.getSpawnPoint();
            DimensionRuntime.WorldBinding binding = DimensionRuntime.binding(destination);
            if (binding != null && binding.save != null && binding.save.metadata.initialized()) {
                double[] entry = binding.save.metadata.entryPosition();
                if (spawn.x == (int) Math.floor(entry[0]) && spawn.y == DimensionMetadataStore.blockSpawnY(entry[1])
                        && spawn.z == (int) Math.floor(entry[2])) {
                    TeleportLocation precise = new TeleportLocation(target, entry[0], entry[1], entry[2],
                            (float) entry[3], (float) entry[4]);
                    return options.at(target, precise.x, precise.y, precise.z, precise);
                }
            }
            return options.at(target, spawn.x + 0.5, spawn.y, spawn.z + 0.5, departure);
        }

        private TeleportLocation buildPlatform(TeleportLocation at, TeleportPlatformDefinition definition)
                throws IOException {
            TeleportPlatformPlan planned = TeleportPlatformPlan.plan(destination, client.player(), at, definition);
            TeleportPlatformPlan combined = platform == null ? planned : platform.combine(planned);
            session.navigation.preparePlatform(ticket.id, target.toString(), combined.write());
            platform = combined;
            planned.apply(destination);
            TeleportLocation landing = new TeleportLocation(target, Math.floor(at.x) + 0.5, definition.y + 1,
                    Math.floor(at.z) + 0.5, at.yaw, at.pitch);
            String rejection = ArrivalInspector.inspect(destination, arrivalActor(), landing, "safe");
            if (rejection != null) {
                throw new IOException(rejection);
            }
            usedFallback = true;
            return landing;
        }

        private void commit(EntityPlayer player) throws IOException {
            String refusal = respawning ? null : playerRefusal(player, options);
            if (refusal != null) {
                fail(refusal);
                return;
            }
            if (sourceWorld != destination) {
                leavePrepared = true;
                WorldServiceRuntime.prepareToLeave(sourceWorld);
            }
            client.closeContainer();
            refusal = respawning ? null : playerRefusal(player, options);
            String safety = ArrivalInspector.inspect(destination, arrivalActor(), arrival, options.safety);
            if (refusal != null || safety != null) {
                fail(refusal == null ? safety : refusal);
                return;
            }
            client.showPreparation("saving", -1);
            DimensionSaveFlush.flush(sourceWorld, session, client.loadingProgress());
            if (destination != sourceWorld) {
                DimensionSaveFlush.flush(destination, session, client.loadingProgress());
            }
            client.showPreparation("saving_player", -1);
            NBTTagCompound source = new NBTTagCompound();
            player.writeToNBT(source);
            session.savePlayerSnapshot(source);
            EntityPlayer arrivingPlayer = player;
            NBTTagCompound arrivingState = source;
            if (respawning) {
                arrivingPlayer = respawnProbe;
                if (arrivingPlayer == null || arrivingPlayer == player || arrivingPlayer.worldObj != destination
                        || arrivingPlayer.health <= 0 || arrivingPlayer.isDead) {
                    throw new IOException("internal_error: native respawn did not create a living replacement");
                }
                client.prepareRespawnPlayer(arrivingPlayer, arrival);
                DimensionPlayerState.inheritRespawn(arrivingPlayer, source);
                arrivingState = new NBTTagCompound();
                arrivingPlayer.writeToNBT(arrivingState);
            }
            NBTTagCompound targetPlayer = DimensionPlayerState.target(arrivingPlayer, session, arrivingState, arrival,
                    destination.worldProvider.worldType, options, ticket.id);
            session.navigation.begin(ticket.id, owner.name, options.channel,
                    returning != null ? "pop" : remember ? "push" : "none",
                    returning == null ? null : returning.getString("Id"), departure, source, targetPlayer,
                    platform == null ? new NBTTagList() : platform.write(), respawning);
            committing = true;
            ticket.update("committing", "switching", elapsed, chunks == null ? 0 : chunks.acquiredCount(),
                    search.checks());
            client.showPreparation("switching", -1);
            if (options.dismount) {
                if (player.ridingEntity != null) {
                    player.mountEntity(null);
                }
                if (player.riddenByEntity != null) {
                    player.riddenByEntity.mountEntity(null);
                }
            }
            if (respawning) {
                client.switchRespawnPlayer(destination, arrivingPlayer, targetPlayer);
            } else {
                client.switchWorld(destination, arrivingPlayer, targetPlayer);
            }
            if (client.world() != destination || client.player() != arrivingPlayer
                    || arrivingPlayer.worldObj != destination) {
                throw new IOException("internal_error: native world switch did not retain the requested player");
            }
            if (!respawning) {
                ItemUseHandler.worldChanged(arrivingPlayer, sourceWorld, destination);
            }
            client.showPreparation("finalizing", -1);
            session.savePlayerSnapshot(targetPlayer);
            session.navigation.complete(ticket.id);
            DimensionRuntime.activate(destination);
            DimensionTransition.mark(destination, respawning ? "respawn" : "teleport", ticket.id);
            if (sourceWorld != destination) {
                DimensionRuntime.releaseInactive(sourceWorld);
            }
            cooldowns.put(session, taskTicks + options.cooldownTicks);
            ticket.finish(null, departure, arrival, usedFallback, remember, respawning ? "respawn" : "teleport",
                    fallbackReason);
            active = null;
            client.finishPreparation(true);
            publishTerminal();
        }

        private void fail(String failure) throws IOException {
            if (committing) {
                throw new IOException("Cannot cancel a committing transfer");
            }
            if (platform != null && destination != null) {
                platform.rollback(destination);
                DimensionSaveFlush.flush(destination, session);
                session.navigation.clearPreparation(ticket.id);
            }
            if (leavePrepared && current()) {
                WorldServiceRuntime.resumeCancelledLeave(sourceWorld);
            }
            if (destination != null && destination != sourceWorld) {
                DimensionRuntime.releaseInactive(destination);
            }
            boolean recoveryNeeded = respawning && !"cancelled".equals(failure) && !"session_changed".equals(failure)
                    && !"owner_unloaded".equals(failure);
            recovering |= recoveryNeeded;
            ticket.finish(failure == null ? "internal_error" : failure, departure, arrival, usedFallback, false);
            active = null;
            client.finishPreparation(false);
            publishTerminal();
            if (recoveryNeeded) {
                client.recovery("A safe respawn destination could not be prepared: " + failure);
            }
        }

        private void publishTerminal() {
            try {
                client.publish(ticket.result());
            } catch (Throwable notificationFailure) {
                BetaMoonCommon.LOGGER.warning("Teleport event notification failed: " + notificationFailure.toString());
            }
            try {
                notifyCompletion();
            } catch (Throwable notificationFailure) {
                BetaMoonCommon.LOGGER.warning("Teleport completion callback failed: " + notificationFailure.toString());
            }
        }

        private void notifyCompletion() {
            Completion callback = completion;
            completion = null;
            if (callback != null && owner.active()) {
                DimensionRuntime.WorldBinding current = DimensionRuntime.binding(client.world());
                boolean accessible = !recovering && current != null && current.session == session
                        && client.player() != null && client.player().worldObj == client.world()
                        && DimensionPlayerState.runtimeToken(client.player(), session) == playerToken;
                callback.completed(ticket.result(), accessible ? client.world() : null,
                        accessible ? client.player() : null);
            }
        }

        private void progress(String stage) {
            ticket.update("preparing", stage, elapsed, chunks == null ? 0 : chunks.acquiredCount(),
                    search == null ? 0 : search.checks());
            client.showPreparation(stage, chunks == null ? -1 : chunks.progressPercent());
        }
    }

    private static double scale(World world) {
        DimensionGenerationBinding binding = DimensionRuntime.generation(world);
        return binding == null
                ? DimensionIdentity.coordinateScale(DimensionIdentity.key(world))
                : binding.definition.coordinateScale;
    }

    public interface Completion {
        void completed(Map<String, Object> result, World world, EntityPlayer player);
    }

    public static final class Acceptance {
        public final TeleportTicket ticket;
        public final String reason;

        private Acceptance(TeleportTicket ticket, String reason) {
            this.ticket = ticket;
            this.reason = reason;
        }

        private static Acceptance refused(String reason) {
            return new Acceptance(null, reason);
        }
    }
}
