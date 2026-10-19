package betamoon.gui.animation;

import org.luaj.vm2.LuaError;

/** Value failures may recover when observed inputs change; schema errors remain ordinary Lua errors. */
public class GuiAnimationValueError extends LuaError {
    public GuiAnimationValueError(String message) {
        super(message);
    }
}
