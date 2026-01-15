package betamoon.luaapi;

import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class QueryItemApi {
    private QueryItemApi() {
    }

    static LuaValue createHandle() {
        return new ItemQueryHandle();
    }

    private static final class ItemQueryHandle extends LuaTable {
        private List entries;

        private ItemQueryHandle() {
            entries = QueryCommon.buildItemEntries();
            set("getById", new GetItemById(this));
            set("filterByName", new FilterItemByName(this));
            set("filterByDisplayName", new FilterItemByDisplayName(this));
            set("filterDamage", new FilterItemByDamage(this));
            set("getByDamage", new GetItemByDamage(this));
            set("fromHandle", new GetItemFromHandle(this));
            set("first", new FirstItem(this));
            set("last", new LastItem(this));
            set("get", new GetItemAt(this));
            set("count", new CountItems(this));
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
            if (id < 256) {
                throw new LuaError("Query: item id must be >= 256: " + id);
            }
            QueryCommon.QueryEntry entry = QueryCommon.findById(handle.entries, id, 0);
            if (entry == null) {
                throw new LuaError("Query: item id not registered: " + id);
            }
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class FilterItemByName extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FilterItemByName(ItemQueryHandle handle) {
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

    private static final class FilterItemByDisplayName extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FilterItemByDisplayName(ItemQueryHandle handle) {
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

    private static final class FilterItemByDamage extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FilterItemByDamage(ItemQueryHandle handle) {
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

    private static final class GetItemByDamage extends VarArgFunction {
        private final ItemQueryHandle handle;

        private GetItemByDamage(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int damage = (int) LuaApiUtils.getNumberArg(args, 1);
            QueryCommon.QueryEntry match = null;
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(i);
                if (entry.damage == damage) {
                    if (match != null) {
                        throw new LuaError("Query: multiple items match damage value: " + damage);
                    }
                    match = entry;
                }
            }
            if (match == null) {
                throw new LuaError("Query: no item found with damage value: " + damage);
            }
            return new QueryItemHandle(match.id, match.damage);
        }
    }

    private static final class GetItemFromHandle extends VarArgFunction {
        private final ItemQueryHandle handle;

        private GetItemFromHandle(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            LuaValue value = LuaApiUtils.getVarArg(args, 1);
            int id = LuaApiUtils.resolveItemId(value);
            int damage = QueryCommon.readDamageFromHandle(value);
            QueryCommon.QueryEntry entry = QueryCommon.findById(handle.entries, id, damage);
            if (entry == null) {
                throw new LuaError("Query: item handle not found in registry.");
            }
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class FirstItem extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FirstItem(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return LuaValue.NIL;
            }
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(0);
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class LastItem extends VarArgFunction {
        private final ItemQueryHandle handle;

        private LastItem(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.entries.isEmpty()) {
                return LuaValue.NIL;
            }
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(handle.entries.size() - 1);
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class GetItemAt extends VarArgFunction {
        private final ItemQueryHandle handle;

        private GetItemAt(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            if (index <= 255) {
                throw new LuaError("Query: item index must be > 255: " + index);
            }
            QueryCommon.QueryEntry entry = QueryCommon.findFirstById(handle.entries, index);
            if (entry == null) {
                throw new LuaError("Query: item id not found in query: " + index);
            }
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class CountItems extends VarArgFunction {
        private final ItemQueryHandle handle;

        private CountItems(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.entries.size());
        }
    }

    private static final class FinishItemQuery extends VarArgFunction {
        private final ItemQueryHandle handle;

        private FinishItemQuery(ItemQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return new ItemQueryResultHandle(handle.entries);
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
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(0);
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
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(handle.entries.size() - 1);
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
                throw new LuaError("Query: item index must be > 255: " + index);
            }
            QueryCommon.QueryEntry entry = QueryCommon.findFirstById(handle.entries, index);
            if (entry == null) {
                throw new LuaError("Query: item id not found in query: " + index);
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
                throw new LuaError("Query: expected exactly one item, found " + handle.entries.size());
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
            if (handle.entries.size() != 1) {
                throw new LuaError("Query: expected exactly one item, found " + handle.entries.size());
            }
            QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(0);
            return new QueryItemHandle(entry.id, entry.damage);
        }
    }

    private static final class IntoItemHandles extends VarArgFunction {
        private final ItemQueryResultHandle handle;

        private IntoItemHandles(ItemQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            LuaTable out = new LuaTable();
            for (int i = 0; i < handle.entries.size(); i++) {
                QueryCommon.QueryEntry entry = (QueryCommon.QueryEntry) handle.entries.get(i);
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
