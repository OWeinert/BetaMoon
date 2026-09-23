package betamoon.luaapi.networking;

import betamoon.assets.AssetKey;
import betamoon.capability.CapabilityDefinition;
import betamoon.data.DataField;
import betamoon.luaapi.capability.CapabilitiesApi;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.networking.LogicalNetworkDefinition;
import betamoon.networking.LogicalNetworkReference;
import betamoon.networking.LogicalNetworkRegistry;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Lua declaration and lookup API for capability-backed gameplay networks. */
public final class LogicalNetworksApi {
    private LogicalNetworksApi() {
    }

    public static void attach(LuaTable root) {
        final LuaTable networks = new LuaTable();
        networks.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return LogicalNetworksApi.add(argument(arguments, networks));
            }
        });
        networks.set("get", lookup(networks, false));
        networks.set("getRequired", lookup(networks, true));
        root.set("logicalNetworks", networks);
    }

    public static LogicalNetworkDefinition definition(LuaValue value, String path) {
        if (value instanceof LogicalNetworkReference) {
            return ((LogicalNetworkReference) value).definition();
        }
        LogicalNetworkDefinition definition = LogicalNetworkRegistry.find(key(value, path));
        if (definition == null) {
            throw new LuaError(path + ": logical network is not registered: " + value.tojstring());
        }
        return definition;
    }

    private static LogicalNetworkReference add(LuaValue declaration) {
        LuaDeclarationValues.fields(declaration, "logicalNetwork",
                "key", "capability", "topology", "tick", "signal", "canConnect", "onTick", "onPulse");
        AssetKey key = key(LuaDeclarationValues.required(declaration, "key"), "logicalNetwork.key");
        if (!key.getPath().startsWith("network/")) {
            throw new LuaError("logicalNetwork.key must use namespace:network/name.");
        }
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("logicalNetworks:add is available only while a Lua mod is loading.");
        }
        CapabilityDefinition capability = CapabilitiesApi.definition(
                LuaDeclarationValues.required(declaration, "capability"), "logicalNetwork.capability");
        LuaValue topologyValue = LuaDeclarationValues.required(declaration, "topology");
        LuaDeclarationValues.fields(topologyValue, "logicalNetwork.topology", "type", "directions", "scope",
                "range", "channelField", "roleField", "rangeField", "enabledField", "endpointField",
                "compatibilityFields");
        String type = LuaDeclarationValues.string(
                LuaDeclarationValues.required(topologyValue, "type"), "logicalNetwork.topology.type").toLowerCase();
        LogicalNetworkDefinition.Topology topology;
        try {
            topology = LogicalNetworkDefinition.Topology.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException error) {
            throw new LuaError("logicalNetwork.topology.type must be adjacent, wireless, explicit, or hybrid.");
        }
        String directions = topologyValue.get("directions").optjstring("orthogonal");
        if (!"orthogonal".equals(directions)) {
            throw new LuaError("logicalNetwork.topology.directions currently supports only 'orthogonal'.");
        }
        String scope = topologyValue.get("scope").optjstring("dimension");
        if (!"dimension".equals(scope)) {
            throw new LuaError("logicalNetwork.topology.scope currently supports only 'dimension'.");
        }
        double range = topologyValue.get("range").optdouble(-1.0D);
        if (!Double.isFinite(range) || range == 0.0D || range < -1.0D || range > 1024.0D) {
            throw new LuaError("logicalNetwork.topology.range must be omitted, or greater than 0 and at most 1024.");
        }
        String channelField = optionalField(topologyValue, "channelField");
        String roleField = topologyValue.get("roleField").optjstring("role");
        String rangeField = optionalField(topologyValue, "rangeField");
        String enabledField = optionalField(topologyValue, "enabledField");
        String endpointField = optionalField(topologyValue, "endpointField");
        if ((topology == LogicalNetworkDefinition.Topology.WIRELESS
                || topology == LogicalNetworkDefinition.Topology.HYBRID) && channelField == null) {
            throw new LuaError("Wireless and hybrid topology require channelField.");
        }
        if (topology == LogicalNetworkDefinition.Topology.EXPLICIT && endpointField == null) {
            throw new LuaError("Explicit topology requires endpointField.");
        }
        List<String> compatibility = strings(topologyValue.get("compatibilityFields"),
                "logicalNetwork.topology.compatibilityFields", 8);
        requireScalarField(capability, channelField, "logicalNetwork.topology.channelField", true);
        requireTypedField(capability, roleField, "logicalNetwork.topology.roleField", false,
                DataField.Type.STRING);
        requireTypedField(capability, rangeField, "logicalNetwork.topology.rangeField", false,
                DataField.Type.INTEGER, DataField.Type.NUMBER);
        requireTypedField(capability, enabledField, "logicalNetwork.topology.enabledField", false,
                DataField.Type.BOOLEAN);
        requireTypedField(capability, endpointField, "logicalNetwork.topology.endpointField", false,
                DataField.Type.STRING, DataField.Type.CONTENT_KEY);
        if (endpointField != null && capability.config.get(endpointField) == null) {
            throw new LuaError("logicalNetwork.topology.endpointField must reference immutable capability config.");
        }
        for (String field : compatibility) {
            requireScalarField(capability, field, "logicalNetwork.topology.compatibilityFields", true);
        }
        int interval = 0;
        LuaValue tick = declaration.get("tick");
        if (!tick.isnil()) {
            LuaDeclarationValues.fields(tick, "logicalNetwork.tick", "interval");
            interval = LuaDeclarationValues.integer(LuaDeclarationValues.required(tick, "interval"),
                    "logicalNetwork.tick.interval", 1, 1200);
        }
        LuaValue onTick = callback(declaration.get("onTick"), "logicalNetwork.onTick");
        if (!onTick.isnil() && interval == 0) {
            throw new LuaError("logicalNetwork.tick is required when onTick is present.");
        }
        DataField signalValue = null;
        String signalMode = null;
        String aggregate = "latest";
        boolean retain = false;
        LuaValue signal = declaration.get("signal");
        if (!signal.isnil()) {
            LuaDeclarationValues.fields(signal, "logicalNetwork.signal",
                    "mode", "value", "aggregate", "retainWithoutTransmitters");
            signalMode = signal.get("mode").optjstring("state");
            if (!("state".equals(signalMode) || "pulse".equals(signalMode) || "both".equals(signalMode))) {
                throw new LuaError("logicalNetwork.signal.mode must be state, pulse, or both.");
            }
            signalValue = DataField.parse("value", LuaDeclarationValues.required(signal, "value"),
                    "logicalNetwork.signal.value");
            if (signalValue.containsEntityReference()) {
                throw new LuaError("logicalNetwork.signal.value: entity_reference is unsupported.");
            }
            aggregate = signal.get("aggregate").optjstring("latest").toLowerCase();
            if (!("latest".equals(aggregate) || "any".equals(aggregate) || "all".equals(aggregate)
                    || "maximum".equals(aggregate) || "minimum".equals(aggregate) || "sum".equals(aggregate))) {
                throw new LuaError("logicalNetwork.signal.aggregate is unsupported.");
            }
            if (("any".equals(aggregate) || "all".equals(aggregate))
                    && signalValue.type != DataField.Type.BOOLEAN) {
                throw new LuaError("any/all signal aggregation requires a boolean value.");
            }
            if (("maximum".equals(aggregate) || "minimum".equals(aggregate) || "sum".equals(aggregate))
                    && !(signalValue.type == DataField.Type.INTEGER || signalValue.type == DataField.Type.NUMBER)) {
                throw new LuaError("numeric signal aggregation requires an integer or number value.");
            }
            retain = signal.get("retainWithoutTransmitters").optboolean(false);
        }
        LuaValue onPulse = callback(declaration.get("onPulse"), "logicalNetwork.onPulse");
        if (!onPulse.isnil() && !("pulse".equals(signalMode) || "both".equals(signalMode))) {
            throw new LuaError("logicalNetwork.onPulse requires signal.mode pulse or both.");
        }
        LogicalNetworkDefinition definition = new LogicalNetworkDefinition(key, owner, capability, topology,
                channelField, roleField, rangeField, enabledField, endpointField, range, compatibility, interval,
                callback(declaration.get("canConnect"), "logicalNetwork.canConnect"), onTick,
                onPulse, signalValue, signalMode, aggregate, retain);
        try {
            LogicalNetworkRegistry.register(definition);
        } catch (IllegalArgumentException error) {
            throw new LuaError("logicalNetworks:add: " + error.getMessage());
        }
        return new LogicalNetworkReference(definition);
    }

    private static VarArgFunction lookup(final LuaTable registry, final boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                LuaValue value = argument(arguments, registry);
                LogicalNetworkDefinition definition = LogicalNetworkRegistry.find(key(value, "logicalNetworks"));
                if (definition == null) {
                    if (required) {
                        throw new LuaError("Logical network is not registered: " + value.tojstring());
                    }
                    return NIL;
                }
                return new LogicalNetworkReference(definition);
            }
        };
    }

    private static List<String> strings(LuaValue value, String path, int maximum) {
        if (value.isnil()) {
            return new ArrayList<>();
        }
        int length = LuaDeclarationValues.length(value, path);
        if (length > maximum) {
            throw new LuaError(path + " may contain at most " + maximum + " entries.");
        }
        List<String> result = new ArrayList<>();
        for (int index = 1; index <= length; index++) {
            String entry = value.get(index).checkjstring();
            if (entry.isEmpty() || result.contains(entry)) {
                throw new LuaError(path + " contains an empty or duplicate field.");
            }
            result.add(entry);
        }
        return result;
    }

    private static String optionalField(LuaValue table, String name) {
        LuaValue value = table.get(name);
        return value.isnil() ? null : value.checkjstring();
    }

    private static void requireScalarField(CapabilityDefinition capability, String name, String path,
            boolean required) {
        DataField field = capabilityField(capability, name);
        if (field == null) {
            if (required && name != null) {
                throw new LuaError(path + " references an undeclared capability field: " + name);
            }
            return;
        }
        if (!(field.type == DataField.Type.BOOLEAN || field.type == DataField.Type.INTEGER
                || field.type == DataField.Type.NUMBER || field.type == DataField.Type.STRING
                || field.type == DataField.Type.CONTENT_KEY)) {
            throw new LuaError(path + " must reference a scalar capability field.");
        }
    }

    private static void requireTypedField(CapabilityDefinition capability, String name, String path,
            boolean required, DataField.Type... accepted) {
        if (name == null) {
            return;
        }
        DataField field = capabilityField(capability, name);
        if (field == null) {
            if (required) {
                throw new LuaError(path + " references an undeclared capability field: " + name);
            }
            return;
        }
        for (DataField.Type type : accepted) {
            if (field.type == type) {
                return;
            }
        }
        throw new LuaError(path + " references a capability field with an incompatible type: " + name);
    }

    private static DataField capabilityField(CapabilityDefinition capability, String name) {
        if (name == null) {
            return null;
        }
        DataField config = capability.config.get(name);
        return config == null ? capability.state.get(name) : config;
    }

    private static LuaValue callback(LuaValue value, String path) {
        if (!value.isnil() && !value.isfunction()) {
            throw new LuaError(path + " must be a function.");
        }
        return value;
    }

    private static AssetKey key(LuaValue value, String path) {
        try {
            return AssetKey.parse(value.checkjstring());
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver) {
        return arguments.arg(arguments.arg1() == receiver ? 2 : 1);
    }
}
