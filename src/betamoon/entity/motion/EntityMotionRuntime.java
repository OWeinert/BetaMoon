package betamoon.entity.motion;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.MotionInputObservation;
import betamoon.entity.modifier.EntityModifierContext;
import betamoon.entity.modifier.EntityModifierDefinition;
import betamoon.entity.modifier.EntityModifierRuntime.Source;
import betamoon.entity.modifier.EntityModifierRuntime;
import betamoon.entity.modifier.EntityMovementRule;
import betamoon.entity.modifier.EntityResolvedRules;
import betamoon.entity.modifier.EntityRuleResolver;
import betamoon.entity.modifier.EntityRuleSet;
import betamoon.entity.modifier.EntityRuleSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.World;

/**
 * One prepared/committed decision per native living step; Minecraft remains the
 * collision solver.
 */
public final class EntityMotionRuntime {
    private static final IdentityHashMap<EntityLiving, Step> STEPS = new IdentityHashMap<>();

    private EntityMotionRuntime() {
    }

    public static void preparePlayer(EntityPlayerSP entity) {
        if (EntityModifierRuntime.hasSources(entity)) {
            Step step = prepare(entity);
            if (step != null) {
                MotionInputObservation.applySneak(entity, step.rules);
            }
        }
    }

    public static boolean jumpIntent(EntityLiving entity, boolean nativeJump) {
        Step step = prepare(entity);
        if (step == null) {
            return nativeJump;
        }
        commitBeforeJump(step, nativeJump);
        if (step.manual()) {
            return false;
        }
        EntityMovementRule enabled = entity.isInWater() || entity.handleLavaMovement()
                ? EntityMovementRule.SWIM_LIFT_ENABLED
                : EntityMovementRule.JUMP_ENABLED;
        return step.rules.booleanValue(enabled, nativeJump);
    }

    private static Step prepare(EntityLiving entity) {
        Step prepared = STEPS.get(entity);
        if (prepared != null) {
            return prepared;
        }
        List<Source> sources = EntityModifierRuntime.eligible(entity);
        if (sources.isEmpty() || EntityMovementSupport.reason(entity) != null) {
            return null;
        }
        EntityModifierRuntime.nextTick(entity);
        Step step;
        try {
            step = new Step(entity, sources);
        } catch (IllegalArgumentException error) {
            for (Source source : sources) {
                source.fail("movement_state", error);
            }
            return null;
        }
        STEPS.put(entity, step);
        selectProducer(step);
        step.rules = resolve(step);
        return step;
    }

    private static void selectProducer(Step step) {
        step.winner = null;
        step.command = null;
        for (PreparedSource candidate : step.sources) {
            if (!candidate.current() || !candidate.source.isEligible()
                    || candidate.definition.callbacks.onMotion == null) {
                continue;
            }
            try {
                if (!candidate.evaluated) {
                    candidate.command = candidate.definition.callbacks.onMotion
                            .call(context(step, candidate, step.initial, false));
                    candidate.evaluated = true;
                }
                EntityMotionCommand command = candidate.command;
                if (command == null) {
                    continue;
                }
                EntityMovementSupport.require(step.entity, new EntityModifierDefinition(candidate.definition.key,
                        candidate.definition.priority, true, command.rules, candidate.definition.callbacks));
                if (step.entity instanceof EntityPlayer && command.pitch != null && Math.abs(command.pitch) > 90) {
                    throw new IllegalArgumentException("Player motion pitch must be within -90..90");
                }
                if ((command.yaw != null || command.pitch != null) && step.entity instanceof EntityPlayerSP
                        && ClientInputRuntime.controllers().ownsLook((EntityPlayerSP) step.entity)) {
                    step.blockedReason = "look_owned";
                    continue;
                }
                if (command.mode == EntityMotionCommand.Mode.MANUAL && command.velocity == null
                        && !step.initial.velocity.within(16)) {
                    throw new IllegalArgumentException("Manual retained velocity must be within -16..16");
                }
                step.winner = candidate;
                step.command = command;
                return;
            } catch (Throwable error) {
                candidate.source.fail("onMotion", error);
            }
        }
    }

    private static EntityResolvedRules resolve(Step step) {
        if (step.command == null || step.command.rules.isEmpty()) {
            return EntityModifierRuntime.rules(step.entity);
        }
        List<EntityRuleSource> values = new ArrayList<>();
        for (PreparedSource candidate : step.sources) {
            if (candidate.current() && candidate.source.isEligible()) {
                EntityRuleSet rules = candidate.definition.rules;
                if (candidate == step.winner && step.command != null) {
                    rules = rules.merge(step.command.rules);
                }
                values.add(new EntityRuleSource(candidate.source.owner(), candidate.definition.key,
                        candidate.definition.priority, rules));
            }
        }
        return EntityRuleResolver.resolve(values);
    }

    private static void commitBeforeJump(Step step, boolean nativeJump) {
        if (step.committed) {
            return;
        }
        EntityLiving entity = step.entity;
        MotionVector incoming = new MotionVector(entity.motionX, entity.motionY, entity.motionZ);
        boolean ready = false;
        for (int attempt = 0; attempt <= step.sources.size(); attempt++) {
            if (step.winner != null && (!step.winner.current() || !step.winner.source.isEligible())) {
                selectProducer(step);
            }
            step.rules = resolve(step);
            preparePlayerIntent(step);
            step.intent = nativeIntent(entity);
            MotionVector base = step.command != null && step.command.velocity != null
                    ? step.command.velocity
                    : incoming;
            MotionVector afterJump = anticipatedJump(step, base, nativeJump);
            MotionVector forces = gatherForces(step, afterJump);
            if (forces == null) {
                selectProducer(step);
                continue;
            }
            step.forces = forces;
            step.baseVelocity = base;
            ready = true;
            break;
        }
        if (!ready) {
            step.command = null;
            step.winner = null;
            step.rules = resolve(step);
            step.baseVelocity = incoming;
            step.forces = MotionVector.ZERO;
        }
        preparePlayerIntent(step);
        entity.motionX = step.baseVelocity.x;
        entity.motionY = step.baseVelocity.y;
        entity.motionZ = step.baseVelocity.z;
        if (step.command != null) {
            if (step.command.yaw != null) {
                entity.rotationYaw = step.command.yaw;
            }
            if (step.command.pitch != null) {
                entity.rotationPitch = step.command.pitch;
            }
            if (step.command.fallDistance != null) {
                NativeEntityFields.fallDistance(entity, step.command.fallDistance.floatValue());
            }
        }
        if (!step.rules.booleanValue(EntityMovementRule.TRACK_FALL_DISTANCE, true)) {
            NativeEntityFields.fallDistance(entity, 0);
        }
        step.committed = true;
    }

    private static void preparePlayerIntent(Step step) {
        if (!(step.entity instanceof EntityPlayerSP)) {
            return;
        }
        EntityPlayerSP player = (EntityPlayerSP) step.entity;
        player.movementInput.moveForward = step.playerForward;
        player.movementInput.moveStrafe = step.playerStrafe;
        MotionInputObservation.applySneak(player, step.rules);
        NativeLivingFields.intent(player, player.movementInput.moveForward, player.movementInput.moveStrafe);
        player.ySize = step.playerBodyOffset;
        if (player.movementInput.sneak && step.rules.booleanValue(EntityMovementRule.SNEAK_BODY_ADJUSTMENT, true)
                && player.ySize < 0.2F) {
            player.ySize = 0.2F;
        }
    }

    private static MotionVector anticipatedJump(Step step, MotionVector velocity, boolean jumping) {
        if (step.manual() || !jumping) {
            return velocity;
        }
        EntityLiving entity = step.entity;
        if (entity.isInWater() || entity.handleLavaMovement()) {
            return step.rules.booleanValue(EntityMovementRule.SWIM_LIFT_ENABLED, true)
                    ? new MotionVector(velocity.x, velocity.y + (double) 0.04F, velocity.z)
                    : velocity;
        }
        return entity.onGround && step.rules.booleanValue(EntityMovementRule.JUMP_ENABLED, true)
                ? new MotionVector(velocity.x,
                        step.rules.numberValue(EntityMovementRule.JUMP_MULTIPLIER, (double) 0.42F), velocity.z)
                : velocity;
    }

    private static MotionVector gatherForces(Step step, MotionVector velocity) {
        double x = 0;
        double y = 0;
        double z = 0;
        boolean contributed = false;
        List<PreparedSource> sources = new ArrayList<>(step.sources);
        Collections.sort(sources, Comparator.comparing((PreparedSource entry) -> entry.source.owner())
                .thenComparing(entry -> entry.definition.key.toString()));
        EntityMotionState state = EntityMotionState.capture(step.entity).prepared(velocity, step.command,
                step.rules.booleanValue(EntityMovementRule.TRACK_FALL_DISTANCE, true));
        for (PreparedSource candidate : sources) {
            if (!candidate.current() || !candidate.source.isEligible()
                    || candidate.definition.callbacks.onForces == null) {
                continue;
            }
            try {
                MotionVector force = candidate.definition.callbacks.onForces
                        .call(context(step, candidate, state, candidate == step.winner));
                if (force != null) {
                    if (!force.within(16)) {
                        throw new IllegalArgumentException("Force components must be within -16..16");
                    }
                    x += force.x;
                    y += force.y;
                    z += force.z;
                    contributed = true;
                }
            } catch (Throwable error) {
                candidate.source.fail("onForces", error);
                return null;
            }
        }
        if (!contributed) {
            step.forceStatus = "none";
            return MotionVector.ZERO;
        }
        MotionVector aggregate = new MotionVector(x, y, z);
        MotionVector result = new MotionVector(velocity.x + x, velocity.y + y, velocity.z + z);
        if (!aggregate.within(16) || !result.within(16)) {
            step.forceStatus = "limit_exceeded";
            return MotionVector.ZERO;
        }
        step.forceStatus = "applied";
        return aggregate;
    }

    public static int beforeHeading(EntityLiving entity) {
        Step step = STEPS.get(entity);
        if (step == null) {
            return 0;
        }
        if (!step.committed) {
            commitBeforeJump(step, NativeLivingFields.jumping(entity));
        }
        step.airborne = !entity.onGround;
        step.fluid = entity.isInWater() || entity.handleLavaMovement();
        entity.motionX += step.forces.x;
        entity.motionY += step.forces.y;
        entity.motionZ += step.forces.z;
        if (step.manual()) {
            collide(entity, entity.motionX, entity.motionY, entity.motionZ);
            NativeLivingFields.animate(entity);
            return 1;
        }
        return 0;
    }

    public static void collide(EntityLiving entity, double x, double y, double z) {
        Step step = STEPS.get(entity);
        if (step == null) {
            entity.moveEntity(x, y, z);
            return;
        }
        step.requested = new MotionVector(x, y, z);
        if (step.command != null || "applied".equals(step.forceStatus)) {
            String reason = !step.requested.within(16)
                    ? "work_limit"
                    : EntitySpatialSafety.check(entity, EntitySpatialSafety.swept(entity, step.requested));
            if (reason != null) {
                step.blockedReason = reason;
                x = y = z = 0;
                entity.motionX = entity.motionY = entity.motionZ = 0;
            }
        }
        try {
            entity.moveEntity(x, y, z);
            step.moved = true;
        } catch (RuntimeException | Error failure) {
            step.nativeFailed = true;
            throw failure;
        }
    }

    public static EntityResolvedRules rules(Entity entity) {
        if (!(entity instanceof EntityLiving)) {
            return EntityResolvedRules.EMPTY;
        }
        Step step = STEPS.get(entity);
        return step == null ? EntityModifierRuntime.rules((EntityLiving) entity) : step.rules;
    }

    public static double drag(EntityLiving entity, double nativeValue, boolean vertical) {
        Step step = STEPS.get(entity);
        if (step == null) {
            return nativeValue;
        }
        boolean fluid = step.fluid;
        if (!fluid && !step.airborne) {
            return nativeValue;
        }
        EntityMovementRule rule = fluid
                ? vertical ? EntityMovementRule.FLUID_DRAG_VERTICAL : EntityMovementRule.FLUID_DRAG_HORIZONTAL
                : vertical ? EntityMovementRule.AIR_DRAG_VERTICAL : EntityMovementRule.AIR_DRAG_HORIZONTAL;
        return step.rules.numberValue(rule, nativeValue);
    }

    public static void finish(EntityLiving entity) {
        Step step = STEPS.remove(entity);
        if (step == null || !step.moved || step.nativeFailed || entity.isDead || entity.health <= 0
                || entity.worldObj != step.world) {
            return;
        }
        EntityMotionState movement = EntityMotionState.capture(entity);
        EntityMotionResult result = new EntityMotionResult(step.winner == null ? null : step.winner.source.owner(),
                step.winner == null ? null : step.winner.definition.key.toString(), step.manual() ? "manual" : "native",
                step.requested,
                new MotionVector(entity.posX - step.initial.position.x, entity.posY - step.initial.position.y,
                        entity.posZ - step.initial.position.z),
                step.forces, movement, step.blockedReason, step.forceStatus);
        for (PreparedSource candidate : step.sources) {
            if (candidate.current()) {
                candidate.source.remember(movement);
            }
            if (candidate.current() && !candidate.source.failed() && !candidate.source.observerFailed()
                    && candidate.definition.callbacks.onAfterMotion != null) {
                try {
                    candidate.definition.callbacks.onAfterMotion
                            .call(context(step, candidate, movement, candidate == step.winner), result);
                } catch (Throwable error) {
                    candidate.source.failObserver(error);
                }
            }
        }
    }

    public static void abort(EntityLiving entity) {
        STEPS.remove(entity);
    }

    private static EntityModifierContext context(Step step, PreparedSource source, EntityMotionState movement,
            boolean ownsMotion) {
        return source.source.context(movement, step.input, step.intent, ownsMotion);
    }

    private static Map<String, Object> nativeIntent(EntityLiving entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity instanceof EntityPlayerSP) {
            EntityPlayerSP player = (EntityPlayerSP) entity;
            result.put("forward", player.movementInput.moveForward);
            result.put("strafe", player.movementInput.moveStrafe);
            result.put("jump", player.movementInput.jump);
            result.put("sneak", player.movementInput.sneak);
        } else {
            result.put("forward", NativeLivingFields.forward(entity));
            result.put("strafe", NativeLivingFields.strafe(entity));
            result.put("jump", NativeLivingFields.jumping(entity));
            result.put("sneak", entity.isSneaking());
        }
        return Collections.unmodifiableMap(result);
    }

    private static final class PreparedSource {
        private final Source source;
        private final EntityModifierDefinition definition;
        private final long revision;
        private boolean evaluated;
        private EntityMotionCommand command;

        private PreparedSource(Source source) {
            this.source = source;
            definition = source.definition();
            revision = source.bindingRevision();
        }

        private boolean current() {
            return source.isValid() && source.bindingRevision() == revision && source.target() != null;
        }
    }

    private static final class Step {
        private final EntityLiving entity;
        private final World world;
        private final List<PreparedSource> sources = new ArrayList<>();
        private final EntityMotionState initial;
        private final Map<String, Object> input;
        private final float playerForward;
        private final float playerStrafe;
        private final float playerBodyOffset;
        private Map<String, Object> intent;
        private PreparedSource winner;
        private EntityMotionCommand command;
        private EntityResolvedRules rules;
        private MotionVector baseVelocity;
        private MotionVector forces = MotionVector.ZERO;
        private MotionVector requested = MotionVector.ZERO;
        private String forceStatus = "none";
        private String blockedReason;
        private boolean committed;
        private boolean airborne;
        private boolean fluid;
        private boolean moved;
        private boolean nativeFailed;

        private Step(EntityLiving entity, List<Source> eligible) {
            this.entity = entity;
            world = entity.worldObj;
            initial = EntityMotionState.capture(entity);
            rules = EntityModifierRuntime.rules(entity);
            for (Source source : eligible) {
                sources.add(new PreparedSource(source));
            }
            input = entity instanceof EntityPlayerSP ? MotionInputObservation.take((EntityPlayerSP) entity) : null;
            intent = nativeIntent(entity);
            playerForward = entity instanceof EntityPlayerSP ? ((EntityPlayerSP) entity).movementInput.moveForward : 0;
            playerStrafe = entity instanceof EntityPlayerSP ? ((EntityPlayerSP) entity).movementInput.moveStrafe : 0;
            playerBodyOffset = entity.ySize;
        }

        private boolean manual() {
            return command != null && command.mode == EntityMotionCommand.Mode.MANUAL;
        }
    }
}
