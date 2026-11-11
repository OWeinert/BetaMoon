package betamoon.luaapi.tileentity;

import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.component.GuiComponentLimits;
import betamoon.luaapi.client.ui.GuiLuaData;
import betamoon.luaapi.client.ui.GuiReadOnlyValues;
import betamoon.luaapi.client.ui.GuiScreenLuaAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.LuaScriptRegistry;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Scoped container data contains detached values; expired closures retain no
 * native host.
 */
public final class GuiContainerLuaAccess {
    public interface Commands {
        GuiActionQueue.Submission request(GuiScreenActivation activation, String name, Map<String, Object> options);
    }

    public static final class Snapshot {
        public final Map<String, Object> values;
        private final LuaValue readonly;

        public Snapshot(Map<String, Object> values) {
            this.values = values;
            readonly = GuiReadOnlyValues.fromPublished(values);
        }
    }

    public static LuaTable context(LuaCallbackScope scope, Snapshot publication, Commands commands,
            GuiScreenActivation activation) {
        LuaTable access = new LuaTable();
        Map<String, Object> snapshot = publication.values;
        LuaValue values = publication.readonly;
        for (String method : new String[]{"schema", "layout", "session", "data", "control"}) {
            access.set(method, new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    scope.requireActive();
                    String key = "container." + method;
                    if (!(method.equals("schema") || method.equals("layout"))) {
                        key += "." + LuaDeclarationValues.string(arguments.arg(arguments.arg1() == access ? 2 : 1),
                                "container." + method + " field");
                    }
                    if (!snapshot.containsKey(key)) {
                        throw LuaDeclarationValues.error(key, "unknown or unsynchronized container field");
                    }
                    scope.readUiSource("native:" + key, snapshot.get(key));
                    return values.get(key);
                }
            });
        }
        WeakReference<Commands> endpoint = new WeakReference<>(commands);
        WeakReference<GuiScreenActivation> source = new WeakReference<>(activation);
        String owner = activation.owner();
        access.set("request", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                scope.requireUiStateMutable();
                int offset = arguments.arg1() == access ? 1 : 0;
                String name = LuaDeclarationValues.string(arguments.arg(offset + 1), "container.request.control");
                LuaValue control = values.get("container.schema").get("controls").get(name);
                if (control.isnil()) {
                    return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf("unknown_control")});
                }
                Map<String, Object> options = options(control.get("type").tojstring(), arguments.arg(offset + 2));
                Commands target = endpoint.get();
                GuiScreenActivation current = source.get();
                GuiActionQueue.Submission result = target == null || current == null
                        ? GuiActionQueue.unavailable("container_unavailable")
                        : target.request(current, name, options);
                String caller = LuaScriptRegistry.getCurrentScriptFile();
                return GuiScreenLuaAccess.submitted(caller == null ? owner : caller, result);
            }
        });
        return access;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> options(String type, LuaValue value) {
        if (value.isnil()) {
            value = new LuaTable();
        }
        String path = "container.request.options";
        Map<String, Object> result = new LinkedHashMap<>();
        switch (type) {
            case "number":
                LuaDeclarationValues.fields(value, path, "value");
                result.put("value", LuaDeclarationValues.number(value.get("value"), path + ".value"));
                break;
            case "text":
                LuaDeclarationValues.fields(value, path, "value");
                result.put("value", LuaDeclarationValues.string(value.get("value"), path + ".value"));
                break;
            case "choice":
                LuaDeclarationValues.fields(value, path, "direction");
                int direction = value.get("direction").isnil()
                        ? 1
                        : LuaDeclarationValues.integer(value.get("direction"), path + ".direction", -1, 1);
                if (direction == 0) {
                    throw LuaDeclarationValues.error(path + ".direction", "expected -1 or 1");
                }
                result.put("direction", direction);
                break;
            case "custom":
                LuaDeclarationValues.fields(value, path, "input");
                LuaValue input = value.get("input").isnil() ? new LuaTable() : value.get("input");
                LuaDeclarationValues.fields(input, path + ".input", "phase", "button", "wheel", "x", "y", "deltaX",
                        "deltaY", "key", "character", "shift", "control");
                Map<String, Object> fields = (Map<String, Object>) GuiLuaData.read(input, path + ".input");
                for (Map.Entry<String, Object> field : fields.entrySet()) {
                    String key = field.getKey();
                    Object item = field.getValue();
                    boolean valid = key.equals("phase") || key.equals("character")
                            ? item instanceof String
                            : key.equals("shift") || key.equals("control")
                                    ? item instanceof Boolean
                                    : item instanceof Number;
                    if (!valid) {
                        throw LuaDeclarationValues.error(path + ".input." + key, "invalid primitive input field");
                    }
                    if (key.equals("button") || key.equals("wheel") || key.equals("key")) {
                        LuaDeclarationValues.integer(input.get(key), path + ".input." + key, Integer.MIN_VALUE,
                                Integer.MAX_VALUE);
                    }
                }
                result.put("input", fields);
                break;
            default:
                LuaDeclarationValues.fields(value, path);
                break;
        }
        try {
            new GuiComponentLimits.Values(65536, 4096).add(result);
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
        return Collections.unmodifiableMap(result);
    }

    private GuiContainerLuaAccess() {
    }
}
