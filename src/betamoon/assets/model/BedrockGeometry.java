package betamoon.assets.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static betamoon.assets.model.ModelJson.array;
import static betamoon.assets.model.ModelJson.bool;
import static betamoon.assets.model.ModelJson.fields;
import static betamoon.assets.model.ModelJson.name;
import static betamoon.assets.model.ModelJson.number;
import static betamoon.assets.model.ModelJson.object;

/** Import the documented cuboid subset of Bedrock geometry 1.12.0. */
public final class BedrockGeometry {
    private static final String[] FACES = {"east", "west", "up", "down", "south", "north"};
    private final List<ModelGeometry.Bone> bones = new ArrayList<>();
    private final Map<String, ModelGeometry.Socket> sockets = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> pending = new LinkedHashMap<>();
    private final List<String> visiting = new ArrayList<>();
    private int cubeCount;
    private int width;
    private int height;

    private BedrockGeometry() {
    }

    public static ModelGeometry decode(byte[] bytes) throws IOException {
        return new BedrockGeometry().read(ModelJson.read(bytes));
    }

    private ModelGeometry read(Map<String, Object> root) throws IOException {
        fields(root, "geometry", "format_version", "minecraft:geometry");
        if (!"1.12.0".equals(root.get("format_version"))) {
            throw new IOException("geometry.format_version: supported export version is 1.12.0");
        }
        List<Object> entries = array(root.get("minecraft:geometry"), "minecraft:geometry");
        if (entries.size() != 1) {
            throw new IOException("Export exactly one geometry per .json file");
        }
        Map<String, Object> geometry = object(entries.get(0), "geometry[0]");
        fields(geometry, "geometry[0]", "description", "bones");
        Map<String, Object> description = object(geometry.get("description"), "description");
        fields(description, "description", "identifier", "texture_width", "texture_height", "visible_bounds_width",
                "visible_bounds_height", "visible_bounds_offset");
        name(description.get("identifier"), "description.identifier");
        width = dimension(description.get("texture_width"), "texture_width");
        height = dimension(description.get("texture_height"), "texture_height");
        List<Object> sourceBones = array(geometry.get("bones"), "bones");
        if (sourceBones.isEmpty() || sourceBones.size() > 128) {
            throw new IOException("Geometry requires 1-128 bones");
        }
        for (Object source : sourceBones) {
            Map<String, Object> bone = object(source, "bone");
            String name = name(bone.get("name"), "bone.name");
            if (pending.put(name, bone) != null) {
                throw new IOException("Duplicate bone: " + name);
            }
        }
        for (String name : new ArrayList<>(pending.keySet())) {
            readBone(name);
        }
        return new ModelGeometry(width, height, bones, sockets);
    }

    private int readBone(String name) throws IOException {
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).name.equals(name)) {
                return i;
            }
        }
        if (visiting.contains(name)) {
            throw new IOException("Bone hierarchy cycle: " + visiting + " -> " + name);
        }
        Map<String, Object> source = pending.get(name);
        if (source == null) {
            throw new IOException("Missing parent bone: " + name);
        }
        String path = "bones." + name;
        fields(source, path, "name", "parent", "pivot", "rotation", "mirror", "inflate", "cubes", "locators");
        visiting.add(name);
        int parent = source.containsKey("parent") ? readBone(name(source.get("parent"), path + ".parent")) : -1;
        ModelVector pivot = ModelVector.read(source.get("pivot"), ModelVector.ZERO, path + ".pivot")
                .fromBedrockPosition();
        ModelVector rotation = ModelVector.read(source.get("rotation"), ModelVector.ZERO, path + ".rotation");
        boolean mirror = source.containsKey("mirror") && bool(source.get("mirror"), path + ".mirror");
        double inflate = source.containsKey("inflate") ? number(source.get("inflate"), path + ".inflate") : 0;
        List<ModelGeometry.Quad> quads = new ArrayList<>();
        if (source.containsKey("cubes")) {
            for (Object cube : array(source.get("cubes"), path + ".cubes")) {
                if (++cubeCount > 2048) {
                    throw new IOException("Geometry exceeds 2048 cubes");
                }
                readCube(object(cube, path + ".cube"), mirror, inflate, quads, path + ".cubes[" + cubeCount + "]");
            }
        }
        int index = bones.size();
        bones.add(new ModelGeometry.Bone(name, parent, pivot, rotation, quads));
        if (source.containsKey("locators")) {
            for (Map.Entry<String, Object> locator : object(source.get("locators"), path + ".locators").entrySet()) {
                ModelVector position;
                ModelRotation orientation = ModelRotation.IDENTITY;
                if (locator.getValue() instanceof Map) {
                    Map<String, Object> data = object(locator.getValue(), path + ".locator");
                    fields(data, path + ".locator", "offset", "rotation");
                    position = ModelVector.read(data.get("offset"), null, path + ".locator.offset");
                    orientation = ModelRotation.bedrock(
                            ModelVector.read(data.get("rotation"), ModelVector.ZERO, path + ".locator.rotation"));
                } else {
                    position = ModelVector.read(locator.getValue(), null, path + ".locator");
                }
                if (sockets.put(locator.getKey(),
                        new ModelGeometry.Socket(index, position.fromBedrockPosition(), orientation)) != null) {
                    throw new IOException("Duplicate model locator: " + locator.getKey());
                }
            }
        }
        visiting.remove(name);
        return index;
    }

    private void readCube(Map<String, Object> cube, boolean inheritedMirror, double inheritedInflate,
            List<ModelGeometry.Quad> quads, String path) throws IOException {
        fields(cube, path, "origin", "size", "pivot", "rotation", "inflate", "mirror", "uv");
        ModelVector origin = ModelVector.read(cube.get("origin"), null, path + ".origin");
        ModelVector size = ModelVector.read(cube.get("size"), null, path + ".size");
        if (size.x < 0 || size.y < 0 || size.z < 0) {
            throw new IOException(path + ".size: dimensions cannot be negative");
        }
        double inflate = cube.containsKey("inflate")
                ? number(cube.get("inflate"), path + ".inflate")
                : inheritedInflate;
        if (size.x + 2 * inflate < 0 || size.y + 2 * inflate < 0 || size.z + 2 * inflate < 0) {
            throw new IOException(path + ".inflate: inverted cube bounds");
        }
        boolean mirror = cube.containsKey("mirror") ? bool(cube.get("mirror"), path + ".mirror") : inheritedMirror;
        ModelVector pivot = cube.containsKey("pivot")
                ? ModelVector.read(cube.get("pivot"), null, path + ".pivot").fromBedrockPosition()
                : ModelVector.ZERO;
        ModelRotation rotation = ModelRotation
                .bedrock(ModelVector.read(cube.get("rotation"), ModelVector.ZERO, path + ".rotation"));
        double x0 = -origin.x - size.x - inflate;
        double x1 = -origin.x + inflate;
        double y0 = origin.y - inflate;
        double y1 = origin.y + size.y + inflate;
        double z0 = origin.z - inflate;
        double z1 = origin.z + size.z + inflate;
        ModelVector[][] faces = {{v(x1, y1, z0), v(x1, y1, z1), v(x1, y0, z1), v(x1, y0, z0)},
                {v(x0, y1, z1), v(x0, y1, z0), v(x0, y0, z0), v(x0, y0, z1)},
                {v(x0, y1, z0), v(x0, y1, z1), v(x1, y1, z1), v(x1, y1, z0)},
                {v(x0, y0, z1), v(x0, y0, z0), v(x1, y0, z0), v(x1, y0, z1)},
                {v(x1, y1, z1), v(x0, y1, z1), v(x0, y0, z1), v(x1, y0, z1)},
                {v(x0, y1, z0), v(x1, y1, z0), v(x1, y0, z0), v(x0, y0, z0)}};
        Object uv = cube.get("uv");
        Map<String, Object> perFace = uv instanceof Map ? object(uv, path + ".uv") : null;
        double[][] rectangles = null;
        if (perFace == null) {
            if (size.x != Math.floor(size.x) || size.y != Math.floor(size.y) || size.z != Math.floor(size.z)) {
                throw new IOException(path + ": fractional cube dimensions require per-face UVs in this importer");
            }
            double[] offset = pair(uv, path + ".uv");
            double x = size.x;
            double y = size.y;
            double z = size.z;
            rectangles = new double[][]{{0, z, z, y}, {z + x, z, z, y}, {z + x, z, -x, -z}, {z + 2 * x, 0, -x, z},
                    {2 * z + x, z, x, y}, {z, z, x, y}};
            for (double[] rectangle : rectangles) {
                rectangle[0] += offset[0];
                rectangle[1] += offset[1];
                if (mirror) {
                    rectangle[0] += rectangle[2];
                    rectangle[2] = -rectangle[2];
                }
            }
            if (mirror) {
                double[] swap = rectangles[0];
                rectangles[0] = rectangles[1];
                rectangles[1] = swap;
            }
        } else {
            fields(perFace, path + ".uv", FACES);
            if (mirror) {
                throw new IOException(path + ": mirror with per-face UV is unsupported; export flipped UV rectangles");
            }
        }
        for (int face = 0; face < FACES.length; face++) {
            String material = "default";
            double[] rectangle;
            if (perFace != null) {
                if (!perFace.containsKey(FACES[face])) {
                    continue;
                }
                Map<String, Object> data = object(perFace.get(FACES[face]), path + ".uv." + FACES[face]);
                fields(data, path + ".uv." + FACES[face], "uv", "uv_size", "material_instance");
                double[] offset = pair(data.get("uv"), path + ".uv");
                double[] extent = data.containsKey("uv_size")
                        ? pair(data.get("uv_size"), path + ".uv_size")
                        : face < 2
                                ? new double[]{size.z, size.y}
                                : face < 4 ? new double[]{size.x, size.z} : new double[]{size.x, size.y};
                rectangle = new double[]{offset[0], offset[1], extent[0], extent[1]};
                if (face == 2 || face == 3) {
                    rectangle = new double[]{offset[0] + extent[0], offset[1] + extent[1], -extent[0], -extent[1]};
                }
                if (data.containsKey("material_instance")) {
                    material = name(data.get("material_instance"), path + ".material_instance");
                }
            } else {
                rectangle = rectangles[face];
            }
            List<ModelVector> vertices = new ArrayList<>();
            for (ModelVector vertex : faces[face]) {
                vertices.add(rotation.transform(vertex.add(pivot.times(-1))).add(pivot));
            }
            double u0 = rectangle[0] / width;
            double v0 = rectangle[1] / height;
            double u1 = (rectangle[0] + rectangle[2]) / width;
            double v1 = (rectangle[1] + rectangle[3]) / height;
            List<ModelVector> coordinates = face == 2 || face == 3
                    ? Arrays.asList(v(u0, v0, 0), v(u0, v1, 0), v(u1, v1, 0), v(u1, v0, 0))
                    : Arrays.asList(v(u1, v0, 0), v(u0, v0, 0), v(u0, v1, 0), v(u1, v1, 0));
            quads.add(
                    new ModelGeometry.Quad(vertices, coordinates, material, size.x == 0 || size.y == 0 || size.z == 0));
        }
    }

    private static ModelVector v(double x, double y, double z) {
        return new ModelVector(x, y, z);
    }

    private static double[] pair(Object value, String path) throws IOException {
        List<Object> values = array(value, path);
        if (values.size() != 2) {
            throw new IOException(path + ": expected two numbers");
        }
        return new double[]{number(values.get(0), path), number(values.get(1), path)};
    }

    private static int dimension(Object value, String path) throws IOException {
        double number = number(value, path);
        if (number < 1 || number > 16384 || number != (int) number) {
            throw new IOException(path + ": expected integer in 1-16384");
        }
        return (int) number;
    }
}
