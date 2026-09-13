package betamoon.luaapi.item;

import betamoon.luaapi.block.LuaBlockActionContext;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.world.LuaWorldActionAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MovingObjectPosition;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/**
 * Context for a held or inventory item; block access exists only for a block
 * target.
 */
public final class LuaItemActionContext extends LuaTable implements AutoCloseable {
    private final LuaCallbackScope scope;
    private final EntityPlayer player;
    private final LuaBlockActionContext blockTarget;
    private final List<LuaBlockActionContext> tracedTargets = new ArrayList<LuaBlockActionContext>();

    public LuaItemActionContext(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, int face,
            boolean mutable) {
        this.player = player;
        scope = new LuaCallbackScope(mutable && !world.multiplayerWorld);
        set("world", LuaWorldActionAccess.create(scope, world, x, y, z));
        set("stack", LuaItemStackAccess.create(scope, player, stack));
        if (player != null) {
            set("player", entityAccess(player));
        }

        LuaTable target = new LuaTable();
        if (face >= 0) {
            blockTarget = new LuaBlockActionContext(world, x, y, z, player, stack, face, mutable);
            target.set("kind", "block");
            target.set("block", blockTarget.blockAccess());
            set("face", blockTarget.get("face"));
            set("position", blockTarget.get("position"));
        } else {
            blockTarget = null;
            target.set("kind", "miss");
        }
        set("target", target);
        set("rayTrace", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                if (player == null) {
                    throw LuaDeclarationValues.error("rayTrace", "requires a player");
                }
                LuaValue options = arguments.arg(arguments.arg1() == LuaItemActionContext.this ? 2 : 1);
                if (options.isnil()) {
                    options = new LuaTable();
                }
                fields(options, "rayTrace", "distance", "liquids");
                double distance = options.get("distance").isnil()
                        ? 5
                        : number(options.get("distance"), "rayTrace.distance");
                if (distance <= 0 || distance > 64) {
                    throw LuaDeclarationValues.error("rayTrace.distance", "expected 0 < distance <= 64");
                }
                boolean liquids = bool(options.get("liquids"), "rayTrace.liquids", false);
                Vec3D start = Vec3D.createVector(player.posX, player.posY + 1.62D - player.yOffset, player.posZ);
                Vec3D look = player.getLook(1);
                MovingObjectPosition hit = world.rayTraceBlocks_do(start,
                        start.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance),
                        liquids);
                LuaTable result = new LuaTable();
                result.set("kind", "miss");
                if (hit != null) {
                    LuaBlockActionContext context = new LuaBlockActionContext(world, hit.blockX, hit.blockY, hit.blockZ,
                            player, stack, hit.sideHit, mutable);
                    tracedTargets.add(context);
                    result.set("kind", "block");
                    result.set("block", context.blockAccess());
                    result.set("position", context.get("position"));
                    result.set("face", context.get("face"));
                }
                return result;
            }
        });
    }

    public LuaTable entityAccess(Entity entity) {
        return LuaEntityActionAccess.create(scope, player, entity);
    }

    @Override
    public void close() {
        scope.close();
        for (LuaBlockActionContext target : tracedTargets) {
            target.close();
        }
        if (blockTarget != null) {
            blockTarget.close();
        }
    }
}
