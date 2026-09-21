package betamoon.entity;

import betamoon.luaapi.entity.EntityInteraction;
import betamoon.luaapi.utils.InteractionOutcome;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;

/** Native projectile bridge with deterministic per-tick segment collision. */
public final class LuaProjectileEntity extends Entity implements MultipartEntity {
    private static final double MAX_MOTION_PER_TICK = 16;
    private static final int MAX_IMPACTS_PER_TICK = 4;
    private static final double IMPACT_EPSILON = 0.001;

    private final EntityInstanceState state = new EntityInstanceState(EntityKind.PROJECTILE);
    private final EntityPartManager parts = new EntityPartManager(this);
    private final StableEntityReference stableOwner = new StableEntityReference();
    private Entity owner;
    private boolean stuck;

    public LuaProjectileEntity(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        // Entity invokes this before the bridge fields are initialized.
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

    public void setOwner(Entity owner) {
        this.owner = owner;
        stableOwner.set(owner);
    }

    public Entity getOwner() {
        if (owner != null && (owner.isDead || owner.worldObj != worldObj)) {
            owner = null;
        }
        if (owner == null) {
            owner = stableOwner.resolve(worldObj);
        }
        return owner;
    }

    public String getOwnerIdentity() {
        return stableOwner.token();
    }

    @Override
    public void onUpdate() {
        EntityTypeDefinition definition = state.definition();
        if (definition == null || worldObj == null) {
            if (definition == null) {
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
        if (definition.lifecycle == EntityLifecycle.MANUAL) {
            EntityMotion.manualTick(this);
            EntityTick.run(this, definition);
            parts.sync(definition);
            state.tickAuxiliary(this, definition);
            return;
        }
        super.onUpdate();
        parts.sync(definition);
        EntityTick.run(this, definition);
        if (isDead) {
            return;
        }
        ProjectileDefinition policy = definition.projectile;
        if (ticksExisted >= policy.lifetimeTicks) {
            state.removalReason("expired");
            setEntityDead();
            return;
        }
        if (definition.physics.mode == EntityPhysicsDefinition.Mode.STATIC) {
            motionX = motionY = motionZ = 0;
            state.tickAuxiliary(this, definition);
            return;
        }
        if (stuck || worldObj.multiplayerWorld) {
            state.tickAuxiliary(this, definition);
            return;
        }
        double speedSquared = motionX * motionX + motionY * motionY + motionZ * motionZ;
        if (!Double.isFinite(speedSquared) || speedSquared > MAX_MOTION_PER_TICK * MAX_MOTION_PER_TICK) {
            state.removalReason("invalid_motion");
            setEntityDead();
            return;
        }

        advance(definition);
        if (isDead || stuck) {
            state.tickAuxiliary(this, definition);
            return;
        }
        motionX *= policy.drag;
        motionY = motionY * policy.drag - policy.gravity;
        motionZ *= policy.drag;
        if (policy.alignToVelocity) {
            updateAngles();
        }
        state.tickAuxiliary(this, definition);
    }

    @Override
    public void setEntityDead() {
        if (posY < -64) {
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
    public boolean attackEntityFrom(Entity attacker, int amount) {
        return EntityDamageEvents.apply(this, attacker, amount,
                applied -> super.attackEntityFrom(attacker, applied));
    }

    private void advance(EntityTypeDefinition definition) {
        Set<Entity> ignored = new HashSet<>();
        double remaining = 1;
        for (int count = 0; count < MAX_IMPACTS_PER_TICK && remaining > 0.000001 && !isDead && !stuck; count++) {
            Vec3D start = Vec3D.createVector(posX, posY, posZ);
            Vec3D end = Vec3D.createVector(posX + motionX * remaining, posY + motionY * remaining,
                    posZ + motionZ * remaining);
            if (!worldObj.blockExists((int) Math.floor(end.xCoord), 64,
                    (int) Math.floor(end.zCoord))) {
                state.removalReason("unloaded_chunk");
                setEntityDead();
                return;
            }
            ProjectileCollision.Hit hit = ProjectileCollision.trace(worldObj, this, getOwner(),
                    ticksExisted <= definition.projectile.ownerGraceTicks, ignored, start, end);
            if (hit == null) {
                setPosition(end.xCoord, end.yCoord, end.zCoord);
                return;
            }
            double length = start.distanceTo(end);
            double travelled = start.distanceTo(hit.position);
            remaining *= length < IMPACT_EPSILON ? 0 : Math.max(0, 1 - travelled / length);
            setPosition(hit.position.xCoord, hit.position.yCoord, hit.position.zCoord);
            ProjectileImpact.Action action = ProjectileImpact.evaluate(this, hit, definition);
            if (isDead) {
                return;
            }
            applyImpact(action, hit, definition);
            if (action == ProjectileImpact.Action.CONTINUE && hit.entity == null) {
                setPosition(end.xCoord, end.yCoord, end.zCoord);
                return;
            }
            if (hit.entity != null) {
                ignored.add(hit.entity);
            }
            if (action == ProjectileImpact.Action.BOUNCE || action == ProjectileImpact.Action.CONTINUE) {
                nudgeAlongMotion();
            }
        }
    }

    private void nudgeAlongMotion() {
        double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (length > IMPACT_EPSILON) {
            setPosition(posX + motionX / length * IMPACT_EPSILON, posY + motionY / length * IMPACT_EPSILON,
                    posZ + motionZ / length * IMPACT_EPSILON);
        }
    }

    private void applyImpact(ProjectileImpact.Action action, ProjectileCollision.Hit hit,
            EntityTypeDefinition definition) {
        if (action == ProjectileImpact.Action.DEFAULT) {
            if (hit.entity != null && definition.projectile.damage > 0) {
                EntityDamageEvents.withOrigin(this, "projectile",
                        () -> hit.entity.attackEntityFrom(getOwner(), definition.projectile.damage));
            }
            state.removalReason("impact");
            setEntityDead();
        } else if (action == ProjectileImpact.Action.REMOVE) {
            state.removalReason("impact");
            setEntityDead();
        } else if (action == ProjectileImpact.Action.STICK) {
            stuck = true;
            motionX = motionY = motionZ = 0;
        } else if (action == ProjectileImpact.Action.BOUNCE) {
            bounce(hit.nativeHit.sideHit);
        }
    }

    private void bounce(int side) {
        if (side == 0 || side == 1) {
            motionY = -motionY;
        } else if (side == 2 || side == 3) {
            motionZ = -motionZ;
        } else if (side == 4 || side == 5) {
            motionX = -motionX;
        } else {
            motionX = -motionX;
            motionY = -motionY;
            motionZ = -motionZ;
        }
    }

    private void updateAngles() {
        double horizontal = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (horizontal + Math.abs(motionY) < 0.000001) {
            return;
        }
        rotationYaw = (float) Math.toDegrees(Math.atan2(motionX, motionZ));
        rotationPitch = (float) Math.toDegrees(Math.atan2(motionY, horizontal));
    }

    @Override
    public boolean canBeCollidedWith() {
        EntityTypeDefinition definition = state.definition();
        return !isDead && definition != null && definition.body.targetable;
    }

    @Override
    public boolean interact(EntityPlayer player) {
        return EntityInteraction.evaluate(this, player) != InteractionOutcome.PASS;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        state.read(tag);
        owner = null;
        stableOwner.read(tag, "BetaMoonProjectileOwnerKind", "BetaMoonProjectileOwner");
        if (stableOwner.token() == null) {
            stableOwner.set("player", tag.getString("BetaMoonOwnerName"));
        }
        stuck = tag.getBoolean("BetaMoonStuck");
        ticksExisted = tag.getInteger("BetaMoonAge");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        state.write(tag);
        stableOwner.write(tag, "BetaMoonProjectileOwnerKind", "BetaMoonProjectileOwner");
        tag.setBoolean("BetaMoonStuck", stuck);
        tag.setInteger("BetaMoonAge", ticksExisted);
    }
}
