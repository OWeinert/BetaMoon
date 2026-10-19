package betamoon.luaapi.input;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.PlayerControllerRuntime;
import betamoon.client.control.input.PlayerControllerRuntime.Activation;
import betamoon.client.control.input.PlayerControllerRuntime.Decision;
import betamoon.client.control.input.PlayerControllerRuntime.Facing;
import betamoon.client.control.input.PlayerControllerRuntime.Movement;
import betamoon.client.control.input.PlayerControllerRuntime.Registration;
import betamoon.client.control.input.PlayerControllerRuntime.Snapshot;
import betamoon.client.control.input.PlayerControllerRuntime.Stage;
import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaContentNamespaces;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Declaration and callback facade for staged local-player controllers. */
final class LuaControllerApi {
    private LuaControllerApi() {
    }

    static void attach(LuaTable input) {
        LuaTable controllers = new LuaTable();
        controllers.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String owner = LuaScriptRegistry.getCurrentScriptFile();
                if (owner == null) {
                    throw new LuaError("Input controllers may only be declared by a Lua script.");
                }
                LuaTable declaration = InputApi.table(argument(args, controllers, 1), "controller");
                InputApi.fields(declaration, "controller", "key", "stages");
                ContentKey key = key(declaration.get("key"), true);
                LuaTable stages = InputApi.table(declaration.get("stages"), "controller stages");
                EnumMap<Stage, PlayerControllerRuntime.Mode> modes = new EnumMap<Stage, PlayerControllerRuntime.Mode>(
                        Stage.class);
                for (LuaValue stageName : stages.keys()) {
                    Stage stage = Stage.parse(stageName.checkjstring());
                    modes.put(stage, PlayerControllerRuntime.Mode.parse(stages.get(stageName).checkjstring()));
                }
                try {
                    Registration registration = ClientInputRuntime.controllers()
                            .register(new PlayerControllerRuntime.Definition(key, modes), owner);
                    ScriptResourceTracker.track(registration::close);
                    return new ControllerHandle(registration);
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("Input controller: " + error.getMessage());
                }
            }
        });
        controllers.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                Registration found = ClientInputRuntime.controllers().get(key(argument(args, controllers, 1), false));
                return found == null ? NIL : new ControllerHandle(found);
            }
        });
        controllers.set("getRequired", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                Registration found = ClientInputRuntime.controllers().get(key(argument(args, controllers, 1), false));
                if (found == null) {
                    throw new LuaError("Input controller was not found");
                }
                return new ControllerHandle(found);
            }
        });
        input.set("controllers", controllers);
    }

    private static ContentKey key(LuaValue value, boolean declaration) {
        try {
            ContentKey result = ContentKey.parseForType(value.checkjstring(), ContentType.INPUT_CONTROLLER);
            if (declaration) {
                LuaContentNamespaces.requireDeclarationNamespace(result);
            }
            return result;
        } catch (IllegalArgumentException error) {
            throw new LuaError("Input controller key: " + error.getMessage());
        }
    }

    private static LuaValue argument(Varargs args, LuaTable receiver, int index) {
        return LuaInputMap.argument(args, receiver, index);
    }

    private static final class ControllerHandle extends LuaTable {
        private final Registration registration;

        private ControllerHandle(Registration registration) {
            this.registration = registration;
            set("key", valueOf(registration.definition().key.toString()));
            set("on", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    if (!registration.owner().equals(LuaScriptRegistry.getCurrentScriptFile())) {
                        throw new LuaError("Only the declaring script can change controller callbacks.");
                    }
                    Stage stage = Stage.parse(argument(args, ControllerHandle.this, 1).checkjstring());
                    LuaValue function = argument(args, ControllerHandle.this, 2);
                    try {
                        registration.on(stage,
                                function.isnil()
                                        ? null
                                        : snapshot -> invokeCallback(registration.owner(), function.checkfunction(),
                                                snapshot, registration.definition().mode(stage)));
                        return ControllerHandle.this;
                    } catch (IllegalArgumentException | IllegalStateException error) {
                        throw new LuaError("Input controller callback: " + error.getMessage());
                    }
                }
            });
            set("activate", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaTable options = InputApi.table(argument(args, ControllerHandle.this, 1),
                            "controller activation");
                    InputApi.fields(options, "controller activation", "player", "priority");
                    Minecraft minecraft = ModLoader.getMinecraftInstance();
                    if (minecraft == null || minecraft.theWorld == null) {
                        throw new LuaError("Controller activation requires a local world.");
                    }
                    LuaValue requestedPlayer = options.get("player");
                    Entity entity = requestedPlayer.isnil()
                            ? minecraft.thePlayer
                            : LuaEntityActionAccess.target(requestedPlayer, minecraft.theWorld,
                                    "controller activation player");
                    if (!(entity instanceof EntityPlayerSP) || entity != minecraft.thePlayer) {
                        throw new LuaError("Controller activation requires the local player.");
                    }
                    int priority = InputApi.priority(options.get("priority"), 0, "controller priority");
                    try {
                        Activation activation = registration.activate((EntityPlayerSP) entity, priority);
                        ScriptResourceTracker.track(activation::close);
                        return new ActivationHandle(activation, (EntityPlayerSP) entity);
                    } catch (IllegalArgumentException | IllegalStateException error) {
                        throw new LuaError("Controller activation: " + error.getMessage());
                    }
                }
            });
            set("isActive", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return valueOf(registration.isActive());
                }
            });
            set("close", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    if (!registration.owner().equals(LuaScriptRegistry.getCurrentScriptFile())) {
                        throw new LuaError("Only the declaring script can close a controller registration.");
                    }
                    registration.close();
                    return NIL;
                }
            });
        }
    }

    private static final class ActivationHandle extends LuaTable {
        private ActivationHandle(Activation activation, EntityPlayerSP player) {
            set("submitIntent", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaTable intent = InputApi.table(argument(args, ActivationHandle.this, 1), "controller intent");
                    InputApi.fields(intent, "controller intent", "movement", "facing", "action");
                    Movement movement = intent.get("movement").isnil() ? null : movement(intent.get("movement"));
                    Facing facing = intent.get("facing").isnil() ? null : facing(intent.get("facing"));
                    ActionIntent action = action(intent.get("action"));
                    try {
                        activation.submit(movement, facing, action == null ? null : action.kind,
                                action == null ? null : action.pass);
                        return ActivationHandle.this;
                    } catch (IllegalArgumentException | IllegalStateException error) {
                        throw new LuaError("Controller intent: " + error.getMessage());
                    }
                }
            });
            set("face", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaTable point = InputApi.table(argument(args, ActivationHandle.this, 1), "face point");
                    InputApi.fields(point, "face point", "x", "y", "z");
                    double x = finite(point.get("x"), "face x") - player.posX;
                    double y = finite(point.get("y"), "face y") - (player.posY + player.getEyeHeight());
                    double z = finite(point.get("z"), "face z") - player.posZ;
                    double horizontal = Math.sqrt(x * x + z * z);
                    if (horizontal < 0.000001 && Math.abs(y) < 0.000001) {
                        return FALSE;
                    }
                    float yaw = (float) (Math.atan2(z, x) * 180 / Math.PI - 90);
                    float pitch = (float) (-Math.atan2(y, horizontal) * 180 / Math.PI);
                    activation.face(new Facing(yaw, pitch));
                    return TRUE;
                }
            });
            set("snapshot", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaTable result = new LuaTable();
                    result.set("active", valueOf(activation.isActive()));
                    result.set("priority", valueOf(activation.priority()));
                    result.set("tick", valueOf(activation.tick()));
                    LuaTable stages = new LuaTable();
                    for (Stage stage : Stage.values()) {
                        stages.set(stage.luaName(), valueOf(activation.definition().mode(stage).luaName()));
                    }
                    result.set("stages", stages);
                    return result;
                }
            });
            set("isActive", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return valueOf(activation.isActive());
                }
            });
            set("close", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    activation.close();
                    return NIL;
                }
            });
        }
    }

    private static Decision invokeCallback(String owner, LuaValue callback, Snapshot snapshot,
            PlayerControllerRuntime.Mode mode) {
        try (LuaCallbackScope scope = LuaCallbackScope.withQueuedTeleport(false, true)) {
            LuaTable event = new LuaTable();
            event.set("stage", LuaValue.valueOf(snapshot.stage.luaName()));
            event.set("tick", LuaValue.valueOf(snapshot.tick));
            event.set("player", LuaEntityActionAccess.create(scope, snapshot.player, snapshot.player));
            event.set("world", LuaWorldActionAccess.create(scope, snapshot.player.worldObj, 0, 0, 0));
            if (snapshot.nativeMovement != null) {
                LuaTable movement = new LuaTable();
                movement.set("forward", LuaValue.valueOf(snapshot.nativeMovement.forward));
                movement.set("strafe", LuaValue.valueOf(snapshot.nativeMovement.strafe));
                movement.set("jump", LuaValue.valueOf(snapshot.nativeMovement.jump));
                movement.set("sneak", LuaValue.valueOf(snapshot.nativeMovement.sneak));
                event.set("nativeIntent", movement);
            }
            if (snapshot.nativeFacing != null) {
                LuaTable facing = new LuaTable();
                facing.set("yaw", LuaValue.valueOf(snapshot.nativeFacing.yaw));
                facing.set("pitch", LuaValue.valueOf(snapshot.nativeFacing.pitch));
                event.set("nativeFacing", facing);
            }
            if (snapshot.action != null) {
                event.set("nativeAction", LuaValue.valueOf(snapshot.action));
                event.set("button", LuaValue.valueOf(snapshot.button));
                event.set("held", LuaValue.valueOf(snapshot.held));
            }
            LuaValue returned = ScriptExecution.invoke(owner, callback, event).arg1();
            if (returned.isnil()) {
                return mode == PlayerControllerRuntime.Mode.MANUAL ? Decision.DENY : Decision.PASS;
            }
            if (returned.isstring()) {
                String result = returned.checkjstring();
                if ("pass".equals(result)) {
                    return Decision.PASS;
                }
                if ("deny".equals(result)) {
                    return Decision.DENY;
                }
                throw new LuaError("Controller callback must return 'pass', 'deny', or an intent table.");
            }
            LuaTable result = InputApi.table(returned, "controller result");
            InputApi.fields(result, "controller result", "movement", "facing", "pass");
            Movement movement = result.get("movement").isnil() ? null : movement(result.get("movement"));
            Facing facing = result.get("facing").isnil() ? null : facing(result.get("facing"));
            boolean pass = result.get("pass").optboolean(false);
            return new Decision(movement, facing, pass);
        }
    }

    private static Movement movement(LuaValue value) {
        LuaTable table = InputApi.table(value, "controller movement");
        InputApi.fields(table, "controller movement", "forward", "strafe", "jump", "sneak");
        return new Movement((float) finite(table.get("forward"), "forward"),
                (float) finite(table.get("strafe"), "strafe"), table.get("jump").optboolean(false),
                table.get("sneak").optboolean(false));
    }

    private static Facing facing(LuaValue value) {
        LuaTable table = InputApi.table(value, "controller facing");
        InputApi.fields(table, "controller facing", "yaw", "pitch");
        return new Facing((float) finite(table.get("yaw"), "yaw"), (float) finite(table.get("pitch"), "pitch"));
    }

    private static ActionIntent action(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        LuaTable table = InputApi.table(value, "controller action");
        InputApi.fields(table, "controller action", "kind", "result");
        String kind = table.get("kind").checkjstring();
        String choice = table.get("result").checkjstring();
        if ("pass".equals(choice)) {
            return new ActionIntent(kind, true);
        }
        if ("deny".equals(choice)) {
            return new ActionIntent(kind, false);
        }
        throw new LuaError("Controller action result must be 'pass' or 'deny'.");
    }

    private static final class ActionIntent {
        private final String kind;
        private final boolean pass;

        private ActionIntent(String kind, boolean pass) {
            this.kind = kind;
            this.pass = pass;
        }
    }

    private static double finite(LuaValue value, String field) {
        double number = value.checkdouble();
        if (!Double.isFinite(number)) {
            throw new LuaError("Controller " + field + " must be finite.");
        }
        return number;
    }
}
