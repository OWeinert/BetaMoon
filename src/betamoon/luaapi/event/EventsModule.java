package betamoon.luaapi.event;

import betamoon.event.Events;
import betamoon.event.context.BlockEventCtx;
import betamoon.event.context.DimensionEventCtx;
import betamoon.event.context.GameEventCtx;
import betamoon.event.context.GuiEventCtx;
import betamoon.event.context.InputEventCtx;
import betamoon.event.context.ItemUseEventCtx;
import betamoon.event.context.PlayerEventCtx;
import betamoon.event.context.WorldEventCtx;
import betamoon.luaapi.LuaApiUtils;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class EventsModule {
    private EventsModule() {
    }

    public static void attach(LuaTable module) {
        LuaTable events = new LuaTable();
        events.set("onWorldJoin", new Subscribe(Events.WORLD_JOIN));
        events.set("onWorldLeave", new Subscribe(Events.WORLD_LEAVE));
        events.set("onPlayerJoin", new Subscribe(Events.PLAYER_JOIN));
        events.set("onPlayerLeave", new Subscribe(Events.PLAYER_LEAVE));
        events.set("onGuiOpened", new Subscribe(Events.GUI_OPENED));
        events.set("onGuiClosed", new Subscribe(Events.GUI_CLOSED));
        events.set("onGuiTick", new Subscribe(Events.GUI_TICK));
        events.set("onGameTick", new Subscribe(Events.GAME_TICK));
        events.set("onScreenChanged", new Subscribe(Events.SCREEN_CHANGED));
        events.set("onKeyInput", new Subscribe(Events.KEY_INPUT));
        events.set("onMouseInput", new Subscribe(Events.MOUSE_INPUT));
        events.set("onBlockBroken", new Subscribe(Events.BLOCK_BREAK_ATTEMPT));
        events.set("onBlockPlaced", new Subscribe(Events.BLOCK_PLACE_ATTEMPT));
        events.set("onItemUsed", new Subscribe(Events.ITEM_USE));
        events.set("onDimensionChanged", new Subscribe(Events.DIMENSION_CHANGE));
        module.set("events", events);
    }

    private static final class Subscribe<TContext extends betamoon.event.context.EventContext> extends VarArgFunction {
        private final betamoon.event.api.EventChannel<TContext> channel;
        private final String eventName;

        private Subscribe(betamoon.event.api.EventChannel<TContext> channel) {
            this.channel = channel;
            this.eventName = resolveEventName(channel);
        }

        public Varargs invoke(Varargs args) {
            LuaValue functionValue = LuaApiUtils.getVarArg(args, 1);
            if (!functionValue.isfunction()) {
                throw new LuaError("Events: " + eventName + " listener must be a function.");
            }
            final LuaValue callback = functionValue.checkfunction();
            channel.subscribe(ctx -> {
                try {
                    LuaValue contextValue = null;
                    if (ctx instanceof GuiEventCtx) {
                        contextValue = new LuaGuiEventCtx((GuiEventCtx) ctx);
                    } else if (ctx instanceof WorldEventCtx) {
                        contextValue = new LuaWorldEventCtx((WorldEventCtx) ctx);
                    } else if (ctx instanceof GameEventCtx) {
                        contextValue = new LuaGameEventCtx((GameEventCtx) ctx);
                    } else if (ctx instanceof PlayerEventCtx) {
                        contextValue = new LuaPlayerEventCtx((PlayerEventCtx) ctx);
                    } else if (ctx instanceof InputEventCtx) {
                        contextValue = new LuaInputEventCtx((InputEventCtx) ctx);
                    } else if (ctx instanceof BlockEventCtx) {
                        contextValue = new LuaBlockEventCtx((BlockEventCtx) ctx);
                    } else if (ctx instanceof ItemUseEventCtx) {
                        contextValue = new LuaItemUseEventCtx((ItemUseEventCtx) ctx);
                    } else if (ctx instanceof DimensionEventCtx) {
                        contextValue = new LuaDimensionEventCtx((DimensionEventCtx) ctx);
                    }
                    if (contextValue == null) {
                        throw new LuaError("Events: context expected but none is provided.");
                    }
                    callback.call(contextValue);
                } catch (LuaError e) {
                    LuaApiUtils.warn("Events", eventName + " listener error: " + e.getMessage());
                }
            });
            return LuaValue.NIL;
        }
    }

    private static String resolveEventName(betamoon.event.api.EventChannel<?> channel) {
        Type type = channel.getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) type).getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class) {
                return ((Class<?>) args[0]).getSimpleName();
            }
        }
        return channel.getClass().getSimpleName();
    }

}
