package betamoon.luaapi.query;

import betamoon.luaapi.LuaApiUtils;
import betamoon.query.ContentQuery;
import betamoon.query.ContentQueryBlock;
import betamoon.query.QueryEntry;
import betamoon.query.QueryExecutionResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class QueryBlockApi {
    private QueryBlockApi() {
    }

    static LuaValue createHandle() {
        return new BlockQueryHandle(new ContentQueryBlock());
    }

    private static final class BlockQueryHandle extends LuaTable {
        private final ContentQueryBlock query;

        private BlockQueryHandle(ContentQueryBlock query) {
            this.query = query;
            set("getById", new GetBlockById(this));
            set("filterByName", new FilterBlockByName(this));
            set("filterByDisplayName", new FilterBlockByDisplayName(this));
            set("filterDamage", new FilterBlockByDamage(this));
            set("getByDamage", new GetBlockByDamage(this));
            set("fromHandle", new GetBlockFromHandle(this));
            set("first", new FirstBlock(this));
            set("last", new LastBlock(this));
            set("get", new GetBlockAt(this));
            set("finishQuery", new FinishBlockQuery(this));
        }
    }

    private static final class GetBlockById extends VarArgFunction {
        private final BlockQueryHandle handle;

        private GetBlockById(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int id = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.query.getById(id);
            return handle;
        }
    }

    private static final class FilterBlockByName extends VarArgFunction {
        private final BlockQueryHandle handle;

        private FilterBlockByName(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            handle.query.filterByName(name);
            return handle;
        }
    }

    private static final class FilterBlockByDisplayName extends VarArgFunction {
        private final BlockQueryHandle handle;

        private FilterBlockByDisplayName(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            handle.query.filterByDisplayName(name);
            return handle;
        }
    }

    private static final class FilterBlockByDamage extends VarArgFunction {
        private final BlockQueryHandle handle;

        private FilterBlockByDamage(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int min = (int) LuaApiUtils.getNumberArg(args, 1);
            int max = (int) LuaApiUtils.getNumberArg(args, 2);
            handle.query.filterDamage(min, max);
            return handle;
        }
    }

    private static final class GetBlockByDamage extends VarArgFunction {
        private final BlockQueryHandle handle;

        private GetBlockByDamage(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int damage = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.query.getByDamage(damage);
            return handle;
        }
    }

    private static final class GetBlockFromHandle extends VarArgFunction {
        private final BlockQueryHandle handle;

        private GetBlockFromHandle(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            LuaValue value = LuaApiUtils.getVarArg(args, 1);
            int id = 0;
            int damage = 0;
            boolean valid = true;
            try {
                id = LuaApiUtils.resolveItemId(value);
                damage = QueryApiUtils.readDamageFromHandle(value);
            } catch (RuntimeException e) {
                valid = false;
            }
            handle.query.fromHandle(id, damage, valid);
            return handle;
        }
    }

    private static final class FirstBlock extends VarArgFunction {
        private final BlockQueryHandle handle;

        private FirstBlock(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.first();
            return handle;
        }
    }

    private static final class LastBlock extends VarArgFunction {
        private final BlockQueryHandle handle;

        private LastBlock(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.last();
            return handle;
        }
    }

    private static final class GetBlockAt extends VarArgFunction {
        private final BlockQueryHandle handle;

        private GetBlockAt(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.query.get(index);
            return handle;
        }
    }

    private static final class FinishBlockQuery extends VarArgFunction {
        private final BlockQueryHandle handle;

        private FinishBlockQuery(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            QueryExecutionResult<List<QueryEntry>> result = handle.query.execute();
            if (result.isFailure()) {
                return QueryApiUtils.pushWarning(result.getFailure());
            }
            if (result.getWarning() != null) {
                QueryApiUtils.pushWarning(result.getWarning());
            }
            ContentQuery.ResultMode mode = result.getResultMode();
            List<QueryEntry> entries = result.getState();
            if (mode == ContentQuery.ResultMode.SINGLE) {
                if (entries.isEmpty()) {
                    return LuaValue.NIL;
                }
                if (entries.size() != 1) {
                    return QueryApiUtils.pushNil("Query: expected exactly one block, found " + entries.size());
                }
                QueryEntry entry = (QueryEntry) entries.get(0);
                return new QueryBlockHandle(entry.id, entry.damage);
            }
            return new BlockQueryResultHandle(entries);
        }
    }

    private static final class BlockQueryResultHandle extends LuaTable {
        private final List entries;

        private BlockQueryResultHandle(List entries) {
            this.entries = new ArrayList(entries);
            set("first", new FirstBlockResult(this));
            set("last", new LastBlockResult(this));
            set("get", new GetBlockResultAt(this));
            set("count", new CountBlockResults(this));
            set("ensureOne", new EnsureOneBlockResult(this));
            set("intoHandle", new IntoBlockHandle(this));
            set("intoHandles", new IntoBlockHandles(this));
        }
    }

    private static final class FirstBlockResult extends VarArgFunction {
        private final BlockQueryResultHandle handle;

        private FirstBlockResult(BlockQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return LuaValue.NIL;
            }
            QueryEntry entry = (QueryEntry) handle.entries.get(0);
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class LastBlockResult extends VarArgFunction {
        private final BlockQueryResultHandle handle;

        private LastBlockResult(BlockQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return LuaValue.NIL;
            }
            QueryEntry entry = (QueryEntry) handle.entries.get(handle.entries.size() - 1);
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class GetBlockResultAt extends VarArgFunction {
        private final BlockQueryResultHandle handle;

        private GetBlockResultAt(BlockQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            if (index < 0 || index > 255) {
                return QueryApiUtils.pushNil("Query: block index out of bounds (0-255): " + index);
            }
            QueryEntry entry = null;
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryEntry candidate = (QueryEntry) handle.entries.get(i);
                if (candidate.id == index) {
                    entry = candidate;
                    break;
                }
            }
            if (entry == null) {
                return QueryApiUtils.pushNil("Query: block id not found in query: " + index);
            }
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class CountBlockResults extends VarArgFunction {
        private final BlockQueryResultHandle handle;

        private CountBlockResults(BlockQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.entries.size());
        }
    }

    private static final class EnsureOneBlockResult extends VarArgFunction {
        private final BlockQueryResultHandle handle;

        private EnsureOneBlockResult(BlockQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.size() != 1) {
                return QueryApiUtils.pushNil("Query: expected exactly one block, found " + handle.entries.size());
            }
            return handle;
        }
    }

    private static final class IntoBlockHandle extends VarArgFunction {
        private final BlockQueryResultHandle handle;

        private IntoBlockHandle(BlockQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return QueryApiUtils.pushNil("Query: no blocks found in query.");
            }
            QueryEntry entry = (QueryEntry) handle.entries.get(0);
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class IntoBlockHandles extends VarArgFunction {
        private final BlockQueryResultHandle handle;

        private IntoBlockHandles(BlockQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return QueryApiUtils.pushNil("Query: no blocks found in query.");
            }
            LuaTable out = new LuaTable();
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryEntry entry = (QueryEntry) handle.entries.get(i);
                out.set(i + 1, new QueryBlockHandle(entry.id, entry.damage));
            }
            return out;
        }
    }

    private static final class QueryBlockHandle extends LuaTable {
        private final int id;
        private final int damage;

        private QueryBlockHandle(int id, int damage) {
            this.id = id;
            this.damage = damage;
            set("getId", new GetQueryId(this));
            set("getDamage", new GetQueryDamage(this));
            set("getName", new GetName(this));
            set("getDisplayName", new GetDisplayName(this));
        }
    }

    private static final class GetName extends VarArgFunction {
        private final QueryBlockHandle handle;

        private GetName(QueryBlockHandle handle) {
            this.handle = handle;
        }

        @Override
        public Varargs invoke(Varargs args) {
            Block block = handle.id >= 0 && handle.id < Block.blocksList.length
                ? Block.blocksList[handle.id] : null;
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
        private final QueryBlockHandle handle;

        private GetDisplayName(QueryBlockHandle handle) {
            this.handle = handle;
        }

        @Override
        public Varargs invoke(Varargs args) {
            Block block = handle.id >= 0 && handle.id < Block.blocksList.length
                ? Block.blocksList[handle.id] : null;
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

    private static final class GetQueryId extends VarArgFunction {
        private final QueryBlockHandle handle;

        private GetQueryId(QueryBlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.id);
        }
    }

    private static final class GetQueryDamage extends VarArgFunction {
        private final QueryBlockHandle handle;

        private GetQueryDamage(QueryBlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.damage);
        }
    }

    
}
