package betamoon.networking;

import betamoon.assets.AssetKey;
import betamoon.capability.CapabilityDefinition;
import betamoon.data.DataField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.LuaValue;

/** Immutable definition of a capability-backed logical gameplay network. */
public final class LogicalNetworkDefinition {
    public enum Topology {
        ADJACENT,
        WIRELESS,
        EXPLICIT,
        HYBRID
    }

    public final AssetKey key;
    public final String owner;
    public final CapabilityDefinition capability;
    public final Topology topology;
    public final String channelField;
    public final String roleField;
    public final String rangeField;
    public final String enabledField;
    public final String endpointField;
    public final double range;
    public final List<String> compatibilityFields;
    public final int tickInterval;
    public final LuaValue canConnect;
    public final LuaValue onTick;
    public final LuaValue onPulse;
    public final DataField signalValue;
    public final String signalMode;
    public final String aggregate;
    public final boolean retainWithoutTransmitters;

    public LogicalNetworkDefinition(AssetKey key, String owner, CapabilityDefinition capability, Topology topology,
            String channelField, String roleField, String rangeField, String enabledField, String endpointField,
            double range, List<String> compatibilityFields, int tickInterval, LuaValue canConnect,
            LuaValue onTick, LuaValue onPulse, DataField signalValue, String signalMode, String aggregate,
            boolean retainWithoutTransmitters) {
        this.key = key;
        this.owner = owner;
        this.capability = capability;
        this.topology = topology;
        this.channelField = channelField;
        this.roleField = roleField;
        this.rangeField = rangeField;
        this.enabledField = enabledField;
        this.endpointField = endpointField;
        this.range = range;
        this.compatibilityFields = Collections.unmodifiableList(new ArrayList<>(compatibilityFields));
        this.tickInterval = tickInterval;
        this.canConnect = canConnect;
        this.onTick = onTick;
        this.onPulse = onPulse;
        this.signalValue = signalValue;
        this.signalMode = signalMode;
        this.aggregate = aggregate;
        this.retainWithoutTransmitters = retainWithoutTransmitters;
    }

    public boolean topologyDependsOn(String field) {
        return field != null && (field.equals(channelField) || field.equals(roleField) || field.equals(rangeField)
                || field.equals(enabledField) || field.equals(endpointField) || compatibilityFields.contains(field));
    }
}
