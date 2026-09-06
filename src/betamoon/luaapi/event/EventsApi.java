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
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.event.api.IEventListener;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class EventsApi {
    private EventsApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable events = new LuaTable();
        Map subscriptions = new HashMap();
        subscriptions.put("world_join", new Subscribe(Events.WORLD_JOIN));
        subscriptions.put("world_leave", new Subscribe(Events.WORLD_LEAVE));
        subscriptions.put("player_join", new Subscribe(Events.PLAYER_JOIN));
        subscriptions.put("player_leave", new Subscribe(Events.PLAYER_LEAVE));
        subscriptions.put("gui_opened", new Subscribe(Events.GUI_OPENED));
        subscriptions.put("gui_closed", new Subscribe(Events.GUI_CLOSED));
        subscriptions.put("gui_tick", new Subscribe(Events.GUI_TICK));
        subscriptions.put("game_tick", new Subscribe(Events.GAME_TICK));
        subscriptions.put("screen_changed", new Subscribe(Events.SCREEN_CHANGED));
        subscriptions.put("key_input", new Subscribe(Events.KEY_INPUT));
        subscriptions.put("mouse_input", new Subscribe(Events.MOUSE_INPUT));
        subscriptions.put("block_broken", new Subscribe(Events.BLOCK_BROKEN));
        subscriptions.put("block_placed", new Subscribe(Events.BLOCK_PLACED));
        subscriptions.put("item_used", new Subscribe(Events.ITEM_USE));
        subscriptions.put("dimension_changed", new Subscribe(Events.DIMENSION_CHANGE));
        events.set("on", new On(events, subscriptions));
        module.set("events", events);
    }

    /** Resolves a snake-case event name and subscribes its callback. */
    private static final class On extends VarArgFunction {
        private final LuaTable service;
        private final Map subscriptions;
        private On(LuaTable service, Map subscriptions) {
            this.service = service;
            this.subscriptions = subscriptions;
        }
        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == service ? 1 : 0;
            String name = args.arg(1 + offset).checkjstring().trim().toLowerCase();
            LuaValue callback = args.arg(2 + offset);
            Subscribe subscription = (Subscribe) subscriptions.get(name);
            if (subscription == null) throw new LuaError("Unknown event: " + name);
            return subscription.invoke(callback);
        }
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
            final IEventListener<TContext> listener = ctx -> {
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
            };
            channel.subscribe(listener);
            final Subscription<TContext> subscription = new Subscription<TContext>(channel, listener);
            ScriptResourceTracker.track(subscription);
            return subscription.luaHandle();
        }
    }

    /** Owns one listener and exposes explicit, idempotent unsubscription to Lua. */
    private static final class Subscription<TContext extends betamoon.event.context.EventContext>
        implements ScriptResourceTracker.Cleanup {
        private final betamoon.event.api.EventChannel<TContext> channel;
        private final IEventListener<TContext> listener;
        private boolean active = true;
        private LuaTable handle;

        private Subscription(betamoon.event.api.EventChannel<TContext> channel,
                             IEventListener<TContext> listener) {
            this.channel = channel;
            this.listener = listener;
        }

        private LuaTable luaHandle() {
            handle = new LuaTable();
            handle.set("active", LuaValue.TRUE);
            handle.set("unsubscribe", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    Subscription.this.run();
                    return LuaValue.NIL;
                }
            });
            return handle;
        }

        public void run() {
            if (!active) return;
            active = false;
            channel.unsubscribe(listener);
            if (handle != null) handle.set("active", LuaValue.FALSE);
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
