package betamoon.luaapi.world;

import betamoon.dimension.DimensionIdentity;
import betamoon.dimension.DimensionKey;
import betamoon.dimension.DimensionQueries;
import betamoon.dimension.DimensionSession;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDataSnapshot;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.luaapi.utils.LuaPositionArguments;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.teleport.ArrivalSearch;
import betamoon.teleport.TeleportLocation;
import betamoon.teleport.TeleportOptions;
import betamoon.teleport.TeleportOwner;
import betamoon.teleport.TeleportRuntime;
import betamoon.teleport.TeleportService;
import betamoon.teleport.TeleportTicket;
import betamoon.worldgen.spatial.GenerationValues;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Scoped acceptance, detached tickets and pure navigation queries; native
 * execution is always queued.
 */
public final class TeleportApi {
    private TeleportApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable api = new LuaTable();
        Map<String, TeleportOwner> owners = new HashMap<String, TeleportOwner>();
        module.set("teleport", api);
        for (String method : new String[]{"request", "returnToPrevious"}) {
            boolean returning = method.equals("returnToPrevious");
            api.set(method, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    EntityPlayer player = LuaEntityActionAccess.requireTeleportPlayer(argument(args, api, 1),
                            "teleport." + method);
                    DimensionKey target = returning ? null : DimensionsApi.key(argument(args, api, 2));
                    LuaValue declaration = argument(args, api, returning ? 2 : 3);
                    LuaTable filtered = withoutCallback(declaration);
                    TeleportOptions options;
                    try {
                        options = new TeleportOptions(
                                LuaDataSnapshot.object(filtered, "teleport.options", Double.MAX_VALUE), returning);
                    } catch (IllegalArgumentException exception) {
                        throw new LuaError(exception.getMessage());
                    }
                    TeleportOwner owner = owner(owners);
                    LuaValue callback = declaration.isnil() ? NIL : declaration.get("onComplete");
                    TeleportService.Completion complete = callback.isnil() ? null : completion(owner, callback);
                    try {
                        TeleportService service = TeleportRuntime.service();
                        TeleportService.Acceptance accepted = returning
                                ? service.returnToPrevious(player, owner, options, complete)
                                : service.request(player, owner, target, options, complete);
                        return accepted.ticket == null ? failure(accepted.reason) : ticket(accepted.ticket);
                    } catch (IllegalStateException exception) {
                        return failure("unsupported_environment");
                    }
                }
            });
        }
        for (String method : new String[]{"getReturnPoint", "clearReturnPoints"}) {
            api.set(method, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    EntityPlayer player = method.equals("clearReturnPoints")
                            ? LuaEntityActionAccess.requireTeleportPlayer(argument(args, api, 1), "teleport." + method)
                            : LuaEntityActionAccess.requirePlayer(argument(args, api, 1), false, "teleport." + method);
                    TeleportOwner owner = owner(owners);
                    if (!owner.active()) {
                        return failure("owner_unloaded");
                    }
                    String channel = argument(args, api, 2).optjstring("default");
                    if (!channel.matches("[a-zA-Z0-9_.:-]{1,64}")) {
                        throw new LuaError("teleport.channel: expected 1..64 identifier characters");
                    }
                    try {
                        DimensionSession session = DimensionQueries.session(player.worldObj);
                        if (method.equals("clearReturnPoints")) {
                            if (TeleportRuntime.busy()) {
                                return failure("busy");
                            }
                            session.navigation.clear(owner.name, channel);
                            return NONE;
                        }
                        NBTTagCompound previous = session.navigation.previous(owner.name, channel);
                        if (previous == null) {
                            return NIL;
                        }
                        Map<String, Object> point = TeleportLocation.read(previous.getCompoundTag("Location"))
                                .describe();
                        point.put("id", previous.getString("Id"));
                        point.put("channel", channel);
                        return LuaDetachedData.copy(point);
                    } catch (IOException exception) {
                        return failure("save_failed");
                    }
                }
            });
        }
        api.set("mapCoordinates", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                DimensionKey source = DimensionsApi.key(argument(args, api, 1));
                DimensionKey target = DimensionsApi.key(argument(args, api, 2));
                LuaPositionArguments.Position position = LuaPositionArguments.vector(args, api, 3,
                        "mapCoordinates");
                try {
                    TeleportLocation location = new TeleportLocation(source, position.x, position.y, position.z,
                            0, 0);
                    return LuaDetachedData.copy(TeleportService
                            .mapCoordinates(location, target, DimensionsApi.coordinateScale(argument(args, api, 1)),
                                    DimensionsApi.coordinateScale(argument(args, api, 2)))
                            .describe());
                } catch (IllegalArgumentException exception) {
                    return failure(
                            exception.getMessage().equals("out_of_bounds") ? "out_of_bounds" : "unknown_dimension");
                }
            }
        });
        api.set("inspectArrival", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                World world = LuaWorldActionAccess.requireWorld(argument(args, api, 1));
                LuaPositionArguments.Position position = LuaPositionArguments.vector(args, api, 2,
                        "inspectArrival");
                double x = position.x;
                double y = position.y;
                double z = position.z;
                LuaValue raw = argument(args, api, position.nextArgument);
                Map<String, Object> settings = raw.isnil()
                        ? new HashMap<String, Object>()
                        : LuaDataSnapshot.object(raw, "teleport.inspectArrival", Double.MAX_VALUE);
                try {
                    GenerationValues.fields(settings, "teleport.inspectArrival", "type", "safety", "searchRadius",
                            "verticalRange");
                    String type = GenerationValues.string(settings, "type", "exact", "inspectArrival");
                    settings.remove("type");
                    if (!type.equals("exact") && !type.equals("near")) {
                        throw new LuaError("inspectArrival.type: expected exact or near");
                    }
                    Map<String, Object> arrival = new HashMap<String, Object>();
                    arrival.put("type", type);
                    arrival.put("x", x);
                    arrival.put("y", y);
                    arrival.put("z", z);
                    settings.put("arrival", arrival);
                    settings.put("loadChunks", false);
                    TeleportOptions options = new TeleportOptions(settings, false);
                    EntityPlayer player = world.playerEntities.isEmpty()
                            ? null
                            : (EntityPlayer) world.playerEntities.get(0);
                    if (player == null) {
                        return failure("invalid_player");
                    }
                    TeleportLocation preferred = options.at(DimensionIdentity.key(world), x, y, z,
                            TeleportLocation.capture(player));
                    ArrivalSearch search = new ArrivalSearch(world, options, preferred, type.equals("exact"));
                    if (!search.step(player)) {
                        return failure("budget_exhausted");
                    }
                    return search.arrival() == null
                            ? failure(search.failure())
                            : LuaDetachedData.copy(search.arrival().describe());
                } catch (IllegalArgumentException exception) {
                    throw new LuaError(exception.getMessage());
                }
            }
        });
    }

    private static LuaTable withoutCallback(LuaValue value) {
        LuaTable copy = new LuaTable();
        if (value.isnil()) {
            return copy;
        }
        LuaTable original = value.checktable();
        for (LuaValue key : original.keys()) {
            if (key.isstring() && key.tojstring().equals("onComplete")) {
                if (!original.get(key).isfunction()) {
                    throw new LuaError("teleport.onComplete: expected a function");
                }
            } else {
                copy.rawset(key, original.rawget(key));
            }
        }
        return copy;
    }

    private static TeleportOwner owner(Map<String, TeleportOwner> owners) {
        String name = LuaScriptRegistry.getCurrentScriptFile();
        if (name == null) {
            throw new LuaError("Teleportation requires a script callback owner");
        }
        TeleportOwner token = owners.get(name);
        if (token == null) {
            token = new TeleportOwner(name);
            owners.put(name, token);
            TeleportOwner owned = token;
            ScriptResourceTracker.track(owned::close);
        }
        return token;
    }

    private static TeleportService.Completion completion(TeleportOwner owner, LuaValue callback) {
        return (result, world, player) -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(
                    world != null && player != null && !TeleportRuntime.frozen())) {
                LuaTable view = LuaDetachedData.copy(result).checktable();
                if (world != null && player != null) {
                    view.set("world", LuaWorldActionAccess.create(scope, world, 0, 0, 0));
                    view.set("player", LuaEntityActionAccess.create(scope, player, player));
                }
                ScriptExecution.invoke(owner.name, "teleport.onComplete", callback, view);
            } catch (LuaError exception) {
                LuaApiUtils.warnForScript(owner.name, "Teleport", "onComplete error: " + exception.getMessage());
            }
        };
    }

    private static LuaTable ticket(TeleportTicket ticket) {
        LuaTable handle = new LuaTable();
        handle.set("id", ticket.id);
        for (String method : new String[]{"getState", "getResult", "cancel"}) {
            handle.set(method, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    if (method.equals("cancel")) {
                        return valueOf(ticket.cancel());
                    }
                    if (method.equals("getResult")) {
                        return LuaDetachedData.copy(ticket.result());
                    }
                    return varargsOf(new LuaValue[]{valueOf(ticket.state()), LuaDetachedData.copy(ticket.progress())});
                }
            });
        }
        return handle;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static Varargs failure(String reason) {
        return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(reason)});
    }
}
