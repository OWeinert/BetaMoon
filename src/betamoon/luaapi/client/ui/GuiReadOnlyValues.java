package betamoon.luaapi.client.ui;

import betamoon.gui.presentation.GuiPresentationRuntime;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Frozen detached Lua values may be reused across raw frames without exposing
 * mutable shared tables.
 */
public final class GuiReadOnlyValues {
    public static LuaValue fromPublished(Map<String, ?> values) {
        return freeze(GuiPresentationRuntime.lua(values));
    }

    private static LuaValue freeze(LuaValue value) {
        if (!value.istable()) {
            return value;
        }
        ReadOnlyTable result = new ReadOnlyTable();
        for (LuaValue key : value.checktable().keys()) {
            result.rawset(key, freeze(value.rawget(key)));
        }
        result.sealed = true;
        return result;
    }

    private static final class ReadOnlyTable extends LuaTable {
        private boolean sealed;

        private void requireBuilding() {
            if (sealed) {
                throw new LuaError("Published UI values are read-only");
            }
        }

        @Override
        public void set(int key, LuaValue value) {
            requireBuilding();
            super.set(key, value);
        }

        @Override
        public void set(LuaValue key, LuaValue value) {
            requireBuilding();
            super.set(key, value);
        }

        @Override
        public void rawset(int key, LuaValue value) {
            requireBuilding();
            super.rawset(key, value);
        }

        @Override
        public void rawset(LuaValue key, LuaValue value) {
            requireBuilding();
            super.rawset(key, value);
        }

        @Override
        public void hashset(LuaValue key, LuaValue value) {
            requireBuilding();
            super.hashset(key, value);
        }

        @Override
        public LuaValue setmetatable(LuaValue metatable) {
            requireBuilding();
            return super.setmetatable(metatable);
        }

        @Override
        public void sort(LuaValue compare) {
            requireBuilding();
            super.sort(compare);
        }

        @Override
        public void insert(int position, LuaValue value) {
            requireBuilding();
            super.insert(position, value);
        }

        @Override
        public LuaValue remove(int position) {
            requireBuilding();
            return super.remove(position);
        }

        @Override
        public void presize(int size) {
            requireBuilding();
            super.presize(size);
        }

        @Override
        public void presize(int array, int hash) {
            requireBuilding();
            super.presize(array, hash);
        }
    }

    private GuiReadOnlyValues() {
    }
}
