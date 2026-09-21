package betamoon.entity;

import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/** Invisible native collision target for one optional multipart hitbox. */
public final class LuaEntityPart extends Entity implements TypedEntity {
    enum Role {
        HIT,
        INTERACTION
    }

    private final Entity parent;
    private final String partName;
    private final Role role;

    LuaEntityPart(World world, Entity parent, String partName, Role role) {
        super(world);
        this.parent = parent;
        this.partName = partName;
        this.role = role;
        preventEntitySpawning = false;
    }

    @Override
    protected void entityInit() {
        // Entity calls this before subclass fields are initialized.
    }

    public Entity parent() {
        return parent;
    }

    public String partName() {
        return partName;
    }

    public String roleName() {
        return role == Role.HIT ? "hit" : "interaction";
    }

    boolean acceptsDamage() {
        return role == Role.HIT;
    }

    boolean acceptsPartInteraction(EntityPartDefinition definition) {
        return role == Role.INTERACTION || definition.interactionBox == null;
    }

    @Override
    public EntityInstanceState entityState() {
        return ((TypedEntity) parent).entityState();
    }

    void position(EntityShapeDefinition shape, double offsetX, double offsetY, double offsetZ) {
        double radians = Math.toRadians(parent.rotationYaw);
        double x = Math.cos(radians) * offsetX - Math.sin(radians) * offsetZ;
        double z = Math.sin(radians) * offsetX + Math.cos(radians) * offsetZ;
        double centerX = parent.posX + x;
        double centerY = parent.posY + offsetY;
        double centerZ = parent.posZ + z;
        setPosition(centerX, centerY, centerZ);
        boundingBox.setBounds(centerX - shape.sizeX / 2,
                centerY - shape.sizeY / 2, centerZ - shape.sizeZ / 2,
                centerX + shape.sizeX / 2, centerY + shape.sizeY / 2,
                centerZ + shape.sizeZ / 2);
    }

    @Override
    public void onUpdate() {
        if (parent.isDead || parent.worldObj != worldObj || entityState().definition() == null
                || !worldObj.blockExists(parent.chunkCoordX << 4, 64, parent.chunkCoordZ << 4)) {
            setEntityDead();
            return;
        }
        EntityMotion.frozenTick(this);
        ticksExisted++;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead && !parent.isDead && entityState().definition() != null;
    }

    @Override
    public boolean interact(EntityPlayer player) {
        return EntityPartEvents.interact(this, player);
    }

    @Override
    public boolean attackEntityFrom(Entity attacker, int amount) {
        return EntityPartEvents.damage(this, attacker, amount);
    }

    @Override
    public boolean addEntityID(NBTTagCompound tag) {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        // Collision parts are rebuilt from their saved parent.
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        // Collision parts are rebuilt from their saved parent.
    }
}
