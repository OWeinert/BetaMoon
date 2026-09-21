package betamoon.entity;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.Entity;

/** Rebuilds non-persistent damage and interaction proxies from the parent's current type. */
final class EntityPartManager {
    private final Entity parent;
    private final Map<String, LuaEntityPart> active = new LinkedHashMap<>();
    private final Map<String, double[]> offsets = new LinkedHashMap<>();

    EntityPartManager(Entity parent) {
        this.parent = parent;
    }

    void sync(EntityTypeDefinition definition) {
        if (parent.worldObj == null || parent.worldObj.multiplayerWorld || parent.isDead || definition == null) {
            clear();
            return;
        }
        Iterator<Map.Entry<String, LuaEntityPart>> entries = active.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, LuaEntityPart> entry = entries.next();
            LuaEntityPart proxy = entry.getValue();
            EntityPartDefinition part = definition.parts.get(proxy.partName());
            if (part == null || shape(part, proxy.roleName()) == null || proxy.isDead) {
                entry.getValue().setEntityDead();
                entries.remove();
                offsets.remove(entry.getKey());
            }
        }
        for (EntityPartDefinition part : definition.parts.values()) {
            syncPart(part, "hit", LuaEntityPart.Role.HIT);
            syncPart(part, "interaction", LuaEntityPart.Role.INTERACTION);
        }
    }

    boolean setOffset(String name, double x, double y, double z) {
        return setOffset(name, "hit", x, y, z);
    }

    boolean setInteractionOffset(String name, double x, double y, double z) {
        return setOffset(name, "interaction", x, y, z);
    }

    private boolean setOffset(String name, String role, double x, double y, double z) {
        EntityTypeDefinition definition = ((TypedEntity) parent).entityState().definition();
        EntityPartDefinition part = definition == null ? null : definition.parts.get(name);
        EntityShapeDefinition shape = part == null ? null : shape(part, role);
        if (shape == null) {
            return false;
        }
        String key = key(name, role);
        offsets.put(key, new double[]{x, y, z});
        LuaEntityPart entity = active.get(key);
        if (entity != null) {
            entity.position(shape, x, y, z);
        }
        return true;
    }

    private void syncPart(EntityPartDefinition part, String role, LuaEntityPart.Role nativeRole) {
        EntityShapeDefinition shape = shape(part, role);
        if (shape == null) {
            return;
        }
        String key = key(part.name, role);
        LuaEntityPart entity = active.get(key);
        double[] offset = offsets.get(key);
        double x = offset == null ? shape.offsetX : offset[0];
        double y = offset == null ? shape.offsetY : offset[1];
        double z = offset == null ? shape.offsetZ : offset[2];
        if (entity == null) {
            entity = new LuaEntityPart(parent.worldObj, parent, part.name, nativeRole);
            entity.position(shape, x, y, z);
            if (parent.worldObj.entityJoinedWorld(entity)) {
                active.put(key, entity);
            }
        } else {
            entity.position(shape, x, y, z);
        }
    }

    private static EntityShapeDefinition shape(EntityPartDefinition part, String role) {
        return "hit".equals(role) ? part.hitbox : part.interactionBox;
    }

    private static String key(String name, String role) {
        return name + ":" + role;
    }

    void clear() {
        for (LuaEntityPart part : active.values()) {
            part.setEntityDead();
        }
        active.clear();
        offsets.clear();
    }
}
