package betamoon.luaapi.event;

import betamoon.client.tick.ClientTickEvents;
import betamoon.client.diagnostics.ClientScriptDiagnostics;
import betamoon.event.Events;
import betamoon.event.api.EventChannel;
import betamoon.event.api.IEventListener;
import betamoon.event.context.EventContext;
import betamoon.event.context.GameEventCtx;
import betamoon.event.context.PlayerEventCtx;
import betamoon.event.context.WorldEventCtx;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptDiagnostics;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.teleport.TeleportRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;
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
        Map<String, EventBinding<?>> bindings = new HashMap<>();
        register(bindings, new EventBinding<>("world_join", Events.WORLD_JOIN, LuaWorldEventCtx::new));
        register(bindings, new EventBinding<>("world_leave", Events.WORLD_LEAVE, LuaWorldEventCtx::new));
        register(bindings, new EventBinding<>("player_join", Events.PLAYER_JOIN, LuaPlayerEventCtx::new));
        register(bindings, new EventBinding<>("player_leave", Events.PLAYER_LEAVE, LuaPlayerEventCtx::new));
        register(bindings, new EventBinding<>("gui_opened", Events.GUI_OPENED, LuaGuiEventCtx::new));
        register(bindings, new EventBinding<>("gui_closed", Events.GUI_CLOSED, LuaGuiEventCtx::new));
        register(bindings, new EventBinding<>("gui_tick", Events.GUI_TICK, LuaGuiEventCtx::new));
        register(bindings, new EventBinding<>("game_tick", Events.GAME_TICK, LuaGameEventCtx::new));
        register(bindings, new EventBinding<>("world_tick", Events.WORLD_TICK, LuaWorldEventCtx::new));
        register(bindings, new EventBinding<>("screen_changed", Events.SCREEN_CHANGED, LuaGuiEventCtx::new));
        register(bindings, new EventBinding<>("key_input", Events.KEY_INPUT, LuaInputEventCtx::new));
        register(bindings, new EventBinding<>("mouse_input", Events.MOUSE_INPUT, LuaInputEventCtx::new));
        register(bindings, new EventBinding<>("block_broken", Events.BLOCK_BROKEN, LuaBlockEventCtx::new));
        register(bindings, new EventBinding<>("block_placed", Events.BLOCK_PLACED, LuaBlockEventCtx::new));
        register(bindings, new EventBinding<>("item_used", Events.ITEM_USE, LuaItemUseEventCtx::new));
        register(bindings, new EventBinding<>("dimension_changed", Events.DIMENSION_CHANGE, LuaDimensionEventCtx::new));
        register(bindings, new EventBinding<>("teleport_completed", Events.TELEPORT_COMPLETED,
                ctx -> LuaDetachedData.copy(ctx.result())));
        register(bindings, new EventBinding<>("teleport_failed", Events.TELEPORT_FAILED,
                ctx -> LuaDetachedData.copy(ctx.result())));
        events.set("on", new On(events, bindings));
        module.set("events", events);
    }

    private static void register(Map<String, EventBinding<?>> bindings, EventBinding<?> binding) {
        bindings.put(binding.name, binding);
    }

    /** Resolves a snake-case event name and subscribes its callback. */
    private static final class On extends VarArgFunction {
        private final LuaTable service;
        private final Map<String, EventBinding<?>> bindings;
        private On(LuaTable service, Map<String, EventBinding<?>> bindings) {
            this.service = service;
            this.bindings = bindings;
        }

        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == service ? 1 : 0;
            String name = args.arg(1 + offset).checkjstring().trim().toLowerCase();
            LuaValue callback = args.arg(2 + offset);
            EventBinding<?> binding = bindings.get(name);
            if (binding == null) {
                throw new LuaError("Unknown event: " + name);
            }
            return binding.subscribe(callback);
        }
    }

    /** Connects one public event name to its exact Java context and Lua view. */
    private static final class EventBinding<TContext extends EventContext> {
        private final String name;
        private final EventChannel<TContext> channel;
        private final Function<TContext, LuaValue> contextFactory;

        private EventBinding(String name, EventChannel<TContext> channel, Function<TContext, LuaValue> contextFactory) {
            this.name = name;
            this.channel = channel;
            this.contextFactory = contextFactory;
        }

        private LuaValue subscribe(LuaValue functionValue) {
            LuaCallbackScope.requireNoBindingMutation();
            if (!functionValue.isfunction()) {
                throw new LuaError("Events: " + name + " listener must be a function.");
            }
            if ("game_tick".equals(name) || "gui_tick".equals(name) || "world_tick".equals(name)) {
                ClientTickEvents.requireAvailable();
            }
            final LuaValue callback = functionValue.checkfunction();
            final String owner = LuaScriptRegistry.getCurrentScriptFile();
            final IEventListener<TContext> listener = ctx -> {
                Minecraft minecraft = ctx == null ? null : ctx.getMinecraft();
                World world = ctx instanceof WorldEventCtx
                        ? ((WorldEventCtx) ctx).getWorld()
                        : ctx instanceof GameEventCtx
                                ? ((GameEventCtx) ctx).getWorld()
                                : ctx instanceof PlayerEventCtx
                                        ? ((PlayerEventCtx) ctx).getWorld()
                                        : minecraft == null ? null : minecraft.theWorld;
                EntityPlayer player = ctx instanceof PlayerEventCtx
                        ? ((PlayerEventCtx) ctx).getPlayer()
                        : minecraft == null ? null : minecraft.thePlayer;
                boolean current = world != null && !world.multiplayerWorld && minecraft != null
                        && minecraft.theWorld == world;
                try (ScriptDiagnostics.Scope diagnostic = ScriptDiagnostics.gameContext(
                        () -> ClientScriptDiagnostics.occurrence(minecraft, world));
                        LuaCallbackScope scope = LuaCallbackScope.withQueuedTeleport(
                                current && !TeleportRuntime.frozen(), current)) {
                    LuaValue view = contextFactory.apply(ctx);
                    if (view.istable() && world != null) {
                        view.set("world", LuaWorldActionAccess.create(scope, world, 0, 0, 0));
                        if (player != null && player.worldObj == world) {
                            view.set("player", LuaEntityActionAccess.create(scope, player, player));
                        }
                    }
                    ScriptExecution.invoke(owner, "events." + name, callback, view);
                } catch (LuaError e) {
                    LuaApiUtils.warnForScript(owner, "Events", name + " listener error: " + e.getMessage(), e);
                }
            };
            channel.subscribe(listener);
            final Subscription<TContext> subscription = new Subscription<>(channel, listener);
            ScriptResourceTracker.track(subscription);
            return subscription.luaHandle();
        }
    }

    /** Owns one listener and exposes explicit, idempotent unsubscription to Lua. */
    private static final class Subscription<TContext extends EventContext> implements ScriptResourceTracker.Cleanup {
        private final EventChannel<TContext> channel;
        private final IEventListener<TContext> listener;
        private boolean active = true;
        private LuaTable handle;

        private Subscription(EventChannel<TContext> channel, IEventListener<TContext> listener) {
            this.channel = channel;
            this.listener = listener;
        }

        private LuaTable luaHandle() {
            handle = new LuaTable();
            handle.set("active", LuaValue.TRUE);
            handle.set("unsubscribe", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaCallbackScope.requireNoBindingMutation();
                    Subscription.this.run();
                    return LuaValue.NIL;
                }
            });
            return handle;
        }

        public void run() {
            if (!active) {
                return;
            }
            active = false;
            channel.unsubscribe(listener);
            if (handle != null) {
                handle.set("active", LuaValue.FALSE);
            }
        }
    }

}
