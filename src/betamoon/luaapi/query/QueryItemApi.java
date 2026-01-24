package betamoon.luaapi.query;

import betamoon.luaapi.LuaApiUtils;
import betamoon.query.ContentQuery;
import betamoon.query.ContentQueryItem;
import betamoon.query.QueryEntry;
import betamoon.query.QueryExecutionResult;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class QueryItemApi {
    private QueryItemApi() {
    }

    static LuaValue createHandle() {
        return new ItemQueryHandle(new ContentQueryItem());
    }

    private static final class ItemQueryHandle extends LuaTable {
        private final ContentQueryItem query;

        private ItemQueryHandle(ContentQueryItem query) {
            this.query = query;
            set("getById", new GetItemById(this));
            set("filterByName", new FilterItemByName(this));
            set("filterByDisplayName", new FilterItemByDisplayName(this));
            set("filterDamage", new FilterItemByDamage(this));
            set("getByDamage", new GetItemByDamage(this));
            set("fromHandle", new GetItemFromHandle(this));
            set("first", new FirstItem(this));
            set("last", new LastItem(this));
            set("get", new GetItemAt(this));
            set("finishQuery", new FinishItemQuery(this));
        }
    }

    private static final class GetItemById extends VarArgFunction {
        private final ItemQueryHandle handle;

        private GetItemById(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int id = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.query.getById(id);
            return handle;
        }
    }

    private static final class FilterItemByName extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FilterItemByName(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            handle.query.filterByName(name);
            return handle;
        }
    }

    private static final class FilterItemByDisplayName extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FilterItemByDisplayName(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            handle.query.filterByDisplayName(name);
            return handle;
        }
    }

    private static final class FilterItemByDamage extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FilterItemByDamage(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int min = (int) LuaApiUtils.getNumberArg(args, 1);
            int max = (int) LuaApiUtils.getNumberArg(args, 2);
            handle.query.filterDamage(min, max);
            return handle;
        }
    }

    private static final class GetItemByDamage extends VarArgFunction {
        private final ItemQueryHandle handle;

        private GetItemByDamage(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int damage = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.query.getByDamage(damage);
            return handle;
        }
    }

    private static final class GetItemFromHandle extends VarArgFunction {
        private final ItemQueryHandle handle;

        private GetItemFromHandle(ItemQueryHandle handle) {
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

    private static final class FirstItem extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FirstItem(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.first();
            return handle;
        }
    }

    private static final class LastItem extends VarArgFunction {
        private final ItemQueryHandle handle;

        private LastItem(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.last();
            return handle;
        }
    }

    private static final class GetItemAt extends VarArgFunction {
        private final ItemQueryHandle handle;

        private GetItemAt(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.query.get(index);
            return handle;
        }
    }

    private static final class FinishItemQuery extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FinishItemQuery(ItemQueryHandle handle) {
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
                    return QueryApiUtils.pushNil("Query: expected exactly one item, found " + entries.size());
                }
                QueryEntry entry = (QueryEntry) entries.get(0);
                return new QueryItemHandle(entry.id, entry.damage);
            }
            return new ItemQueryResultHandle(entries);
        }
    }

    private static final class ItemQueryResultHandle extends LuaTable {
        private final List entries;

        private ItemQueryResultHandle(List entries) {
            this.entries = new ArrayList(entries);
            set("first", new FirstItemResult(this));
            set("last", new LastItemResult(this));
            set("get", new GetItemResultAt(this));
            set("count", new CountItemResults(this));
            set("ensureOne", new EnsureOneItemResult(this));
            set("intoHandle", new IntoItemHandle(this));
            set("intoHandles", new IntoItemHandles(this));
        }
    }

    private static final class FirstItemResult extends VarArgFunction {
        private final ItemQueryResultHandle handle;

        private FirstItemResult(ItemQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return LuaValue.NIL;
            }
            QueryEntry entry = (QueryEntry) handle.entries.get(0);
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class LastItemResult extends VarArgFunction {
        private final ItemQueryResultHandle handle;

        private LastItemResult(ItemQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return LuaValue.NIL;
            }
            QueryEntry entry = (QueryEntry) handle.entries.get(handle.entries.size() - 1);
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class GetItemResultAt extends VarArgFunction {
        private final ItemQueryResultHandle handle;

        private GetItemResultAt(ItemQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            if (index <= 255) {
                return QueryApiUtils.pushNil("Query: item index must be > 255: " + index);
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
                return QueryApiUtils.pushNil("Query: item id not found in query: " + index);
            }
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class CountItemResults extends VarArgFunction {
        private final ItemQueryResultHandle handle;

        private CountItemResults(ItemQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.entries.size());
        }
    }

    private static final class EnsureOneItemResult extends VarArgFunction {
        private final ItemQueryResultHandle handle;

        private EnsureOneItemResult(ItemQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.size() != 1) {
                return QueryApiUtils.pushNil("Query: expected exactly one item, found " + handle.entries.size());
            }
            return handle;
        }
    }

    private static final class IntoItemHandle extends VarArgFunction {
        private final ItemQueryResultHandle handle;

        private IntoItemHandle(ItemQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return QueryApiUtils.pushNil("Query: no items found in query.");
            }
            QueryEntry entry = (QueryEntry) handle.entries.get(0);
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class IntoItemHandles extends VarArgFunction {
        private final ItemQueryResultHandle handle;

        private IntoItemHandles(ItemQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return QueryApiUtils.pushNil("Query: no items found in query.");
            }
            LuaTable out = new LuaTable();
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryEntry entry = (QueryEntry) handle.entries.get(i);
                out.set(i + 1, new QueryItemHandle(entry.id, entry.damage));
            }
            return out;
        }
    }

    private static final class QueryItemHandle extends LuaTable {
        private final int id;
        private final int damage;

        private QueryItemHandle(int id, int damage) {
            this.id = id;
            this.damage = damage;
            set("getId", new GetQueryId(this));
            set("getDamage", new GetQueryDamage(this));
        }
    }

    private static final class GetQueryId extends VarArgFunction {
        private final QueryItemHandle handle;

        private GetQueryId(QueryItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.id);
        }
    }

    private static final class GetQueryDamage extends VarArgFunction {
        private final QueryItemHandle handle;

        private GetQueryDamage(QueryItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.damage);
        }
    }

    
}
