package betamoon.entity;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Server-side overlap transitions for named, non-colliding entity sensors. */
final class EntitySensorManager {
    private static final int MAX_TARGETS = 64;
    private static final Map<EntitySensorDefinition, Set<String>> DISABLED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Entity parent;
    private final Map<String, Set<Entity>> occupants = new LinkedHashMap<>();
    private final Map<String, double[]> offsets = new LinkedHashMap<>();
    private EntityTypeDefinition activeDefinition;
    private boolean flushing;

    EntitySensorManager(Entity parent) {
        this.parent = parent;
    }

    void tick(EntityTypeDefinition definition) {
        if (parent.isDead || parent.worldObj == null || parent.worldObj.multiplayerWorld || definition == null) {
            return;
        }
        if (activeDefinition != definition) {
            leaveAll("definition_changed");
            if (parent.isDead) {
                return;
            }
            offsets.keySet().retainAll(definition.sensors.keySet());
            activeDefinition = definition;
        }
        for (EntitySensorDefinition sensor : definition.sensors.values()) {
            if (parent.isDead) {
                return;
            }
            if (parent.ticksExisted % sensor.intervalTicks == 0) {
                scan(sensor);
            }
        }
    }

    boolean setOffset(String name, double x, double y, double z) {
        EntityTypeDefinition definition = ((TypedEntity) parent).entityState().definition();
        if (definition == null || !definition.sensors.containsKey(name)) {
            return false;
        }
        offsets.put(name, new double[]{x, y, z});
        return true;
    }

    void deactivate(String reason) {
        if (flushing) {
            return;
        }
        leaveAll(reason);
        activeDefinition = null;
    }

    private void scan(EntitySensorDefinition sensor) {
        AxisAlignedBB bounds = bounds(sensor);
        Object[] candidates = parent.worldObj.getEntitiesWithinAABBExcludingEntity(parent, bounds).toArray();
        Set<Entity> current = new LinkedHashSet<>();
        for (Object value : candidates) {
            if (!(value instanceof Entity)) {
                continue;
            }
            Entity found = (Entity) value;
            Entity other = found instanceof LuaEntityPart ? ((LuaEntityPart) found).parent() : found;
            if (other == parent || !other.isEntityAlive() || !matches(sensor.filter, other)
                    || !other.boundingBox.intersectsWith(bounds)) {
                continue;
            }
            current.add(other);
            if (current.size() >= MAX_TARGETS) {
                break;
            }
        }

        Set<Entity> previous = occupants.computeIfAbsent(sensor.name, ignored -> new LinkedHashSet<>());
        for (Entity other : new ArrayList<>(previous)) {
            if (!current.contains(other)) {
                previous.remove(other);
                String reason = !other.isEntityAlive() ? "removed"
                        : parent.worldObj.loadedEntityList.contains(other) ? "left" : "unloaded";
                dispatch(sensor, "onLeave", other, reason);
                if (parent.isDead) {
                    return;
                }
            }
        }
        for (Entity other : current) {
            if (parent.isDead) {
                return;
            }
            if (previous.add(other)) {
                dispatch(sensor, "onEnter", other, null);
            } else {
                dispatch(sensor, "onStay", other, null);
            }
        }
    }

    private AxisAlignedBB bounds(EntitySensorDefinition sensor) {
        EntityShapeDefinition shape = sensor.shape;
        double[] override = offsets.get(sensor.name);
        double offsetX = override == null ? shape.offsetX : override[0];
        double offsetY = override == null ? shape.offsetY : override[1];
        double offsetZ = override == null ? shape.offsetZ : override[2];
        double radians = Math.toRadians(parent.rotationYaw);
        double centerX = parent.posX + Math.cos(radians) * offsetX - Math.sin(radians) * offsetZ;
        double centerY = parent.posY + offsetY;
        double centerZ = parent.posZ + Math.sin(radians) * offsetX + Math.cos(radians) * offsetZ;
        return AxisAlignedBB.getBoundingBox(centerX - shape.sizeX / 2, centerY - shape.sizeY / 2,
                centerZ - shape.sizeZ / 2, centerX + shape.sizeX / 2,
                centerY + shape.sizeY / 2, centerZ + shape.sizeZ / 2);
    }

    private static boolean matches(EntitySensorDefinition.Filter filter, Entity other) {
        if (filter == EntitySensorDefinition.Filter.PLAYERS) {
            return other instanceof EntityPlayer;
        }
        return filter != EntitySensorDefinition.Filter.LIVING || other instanceof EntityLiving;
    }

    private void leaveAll(String reason) {
        if (flushing) {
            return;
        }
        EntityTypeDefinition definition = activeDefinition;
        if (definition == null) {
            occupants.clear();
            return;
        }
        Map<String, Set<Entity>> snapshot = new LinkedHashMap<>(occupants);
        occupants.clear();
        flushing = true;
        try {
            for (Map.Entry<String, Set<Entity>> entry : snapshot.entrySet()) {
                EntitySensorDefinition sensor = definition.sensors.get(entry.getKey());
                if (sensor == null) {
                    continue;
                }
                for (Entity other : entry.getValue()) {
                    dispatch(sensor, "onLeave", other, reason);
                }
            }
        } finally {
            flushing = false;
        }
    }

    private void dispatch(EntitySensorDefinition sensor, String event, Entity other, String reason) {
        EntityTypeDefinition definition = activeDefinition;
        if (definition == null) {
            return;
        }
        LuaValue callback = "onEnter".equals(event) ? sensor.onEnter
                : "onStay".equals(event) ? sensor.onStay : sensor.onLeave;
        Set<String> disabled = DISABLED.get(sensor);
        if (callback.isnil() || disabled != null && disabled.contains(event)) {
            return;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = new LuaTable();
            context.set("entity", LuaEntityActionAccess.create(scope, null, parent));
            context.set("other", LuaEntityActionAccess.create(scope, null, other));
            context.set("sensor", sensor.name);
            context.set("world", LuaWorldActionAccess.create(scope, parent.worldObj,
                    (int) Math.floor(parent.posX), (int) Math.floor(parent.posY), (int) Math.floor(parent.posZ)));
            if (reason != null) {
                context.set("reason", reason);
            }
            callback.call(context);
        } catch (RuntimeException error) {
            if (DISABLED.computeIfAbsent(sensor, ignored -> new HashSet<>()).add(event)) {
                String message = definition.key + ".sensors." + sensor.name + "." + event
                        + " disabled after error: " + error.getMessage();
                String owner = EntityTypeRegistry.owner(definition.key);
                LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message);
                BetaMoonCommon.LOGGER.warning(message);
            }
        }
    }
}
