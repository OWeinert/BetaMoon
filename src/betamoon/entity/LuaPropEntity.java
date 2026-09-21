package betamoon.entity;

import betamoon.luaapi.entity.EntityInteraction;
import betamoon.luaapi.utils.InteractionOutcome;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/** General nonliving bridge. Minecraft calls entityInit before this class's fields exist. */
public final class LuaPropEntity extends Entity implements MultipartEntity {
    private final EntityInstanceState state = new EntityInstanceState(EntityKind.PROP);
    private final EntityPartManager parts = new EntityPartManager(this);
    private EntityTypeDefinition appliedDefinition;

    public LuaPropEntity(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        // Do not read subclass state here: Entity invokes this from its constructor.
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
        EntityTypeDefinition definition = state.definition();
        if (definition == null) {
            EntityLifecycleEvents.definitionMissing(this, state);
            EntityMotion.stationaryTick(this);
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
        if (definition.physics.mode == EntityPhysicsDefinition.Mode.STATIC) {
            EntityMotion.stationaryTick(this);
            EntityTick.run(this, definition);
        } else {
            super.onUpdate();
            EntityTick.run(this, definition);
            if (!isDead) {
                moveDynamic(definition.physics);
            }
        }
        parts.sync(definition);
        state.tickAuxiliary(this, definition);
    }

    private void moveDynamic(EntityPhysicsDefinition physics) {
        motionY -= physics.gravity;
        if (!Double.isFinite(motionX) || !Double.isFinite(motionY) || !Double.isFinite(motionZ)) {
            motionX = motionY = motionZ = 0;
            return;
        }
        motionX = Math.max(-16, Math.min(16, motionX));
        motionY = Math.max(-16, Math.min(16, motionY));
        motionZ = Math.max(-16, Math.min(16, motionZ));
        moveEntity(motionX, motionY, motionZ);
        if (onGround && motionY < 0) {
            motionY = -motionY * physics.bounce;
        }
        double horizontalDrag = onGround ? physics.groundFriction : physics.drag;
        motionX *= horizontalDrag;
        motionY *= physics.drag;
        motionZ *= horizontalDrag;
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

    @Override
    public boolean canBeCollidedWith() {
        applyDefinition();
        EntityTypeDefinition definition = state.definition();
        return !isDead && definition != null && definition.body.targetable;
    }

    @Override
    public double getMountedYOffset() {
        EntityTypeDefinition definition = state.definition();
        return definition != null && definition.mount != null
                ? definition.mount.passengerOffset : super.getMountedYOffset();
    }

    @Override
    public boolean canBePushed() {
        applyDefinition();
        EntityTypeDefinition definition = state.definition();
        return !isDead && definition != null && definition.lifecycle == EntityLifecycle.NATIVE
                && definition.physics.mode == EntityPhysicsDefinition.Mode.DYNAMIC
                && definition.physics.pushable;
    }

    @Override
    public boolean interact(EntityPlayer player) {
        return EntityInteraction.evaluate(this, player) != InteractionOutcome.PASS;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        state.read(tag);
        applyDefinition();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        state.write(tag);
    }

    private void applyDefinition() {
        EntityTypeDefinition definition = state.definition();
        if (definition == null || definition == appliedDefinition) {
            return;
        }
        setSize(definition.width, definition.height);
        setPosition(posX, posY, posZ);
        appliedDefinition = definition;
    }
}
