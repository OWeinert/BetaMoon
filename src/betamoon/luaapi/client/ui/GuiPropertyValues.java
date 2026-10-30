package betamoon.luaapi.client.ui;

import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;

/**
 * Strict conversion into typed Java properties; does not acquire host or native
 * access.
 */
public final class GuiPropertyValues {
    private GuiPropertyValues() {
    }

    public static Object read(GuiPropertyDescriptor descriptor, LuaValue value) {
        Object candidate = null;
        switch (descriptor.kind) {
            case DATA:
                try {
                    candidate = GuiLuaData.read(value, descriptor.name);
                } catch (LuaError invalid) {
                    throw new GuiProperties.ValueError(invalid.getMessage());
                }
                break;
            case STRING_LIST:
                if (value.istable() && value.length() <= 4096) {
                    List<String> entries = new ArrayList<>();
                    int length = value.length();
                    for (LuaValue key : value.checktable().keys()) {
                        if (!key.isinttype() || key.toint() < 1 || key.toint() > length) {
                            throw new GuiProperties.ValueError(descriptor.name + " requires a dense string list");
                        }
                    }
                    for (int i = 1; i <= length; i++) {
                        if (value.get(i).type() != LuaValue.TSTRING) {
                            throw new GuiProperties.ValueError(descriptor.name + " requires a dense string list");
                        }
                        entries.add(value.get(i).tojstring());
                    }
                    candidate = entries;
                }
                break;
            case NUMBER:
            case INTEGER:
                if (value.type() == LuaValue.TNUMBER) {
                    candidate = value.todouble();
                }
                break;
            case COLOR:
                if (value.type() == LuaValue.TNUMBER) {
                    double number = value.todouble();
                    if (Double.isFinite(number) && number == Math.rint(number) && number >= Integer.MIN_VALUE
                            && number <= 0xFFFFFFFFL) {
                        candidate = (int) (long) number;
                    }
                } else if (value.type() == LuaValue.TSTRING
                        && value.tojstring().matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                    String hex = value.tojstring().substring(1);
                    candidate = (int) Long.parseLong(hex, 16) | (hex.length() == 6 ? 0xFF000000 : 0);
                }
                break;
            case STRING:
                if (value.type() == LuaValue.TSTRING) {
                    candidate = value.tojstring();
                }
                break;
            case BOOLEAN:
                if (value.isboolean()) {
                    candidate = value.toboolean();
                }
                break;
            default:
                throw new IllegalStateException("Unknown property value type");
        }
        if (candidate == null && !(value.isnil() && descriptor.optional)) {
            throw new GuiProperties.ValueError(descriptor.name + " requires " + descriptor.kind.name().toLowerCase());
        }
        try {
            return descriptor.normalize(candidate);
        } catch (IllegalArgumentException invalid) {
            throw new GuiProperties.ValueError(invalid.getMessage());
        }
    }
}
