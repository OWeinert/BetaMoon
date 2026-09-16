package betamoon.assets.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Headless fixtures for exported pivots/UVs and animation semantics,
 * independent of Lua and GL.
 */
public final class ModelFoundationTest {
    public static final String GEOMETRY = "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{"
            + "\"description\":{\"identifier\":\"geometry.test\",\"texture_width\":64,\"texture_height\":32},"
            + "\"bones\":[{\"name\":\"head\",\"parent\":\"body\",\"pivot\":[0,8,0],"
            + "\"locators\":{\"tip\":[0,12,0]},\"cubes\":[{\"origin\":[-2,8,-2],\"size\":[4,4,4],\"uv\":[0,0]}]},"
            + "{\"name\":\"body\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[-1,0,-1],\"size\":[2,8,2],\"uv\":[16,0]}]}]}]}";
    public static final String ANIMATIONS = "{\"format_version\":\"1.8.0\",\"animations\":{"
            + "\"wave\":{\"loop\":true,\"animation_length\":2,\"bones\":{\"head\":{"
            + "\"rotation\":{\"0\":[0,0,0],\"1\":[0,0,90],\"2\":[0,0,0]},"
            + "\"position\":{\"0\":[0,0,0],\"1\":[-4,0,0],\"2\":[0,0,0]}}}}}}";

    private ModelFoundationTest() {
    }

    public static void main(String[] arguments) throws Exception {
        ModelGeometry geometry = geometry(GEOMETRY);
        require(geometry.bones.get(0).name.equals("body"), "Parents must be evaluated before children");
        require(geometry.bones.get(1).faces.size() == 6, "All cube faces must survive import");
        ModelGeometry.Quad east = geometry.bones.get(1).faces.get(0);
        near(east.uv.get(0).x, 4.0 / 64, "East-facing quad starts at its right UV edge");
        near(east.uv.get(1).x, 0, "East UV left edge maps toward positive Z");
        near(east.uv.get(0).y, 4.0 / 32, "East box UV row");
        ModelGeometry mirrored = geometry(GEOMETRY.replace("\"uv\":[0,0]", "\"mirror\":true,\"uv\":[0,0]"));
        near(mirrored.bones.get(1).faces.get(0).uv.get(0).x, 8.0 / 64,
                "Mirrored box UV swaps east/west and reverses U");
        ModelGeometry plane = geometry(GEOMETRY.replace("\"size\":[4,4,4]", "\"size\":[4,4,0]"));
        require(plane.bones.get(1).faces.get(4).plane, "Plane faces retain directional UV rendering");
        ModelPose first = new ModelPose(geometry);
        ModelPose second = new ModelPose(geometry);
        first.setRotation("head", ModelRotation.euler(new ModelVector(0, 0, 90)));
        vector(first.socketPosition("tip"), -4, 8, 0, "Rotation around nested pivot");
        vector(second.socketPosition("tip"), 0, 12, 0, "Independent pose storage");
        first.setPosition("body", new ModelVector(3, 0, 0));
        vector(first.socketPosition("tip"), -1, 8, 0, "Parent translation reaches locator");
        first.reset("head");
        vector(first.socketPosition("tip"), 3, 12, 0, "Reset one part preserves parent pose");
        first.reset();

        ModelAnimations clips = animation(ANIMATIONS);
        ModelAnimations.Clip clip = clips.clip("wave");
        clip.validate(geometry);
        clip.apply(first, 0.5, 1, null);
        near(first.getPosition("head").x, 2, "Source position X is converted");
        near(first.getRotation("head").toEuler().z, 45, "Linear rotation sample");
        first.reset();
        clip.apply(first, 2.5, 1, null);
        near(first.getPosition("head").x, 2, "Loop wraps explicit seconds");
        first.reset();
        clip.apply(first, 1, 0.5, Collections.singleton("head"));
        near(first.getRotation("head").toEuler().z, 45, "Quaternion blending");
        first.setRotation("head", first.getRotation("head").then(ModelRotation.euler(new ModelVector(90, 0, 0))));
        vector(first.getRotation("head").transform(new ModelVector(0, 1, 0)), 0, 0, 1,
                "Procedural local composition after clip");

        ModelRotation rotation = ModelRotation.euler(new ModelVector(22, 37, -18));
        ModelVector test = new ModelVector(3, -2, 7);
        ModelVector expected = rotation.transform(test);
        ModelVector actual = ModelRotation.euler(rotation.toEuler()).transform(test);
        vector(actual, expected.x, expected.y, expected.z, "Euler getter round trip");
        String rotated = GEOMETRY.replace("\"name\":\"head\"", "\"rotation\":[25,30,10],\"name\":\"head\"");
        ModelPose restRotated = new ModelPose(geometry(rotated));
        clip.apply(restRotated, 1, 1, null);
        ModelGeometry.Bone head = restRotated.getGeometry().bones.get(1);
        actual = head.rotation.then(restRotated.getRotation("head")).transform(test);
        expected = ModelRotation.bedrock(new ModelVector(25, 30, 100)).transform(test);
        vector(actual, expected.x, expected.y, expected.z,
                "Clip Euler values add to authored angles before conversion");

        String discontinuity = "{\"format_version\":\"1.8.0\",\"animations\":{\"clip\":{\"loop\":\"hold_on_last_frame\","
                + "\"bones\":{\"head\":{\"position\":{\"0\":[0,0,0],\"1\":{\"pre\":[-2,0,0],\"post\":[-8,0,0]}}}}}}}";
        ModelAnimations.Clip step = animation(discontinuity).clip("clip");
        first.reset();
        step.apply(first, 0.5, 1, null);
        near(first.getPosition("head").x, 1, "Interpolate toward pre value");
        step.apply(first, 1, 1, null);
        near(first.getPosition("head").x, 8, "Exact timestamp selects post value");
        step.apply(first, 20, 1, null);
        near(first.getPosition("head").x, 8, "Hold retains last frame");
        verifyInterpolation(first);
        reject(() -> geometry(GEOMETRY.replace("\"name\":\"body\"", "\"name\":\"body\",\"parent\":\"head\"")), "cycle");
        reject(() -> geometry(GEOMETRY.replace("\"cubes\":", "\"poly_mesh\":")), "unsupported");
        reject(() -> animation(ANIMATIONS.replace("[0,0,90]", "[\"query.anim_time\",0,0]")), "expressions");
        reject(() -> animation(ANIMATIONS.replace("\"1\":[0,0,90]", "\"1\":[0,0,90],\"1.0\":[0,0,90]")), "duplicate");
        reject(() -> ModelJson.read(bytes("{\"a\":1,\"a\":2}")), "Duplicate");
        reject(() -> ModelJson.read(bytes("{\"a\":01}")), "number");
        reject(() -> clip.validate(geometry(GEOMETRY.replace("head", "other"))), "missing");
        System.out.println(
                "Model foundations passed: hierarchy, pivots, UVs, independent poses, numeric clips, blending and strict input.");
    }

    private static void verifyInterpolation(ModelPose pose) throws IOException {
        String curve = "{\"format_version\":\"1.8.0\",\"animations\":{\"curve\":{\"bones\":{\"head\":{"
                + "\"position\":{\"0\":[0,0,0],\"1\":{\"post\":[0,2,0],\"lerp_mode\":\"catmullrom\"},"
                + "\"2\":[0,4,0],\"3\":[0,0,0]}}}}}}";
        ModelAnimations.Clip smooth = animation(curve).clip("curve");
        pose.reset();
        smooth.apply(pose, 1.5, 1, null);
        near(pose.getPosition("head").y, 3.375, "Catmull-Rom uses both neighboring control values");
        ModelAnimations.Clip stepped = animation(curve.replace("catmullrom", "step")).clip("curve");
        pose.reset();
        stepped.apply(pose, 1.5, 1, null);
        near(pose.getPosition("head").y, 2, "Step holds the left key until the next timestamp");
        stepped.apply(pose, 2, 1, null);
        near(pose.getPosition("head").y, 4, "Step changes at the exact next timestamp");
        pose.reset();
        smooth.apply(pose, 4, 1, null);
        near(pose.getPosition("head").y, 0, "Finished one-shot clips leave the pose unchanged");
    }

    private static ModelGeometry geometry(String value) throws IOException {
        return BedrockGeometry.decode(bytes(value));
    }

    private static ModelAnimations animation(String value) throws IOException {
        return BedrockAnimations.decode(bytes(value));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void vector(ModelVector actual, double x, double y, double z, String message) {
        near(actual.x, x, message + " X");
        near(actual.y, y, message + " Y");
        near(actual.z, z, message + " Z");
    }

    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.000001, message + ": " + actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface Checked {
        void run() throws IOException;
    }

    private static void reject(Checked action, String message) throws IOException {
        try {
            action.run();
            throw new AssertionError("Expected rejection: " + message);
        } catch (IOException error) {
            require(error.getMessage().contains(message), "Wrong diagnostic: " + error.getMessage());
        }
    }
}
