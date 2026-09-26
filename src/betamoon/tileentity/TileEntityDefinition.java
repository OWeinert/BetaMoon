package betamoon.tileentity;

import betamoon.assets.AssetKey;
import betamoon.capability.CapabilityAttachmentDefinition;
import betamoon.data.DataField;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/** Immutable structural definition used by Lua-backed tile entity instances. */
public final class TileEntityDefinition {
    public final String name;
    public final String owner;
    public final String inventoryName;
    public final Map<String, Integer> slots;
    public final Map<String, Field> fields;
    public final Map<AssetKey, CapabilityAttachmentDefinition> capabilities;
    public final LuaValue tickAction;
    public final LuaValue inventoryChangedAction;
    public final int initialTickDelay;
    public final int repeatTickDelay;
    public final boolean randomTicks;
    public final double randomTickChance;

    public TileEntityDefinition(String name, String owner, String inventoryName, Map<String, Integer> slots,
            Map<String, Field> fields, LuaValue tickAction, LuaValue inventoryChangedAction, int initialTickDelay,
            int repeatTickDelay, boolean randomTicks, double randomTickChance) {
        this(name, owner, inventoryName, slots, fields, Collections.<CapabilityAttachmentDefinition>emptyList(),
                tickAction, inventoryChangedAction, initialTickDelay, repeatTickDelay, randomTicks, randomTickChance);
    }

    public TileEntityDefinition(String name, String owner, String inventoryName, Map<String, Integer> slots,
            Map<String, Field> fields, List<CapabilityAttachmentDefinition> capabilities, LuaValue tickAction,
            LuaValue inventoryChangedAction, int initialTickDelay, int repeatTickDelay, boolean randomTicks,
            double randomTickChance) {
        this.name = name;
        this.owner = owner;
        this.inventoryName = inventoryName;
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        Map<AssetKey, CapabilityAttachmentDefinition> attachments = new LinkedHashMap<>();
        for (CapabilityAttachmentDefinition capability : capabilities) {
            attachments.put(capability.capability.key, capability);
        }
        this.capabilities = Collections.unmodifiableMap(attachments);
        this.tickAction = tickAction;
        this.inventoryChangedAction = inventoryChangedAction;
        this.initialTickDelay = initialTickDelay;
        this.repeatTickDelay = repeatTickDelay;
        this.randomTicks = randomTicks;
        this.randomTickChance = randomTickChance;
    }

    /** Description of one typed, persistent value. */
    public static final class Field {
        public final String name;
        public final DataField schema;
        public final Object defaultValue;
        public final boolean sync;

        public Field(String name, DataField schema, boolean sync) {
            this.name = name;
            this.schema = schema;
            this.defaultValue = schema.defaultValue;
            this.sync = sync;
        }

        /** Compatibility constructor retained for internal fixtures and integrations. */
        public Field(String name, TileDataType type, Object defaultValue, boolean sync) {
            this(name, type.schema(name, defaultValue), sync);
        }
    }
}
