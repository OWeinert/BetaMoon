package betamoon.luaapi.query;

import betamoon.luaapi.LuaApiUtils;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class QueryBlockApi {
    private QueryBlockApi() {
    }

    static LuaValue createHandle() {
        return new BlockQueryHandle();
    }

    private static final class BlockQueryHandle extends LuaTable {
        private List entries;

        private BlockQueryHandle() {
            entries = QueryCommon.buildBlockEntries();
            set("getById", new GetBlockById(this));
            set("filterByName", new FilterBlockByName(this));
            set("filterByDisplayName", new FilterBlockByDisplayName(this));
            set("filterDamage", new FilterBlockByDamage(this));
            set("getByDamage", new GetBlockByDamage(this));
            set("fromHandle", new GetBlockFromHandle(this));
            set("first", new FirstBlock(this));
            set("last", new LastBlock(this));
            set("get", new GetBlockAt(this));
            set("count", new CountBlocks(this));
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
            if (id < 0 || id > 255) {
                throw new LuaError("Query: block id outside allowed range (0-255): " + id);
            }
            QueryCommon.QueryEntry entry = QueryCommon.findById(handle.entries, id, 0);
            if (entry == null) {
                throw new LuaError("Query: block id not registered: " + id);
            }
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class FilterBlockByName extends VarArgFunction {
        private final BlockQueryHandle handle;

        private FilterBlockByName(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            List filtered = new ArrayList();
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(i);
                if (name.equals(entry.internalName)) {
                    filtered.add(entry);
                }
            }
            handle.entries = filtered;
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
            List filtered = new ArrayList();
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(i);
                if (name.equals(entry.displayName)) {
                    filtered.add(entry);
                }
            }
            handle.entries = filtered;
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
            List filtered = new ArrayList();
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(i);
                if (entry.damage >= min && entry.damage <= max) {
                    filtered.add(entry);
                }
            }
            handle.entries = filtered;
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
            QueryCommon.QueryEntry match = null;
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(i);
                if (entry.damage == damage) {
                    if (match != null) {
                        throw new LuaError("Query: multiple blocks match damage value: " + damage);
                    }
                    match = entry;
                }
            }
            if (match == null) {
                throw new LuaError("Query: no block found with damage value: " + damage);
            }
            return new QueryBlockHandle(match.id, match.damage);
        }
    }

    private static final class GetBlockFromHandle extends VarArgFunction {
        private final BlockQueryHandle handle;

        private GetBlockFromHandle(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            LuaValue value = LuaApiUtils.getVarArg(args, 1);
            int id = LuaApiUtils.resolveItemId(value);
            int damage = QueryCommon.readDamageFromHandle(value);
            QueryCommon.QueryEntry entry = QueryCommon.findById(handle.entries, id, damage);
            if (entry == null) {
                throw new LuaError("Query: block handle not found in registry.");
            }
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class FirstBlock extends VarArgFunction {
        private final BlockQueryHandle handle;

        private FirstBlock(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return LuaValue.NIL;
            }
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(0);
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class LastBlock extends VarArgFunction {
        private final BlockQueryHandle handle;

        private LastBlock(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return LuaValue.NIL;
            }
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(handle.entries.size() - 1);
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class GetBlockAt extends VarArgFunction {
        private final BlockQueryHandle handle;

        private GetBlockAt(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            if (index < 0 || index > 255) {
                throw new LuaError("Query: block index out of bounds (0-255): " + index);
            }
            QueryCommon.QueryEntry entry = QueryCommon.findFirstById(handle.entries, index);
            if (entry == null) {
                throw new LuaError("Query: block id not found in query: " + index);
            }
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class CountBlocks extends VarArgFunction {
        private final BlockQueryHandle handle;

        private CountBlocks(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.entries.size());
        }
    }

    private static final class FinishBlockQuery extends VarArgFunction {
        private final BlockQueryHandle handle;

        private FinishBlockQuery(BlockQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return new BlockQueryResultHandle(handle.entries);
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
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(0);
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
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(handle.entries.size() - 1);
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
                throw new LuaError("Query: block index out of bounds (0-255): " + index);
            }
            QueryCommon.QueryEntry entry = QueryCommon.findFirstById(handle.entries, index);
            if (entry == null) {
                throw new LuaError("Query: block id not found in query: " + index);
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
                throw new LuaError("Query: expected exactly one block, found " + handle.entries.size());
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
            if (handle.entries.size() != 1) {
                throw new LuaError("Query: expected exactly one block, found " + handle.entries.size());
            }
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(0);
            return new QueryBlockHandle(entry.id, entry.damage);
        }
    }

    private static final class IntoBlockHandles extends VarArgFunction {
        private final BlockQueryResultHandle handle;

        private IntoBlockHandles(BlockQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            LuaTable out = new LuaTable();
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(i);
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
