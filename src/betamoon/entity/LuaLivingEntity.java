package betamoon.entity;

import betamoon.luaapi.entity.EntityInteraction;
import betamoon.luaapi.utils.InteractionOutcome;
import java.util.Random;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityCreature;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;

/** Native living bridge: Minecraft owns movement, collision, health and pathfinding. */
public final class LuaLivingEntity extends EntityCreature implements MultipartEntity {
    private final EntityInstanceState state = new EntityInstanceState(EntityKind.LIVING);
    private final EntityPartManager parts = new EntityPartManager(this);
    private final EntityAiController aiController = new EntityAiController(this);
    private EntityTypeDefinition appliedDefinition;
    private boolean initializedHealth;
    private float manualForward;
    private float manualStrafe;
    private boolean manualJump;
    private boolean checkingDespawn;

    public LuaLivingEntity(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        // Entity calls this before subclass fields are initialized.
    }

    @Override
    public EntityInstanceState entityState() {
        return state;
    }

    @Override
    public boolean setPartOffset(String name, double x, double y, double z) {
        return parts.setOffset(name, x, y, z);
    }

    @Override
    public boolean setInteractionOffset(String name, double x, double y, double z) {
        return parts.setInteractionOffset(name, x, y, z);
    }

    @Override
    public void onUpdate() {
        applyDefinition();
        if (state.definition() == null || worldObj == null) {
            if (state.definition() == null) {
                EntityLifecycleEvents.definitionMissing(this, state);
            }
            EntityMotion.frozenTick(this);
            parts.clear();
            return;
        }
        EntityLifecycleEvents.loaded(this, state);
        if (isDead) {
            return;
        }
        if (state.definition().lifecycle == EntityLifecycle.MANUAL) {
            EntityMotion.manualTick(this);
            if (health > 0) {
                EntityAiTick.run(this, state.definition());
            }
            if (isDead) {
                return;
            }
            EntityTick.run(this, state.definition());
            parts.sync(state.definition());
            state.tickAuxiliary(this, state.definition());
            return;
        }
        if (health > 0) {
            EntityAiTick.run(this, state.definition());
        }
        if (isDead) {
            return;
        }
        if (state.definition().living.ai == LivingDefinition.Ai.MANUAL) {
            EntityTick.run(this, state.definition());
            if (isDead) {
                return;
            }
        }
        if (state.definition().physics.mode == EntityPhysicsDefinition.Mode.STATIC) {
            motionX = motionY = motionZ = 0;
        }
        super.onUpdate();
        if (state.definition().physics.mode == EntityPhysicsDefinition.Mode.STATIC) {
            motionX = motionY = motionZ = 0;
        }
        parts.sync(state.definition());
        if (!isDead && state.definition().living.ai != LivingDefinition.Ai.MANUAL) {
            EntityTick.run(this, state.definition());
        }
        state.tickAuxiliary(this, state.definition());
    }

    @Override
    public void moveEntity(double x, double y, double z) {
        if (state != null && state.definition() != null
                && state.definition().physics.mode == EntityPhysicsDefinition.Mode.STATIC) {
            return;
        }
        super.moveEntity(x, y, z);
    }

    @Override
    public boolean canBePushed() {
        return state == null || state.definition() == null
                || state.definition().lifecycle == EntityLifecycle.NATIVE
                && state.definition().physics.mode != EntityPhysicsDefinition.Mode.STATIC;
    }

    @Override
    public boolean canBeCollidedWith() {
        EntityTypeDefinition definition = state == null ? null : state.definition();
        return !isDead && definition != null && definition.body.targetable;
    }

    @Override
    public double getMountedYOffset() {
        EntityTypeDefinition definition = state.definition();
        return definition != null && definition.mount != null
                ? definition.mount.passengerOffset : super.getMountedYOffset();
    }

    @Override
    public void setEntityDead() {
        if (checkingDespawn) {
            state.removalReason("despawned");
        } else if (posY < -64) {
            state.removalReason("void");
        }
        EntityMounts.detach(this);
        super.setEntityDead();
        if (!state.damageInProgress()) {
            EntityLifecycleEvents.removed(this, state);
        }
        if (parts != null) {
            parts.clear();
        }
    }

    @Override
    public void onDeath(Entity attacker) {
        if (state.deathNotified()) {
            return;
        }
        super.onDeath(attacker);
        EntityLifecycleEvents.died(this, state, attacker);
    }

    @Override
    protected void func_27021_X() {
        checkingDespawn = true;
        try {
            super.func_27021_X();
        } finally {
            checkingDespawn = false;
        }
    }

    @Override
    protected void updatePlayerActionState() {
        EntityTypeDefinition definition = state.definition();
        if (definition != null && definition.living.composed()) {
            aiController.update(definition);
            return;
        }
        if (definition != null && definition.living.ai == LivingDefinition.Ai.MANUAL) {
            moveForward = manualForward * moveSpeed;
            moveStrafing = manualStrafe * moveSpeed;
            isJumping = manualJump;
            return;
        }
        if (definition == null || definition.living.ai == LivingDefinition.Ai.IDLE) {
            moveForward = 0;
            moveStrafing = 0;
            isJumping = false;
            return;
        }
        super.updatePlayerActionState();
    }

    public boolean setManualMovement(float forward, float strafe, boolean jump) {
        EntityTypeDefinition definition = state.definition();
        if (definition == null || definition.living.ai != LivingDefinition.Ai.MANUAL
                && (!definition.living.composed()
                || definition.living.movementStage.mode != EntityAiStageDefinition.Mode.MANUAL)) {
            return false;
        }
        manualForward = forward;
        manualStrafe = strafe;
        manualJump = jump;
        return true;
    }

    public boolean usesNativeNavigation() {
        EntityTypeDefinition definition = state.definition();
        return definition != null && (definition.living.composed() || definition.living.ai != LivingDefinition.Ai.IDLE)
                && definition.living.ai != LivingDefinition.Ai.MANUAL;
    }

    public boolean requestNavigation(int x, int y, int z, float range) {
        return state.definition() != null && state.definition().living.composed()
                && aiController.request(x, y, z, range);
    }

    public boolean cancelNavigation() {
        if (state.definition() == null || !state.definition().living.composed()) {
            return false;
        }
        aiController.cancel();
        return true;
    }

    public String navigationStatus() {
        return state.definition() != null && state.definition().living.composed()
                ? aiController.status() : null;
    }

    public Vec3D navigationWaypoint() {
        return state.definition() != null && state.definition().living.composed()
                ? aiController.waypoint() : null;
    }

    void applyMovement(float forward, float strafe, boolean jump) {
        moveForward = forward;
        moveStrafing = strafe;
        isJumping = jump;
    }

    void applyManualMovement() {
        applyMovement(manualForward * moveSpeed, manualStrafe * moveSpeed, manualJump);
    }

    void performNativeAttack(Entity target) {
        if (canEntityBeSeen(target)) {
            attackEntity(target, target.getDistanceToEntity(this));
        }
    }

    void performStageAttack(Entity target, int damage, int cooldownTicks) {
        if (attackTime == 0 && target.getDistanceToEntity(this) < 2 && canEntityBeSeen(target)) {
            attackTime = cooldownTicks;
            target.attackEntityFrom(this, damage);
        }
    }

    Random aiRandom() {
        return rand;
    }

    float aiMovementSpeed() {
        return moveSpeed;
    }

    float aiPathWeight(int x, int y, int z) {
        return getBlockPathWeight(x, y, z);
    }

    @Override
    protected void func_31026_E() {
        EntityTypeDefinition definition = state.definition();
        if (definition != null && definition.living.ai == LivingDefinition.Ai.WANDER) {
            super.func_31026_E();
        }
    }

    @Override
    public void heal(int amount) {
        EntityTypeDefinition definition = state.definition();
        if (definition != null && health > 0 && amount > 0) {
            health = Math.min(definition.living.maxHealth, health + amount);
            heartsLife = heartsHalvesLife / 2;
        }
    }

    @Override
    protected boolean canDespawn() {
        EntityTypeDefinition definition = state.definition();
        return definition != null && (definition.spawning == null
                ? definition.living.despawn : definition.spawning.nativeDespawn);
    }

    @Override
    protected Entity findPlayerToAttack() {
        EntityTypeDefinition definition = state.definition();
        if (definition == null || definition.living.aggression != LivingDefinition.Aggression.PLAYERS) {
            return null;
        }
        EntityPlayer nearest = worldObj.getClosestPlayerToEntity(this, definition.living.targetRange);
        return nearest != null && canEntityBeSeen(nearest) ? nearest : null;
    }

    @Override
    public boolean attackEntityFrom(Entity attacker, int amount) {
        boolean damaged = EntityDamageEvents.apply(this, attacker, amount,
                applied -> super.attackEntityFrom(attacker, applied));
        EntityTypeDefinition definition = state.definition();
        if (damaged && definition != null && attacker != null && attacker != this
                && definition.living.aggression != LivingDefinition.Aggression.NONE
                && (!definition.living.composed()
                || definition.living.targetStage.mode == EntityAiStageDefinition.Mode.NATIVE)) {
            setTarget(attacker);
        }
        return damaged;
    }

    @Override
    protected void attackEntity(Entity target, float distance) {
        EntityTypeDefinition definition = state.definition();
        if (definition == null || attackTime > 0 || distance >= 2.0f
                || target.boundingBox.maxY <= boundingBox.minY || target.boundingBox.minY >= boundingBox.maxY) {
            return;
        }
        attackTime = definition.living.attackCooldownTicks;
        if (definition.living.attackDamage > 0) {
            target.attackEntityFrom(this, definition.living.attackDamage);
        }
    }

    @Override
    public boolean interact(EntityPlayer player) {
        return EntityInteraction.evaluate(this, player) != InteractionOutcome.PASS;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        state.write(tag);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        state.read(tag);
        if (health <= 0 && state.markDeathNotified()) {
            state.removalReason("death");
        }
        initializedHealth = true;
        applyDefinition();
    }

    private void applyDefinition() {
        EntityTypeDefinition definition = state.definition();
        if (definition == null || definition == appliedDefinition) {
            return;
        }
        setSize(definition.width, definition.height);
        setPosition(posX, posY, posZ);
        moveSpeed = definition.living.movementSpeed;
        if (!initializedHealth) {
            health = definition.living.maxHealth;
            initializedHealth = true;
        }
        setPathToEntity(null);
        aiController.reset();
        appliedDefinition = definition;
    }
}
