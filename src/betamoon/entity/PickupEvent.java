package betamoon.entity;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;

/** One post-collection reaction after an entire pickup stack enters inventory. */
final class PickupEvent {
    private static final Set<EntityTypeDefinition> DISABLED = Collections.newSetFromMap(new WeakHashMap<>());

    private PickupEvent() {
    }

    static void collected(LuaPickupEntity pickup, EntityPlayer player, EntityTypeDefinition definition) {
        EntityPresentationEvents.sound(pickup,
                definition.sounds.get(EntitySoundsDefinition.Event.PICKUP));
        if (definition.onPickup.isnil() || DISABLED.contains(definition)) {
            return;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = new LuaTable();
            context.set("entity", LuaEntityActionAccess.create(scope, player, pickup));
            context.set("player", LuaEntityActionAccess.create(scope, player, player));
            context.set("world", LuaWorldActionAccess.create(scope, pickup.worldObj, (int) Math.floor(pickup.posX),
                    (int) Math.floor(pickup.posY), (int) Math.floor(pickup.posZ), pickup));
            definition.onPickup.call(context);
        } catch (RuntimeException error) {
            if (DISABLED.add(definition)) {
                String message = definition.key + ".onPickup disabled after error: " + error.getMessage();
                String owner = EntityTypeRegistry.owner(definition.key);
                LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message);
                BetaMoonCommon.LOGGER.warning(message);
            }
        }
    }
}
