package betamoon.fluid.interaction;

import betamoon.content.ContentKey;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.FluidState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.World;

/** Reloadable rules with lock-free immutable snapshots for world ticks. */
public final class FluidInteractionRegistry {
    private static final FluidInteractionRegistry INSTANCE = new FluidInteractionRegistry();

    private final Map<ContentKey, FluidInteractionDefinition> byKey = new LinkedHashMap<>();
    private final Set<ContentKey> permanentKeys = new LinkedHashSet<>();
    private volatile List<FluidInteractionDefinition> snapshot = Collections.emptyList();
    private Map<ContentKey, FluidInteractionDefinition> staged;

    private FluidInteractionRegistry() {
    }

    public static FluidInteractionRegistry instance() {
        return INSTANCE;
    }

    public synchronized void beginLoadPass() {
        staged = new LinkedHashMap<ContentKey, FluidInteractionDefinition>();
        for (ContentKey key : permanentKeys) {
            FluidInteractionDefinition definition = byKey.get(key);
            if (definition != null) {
                staged.put(key, definition);
            }
        }
    }

    public synchronized FluidInteractionDefinition register(FluidInteractionDefinition definition) {
        Map<ContentKey, FluidInteractionDefinition> target = staged == null ? byKey : staged;
        registerInto(target, definition);
        if (staged == null) {
            publishSnapshot();
        }
        return definition;
    }

    public synchronized FluidInteractionDefinition registerPermanent(FluidInteractionDefinition definition) {
        registerInto(byKey, definition);
        permanentKeys.add(definition.key());
        if (staged != null) {
            registerInto(staged, definition);
        }
        publishSnapshot();
        return definition;
    }

    private static void registerInto(Map<ContentKey, FluidInteractionDefinition> target,
            FluidInteractionDefinition definition) {
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

    public synchronized FluidInteractionDefinition get(ContentKey key) {
        return byKey.get(key);
    }

    public boolean evaluateCell(World world, int x, int y, int z) {
        return evaluateCellResult(world, x, y, z).changed;
    }

    private Evaluation evaluateCellResult(World world, int x, int y, int z) {
        FluidRegistration fluid = FluidRegistry.instance().getByBlockId(world.getBlockId(x, y, z));
        if (fluid == null) {
            return Evaluation.NONE;
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
                return changed ? Evaluation.CHANGED : Evaluation.CONTACT;
            }
        }
        return Evaluation.NONE;
    }

    public boolean evaluateContact(World world, int firstX, int firstY, int firstZ, int secondX, int secondY,
            int secondZ) {
        Evaluation first = evaluateCellResult(world, firstX, firstY, firstZ);
        return first.matched ? first.changed : evaluateCellResult(world, secondX, secondY, secondZ).changed;
    }

    /** Evaluates both the custom cell and every directly touching counterpart. */
    public boolean evaluateAround(World world, int x, int y, int z) {
        Evaluation result = evaluateCellResult(world, x, y, z);
        if (result.matched) {
            return result.changed;
        }
        for (FluidFace face : FluidFace.values()) {
            result = evaluateCellResult(world, x + face.x, y + face.y, z + face.z);
            if (result.matched) {
                return result.changed;
            }
        }
        return false;
    }

    private enum Evaluation {
        NONE(false, false),
        CONTACT(true, false),
        CHANGED(true, true);

        private final boolean matched;
        private final boolean changed;

        Evaluation(boolean matched, boolean changed) {
            this.matched = matched;
            this.changed = changed;
        }
    }
}
