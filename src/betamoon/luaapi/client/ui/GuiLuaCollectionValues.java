package betamoon.luaapi.client.ui;

import betamoon.gui.collection.GuiCollectionModel;
import betamoon.gui.collection.GuiCollectionValues;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/**
 * Dedicated record-list conversion; each payload and the complete model retain
 * their independent budgets.
 */
public final class GuiLuaCollectionValues {
    private GuiLuaCollectionValues() {
    }

    public static GuiCollectionValues read(LuaValue value, String path) {
        int length = LuaDeclarationValues.length(value, path);
        if (length > GuiCollectionModel.MAX_RECORDS) {
            throw LuaDeclarationValues.error(path, "collection exceeds 4096 records");
        }
        GuiCollectionValues.Preparation prepared = new GuiCollectionValues.Preparation();
        for (int index = 1; index <= length; index++) {
            LuaValue record = value.get(index);
            String item = path + "[" + index + "]";
            LuaDeclarationValues.fields(record, item, "key", "parent", "label", "enabled", "data");
            String key = LuaDeclarationValues.string(record.get("key"), item + ".key");
            String parent = record.get("parent").isnil()
                    ? null
                    : LuaDeclarationValues.string(record.get("parent"), item + ".parent");
            String label = record.get("label").isnil()
                    ? key
                    : LuaDeclarationValues.string(record.get("label"), item + ".label");
            boolean enabled = LuaDeclarationValues.bool(record.get("enabled"), item + ".enabled", true);
            Map<String, Object> data = record.get("data").isnil()
                    ? Collections.emptyMap()
                    : GuiLuaData.record(record.get("data"), item + ".data");
            try {
                prepared.add(new GuiCollectionModel.Record(key, parent, label, enabled, data));
            } catch (IllegalArgumentException invalid) {
                throw LuaDeclarationValues.error(item, invalid.getMessage());
            }
        }
        try {
            return prepared.finish();
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }
}
