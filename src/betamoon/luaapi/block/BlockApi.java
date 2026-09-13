package betamoon.luaapi.block;

import net.minecraft.src.Block;
import org.luaj.vm2.LuaValue;

/** Declarative entry point for block creation and registration. */
public final class BlockApi {
    private BlockApi() {
    }

    public static Block add(LuaValue definition) {
        return BlockRegistration.register(new BlockDeclaration(definition));
    }
}
