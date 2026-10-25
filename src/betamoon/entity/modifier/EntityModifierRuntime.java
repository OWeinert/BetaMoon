package betamoon.entity.modifier;

import betamoon.entity.motion.EntityMotionState;
import betamoon.entity.motion.EntityMovementSupport;
import betamoon.entity.motion.EntitySimulationThread;
import betamoon.logging.LogLevel;
import betamoon.logging.RuntimeLogs;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;

/**
 * Simulation-thread owned source lifetimes; native step snapshots never retain
 * callback facades.
 */
public final class EntityModifierRuntime {
    public static final int MAX_LOCAL_SOURCES = 256;
    public static final int MAX_WORLD_SOURCES = 4096;

    private static final IdentityHashMap<EntityLiving, BindingGroup> GROUPS = new IdentityHashMap<>();
    private static final List<Source> LOCALS = new ArrayList<>();
    private static long generationSequence;

    public static final Comparator<Source> PRODUCER_ORDER = (left, right) -> {
        int priority = Integer.compare(right.definition.priority, left.definition.priority);
        int owner = left.owner.compareTo(right.owner);
        return priority != 0
                ? priority
                : owner != 0 ? owner : left.definition.key.toString().compareTo(right.definition.key.toString());
    };

    private EntityModifierRuntime() {
    }

    public static Source attach(String owner, EntityLiving entity, EntityModifierDefinition definition) {
        EntitySimulationThread.requireCurrent();
        requireOwner(owner);
        EntityMovementSupport.require(entity, definition);
        Source source = new Source(owner, definition, false);
        bind(source, entity);
        return source;
    }

    public static Source registerLocal(String owner, EntityModifierDefinition definition) {
        EntitySimulationThread.requireCurrent();
        requireOwner(owner);
        EntityMovementSupport.requireHooks(true, true);
        if (LOCALS.size() >= MAX_LOCAL_SOURCES) {
            throw new IllegalStateException("Local modifier registration limit reached: " + MAX_LOCAL_SOURCES);
        }
        for (Source existing : LOCALS) {
            if (existing.owner.equals(owner) && existing.definition.key.equals(definition.key)) {
                throw new IllegalArgumentException("Duplicate local modifier: " + definition.key);
            }
        }
        Source source = new Source(owner, definition, true);
        LOCALS.add(source);
        return source;
    }

    public static void synchronize(World world, EntityPlayer player) {
        if (!EntitySimulationThread.isCurrent() || GROUPS.isEmpty() && LOCALS.isEmpty()) {
            return;
        }
        for (BindingGroup group : new ArrayList<>(GROUPS.values())) {
            EntityLiving target = group.entity;
            boolean remote = group.world.multiplayerWorld;
            boolean stale = remote || target.worldObj != group.world || group.world != world || target.isDead
                    || target.health <= 0;
            for (Source source : new ArrayList<>(group.sources)) {
                if (!source.ownerCurrent()) {
                    source.close();
                } else if (stale || source.local && target != player) {
                    if (source.local) {
                        unbind(source,
                                target.isDead || target.health <= 0
                                        ? "death"
                                        : remote ? "unsupported_environment" : "world_changed");
                    } else {
                        close(source,
                                target.isDead || target.health <= 0
                                        ? "death"
                                        : remote ? "unsupported_environment" : "world_changed");
                    }
                }
            }
        }
        for (Source source : new ArrayList<>(LOCALS)) {
            if (!source.ownerCurrent()) {
                source.close();
                continue;
            }
            source.environment = world != null && world.multiplayerWorld ? "unsupported_environment" : null;
            if (source.awaitingWorld != world) {
                source.awaitingTarget = null;
                source.awaitingWorld = world;
            }
            if (source.group == null && source.environment == null && player != null && player.worldObj == world
                    && !player.isDead && player.health > 0 && source.awaitingTarget != player) {
                try {
                    EntityMovementSupport.require(player, source.definition);
                    bind(source, player);
                } catch (IllegalArgumentException | IllegalStateException error) {
                    source.awaitingTarget = player;
                    source.failed = true;
                    source.failurePhase = "binding";
                    source.failure = error.getMessage();
                    report(source, "binding", error);
                }
            }
            if (player == null) {
                source.awaitingTarget = null;
            }
        }
    }

    public static List<Source> eligible(EntityLiving entity) {
        BindingGroup group = GROUPS.get(entity);
        if (group == null || group.world != entity.worldObj || suspension(entity) != null
                || EntityMovementSupport.reason(entity) != null) {
            return Collections.emptyList();
        }
        List<Source> result = new ArrayList<>();
        for (Source source : group.sources) {
            if (source.isEligible()) {
                result.add(source);
            }
        }
        Collections.sort(result, PRODUCER_ORDER);
        return result;
    }

    public static boolean hasSources(EntityLiving entity) {
        BindingGroup group = GROUPS.get(entity);
        return group != null && !group.sources.isEmpty();
    }

    public static List<Source> sources(EntityLiving entity) {
        BindingGroup group = GROUPS.get(entity);
        if (group == null) {
            return Collections.emptyList();
        }
        List<Source> result = new ArrayList<>(group.sources);
        Collections.sort(result, PRODUCER_ORDER);
        return Collections.unmodifiableList(result);
    }

    public static EntityResolvedRules rules(EntityLiving entity) {
        BindingGroup group = GROUPS.get(entity);
        if (group == null || suspension(entity) != null || group.world != entity.worldObj
                || EntityMovementSupport.reason(entity) != null) {
            return EntityResolvedRules.EMPTY;
        }
        if (group.cachedRevision != group.revision) {
            List<EntityRuleSource> values = new ArrayList<>();
            for (Source source : group.sources) {
                if (source.isEligible()) {
                    values.add(source.ruleSnapshot(source.definition.rules));
                }
            }
            group.cached = EntityRuleResolver.resolve(values);
            group.cachedRevision = group.revision;
        }
        return group.cached;
    }

    public static long nextTick(EntityLiving entity) {
        BindingGroup group = GROUPS.get(entity);
        return group == null ? 0 : ++group.tick;
    }

    public static String suspension(EntityLiving entity) {
        if (entity.isDead || entity.health <= 0) {
            return "dead";
        }
        if (entity.ridingEntity != null || entity.riddenByEntity != null) {
            return "mounted";
        }
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isPlayerSleeping()) {
            return "sleeping";
        }
        return null;
    }

    private static void bind(Source source, EntityLiving entity) {
        BindingGroup group = GROUPS.get(entity);
        if (group != null && group.sources.size() >= EntityRuleResolver.MAX_SOURCES) {
            throw new IllegalStateException("Entity modifier source limit reached: " + EntityRuleResolver.MAX_SOURCES);
        }
        int worldCount = 0;
        for (BindingGroup entry : GROUPS.values()) {
            if (entry.world == entity.worldObj) {
                worldCount += entry.sources.size();
            }
        }
        if (worldCount >= MAX_WORLD_SOURCES) {
            throw new IllegalStateException("World modifier source limit reached: " + MAX_WORLD_SOURCES);
        }
        if (group != null) {
            for (Source existing : group.sources) {
                if (existing.owner.equals(source.owner) && existing.definition.key.equals(source.definition.key)) {
                    throw new IllegalArgumentException("Duplicate entity modifier: " + source.definition.key);
                }
            }
        } else {
            group = new BindingGroup(entity);
            GROUPS.put(entity, group);
        }
        source.group = group;
        source.awaitingTarget = null;
        source.failed = false;
        source.observerFailed = false;
        source.failure = null;
        source.failurePhase = null;
        source.bindingRevision++;
        group.sources.add(source);
        group.revision++;
        initializeState(source);
    }

    private static void initializeState(Source source) {
        try {
            source.state = source.definition.callbacks.stateFactory.create();
            source.lastMovement = EntityMotionState.capture(source.group.entity);
            if (source.definition.callbacks.onAttach != null) {
                source.definition.callbacks.onAttach
                        .call(source.context(source.lastMovement, null, Collections.<String, Object>emptyMap(), false));
            }
        } catch (Throwable error) {
            source.fail("onAttach", error);
        }
    }

    private static void unbind(Source source, String reason) {
        BindingGroup group = source.group;
        if (group == null) {
            return;
        }
        EntityModifierContext detached = new EntityModifierContext(null, null, group.tick, group.generation,
                source.state, false, source.lastMovement, null, Collections.<String, Object>emptyMap());
        source.group = null;
        source.state = null;
        source.lastMovement = null;
        source.bindingRevision++;
        group.sources.remove(source);
        group.revision++;
        if (group.sources.isEmpty()) {
            GROUPS.remove(group.entity);
        }
        if (source.ownerCurrent() && source.definition.callbacks.onDetach != null) {
            try {
                source.definition.callbacks.onDetach.call(detached, reason);
            } catch (Throwable error) {
                report(source, "onDetach", error);
            }
        }
    }

    private static void close(Source source, String reason) {
        if (!source.closed) {
            source.closed = true;
            LOCALS.remove(source);
            source.awaitingTarget = null;
            source.awaitingWorld = null;
            unbind(source, reason);
        }
    }

    private static void requireOwner(String owner) {
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity modifiers require a script owner");
        }
    }

    private static void report(Source source, String phase, Throwable error) {
        RuntimeLogs.log(LogLevel.ERROR, source.owner,
                source.definition.key + " " + phase + " disabled after error: " + error.getMessage());
    }

    public static final class Source implements AutoCloseable {
        private final String owner;
        private final long ownerGeneration;
        private final boolean local;
        private EntityModifierDefinition definition;
        private BindingGroup group;
        private EntityPlayer awaitingTarget;
        private World awaitingWorld;
        private Object state;
        private EntityMotionState lastMovement;
        private long bindingRevision;
        private boolean closed;
        private boolean failed;
        private boolean observerFailed;
        private String failure;
        private String failurePhase;
        private String environment;

        private Source(String owner, EntityModifierDefinition definition, boolean local) {
            this.owner = owner;
            this.ownerGeneration = ScriptResourceTracker.ownerGeneration(owner);
            this.definition = definition;
            this.local = local;
        }

        public String owner() {
            return owner;
        }

        public EntityModifierDefinition definition() {
            return definition;
        }

        public long bindingRevision() {
            return bindingRevision;
        }

        public boolean observerFailed() {
            return observerFailed;
        }

        public boolean failed() {
            return failed;
        }

        public EntityLiving target() {
            return group == null ? null : group.entity;
        }

        public boolean isValid() {
            return !closed && ownerCurrent() && (local || group != null && !group.entity.isDead
                    && group.entity.health > 0 && group.entity.worldObj == group.world);
        }

        public void remember(EntityMotionState movement) {
            lastMovement = movement;
        }

        public boolean isEligible() {
            return isValid() && group != null && definition.enabled && !failed && environment == null
                    && group.world == group.entity.worldObj && suspension(group.entity) == null
                    && EntityMovementSupport.reason(group.entity) == null;
        }

        public boolean ownerCurrent() {
            return ScriptResourceTracker.ownerGeneration(owner) == ownerGeneration;
        }

        public void update(EntityModifierDefinition next) {
            requireValid();
            if (!definition.key.equals(next.key)) {
                throw new IllegalArgumentException("Modifier keys cannot be changed");
            }
            if (group != null) {
                EntityMovementSupport.require(group.entity, next);
            } else {
                EntityMovementSupport.requireHooks(true, true);
            }
            boolean recover = failed && failedCallbackChanged(next.callbacks);
            if (definition.callbacks.onAfterMotion != next.callbacks.onAfterMotion) {
                observerFailed = false;
                if (!failed) {
                    failure = null;
                }
            }
            definition = next;
            changed();
            if (recover) {
                retry();
            }
        }

        public void setEnabled(boolean enabled) {
            update(definition.withEnabled(enabled));
        }

        public void retry() {
            requireValid();
            observerFailed = false;
            failure = null;
            failurePhase = null;
            awaitingTarget = null;
            if (failed) {
                failed = false;
                bindingRevision++;
                if (group != null) {
                    initializeState(this);
                }
            }
            changed();
        }

        public void fail(String phase, Throwable error) {
            if (!failed) {
                failed = true;
                failurePhase = phase;
                failure = phase + ": " + error.getMessage();
                changed();
                report(this, phase, error);
            }
        }

        public void failObserver(Throwable error) {
            if (!observerFailed) {
                observerFailed = true;
                failure = "onAfterMotion: " + error.getMessage();
                report(this, "onAfterMotion", error);
            }
        }

        public EntityRuleSource ruleSnapshot(EntityRuleSet rules) {
            return new EntityRuleSource(owner, definition.key, definition.priority, rules);
        }

        public EntityModifierContext context(EntityMotionState movement, Map<String, Object> input,
                Map<String, Object> intent, boolean ownsMotion) {
            if (group == null) {
                throw new IllegalStateException("Modifier binding is unavailable");
            }
            lastMovement = movement;
            return new EntityModifierContext(group.entity, group.world, group.tick, group.generation, state, ownsMotion,
                    movement, input, intent);
        }

        public Map<String, Object> status() {
            EntitySimulationThread.requireCurrent();
            Map<String, Object> status = new LinkedHashMap<>();
            String suspended = group == null ? null : suspension(group.entity);
            status.put("state",
                    !isValid()
                            ? "closed"
                            : environment != null
                                    ? environment
                                    : failed ? "failed" : group == null ? "awaiting_player" : "attached");
            status.put("enabled", definition.enabled);
            status.put("applied", isEligible());
            status.put("suspended", suspended != null);
            if (suspended != null) {
                status.put("reason", suspended);
            }
            if (failure != null) {
                status.put("error", failure);
            }
            status.put("observerFailed", observerFailed);
            status.put("key", definition.key.toString());
            status.put("owner", owner);
            if (group != null) {
                status.put("generation", group.generation);
            }
            return status;
        }

        public void close() {
            EntitySimulationThread.requireCurrent();
            EntityModifierRuntime.close(this, "closed");
        }

        private void requireValid() {
            EntitySimulationThread.requireCurrent();
            if (!isValid()) {
                throw new IllegalStateException("Modifier source generation is closed");
            }
        }

        private void changed() {
            if (group != null) {
                group.revision++;
            }
        }

        private boolean failedCallbackChanged(EntityModifierCallbacks callbacks) {
            return "onMotion".equals(failurePhase) && definition.callbacks.onMotion != callbacks.onMotion
                    || "onForces".equals(failurePhase) && definition.callbacks.onForces != callbacks.onForces
                    || "onAttach".equals(failurePhase) && definition.callbacks.onAttach != callbacks.onAttach;
        }
    }

    private static final class BindingGroup {
        private final EntityLiving entity;
        private final World world;
        private final long generation = ++generationSequence;
        private final List<Source> sources = new ArrayList<>();
        private long tick;
        private long revision;
        private long cachedRevision = -1;
        private EntityResolvedRules cached;

        private BindingGroup(EntityLiving entity) {
            this.entity = entity;
            this.world = entity.worldObj;
        }
    }
}
