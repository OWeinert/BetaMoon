package betamoon.multiblock;

import betamoon.content.ContentKey;
import betamoon.data.DataSchema;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.block.BlockStateSchema;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaValue;

/** Compiled declarations contain no live world or controller references. */
public final class MultiblockDefinition {
    public final ContentKey key;
    public final String owner;
    public final int controller;
    public final MultiblockManagement management;
    public final Integer fixedFacing;
    public final boolean mirror;
    public final boolean exclusive;
    public final List<Variant> variants;
    public final Map<String, String> captures;
    public final List<Constraint> constraints;
    public final Map<String, LuaValue> callbacks;
    public final Set<String> symbols;
    public final DataSchema dataSchema;
    public final BlockStateSchema controllerState;

    public MultiblockDefinition(ContentKey key, String owner, int controller, Integer fixedFacing, boolean mirror,
            boolean exclusive, List<Variant> variants, Map<String, String> captures, List<Constraint> constraints,
            Map<String, LuaValue> callbacks, Set<String> symbols, DataSchema dataSchema,
            MultiblockManagement management) {
        this.management = management;
        this.key = key;
        this.owner = owner;
        this.controller = controller;
        BlockDefinition block = BlockCallbackRegistry.get(controller);
        this.controllerState = block == null ? null : block.state;
        this.fixedFacing = fixedFacing;
        this.mirror = mirror;
        this.exclusive = exclusive;
        this.variants = Collections.unmodifiableList(new ArrayList<Variant>(variants));
        this.captures = Collections.unmodifiableMap(new LinkedHashMap<String, String>(captures));
        this.constraints = Collections.unmodifiableList(new ArrayList<Constraint>(constraints));
        this.callbacks = Collections.unmodifiableMap(new LinkedHashMap<String, LuaValue>(callbacks));
        this.symbols = Collections.unmodifiableSet(new LinkedHashSet<String>(symbols));
        this.dataSchema = dataSchema;
    }

    public static final class Cell {
        public final int x;
        public final int y;
        public final int z;
        public final int layer;
        public final int row;
        public final int column;
        public final String symbol;
        public final MultiblockRule rule;

        public Cell(int x, int y, int z, int layer, int row, int column, String symbol, MultiblockRule rule) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.layer = layer;
            this.row = row;
            this.column = column;
            this.symbol = symbol;
            this.rule = rule;
        }
    }

    public static final class Variant {
        public final List<Cell> cells;
        public final int repeats;
        public final int width;
        public final int height;
        public final int depth;

        public Variant(List<Cell> cells, int repeats, int width, int height, int depth) {
            this.cells = Collections.unmodifiableList(new ArrayList<Cell>(cells));
            this.repeats = repeats;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    public static final class Constraint {
        public final String cells;
        public final String label;
        public final MultiblockRule rule;
        public final int min;
        public final int max;
        public final String allSame;
        public final Map<Integer, BlockStateSchema> states;

        public Constraint(String cells, String label, MultiblockRule rule, int min, int max, String allSame) {
            this.cells = cells;
            this.label = label;
            this.rule = rule;
            this.min = min;
            this.max = max;
            this.allSame = allSame;
            Map<Integer, BlockStateSchema> compiled = new LinkedHashMap<>();
            if (allSame != null && allSame.startsWith("state:")) {
                String field = allSame.substring("state:".length());
                for (int id = 0; id < Block.blocksList.length; id++) {
                    BlockDefinition block = BlockCallbackRegistry.get(id);
                    if (block != null && block.state.has(field)) {
                        compiled.put(id, block.state);
                    }
                }
                if (compiled.isEmpty()) {
                    throw new IllegalArgumentException("allSame has no declared block state field: " + field);
                }
            }
            states = Collections.unmodifiableMap(compiled);
        }
    }
}
