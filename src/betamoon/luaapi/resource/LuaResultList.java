package betamoon.luaapi.resource;

import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** A query result that is both a normal 1-based Lua array and a small collection API. */
public final class LuaResultList extends LuaTable {
    public interface BulkOverride {
        LuaValue apply(LuaValue reference, LuaValue definition, int index);
    }

    private final BulkOverride bulkOverride;

    public LuaResultList(List values, BulkOverride bulkOverride) {
        this.bulkOverride = bulkOverride;
        for (int i = 0; i < values.size(); i++) set(i + 1, (LuaValue) values.get(i));
        set("first", new Select(this, false));
        set("last", new Select(this, true));
        set("one", new One(this));
        set("isEmpty", new IsEmpty(this));
        if (bulkOverride != null) set("overrideAll", new OverrideAll(this));
    }

    private static final class Select extends VarArgFunction {
        private final LuaResultList list;
        private final boolean last;
        private Select(LuaResultList list, boolean last) { this.list = list; this.last = last; }
        public Varargs invoke(Varargs args) {
            int length = list.length();
            return length == 0 ? NIL : list.get(last ? length : 1);
        }
    }

    private static final class One extends VarArgFunction {
        private final LuaResultList list;
        private One(LuaResultList list) { this.list = list; }
        public Varargs invoke(Varargs args) {
            int length = list.length();
            if (length == 0) return NIL;
            if (length != 1) throw new LuaError("Expected exactly one result, found " + length + ".");
            return list.get(1);
        }
    }

    private static final class IsEmpty extends VarArgFunction {
        private final LuaResultList list;
        private IsEmpty(LuaResultList list) { this.list = list; }
        public Varargs invoke(Varargs args) { return valueOf(list.length() == 0); }
    }

    private static final class OverrideAll extends VarArgFunction {
        private final LuaResultList list;
        private OverrideAll(LuaResultList list) { this.list = list; }
        public Varargs invoke(Varargs args) {
            LuaValue definition = args.arg(args.arg1() == list ? 2 : 1);
            if (!definition.istable()) throw new LuaError("overrideAll expects an override table.");
            LuaTable handles = new LuaTable();
            for (int i = 1; i <= list.length(); i++) {
                handles.set(i, list.bulkOverride.apply(list.get(i), definition, i));
            }
            return handles;
        }
    }
}
