package betamoon.multiblock;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.block.BlockFace;
import betamoon.luaapi.multiblock.MultiblocksApi;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptExecution;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Owns callback scopes; no engine transition depends on post-commit callback
 * success.
 */
final class MultiblockLifecycle {
    private MultiblockLifecycle() {
    }

    static boolean invoke(MultiblockInstance instance, String event, String reason, EntityPlayer player,
            ItemStack stack, MultiblockPosition clicked, MultiblockPosition target, int face) {
        LuaValue callback = instance.definition.callbacks.get(event);
        if (callback == null || callback.isnil()) {
            return true;
        }
        boolean query = event.equals("canActivate");
        if (MultiblockRegistry.find(instance.definition.key) != instance.definition) {
            return !query;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(!query)) {
            LuaTable context = new LuaTable();
            context.set("owner", instance.definition.owner);
            context.set("key", instance.definition.key.toString());
            context.set("x", instance.position.x);
            context.set("y", instance.position.y);
            context.set("z", instance.position.z);
            context.set("reason", reason);
            context.set("facing", MultiblockOrientation.name(instance.facing));
            context.set("report", MultiblocksApi.report(instance.definition, instance.position, instance.facing,
                    instance.mirror, instance.report));
            context.set("multiblock", MultiblocksApi.instance(scope, instance, instance.expired));
            context.set("data", MultiblocksApi.data(scope, instance, instance.expired));
            context.set("world", LuaWorldActionAccess.create(scope, instance.world(), instance.position.x,
                    instance.position.y, instance.position.z));
            if (player != null) {
                context.set("player", LuaEntityActionAccess.create(scope, player, player));
            }
            if (stack != null) {
                LuaTable item = new LuaTable();
                item.set("id", stack.itemID);
                item.set("count", stack.stackSize);
                item.set("damage", stack.getItemDamage());
                context.set("item", item);
            }
            if (clicked != null) {
                context.set("clicked", position(clicked));
                context.set("target", position(target));
                if (face >= 0) {
                    context.set("face", BlockFace.fromNative(face).luaName);
                }
            }
            LuaValue result = ScriptExecution
                    .invoke(instance.definition.owner, "multiblocks." + event, callback, context).arg1();
            if (query && !result.isboolean()) {
                throw new IllegalArgumentException("canActivate must return a boolean");
            }
            return !query || result.toboolean();
        } catch (Throwable error) {
            instance.callbackError = event + ": " + error.getMessage();
            LuaScriptErrors.add(instance.definition.owner,
                    "Multiblock " + instance.definition.key + " " + instance.callbackError,
                    LuaScriptErrors.Outcome.UNKNOWN, error);
            return false;
        }
    }

    private static LuaTable position(MultiblockPosition point) {
        LuaTable result = new LuaTable();
        result.set("x", point.x);
        result.set("y", point.y);
        result.set("z", point.z);
        return result;
    }
}
