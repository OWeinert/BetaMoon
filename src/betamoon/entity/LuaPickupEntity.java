package betamoon.entity;

import betamoon.luaapi.entity.EntityInteraction;
import betamoon.luaapi.utils.InteractionOutcome;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/** Native dropped-item movement and inventory pickup with a stable Lua type. */
public final class LuaPickupEntity extends EntityItem implements TypedEntity {
    private final EntityInstanceState state = new EntityInstanceState(EntityKind.PICKUP);
    private EntityTypeDefinition appliedDefinition;
    private boolean pickupReactionDelivered;
    private boolean collecting;

    public LuaPickupEntity(World world) {
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
    public void onUpdate() {
        applyDefinition();
        EntityTypeDefinition definition = state.definition();
        if (definition == null || worldObj == null || item == null) {
            if (definition == null) {
                EntityLifecycleEvents.definitionMissing(this, state);
            }
            EntityMotion.frozenTick(this);
            return;
        }
        EntityLifecycleEvents.loaded(this, state);
        if (isDead) {
            return;
        }
        if (definition.lifecycle == EntityLifecycle.MANUAL) {
            EntityMotion.manualTick(this);
            EntityTick.run(this, definition);
            state.tickAuxiliary(this, definition);
            return;
        }
        super.onUpdate();
        if (isDead) {
            return;
        }
        if (definition.physics.mode == EntityPhysicsDefinition.Mode.STATIC) {
            motionX = motionY = motionZ = 0;
        }
        EntityTick.run(this, definition);
        state.tickAuxiliary(this, definition);
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
    public void setEntityDead() {
        if (posY < -64) {
            state.removalReason("void");
        } else if (item != null && item.stackSize <= 0) {
            state.removalReason("collected");
        } else if (age >= 6000) {
            state.removalReason("expired");
        }
        EntityMounts.detach(this);
        super.setEntityDead();
        if (!collecting && !state.damageInProgress()) {
            EntityLifecycleEvents.removed(this, state);
        }
    }

    @Override
    public boolean attackEntityFrom(Entity attacker, int amount) {
        return EntityDamageEvents.apply(this, attacker, amount,
                applied -> super.attackEntityFrom(attacker, applied));
    }

    @Override
    public void onCollideWithPlayer(EntityPlayer player) {
        EntityTypeDefinition definition = state.definition();
        if (isDead || item == null || definition == null || worldObj == null || worldObj.multiplayerWorld
                || definition.lifecycle == EntityLifecycle.MANUAL) {
            return;
        }
        collectInto(player);
    }

    public boolean collectInto(EntityPlayer player) {
        EntityTypeDefinition definition = state.definition();
        if (player == null || isDead || item == null || definition == null || worldObj == null
                || worldObj.multiplayerWorld || player.worldObj != worldObj || player.isDead) {
            return false;
        }
        int previousCount = item.stackSize;
        collecting = true;
        try {
            super.onCollideWithPlayer(player);
        } finally {
            if (isDead && item.stackSize <= 0 && !pickupReactionDelivered) {
                pickupReactionDelivered = true;
                PickupEvent.collected(this, player, definition);
            }
            collecting = false;
            if (isDead) {
                EntityLifecycleEvents.removed(this, state);
            }
        }
        return item.stackSize < previousCount;
    }

    @Override
    public boolean interact(EntityPlayer player) {
        return EntityInteraction.evaluate(this, player) != InteractionOutcome.PASS;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        state.write(tag);
        tag.setInteger("BetaMoonPickupDelay", delayBeforeCanPickup);
        tag.setBoolean("BetaMoonPickupReactionDelivered", pickupReactionDelivered);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        state.read(tag);
        delayBeforeCanPickup = tag.getInteger("BetaMoonPickupDelay");
        pickupReactionDelivered = tag.getBoolean("BetaMoonPickupReactionDelivered");
        applyDefinition();
    }

    public void initializeItem(EntityTypeDefinition definition) {
        state.attach(definition);
        item = new ItemStack(definition.pickup.itemId, definition.pickup.count, definition.pickup.damage);
        delayBeforeCanPickup = definition.pickup.delayTicks;
        applyDefinition();
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
