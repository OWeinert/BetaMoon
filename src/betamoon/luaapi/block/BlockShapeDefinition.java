package betamoon.luaapi.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.AxisAlignedBB;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Validated physical properties and local collision/selection geometry. */
public final class BlockShapeDefinition {
    public final boolean replaceable;
    public final boolean climbable;
    public final Boolean opaque;
    public final List<BlockBox> boxes;
    public final BlockBox selection;
    public final float slipperiness;
    public final int mobility;

    public BlockShapeDefinition(LuaValue def) {
        replaceable = bool(def.get("replaceable"), "replaceable", false);
        climbable = bool(def.get("climbable"), "climbable", false);
        opaque = def.get("opaque").isnil() ? null : Boolean.valueOf(bool(def.get("opaque"), "opaque", true));
        slipperiness = def.get("slipperiness").isnil() ? .6F : (float) number(def.get("slipperiness"), "slipperiness");
        if (slipperiness < 0 || slipperiness > 1) {
            throw error("slipperiness", "expected 0..1");
        }
        int reaction = 0;
        if (!def.get("piston").isnil()) {
            fields(def.get("piston"), "piston", "reaction");
            String r = string(def.get("piston").get("reaction"), "piston.reaction");
            reaction = r.equals("move") ? 0 : r.equals("destroy") ? 1 : r.equals("block") ? 2 : -1;
            if (reaction < 0) {
                throw error("piston.reaction", "expected move, destroy, or block");
            }
            if (!def.get("tileEntity").isnil() && reaction != 2) {
                throw error("piston.reaction", "tile entities cannot move or be destroyed by pistons");
            }
        } else if (!def.get("tileEntity").isnil()) {
            reaction = 2;
        }
        mobility = reaction;
        List<BlockBox> parsed = null;
        if (!def.get("collision").isnil()) {
            fields(def.get("collision"), "collision", "boxes");
            LuaValue list = def.get("collision").get("boxes");
            int count = length(list, "collision.boxes");
            if (count > 16) {
                throw error("collision.boxes", "maximum 16 boxes");
            }
            parsed = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                parsed.add(BlockBox.fromCoordinates(box(list.get(i + 1), "collision.boxes[" + (i + 1) + "]")));
            }
        }
        boxes = parsed == null ? null : Collections.unmodifiableList(parsed);
        selection = def.get("selection").isnil()
                ? null
                : BlockBox.fromCoordinates(box(def.get("selection"), "selection"));
    }

    public boolean hasFullCubeCollision() {
        if (boxes == null) {
            return true;
        }
        for (BlockBox box : boxes) {
            if (box.isFullCube()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFullCube(double[] box) {
        return box[0] == 0 && box[1] == 0 && box[2] == 0 && box[3] == 1 && box[4] == 1 && box[5] == 1;
    }

    public static AxisAlignedBB bounds(double[] box, int x, int y, int z) {
        return AxisAlignedBB.getBoundingBoxFromPool(x + box[0], y + box[1], z + box[2], x + box[3], y + box[4],
                z + box[5]);
    }

    public static double[] box(LuaValue value, String path) {
        fields(value, path, "min", "max");
        double[] out = new double[6];
        for (int side = 0; side < 2; side++) {
            LuaValue vector = value.get(side == 0 ? "min" : "max");
            if (length(vector, path) != 3) {
                throw error(path, "expected three coordinates");
            }
            for (int i = 0; i < 3; i++) {
                double n = number(vector.get(i + 1), path);
                if (n < 0 || n > 1) {
                    throw error(path, "coordinates must be 0..1");
                }
                out[side * 3 + i] = n;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (out[i] >= out[i + 3]) {
                throw error(path, "min must be less than max");
            }
        }
        return out;
    }
}
