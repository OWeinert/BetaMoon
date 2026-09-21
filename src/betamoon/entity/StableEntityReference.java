package betamoon.entity;

import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/** Saved reference to a player or BetaMoon entity without forcing its chunk to load. */
final class StableEntityReference {
    private static final int MAX_IDENTITY_LENGTH = 128;

    private String kind = "";
    private String identity = "";

    boolean set(Entity entity) {
        if (entity == null) {
            clear();
            return true;
        }
        if (entity instanceof EntityPlayer) {
            return set("player", ((EntityPlayer) entity).username);
        }
        if (entity instanceof TypedEntity && !(entity instanceof LuaEntityPart)) {
            return set("entity", ((TypedEntity) entity).entityState().identity());
        }
        clear();
        return false;
    }

    boolean set(String kind, String identity) {
        if (!("player".equals(kind) || "entity".equals(kind))
                || identity == null || identity.isEmpty() || identity.length() > MAX_IDENTITY_LENGTH) {
            clear();
            return false;
        }
        this.kind = kind;
        this.identity = identity;
        return true;
    }

    void clear() {
        kind = "";
        identity = "";
    }

    Entity resolve(World world) {
        if (world == null || identity.isEmpty()) {
            return null;
        }
        if ("player".equals(kind)) {
            Entity player = world.getPlayerEntityByName(identity);
            return player == null || player.isDead ? null : player;
        }
        for (Object value : world.loadedEntityList) {
            if (value instanceof TypedEntity && !(value instanceof LuaEntityPart)) {
                Entity entity = (Entity) value;
                if (!entity.isDead && identity.equals(((TypedEntity) value).entityState().identity())) {
                    return entity;
                }
            }
        }
        return null;
    }

    String token() {
        return identity.isEmpty() ? null : kind + ":" + identity;
    }

    void write(NBTTagCompound tag, String kindName, String identityName) {
        if (!identity.isEmpty()) {
            tag.setString(kindName, kind);
            tag.setString(identityName, identity);
        }
    }

    void read(NBTTagCompound tag, String kindName, String identityName) {
        set(tag.getString(kindName), tag.getString(identityName));
    }
}
