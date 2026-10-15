package betamoon.luaapi.block;

import net.minecraft.src.Block;
import betamoon.luamodloader.LuaContentRegistry;
import org.luaj.vm2.LuaValue;

/** Declarative entry point for block creation and registration. */
public final class BlockApi {
    private BlockApi() {
    }

    public static Block add(LuaValue definition) {
        Block block;
        if ("fluid".equals(definition.get("type").optjstring(""))) {
            block = FluidBlockRegistration.register(definition);
        } else {
            block = BlockRegistration.register(new BlockDeclaration(definition));
        }
        LuaValue key = definition.get("key");
        LuaContentRegistry.recordDeclaredKey("block", block.blockID, key.isstring() ? key.tojstring() : null);
        return block;
    }
}
