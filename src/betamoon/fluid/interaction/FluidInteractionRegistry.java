package betamoon.fluid.interaction;

import betamoon.content.ContentKey;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.FluidState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.World;

/** Reloadable rules with lock-free immutable snapshots for world ticks. */
public final class FluidInteractionRegistry {
    private static final FluidInteractionRegistry INSTANCE = new FluidInteractionRegistry();

    private final Map<ContentKey, FluidInteractionDefinition> byKey = new LinkedHashMap<>();
    private volatile List<FluidInteractionDefinition> snapshot = Collections.emptyList();
    private Map<ContentKey, FluidInteractionDefinition> staged;

    private FluidInteractionRegistry() {
    }

    public static FluidInteractionRegistry instance() {
        return INSTANCE;
    }

    public synchronized void beginLoadPass() {
        staged = new LinkedHashMap<ContentKey, FluidInteractionDefinition>();
    }

    public synchronized FluidInteractionDefinition register(FluidInteractionDefinition definition) {
        Map<ContentKey, FluidInteractionDefinition> target = staged == null ? byKey : staged;
        FluidInteractionDefinition existing = target.get(definition.key());
        if (existing != null && !existing.owner().equals(definition.owner())) {
            throw new IllegalStateException("Fluid interaction '" + definition.key() + "' belongs to another owner.");
        }
        for (FluidInteractionDefinition other : target.values()) {
            if (other.key().equals(definition.key())) {
                continue;
            }
            if (other.replaced() == definition.replaced() && other.counterpart() == definition.counterpart()) {
                java.util.EnumSet<FluidFace> overlap = other.faces();
                overlap.retainAll(definition.faces());
                if (!overlap.isEmpty()) {
                    throw new IllegalStateException("Fluid interaction '" + definition.key()
                            + "' overlaps '" + other.key() + "' on faces " + overlap + ".");
                }
            }
        }
        target.put(definition.key(), definition);
        if (staged == null) {
            publishSnapshot();
        }
        return definition;
    }

    /** Removes script rules which disappeared; interactions have no native identity to retain. */
    public synchronized void finishLoadPass() {
        if (staged == null) {
            return;
        }
        byKey.clear();
        byKey.putAll(staged);
        staged = null;
        publishSnapshot();
    }

    private void publishSnapshot() {
        snapshot = Collections.unmodifiableList(new ArrayList<FluidInteractionDefinition>(byKey.values()));
    }

    public boolean evaluateCell(World world, int x, int y, int z) {
        FluidRegistration fluid = FluidRegistry.instance().getByBlockId(world.getBlockId(x, y, z));
        if (fluid == null) {
            return false;
        }
        for (FluidInteractionDefinition rule : snapshot) {
            if (rule.replaced() != fluid) {
                continue;
            }
            for (FluidFace face : rule.faces()) {
                FluidRegistration neighbor = FluidRegistry.instance()
                        .getByBlockId(world.getBlockId(x + face.x, y + face.y, z + face.z));
                if (neighbor != rule.counterpart()) {
                    continue;
                }
                FluidInteractionOutcome outcome = rule.outcome(FluidState.of(world.getBlockMetadata(x, y, z)));
                boolean changed = outcome != null;
                if (changed) {
                    world.setBlockWithNotify(x, y, z, outcome.resultBlockId());
                }
                FluidInteractionEffects effects = rule.effects();
                if (effects != null && (changed || effects.timing() == FluidInteractionEffects.Timing.CONTACT)) {
                    effects.play(world, x, y, z);
                }
                return changed;
            }
        }
        return false;
    }

    public boolean evaluateContact(World world, int firstX, int firstY, int firstZ, int secondX, int secondY,
            int secondZ) {
        return evaluateCell(world, firstX, firstY, firstZ)
                || evaluateCell(world, secondX, secondY, secondZ);
    }

    /** Evaluates both the custom cell and every directly touching counterpart. */
    public boolean evaluateAround(World world, int x, int y, int z) {
        if (evaluateCell(world, x, y, z)) {
            return true;
        }
        for (FluidFace face : FluidFace.values()) {
            if (evaluateCell(world, x + face.x, y + face.y, z + face.z)) {
                return true;
            }
        }
        return false;
    }
}
