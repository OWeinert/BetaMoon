package betamoon.multiblock;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Block;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import org.luaj.vm2.LuaValue;

/**
 * One saved placement owns activation, not any particular block or tile entity.
 */
final class ControllerlessMultiblockInstance extends MultiblockInstance {
    final ControllerlessMultiblockWorld runtime;
    final MultiblockPlacement placement;
    final NBTTagCompound saved;
    final Set<MultiblockPosition> footprint;
    private NBTTagCompound ownedTags;
    private int cleanupTurn;
    final Map<MultiblockPosition, MultiblockOwnedBlock> owned = new LinkedHashMap<>();
    long revision;
    long jobRevision;
    MultiblockMatchJob job;
    boolean formedHint;
    boolean active;
    boolean outputLost;
    boolean inhibited;
    boolean resumed;
    boolean transitioning;
    boolean cleaning;
    MultiblockPosition writing;

    ControllerlessMultiblockInstance(ControllerlessMultiblockWorld runtime, MultiblockPlacement placement,
            NBTTagCompound saved) {
        super(placement.anchor, placement.definition, saved, runtime::markDirty);
        this.runtime = runtime;
        this.placement = placement;
        this.saved = saved;
        footprint = new LinkedHashSet<>(placement.positions);
        facing = placement.facing;
        mirror = placement.mirror;
        formedHint = saved.getBoolean("Formed");
        active = saved.getBoolean("Active");
        outputLost = saved.getBoolean("OutputLost");
        inhibited = saved.getBoolean("Inhibited");
        expired = saved.getBoolean("Retired");
        resumed = formedHint;
        ownedTags = saved.getCompoundTag("Owned");
        for (Object value : ownedTags.func_28110_c()) {
            if (value instanceof NBTTagCompound && !((NBTTagCompound) value).getBoolean("Released")) {
                MultiblockOwnedBlock block = new MultiblockOwnedBlock((NBTTagCompound) value);
                if (!block.valid() || !footprint.contains(block.position)) {
                    throw new IllegalArgumentException("Invalid saved owned output; retained for reconciliation");
                }
                owned.put(block.position, block);
            }
        }
        save();
    }

    @Override
    public World world() {
        return runtime.world;
    }

    @Override
    public void requireCurrent() {
        if (expired || !runtime.current() || runtime.instances.get(placement.key) != this
                || MultiblockRegistry.find(definition.key) != definition) {
            throw new IllegalStateException("Multiblock instance expired after removal, reload or world change");
        }
    }

    @Override
    public void requestCheck() {
        requireCurrent();
        invalidate();
    }

    void invalidate() {
        dirty = true;
        revision++;
        job = null;
    }

    @Override
    public boolean isActive() {
        return isFormed() && active && !cleaning;
    }

    @Override
    public boolean activationSaved() {
        return active;
    }

    @Override
    public boolean manuallyInhibited() {
        return inhibited;
    }

    void save() {
        saved.setInteger("Format", 1);
        saved.setString("Key", placement.key);
        saved.setString("Definition", definition.key.toString());
        saved.setInteger("X", position.x);
        saved.setInteger("Y", position.y);
        saved.setInteger("Z", position.z);
        saved.setInteger("Facing", facing);
        saved.setBoolean("Mirror", mirror);
        saved.setInteger("Repeats", placement.variant.repeats);
        saved.setBoolean("Formed", formedHint);
        saved.setBoolean("Active", active);
        saved.setBoolean("OutputLost", outputLost);
        saved.setBoolean("Inhibited", inhibited);
        saved.setBoolean("Retired", expired);
        saved.setCompoundTag("Owned", ownedTags);
        runtime.records().setCompoundTag(placement.key, saved);
        runtime.markDirty();
    }

    MultiblockMatchJob.Reader reader() {
        return new MultiblockMatchJob.Reader() {
            public boolean loaded(int x, int z) {
                return runtime.loaded(x, z);
            }

            public int blockId(int x, int y, int z) {
                MultiblockOwnedBlock block = validOwned(x, y, z);
                return block == null ? runtime.blockId(x, y, z) : block.originalId;
            }

            public int metadata(int x, int y, int z) {
                MultiblockOwnedBlock block = validOwned(x, y, z);
                return block == null ? runtime.metadata(x, y, z) : block.originalMetadata;
            }
        };
    }

    private MultiblockOwnedBlock validOwned(int x, int y, int z) {
        MultiblockOwnedBlock block = owned.get(new MultiblockPosition(x, y, z));
        return block != null && matchesPlaced(block) ? block : null;
    }

    boolean matchesPlaced(MultiblockOwnedBlock block) {
        return runtime.loaded(block.position.x, block.position.z)
                && runtime.blockId(block.position.x, block.position.y, block.position.z) == block.placedId
                && runtime.metadata(block.position.x, block.position.y, block.position.z) == block.placedMetadata;
    }

    void changing(MultiblockPosition point) {
        MultiblockOwnedBlock block = owned.get(point);
        // Own setter enters while the original is still present. A nested
        // remove/replace
        // after the output exists is an external edit, including replacement with the
        // same ID.
        if (block != null && !(cleaning && point.equals(writing))
                && (!point.equals(writing) || block.written || matchesPlaced(block))) {
            releaseOwned(point);
            outputLost |= active;
            save();
        }
        invalidate();
    }

    int validate(int budget) {
        if (runtime.instances.get(placement.key) != this) {
            return 1;
        }
        if (expired) {
            return Math.max(1, cleanup(Math.min(32, budget)));
        }
        if (!dirty) {
            return 0;
        }
        if (job == null) {
            if (active) {
                for (MultiblockOwnedBlock output : new ArrayList<>(owned.values())) {
                    if (runtime.loaded(output.position.x, output.position.z) && !matchesPlaced(output)) {
                        releaseOwned(output.position);
                        outputLost = true;
                    }
                }
            }
            job = new MultiblockMatchJob(definition, reader(), position.x, position.y, position.z, facing, mirror,
                    false, placement.variant.repeats);
            jobRevision = revision;
        }
        int used = job.advance(budget);
        if (job.done() && jobRevision == revision) {
            MultiblockReport result = job.report();
            job = null;
            commit(result);
        }
        return Math.max(1, used);
    }

    void commit(MultiblockReport result) {
        String previous = status;
        if (outputLost) {
            boolean wasActive = active;
            active = false;
            outputLost = false;
            save();
            cleanup();
            if (wasActive) {
                event("onDeactivated", "output_changed");
            }
        }
        report = result;
        dirty = false;
        if (result.status.equals("unavailable")) {
            status = "suspended";
            if (formedHint && !previous.equals("suspended")) {
                event("onSuspended", "chunk_unavailable");
            }
            return;
        }
        if (result.status.equals("complete") && !runtime.conflicts(this, result)) {
            boolean first = !formedHint;
            status = "formed";
            formedHint = true;
            save();
            if (resumed || previous.equals("suspended")) {
                resumed = false;
                event("onResumed", "revalidated");
            } else if (first) {
                event("onFormed", "complete");
            }
            if (definition.management.automatic && !inhibited && !active && !dirty && !expired) {
                activate();
            }
        } else if (formedHint) {
            retire(result.reason);
        } else {
            status = "unformed";
        }
    }

    private void event(String name, String reason) {
        MultiblockLifecycle.invoke(this, name, reason, null, null, null, null, -1);
    }

    @Override
    public MultiblockActivationResult activate() {
        requireCurrent();
        if (!definition.management.automatic) {
            return result(false, false, "item_required");
        }
        return activate(null, null, null, null, -1);
    }

    MultiblockActivationResult activate(EntityPlayer player, ItemStack stack, MultiblockPosition clicked,
            MultiblockPosition target, int face) {
        if (transitioning) {
            return result(false, true, "activation_in_progress");
        }
        if (active && isActive()) {
            return result(false, true, "already_active");
        }
        if (!owned.isEmpty()) {
            return result(false, true, "cleanup_pending");
        }
        if (expired || dirty || !isFormed()) {
            return result(false, true, dirty ? "checking" : "not_formed");
        }
        if (!runtime.reserveActivation(this)) {
            return result(false, true, "activation_budget");
        }
        transitioning = true;
        try {
            ItemStack original = stack == null ? null : stack.copy();
            if (!MultiblockLifecycle.invoke(this, "canActivate", "activation", player, original, clicked, target,
                    face)) {
                return result(false, false, "denied");
            }
            if (dirty || expired || !runtime.revalidate(this) || runtime.conflicts(this, report)
                    || player != null && (player.worldObj != world() || player.getCurrentEquippedItem() != stack
                            || !ItemStack.areItemStacksEqual(stack, original))) {
                return result(false, true, "stale_activation");
            }
            MultiblockManagement policy = definition.management;
            if (stack != null && (stack.itemID != policy.item || stack.stackSize < Math.max(1, policy.consumeCost)
                    || policy.itemDamage >= 0 && stack.getItemDamage() != policy.itemDamage)) {
                return result(false, false, "item_mismatch");
            }
            if (!fill()) {
                active = false;
                cleanup();
                return result(false, true, "fill_failed");
            }
            if (player != null
                    && (player.getCurrentEquippedItem() != stack || !ItemStack.areItemStacksEqual(stack, original))) {
                active = false;
                cleanup();
                return result(false, true, "stale_activation");
            }
            long validatedRevision = revision;
            if (player != null && stack != null) {
                if (policy.damageCost > 0) {
                    stack.damageItem(policy.damageCost, player);
                } else {
                    stack.stackSize -= policy.consumeCost;
                }
                MultiblockItemUse.charged(player, stack);
            }
            active = true;
            inhibited = false;
            dirty = revision != validatedRevision;
            save();
            MultiblockLifecycle.invoke(this, "onActivated", policy.automatic ? "automatic" : "item", player, original,
                    clicked, target, face);
            return result(true, true, "activated");
        } finally {
            transitioning = false;
        }
    }

    private boolean fill() {
        MultiblockManagement policy = definition.management;
        if (policy.fillBlock < 0) {
            return true;
        }
        int metadata = 0;
        BlockDefinition block = BlockCallbackRegistry.get(policy.fillBlock);
        if (block != null) {
            metadata = block.state.defaults;
            if (policy.facingState != null) {
                metadata = block.state.set(metadata, policy.facingState,
                        LuaValue.valueOf(MultiblockOrientation.name(facing)));
            }
        }
        for (MultiblockReport.Match match : report.matches) {
            if (policy.fillCells.contains(match.symbol)) {
                MultiblockPosition point = new MultiblockPosition(match.x, match.y, match.z);
                if (runtime.outputReserved(point, this) || Block.isBlockContainer[match.blockId]
                        || world().getBlockTileEntity(point.x, point.y, point.z) != null) {
                    return false;
                }
                MultiblockOwnedBlock output = new MultiblockOwnedBlock(point, match.blockId, match.metadata,
                        policy.fillBlock, metadata);
                owned.put(point, output);
                ownedTags.setCompoundTag(point.toString(), output.save());
            }
        }
        save();
        try {
            for (MultiblockOwnedBlock output : new ArrayList<>(owned.values())) {
                writing = output.position;
                world().setBlockAndMetadataWithNotify(writing.x, writing.y, writing.z, output.placedId,
                        output.placedMetadata);
                if (owned.get(output.position) != output || !matchesPlaced(output)) {
                    return false;
                }
                output.written = true;
                ownedTags.setCompoundTag(output.position.toString(), output.save());
                save();
                writing = null;
            }
            active = true;
            return runtime.revalidate(this);
        } catch (Throwable error) {
            callbackError = "activation.fill: " + error.getMessage();
            return false;
        } finally {
            writing = null;
        }
    }

    @Override
    public MultiblockActivationResult deactivate() {
        requireCurrent();
        if (transitioning) {
            return result(false, true, "activation_in_progress");
        }
        transitioning = true;
        try {
            inhibited = true;
            if (!active) {
                save();
                return result(false, true, "already_inactive");
            }
            active = false;
            save();
            cleanup();
            event("onDeactivated", "manual");
            invalidate();
            return result(false, true, "deactivated");
        } finally {
            transitioning = false;
        }
    }

    void retire(String reason) {
        if (expired) {
            return;
        }
        boolean wasActive = active;
        active = false;
        status = "unformed";
        dirty = false;
        save();
        cleanup();
        if (wasActive) {
            event("onDeactivated", reason);
        }
        if (formedHint) {
            event("onInvalidated", reason);
        }
        formedHint = false;
        expired = true;
        status = "removed";
        save();
    }

    void cleanup() {
        cleanup(64);
    }

    int cleanup(int allowance) {
        int used = 0;
        cleaning = true;
        try {
            List<MultiblockOwnedBlock> pending = new ArrayList<>(owned.values());
            int start = pending.isEmpty() ? 0 : Math.floorMod(cleanupTurn, pending.size());
            for (int index = 0; index < pending.size() && used < allowance; index++) {
                MultiblockOwnedBlock output = pending.get((start + index) % pending.size());
                used++;
                if (!runtime.loaded(output.position.x, output.position.z)) {
                    continue;
                }
                if (matchesPlaced(output)) {
                    if (output.originalId != 0 && Block.blocksList[output.originalId] == null) {
                        callbackError = "activation.cleanup: original block is unavailable";
                        continue;
                    }
                    output.restoring = true;
                    ownedTags.setCompoundTag(output.position.toString(), output.save());
                    save();
                    writing = output.position;
                    try {
                        world().setBlockAndMetadataWithNotify(output.position.x, output.position.y, output.position.z,
                                output.originalId, output.originalMetadata);
                    } catch (RuntimeException error) {
                        callbackError = "activation.cleanup: " + error.getMessage();
                    } finally {
                        writing = null;
                    }
                    if (matchesPlaced(output)) {
                        continue;
                    }
                }
                releaseOwned(output.position);
                save();
            }
        } finally {
            cleanupTurn += used;
            cleaning = false;
            if (owned.isEmpty()) {
                ownedTags = new NBTTagCompound();
            }
            save();
        }
        return used;
    }

    private void releaseOwned(MultiblockPosition point) {
        owned.remove(point);
        ownedTags.getCompoundTag(point.toString()).setBoolean("Released", true);
    }

    private MultiblockActivationResult result(boolean activated, boolean handled, String reason) {
        return new MultiblockActivationResult(activated, handled, reason, this);
    }
}
