package betamoon.luaapi.recipe;

import betamoon.luaapi.resource.LuaResultList;
import betamoon.recipes.custom.RecipeMatching;
import betamoon.recipes.custom.RecipeTypes;
import betamoon.recipes.custom.RecipeValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.recipes.custom.RecipeValues.*;

/** Declarative public recipe type registry. */
public final class RecipeTypesApi {
    private RecipeTypesApi() {
    }

    public static void attach(LuaTable root) {
        final LuaTable service = new LuaTable();
        root.set("recipeTypes", service);
        service.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return RecipeTypes.add(args.arg(args.arg1() == service ? 2 : 1)).reference;
            }
        });
        for (final String operation : new String[]{"get", "getRequired", "find", "first", "one"}) {
            service.set(operation, new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaValue query = args.arg(args.arg1() == service ? 2 : 1);
                    if (operation.equals("get") || operation.equals("getRequired")) {
                        RecipeTypes.Type type = RecipeTypes.get(query, operation.equals("getRequired"));
                        return type == null ? NIL : type.reference;
                    }
                    if (query.isnil()) {
                        query = new LuaTable();
                    }
                    fields(query, "recipeTypes query", "name", "owner", "nameContains", "ignoreCase");
                    boolean ignoreCase = bool(query.get("ignoreCase"), false, "recipeTypes query.ignoreCase");
                    List<LuaValue> results = new ArrayList<LuaValue>();
                    for (RecipeTypes.Type type : RecipeTypes.all()) {
                        if (!query.get("name").isnil() && !type.name.equals(RecipeTypes.name(query.get("name")))) {
                            continue;
                        }
                        if (!query.get("owner").isnil()
                                && !type.owner.equals(string(query.get("owner"), "recipeTypes query.owner"))) {
                            continue;
                        }
                        if (!query.get("nameContains").isnil()) {
                            String needle = string(query.get("nameContains"), "recipeTypes query.nameContains");
                            String haystack = type.name;
                            if (ignoreCase) {
                                needle = needle.toLowerCase(Locale.ROOT);
                                haystack = haystack.toLowerCase(Locale.ROOT);
                            }
                            if (!haystack.contains(needle)) {
                                continue;
                            }
                        }
                        results.add(type.reference);
                    }
                    if (operation.equals("find")) {
                        return new LuaResultList(results, null);
                    }
                    if (operation.equals("one") && results.size() > 1) {
                        throw RecipeValues.error("recipeTypes:one",
                                "expected exactly one result, found " + results.size());
                    }
                    return results.isEmpty() ? NIL : results.get(0);
                }
            });
        }
        RecipeMatching.attach((LuaTable) root.get("recipes"), null);
    }
}
