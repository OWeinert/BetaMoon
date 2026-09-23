package betamoon.tileentity;

import betamoon.BetaMoonCommon;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.tileentity.LuaTileDataAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Authoritative value validation and callback dispatch for one open container. */
public final class ContainerControlRuntime {
    private final ContainerDefinition definition;
    private final LuaTileEntity tile;
    private final EntityPlayer player;
    private final ContainerSession session;
    private final Set<String> disabledCallbacks = new HashSet<String>();
    private boolean active = true;

    public ContainerControlRuntime(ContainerDefinition definition, LuaTileEntity tile, EntityPlayer player) {
        this.definition = definition;
        this.tile = tile;
        this.player = player;
        this.session = new ContainerSession(definition.session);
    }

    public ContainerSession session() {
        requireActive();
        return session;
    }

    public Object value(ContainerControlDefinition control) {
        requireActive();
        if (control.source == ContainerControlDefinition.Source.DATA) {
            return tile.getDataValue(control.field);
        }
        if (control.source == ContainerControlDefinition.Source.SESSION) {
            return session.get(control.field);
        }
        return null;
    }

    public boolean enabled(ContainerControlDefinition control) {
        return active && GuiConditionEvaluator.evaluate(control.enabledWhen, tile::getDataValue, session::get);
    }

    public boolean activate(ContainerControlDefinition control, LuaTable input) {
        requireActive();
        if (!enabled(control)) {
            return false;
        }
        if (control.type == ContainerControlDefinition.Type.ACTION) {
            CallbackResult result = invoke(control, "onActivate", control.onActivate, input);
            return result.success && !"deny".equals(result.result);
        }
        if (control.type == ContainerControlDefinition.Type.TOGGLE) {
            return change(control, Boolean.valueOf(!Boolean.TRUE.equals(value(control))), input, false);
        }
        if (control.type == ContainerControlDefinition.Type.CHOICE) {
            return cycle(control, 1, input);
        }
        return false;
    }

    public boolean cycle(ContainerControlDefinition control, int direction, LuaTable input) {
        Object current = value(control);
        int index = control.choices.indexOf(current);
        if (index < 0) {
            index = 0;
        } else {
            index += direction < 0 ? -1 : 1;
        }
        if (control.wrap) {
            index = (index % control.choices.size() + control.choices.size()) % control.choices.size();
        } else {
            index = Math.max(0, Math.min(control.choices.size() - 1, index));
        }
        return change(control, control.choices.get(index), input, false);
    }

    public boolean changeNumber(ContainerControlDefinition control, double proposed, LuaTable input) {
        return change(control, normalizeNumber(control, proposed), input, false);
    }

    public boolean editText(ContainerControlDefinition control, String value, LuaTable input, boolean commit) {
        requireActive();
        if (!enabled(control)) {
            return false;
        }
        if (value.length() > control.maximumLength) {
            value = value.substring(0, control.maximumLength);
        }
        if (commit) {
            return change(control, value, input, true);
        }
        return invoke(control, "onEdit", control.onEdit, input, LuaValue.valueOf(value)).success;
    }

    public boolean custom(ContainerControlDefinition control, LuaTable input) {
        requireActive();
        if (!enabled(control)) {
            return false;
        }
        CallbackResult result = invoke(control, "onInput", control.onInput, input, input);
        return result.success && !"pass".equals(result.result);
    }

    public void close() {
        if (!active) {
            return;
        }
        ContainerControlDefinition synthetic = new ContainerControlDefinition("close",
                ContainerControlDefinition.Type.ACTION, ContainerControlDefinition.Source.NONE, null,
                0, 0, 0, 0, 0, false, java.util.Collections.emptyList(), null,
                LuaValue.NIL, definition.closeAction, LuaValue.NIL, LuaValue.NIL, LuaValue.NIL, LuaValue.NIL);
        invoke(synthetic, "onClose", definition.closeAction, input("close"));
        active = false;
        session.close();
    }

    public static LuaTable input(String source) {
        LuaTable input = new LuaTable();
        input.set("source", source);
        return input;
    }

    private boolean change(ContainerControlDefinition control, Object proposed, LuaTable input, boolean commit) {
        requireActive();
        if (!enabled(control)) {
            return false;
        }
        Object previous = value(control);
        CallbackResult before = invoke(control, "beforeChange", control.beforeChange, input,
                toLua(proposed), toLua(previous));
        if (!before.success || "deny".equals(before.result)) {
            return false;
        }
        try {
            if (before.value != null) {
                proposed = fromLuaLike(previous, before.value);
            }
            proposed = validate(control, proposed);
        } catch (Throwable error) {
            callbackFailure(control, "beforeChange", error);
            return false;
        }
        apply(control, proposed);
        LuaValue callback = commit && !control.onCommit.isnil() ? control.onCommit : control.onChange;
        String callbackName = commit && !control.onCommit.isnil() ? "onCommit" : "onChange";
        invoke(control, callbackName, callback, input, toLua(proposed), toLua(previous));
        return true;
    }

    private Object validate(ContainerControlDefinition control, Object proposed) {
        if (control.type == ContainerControlDefinition.Type.TOGGLE && !(proposed instanceof Boolean)) {
            throw new LuaError("Toggle values must be boolean.");
        }
        if (control.type == ContainerControlDefinition.Type.NUMBER) {
            if (!(proposed instanceof Number) || !Double.isFinite(((Number) proposed).doubleValue())) {
                throw new LuaError("Number control values must be finite numbers.");
            }
            return normalizeNumber(control, ((Number) proposed).doubleValue());
        }
        if (control.type == ContainerControlDefinition.Type.TEXT) {
            String text = String.valueOf(proposed);
            return text.length() > control.maximumLength ? text.substring(0, control.maximumLength) : text;
        }
        if (control.type == ContainerControlDefinition.Type.CHOICE && !control.choices.contains(proposed)) {
            throw new LuaError("Choice value is not one of the declared values.");
        }
        return proposed;
    }

    private Object normalizeNumber(ContainerControlDefinition control, double proposed) {
        double clamped = Math.max(control.minimum, Math.min(control.maximum, proposed));
        double snapped = control.minimum + Math.round((clamped - control.minimum) / control.step) * control.step;
        snapped = Math.max(control.minimum, Math.min(control.maximum, snapped));
        if (value(control) instanceof Integer) {
            return Integer.valueOf((int) Math.round(snapped));
        }
        return Double.valueOf(snapped);
    }

    private void apply(ContainerControlDefinition control, Object value) {
        if (control.source == ContainerControlDefinition.Source.DATA) {
            tile.setDataValue(control.field, value);
        } else if (control.source == ContainerControlDefinition.Source.SESSION) {
            session.set(control.field, value);
        }
    }

    private CallbackResult invoke(ContainerControlDefinition control, String name, LuaValue callback,
            LuaTable input, LuaValue... arguments) {
        if (callback.isnil() || disabledCallbacks.contains(control.name + ":" + name)) {
            return CallbackResult.pass();
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaValue[] supplied = new LuaValue[arguments.length + 1];
            supplied[0] = context(scope, control, input);
            System.arraycopy(arguments, 0, supplied, 1, arguments.length);
            LuaValue returned = callback.invoke(LuaValue.varargsOf(supplied)).arg1();
            return CallbackResult.parse(returned);
        } catch (Throwable error) {
            callbackFailure(control, name, error);
            return CallbackResult.failure();
        }
    }

    private void callbackFailure(ContainerControlDefinition control, String name, Throwable error) {
        disabledCallbacks.add(control.name + ":" + name);
        String detail = error.getMessage() == null ? error.toString() : error.getMessage();
        String message = "container " + definition.name + " control '" + control.name + "' " + name
                + " was disabled after an error: " + detail;
        LuaScriptErrors.add(definition.owner, message);
        BetaMoonCommon.LOGGER.warning(definition.owner + ": " + message);
    }

    private LuaTable context(final LuaCallbackScope scope, final ContainerControlDefinition control,
            LuaTable input) {
        LuaTable context = new LuaTable();
        context.set("player", player(player));
        LuaTable tileValue = new LuaTable();
        tileValue.set("x", tile.xCoord);
        tileValue.set("y", tile.yCoord);
        tileValue.set("z", tile.zCoord);
        tileValue.set("data", LuaTileDataAccess.create(scope, tile));
        context.set("tile", tileValue);
        context.set("data", tileValue.get("data"));
        context.set("inventory", inventory(scope));
        context.set("session", session(scope));
        LuaTable controlValue = new LuaTable();
        controlValue.set("key", control.name);
        controlValue.set("type", control.type.name().toLowerCase());
        controlValue.set("enabled", LuaValue.valueOf(enabled(control)));
        context.set("control", controlValue);
        context.set("input", input);
        return context;
    }

    private LuaTable session(final LuaCallbackScope scope) {
        final LuaTable access = new LuaTable();
        access.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return toLua(session.get(arguments.arg(arguments.arg1() == access ? 2 : 1).checkjstring()));
            }
        });
        access.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                int offset = arguments.arg1() == access ? 1 : 0;
                session.setLua(arguments.arg(1 + offset).checkjstring(), arguments.arg(2 + offset));
                return NIL;
            }
        });
        return access;
    }

    private LuaTable inventory(final LuaCallbackScope scope) {
        final LuaTable access = new LuaTable();
        access.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                int slot = slot(arguments.arg(arguments.arg1() == access ? 2 : 1).checkjstring());
                return stack(tile.getStackInSlot(slot));
            }
        });
        access.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                int offset = arguments.arg1() == access ? 1 : 0;
                int slot = slot(arguments.arg(1 + offset).checkjstring());
                tile.setInventorySlotContents(slot,
                        LuaApiUtils.readItemStack(arguments.arg(2 + offset), true, "container inventory"));
                return NIL;
            }
        });
        access.set("remove", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                int offset = arguments.arg1() == access ? 1 : 0;
                return stack(tile.decrStackSize(slot(arguments.arg(1 + offset).checkjstring()),
                        arguments.arg(2 + offset).optint(1)));
            }
        });
        return access;
    }

    private int slot(String name) {
        Integer slot = definition.tileEntity.slots.get(name);
        if (slot == null) {
            throw new LuaError("Unknown inventory slot: " + name);
        }
        return slot.intValue();
    }

    private static LuaTable player(EntityPlayer player) {
        LuaTable value = new LuaTable();
        if (player != null) {
            value.set("name", player.username);
        }
        return value;
    }

    private static LuaValue stack(ItemStack stack) {
        if (stack == null) {
            return LuaValue.NIL;
        }
        LuaTable value = new LuaTable();
        value.set("id", stack.itemID);
        value.set("count", stack.stackSize);
        value.set("damage", stack.getItemDamage());
        return value;
    }

    private static LuaValue toLua(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Boolean) {
            return LuaValue.valueOf(((Boolean) value).booleanValue());
        }
        if (value instanceof Integer) {
            return LuaValue.valueOf(((Integer) value).intValue());
        }
        if (value instanceof Number) {
            return LuaValue.valueOf(((Number) value).doubleValue());
        }
        return LuaValue.valueOf(String.valueOf(value));
    }

    private static Object fromLuaLike(Object example, LuaValue value) {
        if (example instanceof Boolean) {
            return Boolean.valueOf(value.checkboolean());
        }
        if (example instanceof Integer) {
            return Integer.valueOf(value.checkint());
        }
        if (example instanceof Number) {
            double number = value.checkdouble();
            if (!Double.isFinite(number)) {
                throw new LuaError("Replacement value must be finite.");
            }
            return Double.valueOf(number);
        }
        return value.checkjstring();
    }

    private void requireActive() {
        if (!active) {
            throw new LuaError("Container control runtime is closed.");
        }
    }

    private static final class CallbackResult {
        private final boolean success;
        private final String result;
        private final LuaValue value;

        private CallbackResult(boolean success, String result, LuaValue value) {
            this.success = success;
            this.result = result;
            this.value = value;
        }

        private static CallbackResult parse(LuaValue value) {
            if (value.isnil()) {
                return pass();
            }
            if (value.istable()) {
                String result = value.get("result").optjstring("pass").toLowerCase();
                validate(result);
                return new CallbackResult(true, result, value.get("value").isnil() ? null : value.get("value"));
            }
            String result = value.checkjstring().toLowerCase();
            validate(result);
            return new CallbackResult(true, result, null);
        }

        private static void validate(String result) {
            if (!("pass".equals(result) || "deny".equals(result) || "handled".equals(result))) {
                throw new LuaError("Callback result must be pass, deny, or handled.");
            }
        }

        private static CallbackResult pass() {
            return new CallbackResult(true, "pass", null);
        }

        private static CallbackResult failure() {
            return new CallbackResult(false, "deny", null);
        }
    }
}
