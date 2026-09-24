package betamoon.luaapi.world;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.system.SystemsApi;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.system.WorldServiceDefinition;
import betamoon.system.WorldServiceRuntime;
import betamoon.tileentity.LuaTileEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Chunk;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IInventory;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.WorldChunkManager;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs coherent, detached world/block/tile/biome/chunk snapshots on a scoped world facade. */
public final class LuaWorldDataAccess {
    private static final int MAX_PLAYERS = 128;
    private static final int MAX_TILE_INVENTORY = 256;
    private static final double MAX_PLAYER_RADIUS = 128.0D;

    private LuaWorldDataAccess() {
    }

    public static void install(final LuaTable api, final LuaCallbackScope scope, final World world,
            final int originX, final int originY, final int originZ) {
        api.set("getInfo", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return worldInfo(world);
            }
        });
        api.set("getBlockView", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                Position position = position(arguments, api, "getBlockView");
                return block(world, position);
            }
        });
        api.set("getTile", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                Position position = position(arguments, api, "getTile");
                return tile(scope, world, position);
            }
        });
        api.set("getBiomeData", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                int offset = arguments.arg1() == api ? 1 : 0;
                LuaValue first = arguments.arg(1 + offset);
                int x;
                int z;
                if (first.istable()) {
                    x = coordinate(first.get("x"), "getBiomeData.position.x");
                    z = coordinate(first.get("z"), "getBiomeData.position.z");
                } else {
                    x = coordinate(first, "getBiomeData.x");
                    z = coordinate(arguments.arg(2 + offset), "getBiomeData.z");
                }
                return biome(world, x, z);
            }
        });
        api.set("getChunk", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                int offset = arguments.arg1() == api ? 1 : 0;
                LuaValue first = arguments.arg(1 + offset);
                int x;
                int z;
                if (first.istable()) {
                    x = coordinate(first.get("x"), "getChunk.position.x");
                    z = coordinate(first.get("z"), "getChunk.position.z");
                } else {
                    x = coordinate(first, "getChunk.x");
                    z = coordinate(arguments.arg(2 + offset), "getChunk.z");
                }
                return chunk(world, x, z);
            }
        });
        api.set("getPlayers", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                int offset = arguments.arg1() == api ? 1 : 0;
                LuaValue options = arguments.arg(1 + offset);
                if (options.isnil()) {
                    options = new LuaTable();
                }
                if (!options.istable()) {
                    throw new LuaError("getPlayers options must be a table.");
                }
                double x = number(options.get("x"), originX + 0.5D, "getPlayers.x");
                double y = number(options.get("y"), originY + 0.5D, "getPlayers.y");
                double z = number(options.get("z"), originZ + 0.5D, "getPlayers.z");
                double radius = number(options.get("radius"), MAX_PLAYER_RADIUS, "getPlayers.radius");
                int limit = integer(options.get("limit"), MAX_PLAYERS, 1, MAX_PLAYERS, "getPlayers.limit");
                if (radius <= 0.0D || radius > MAX_PLAYER_RADIUS) {
                    throw new LuaError("getPlayers.radius must be greater than 0 and at most "
                            + (int) MAX_PLAYER_RADIUS + ".");
                }
                return players(scope, world, x, y, z, radius, limit);
            }
        });
        api.set("getSystemData", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                WorldServiceDefinition definition = SystemsApi.definition(
                        arguments.arg(arguments.arg1() == api ? 2 : 1), "getSystemData.system");
                return WorldServiceRuntime.snapshot(world, definition);
            }
        });
    }

    private static LuaTable worldInfo(World world) {
        return LuaWorldInfo.fromWorld(world);
    }

    private static LuaValue block(World world, Position position) {
        if (!loaded(world, position.x, position.z)) {
            return LuaValue.NIL;
        }
        int id = world.getBlockId(position.x, position.y, position.z);
        int metadata = world.getBlockMetadata(position.x, position.y, position.z);
        Material material = world.getBlockMaterial(position.x, position.y, position.z);
        LuaTable result = positioned(position);
        result.set("id", id);
        result.set("damage", metadata);
        result.set("solid", LuaValue.valueOf(world.isBlockNormalCube(position.x, position.y, position.z)));
        result.set("canSeeSky", LuaValue.valueOf(world.canBlockSeeTheSky(position.x, position.y, position.z)));
        result.set("powered", LuaValue.valueOf(world.isBlockIndirectlyGettingPowered(
                position.x, position.y, position.z)));
        result.set("light", world.getBlockLightValue(position.x, position.y, position.z));
        result.set("hasTile", LuaValue.valueOf(world.getBlockTileEntity(position.x, position.y, position.z) != null));
        LuaTable materialView = new LuaTable();
        materialView.set("liquid", LuaValue.valueOf(material.getIsLiquid()));
        materialView.set("solid", LuaValue.valueOf(material.getIsSolid()));
        materialView.set("burning", LuaValue.valueOf(material.getBurning()));
        materialView.set("translucent", LuaValue.valueOf(material.getIsTranslucent()));
        materialView.set("mobility", material.getMaterialMobility());
        result.set("material", materialView);
        return result;
    }

    private static LuaValue tile(LuaCallbackScope scope, World world, Position position) {
        if (!loaded(world, position.x, position.z)) {
            return LuaValue.NIL;
        }
        TileEntity entity = world.getBlockTileEntity(position.x, position.y, position.z);
        if (entity == null) {
            return LuaValue.NIL;
        }
        LuaTable result = positioned(position);
        result.set("className", entity.getClass().getName());
        result.set("valid", LuaValue.valueOf(!entity.func_31006_g()));
        if (entity instanceof LuaTileEntity) {
            LuaTileEntity lua = (LuaTileEntity) entity;
            result.set("type", lua.getTypeName());
            result.set("data", lua.snapshotData());
        }
        if (entity instanceof IInventory) {
            IInventory inventory = (IInventory) entity;
            result.set("inventorySize", inventory.getSizeInventory());
            LuaTable stacks = new LuaTable();
            int captured = Math.min(inventory.getSizeInventory(), MAX_TILE_INVENTORY);
            for (int slot = 0; slot < captured; slot++) {
                stacks.set(slot + 1, stack(inventory.getStackInSlot(slot)));
            }
            result.set("inventory", stacks);
            if (captured < inventory.getSizeInventory()) {
                result.set("inventoryTruncated", LuaValue.TRUE);
            }
        }
        result.set("getNbt", new TileNbt(scope, world, position, entity));
        return result;
    }

    private static LuaValue biome(World world, int x, int z) {
        WorldChunkManager manager = world.getWorldChunkManager();
        BiomeGenBase biome = manager.loadBlockGeneratorData(null, x, z, 1, 1)[0];
        double temperature = manager.temperature[0];
        double humidity = manager.humidity[0];
        LuaTable result = new LuaTable();
        result.set("name", biome.biomeName);
        result.set("color", biome.color);
        result.set("topBlockId", biome.topBlock & 255);
        result.set("fillerBlockId", biome.fillerBlock & 255);
        result.set("snow", LuaValue.valueOf(biome.getEnableSnow()));
        result.set("rain", LuaValue.valueOf(biome.canSpawnLightningBolt()));
        result.set("temperature", temperature);
        result.set("humidity", humidity);
        return result;
    }

    private static LuaValue chunk(World world, int blockX, int blockZ) {
        if (!loaded(world, blockX, blockZ)) {
            return LuaValue.NIL;
        }
        Chunk chunk = world.getChunkFromBlockCoords(blockX, blockZ);
        int entities = 0;
        for (List<?> slice : chunk.entities) {
            entities += slice.size();
        }
        LuaTable result = new LuaTable();
        result.set("x", chunk.xPosition);
        result.set("z", chunk.zPosition);
        result.set("minX", chunk.xPosition << 4);
        result.set("maxX", (chunk.xPosition << 4) + 15);
        result.set("minZ", chunk.zPosition << 4);
        result.set("maxZ", (chunk.zPosition << 4) + 15);
        result.set("loaded", LuaValue.valueOf(chunk.isChunkLoaded));
        result.set("terrainPopulated", LuaValue.valueOf(chunk.isTerrainPopulated));
        result.set("modified", LuaValue.valueOf(chunk.isModified));
        result.set("lowestHeight", chunk.lowestBlockHeight);
        result.set("entityCount", entities);
        result.set("tileCount", chunk.chunkTileEntityMap.size());
        return result;
    }

    private static LuaTable players(LuaCallbackScope scope, World world, double x, double y, double z,
            double radius, int limit) {
        List<EntityPlayer> found = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (Object value : world.playerEntities) {
            if (value instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) value;
                if (!player.isDead && player.getDistanceSq(x, y, z) <= radiusSquared) {
                    found.add(player);
                }
            }
        }
        Collections.sort(found, new Comparator<EntityPlayer>() {
            public int compare(EntityPlayer left, EntityPlayer right) {
                return Double.compare(left.getDistanceSq(x, y, z), right.getDistanceSq(x, y, z));
            }
        });
        LuaTable result = new LuaTable();
        for (int index = 0; index < Math.min(limit, found.size()); index++) {
            result.set(index + 1, LuaEntityActionAccess.create(scope, null, found.get(index)));
        }
        return result;
    }

    private static LuaValue stack(ItemStack stack) {
        if (stack == null) {
            return LuaValue.NIL;
        }
        LuaTable result = new LuaTable();
        result.set("id", stack.itemID);
        result.set("count", stack.stackSize);
        result.set("damage", stack.getItemDamage());
        return result;
    }

    private static Position position(Varargs arguments, LuaTable receiver, String path) {
        int offset = arguments.arg1() == receiver ? 1 : 0;
        LuaValue first = arguments.arg(1 + offset);
        if (first.istable()) {
            return new Position(coordinate(first.get("x"), path + ".position.x"),
                    integer(first.get("y"), 0, 0, 127, path + ".position.y"),
                    coordinate(first.get("z"), path + ".position.z"));
        }
        return new Position(coordinate(first, path + ".x"),
                integer(arguments.arg(2 + offset), 0, 0, 127, path + ".y"),
                coordinate(arguments.arg(3 + offset), path + ".z"));
    }

    private static LuaTable positioned(Position position) {
        LuaTable result = new LuaTable();
        LuaTable coordinates = new LuaTable();
        coordinates.set("x", position.x);
        coordinates.set("y", position.y);
        coordinates.set("z", position.z);
        result.set("position", coordinates);
        return result;
    }

    private static boolean loaded(World world, int x, int z) {
        return world.blockExists(x, 64, z);
    }

    private static int coordinate(LuaValue value, String path) {
        return integer(value, 0, -30000000, 30000000, path);
    }

    private static int integer(LuaValue value, int fallback, int minimum, int maximum, String path) {
        int result = value.isnil() ? fallback : value.checkint();
        if (result < minimum || result > maximum) {
            throw new LuaError(path + " must be between " + minimum + " and " + maximum + ".");
        }
        return result;
    }

    private static double number(LuaValue value, double fallback, String path) {
        double result = value.isnil() ? fallback : value.checkdouble();
        if (!Double.isFinite(result)) {
            throw new LuaError(path + " must be finite.");
        }
        return result;
    }

    private static final class Position {
        private final int x;
        private final int y;
        private final int z;
        private Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class TileNbt extends VarArgFunction {
        private final LuaCallbackScope scope;
        private final World world;
        private final Position position;
        private final TileEntity tile;
        private TileNbt(LuaCallbackScope scope, World world, Position position, TileEntity tile) {
            this.scope = scope;
            this.world = world;
            this.position = position;
            this.tile = tile;
        }
        public Varargs invoke(Varargs arguments) {
            scope.requireActive();
            if (tile.func_31006_g()
                    || world.getBlockTileEntity(position.x, position.y, position.z) != tile) {
                return NIL;
            }
            try {
                NBTTagCompound tag = new NBTTagCompound();
                tile.writeToNBT(tag);
                return LuaNbtView.capture(tag);
            } catch (Throwable error) {
                throw new LuaError("Could not capture tile NBT: "
                        + (error.getMessage() == null ? error.toString() : error.getMessage()));
            }
        }
    }
}
