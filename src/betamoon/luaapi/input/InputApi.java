package betamoon.luaapi.input;

import betamoon.client.control.input.ClientHotkeys;
import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.HotkeyDefinition;
import betamoon.client.control.input.HotkeyRegistration;
import betamoon.client.control.input.InputActionDefinition;
import betamoon.client.control.input.InputBinding;
import betamoon.client.control.input.InputDeviceEvent;
import betamoon.client.control.input.InputFamily;
import betamoon.client.control.input.InputMapDefinition;
import betamoon.client.control.input.InputMapRegistration;
import betamoon.content.ContentKey;
import betamoon.content.ContentKeyException;
import betamoon.content.ContentNamespaces;
import betamoon.content.ContentType;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Lua facade for owner-scoped named input maps and contexts. */
public final class InputApi {
    private InputApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable input = new LuaTable();
        input.set("map", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue declaration = args.arg(args.arg1() == input ? 2 : 1);
                return register(declaration);
            }
        });
        input.set("hotkey", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue declaration = args.arg(args.arg1() == input ? 2 : 1);
                return registerHotkey(declaration);
            }
        });
        module.set("input", input);
    }

    private static LuaInputMap register(LuaValue value) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Input maps may only be declared by a Lua script.");
        }
        LuaTable declaration = table(value, "input map");
        fields(declaration, "input map", "key", "actions", "priority", "captures");
        ContentKey key = contentKey(declaration.get("key"), ContentType.INPUT_MAP, "Input map key");
        List<InputActionDefinition> actions = actions(declaration.get("actions"));
        int priority = integer(declaration.get("priority"), 0, InputRouterLimits.MIN_PRIORITY,
                InputRouterLimits.MAX_PRIORITY, "input map priority");
        EnumSet<InputFamily> captures = captures(declaration.get("captures"), "input map captures");
        try {
            InputMapRegistration registration = ClientInputRuntime.router()
                    .register(new InputMapDefinition(key, actions), owner);
            LuaInputMap handle = new LuaInputMap(registration, priority, captures, owner);
            ScriptResourceTracker.track(registration::close);
            return handle;
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new LuaError("Input map: " + error.getMessage());
        }
    }

    private static LuaHotkey registerHotkey(LuaValue value) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Hotkeys may only be declared by a Lua script.");
        }
        LuaTable declaration = table(value, "hotkey");
        fields(declaration, "hotkey", "key", "label", "default", "repeatWhileHeld");
        ContentKey key = contentKey(declaration.get("key"), ContentType.HOTKEY, "Hotkey key");
        String label = declaration.get("label").checkjstring();
        InputBinding binding = parseBinding(declaration.get("default").checkjstring(), "Hotkey default");
        if (binding.device() != InputDeviceEvent.Device.KEYBOARD || binding.modifiers() != 0) {
            throw new LuaError("Hotkey default must be one unmodified keyboard binding such as 'key.g'.");
        }
        boolean repeat = declaration.get("repeatWhileHeld").optboolean(false);
        try {
            HotkeyRegistration registration = ClientHotkeys.registry()
                    .register(new HotkeyDefinition(key, label, binding.code(), repeat), owner);
            LuaHotkey handle = new LuaHotkey(registration, owner);
            ScriptResourceTracker.track(registration::close);
            return handle;
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new LuaError("Hotkey: " + error.getMessage());
        }
    }

    private static ContentKey contentKey(LuaValue value, ContentType type, String path) {
        try {
            ContentKey key = ContentKey.parseForType(value.checkjstring(), type);
            ContentNamespaces.requireUserNamespace(key);
            return key;
        } catch (ContentKeyException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static List<InputActionDefinition> actions(LuaValue value) {
        LuaTable declarations = table(value, "input map actions");
        List<String> names = new ArrayList<String>();
        for (LuaValue key : declarations.keys()) {
            if (!key.isstring()) {
                throw new LuaError("Input map action names must be strings.");
            }
            names.add(key.checkjstring());
        }
        Collections.sort(names);
        List<InputActionDefinition> actions = new ArrayList<InputActionDefinition>();
        for (String name : names) {
            try {
                actions.add(new InputActionDefinition(name, actionBindings(declarations.get(name), name)));
            } catch (IllegalArgumentException error) {
                throw new LuaError("Input action '" + name + "': " + error.getMessage());
            }
        }
        return actions;
    }

    private static List<InputBinding> actionBindings(LuaValue value, String action) {
        if (value.isstring()) {
            return bindings(value, "input action '" + action + "'");
        }
        LuaTable declaration = table(value, "input action '" + action + "'");
        fields(declaration, "input action '" + action + "'", "default", "bindings");
        LuaValue defaults = declaration.get("default");
        LuaValue explicit = declaration.get("bindings");
        if (!defaults.isnil() && !explicit.isnil()) {
            throw new LuaError("Input action '" + action + "' cannot declare both default and bindings.");
        }
        LuaValue selected = defaults.isnil() ? explicit : defaults;
        if (selected.isnil()) {
            throw new LuaError("Input action '" + action + "' requires default or bindings.");
        }
        return bindings(selected, "input action '" + action + "'");
    }

    static List<InputBinding> bindings(LuaValue value, String path) {
        List<InputBinding> result = new ArrayList<InputBinding>();
        if (value.isstring()) {
            result.add(parseBinding(value.checkjstring(), path));
            return result;
        }
        LuaTable values = table(value, path + " bindings");
        int length = values.length();
        if (length == 0) {
            throw new LuaError(path + " requires at least one binding.");
        }
        for (int index = 1; index <= length; index++) {
            result.add(parseBinding(values.get(index).checkjstring(), path));
        }
        return result;
    }

    private static InputBinding parseBinding(String value, String path) {
        try {
            return InputBinding.parse(value);
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    static EnumSet<InputFamily> captures(LuaValue value, String path) {
        EnumSet<InputFamily> result = EnumSet.noneOf(InputFamily.class);
        if (value.isnil()) {
            return result;
        }
        LuaTable values = table(value, path);
        for (LuaValue key : values.keys()) {
            LuaValue entry;
            if (key.isint()) {
                entry = values.get(key);
            } else if (key.isstring() && values.get(key).toboolean()) {
                entry = key;
            } else {
                throw new LuaError(path + " must be an array of names or a table of true flags.");
            }
            try {
                result.add(InputFamily.parse(entry.checkjstring()));
            } catch (IllegalArgumentException error) {
                throw new LuaError(
                        path + ": " + error.getMessage() + ". Supported: " + Arrays.toString(InputFamily.values()));
            }
        }
        return result;
    }

    static int priority(LuaValue value, int fallback, String path) {
        return integer(value, fallback, InputRouterLimits.MIN_PRIORITY, InputRouterLimits.MAX_PRIORITY, path);
    }

    private static int integer(LuaValue value, int fallback, int minimum, int maximum, String path) {
        int result = value.isnil() ? fallback : value.checkint();
        if (result < minimum || result > maximum) {
            throw new LuaError(path + " must be within " + minimum + ".." + maximum + ".");
        }
        return result;
    }

    static LuaTable table(LuaValue value, String path) {
        if (!value.istable()) {
            throw new LuaError(path + " must be a table.");
        }
        return value.checktable();
    }

    static void fields(LuaTable table, String path, String... accepted) {
        List<String> allowed = Arrays.asList(accepted);
        for (LuaValue key : table.keys()) {
            if (!key.isstring() || !allowed.contains(key.checkjstring())) {
                throw new LuaError(path + " contains unknown field '" + key.tojstring() + "'.");
            }
        }
    }

    /**
     * Avoids exposing the router implementation solely for validation constants.
     */
    private static final class InputRouterLimits {
        private static final int MIN_PRIORITY = -10000;
        private static final int MAX_PRIORITY = 10000;
    }
}
