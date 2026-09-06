package betamoon.tileentity;

import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/** Immutable structural definition used by Lua-backed tile entity instances. */
public final class TileEntityDefinition {
    public final String name;
    public final String owner;
    public final String inventoryName;
    public final Map slots;
    public final Map fields;
    public final LuaValue tickAction;
    public final LuaValue inventoryChangedAction;
    public final int initialTickDelay;
    public final int repeatTickDelay;
    public final boolean randomTicks;
    public final double randomTickChance;

    public TileEntityDefinition(String name, String owner, String inventoryName, Map slots,
        Map fields, LuaValue tickAction, LuaValue inventoryChangedAction, int initialTickDelay,
        int repeatTickDelay, boolean randomTicks, double randomTickChance) {
        this.name = name;
        this.owner = owner;
        this.inventoryName = inventoryName;
        this.slots = new LinkedHashMap(slots);
        this.fields = new LinkedHashMap(fields);
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
        public final String type;
        public final Object defaultValue;
        public final boolean sync;

        public Field(String name, String type, Object defaultValue, boolean sync) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.sync = sync;
        }
    }
}
