package betamoon.luaapi.block;

import java.util.HashSet;
import java.util.Set;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * Inventory identity occupies explicitly selected, unshifted Minecraft metadata
 * bits.
 */
public final class BlockSubtypes {
    public static final BlockSubtypes NONE = new BlockSubtypes();
    public final boolean enabled;
    public final int mask;
    public final String signature;
    private final BlockStateSchema state;

    private BlockSubtypes() {
        enabled = false;
        mask = 0;
        signature = "none";
        state = null;
    }

    public BlockSubtypes(LuaValue declaration, BlockStateSchema state, BlockPlacementDefinition placement) {
        this.state = state;
        enabled = !declaration.isnil() && !declaration.raweq(LuaValue.FALSE);
        int selected = 0;
        if (enabled) {
            if (declaration.raweq(LuaValue.TRUE)) {
                selected = 15 & ~state.usedMask();
            } else {
                fields(declaration, "subtypes", "mask", "fields");
                if (declaration.get("mask").isnil() == declaration.get("fields").isnil()) {
                    throw error("subtypes", "provide exactly one of mask or fields");
                }
                if (!declaration.get("mask").isnil()) {
                    selected = integer(declaration.get("mask"), "subtypes.mask", 1, 15);
                } else {
                    LuaValue names = declaration.get("fields");
                    int count = length(names, "subtypes.fields");
                    if (count < 1 || count > 4) {
                        throw error("subtypes.fields", "expected 1..4 state field names");
                    }
                    Set<String> unique = new HashSet<String>();
                    for (int i = 1; i <= count; i++) {
                        String name = string(names.get(i), "subtypes.fields[" + i + "]");
                        if (!unique.add(name)) {
                            throw error("subtypes.fields", "duplicate state field " + name);
                        }
                        selected |= state.metadataMask(name);
                    }
                }
            }
            if (selected == 0) {
                throw error("subtypes", "no unused metadata bits; choose persistent state fields explicitly");
            }
            int managed = state.has("attachedFace") ? state.metadataMask("attachedFace") : 0;
            if (placement.horizontal) {
                managed |= state.metadataMask("facing");
            }
            if ((selected & managed) != 0) {
                throw error("subtypes", "cannot include placement-managed facing or attachment bits");
            }
            state.validateSubtypeMask(selected);
        }
        mask = selected;
        signature = enabled ? "metadata:" + mask : "none";
    }

    public boolean validItemMetadata(int metadata) {
        return metadata >= 0 && metadata <= 15 && (metadata & ~mask) == 0
                && (state == null || state.validMetadata(metadata, mask));
    }

    public int variant(int metadata) {
        return state == null ? 0 : state.normalizeMetadata(metadata, mask) & mask;
    }

    public int placedMetadata(int metadata) {
        return state == null ? 0 : (state.defaults & ~mask) | variant(metadata);
    }
}
