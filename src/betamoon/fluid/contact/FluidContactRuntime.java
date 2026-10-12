package betamoon.fluid.contact;

import betamoon.fluid.FluidEntityPolicy;
import betamoon.fluid.FluidRegistration;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.Material;
import net.minecraft.src.Vec3D;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.IdentityHashMap;

/** Applies resolved fluid policy without dispatching Lua from native entity code. */
public final class FluidContactRuntime {
    private static final Map<Entity, CachedSnapshot> SNAPSHOTS = new WeakHashMap<Entity, CachedSnapshot>();
    private static final Map<Entity, ContactTimers> DAMAGE_TIMERS = new WeakHashMap<Entity, ContactTimers>();
    private static final Map<Entity, Integer> CURRENT_TICKS = new WeakHashMap<Entity, Integer>();

    private FluidContactRuntime() {
    }

    public static FluidContactSnapshot snapshot(Entity entity) {
        synchronized (SNAPSHOTS) {
            CachedSnapshot cached = SNAPSHOTS.get(entity);
            if (cached != null && cached.matches(entity)) {
                return cached.snapshot;
            }
            FluidContactSnapshot resolved = FluidContactResolver.resolve(entity.worldObj, entity.boundingBox,
                    entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
            SNAPSHOTS.put(entity, new CachedSnapshot(entity, resolved));
            return resolved;
        }
    }

    public static boolean hasCustomBody(Entity entity) {
        for (FluidBodyContact contact : snapshot(entity).bodyContacts()) {
            if (!contact.fluid().definition().isVanilla()) {
                return true;
            }
        }
        return false;
    }

    public static boolean movement(Entity entity, FluidEntityPolicy.Movement movement) {
        FluidBodyContact dominant = snapshot(entity).dominantBody();
        if (dominant == null || dominant.fluid().definition().entity().movement() != movement) {
            return false;
        }
        addCurrent(entity, dominant);
        return true;
    }

    private static void addCurrent(Entity entity, FluidBodyContact contact) {
        float scale = contact.fluid().definition().entity().currentScale();
        if (scale == 0.0F) {
            return;
        }
        synchronized (CURRENT_TICKS) {
            Integer applied = CURRENT_TICKS.get(entity);
            if (applied != null && applied.intValue() == entity.ticksExisted) {
                return;
            }
            CURRENT_TICKS.put(entity, Integer.valueOf(entity.ticksExisted));
        }
        int x = contact.firstX();
        int y = contact.firstY();
        int z = contact.firstZ();
        Block block = Block.blocksList[entity.worldObj.getBlockId(x, y, z)];
        if (block == null) {
            return;
        }
        Vec3D current = Vec3D.createVector(0.0D, 0.0D, 0.0D);
        block.velocityToAddToEntity(entity.worldObj, x, y, z, entity, current);
        if (current.lengthVector() == 0.0D) {
            return;
        }
        current = current.normalize();
        entity.motionX += current.xCoord * 0.014D * scale;
        entity.motionY += current.yCoord * 0.014D * scale;
        entity.motionZ += current.zCoord * 0.014D * scale;
    }

    public static boolean hasCustomEye(Entity entity) {
        FluidRegistration eye = snapshot(entity).eyeFluid();
        return eye != null && !eye.definition().isVanilla();
    }

    public static boolean eyeMatchesMaterial(Entity entity, Material material) {
        FluidRegistration eye = snapshot(entity).eyeFluid();
        if (eye == null) {
            return false;
        }
        FluidEntityPolicy.Movement movement = eye.definition().entity().movement();
        return material == Material.water && movement == FluidEntityPolicy.Movement.WATER
                || material == Material.lava && movement == FluidEntityPolicy.Movement.LAVA;
    }

    public static boolean shouldDrown(Entity entity) {
        FluidRegistration eye = snapshot(entity).eyeFluid();
        return eye != null && eye.definition().entity().drowning();
    }

    public static boolean isWet(Entity entity, boolean nativeResult) {
        FluidContactSnapshot contact = snapshot(entity);
        if (!hasCustom(contact)) {
            return nativeResult;
        }
        for (FluidBodyContact body : contact.bodyContacts()) {
            if (body.fluid().definition().entity().countsAsWet()) {
                return true;
            }
        }
        return entity.worldObj.canBlockBeRainedOn((int) Math.floor(entity.posX),
                (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ));
    }

    public static boolean shouldSkipNativeLavaDamage(Entity entity) {
        return hasCustomBody(entity);
    }

    public static void finishEntityTick(Entity entity, int previousFire, boolean immuneToFire) {
        FluidContactSnapshot contact = snapshot(entity);
        if (!hasCustom(contact)) {
            synchronized (DAMAGE_TIMERS) {
                DAMAGE_TIMERS.remove(entity);
            }
            return;
        }
        boolean extinguish = false;
        boolean ignite = false;
        ContactTimers timers = timers(entity);
        Map<FluidRegistration, Integer> currentContacts = new IdentityHashMap<FluidRegistration, Integer>();
        for (FluidBodyContact body : contact.bodyContacts()) {
            FluidEntityPolicy policy = body.fluid().definition().entity();
            extinguish |= policy.extinguishesFire();
            ignite |= policy.ignitesEntities();
            Integer last = timers.lastDamage.get(body.fluid());
            if (last == null) {
                last = Integer.valueOf(entity.ticksExisted);
            }
            if (!entity.isDead && !entity.worldObj.multiplayerWorld && policy.contactDamage() > 0
                    && entity.ticksExisted - last.intValue() >= policy.contactIntervalTicks()) {
                FluidEnvironmentalDamage.apply(entity, body.fluid(), policy.contactDamage());
                last = Integer.valueOf(entity.ticksExisted);
            }
            currentContacts.put(body.fluid(), last);
        }
        timers.lastDamage.clear();
        timers.lastDamage.putAll(currentContacts);
        if (extinguish) {
            entity.fire = 0;
        } else if (entity.isInWater() && previousFire > 0 && entity.fire == 0 && !entity.worldObj.multiplayerWorld) {
            entity.fire = Math.max(0, previousFire - 1);
            if (previousFire % 20 == 0 && !immuneToFire) {
                entity.attackEntityFrom(null, 1);
            }
        }
        if (ignite && !entity.worldObj.multiplayerWorld && !immuneToFire) {
            entity.fire = 600;
        }
    }

    public static void finishLivingTick(EntityLiving entity, int previousFire, boolean immuneToFire) {
        FluidContactSnapshot contact = snapshot(entity);
        if (!hasCustom(contact)) {
            return;
        }
        boolean extinguish = false;
        boolean ignite = false;
        for (FluidBodyContact body : contact.bodyContacts()) {
            extinguish |= body.fluid().definition().entity().extinguishesFire();
            ignite |= body.fluid().definition().entity().ignitesEntities();
        }
        if (extinguish) {
            entity.fire = 0;
        } else if (!immuneToFire && !entity.worldObj.multiplayerWorld && previousFire > 0 && entity.fire == 0) {
            entity.fire = Math.max(0, previousFire - 1);
        }
        if (ignite && !immuneToFire && !entity.worldObj.multiplayerWorld) {
            entity.fire = 600;
        }
    }

    /** Contact path for BetaMoon actors whose manual/static lifecycle bypasses Entity.onEntityUpdate. */
    public static void tickWithoutNativeUpdate(Entity entity) {
        if (entity.worldObj == null || entity.isDead) {
            return;
        }
        int previousFire = entity.fire;
        if (!entity.worldObj.multiplayerWorld && entity.fire > 0) {
            if (entity.fire % 20 == 0) {
                entity.attackEntityFrom(null, 1);
            }
            entity.fire--;
        }
        if (entity instanceof EntityLiving) {
            EntityLiving living = (EntityLiving) entity;
            if (living.isEntityAlive() && shouldDrown(living) && !living.canBreatheUnderwater()) {
                living.air--;
                if (living.air <= -20) {
                    living.air = 0;
                    living.attackEntityFrom(null, 2);
                }
            } else {
                living.air = 300;
            }
        }
        finishEntityTick(entity, previousFire, false);
    }

    private static boolean hasCustom(FluidContactSnapshot contact) {
        for (FluidBodyContact body : contact.bodyContacts()) {
            if (!body.fluid().definition().isVanilla()) {
                return true;
            }
        }
        return contact.eyeFluid() != null && !contact.eyeFluid().definition().isVanilla();
    }

    private static ContactTimers timers(Entity entity) {
        synchronized (DAMAGE_TIMERS) {
            ContactTimers current = DAMAGE_TIMERS.get(entity);
            if (current == null || current.world != entity.worldObj || entity.ticksExisted < current.lastTick) {
                current = new ContactTimers(entity.worldObj);
                DAMAGE_TIMERS.put(entity, current);
            }
            current.lastTick = entity.ticksExisted;
            return current;
        }
    }

    private static final class ContactTimers {
        private final Object world;
        private final Map<FluidRegistration, Integer> lastDamage = new IdentityHashMap<FluidRegistration, Integer>();
        private int lastTick;

        private ContactTimers(Object world) {
            this.world = world;
        }
    }

    private static final class CachedSnapshot {
        private final FluidContactSnapshot snapshot;
        private final int tick;
        private final Object world;

        private CachedSnapshot(Entity entity, FluidContactSnapshot snapshot) {
            this.snapshot = snapshot;
            tick = entity.ticksExisted;
            world = entity.worldObj;
        }

        private boolean matches(Entity entity) {
            return tick == entity.ticksExisted && world == entity.worldObj;
        }
    }
}
