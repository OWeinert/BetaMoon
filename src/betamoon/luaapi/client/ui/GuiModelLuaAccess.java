package betamoon.luaapi.client.ui;

import betamoon.gui.component.GuiComponentSchema;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Scoped typed model access; retained Lua never receives a mutable backing
 * table.
 */
public final class GuiModelLuaAccess {
    public static LuaTable create(LuaCallbackScope scope, GuiComponentSchema schema,
            Supplier<Map<String, Object>> values, Consumer<Map<String, Object>> publish) {
        return create(scope, schema, values, publish, null);
    }

    public static LuaTable create(LuaCallbackScope scope, GuiComponentSchema schema,
            Supplier<Map<String, Object>> values, Consumer<Map<String, Object>> publish, String owner) {
        LuaTable access = new LuaTable();
        access.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                String name = LuaDeclarationValues.string(arguments.arg(arguments.arg1() == access ? 2 : 1),
                        "model field");
                if (!schema.fields().containsKey(name)) {
                    throw new LuaError("Unknown UI model field: " + name);
                }
                return GuiReadOnlyValues.fromPublished(Collections.singletonMap("value", values.get().get(name)))
                        .get("value");
            }
        });
        access.set("snapshot", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return GuiReadOnlyValues.fromPublished(values.get());
            }
        });
        access.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireUiStateMutable();
                String source = LuaScriptRegistry.getCurrentScriptFile();
                if (owner != null && source != null && !owner.equals(source)) {
                    throw new LuaError("UI model publication belongs to another owner");
                }
                int offset = arguments.arg1() == access ? 1 : 0;
                Map<String, Object> changes;
                if (arguments.arg(offset + 1).istable()) {
                    changes = GuiAuthoredNodeParser.readDeclaredProps(schema, arguments.arg(offset + 1),
                            "model changes");
                } else {
                    String name = LuaDeclarationValues.string(arguments.arg(offset + 1), "model field");
                    GuiComponentSchema.Field field = schema.fields().get(name);
                    if (field == null) {
                        throw new LuaError("Unknown UI model field: " + name);
                    }
                    changes = Collections.singletonMap(name,
                            GuiPropertyValues.read(field.descriptor, arguments.arg(offset + 2)));
                }
                publish.accept(changes);
                return NIL;
            }
        });
        return access;
    }

    private GuiModelLuaAccess() {
    }
}
