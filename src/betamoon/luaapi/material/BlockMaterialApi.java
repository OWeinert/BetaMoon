package betamoon.luaapi.material;

import betamoon.luaapi.utils.LuaCallbackScope;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Lua entry point for block and fluid native materials. */
public final class BlockMaterialApi {
    private BlockMaterialApi() {
    }

    public static void attach(LuaTable materials) {
        LuaTable blocks = new LuaTable();
        blocks.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaCallbackScope.requireNoBindingMutation();
                LuaValue definition = argument(args, blocks);
                if (!definition.istable()) {
                    throw new LuaError("material add expects a definition table.");
                }
                return new BlockMaterialReference(BlockMaterialRegistry.register(definition));
            }
        });
        blocks.set("get", lookup(blocks, false));
        blocks.set("getRequired", lookup(blocks, true));
        materials.set("blocks", blocks);
    }

    private static VarArgFunction lookup(LuaTable blocks, boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = argument(args, blocks).checkjstring();
                BlockMaterialRegistry.Entry entry = BlockMaterialRegistry.find(name);
                if (entry == null) {
                    if (required) {
                        throw new LuaError("Material is not registered: " + name);
                    }
                    return NIL;
                }
                return new BlockMaterialReference(entry);
            }
        };
    }

    private static LuaValue argument(Varargs args, LuaTable receiver) {
        return args.arg(args.arg1() == receiver ? 2 : 1);
    }
}
