package betamoon.luaapi.client.ui;

import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.screen.GuiActionDefinition;
import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiActionTicket;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.screen.GuiScreenController;
import betamoon.gui.screen.GuiScreenDefinition;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Owned logical handles; callback contexts additionally expire when their scope
 * returns.
 */
public final class GuiScreenLuaAccess {
    public interface Lookup {
        GuiScreenDefinition getRequired(String key);
    }

    public static LuaTable instance(String source, GuiScreenActivation activation, LuaCallbackScope scope,
            Lookup definitions) {
        LuaTable access = new LuaTable();
        access.set("key", activation.key);
        access.set("status", function(args -> {
            guard(source, scope, false);
            return LuaValue.valueOf(activation.status().name().toLowerCase(Locale.ROOT));
        }));
        access.set("reason", function(args -> {
            guard(source, scope, false);
            return activation.reason() == null ? LuaValue.NIL : LuaValue.valueOf(activation.reason());
        }));
        access.set("close", function(args -> {
            guard(source, scope, true);
            return LuaValue.valueOf(activation.close());
        }));
        access.set("back", function(args -> {
            guard(source, scope, true);
            return LuaValue.valueOf(activation.back());
        }));
        for (String operation : Arrays.asList("push", "replace", "dialog")) {
            access.set(operation, function(args -> {
                guard(source, scope, true);
                int offset = args.arg1() == access ? 1 : 0;
                String key = LuaDeclarationValues.string(args.arg(offset + 1), "screen key");
                GuiScreenDefinition definition = definitions.getRequired(key);
                Map<String, Object>[] options = options(definition, args.arg(offset + 2));
                GuiScreenController.OpenResult result = operation.equals("dialog")
                        ? activation.dialog(key, options[0], options[1])
                        : operation.equals("push")
                                ? activation.push(key, options[0], options[1])
                                : activation.replace(key, options[0], options[1]);
                return result.instance == null
                        ? refused(result.reason)
                        : instance(source, result.instance, null, definitions);
            }));
        }
        access.set("request", function(args -> {
            guard(source, scope, true);
            activation.requireActive();
            int offset = args.arg1() == access ? 1 : 0;
            String name = LuaDeclarationValues.string(args.arg(offset + 1), "action name");
            GuiActionDefinition action = activation.definition().actions.get(name);
            if (action == null) {
                throw new LuaError("Undeclared screen action: " + name);
            }
            Map<String, Object> payload = GuiAuthoredNodeParser.readProps(action.payload, args.arg(offset + 2),
                    "action." + name);
            GuiActionQueue.Submission result = activation.request(name, payload);
            return result.ticket == null ? refused(result.reason) : ticket(source, result.ticket);
        }));
        access.set("element", function(args -> {
            guard(source, scope, false);
            activation.requireActive();
            String id = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1), "element id");
            return element(source, activation, scope, activation.plan().elementId(id));
        }));
        access.set("state",
                model(source, scope, () -> activation.definition().state, activation::state, activation::state));
        access.set("data",
                model(source, scope, () -> activation.definition().data, activation::data, activation::data));
        return access;
    }

    public static Varargs opened(String source, GuiScreenController.OpenResult result, Lookup definitions) {
        return result.instance == null ? refused(result.reason) : instance(source, result.instance, null, definitions);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object>[] options(GuiScreenDefinition definition, LuaValue value) {
        if (value.isnil()) {
            return new Map[]{Collections.emptyMap(), Collections.emptyMap()};
        }
        LuaDeclarationValues.fields(value, "screen.options", "state", "data");
        return new Map[]{
                GuiAuthoredNodeParser.readDeclaredProps(definition.state, value.get("state"), "screen.options.state"),
                GuiAuthoredNodeParser.readDeclaredProps(definition.data, value.get("data"), "screen.options.data")};
    }

    private static LuaTable element(String source, GuiScreenActivation activation, LuaCallbackScope scope, String id) {
        long revision = activation.revision();
        LuaTable access = new LuaTable();
        for (String operation : Arrays.asList("set", "animate", "override", "clearOverride", "cancel", "finish",
                "refresh", "get", "target")) {
            access.set(operation, function(args -> {
                boolean read = operation.equals("get") || operation.equals("target");
                guard(source, scope, !read);
                activation.requireActive();
                if (revision != activation.revision()) {
                    throw new LuaError("UI element handle belongs to a stale visual revision");
                }
                int offset = args.arg1() == access ? 1 : 0;
                if (read) {
                    LuaDeclarationValues.string(args.arg(offset + 1), "element property");
                }
                return activation.plan().runtime().invokeElement(id, operation, args.arg(offset + 1),
                        args.arg(offset + 2));
            }));
        }
        return access;
    }

    private static LuaTable model(String source, LuaCallbackScope scope, Supplier<GuiComponentSchema> schema,
            Supplier<Map<String, Object>> values, java.util.function.Consumer<Map<String, Object>> publish) {
        LuaTable access = new LuaTable();
        access.set("get", function(args -> {
            guard(source, scope, false);
            String name = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1), "model field");
            if (!schema.get().fields().containsKey(name)) {
                throw new LuaError("Unknown UI model field: " + name);
            }
            return GuiReadOnlyValues.fromPublished(Collections.singletonMap("value", values.get().get(name)))
                    .get("value");
        }));
        access.set("snapshot", function(args -> {
            guard(source, scope, false);
            return GuiReadOnlyValues.fromPublished(values.get());
        }));
        access.set("set", function(args -> {
            guard(source, scope, true);
            int offset = args.arg1() == access ? 1 : 0;
            Map<String, Object> changes;
            if (args.arg(offset + 1).istable()) {
                changes = GuiAuthoredNodeParser.readDeclaredProps(schema.get(), args.arg(offset + 1), "model changes");
            } else {
                String name = LuaDeclarationValues.string(args.arg(offset + 1), "model field");
                GuiComponentSchema.Field field = schema.get().fields().get(name);
                if (field == null) {
                    throw new LuaError("Unknown UI model field: " + name);
                }
                changes = Collections.singletonMap(name,
                        GuiPropertyValues.read(field.descriptor, args.arg(offset + 2)));
            }
            publish.accept(changes);
            return LuaValue.NIL;
        }));
        return access;
    }

    private static LuaTable ticket(String source, GuiActionTicket ticket) {
        LuaTable access = new LuaTable();
        access.set("id", LuaValue.valueOf((double) ticket.identity));
        access.set("status", function(args -> {
            guard(source, null, false);
            return LuaValue.valueOf(ticket.status().name().toLowerCase(Locale.ROOT));
        }));
        access.set("reason", function(args -> {
            guard(source, null, false);
            return ticket.reason() == null ? LuaValue.NIL : LuaValue.valueOf(ticket.reason());
        }));
        access.set("result", function(args -> {
            guard(source, null, false);
            return GuiReadOnlyValues.fromPublished(Collections.singletonMap("value", ticket.result())).get("value");
        }));
        return access;
    }

    private static void guard(String source, LuaCallbackScope scope, boolean mutable) {
        if (scope != null) {
            scope.requireActive();
        }
        String current = LuaScriptRegistry.getCurrentScriptFile();
        if (current != null && !current.equals(source)) {
            throw new LuaError("UI handle belongs to another script owner");
        }
        if (mutable) {
            if (scope != null) {
                scope.requireUiStateMutable();
            } else {
                LuaCallbackScope.requireUiStateWrite();
            }
        }
    }

    private static Varargs refused(String reason) {
        return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(reason)});
    }
    private interface Method {
        Varargs invoke(Varargs arguments);
    }
    private static LuaValue function(Method method) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                try {
                    return method.invoke(arguments);
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    throw new LuaError(invalid.getMessage());
                }
            }
        };
    }

    private GuiScreenLuaAccess() {
    }
}
