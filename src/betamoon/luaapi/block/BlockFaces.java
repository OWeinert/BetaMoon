package betamoon.luaapi.block;

import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * Public names always describe faces of the block, independent of engine
 * conventions.
 */
public final class BlockFaces {
    private BlockFaces() {
    }

    public static int side(String name, int facing) {
        return BlockFace.resolve(name, facing).nativeSide;
    }

    public static String[] parse(LuaValue value, String path, boolean relative) {
        if (value.isnil()) {
            BlockFace[] faces = BlockFace.values();
            String[] names = new String[faces.length];
            for (int i = 0; i < faces.length; i++) {
                names[i] = faces[i].luaName;
            }
            return names;
        }
        int n = length(value, path);
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            names[i] = string(value.get(i + 1), path);
            side(names[i], relative ? 2 : -1);
            for (int j = 0; j < i; j++) {
                if (names[j].equals(names[i])) {
                    throw error(path, "duplicate direction");
                }
            }
        }
        return names;
    }
}
