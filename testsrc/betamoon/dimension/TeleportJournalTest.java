package betamoon.dimension;

import betamoon.assets.model.ModelJson;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.dimension.storage.DimensionPaths;
import betamoon.teleport.TeleportDiagnostics;
import betamoon.teleport.TeleportJournal;
import betamoon.teleport.TeleportLocation;
import betamoon.teleport.TeleportOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;

/**
 * Simulated interruptions on both sides of intent/player/completion
 * persistence.
 */
public final class TeleportJournalTest {
    public static void main(String[] arguments) throws Exception {
        recoveryBoundaries();
        historyLimits();
        failedWriteDoesNotPublish();
        structuralOptions();
        fingerprints();
        diagnostics();
        System.out
                .println("Teleport journal passed: interruption reconciliation, idempotent history, bounded channels, "
                        + "failed-write isolation, divergence refusal and strict options.");
    }

    private static void diagnostics() {
        for (int index = 0; index < 100; index++) {
            TeleportDiagnostics.begin("test-" + index, "owner.lua", "minecraft:overworld", "test:dimension/one",
                    "teleport");
            for (int stage = 0; stage < 40; stage++) {
                TeleportDiagnostics.stage("test-" + index, "preparing", "step-" + stage, stage, 25, 2048);
            }
        }
        List<Map<String, Object>> rows = TeleportDiagnostics.snapshot();
        require(rows.size() == 64 && rows.get(0).get("id").equals("test-36"),
                "Diagnostics must evict oldest entries at 64");
        require(((List<?>) rows.get(0).get("stages")).size() == 24, "Stage retention must stay bounded");
        TeleportDiagnostics.begin("new", "owner.lua", "minecraft:overworld", "minecraft:nether", "native_portal");
        require(rows.size() == 64 && rows.get(0).get("id").equals("test-36"),
                "Snapshots cannot follow later diagnostic mutations");
    }

    private static void recoveryBoundaries() throws Exception {
        DimensionPaths paths = new DimensionPaths(Files.createTempDirectory("betamoon-transfer-recovery-"));
        TeleportJournal journal = new TeleportJournal(paths);
        journal.ensureSaved();
        String id = UUID.randomUUID().toString();
        NBTTagCompound source = player(journal, 0, "");
        NBTTagCompound target = player(journal, 1, id);
        TeleportLocation departure = location();
        journal.begin(id, "owner.lua", "default", "push", null, departure, source, target, new NBTTagList());
        TeleportJournal reopened = new TeleportJournal(paths);
        require(reopened.recoverySide(source).equals("source"), "Intent alone must retain the saved source");
        require(reopened.previous("owner.lua", "default") == null, "Uncommitted transfers cannot push history");
        require(reopened.recoverySide(target).equals("target"),
                "A durable target player identifies completed switching");
        NBTTagCompound diverged = DimensionNbt.copy(target, TeleportJournal.PLAYER_BYTES);
        diverged.setString("InventoryMarker", "changed");
        fails(() -> reopened.recoverySide(diverged));
        reopened.complete(id);
        reopened.complete(id);
        TeleportJournal completed = new TeleportJournal(paths);
        require(completed.revision() == 1 && completed.recoverySide(target).equals("none"),
                "Completion and revision must survive reopen");
        NBTTagCompound previous = completed.previous("owner.lua", "default");
        require(previous != null && TeleportLocation.read(previous.getCompoundTag("Location")).x == -0.25,
                "Remember exact negative departure coordinates");
        require(completed.previous("other.lua", "default") == null, "Owners must not read each other's routes");
        String returning = UUID.randomUUID().toString();
        NBTTagCompound returned = player(completed, 2, returning);
        completed.begin(returning, "owner.lua", "default", "pop", previous.getString("Id"), departure, target, returned,
                new NBTTagList());
        completed.abort(returning);
        require(completed.previous("owner.lua", "default") != null && completed.revision() == 1,
                "Failed return must preserve its route and revision");
        completed.begin(returning, "owner.lua", "default", "pop", previous.getString("Id"), departure, target, returned,
                new NBTTagList());
        completed.complete(returning);
        require(completed.previous("owner.lua", "default") == null, "Successful return pops exactly one entry");
        fails(() -> completed.recoverySide(target));
    }

    private static void historyLimits() throws Exception {
        TeleportJournal journal = new TeleportJournal(
                new DimensionPaths(Files.createTempDirectory("betamoon-returns-")));
        for (int index = 0; index < 32; index++) {
            push(journal, "owner", "default");
        }
        fails(() -> journal.requireCapacity("owner", "default"));
        for (int channel = 1; channel < 16; channel++) {
            push(journal, "owner", "channel" + channel);
        }
        fails(() -> journal.requireCapacity("owner", "channel16"));
        journal.clear("owner", "default");
        require(journal.previous("owner", "channel1") != null, "Clearing one channel preserves other channels");
        for (int count = 15; count < 256; count++) {
            push(journal, "owner" + count, "default");
        }
        fails(() -> journal.requireCapacity("next-owner", "default"));
    }

    private static void push(TeleportJournal journal, String owner, String channel) throws Exception {
        String id = UUID.randomUUID().toString();
        long revision = journal.revision();
        journal.begin(id, owner, channel, "push", null, location(), player(journal, revision, ""),
                player(journal, revision + 1, id), new NBTTagList());
        journal.complete(id);
    }

    private static void failedWriteDoesNotPublish() throws Exception {
        Path root = Files.createTempDirectory("betamoon-transfer-write-failure-");
        TeleportJournal journal = new TeleportJournal(new DimensionPaths(root));
        journal.ensureSaved();
        Path file = root.resolve("betamoon/player_teleport.dat");
        Path backup = root.resolve("betamoon/previous.dat");
        Files.move(file, backup);
        Files.createDirectory(file);
        fails(() -> push(journal, "owner", "default"));
        require(!journal.pending() && journal.revision() == 0 && journal.previous("owner", "default") == null,
                "Failed atomic write must not publish in-memory mutation");
        Files.delete(file);
        Files.move(backup, file);
    }

    private static void structuralOptions() throws Exception {
        new TeleportOptions(Collections.emptyMap(), false);
        new TeleportOptions(
                json("{\"arrival\":{\"type\":\"exact\",\"x\":-0.25,\"y\":65,\"z\":0.5},\"safety\":\"unchecked\"}"),
                false);
        fails(() -> new TeleportOptions(json("{\"safety\":\"unchecked\"}"), false));
        fails(() -> new TeleportOptions(json("{\"arrival\":{\"type\":\"exact\",\"x\":0}}"), false));
        fails(() -> new TeleportOptions(json("{\"arrival\":{\"x\":1}}"), false));
        fails(() -> new TeleportOptions(json("{\"arrival\":{\"type\":\"spawn\"}}"), true));
        fails(() -> new TeleportOptions(json("{\"yaw\":0}"), false));
        fails(() -> new TeleportOptions(json("{\"maxChunks\":82}"), false));
        fails(() -> new TeleportOptions(json("{\"channel\":\"../other\"}"), false));
        fails(() -> new TeleportOptions(json("{\"velocity\":{\"x\":11,\"y\":0,\"z\":0}}"), false));
        fails(() -> new TeleportOptions(json("{\"unknown\":true}"), false));
    }

    private static void fingerprints() throws Exception {
        NBTTagCompound one = new NBTTagCompound();
        one.setInteger("Aa", 1);
        one.setInteger("BB", 2);
        NBTTagCompound two = new NBTTagCompound();
        two.setInteger("BB", 2);
        two.setInteger("Aa", 1);
        require(DimensionNbt.fingerprint(one, 4096).equals(DimensionNbt.fingerprint(two, 4096)),
                "Native compound hash order cannot change the player fingerprint");
    }

    private static NBTTagCompound player(TeleportJournal journal, long revision, String transfer) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("BetaMoonPlayer", journal.playerId());
        tag.setLong("BetaMoonRevision", revision);
        tag.setString("BetaMoonTransfer", transfer);
        return tag;
    }

    private static TeleportLocation location() {
        return new TeleportLocation(DimensionKey.OVERWORLD, -0.25, 65, 0.5, 45, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String value) throws Exception {
        return (Map<String, Object>) ModelJson.read(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void fails(Action action) throws Exception {
        try {
            action.run();
        } catch (IOException | IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected a checked refusal");
    }

    private interface Action {
        void run() throws Exception;
    }
}
