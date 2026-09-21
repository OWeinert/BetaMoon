package betamoon.entity;

import betamoon.assets.AssetKey;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/** Saved stable owner token and optional per-instance team override. */
public final class EntityRelationsState {
    private final StableEntityReference owner = new StableEntityReference();
    private String teamOverride;

    public boolean setOwner(Entity entity) {
        return owner.set(entity);
    }

    public Entity owner(World world) {
        return owner.resolve(world);
    }

    public String ownerIdentity() {
        return owner.token();
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
        owner.write(tag, "BetaMoonOwnerKind", "BetaMoonOwner");
        if (teamOverride != null) {
            tag.setBoolean("BetaMoonTeamOverride", true);
            tag.setString("BetaMoonTeam", teamOverride);
        }
    }

    public void read(NBTTagCompound tag) {
        owner.read(tag, "BetaMoonOwnerKind", "BetaMoonOwner");
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
