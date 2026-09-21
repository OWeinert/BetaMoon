package betamoon.entity;

import betamoon.assets.AssetKey;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/** Saved stable owner token and optional per-instance team override. */
public final class EntityRelationsState {
    private String ownerKind = "";
    private String ownerIdentity = "";
    private String teamOverride;

    public boolean setOwner(Entity owner) {
        if (owner == null) {
            ownerKind = "";
            ownerIdentity = "";
            return true;
        }
        if (owner instanceof EntityPlayer) {
            String username = ((EntityPlayer) owner).username;
            if (username == null || username.isEmpty()) {
                return false;
            }
            ownerKind = "player";
            ownerIdentity = username;
            return true;
        }
        if (owner instanceof TypedEntity && !(owner instanceof LuaEntityPart)) {
            ownerKind = "entity";
            ownerIdentity = ((TypedEntity) owner).entityState().identity();
            return true;
        }
        return false;
    }

    public Entity owner(World world) {
        if (world == null || ownerIdentity.isEmpty()) {
            return null;
        }
        if ("player".equals(ownerKind)) {
            return world.getPlayerEntityByName(ownerIdentity);
        }
        if ("entity".equals(ownerKind)) {
            for (Object value : world.loadedEntityList) {
                if (value instanceof TypedEntity && !(value instanceof LuaEntityPart)
                        && ownerIdentity.equals(((TypedEntity) value).entityState().identity())) {
                    return (Entity) value;
                }
            }
        }
        return null;
    }

    public String ownerIdentity() {
        return ownerIdentity.isEmpty() ? null : ownerKind + ":" + ownerIdentity;
    }

    public String team(EntityRelationsDefinition definition) {
        return teamOverride == null ? definition.team : teamOverride;
    }

    public void team(String value) {
        teamOverride = value;
    }

    public void resetTeam() {
        teamOverride = null;
    }

    public boolean allied(EntityRelationsDefinition definition, Entity other) {
        String team = team(definition);
        if (!team.isEmpty() && other instanceof TypedEntity) {
            EntityInstanceState state = ((TypedEntity) other).entityState();
            EntityTypeDefinition otherDefinition = state.definition();
            if (otherDefinition != null && otherDefinition.relations != null
                    && team.equals(state.relations().team(otherDefinition.relations))) {
                return true;
            }
        }
        String own = ownerIdentity();
        if (other instanceof EntityPlayer && own != null
                && own.equals("player:" + ((EntityPlayer) other).username)) {
            return true;
        }
        if (other instanceof TypedEntity && !(other instanceof LuaEntityPart)) {
            EntityInstanceState state = ((TypedEntity) other).entityState();
            String identity = "entity:" + state.identity();
            if (identity.equals(own)) {
                return true;
            }
            EntityTypeDefinition otherDefinition = state.definition();
            String otherOwner = otherDefinition != null && otherDefinition.relations != null
                    && otherDefinition.relations.ownership ? state.relations().ownerIdentity() : null;
            return own != null && own.equals(otherOwner);
        }
        return false;
    }

    public void write(NBTTagCompound tag) {
        if (!ownerIdentity.isEmpty()) {
            tag.setString("BetaMoonOwnerKind", ownerKind);
            tag.setString("BetaMoonOwner", ownerIdentity);
        }
        if (teamOverride != null) {
            tag.setBoolean("BetaMoonTeamOverride", true);
            tag.setString("BetaMoonTeam", teamOverride);
        }
    }

    public void read(NBTTagCompound tag) {
        ownerKind = tag.getString("BetaMoonOwnerKind");
        ownerIdentity = tag.getString("BetaMoonOwner");
        if (!("player".equals(ownerKind) || "entity".equals(ownerKind))
                || ownerIdentity.isEmpty() || ownerIdentity.length() > 128) {
            ownerKind = "";
            ownerIdentity = "";
        }
        teamOverride = null;
        if (tag.getBoolean("BetaMoonTeamOverride")) {
            try {
                teamOverride = AssetKey.parse(tag.getString("BetaMoonTeam")).toString();
            } catch (IllegalArgumentException ignored) {
                // Ignore a corrupt override and fall back to the declaration team.
            }
        }
    }
}
