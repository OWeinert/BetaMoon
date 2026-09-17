package betamoon.luamodloader;

import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetDefinition;
import betamoon.assets.BuiltinAssets;
import betamoon.assets.io.AssetProvider;
import betamoon.assets.model.ModelFoundationTest;
import betamoon.assets.model.ModelGeometry;
import betamoon.assets.model.ModelPose;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.ClientModelAssets;
import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ModelAsset;
import betamoon.client.render.ModelAppearance;
import betamoon.luaapi.asset.AssetsApi;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import betamoon.luaapi.asset.PoseReference;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real Lua declarations, snapshot poses, compatible pack fallback, and
 * ownership cleanup.
 */
public final class ModelLuaApiTest {
    private ModelLuaApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Memory defaults = new Memory();
        Memory pack = new Memory();
        defaults.files.put("test.json", ModelFoundationTest.GEOMETRY.getBytes(StandardCharsets.UTF_8));
        defaults.files.put("test.animation.json", ModelFoundationTest.ANIMATIONS.getBytes(StandardCharsets.UTF_8));
        defaults.files.put("mymod/models/inferred.json", ModelFoundationTest.GEOMETRY.getBytes(StandardCharsets.UTF_8));
        defaults.files.put("mymod/animations/inferred.animation.json",
                ModelFoundationTest.ANIMATIONS.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), "png", image);
        defaults.files.put("test.png", image.toByteArray());
        List<String> warnings = new ArrayList<>();
        ClientAssets.useProviders(defaults, pack, warnings::add);
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        AssetsApi.attach(api);
        lua.set("betamoon", api);
        for (AssetDefinition definition : BuiltinAssets.models().values()) {
            try (ModelAsset<ModelGeometry> builtIn = ClientModelAssets.model(new AssetLocation(definition))) {
                require(builtIn.getContent().getSourceKind().equals("builtin"),
                        "Every built-in model must load from packaged resources");
                ModelGeometry geometry = builtIn.getContent().getValue();
                require(!geometry.bones.isEmpty() && geometry.bones.stream().anyMatch(bone -> !bone.faces.isEmpty()),
                        "Built-in model must have renderable geometry");
            }
        }
        require(BuiltinAssets.models().size() == 57, "Expected 52 model shapes and five aliases");
        Set<AssetPath> bundledFiles = new HashSet<>();
        for (Map.Entry<AssetId, AssetDefinition> entry : BuiltinAssets.models().entrySet()) {
            bundledFiles.add(entry.getValue().getFallbackPath());
            String name = entry.getKey().getKey().getPath();
            if (!name.startsWith("block/fence")) {
                require(!name.matches(".*_(north|east|south|west)(?:_open|_on|_pressed)?$"),
                        "Rotational shape must not have a directional model key: " + name);
            }
        }
        require(bundledFiles.size() == 52, "Expected one bundled file per distinct shape");
        require(distinctGeometry("fence", "fence_north", "fence_north_east"),
                "Fence connection variants must have distinct geometry");
        require(distinctGeometry("torch", "torch_wall", "lever"),
                "Distinct placement types must have distinct geometry");
        require(distinctGeometry("rail_straight", "rail_ascending", "rail_corner"),
                "Rail variants must distinguish flat, rising, and curved appearances");
        require(BuiltinAssets
                .find(new AssetId(AssetKind.MODEL, AssetKey.parse("minecraft:block/stairs_north"))) == null,
                "Directional stair assets must be removed");
        require(BuiltinAssets
                .find(new AssetId(AssetKind.MODEL, AssetKey.parse("minecraft:block/torch_wall_east"))) == null,
                "Directional torch assets must be removed");
        lua.load("assert(betamoon.assets.models:get('minecraft:block/torch') ~= nil); "
                + "assert(betamoon.assets.models:get('minecraft:block/torch'):getSource().kind == 'builtin'); "
                + "assert(betamoon.assets.models:get('minecraft:block/torch'):getOverridePath() == "
                + "'bm_assets/minecraft/models/block/torch.json'); "
                + "assert(betamoon.assets.models:get('minecraft:block/torch'):createPose():hasPart('root'))").call();
        lua.load("assert(betamoon.assets.models:get('minecraft:block/stair'):getOverridePath() == "
                + "'bm_assets/minecraft/models/block/stairs.json')").call();
        AssetId torchId = new AssetId(AssetKind.MODEL, AssetKey.parse("minecraft:block/torch"));
        AssetLocation torch = new AssetLocation(BuiltinAssets.find(torchId));
        String replacement = "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{"
                + "\"description\":{\"identifier\":\"geometry.torch\",\"texture_width\":16,"
                + "\"texture_height\":16},\"bones\":[{\"name\":\"root\",\"cubes\":[{"
                + "\"origin\":[-2,0,-2],\"size\":[4,4,4],\"uv\":[0,0]}]}]}]}";
        pack.files.put("bm_assets/minecraft/models/block/torch.json", replacement.getBytes(StandardCharsets.UTF_8));
        try (ModelAsset<ModelGeometry> selected = ClientModelAssets.model(torch)) {
            require(selected.getContent().getSourceKind().equals("pack"),
                    "Valid Bedrock JSON can replace a built-in model");
        }
        pack.files.put("bm_assets/minecraft/models/block/torch.json",
                replacement.replace("root", "other").getBytes(StandardCharsets.UTF_8));
        try (ModelAsset<ModelGeometry> selected = ClientModelAssets.model(torch)) {
            require(selected.getContent().getSourceKind().equals("builtin"),
                    "An incompatible built-in model override must fall back");
        }
        require(!warnings.isEmpty(), "Rejected built-in model overrides must be diagnosed");
        pack.files.clear();
        try (ScriptExecutionScope owner = ScriptExecutionScope.open("models.lua");
                ScriptAssetScope scope = ScriptAssetScope.open("models.lua")) {
            lua.load("model=betamoon.assets.models:add{key='mymod:test',path='test.json'}; "
                    + "assert(not pcall(function() betamoon.assets.models:add{key='minecraft:block/torch',"
                    + "path='test.json'} end)); "
                    + "animation=betamoon.assets.animations:add{key='mymod:test',path='test.animation.json'}; "
                    + "local alias=betamoon.assets.models:add{key='mymod:alias',path='test.json'}; "
                    + "assert(alias:getOverridePath()==model:getOverridePath()); "
                    + "local inferredModel=betamoon.assets.models:add{key='mymod:inferred'}; "
                    + "local inferredAnimation=betamoon.assets.animations:add{key='mymod:inferred'}; "
                    + "assert(inferredModel:getPath()=='mymod/models/inferred.json'); "
                    + "assert(inferredAnimation:getOverridePath()=='bm_assets/mymod/animations/inferred.animation.json'); "
                    + "assert(model:getKind()=='models'); assert(model:getOverridePath()=='bm_assets/test.json'); "
                    + "assert(animation:getClips().wave.duration==2); assert(animation:getSource().kind=='script'); "
                    + "assert(not pcall(function() betamoon.assets.models:add{key='mymod:bad',path='test.png'} end)); "
                    + "pose=model:createPose(); other=model:createPose(); "
                    + "pose:applyAnimation(animation,'wave',0.5); assert(math.abs(pose:getRotation('head').z-45)<0.001); "
                    + "pose:translate('head',{x=1,y=0,z=0}); assert(pose:getPosition('head').x==3); "
                    + "assert(other:getPosition('head').x==0); assert(pose:hasPart('head')); "
                    + "assert(not pcall(function() pose:setScale('head',{x=-1,y=1,z=1}) end)); "
                    + "pose:reset(); assert(pose:getSocket('tip').position.y==12); "
                    + "assert(not pcall(function() pose:applyAnimation(model,'wave',1) end)); "
                    + "appearance={model=model,texture='test.png',animation={asset=animation,clip='wave'},"
                    + "onPose=function(ctx) remembered=ctx.pose; ctx.pose:translate('head',{x=1,y=0,z=0}) end}").call();
            scope.publish();
        }
        try (ModelAppearance appearance = new ModelAppearance(new ModelAppearanceDeclaration(lua.get("appearance")))) {
            int reads = defaults.reads + pack.reads;
            ModelPose first = appearance.evaluate("held", 10, 0, 0, 0, 0);
            ModelPose next = appearance.evaluate("ground", 0, 0, 0, 0, 0);
            require(first.getPosition("head").x == 3 && next.getPosition("head").x == 1,
                    "Pose evaluations must start from rest");
            require(defaults.reads + pack.reads == reads, "Drawing must not read providers");
            lua.load("assert(not pcall(function() remembered:translate('head',{x=1,y=0,z=0}) end))").call();
            pack.files.put("bm_assets/mymod/models/test.json", ModelFoundationTest.GEOMETRY
                    .replace("\"bones\":[", "\"bones\":[{\"name\":\"legacy\"},")
                    .getBytes(StandardCharsets.UTF_8));
            ClientAssets.refresh();
            require(!appearance.geometry().hasPart("legacy"),
                    "An old key-derived pack entry must not override an explicit script path");
            pack.files.put("bm_assets/test.json",
                    ModelFoundationTest.GEOMETRY.replace("head", "different").getBytes(StandardCharsets.UTF_8));
            ClientAssets.refresh();
            require(appearance.geometry().hasPart("head"), "Incompatible geometry override must fall back");
            require(!warnings.isEmpty(), "Rejected pack must be diagnosed");
            pack.files.put("bm_assets/test.animation.json",
                    ModelFoundationTest.ANIMATIONS.replace("90", "180").getBytes(StandardCharsets.UTF_8));
            ClientAssets.refresh();
            require(Math.abs(appearance.evaluate("held", 10, 0, 0, 0, 0).getRotation("head").toEuler().z - 90) < 0.001,
                    "Compatible clip replacement must reach later poses");
            lua.load("assert(other:getPosition('head').x==0)").call();
            pack.files.put("bm_assets/test.json", ModelFoundationTest.GEOMETRY
                    .replace("\"bones\":[", "\"bones\":[{\"name\":\"extra\"},").getBytes(StandardCharsets.UTF_8));
            ClientAssets.refresh();
            require(appearance.geometry().hasPart("extra"), "Compatible packs may add optional parts");
            pack.files.clear();
            ClientAssets.refresh();
            require(!appearance.geometry().hasPart("extra"),
                    "Pack-only parts must not become requirements that prevent restoring bundled geometry");
            require(Math.abs(appearance.evaluate("held", 10, 0, 0, 0, 0).getRotation("head").toEuler().z - 45) < 0.001,
                    "Default pack restores bundled clips");
        }
        ScriptResourceTracker.unload("models.lua");
        lua.load(
                "assert(betamoon.assets.models:get('mymod:test')==nil); assert(not pcall(function() model:createPose() end))")
                .call();
        System.out.println(
                "Model Lua API passed: typed declarations, independent poses, callback expiry, pack fallback and cleanup.");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static boolean distinctGeometry(String first, String second, String third) throws Exception {
        String a = geometrySignature(first);
        String b = geometrySignature(second);
        String c = geometrySignature(third);
        return !a.equals(b) && !a.equals(c) && !b.equals(c);
    }

    private static String geometrySignature(String name) throws Exception {
        AssetId id = new AssetId(AssetKind.MODEL, AssetKey.parse("minecraft:block/" + name));
        try (ModelAsset<ModelGeometry> model = ClientModelAssets.model(new AssetLocation(BuiltinAssets.find(id)))) {
            StringBuilder result = new StringBuilder();
            for (ModelGeometry.Bone bone : model.getContent().getValue().bones) {
                for (ModelGeometry.Quad face : bone.faces) {
                    for (betamoon.assets.model.ModelVector vertex : face.vertices) {
                        result.append(vertex.x).append('/').append(vertex.y).append('/').append(vertex.z).append(';');
                    }
                    for (betamoon.assets.model.ModelVector uv : face.uv) {
                        result.append(uv.x).append('/').append(uv.y).append(';');
                    }
                }
            }
            return result.toString();
        }
    }

    private static final class Memory implements AssetProvider {
        private final Map<String, byte[]> files = new HashMap<>();
        private int reads;
        public boolean exists(AssetPath path) {
            return files.containsKey(path.toString());
        }
        public String getName() {
            return "fixture";
        }

        public byte[] read(AssetPath path, int limit) {
            reads++;
            return files.get(path.toString());
        }
    }
}
