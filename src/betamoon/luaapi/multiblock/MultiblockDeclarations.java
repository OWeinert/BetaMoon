package betamoon.luaapi.multiblock;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.data.DataSchema;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.block.BlockStateSchema;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.multiblock.MultiblockDefinition;
import betamoon.multiblock.MultiblockDefinition.Cell;
import betamoon.multiblock.MultiblockDefinition.Constraint;
import betamoon.multiblock.MultiblockDefinition.Variant;
import betamoon.multiblock.MultiblockLimits;
import betamoon.multiblock.MultiblockOrientation;
import betamoon.multiblock.MultiblockRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.keys;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * Strict frontend; every executable cell rule is compiled before publication.
 */
public final class MultiblockDeclarations {
    private MultiblockDeclarations() {
    }

    public static MultiblockDefinition parse(LuaValue value) {
        fields(value, "multiblock", "key", "controller", "facing", "mirror", "exclusive", "layers", "cells", "palette",
                "constraints", "repeatLayers", "captures", "data", "onFormed", "onInvalidated", "onSuspended",
                "onResumed", "discovery", "activation", "canActivate", "onActivated", "onDeactivated");
        ContentKey key = ContentKey.parseForType(string(value.get("key"), "key"), ContentType.of("multiblock"));
        int controller = value.get("controller").isnil() ? -1 : block(value.get("controller"), "controller");
        Integer facing = value.get("facing").isnil() ? null : direction(value.get("facing"), "facing");
        if (controller >= 0 && facing == null) {
            BlockDefinition block = BlockCallbackRegistry.get(controller);
            if (block == null || !block.state.has("facing")) {
                throw error("facing", "a non-directional controller requires an explicit fixed facing");
            }
            block.state.requireEnumValues("facing", "north", "east", "south", "west");
        }
        Map<String, MultiblockRule> palette = new LinkedHashMap<String, MultiblockRule>();
        LuaValue declared = value.get("palette");
        if (!declared.isnil()) {
            for (String symbol : keys(declared, "palette")) {
                symbol(symbol, "palette");
                if (".?".contains(symbol) || (controller >= 0 && "@".equals(symbol))) {
                    throw error("palette." + symbol, "reserved symbol");
                }
                palette.put(symbol, rule(declared.get(symbol), "palette." + symbol));
            }
        }
        palette.put(".", identity(0, -1));
        if (controller >= 0) {
            palette.put("@", identity(controller, -1));
        }
        if (!palette.containsKey("@")) {
            throw error("palette.@", "standalone patterns require an anchor rule");
        }
        Set<String> symbols = new LinkedHashSet<String>();
        List<Variant> variants = value.get("cells").isnil()
                ? layers(value, palette, symbols)
                : sparse(value, palette, symbols);
        List<Constraint> constraints = constraints(value.get("constraints"), symbols);
        Map<String, String> captures = new LinkedHashMap<String, String>();
        if (!value.get("captures").isnil()) {
            List<String> names = keys(value.get("captures"), "captures");
            if (names.size() > 64) {
                throw error("captures", "at most 64 captures");
            }
            for (String name : names) {
                if (name.isEmpty() || name.length() > 64) {
                    throw error("captures", "capture names must have 1..64 characters");
                }
                if (symbols.contains(name)) {
                    throw error("captures." + name, "capture shadows a pattern symbol");
                }
                String selected = selection(value.get("captures").get(name), symbols, "captures." + name);
                captures.put(name, selected);
            }
        }
        Map<String, LuaValue> callbacks = new LinkedHashMap<String, LuaValue>();
        for (String name : new String[]{"onFormed", "onInvalidated", "onSuspended", "onResumed", "canActivate",
                "onActivated", "onDeactivated"}) {
            LuaValue callback = value.get(name);
            if (!callback.isnil() && !callback.isfunction()) {
                throw error(name, "expected a function");
            }
            callbacks.put(name, callback);
        }
        DataSchema schema = DataSchema.parse(value.get("data"), "data");
        if (schema.containsEntityReference()) {
            throw error("data", "multiblock data cannot contain live entity references");
        }
        return new MultiblockDefinition(key, LuaScriptRegistry.getCurrentScriptFile(), controller, facing,
                bool(value.get("mirror"), "mirror", false), bool(value.get("exclusive"), "exclusive", false), variants,
                captures, constraints, callbacks, symbols, schema, MultiblockManagementDeclarations.parse(value,
                        controller, facing, variants, palette, symbols, captures));
    }

    private static List<Variant> layers(LuaValue value, Map<String, MultiblockRule> palette, Set<String> symbols) {
        LuaValue layers = value.get("layers");
        int height = length(layers, "layers");
        dimension(height, "layers");
        List<List<String>> source = new ArrayList<List<String>>();
        int width = -1;
        int depth = -1;
        int anchorX = -1;
        int anchorY = -1;
        int anchorZ = -1;
        for (int y = 0; y < height; y++) {
            LuaValue layer = layers.get(y + 1);
            int rows = length(layer, "layers[" + (y + 1) + "]");
            dimension(rows, "layer depth");
            if (depth >= 0 && depth != rows) {
                throw error("layers[" + (y + 1) + "]", "layers must have equal depths");
            }
            depth = rows;
            List<String> strings = new ArrayList<String>();
            for (int z = 0; z < rows; z++) {
                String row = string(layer.get(z + 1), "layer " + (y + 1) + " row " + (z + 1));
                dimension(row.length(), "row width");
                if (width >= 0 && width != row.length()) {
                    throw error("layer " + (y + 1) + " row " + (z + 1), "ragged row");
                }
                width = row.length();
                for (int x = 0; x < width; x++) {
                    String symbol = row.substring(x, x + 1);
                    String path = "layer " + (y + 1) + " row " + (z + 1) + " column " + (x + 1);
                    validateSymbol(symbol, palette, symbols, path);
                    if ("@".equals(symbol)) {
                        if (anchorY >= 0) {
                            throw error(path, "duplicate anchor");
                        }
                        anchorX = x;
                        anchorY = y;
                        anchorZ = z;
                    }
                }
                strings.add(row);
            }
            source.add(strings);
        }
        if (anchorY < 0) {
            throw error("layers", "exactly one @ anchor is required");
        }
        int first = -1;
        int last = -1;
        int min = 1;
        int max = 1;
        LuaValue repeat = value.get("repeatLayers");
        if (!repeat.isnil()) {
            fields(repeat, "repeatLayers", "first", "last", "min", "max");
            first = integer(repeat.get("first"), "repeatLayers.first", 1, height) - 1;
            last = integer(repeat.get("last"), "repeatLayers.last", first + 1, height) - 1;
            min = integer(repeat.get("min"), "repeatLayers.min", 0, 32);
            max = integer(repeat.get("max"), "repeatLayers.max", min, 32);
            if (anchorY >= first && anchorY <= last) {
                throw error("repeatLayers", "anchor must be outside the repeated section");
            }
        }
        List<Variant> variants = new ArrayList<Variant>();
        for (int count = max; count >= min; count--) {
            List<Integer> expanded = new ArrayList<Integer>();
            for (int y = 0; y < height; y++) {
                if (y == first) {
                    for (int n = 0; n < count; n++) {
                        for (int r = first; r <= last; r++) {
                            expanded.add(r);
                        }
                    }
                    y = last;
                } else {
                    expanded.add(y);
                }
            }
            dimension(expanded.size(), "expanded height");
            int expandedAnchor = expanded.indexOf(anchorY);
            List<Cell> cells = new ArrayList<Cell>();
            for (int y = 0; y < expanded.size(); y++) {
                List<String> layer = source.get(expanded.get(y));
                for (int z = 0; z < depth; z++) {
                    for (int x = 0; x < width; x++) {
                        String symbol = layer.get(z).substring(x, x + 1);
                        if (!"?".equals(symbol)) {
                            cells.add(new Cell(x - anchorX, y - expandedAnchor, z - anchorZ, y + 1, z + 1, x + 1,
                                    symbol, palette.get(symbol)));
                        }
                    }
                }
            }
            limit(cells.size());
            variants.add(new Variant(cells, count, width, expanded.size(), depth));
        }
        return variants;
    }

    private static List<Variant> sparse(LuaValue value, Map<String, MultiblockRule> palette, Set<String> symbols) {
        if (!value.get("layers").isnil() || !value.get("repeatLayers").isnil()) {
            throw error("cells", "sparse cells cannot be combined with layers or repeatLayers");
        }
        LuaValue input = value.get("cells");
        int size = length(input, "cells");
        limit(size);
        List<Cell> cells = new ArrayList<Cell>();
        Set<String> positions = new LinkedHashSet<String>();
        int anchors = 0;
        int[] min = {0, 0, 0};
        int[] max = {0, 0, 0};
        for (int i = 1; i <= size; i++) {
            LuaValue cell = input.get(i);
            String path = "cells[" + i + "]";
            fields(cell, path, "x", "y", "z", "symbol");
            int x = integer(cell.get("x"), path + ".x", -31, 31);
            int y = integer(cell.get("y"), path + ".y", -31, 31);
            int z = integer(cell.get("z"), path + ".z", -31, 31);
            String symbol = string(cell.get("symbol"), path + ".symbol");
            validateSymbol(symbol, palette, symbols, path);
            if (!positions.add(x + ":" + y + ":" + z)) {
                throw error(path, "duplicate sparse position");
            }
            if ("@".equals(symbol)) {
                anchors++;
                if (x != 0 || y != 0 || z != 0) {
                    throw error(path, "sparse anchor must be at offset 0,0,0");
                }
            }
            int[] coords = {x, y, z};
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], coords[axis]);
                max[axis] = Math.max(max[axis], coords[axis]);
            }
            if (!"?".equals(symbol)) {
                cells.add(new Cell(x, y, z, y, z, x, symbol, palette.get(symbol)));
            }
        }
        if (anchors != 1) {
            throw error("cells", "exactly one @ anchor is required");
        }
        for (int axis = 0; axis < 3; axis++) {
            dimension(max[axis] - min[axis] + 1, "sparse bounds");
        }
        return Collections
                .singletonList(new Variant(cells, 1, max[0] - min[0] + 1, max[1] - min[1] + 1, max[2] - min[2] + 1));
    }

    private static List<Constraint> constraints(LuaValue value, Set<String> symbols) {
        List<Constraint> result = new ArrayList<Constraint>();
        if (value.isnil()) {
            return result;
        }
        int size = length(value, "constraints");
        if (size > 64) {
            throw error("constraints", "at most 64 constraints");
        }
        for (int i = 1; i <= size; i++) {
            LuaValue input = value.get(i);
            String path = "constraints[" + i + "]";
            fields(input, path, "cells", "rule", "min", "max", "label", "allSame");
            String selected = selection(input.get("cells"), symbols, path + ".cells");
            String same = input.get("allSame").isnil() ? null : string(input.get("allSame"), path + ".allSame");
            if (same != null && !(same.equals("block") || same.equals("metadata") || same.startsWith("state:"))) {
                throw error(path + ".allSame", "expected block, metadata or state:<field>");
            }
            if (same != null
                    && (!input.get("rule").isnil() || !input.get("min").isnil() || !input.get("max").isnil())) {
                throw error(path, "allSame cannot be combined with rule/min/max");
            }
            int min = input.get("min").isnil() ? 0 : integer(input.get("min"), path + ".min", 0, 32768);
            int max = input.get("max").isnil() ? 32768 : integer(input.get("max"), path + ".max", min, 32768);
            result.add(new Constraint(selected,
                    input.get("label").isnil() ? selected : string(input.get("label"), path + ".label"),
                    same == null ? rule(input.get("rule"), path + ".rule") : null, min, max, same));
        }
        return result;
    }

    private static String selection(LuaValue value, Set<String> symbols, String path) {
        String selected = string(value, path);
        if (selected.isEmpty() || selected.length() > 94) {
            throw error(path, "expected 1..94 symbols");
        }
        for (int i = 0; i < selected.length(); i++) {
            if (!symbols.contains(selected.substring(i, i + 1)) || selected.charAt(i) == '?') {
                throw error(path, "unknown or ignored symbol " + selected.charAt(i));
            }
        }
        return selected;
    }

    public static MultiblockRule rule(LuaValue value, String path) {
        return rule(value, path, new IdentityHashMap<LuaValue, Boolean>(), new int[1], 0);
    }

    private static MultiblockRule rule(LuaValue value, String path, IdentityHashMap<LuaValue, Boolean> stack,
            int[] nodes, int depth) {
        if (++nodes[0] > 256 || depth > 16 || stack.containsKey(value)) {
            throw error(path, "cyclic rule, nesting exceeds 16, or rule exceeds 256 nodes");
        }
        if (value.istable() && !value.get("id").isnil() && !value.get("exists").isnil()) {
            return identity(block(value, path), -1);
        }
        fields(value, path, "block", "damage", "state", "air", "anyOf", "allOf", "notRule");
        int forms = 0;
        for (String name : new String[]{"block", "air", "anyOf", "allOf", "notRule"}) {
            if (!value.get(name).isnil()) {
                forms++;
            }
        }
        if (forms != 1) {
            throw error(path, "expected exactly one of block, air, anyOf, allOf, notRule");
        }
        if (value.get("block").isnil() && (!value.get("damage").isnil() || !value.get("state").isnil())) {
            throw error(path, "damage/state require block");
        }
        stack.put(value, Boolean.TRUE);
        try {
            if (!value.get("block").isnil()) {
                int id = block(value.get("block"), path + ".block");
                int damage = value.get("damage").isnil() ? -1 : integer(value.get("damage"), path + ".damage", 0, 15);
                LuaValue state = value.get("state");
                if (state.isnil()) {
                    return identity(id, damage);
                }
                BlockDefinition definition = BlockCallbackRegistry.get(id);
                if (definition == null) {
                    throw error(path + ".state", "block has no declared semantic state");
                }
                BlockStateSchema schema = definition.state;
                List<String> names = keys(state, path + ".state");
                final boolean[][] accepted = new boolean[8][16];
                for (String name : names) {
                    schema.set(schema.defaults, name, state.get(name));
                }
                for (int orientation = 0; orientation < 8; orientation++) {
                    for (int metadata = 0; metadata < 16; metadata++) {
                        boolean match = damage < 0 || metadata == damage;
                        for (String name : names) {
                            LuaValue expected = state.get(name);
                            if (name.equals("facing") && expected.isstring()) {
                                expected = LuaValue.valueOf(MultiblockOrientation.direction(expected.tojstring(),
                                        orientation & 3, orientation >= 4));
                            }
                            match &= schema.get(metadata, name).raweq(expected);
                        }
                        accepted[orientation][metadata] = match;
                    }
                }
                return new MultiblockRule() {
                    public boolean matches(int actual, int metadata, int facing, boolean mirror) {
                        return actual == id && accepted[facing + (mirror ? 4 : 0)][metadata];
                    }

                    public String signature(int facing, boolean mirror) {
                        return id + ":" + Arrays.toString(accepted[facing + (mirror ? 4 : 0)]);
                    }

                    public Set<Integer> candidateBlocks() {
                        return Collections.singleton(id);
                    }

                    public String describe() {
                        return "block " + id + " declared state " + names;
                    }
                };
            }
            if (!value.get("air").isnil()) {
                if (!bool(value.get("air"), path + ".air", false)) {
                    throw error(path + ".air", "must be true");
                }
                return identity(0, -1);
            }
            final boolean negate = !value.get("notRule").isnil();
            final boolean all = !value.get("allOf").isnil();
            List<MultiblockRule> children = new ArrayList<MultiblockRule>();
            if (negate) {
                children.add(rule(value.get("notRule"), path + ".notRule", stack, nodes, depth + 1));
            } else {
                LuaValue list = value.get(all ? "allOf" : "anyOf");
                int size = length(list, path);
                if (size < 1 || size > 32) {
                    throw error(path, "expected 1..32 child rules");
                }
                for (int i = 1; i <= size; i++) {
                    children.add(rule(list.get(i), path + "[" + i + "]", stack, nodes, depth + 1));
                }
            }
            return new MultiblockRule() {
                public boolean matches(int id, int metadata, int facing, boolean mirror) {
                    if (negate) {
                        return !children.get(0).matches(id, metadata, facing, mirror);
                    }
                    for (MultiblockRule child : children) {
                        if (child.matches(id, metadata, facing, mirror) != all) {
                            return !all;
                        }
                    }
                    return all;
                }

                public Set<Integer> candidateBlocks() {
                    if (negate) {
                        return null;
                    }
                    Set<Integer> result = new LinkedHashSet<>();
                    for (MultiblockRule child : children) {
                        Set<Integer> candidates = child.candidateBlocks();
                        if (all && candidates != null) {
                            return candidates;
                        }
                        if (!all && candidates == null) {
                            return null;
                        }
                        if (candidates != null) {
                            result.addAll(candidates);
                        }
                    }
                    return all ? null : result;
                }

                public String signature(int facing, boolean mirror) {
                    List<String> parts = new ArrayList<>();
                    for (MultiblockRule child : children) {
                        parts.add(child.signature(facing, mirror));
                    }
                    Collections.sort(parts);
                    return (negate ? "not" : all ? "all" : "any") + parts;
                }

                public String describe() {
                    StringBuilder text = new StringBuilder(negate ? "not(" : all ? "all(" : "any(");
                    for (MultiblockRule child : children) {
                        text.append(child.describe()).append(';');
                    }
                    return text.append(')').toString();
                }
            };
        } finally {
            stack.remove(value);
        }
    }

    private static MultiblockRule identity(final int id, final int damage) {
        return new MultiblockRule() {
            public boolean matches(int actual, int metadata, int facing, boolean mirror) {
                return id == actual && (damage < 0 || damage == metadata);
            }

            public Set<Integer> candidateBlocks() {
                return Collections.singleton(id);
            }

            public String describe() {
                return id == 0 ? "air" : "block " + id + (damage < 0 ? "" : " metadata " + damage);
            }
        };
    }

    private static int block(LuaValue value, String path) {
        try {
            Block block = ResourceApi.requireBlockReference(value);
            if (Block.blocksList[block.blockID] != block) {
                throw error(path, "block reference is no longer registered");
            }
            return block.blockID;
        } catch (LuaError invalid) {
            throw error(path, "expected a resolved block reference: " + invalid.getMessage());
        }
    }

    public static int direction(LuaValue value, String path) {
        try {
            return MultiblockOrientation.facing(string(value, path));
        } catch (IllegalArgumentException invalid) {
            throw error(path, invalid.getMessage());
        }
    }

    private static void symbol(String symbol, String path) {
        if (symbol.length() != 1 || symbol.charAt(0) < 33 || symbol.charAt(0) > 126) {
            throw error(path, "expected one printable ASCII symbol (spaces are not wildcards)");
        }
    }

    private static void validateSymbol(String symbol, Map<String, MultiblockRule> palette, Set<String> symbols,
            String path) {
        symbol(symbol, path);
        if (!"?".equals(symbol) && !palette.containsKey(symbol)) {
            throw error(path, "undefined symbol " + symbol);
        }
        symbols.add(symbol);
    }

    private static void dimension(int size, String path) {
        if (size < 1 || size > 32) {
            throw error(path, "dimension must be 1..32");
        }
    }

    private static void limit(int size) {
        if (size < 1 || size > MultiblockLimits.requiredCells()) {
            throw error("pattern", "required cells exceed configured cap " + MultiblockLimits.requiredCells());
        }
    }
}
