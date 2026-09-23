package betamoon.luaapi.capability;

import betamoon.assets.AssetKey;
import betamoon.capability.CapabilityDefinition;
import betamoon.capability.CapabilityOperationDefinition;
import betamoon.capability.CapabilityReference;
import betamoon.capability.CapabilityRegistry;
import betamoon.data.DataSchema;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Lua registration and lookup API for reusable typed capability contracts. */
public final class CapabilitiesApi {
    private CapabilitiesApi() {
    }

    public static void attach(LuaTable root) {
        final LuaTable capabilities = new LuaTable();
        capabilities.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return CapabilitiesApi.add(argument(arguments, capabilities));
            }
        });
        capabilities.set("get", lookup(capabilities, false));
        capabilities.set("getRequired", lookup(capabilities, true));
        root.set("capabilities", capabilities);
    }

    public static CapabilityDefinition definition(LuaValue value, String path) {
        if (value instanceof CapabilityReference) {
            return ((CapabilityReference) value).definition();
        }
        try {
            CapabilityDefinition definition = CapabilityRegistry.find(AssetKey.parse(value.checkjstring()));
            if (definition == null) {
                throw new LuaError(path + ": capability is not registered: " + value.tojstring());
            }
            return definition;
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static CapabilityReference add(LuaValue declaration) {
        LuaDeclarationValues.fields(declaration, "capability", "key", "state", "config", "operations");
        AssetKey key;
        try {
            key = AssetKey.parse(LuaDeclarationValues.string(
                    LuaDeclarationValues.required(declaration, "key"), "capability.key"));
        } catch (IllegalArgumentException error) {
            throw new LuaError("capability.key: " + error.getMessage());
        }
        if (!key.getPath().startsWith("capability/")) {
            throw new LuaError("capability.key must use namespace:capability/name.");
        }
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("capabilities:add is available only while a Lua mod is loading.");
        }
        DataSchema state = withoutEntityReferences(
                DataSchema.parse(declaration.get("state"), "capability.state"), "capability.state");
        DataSchema config = withoutEntityReferences(
                DataSchema.parse(declaration.get("config"), "capability.config"), "capability.config");
        LuaValue operationDeclarations = declaration.get("operations");
        if (operationDeclarations.isnil()) {
            operationDeclarations = new LuaTable();
        }
        List<String> names = LuaDeclarationValues.keys(operationDeclarations, "capability.operations");
        if (names.size() > 64) {
            throw new LuaError("capability.operations may contain at most 64 operations.");
        }
        Map<String, CapabilityOperationDefinition> operations = new LinkedHashMap<>();
        for (String name : names) {
            if (!name.matches("[a-z][a-zA-Z0-9_]{0,63}")) {
                throw new LuaError("Invalid capability operation name: " + name);
            }
            String path = "capability.operations." + name;
            LuaValue operation = operationDeclarations.get(name);
            LuaDeclarationValues.fields(operation, path, "mode", "request", "response");
            String modeName = operation.get("mode").optjstring("query").toLowerCase();
            CapabilityOperationDefinition.Mode mode;
            if ("query".equals(modeName)) {
                mode = CapabilityOperationDefinition.Mode.QUERY;
            } else if ("action".equals(modeName)) {
                mode = CapabilityOperationDefinition.Mode.ACTION;
            } else {
                throw new LuaError(path + ".mode must be 'query' or 'action'.");
            }
            DataSchema request = withoutEntityReferences(
                    DataSchema.parse(operation.get("request"), path + ".request"), path + ".request");
            DataSchema response = withoutEntityReferences(
                    DataSchema.parse(operation.get("response"), path + ".response"), path + ".response");
            operations.put(name, new CapabilityOperationDefinition(name, mode, request, response));
        }
        CapabilityDefinition definition = new CapabilityDefinition(key, owner, state, config, operations);
        try {
            CapabilityRegistry.register(definition);
        } catch (IllegalArgumentException error) {
            throw new LuaError("capabilities:add: " + error.getMessage());
        }
        return new CapabilityReference(definition);
    }

    private static DataSchema withoutEntityReferences(DataSchema schema, String path) {
        if (schema.containsEntityReference()) {
            throw new LuaError(path + ": entity_reference is currently supported only by entity data.");
        }
        return schema;
    }

    private static VarArgFunction lookup(final LuaTable registry, final boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                LuaValue keyValue = argument(arguments, registry);
                try {
                    CapabilityDefinition definition = CapabilityRegistry.find(AssetKey.parse(keyValue.checkjstring()));
                    if (definition == null) {
                        if (required) {
                            throw new LuaError("Capability is not registered: " + keyValue.tojstring());
                        }
                        return LuaValue.NIL;
                    }
                    return new CapabilityReference(definition);
                } catch (IllegalArgumentException error) {
                    throw new LuaError("capabilities: " + error.getMessage());
                }
            }
        };
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver) {
        return arguments.arg(arguments.arg1() == receiver ? 2 : 1);
    }
}
