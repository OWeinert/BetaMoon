package betamoon.luaapi.asset;

import betamoon.assets.AssetKind;
import betamoon.assets.model.ModelAnimations;
import betamoon.assets.model.ModelPose;
import betamoon.assets.model.ModelRotation;
import betamoon.assets.model.ModelVector;
import betamoon.client.assets.ClientModelAssets;
import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ModelAsset;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Lua pose writer. Render callbacks expire it; explicit createPose results
 * remain usable.
 */
public final class PoseReference extends LuaTable {
    private final ModelPose pose;
    private boolean active = true;
    private final boolean renderScoped;

    public PoseReference(ModelPose pose) {
        this(pose, false);
    }

    public PoseReference(ModelPose pose, boolean renderScoped) {
        this.pose = pose;
        this.renderScoped = renderScoped;
        for (String operation : new String[]{"setPosition", "setRotation", "setScale", "translate", "rotate", "scale",
                "getPosition", "getRotation", "getScale", "hasPart", "reset", "getSocket", "applyAnimation"}) {
            set(operation, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs arguments) {
                    if (!active) {
                        throw new LuaError("Pose writer is no longer active outside its render callback");
                    }
                    Varargs args = arguments.subargs(arguments.arg1() == PoseReference.this ? 2 : 1);
                    try {
                        return operation(operation, args);
                    } catch (IOException | IllegalArgumentException error) {
                        throw new LuaError("Pose: " + error.getMessage());
                    }
                }
            });
        }
    }

    public void expire() {
        active = false;
    }

    private Varargs operation(String operation, Varargs args) throws IOException {
        if (operation.equals("applyAnimation")) {
            AssetLocation location = AssetInputs.read(args.arg1(), AssetKind.ANIMATION);
            if (renderScoped && !ClientModelAssets.hasAnimations(location)) {
                throw new LuaError("Register animation assets during initialization before using them in onPose");
            }
            try (ModelAsset<ModelAnimations> asset = ClientModelAssets.animations(location)) {
                ModelAnimations.Clip clip = asset.getContent().getValue().clip(args.arg(2).checkjstring());
                clip.validate(pose.getGeometry());
                Set<String> mask = null;
                if (!args.arg(5).isnil()) {
                    mask = new HashSet<>();
                    LuaTable parts = args.arg(5).checktable();
                    if (parts.length() > 128 || parts.keys().length != parts.length()) {
                        throw new LuaError("Bone mask must be a dense list of at most 128 part names");
                    }
                    for (int i = 1; i <= parts.length(); i++) {
                        String part = parts.get(i).checkjstring();
                        pose.getGeometry().index(part);
                        mask.add(part);
                    }
                }
                clip.apply(pose, args.arg(3).checkdouble(), args.arg(4).optdouble(1), mask);
            }
            return NONE;
        }
        if (operation.equals("reset") && args.arg1().isnil()) {
            pose.reset();
            return NONE;
        }
        String part = args.arg1().checkjstring();
        switch (operation) {
            case "hasPart":
                return valueOf(pose.getGeometry().hasPart(part));
            case "getPosition":
                return vector(pose.getPosition(part));
            case "getRotation":
                return vector(pose.getRotation(part).toEuler());
            case "getScale":
                return vector(pose.getScale(part));
            case "reset":
                pose.reset(part);
                return NONE;
            case "getSocket":
                LuaTable socket = new LuaTable();
                socket.set("position", vector(pose.socketPosition(part)));
                socket.set("rotation", vector(pose.socketRotation(part).toEuler()));
                return socket;
            default:
                break;
        }
        ModelVector value = readVector(args.arg(2));
        switch (operation) {
            case "setPosition":
                pose.setPosition(part, value);
                break;
            case "setRotation":
                pose.setRotation(part, ModelRotation.euler(value));
                break;
            case "setScale":
                pose.setScale(part, value);
                break;
            case "translate":
                pose.setPosition(part, pose.getPosition(part).add(value));
                break;
            case "rotate":
                pose.setRotation(part, pose.getRotation(part).then(ModelRotation.euler(value)));
                break;
            case "scale":
                pose.setScale(part, pose.getScale(part).multiply(value));
                break;
            default:
                throw new IllegalArgumentException("Unknown pose operation");
        }
        return NONE;
    }

    public static ModelVector readVector(LuaValue value) {
        LuaTable table = value.checktable();
        for (LuaValue key : table.keys()) {
            if (!key.eq_b(valueOf("x")) && !key.eq_b(valueOf("y")) && !key.eq_b(valueOf("z"))) {
                throw new LuaError("Unknown vector field: " + key.tojstring());
            }
        }
        double x = table.get("x").checkdouble();
        double y = table.get("y").checkdouble();
        double z = table.get("z").checkdouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 1000000
                || Math.abs(y) > 1000000 || Math.abs(z) > 1000000) {
            throw new LuaError("Pose vector must be finite and within +/-1000000");
        }
        return new ModelVector(x, y, z);
    }

    private static LuaTable vector(ModelVector value) {
        LuaTable table = new LuaTable();
        table.set("x", valueOf(value.x));
        table.set("y", valueOf(value.y));
        table.set("z", valueOf(value.z));
        return table;
    }
}
