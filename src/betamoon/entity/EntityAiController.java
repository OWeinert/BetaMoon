package betamoon.entity;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.MathHelper;
import net.minecraft.src.PathEntity;
import net.minecraft.src.Vec3D;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/** Bounded ground AI for declarations that compose native and manual stages. */
final class EntityAiController {
    private static final int REPLAN_TICKS = 20;
    private static final int STUCK_TICKS = 40;
    private final LuaLivingEntity entity;
    private PathEntity path;
    private Entity pathTarget;
    private boolean explicitRoute;
    private int nextPlanTick;
    private int destinationX;
    private int destinationY;
    private int destinationZ;
    private float destinationRange;
    private boolean hasDestination;
    private int stagnantTicks;
    private double lastDistance = Double.POSITIVE_INFINITY;
    private String status = "idle";

    EntityAiController(LuaLivingEntity entity) {
        this.entity = entity;
    }

    void update(EntityTypeDefinition definition) {
        LivingDefinition ai = definition.living;
        selectTarget(definition, ai);
        if (entity.isDead) {
            return;
        }
        selectPath(definition, ai);
        if (entity.isDead) {
            return;
        }
        attack(definition, ai);
        if (entity.isDead) {
            return;
        }
        move(definition, ai);
    }

    String status() {
        return status;
    }

    Vec3D waypoint() {
        return path == null || path.isFinished() ? null : path.getPosition(entity);
    }

    void cancel() {
        path = null;
        pathTarget = null;
        explicitRoute = false;
        hasDestination = false;
        status = "cancelled";
        entity.applyMovement(0, 0, false);
    }

    boolean request(int x, int y, int z, float range) {
        destinationX = x;
        destinationY = y;
        destinationZ = z;
        destinationRange = range;
        hasDestination = true;
        explicitRoute = true;
        nextPlanTick = entity.ticksExisted + REPLAN_TICKS;
        if (!areaLoaded(x, y, z, range, 8)) {
            path = null;
            explicitRoute = false;
            status = "unavailable";
            return false;
        }
        path = entity.worldObj.getEntityPathToXYZ(entity, x, y, z, range);
        pathTarget = null;
        return beginPath();
    }

    void reset() {
        path = null;
        pathTarget = null;
        explicitRoute = false;
        nextPlanTick = 0;
        hasDestination = false;
        status = "idle";
        stagnantTicks = 0;
        lastDistance = Double.POSITIVE_INFINITY;
    }

    private void selectTarget(EntityTypeDefinition definition, LivingDefinition ai) {
        Entity previous = entity.getTarget();
        if (previous != null && !previous.isEntityAlive()) {
            entity.setTarget(null);
        }
        if (ai.targetStage.mode == EntityAiStageDefinition.Mode.NATIVE) {
            if (entity.getTarget() == null && ai.aggression == LivingDefinition.Aggression.PLAYERS) {
                EntityPlayer nearby = entity.worldObj.getClosestPlayerToEntity(entity, ai.targetRange);
                if (nearby != null && entity.canEntityBeSeen(nearby)) {
                    entity.setTarget(nearby);
                }
            }
        } else {
            TargetDecision decision = EntityAiStageEvents.call(entity, definition, "target", ai.targetStage,
                    status, this::targetDecision, TargetDecision.KEEP);
            if (decision.clear) {
                entity.setTarget(null);
            } else if (decision.target != null) {
                entity.setTarget(decision.target);
            }
        }
        if (entity.getTarget() != previous) {
            path = null;
            pathTarget = null;
            explicitRoute = false;
            nextPlanTick = 0;
            hasDestination = false;
            status = "idle";
        }
    }

    private TargetDecision targetDecision(LuaValue value) {
        if (value.isnil()) {
            return TargetDecision.KEEP;
        }
        if (value.isboolean() && !value.toboolean()) {
            return TargetDecision.CLEAR;
        }
        Entity selected = LuaEntityActionAccess.target(value, entity, "living.ai.stages.target");
        if (selected == null || selected == entity) {
            throw new IllegalArgumentException("target stage must return a live entity in the same world");
        }
        return new TargetDecision(selected, false);
    }

    private void selectPath(EntityTypeDefinition definition, LivingDefinition ai) {
        if (ai.pathStage.mode == EntityAiStageDefinition.Mode.MANUAL) {
            PathDecision decision = EntityAiStageEvents.call(entity, definition, "path", ai.pathStage,
                    status, this::pathDecision, PathDecision.KEEP);
            if (decision.cancel) {
                cancel();
            } else if (decision.destination != null) {
                int[] destination = decision.destination;
                if (!hasDestination || destination[0] != destinationX || destination[1] != destinationY
                        || destination[2] != destinationZ || decision.range != destinationRange
                        || (entity.ticksExisted >= nextPlanTick && path == null
                        && !"reached".equals(status))) {
                    request(destination[0], destination[1], destination[2], decision.range);
                }
            }
            return;
        }
        Entity target = entity.getTarget();
        if (explicitRoute && path != null) {
            return;
        }
        if (target != null && ai.ai != LivingDefinition.Ai.IDLE
                && (pathTarget != target || entity.ticksExisted >= nextPlanTick)) {
            int x = MathHelper.floor_double(target.posX);
            int y = MathHelper.floor_double(target.posY);
            int z = MathHelper.floor_double(target.posZ);
            if (!areaLoaded(x, y, z, ai.targetRange, 16)) {
                path = null;
                pathTarget = target;
                status = "unavailable";
            } else {
                path = entity.worldObj.getPathToEntity(entity, target, ai.targetRange);
                pathTarget = target;
                beginPath();
            }
            nextPlanTick = entity.ticksExisted + REPLAN_TICKS;
        } else if (target == null && ai.ai == LivingDefinition.Ai.WANDER && path == null
                && entity.ticksExisted >= nextPlanTick && entity.aiRandom().nextInt(80) == 0) {
            chooseWanderPath();
        }
    }

    private void chooseWanderPath() {
        int x = 0;
        int y = 0;
        int z = 0;
        float bestWeight = Float.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 10; attempt++) {
            int candidateX = MathHelper.floor_double(entity.posX) + entity.aiRandom().nextInt(13) - 6;
            int candidateY = MathHelper.floor_double(entity.posY) + entity.aiRandom().nextInt(7) - 3;
            int candidateZ = MathHelper.floor_double(entity.posZ) + entity.aiRandom().nextInt(13) - 6;
            float weight = entity.aiPathWeight(candidateX, candidateY, candidateZ);
            if (weight > bestWeight) {
                x = candidateX;
                y = candidateY;
                z = candidateZ;
                bestWeight = weight;
            }
        }
        request(x, y, z, 10);
    }

    private PathDecision pathDecision(LuaValue value) {
        if (value.isnil()) {
            return PathDecision.KEEP;
        }
        if (value.isboolean() && !value.toboolean()) {
            return PathDecision.CANCEL;
        }
        if (!value.istable()) {
            throw new IllegalArgumentException("path stage must return {x, y, z}, false, or nil");
        }
        int x = integer(value.get("x"), "living.ai.stages.path.x", -30000000, 30000000);
        int y = integer(value.get("y"), "living.ai.stages.path.y", 0, 127);
        int z = integer(value.get("z"), "living.ai.stages.path.z", -30000000, 30000000);
        float range = value.get("range").isnil() ? 16
                : (float) number(value.get("range"), "living.ai.stages.path.range");
        if (range <= 0 || range > 32) {
            throw new IllegalArgumentException("path stage range must be > 0 and <= 32");
        }
        return new PathDecision(new int[]{x, y, z}, range, false);
    }

    private void attack(EntityTypeDefinition definition, LivingDefinition ai) {
        Entity target = entity.getTarget();
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        if (ai.attackStage.mode == EntityAiStageDefinition.Mode.NATIVE) {
            entity.performNativeAttack(target);
            return;
        }
        Integer damage = EntityAiStageEvents.call(entity, definition, "attack", ai.attackStage,
                status, value -> attackDecision(value), null);
        if (damage != null && damage > 0) {
            entity.performStageAttack(target, damage, ai.attackCooldownTicks);
        }
    }

    private Integer attackDecision(LuaValue value) {
        if (value.isnil() || value.isboolean() && !value.toboolean()) {
            return null;
        }
        return integer(value, "living.ai.stages.attack", 0, 32767);
    }

    private void move(EntityTypeDefinition definition, LivingDefinition ai) {
        if (ai.movementStage.mode == EntityAiStageDefinition.Mode.MANUAL) {
            Movement decision = EntityAiStageEvents.call(entity, definition, "movement", ai.movementStage,
                    status, this::movementDecision, null);
            if (decision == null && EntityAiStageEvents.disabled(definition, "movement")) {
                decision = new Movement(0, 0, false);
            }
            if (decision != null) {
                entity.setManualMovement(decision.forward, decision.strafe, decision.jump);
            }
            entity.applyManualMovement();
            return;
        }
        Vec3D next = waypoint();
        if (next == null) {
            entity.applyMovement(0, 0, false);
            return;
        }
        double distance = next.squareDistanceTo(entity.posX, next.yCoord, entity.posZ);
        double reach = entity.width * 2.0;
        while (distance < reach * reach) {
            path.incrementPathIndex();
            if (path.isFinished()) {
                path = null;
                explicitRoute = false;
                status = "reached";
                entity.applyMovement(0, 0, false);
                return;
            }
            next = path.getPosition(entity);
            distance = next.squareDistanceTo(entity.posX, next.yCoord, entity.posZ);
        }
        if (distance < lastDistance - 0.01) {
            stagnantTicks = 0;
        } else if (++stagnantTicks >= STUCK_TICKS) {
            path = null;
            explicitRoute = false;
            status = "blocked";
            entity.applyMovement(0, 0, false);
            return;
        }
        lastDistance = distance;
        float facing = (float) (Math.atan2(next.zCoord - entity.posZ,
                next.xCoord - entity.posX) * 180.0 / Math.PI) - 90;
        float turn = (facing - entity.rotationYaw + 540) % 360 - 180;
        entity.rotationYaw += Math.max(-30, Math.min(30, turn));
        entity.applyMovement(entity.aiMovementSpeed(), 0, next.yCoord > entity.boundingBox.minY + 0.5);
    }

    private Movement movementDecision(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        if (value.isboolean() && !value.toboolean()) {
            return new Movement(0, 0, false);
        }
        if (!value.istable()) {
            throw new IllegalArgumentException("movement stage must return {forward, strafe, jump}, false, or nil");
        }
        float forward = (float) number(value.get("forward"), "living.ai.stages.movement.forward");
        float strafe = (float) number(value.get("strafe"), "living.ai.stages.movement.strafe");
        if (Math.abs(forward) > 1 || Math.abs(strafe) > 1 || !value.get("jump").isboolean()) {
            throw new IllegalArgumentException("movement stage requires forward/strafe within -1..1 and boolean jump");
        }
        return new Movement(forward, strafe, value.get("jump").toboolean());
    }

    private boolean beginPath() {
        if (path == null || path.pathLength == 0) {
            path = null;
            explicitRoute = false;
            status = "blocked";
            return false;
        }
        status = "moving";
        stagnantTicks = 0;
        lastDistance = Double.POSITIVE_INFINITY;
        return true;
    }

    private boolean areaLoaded(int x, int y, int z, float range, int cacheMargin) {
        if (y < 0 || y > 127 || !entity.worldObj.blockExists(x, y, z)) {
            return false;
        }
        int radius = (int) (range + cacheMargin);
        int currentX = MathHelper.floor_double(entity.posX);
        int currentZ = MathHelper.floor_double(entity.posZ);
        return entity.worldObj.checkChunksExist(currentX - radius, 0, currentZ - radius,
                currentX + radius, 127, currentZ + radius);
    }

    private static final class TargetDecision {
        private static final TargetDecision KEEP = new TargetDecision(null, false);
        private static final TargetDecision CLEAR = new TargetDecision(null, true);
        private final Entity target;
        private final boolean clear;

        private TargetDecision(Entity target, boolean clear) {
            this.target = target;
            this.clear = clear;
        }
    }

    private static final class PathDecision {
        private static final PathDecision KEEP = new PathDecision(null, 0, false);
        private static final PathDecision CANCEL = new PathDecision(null, 0, true);
        private final int[] destination;
        private final float range;
        private final boolean cancel;

        private PathDecision(int[] destination, float range, boolean cancel) {
            this.destination = destination;
            this.range = range;
            this.cancel = cancel;
        }
    }

    private static final class Movement {
        private final float forward;
        private final float strafe;
        private final boolean jump;

        private Movement(float forward, float strafe, boolean jump) {
            this.forward = forward;
            this.strafe = strafe;
            this.jump = jump;
        }
    }
}
