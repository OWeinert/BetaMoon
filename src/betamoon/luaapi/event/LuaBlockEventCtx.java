package betamoon.luaapi.event;

import betamoon.event.context.BlockEventCtx;
import betamoon.luaapi.utils.PositionI;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaBlockEventCtx extends LuaTable {
    private final BlockEventCtx context;

    public LuaBlockEventCtx(BlockEventCtx context) {
        this.context = context;
        if (context != null) {
            set("x", context.getX());
            set("y", context.getY());
            set("z", context.getZ());
            set("position", new PositionI(context.getX(), context.getY(), context.getZ()));
            set("side", context.getSideHit());
            set("id", context.getBlockId());
            set("damage", context.getBlockMeta());
            Block block = context.getBlockId() >= 0 && context.getBlockId() < Block.blocksList.length
                ? Block.blocksList[context.getBlockId()] : null;
            if (block != null) {
                set("name", LuaValue.valueOf(block.getBlockName()));
                set("displayName", LuaValue.valueOf(block.translateBlockName()));
            }
        }
    }

    private static final class GetX extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetX(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getX());
        }
    }

    private static final class GetY extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetY(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getY());
        }
    }

    private static final class GetZ extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetZ(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getZ());
        }
    }

    private static final class GetSideHit extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetSideHit(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getSideHit());
        }
    }

    private static final class GetPos extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetPos(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return new PositionI(owner.context.getX(), owner.context.getY(), owner.context.getZ());
        }
    }

    private static final class GetBlockId extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetBlockId(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            int blockId = owner.context.getBlockId();
            if (blockId < 0) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(blockId);
        }
    }

    private static final class GetBlockDamage extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetBlockDamage(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            int blockId = owner.context.getBlockId();
            if (blockId < 0) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getBlockMeta());
        }
    }

    private static final class GetName extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetName(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            Block block = resolveBlock(owner);
            if (block == null) {
                return LuaValue.valueOf("NULL BLOCK");
            }
            String name = block.getBlockName();
            if (name == null || name.length() == 0) {
                return LuaValue.valueOf("UNKNOWN BLOCK");
            }
            return LuaValue.valueOf(name);
        }
    }

    private static final class GetDisplayName extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetDisplayName(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            Block block = resolveBlock(owner);
            if (block == null) {
                return LuaValue.valueOf("NULL BLOCK");
            }
            String name = block.translateBlockName();
            if (name == null || "null.name".equals(name) || "Unknown".equals(name) || name.endsWith(".name")) {
                return LuaValue.valueOf("UNKNOWN BLOCK");
            }
            return LuaValue.valueOf(name);
        }
    }

    private static Block resolveBlock(LuaBlockEventCtx owner) {
        if (owner.context == null) {
            return null;
        }
        int blockId = owner.context.getBlockId();
        if (blockId < 0 || blockId >= Block.blocksList.length) {
            return null;
        }
        return Block.blocksList[blockId];
    }
}
