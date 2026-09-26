package betamoon.entity;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Entity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Scoped impact callback. A failed callback stops the projectile safely. */
public final class ProjectileImpact {
    public enum Action {
        DEFAULT,
        REMOVE,
        BOUNCE,
        STICK,
        CONTINUE
    }

    private static final Set<EntityTypeDefinition> DISABLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};

    private ProjectileImpact() {
    }

    static Action evaluate(LuaProjectileEntity projectile, ProjectileCollision.Hit hit,
            EntityTypeDefinition definition) {
        EntityPresentationEvents.sound(projectile,
                definition.sounds.get(EntitySoundsDefinition.Event.IMPACT),
                hit.position.xCoord, hit.position.yCoord, hit.position.zCoord);
        if (definition.onImpact.isnil() || DISABLED.contains(definition)) {
            return Action.DEFAULT;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = new LuaTable();
            context.set("entity", LuaEntityActionAccess.create(scope, null, projectile));
            Entity owner = projectile.getOwner();
            if (owner != null) {
                context.set("owner", LuaEntityActionAccess.create(scope, null, owner));
            }
            if (hit.entity != null) {
                context.set("target", LuaEntityActionAccess.create(scope, null, hit.entity));
            } else {
                LuaTable block = new LuaTable();
                block.set("x", hit.nativeHit.blockX);
                block.set("y", hit.nativeHit.blockY);
                block.set("z", hit.nativeHit.blockZ);
                int side = hit.nativeHit.sideHit;
                if (side >= 0 && side < FACES.length) {
                    block.set("face", FACES[side]);
                }
                context.set("block", block);
            }
            LuaTable position = new LuaTable();
            position.set("x", hit.position.xCoord);
            position.set("y", hit.position.yCoord);
            position.set("z", hit.position.zCoord);
            context.set("position", position);
            context.set("world", LuaWorldActionAccess.create(scope, projectile.worldObj,
                    (int) Math.floor(hit.position.xCoord), (int) Math.floor(hit.position.yCoord),
                    (int) Math.floor(hit.position.zCoord), projectile));
            LuaValue result = definition.onImpact.call(context);
            if (result.isnil() || result.eq_b(LuaValue.valueOf("default"))) {
                return Action.DEFAULT;
            }
            if (result.eq_b(LuaValue.valueOf("remove"))) {
                return Action.REMOVE;
            }
            if (result.eq_b(LuaValue.valueOf("bounce"))) {
                return Action.BOUNCE;
            }
            if (result.eq_b(LuaValue.valueOf("stick"))) {
                return Action.STICK;
            }
            if (result.eq_b(LuaValue.valueOf("continue"))) {
                return Action.CONTINUE;
            }
            throw new IllegalArgumentException("onImpact must return default, remove, bounce, stick, continue, or nil");
        } catch (RuntimeException error) {
            if (DISABLED.add(definition)) {
                String message = definition.key + ".onImpact disabled after error: " + error.getMessage();
                String owner = EntityTypeRegistry.owner(definition.key);
                LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message);
                BetaMoonCommon.LOGGER.warning(message);
            }
            return Action.REMOVE;
        }
    }
}
