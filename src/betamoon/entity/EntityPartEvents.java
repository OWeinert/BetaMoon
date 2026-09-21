package betamoon.entity;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Routes part-local interaction and damage before the parent fallback. */
final class EntityPartEvents {
    private static final Map<EntityPartDefinition, Set<String>> DISABLED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private EntityPartEvents() {
    }

    static boolean interact(LuaEntityPart target, EntityPlayer player) {
        EntityPartDefinition part = definition(target);
        if (part == null) {
            return false;
        }
        if (!target.acceptsPartInteraction(part)) {
            return !target.parent().isDead && target.parent().interact(player);
        }
        if (!part.onInteract.isnil() && !disabled(part, "onInteract")) {
            try (LuaCallbackScope scope = new LuaCallbackScope(!target.worldObj.multiplayerWorld)) {
                LuaTable context = context(scope, target);
                context.set("player", LuaEntityActionAccess.create(scope, player, player));
                InteractionOutcome result = InteractionOutcome.fromLua(part.onInteract.call(context),
                        "entity.parts." + part.name + ".onInteract");
                if (result != InteractionOutcome.PASS) {
                    return true;
                }
            } catch (RuntimeException error) {
                disable(target, part, "onInteract", error);
                return true;
            }
        }
        if (target.isDead || target.parent().isDead) {
            return true;
        }
        return target.parent().interact(player);
    }

    static boolean damage(LuaEntityPart target, Entity attacker, int amount) {
        EntityPartDefinition part = definition(target);
        if (part == null || !target.acceptsDamage()) {
            return false;
        }
        int applied = amount;
        if (!part.onDamage.isnil() && !disabled(part, "onDamage")) {
            try (LuaCallbackScope scope = new LuaCallbackScope(!target.worldObj.multiplayerWorld)) {
                LuaTable context = context(scope, target);
                context.set("amount", amount);
                if (attacker != null) {
                    context.set("attacker", LuaEntityActionAccess.create(scope, null, attacker));
                }
                LuaValue result = part.onDamage.call(context);
                if (!result.isnil()) {
                    double proposed = result.checkdouble();
                    if (!Double.isFinite(proposed) || proposed != Math.floor(proposed)
                            || proposed < 0 || proposed > 32767) {
                        throw new IllegalArgumentException("part damage must return an integer 0..32767 or nil");
                    }
                    applied = (int) proposed;
                }
            } catch (RuntimeException error) {
                disable(target, part, "onDamage", error);
                return false;
            }
        }
        if (target.isDead || target.parent().isDead) {
            return false;
        }
        return applied > 0 && target.parent().attackEntityFrom(attacker, applied);
    }

    private static EntityPartDefinition definition(LuaEntityPart target) {
        EntityTypeDefinition type = target.entityState().definition();
        return type == null ? null : type.parts.get(target.partName());
    }

    private static LuaTable context(LuaCallbackScope scope, LuaEntityPart target) {
        LuaTable context = new LuaTable();
        context.set("entity", LuaEntityActionAccess.create(scope, null, target.parent()));
        context.set("part", target.partName());
        context.set("hitbox", LuaEntityActionAccess.create(scope, null, target));
        context.set("world", LuaWorldActionAccess.create(scope, target.worldObj,
                (int) Math.floor(target.posX), (int) Math.floor(target.posY), (int) Math.floor(target.posZ)));
        return context;
    }

    private static void disable(LuaEntityPart target, EntityPartDefinition part, String callback,
            RuntimeException error) {
        if (DISABLED.computeIfAbsent(part, ignored -> new HashSet<>()).add(callback)) {
            EntityTypeDefinition type = target.entityState().definition();
            String message = type.key + ".parts." + part.name + "." + callback + " disabled after error: "
                    + error.getMessage();
            String owner = EntityTypeRegistry.owner(type.key);
            LuaScriptErrors.add(owner == null ? type.key.toString() : owner, message);
            BetaMoonCommon.LOGGER.warning(message);
        }
    }

    private static boolean disabled(EntityPartDefinition part, String callback) {
        Set<String> events = DISABLED.get(part);
        return events != null && events.contains(callback);
    }
}
