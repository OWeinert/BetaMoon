package betamoon.teleport;

import betamoon.dimension.DimensionKey;
import betamoon.dimension.storage.CheckedDimensionRecord;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.dimension.storage.DimensionPaths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;

/**
 * Root-owned player revision, bounded return stacks and write-ahead intent in
 * one atomic record.
 */
public final class TeleportJournal {
    public static final int PLAYER_BYTES = 512 * 1024;
    private static final int RECORD_BYTES = 2 * 1024 * 1024;
    private final CheckedDimensionRecord file;
    private NBTTagCompound state;
    private boolean persisted;

    public TeleportJournal(DimensionPaths paths) throws IOException {
        file = new CheckedDimensionRecord(paths, "betamoon/player_teleport.dat", 0x424d5450, RECORD_BYTES);
        byte[] saved = file.read();
        if (saved == null) {
            state = new NBTTagCompound();
            state.setString("Player", UUID.randomUUID().toString());
            state.setLong("Revision", 0);
            state.setTag("Returns", new NBTTagList());
        } else {
            state = DimensionNbt.decode(saved, RECORD_BYTES);
            validate(state);
            persisted = true;
        }
    }

    public synchronized String playerId() {
        return state.getString("Player");
    }

    public synchronized long revision() {
        return state.getLong("Revision");
    }

    public synchronized boolean pending() {
        return state.hasKey("Intent");
    }

    public synchronized NBTTagCompound preparation() throws IOException {
        return state.hasKey("Preparation") ? DimensionNbt.copy(state.getCompoundTag("Preparation"), 131072) : null;
    }

    public synchronized void preparePlatform(String id, String dimension, NBTTagList cells) throws IOException {
        if (pending()) {
            throw new IOException("busy");
        }
        if (state.hasKey("Preparation") && !state.getCompoundTag("Preparation").getString("Id").equals(id)) {
            throw new IOException("recovery_required: unfinished platform preparation");
        }
        uuid(id);
        DimensionKey.parse(dimension);
        TeleportPlatformPlan.read(cells);
        NBTTagCompound next = copy();
        NBTTagCompound preparation = new NBTTagCompound();
        preparation.setString("Id", id);
        preparation.setString("Dimension", dimension);
        preparation.setTag("Cells", cells);
        next.setCompoundTag("Preparation", preparation);
        replace(next);
    }

    public synchronized void clearPreparation(String id) throws IOException {
        if (!state.hasKey("Preparation")) {
            return;
        }
        if (!state.getCompoundTag("Preparation").getString("Id").equals(id)) {
            throw new IOException("Platform preparation identity mismatch");
        }
        NBTTagCompound next = copy();
        DimensionNbt.remove(next, "Preparation");
        replace(next);
    }

    /**
     * Explicit backed-up rescue can retain platform undo records without loading
     * unavailable terrain.
     */
    public synchronized void deferPendingCleanup(String dimension) throws IOException {
        DimensionKey.parse(dimension);
        NBTTagCompound pending = state.hasKey("Intent")
                ? state.getCompoundTag("Intent")
                : state.hasKey("Preparation") ? state.getCompoundTag("Preparation") : null;
        if (pending == null) {
            return;
        }
        NBTTagList cells = pending.getTagList(state.hasKey("Intent") ? "Platform" : "Cells");
        NBTTagCompound next = copy();
        if (cells.tagCount() != 0) {
            NBTTagList cleanup = next.getTagList("Cleanup");
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Id", pending.getString("Id"));
            entry.setString("Dimension", dimension);
            entry.setTag("Cells", cells);
            cleanup.setTag(entry);
            next.setTag("Cleanup", cleanup);
        }
        DimensionNbt.remove(next, "Intent");
        DimensionNbt.remove(next, "Preparation");
        replace(next);
    }

    public synchronized List<NBTTagCompound> cleanupFor(String dimension) throws IOException {
        List<NBTTagCompound> result = new ArrayList<NBTTagCompound>();
        NBTTagList cleanup = state.getTagList("Cleanup");
        for (int index = 0; index < cleanup.tagCount(); index++) {
            NBTTagCompound entry = (NBTTagCompound) cleanup.tagAt(index);
            if (dimension.equals(entry.getString("Dimension"))) {
                result.add(DimensionNbt.copy(entry, 131072));
            }
        }
        return result;
    }

    public synchronized boolean hasCleanup(String dimension) {
        NBTTagList cleanup = state.getTagList("Cleanup");
        for (int index = 0; index < cleanup.tagCount(); index++) {
            if (dimension.equals(((NBTTagCompound) cleanup.tagAt(index)).getString("Dimension"))) {
                return true;
            }
        }
        return false;
    }

    public synchronized void clearCleanup(String id) throws IOException {
        NBTTagCompound next = copy();
        NBTTagList retained = new NBTTagList();
        NBTTagList cleanup = next.getTagList("Cleanup");
        for (int index = 0; index < cleanup.tagCount(); index++) {
            NBTTagCompound entry = (NBTTagCompound) cleanup.tagAt(index);
            if (!id.equals(entry.getString("Id"))) {
                retained.setTag(entry);
            }
        }
        next.setTag("Cleanup", retained);
        replace(next);
    }

    public synchronized NBTTagCompound intent() throws IOException {
        return pending() ? DimensionNbt.copy(state.getCompoundTag("Intent"), RECORD_BYTES) : null;
    }

    public synchronized void ensureSaved() throws IOException {
        if (!persisted) {
            file.write(DimensionNbt.encode(state, RECORD_BYTES));
            persisted = true;
        }
    }

    /**
     * Returns the durable side without guessing from terrain or merging player
     * inventories.
     */
    public synchronized String recoverySide(NBTTagCompound player) throws IOException {
        if (player == null) {
            if (revision() == 0 && !pending()) {
                return "none";
            }
            throw new IOException("recovery_required: saved player is missing");
        }
        if (!player.hasKey("BetaMoonPlayer") && revision() == 0 && !pending()) {
            return "legacy";
        }
        if (!playerId().equals(player.getString("BetaMoonPlayer"))) {
            throw new IOException("recovery_required: player UUID disagrees with the root journal");
        }
        if (!pending()) {
            if (player.getLong("BetaMoonRevision") != revision()) {
                throw new IOException("recovery_required: saved player revision disagrees with the root journal");
            }
            return "none";
        }
        NBTTagCompound intent = state.getCompoundTag("Intent");
        String actual = DimensionNbt.fingerprint(player, PLAYER_BYTES);
        if (actual.equals(DimensionNbt.fingerprint(intent.getCompoundTag("Target"), PLAYER_BYTES))) {
            return "target";
        }
        if (actual.equals(DimensionNbt.fingerprint(intent.getCompoundTag("Source"), PLAYER_BYTES))) {
            return "source";
        }
        throw new IOException("recovery_required: saved player diverges from both transfer snapshots");
    }

    public synchronized void stamp(NBTTagCompound player, long revision, String transfer) throws IOException {
        if (revision < this.revision() || revision < 0 || revision - this.revision() > 1) {
            throw new IOException("Invalid live player revision");
        }
        player.setString("BetaMoonPlayer", playerId());
        player.setLong("BetaMoonRevision", revision);
        player.setString("BetaMoonTransfer", transfer == null ? "" : transfer);
    }

    public synchronized void requireCapacity(String owner, String channel) throws IOException {
        identifier(owner, 256);
        identifier(channel, 64);
        NBTTagList returns = state.getTagList("Returns");
        int count = 0;
        Set<String> channels = new HashSet<String>();
        for (int index = 0; index < returns.tagCount(); index++) {
            NBTTagCompound entry = (NBTTagCompound) returns.tagAt(index);
            if (owner.equals(entry.getString("Owner"))) {
                channels.add(entry.getString("Channel"));
                if (channel.equals(entry.getString("Channel"))) {
                    count++;
                }
            }
        }
        if (returns.tagCount() >= 256 || count >= 32 || !channels.contains(channel) && channels.size() >= 16) {
            throw new IOException("return_history_full");
        }
    }

    public synchronized NBTTagCompound previous(String owner, String channel) throws IOException {
        NBTTagList returns = state.getTagList("Returns");
        for (int index = returns.tagCount() - 1; index >= 0; index--) {
            NBTTagCompound entry = (NBTTagCompound) returns.tagAt(index);
            if (owner.equals(entry.getString("Owner")) && channel.equals(entry.getString("Channel"))) {
                return DimensionNbt.copy(entry, 4096);
            }
        }
        return null;
    }

    public synchronized void clear(String owner, String channel) throws IOException {
        if (pending()) {
            throw new IOException("busy");
        }
        NBTTagCompound next = copy();
        NBTTagList retained = new NBTTagList();
        NBTTagList returns = next.getTagList("Returns");
        for (int index = 0; index < returns.tagCount(); index++) {
            NBTTagCompound entry = (NBTTagCompound) returns.tagAt(index);
            if (!owner.equals(entry.getString("Owner")) || !channel.equals(entry.getString("Channel"))) {
                retained.setTag(entry);
            }
        }
        next.setTag("Returns", retained);
        replace(next);
    }

    public synchronized void begin(String id, String owner, String channel, String mutation, String returnId,
            TeleportLocation departure, NBTTagCompound source, NBTTagCompound target, NBTTagList platform)
            throws IOException {
        begin(id, owner, channel, mutation, returnId, departure, source, target, platform, false);
    }

    public synchronized void begin(String id, String owner, String channel, String mutation, String returnId,
            TeleportLocation departure, NBTTagCompound source, NBTTagCompound target, NBTTagList platform,
            boolean preserveArrival) throws IOException {
        if (pending()) {
            throw new IOException("busy");
        }
        uuid(id);
        identifier(owner, 256);
        identifier(channel, 64);
        if (!mutation.equals("push") && !mutation.equals("pop") && !mutation.equals("none")) {
            throw new IOException("Invalid return mutation");
        }
        if (mutation.equals("push")) {
            requireCapacity(owner, channel);
        }
        if (mutation.equals("pop")) {
            NBTTagCompound previous = previous(owner, channel);
            if (previous == null || !previous.getString("Id").equals(returnId)) {
                throw new IOException("no_return_point");
            }
        }
        if (revision() == Long.MAX_VALUE) {
            throw new IOException("Player revision exhausted");
        }
        NBTTagCompound intent = new NBTTagCompound();
        intent.setString("Id", id);
        intent.setString("Owner", owner);
        intent.setString("Channel", channel);
        intent.setString("Mutation", mutation);
        intent.setBoolean("PreserveArrival", preserveArrival);
        intent.setString("ReturnId", returnId == null ? "" : returnId);
        intent.setLong("Revision", revision() + 1);
        intent.setCompoundTag("Departure", departure.write());
        intent.setCompoundTag("Source", DimensionNbt.copy(source, PLAYER_BYTES));
        intent.setCompoundTag("Target", DimensionNbt.copy(target, PLAYER_BYTES));
        intent.setTag("Platform", platform);
        NBTTagCompound next = copy();
        next.setCompoundTag("Intent", intent);
        DimensionNbt.remove(next, "Preparation");
        replace(next);
    }

    /**
     * Native portal search completes before its final target can be recorded.
     * Source stays recoverable throughout.
     */
    public synchronized void replacePendingTarget(String id, NBTTagCompound target) throws IOException {
        if (!pending() || !id.equals(state.getCompoundTag("Intent").getString("Id"))) {
            throw new IOException("No matching transfer intent");
        }
        NBTTagCompound next = copy();
        next.getCompoundTag("Intent").setCompoundTag("Target", DimensionNbt.copy(target, PLAYER_BYTES));
        replace(next);
    }

    /** Complete the same durable intent once; a repeat is a no-op. */
    public synchronized void complete(String id) throws IOException {
        if (!pending()) {
            if (id.equals(state.getString("LastTransfer"))) {
                return;
            }
            throw new IOException("No matching transfer intent");
        }
        NBTTagCompound next = copy();
        NBTTagCompound intent = next.getCompoundTag("Intent");
        if (!id.equals(intent.getString("Id"))) {
            throw new IOException("Transfer intent identity mismatch");
        }
        String mutation = intent.getString("Mutation");
        NBTTagList returns = next.getTagList("Returns");
        if (mutation.equals("push")) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Id", id);
            entry.setString("Owner", intent.getString("Owner"));
            entry.setString("Channel", intent.getString("Channel"));
            entry.setCompoundTag("Location", intent.getCompoundTag("Departure"));
            returns.setTag(entry);
        } else if (mutation.equals("pop")) {
            NBTTagList retained = new NBTTagList();
            for (int index = 0; index < returns.tagCount(); index++) {
                NBTTagCompound entry = (NBTTagCompound) returns.tagAt(index);
                if (!entry.getString("Id").equals(intent.getString("ReturnId"))) {
                    retained.setTag(entry);
                }
            }
            returns = retained;
        }
        next.setTag("Returns", returns);
        next.setLong("Revision", intent.getLong("Revision"));
        next.setString("LastTransfer", id);
        if (!intent.getBoolean("PreserveArrival")) {
            next.setString("ArrivalOwner", intent.getString("Owner"));
            next.setString("ArrivalChannel", intent.getString("Channel"));
        }
        DimensionNbt.remove(next, "Intent");
        replace(next);
    }

    public synchronized NBTTagCompound respawnReturn() throws IOException {
        return previous(state.getString("ArrivalOwner"), state.getString("ArrivalChannel"));
    }

    public synchronized Map<String, Integer> historyCounts() {
        Map<String, Integer> result = new TreeMap<String, Integer>();
        NBTTagList returns = state.getTagList("Returns");
        for (int index = 0; index < returns.tagCount(); index++) {
            NBTTagCompound entry = (NBTTagCompound) returns.tagAt(index);
            String channel = entry.getString("Owner") + " / " + entry.getString("Channel");
            result.put(channel, result.getOrDefault(channel, 0) + 1);
        }
        return Collections.unmodifiableMap(result);
    }

    public synchronized void abort(String id) throws IOException {
        if (!pending()) {
            return;
        }
        if (!state.getCompoundTag("Intent").getString("Id").equals(id)) {
            throw new IOException("Transfer intent identity mismatch");
        }
        NBTTagCompound next = copy();
        DimensionNbt.remove(next, "Intent");
        replace(next);
    }

    private NBTTagCompound copy() throws IOException {
        return DimensionNbt.copy(state, RECORD_BYTES);
    }

    private void replace(NBTTagCompound next) throws IOException {
        validate(next);
        file.write(DimensionNbt.encode(next, RECORD_BYTES));
        state = next;
        persisted = true;
    }

    private static void validate(NBTTagCompound state) throws IOException {
        uuid(state.getString("Player"));
        if (state.getLong("Revision") < 0) {
            throw new IOException("Invalid player revision");
        }
        NBTTagList cleanup = state.getTagList("Cleanup");
        if (cleanup.tagCount() > 8) {
            throw new IOException("Deferred platform cleanup exceeds its eight-record budget");
        }
        Set<String> cleanupIds = new HashSet<String>();
        int cleanupCells = 0;
        for (int index = 0; index < cleanup.tagCount(); index++) {
            if (!(cleanup.tagAt(index) instanceof NBTTagCompound)) {
                throw new IOException("Invalid deferred cleanup record");
            }
            NBTTagCompound entry = (NBTTagCompound) cleanup.tagAt(index);
            uuid(entry.getString("Id"));
            if (!cleanupIds.add(entry.getString("Id"))) {
                throw new IOException("Duplicate deferred cleanup identity");
            }
            DimensionKey.parse(entry.getString("Dimension"));
            TeleportPlatformPlan.read(entry.getTagList("Cells"));
            cleanupCells += entry.getTagList("Cells").tagCount();
        }
        if (cleanupCells > 4096) {
            throw new IOException("Deferred cleanup exceeds its 4096-cell budget");
        }
        NBTTagList returns = state.getTagList("Returns");
        if (returns.tagCount() > 256) {
            throw new IOException("Return history exceeds its budget");
        }
        Set<String> ids = new HashSet<String>();
        Map<String, Integer> counts = new HashMap<String, Integer>();
        Map<String, Set<String>> channels = new HashMap<String, Set<String>>();
        for (int index = 0; index < returns.tagCount(); index++) {
            if (!(returns.tagAt(index) instanceof NBTTagCompound)) {
                throw new IOException("Invalid return history entry");
            }
            NBTTagCompound entry = (NBTTagCompound) returns.tagAt(index);
            String owner = entry.getString("Owner");
            String channel = entry.getString("Channel");
            identifier(owner, 256);
            identifier(channel, 64);
            uuid(entry.getString("Id"));
            TeleportLocation.read(entry.getCompoundTag("Location"));
            if (!ids.add(entry.getString("Id"))) {
                throw new IOException("Duplicate return identity");
            }
            String key = owner + "\n" + channel;
            int count = counts.getOrDefault(key, 0) + 1;
            counts.put(key, count);
            Set<String> ownerChannels = channels.computeIfAbsent(owner, ignored -> new HashSet<String>());
            ownerChannels.add(channel);
            if (count > 32 || ownerChannels.size() > 16) {
                throw new IOException("Return history exceeds its owner/channel budget");
            }
        }
        if (state.hasKey("Intent")) {
            NBTTagCompound intent = state.getCompoundTag("Intent");
            uuid(intent.getString("Id"));
            identifier(intent.getString("Owner"), 256);
            identifier(intent.getString("Channel"), 64);
            if (state.getLong("Revision") == Long.MAX_VALUE
                    || intent.getLong("Revision") != state.getLong("Revision") + 1) {
                throw new IOException("Invalid pending transfer revision");
            }
            String mutation = intent.getString("Mutation");
            if (!mutation.equals("push") && !mutation.equals("pop") && !mutation.equals("none")) {
                throw new IOException("Invalid pending return mutation");
            }
            TeleportLocation.read(intent.getCompoundTag("Departure"));
            DimensionNbt.encode(intent.getCompoundTag("Source"), PLAYER_BYTES);
            DimensionNbt.encode(intent.getCompoundTag("Target"), PLAYER_BYTES);
            if (!intent.getCompoundTag("Source").getString("BetaMoonPlayer").equals(state.getString("Player"))
                    || !intent.getCompoundTag("Target").getString("BetaMoonPlayer").equals(state.getString("Player"))
                    || intent.getCompoundTag("Source").getLong("BetaMoonRevision") != state.getLong("Revision")
                    || intent.getCompoundTag("Target").getLong("BetaMoonRevision") != intent.getLong("Revision")
                    || !intent.getCompoundTag("Target").getString("BetaMoonTransfer").equals(intent.getString("Id"))) {
                throw new IOException("Transfer snapshots have inconsistent player identities/revisions");
            }
            TeleportPlatformPlan.read(intent.getTagList("Platform"));
        }
        if (state.hasKey("Preparation")) {
            if (state.hasKey("Intent")) {
                throw new IOException("Conflicting platform preparation and transfer intent");
            }
            NBTTagCompound preparation = state.getCompoundTag("Preparation");
            uuid(preparation.getString("Id"));
            DimensionKey.parse(preparation.getString("Dimension"));
            TeleportPlatformPlan.read(preparation.getTagList("Cells"));
        }
    }

    private static void identifier(String value, int max) throws IOException {
        if (value == null || value.isEmpty() || value.length() > max || value.matches("(?s).*[\\p{Cntrl}].*")) {
            throw new IOException("Invalid saved navigation identifier");
        }
    }

    private static void uuid(String value) throws IOException {
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid saved transfer/player UUID", exception);
        }
    }
}
