package betamoon.luaapi.world;

import betamoon.assets.AssetKey;
import betamoon.loot.LootTableDefinition;
import betamoon.loot.LootTableRegistry;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.structure.WorldGenDataResolver;
import java.io.IOException;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Installs reusable data-driven loot-table registration and lookup. */
public final class LootTablesApi {
    private LootTablesApi() {
    }

    public static void attach(LuaTable module) {
        final LuaTable tables = new LuaTable();
        tables.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue definition = argument(arguments, tables, 1);
                if (!definition.istable()) {
                    throw new LuaError("lootTables:add expects a definition table");
                }
                fields(definition, "lootTables:add", "key", "path", "pools");
                String declaredKey = required(definition, "key").checkjstring();
                LuaValue path = definition.get("path");
                LuaValue pools = definition.get("pools");
                if (!path.isnil() && !pools.isnil()) {
                    throw new LuaError("LootTable: path and pools are mutually exclusive");
                }
                String owner = LuaScriptRegistry.getCurrentScriptFile();
                try {
                    AssetKey key = AssetKey.parse(declaredKey);
                    LootTableDefinition table;
                    String source;
                    if (!pools.isnil()) {
                        Map<String, Object> data = LuaStructureDocument.snapshotObject(definition,
                                "LootTable");
                        data.remove("key");
                        data.remove("path");
                        data.put("format", "betamoon_loot_table");
                        table = LootTableDefinition.read(data);
                        source = owner + ":inline loot table " + key;
                    } else {
                        WorldGenDataResolver.ResolvedData data = WorldGenDataResolver.lootTable(owner, key,
                                path.isnil() ? null : path.checkjstring());
                        table = LootTableDefinition.read(data.bytes);
                        source = data.displayPath;
                    }
                    return new LootTableReference(LootTableRegistry.add(declaredKey, table, source));
                } catch (IOException | IllegalArgumentException error) {
                    throw new LuaError("Loot table " + declaredKey + ": " + error.getMessage());
                }
            }
        });
        tables.set("get", lookup(tables, false));
        tables.set("getRequired", lookup(tables, true));
        module.set("lootTables", tables);
    }

    private static VarArgFunction lookup(final LuaTable tables, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                AssetKey key;
                try {
                    key = AssetKey.parse(argument(arguments, tables, 1).checkjstring());
                } catch (IllegalArgumentException error) {
                    throw new LuaError("LootTable.get: " + error.getMessage());
                }
                if (LootTableRegistry.find(key) == null) {
                    if (required) {
                        throw new LuaError("Loot table is not registered: " + key);
                    }
                    return NIL;
                }
                return new LootTableReference(key);
            }
        };
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }
}
