package betamoon.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Saved logical equipment slots; rendering and combat effects remain script-controlled. */
public final class EntityEquipmentDefinition {
    public enum Slot {
        HAND("hand"),
        HEAD("head"),
        CHEST("chest"),
        LEGS("legs"),
        FEET("feet");

        public final String luaName;

        Slot(String luaName) {
            this.luaName = luaName;
        }

        public static Slot parse(String value, String path) {
            for (Slot slot : values()) {
                if (slot.luaName.equals(value)) {
                    return slot;
                }
            }
            throw error(path, "expected hand, head, chest, legs, or feet");
        }
    }

    public final Set<Slot> slots;
    public final boolean dropOnDeath;

    public EntityEquipmentDefinition(LuaValue value) {
        fields(value, "entity.equipment", "slots", "dropOnDeath");
        LuaValue slotValue = value.get("slots");
        EnumSet<Slot> parsed = EnumSet.noneOf(Slot.class);
        if (slotValue.isnil()) {
            parsed.addAll(EnumSet.allOf(Slot.class));
        } else {
            LuaTable table = slotValue.checktable();
            for (int index = 1; index <= table.length(); index++) {
                String path = "entity.equipment.slots[" + index + "]";
                if (!parsed.add(Slot.parse(string(table.get(index), path), path))) {
                    throw error(path, "duplicate equipment slot");
                }
            }
            if (parsed.isEmpty() || table.keys().length != table.length()) {
                throw error("entity.equipment.slots", "expected a non-empty array");
            }
        }
        slots = Collections.unmodifiableSet(parsed);
        dropOnDeath = bool(value.get("dropOnDeath"), "entity.equipment.dropOnDeath", false);
    }
}
