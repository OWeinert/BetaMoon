package betamoon.client.particles;

import betamoon.luaapi.particle.LuaParticleAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.particles.ParticleDefinition;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.src.EntityFX;
import net.minecraft.src.Entity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;

/**
 * One transient sprite with exact authored motion and independent
 * animation/callback state.
 */
public final class ParticleSprite extends EntityFX {
    public final ParticleDefinition definition;
    public final String owner;
    public final int frameSeed;
    public final double spawnScale;
    private final double[] tint;
    public double size;
    public double previousSize;
    public double rotation;
    public double previousRotation;
    public final double[] color = new double[4];
    public final double[] previousColor = new double[4];
    public double gravity;
    public double drag;
    private boolean stopped;
    private boolean callbackFailed;
    private LuaTable memory;
    private final Consumer<RuntimeException> callbackError;

    public ParticleSprite(World world, ParticleDefinition definition, String owner, double x, double y, double z,
            double vx, double vy, double vz, double scale, double[] tint, long seed,
            Consumer<RuntimeException> callbackError) {
        super(world, x, y, z, 0, 0, 0);
        this.definition = definition;
        this.owner = owner;
        this.spawnScale = scale;
        this.tint = tint.clone();
        this.callbackError = callbackError;
        Random random = new Random(seed);
        particleMaxAge = definition.lifetimeMin + random.nextInt(definition.lifetimeMax - definition.lifetimeMin + 1);
        frameSeed = definition.randomFrame ? random.nextInt(Integer.MAX_VALUE) : 0;
        motionX = vx;
        motionY = vy;
        motionZ = vz;
        gravity = definition.gravity;
        drag = definition.drag;
        yOffset = 0;
        setPosition(x, y, z);
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        updateAppearance();
        snapshot();
    }

    public int age() {
        return particleAge;
    }

    public int lifetime() {
        return particleMaxAge;
    }

    @Override
    public void onUpdate() {
        if (isDead) {
            return;
        }
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        snapshot();
        particleAge++;
        updateAppearance();
        updateScript();
        if (!isDead && !stopped) {
            move();
        }
        if (particleAge >= particleMaxAge) {
            setEntityDead();
        }
        if (!isDead && !worldObj.blockExists((int) Math.floor(posX), 64, (int) Math.floor(posZ))) {
            setEntityDead();
        }
    }

    private void snapshot() {
        previousSize = size;
        previousRotation = rotation;
        System.arraycopy(color, 0, previousColor, 0, 4);
    }

    private void updateAppearance() {
        double progress = Math.min(1, (double) particleAge / particleMaxAge);
        size = (definition.sizeStart + (definition.sizeFinish - definition.sizeStart) * progress) * spawnScale;
        rotation = definition.rotationStart + definition.rotationSpeed * particleAge;
        for (int component = 0; component < 4; component++) {
            color[component] = definition.color(component, progress) * tint[component];
        }
    }

    private void updateScript() {
        if (callbackFailed || definition.onTick.isnil()) {
            return;
        }
        if (memory == null) {
            memory = new LuaTable();
        }
        double vx = motionX;
        double vy = motionY;
        double vz = motionZ;
        double oldGravity = gravity;
        double oldDrag = drag;
        boolean oldStopped = stopped;
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = new LuaTable();
            context.set("particle", LuaParticleAccess.create(scope, this));
            context.set("memory", memory);
            context.set("age", particleAge);
            context.set("lifetime", particleMaxAge);
            context.set("progress", Math.min(1, (double) particleAge / particleMaxAge));
            ScriptExecution.invoke(definition.owner, definition.onTick, context);
        } catch (RuntimeException error) {
            callbackFailed = true;
            memory = null;
            motionX = vx;
            motionY = vy;
            motionZ = vz;
            gravity = oldGravity;
            drag = oldDrag;
            stopped = oldStopped;
            updateAppearance();
            callbackError.accept(error);
        }
    }

    private void move() {
        motionY -= gravity;
        motionX = Math.max(-16, Math.min(16, motionX));
        motionY = Math.max(-16, Math.min(16, motionY));
        motionZ = Math.max(-16, Math.min(16, motionZ));
        double vx = motionX;
        double vy = motionY;
        double vz = motionZ;
        if ("none".equals(definition.collision)) {
            setPosition(posX + vx, posY + vy, posZ + vz);
        } else {
            int minX = (int) Math.floor(Math.min(posX, posX + vx) - width);
            int minZ = (int) Math.floor(Math.min(posZ, posZ + vz) - width);
            int maxX = (int) Math.floor(Math.max(posX, posX + vx) + width + 1);
            int maxZ = (int) Math.floor(Math.max(posZ, posZ + vz) + width + 1);
            if (!worldObj.checkChunksExist(minX, 0, minZ, maxX, 127, maxZ)) {
                setEntityDead();
                return;
            }
            double x = posX;
            double y = posY;
            double z = posZ;
            ParticleCollision.move(this, vx, vy, vz);
            boolean hitX = Math.abs(posX - x - vx) > 0.000001;
            boolean hitY = Math.abs(posY - y - vy) > 0.000001;
            boolean hitZ = Math.abs(posZ - z - vz) > 0.000001;
            if (hitX || hitY || hitZ) {
                if ("die".equals(definition.collision)) {
                    setEntityDead();
                } else if ("stop".equals(definition.collision)) {
                    stopped = true;
                    motionX = 0;
                    motionY = 0;
                    motionZ = 0;
                } else {
                    motionX = hitX ? -vx * definition.bounce : vx;
                    motionY = hitY ? -vy * definition.bounce : vy;
                    motionZ = hitZ ? -vz * definition.bounce : vz;
                }
            }
        }
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        if (!Double.isFinite(posX) || !Double.isFinite(posY) || !Double.isFinite(posZ) || Math.abs(posX) > 30000000
                || Math.abs(posZ) > 30000000 || Math.abs(posY) > 30000000) {
            setEntityDead();
        }
    }

    public void velocity(double x, double y, double z) {
        motionX = x;
        motionY = y;
        motionZ = z;
        stopped = false;
    }

    @Override
    public boolean attackEntityFrom(Entity source, int amount) {
        return false;
    }

    @Override
    public void setEntityDead() {
        memory = null;
        super.setEntityDead();
    }
}
