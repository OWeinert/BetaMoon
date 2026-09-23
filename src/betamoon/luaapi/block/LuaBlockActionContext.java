package betamoon.luaapi.block;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.item.LuaItemStackAccess;
import betamoon.luaapi.tileentity.LuaTileDataAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.world.LuaExplosionApi;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.tileentity.LuaTileEntity;
import betamoon.world.explosion.ExplosionExecution;
import betamoon.world.explosion.ExplosionRequest;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * Live, invocation-scoped access. Query contexts prohibit gameplay mutation.
 */
public final class LuaBlockActionContext extends LuaTable implements AutoCloseable {
    public final World world;
    public final int x;
    public final int y;
    public final int z;
    public final int blockId;
    public final EntityPlayer player;
    private final LuaCallbackScope scope;
    private final int initialMetadata;
    public LuaBlockActionContext(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, int face,
            boolean mutable) {
        this(world, x, y, z, player, stack, face, mutable, world.getBlockId(x, y, z), world.getBlockMetadata(x, y, z));
    }

    public LuaBlockActionContext(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, int face,
            boolean mutable, int blockId, int metadata) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.player = player;
        this.scope = new LuaCallbackScope(mutable && !world.multiplayerWorld);
        this.blockId = blockId;
        this.initialMetadata = metadata;
        set("x", x);
        set("y", y);
        set("z", z);
        set("id", blockId);
        set("damage", metadata);
        LuaTable pos = new LuaTable();
        pos.set("x", x);
        pos.set("y", y);
        pos.set("z", z);
        set("position", pos);
        if (face >= 0 && face < 6) {
            set("face", BlockFace.fromNative(face).luaName);
        }
        TileEntity tile = world.getBlockId(x, y, z) == blockId ? world.getBlockTileEntity(x, y, z) : null;
        if (tile instanceof LuaTileEntity) {
            LuaTable tileAccess = new LuaTable();
            tileAccess.set("data", LuaTileDataAccess.create(scope, (LuaTileEntity) tile));
            set("tileEntity", tileAccess);
        }
        set("world", LuaWorldActionAccess.create(scope, world, x, y, z));
        ExplosionRequest explosion = ExplosionExecution.current();
        if (explosion != null) {
            set("explosion", LuaExplosionApi.context(scope, world, explosion));
        }
        set("state", stateAccess());
        set("redstone", redstoneAccess());
        LuaTable target = new LuaTable();
        target.set("kind", "block");
        target.set("block", blockAccess());
        set("target", target);
        if (player != null) {
            set("player", entityAccess(player));
        }
        if (stack != null) {
            set("stack", LuaItemStackAccess.create(scope, player, stack));
        }
        set("schedule", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                write();
                requireBlock();
                int offset = args.arg1() == LuaBlockActionContext.this ? 1 : 0;
                int delay = integer(args.arg(1 + offset), "schedule.delay", 1, 1000000);
                BlockTickRegistry.schedule(world, x, y, z, blockId, delay);
                return NIL;
            }
        });
        set("random", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                read();
                return valueOf(world.rand.nextDouble());
            }
        });
    }

    @Override
    public void close() {
        scope.close();
    }

    LuaCallbackScope callbackScope() {
        return scope;
    }

    private void read() {
        scope.requireActive();
    }

    private void write() {
        scope.requireMutable();
    }

    public LuaTable entityAccess(Entity entity) {
        return LuaEntityActionAccess.create(scope, player, entity);
    }

    private void requireBlock() {
        if (world.getBlockId(x, y, z) != blockId) {
            throw LuaDeclarationValues.error("context", "block was replaced");
        }
    }

    private BlockStateSchema schema() {
        read();
        BlockDefinition def = BlockCallbackRegistry.get(blockId);
        if (def == null) {
            throw LuaDeclarationValues.error("state", "block has no declared state");
        }
        return def.state;
    }

    private LuaTable stateAccess() {
        final LuaTable api = new LuaTable();
        api.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                int metadata = world.getBlockId(x, y, z) == blockId ? world.getBlockMetadata(x, y, z) : initialMetadata;
                return schema().get(metadata, string(argument(a, api, 1), "state.get"));
            }
        });
        api.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                write();
                requireBlock();
                BlockStateSchema schema = schema();
                int old = world.getBlockMetadata(x, y, z);
                int next = schema.set(old, string(argument(a, api, 1), "state.set"), argument(a, api, 2));
                if (next != old) {
                    world.setBlockMetadataWithNotify(x, y, z, next);
                }
                return NIL;
            }
        });
        return api;
    }

    public LuaTable blockAccess() {
        final LuaTable api = new LuaTable();
        api.set("id", blockId);
        api.set("state", stateAccess());
        api.set("rotate", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                write();
                requireBlock();
                BlockDefinition def = BlockCallbackRegistry.get(blockId);
                if (def == null || !def.state.has("facing")) {
                    return FALSE;
                }
                String direction = string(argument(a, api, 1), "rotate");
                if (!direction.equals("clockwise") && !direction.equals("counterclockwise")) {
                    throw LuaDeclarationValues.error("rotate", "expected clockwise or counterclockwise");
                }
                String[] order = {"north", "east", "south", "west"};
                String current = def.state.get(world.getBlockMetadata(x, y, z), "facing").tojstring();
                for (int i = 0; i < 4; i++) {
                    if (order[i].equals(current)) {
                        int next = def.state.set(world.getBlockMetadata(x, y, z), "facing",
                                valueOf(order[(i + (direction.equals("clockwise") ? 1 : 3)) % 4]));
                        world.setBlockMetadataWithNotify(x, y, z, next);
                        return TRUE;
                    }
                }
                return FALSE;
            }
        });
        return api;
    }

    private LuaTable redstoneAccess() {
        final LuaTable api = new LuaTable();
        api.set("isPowered", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                read();
                LuaValue name = argument(a, api, 1);
                if (name.isnil()) {
                    return valueOf(world.isBlockIndirectlyGettingPowered(x, y, z));
                }
                BlockDefinition def = BlockCallbackRegistry.get(blockId);
                BlockFace side = BlockFace.resolve(string(name, "redstone.isPowered"),
                        def == null ? -1 : def.facing(world.getBlockMetadata(x, y, z)));
                return valueOf(world.isBlockIndirectlyProvidingPowerTo(x + side.xOffset, y + side.yOffset,
                        z + side.zOffset, side.nativeSide));
            }
        });
        api.set("notifyNeighbors", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                write();
                requireBlock();
                world.notifyBlocksOfNeighborChange(x, y, z, blockId);
                return NIL;
            }
        });
        return api;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int n) {
        return args.arg(n + (args.arg1() == receiver ? 1 : 0));
    }

    private static int coordinate(LuaValue value) {
        return integer(value, "coordinate", -30000000, 30000000);
    }
}
