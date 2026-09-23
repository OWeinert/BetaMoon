package betamoon.luaapi.world;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityKind;
import betamoon.entity.EntitySpawner;
import betamoon.entity.EntitySpatialQueries;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.EntityTypeRegistry;
import betamoon.entity.LuaEntityPart;
import betamoon.luaapi.entity.EntityTypeReference;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.capability.LuaCapabilityAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import net.minecraft.src.Block;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EnumSkyBlock;
import net.minecraft.src.Material;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.id;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Narrow live access for a single callback invocation. */
public final class LuaWorldActionAccess {
    private LuaWorldActionAccess() {
    }

    public static LuaTable create(LuaCallbackScope scope, World world, int x, int y, int z) {

        final LuaTable api = new LuaTable();
        api.set("getTime", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(world.getWorldTime());
            }
        });
        api.set("getDayTime", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(Math.floorMod(world.getWorldTime(), 24000));
            }
        });
        api.set("isDaytime", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(world.isDaytime());
            }
        });
        api.set("getDifficulty", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(world.difficultySetting);
            }
        });
        api.set("getSpawnPoint", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                ChunkCoordinates spawn = world.getSpawnPoint();
                LuaTable position = new LuaTable();
                position.set("x", spawn.x);
                position.set("y", spawn.y);
                position.set("z", spawn.z);
                return position;
            }
        });
        api.set("getDimension", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(world.worldProvider.worldType);
            }
        });
        api.set("getWeather", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                LuaTable weather = new LuaTable();
                weather.set("raining", valueOf(world.getWorldInfo().getRaining()));
                weather.set("thundering", valueOf(world.getWorldInfo().getThundering()));
                weather.set("rainStrength", world.func_27162_g(1.0f));
                weather.set("thunderStrength", world.func_27166_f(1.0f));
                return weather;
            }
        });
        api.set("isLoaded", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int bz = coordinate(argument(a, api, 2));
                return valueOf(world.blockExists(bx, 64, bz));
            }
        });
        api.set("getBiome", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int bz = coordinate(argument(a, api, 2));
                return valueOf(world.getWorldChunkManager().getBiomeGenAt(bx, bz).biomeName);
            }
        });
        api.set("getHeight", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int bz = coordinate(argument(a, api, 2));
                return world.blockExists(bx, 64, bz) ? valueOf(world.getHeightValue(bx, bz)) : NIL;
            }
        });
        api.set("getLight", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int by = integer(argument(a, api, 2), "getLight.y", 0, 127);
                int bz = coordinate(argument(a, api, 3));
                if (!world.blockExists(bx, by, bz)) {
                    return NIL;
                }
                LuaTable light = new LuaTable();
                light.set("combined", world.getBlockLightValue(bx, by, bz));
                light.set("sky", world.getSavedLightValue(EnumSkyBlock.Sky, bx, by, bz));
                light.set("block", world.getSavedLightValue(EnumSkyBlock.Block, bx, by, bz));
                light.set("brightness", world.getLightBrightness(bx, by, bz));
                return light;
            }
        });
        api.set("isSolid", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int by = integer(argument(a, api, 2), "isSolid.y", 0, 127);
                int bz = coordinate(argument(a, api, 3));
                return valueOf(world.blockExists(bx, by, bz) && world.isBlockNormalCube(bx, by, bz));
            }
        });
        api.set("canSeeSky", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int by = integer(argument(a, api, 2), "canSeeSky.y", 0, 127);
                int bz = coordinate(argument(a, api, 3));
                return valueOf(world.blockExists(bx, by, bz) && world.canBlockSeeTheSky(bx, by, bz));
            }
        });
        api.set("isRainingAt", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int by = integer(argument(a, api, 2), "isRainingAt.y", 0, 127);
                int bz = coordinate(argument(a, api, 3));
                return valueOf(world.blockExists(bx, by, bz) && world.canBlockBeRainedOn(bx, by, bz));
            }
        });
        api.set("isLiquid", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int by = integer(argument(a, api, 2), "isLiquid.y", 0, 127);
                int bz = coordinate(argument(a, api, 3));
                Material material = world.blockExists(bx, by, bz) ? world.getBlockMaterial(bx, by, bz) : Material.air;
                return valueOf(material.getIsLiquid());
            }
        });
        api.set("isPowered", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                boolean origin = argument(a, api, 1).isnil();
                int bx = origin ? x : coordinate(argument(a, api, 1));
                int by = origin ? y : integer(argument(a, api, 2), "isPowered.y", 0, 127);
                int bz = origin ? z : coordinate(argument(a, api, 3));
                return valueOf((origin || world.blockExists(bx, by, bz))
                        && world.isBlockIndirectlyGettingPowered(bx, by, bz));
            }
        });
        api.set("getClosestPlayer", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                double radius = number(argument(a, api, 1), "getClosestPlayer.radius");
                if (radius <= 0 || radius > 64) {
                    throw LuaDeclarationValues.error("getClosestPlayer.radius", "expected > 0 and <= 64");
                }
                EntityPlayer nearest = world.getClosestPlayer(x + 0.5, y + 0.5, z + 0.5, radius);
                return nearest == null ? NIL : LuaEntityActionAccess.create(scope, null, nearest);
            }
        });
        api.set("getBlock", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int by = integer(argument(a, api, 2), "getBlock.y", 0, 127);
                int bz = coordinate(argument(a, api, 3));
                if (!world.blockExists(bx, by, bz)) {
                    return NIL;
                }
                LuaTable value = new LuaTable();
                value.set("id", world.getBlockId(bx, by, bz));
                value.set("damage", world.getBlockMetadata(bx, by, bz));
                return value;
            }
        });
        api.set("setBlock", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                int bx = coordinate(argument(a, api, 1));
                int by = integer(argument(a, api, 2), "y", 0, 127);
                int bz = coordinate(argument(a, api, 3));
                int id = id(argument(a, api, 4), "setBlock.id");
                if (id >= Block.blocksList.length || id != 0 && Block.blocksList[id] == null) {
                    throw LuaDeclarationValues.error("setBlock.id", "unknown block");
                }
                int damage = argument(a, api, 5).isnil() ? 0 : integer(argument(a, api, 5), "setBlock.damage", 0, 15);
                return valueOf(world.setBlockAndMetadataWithNotify(bx, by, bz, id, damage));
            }
        });
        api.set("playSound", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                String sound = string(argument(a, api, 1), "sound");
                float volume = argument(a, api, 2).isnil() ? 1 : (float) number(argument(a, api, 2), "volume");
                float pitch = argument(a, api, 3).isnil() ? 1 : (float) number(argument(a, api, 3), "pitch");
                world.playSoundEffect(x + .5, y + .5, z + .5, sound, volume, pitch);
                return NIL;
            }
        });
        api.set("spawnParticle", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                String name = string(argument(a, api, 1), "particle");
                world.spawnParticle(name, x + .5, y + .5, z + .5, 0, 0, 0);
                return NIL;
            }
        });
        api.set("spawnEntity", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                LuaValue type = argument(a, api, 1);
                AssetKey key;
                try {
                    key = type instanceof EntityTypeReference
                            ? ((EntityTypeReference) type).key()
                            : AssetKey.parse(string(type, "spawnEntity.type"));
                } catch (IllegalArgumentException error) {
                    throw new LuaError("spawnEntity.type: " + error.getMessage());
                }
                LuaValue options = argument(a, api, 2);
                fields(options, "spawnEntity.options", "position", "rotation", "owner");
                LuaValue position = required(options, "position");
                fields(position, "spawnEntity.position", "x", "y", "z");
                double px = number(required(position, "x"), "spawnEntity.position.x");
                double py = number(required(position, "y"), "spawnEntity.position.y");
                double pz = number(required(position, "z"), "spawnEntity.position.z");
                if (Math.abs(px) > 30000000 || Math.abs(pz) > 30000000) {
                    throw LuaDeclarationValues.error("spawnEntity.position", "outside world coordinate bounds");
                }
                LuaValue rotation = options.get("rotation");
                float yaw = 0;
                float pitch = 0;
                if (!rotation.isnil()) {
                    fields(rotation, "spawnEntity.rotation", "yaw", "pitch");
                    yaw = (float) number(rotation.get("yaw").isnil() ? ZERO : rotation.get("yaw"),
                            "spawnEntity.rotation.yaw");
                    pitch = (float) number(rotation.get("pitch").isnil() ? ZERO : rotation.get("pitch"),
                            "spawnEntity.rotation.pitch");
                    if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                        throw LuaDeclarationValues.error("spawnEntity.rotation", "angles exceed the supported range");
                    }
                }
                Entity owner = null;
                LuaValue ownerValue = options.get("owner");
                if (!ownerValue.isnil()) {
                    EntityTypeDefinition definition = EntityTypeRegistry.find(key);
                    if (definition == null) {
                        throw LuaDeclarationValues.error("spawnEntity.type", "entity type is not registered");
                    }
                    if (definition.kind != EntityKind.PROJECTILE) {
                        throw LuaDeclarationValues.error("spawnEntity.options.owner",
                                "owner is supported only for projectile types");
                    }
                    owner = LuaEntityActionAccess.target(ownerValue, world, "spawnEntity.options.owner");
                    if (owner == null || owner instanceof LuaEntityPart) {
                        throw LuaDeclarationValues.error("spawnEntity.options.owner",
                                "expected a live non-part entity in the same world");
                    }
                }
                EntitySpawner.Result result = EntitySpawner.spawn(world, key, px, py, pz, yaw, pitch, owner);
                if (result.entity == null) {
                    return varargsOf(new LuaValue[]{NIL, valueOf(result.reason)});
                }
                return LuaEntityActionAccess.create(scope, null, result.entity);
            }
        });
        api.set("getNearbyEntities", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                double radius = number(argument(a, api, 1), "getNearbyEntities.radius");
                if (radius <= 0 || radius > 32) {
                    throw LuaDeclarationValues.error("getNearbyEntities.radius", "expected a radius > 0 and <= 32");
                }
                LuaTable result = new LuaTable();
                int index = 0;
                for (Entity entity : EntitySpatialQueries.nearby(world, null,
                        x + 0.5, y + 0.5, z + 0.5, radius, 256)) {
                    result.set(++index, LuaEntityActionAccess.create(scope, null, entity));
                }
                return result;
            }
        });
        api.set("notifyNeighbors", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                boolean origin = argument(a, api, 1).isnil();
                int bx = origin ? x : coordinate(argument(a, api, 1));
                int by = origin ? y : integer(argument(a, api, 2), "notifyNeighbors.y", 0, 127);
                int bz = origin ? z : coordinate(argument(a, api, 3));
                if (!origin && !world.blockExists(bx, by, bz)) {
                    return FALSE;
                }
                world.notifyBlocksOfNeighborChange(bx, by, bz, world.getBlockId(bx, by, bz));
                return TRUE;
            }
        });
        LuaWorldDataAccess.install(api, scope, world, x, y, z);
        LuaCapabilityAccess.installWorld(api, scope, world);
        return api;

    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static int coordinate(LuaValue value) {
        return integer(value, "coordinate", -30000000, 30000000);
    }
}
