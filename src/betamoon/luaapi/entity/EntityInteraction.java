package betamoon.luaapi.entity;

import betamoon.BetaMoonCommon;

import betamoon.entity.EntityInstanceState;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.EntityTypeRegistry;
import betamoon.entity.TypedEntity;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Prop interaction decision with normal item fallback on pass. */
public final class EntityInteraction {
    private static final Set<EntityTypeDefinition> DISABLED = Collections.newSetFromMap(new WeakHashMap<>());

    private EntityInteraction() {
    }

    public static InteractionOutcome evaluate(Entity entity, EntityPlayer player) {
        EntityInstanceState state = ((TypedEntity) entity).entityState();
        EntityTypeDefinition definition = state.definition();
        if (definition == null) {
            return InteractionOutcome.DENY;
        }
        InteractionOutcome outcome = InteractionOutcome.PASS;
        if (!definition.onInteract.isnil() && !DISABLED.contains(definition)) {
            outcome = callback(entity, player, state, definition);
        }
        if (outcome == InteractionOutcome.PASS && definition.inventory != null
                && definition.inventory.openOnInteract && player != null) {
            player.displayGUIChest(state.inventory(entity, definition));
            return InteractionOutcome.HANDLED;
        }
        return outcome;
    }

    private static InteractionOutcome callback(Entity entity, EntityPlayer player, EntityInstanceState state,
            EntityTypeDefinition definition) {
        try (LuaCallbackScope scope = new LuaCallbackScope(entity.worldObj == null
                || !entity.worldObj.multiplayerWorld)) {
            LuaTable context = new LuaTable();
            context.set("entity", LuaEntityActionAccess.create(scope, player, entity));
            if (player != null) {
                context.set("player", LuaEntityActionAccess.create(scope, player, player));
            }
            if (entity.worldObj != null) {
                context.set("world", LuaWorldActionAccess.create(scope, entity.worldObj, (int) Math.floor(entity.posX),
                        (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ)));
            }
            LuaValue result = definition.onInteract.call(context);
            return InteractionOutcome.fromLua(result, "entity.onInteract");
        } catch (RuntimeException error) {
            if (DISABLED.add(definition)) {
                String message = definition.key + ".onInteract disabled after error: " + error.getMessage();
                String owner = EntityTypeRegistry.owner(definition.key);
                LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message);
                BetaMoonCommon.LOGGER.warning(message);
            }
            return InteractionOutcome.DENY;
        }
    }
}
