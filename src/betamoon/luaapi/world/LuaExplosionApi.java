package betamoon.luaapi.world;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.utils.PositionI;
import betamoon.world.explosion.ExplosionExecution;
import betamoon.world.explosion.ExplosionPosition;
import betamoon.world.explosion.ExplosionRequest;
import betamoon.world.explosion.ExplosionResult;
import net.minecraft.src.Entity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Parsing and detached Lua snapshots for the configurable explosion API. */
public final class LuaExplosionApi {
    private LuaExplosionApi() {
    }

    static void install(LuaTable api, LuaCallbackScope scope, World world, double originX, double originY,
            double originZ, Entity defaultSource) {
        api.set("createExplosion", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                LuaValue options = argument(args, api, 1);
                ExplosionRequest request = parse(options, world, originX, originY, originZ, defaultSource);
                try {
                    return result(ExplosionExecution.execute(world, request));
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw LuaDeclarationValues.error("createExplosion", error.getMessage());
                }
            }
        });
    }

    public static LuaTable context(LuaCallbackScope scope, World world, ExplosionRequest request) {
        LuaTable value = new LuaTable();
        value.set("position", position(request.x, request.y, request.z));
        value.set("strength", request.strength);
        value.set("fire", LuaValue.valueOf(request.fire));
        value.set("blocks", request.blockMode == ExplosionRequest.BlockMode.DESTROY ? "destroy" : "none");
        value.set("entities", LuaValue.valueOf(request.damageEntities));
        value.set("knockback", LuaValue.valueOf(request.knockback));
        value.set("drops", LuaValue.valueOf(request.drops));
        value.set("dropChance", request.dropChance);
        value.set("sound", LuaValue.valueOf(request.sound));
        value.set("particles", LuaValue.valueOf(request.particles));
        value.set("includeAffectedBlocks", LuaValue.valueOf(request.includeAffectedBlocks));
        if (request.source != null && !request.source.isDead && request.source.worldObj == world) {
            value.set("source", LuaEntityActionAccess.create(scope, null, request.source));
        }
        return value;
    }

    private static ExplosionRequest parse(LuaValue options, World world, double originX, double originY,
            double originZ, Entity defaultSource) {
        fields(options, "createExplosion.options", "position", "strength", "source", "fire", "blocks",
                "entities", "knockback", "drops", "dropChance", "sound", "particles", "includeAffectedBlocks");
        double x = originX;
        double y = originY;
        double z = originZ;
        LuaValue position = options.get("position");
        if (!position.isnil()) {
            fields(position, "createExplosion.position", "x", "y", "z");
            x = coordinate(required(position, "x"), "createExplosion.position.x");
            y = coordinate(required(position, "y"), "createExplosion.position.y");
            z = coordinate(required(position, "z"), "createExplosion.position.z");
        }
        double parsedStrength = number(required(options, "strength"), "createExplosion.strength");
        if (parsedStrength < 0.1D || parsedStrength > 16.0D) {
            throw LuaDeclarationValues.error("createExplosion.strength", "expected 0.1..16");
        }

        Entity source = source(options.get("source"), world, defaultSource);
        String blockMode = options.get("blocks").isnil()
                ? "destroy" : string(options.get("blocks"), "createExplosion.blocks");
        ExplosionRequest.BlockMode blocks;
        if ("destroy".equals(blockMode)) {
            blocks = ExplosionRequest.BlockMode.DESTROY;
        } else if ("none".equals(blockMode)) {
            blocks = ExplosionRequest.BlockMode.NONE;
        } else {
            throw LuaDeclarationValues.error("createExplosion.blocks", "expected 'destroy' or 'none'");
        }
        double parsedDropChance = options.get("dropChance").isnil()
                ? 0.3D : number(options.get("dropChance"), "createExplosion.dropChance");
        if (parsedDropChance < 0.0D || parsedDropChance > 1.0D) {
            throw LuaDeclarationValues.error("createExplosion.dropChance", "expected 0..1");
        }
        return new ExplosionRequest(x, y, z, (float) parsedStrength, source,
                bool(options.get("fire"), "createExplosion.fire", false), blocks,
                bool(options.get("entities"), "createExplosion.entities", true),
                bool(options.get("knockback"), "createExplosion.knockback", true),
                bool(options.get("drops"), "createExplosion.drops", true), (float) parsedDropChance,
                bool(options.get("sound"), "createExplosion.sound", true),
                bool(options.get("particles"), "createExplosion.particles", true),
                bool(options.get("includeAffectedBlocks"), "createExplosion.includeAffectedBlocks", false));
    }

    private static Entity source(LuaValue value, World world, Entity fallback) {
        if (value.isnil()) {
            return fallback == null || fallback.isDead || fallback.worldObj != world ? null : fallback;
        }
        if (value.isboolean() && !value.toboolean()) {
            return null;
        }
        Entity source = LuaEntityActionAccess.target(value, world, "createExplosion.source");
        if (source == null) {
            throw LuaDeclarationValues.error("createExplosion.source",
                    "expected a live entity handle in the same world, nil, or false");
        }
        return source;
    }

    private static LuaTable result(ExplosionResult result) {
        LuaTable value = new LuaTable();
        value.set("position", position(result.x, result.y, result.z));
        value.set("strength", result.strength);
        value.set("blocksAffected", result.blocksAffected);
        value.set("blocksDestroyed", result.blocksDestroyed);
        value.set("entitiesDamaged", result.entitiesDamaged);
        value.set("entitiesKnockedBack", result.entitiesKnockedBack);
        value.set("firesPlaced", result.firesPlaced);
        if (result.includesAffectedBlocks) {
            LuaTable blocks = new LuaTable();
            int index = 0;
            for (ExplosionPosition position : result.affectedBlocks) {
                blocks.set(++index, new PositionI(position.x, position.y, position.z));
            }
            value.set("affectedBlocks", blocks);
            value.set("affectedBlocksTruncated", LuaValue.valueOf(result.affectedBlocksTruncated));
        }
        return value;
    }

    private static LuaTable position(double x, double y, double z) {
        LuaTable value = new LuaTable();
        value.set("x", x);
        value.set("y", y);
        value.set("z", z);
        return value;
    }

    private static double coordinate(LuaValue value, String path) {
        double coordinate = number(value, path);
        if (Math.abs(coordinate) > 30000000.0D) {
            throw LuaDeclarationValues.error(path, "outside supported world bounds");
        }
        return coordinate;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }
}
