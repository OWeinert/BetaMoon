package betamoon.luaapi.client.ui;

import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import org.luaj.vm2.LuaValue;

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
