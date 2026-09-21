package betamoon.assets.model;

import betamoon.assets.AssetDefinition;
import betamoon.assets.BuiltinAssets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.src.Block;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.Material;
import net.minecraft.src.RenderBlocks;
import net.minecraft.src.Tessellator;

/**
 * Converts Beta 1.7.3 native rendering into bundled Bedrock 1.12 geometry.
 * Axis-aligned faces preserve native coordinates and UVs; slanted shapes use
 * rotated cubes or planes supported by the shared importer.
 */
public final class BuiltinModelExporter {
    private static final String[] DIRECTIONS = {"north", "east", "south", "west"};

    private BuiltinModelExporter() {
    }

    public static void main(String[] arguments) throws Exception {
        Path root = Paths.get("resources/builtin/minecraft/models/block").toAbsolutePath().normalize();
        Files.createDirectories(root);
        int written = 0;
        Set<Path> exported = new HashSet<>();
        for (AssetDefinition definition : BuiltinAssets.models().values()) {
            Path path = Paths.get("resources", definition.getFallbackPath().toString()).toAbsolutePath().normalize();
            if (!path.startsWith(root)) {
                throw new IOException("Built-in model path escaped its resource directory");
            }
            if (!exported.add(path)) {
                continue;
            }
            String name = path.getFileName().toString().replace(".json", "");
            Capture capture = capture(name);
            write(path, name, capture);
            ModelGeometry checked = BedrockGeometry.decode(Files.readAllBytes(path));
            if (checked.bones.isEmpty()) {
                throw new IOException("Empty built-in model: " + name);
            }
            if (!name.equals("rail_ascending") && !name.equals("torch_wall") && !name.startsWith("lever")) {
                verifyCapturedFaces(name, capture, checked);
            }
            written++;
        }
        System.out.println("Exported " + written + " Beta 1.7.3 model resources.");
    }

    private static Capture capture(String name) {
        Capture output = new Capture();
        Tessellator original = Tessellator.instance;
        Tessellator.instance = output;
        try {
            if (name.startsWith("fence")) {
                int mask = 0;
                for (int i = 0; i < DIRECTIONS.length; i++) {
                    if (name.contains("_" + DIRECTIONS[i])) {
                        mask |= 1 << i;
                    }
                }
                render(output, Block.fence, 0, mask, 0, 0, 0);
            } else if (name.equals("stairs")) {
                render(output, Block.stairCompactPlanks, 2, 0, 0, 0, 0);
            } else if (name.startsWith("trapdoor")) {
                int metadata = name.equals("trapdoor_open") ? 4 : 0;
                render(output, Block.trapdoor, metadata, 0, 0, 0, 0);
            } else if (name.startsWith("door")) {
                boolean open = name.endsWith("_open");
                int metadata = 2 | (open ? 4 : 0);
                if (!name.startsWith("door_upper")) {
                    output.part("lower");
                    render(output, Block.doorWood, metadata, 0, 0, 0, 0);
                }
                if (!name.startsWith("door_lower")) {
                    output.part("upper");
                    render(output, Block.doorWood, 8 | metadata, 0, 0, name.startsWith("door_upper") ? 0 : 1, 0);
                }
            } else if (name.startsWith("bed")) {
                int metadata = 2;
                if (!name.equals("bed_foot")) {
                    output.part("head");
                    render(output, Block.blockBed, metadata, 0, 0, 0, 0);
                }
                if (!name.equals("bed_head")) {
                    output.part("foot");
                    render(output, Block.blockBed, 8 | metadata, 0, 0, 0, name.equals("bed_foot") ? 0 : -1);
                }
            } else if (name.startsWith("torch")) {
                int metadata = name.equals("torch") ? 5 : 4;
                render(output, Block.torchWood, metadata, 0, 0, 0, 0);
            } else if (name.startsWith("lever")) {
                int metadata = name.startsWith("lever_floor") ? 5 : 4;
                render(output, Block.lever, metadata | (name.endsWith("_on") ? 8 : 0), 0, 0, 0, 0);
            } else if (name.startsWith("rail_")) {
                int metadata = name.equals("rail_straight") ? 0 : name.equals("rail_ascending") ? 5 : 6;
                render(output, Block.rail, metadata, 0, 0, 0, 0);
            } else if (name.equals("ladder")) {
                render(output, Block.ladder, 3, 0, 0, 0, 0);
            } else if (name.startsWith("button")) {
                int metadata = 4;
                render(output, Block.button, metadata | (name.endsWith("_pressed") ? 8 : 0), 0, 0, 0, 0);
            } else if (name.startsWith("snow_")) {
                render(output, Block.snow, Integer.parseInt(name.substring(5)) - 1, 0, 0, 0, 0);
            } else if (name.equals("slab")) {
                render(output, Block.stairSingle, 0, 0, 0, 0, 0);
            } else if (name.equals("slab_double")) {
                render(output, Block.stairDouble, 0, 0, 0, 0, 0);
            } else if (name.startsWith("pressure_plate")) {
                render(output, Block.pressurePlateStone, name.endsWith("pressed") ? 1 : 0, 0, 0, 0, 0);
            } else {
                throw new IllegalArgumentException("No vanilla model exporter for " + name);
            }
            return output;
        } finally {
            Tessellator.instance = original;
        }
    }

    private static void render(Capture output, Block block, int metadata, int fenceMask, int x, int y, int z) {
        IBlockAccess world = (IBlockAccess) java.lang.reflect.Proxy.newProxyInstance(
                IBlockAccess.class.getClassLoader(), new Class<?>[]{IBlockAccess.class}, (proxy, method, args) -> {
                    int bx = (Integer) args[0];
                    int by = (Integer) args[1];
                    int bz = (Integer) args[2];
                    if (method.getName().equals("getBlockMetadata")) {
                        return by == y ? metadata : metadata | 8;
                    }
                    if (method.getName().equals("getBlockId")) {
                        int bit = bx < x ? 3 : bx > x ? 1 : bz < z ? 0 : bz > z ? 2 : -1;
                        return bit >= 0 && (fenceMask & (1 << bit)) != 0 ? block.blockID : 0;
                    }
                    if (method.getReturnType() == float.class) {
                        return 1.0f;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == Material.class) {
                        return Material.air;
                    }
                    return null;
                });
        RenderBlocks renderer = new RenderBlocks(world);
        renderer.renderAllFaces = true;
        if (!renderer.renderBlockByRenderType(block, x, y, z)) {
            throw new IllegalStateException("Native renderer emitted no model: " + block.blockID + "/" + metadata);
        }
    }

    private static void write(Path path, String name, Capture capture) throws IOException {
        StringBuilder json = new StringBuilder(32768);
        json.append("{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{");
        json.append("\"identifier\":\"geometry.minecraft.").append(name);
        json.append("\",\"texture_width\":16,\"texture_height\":16},\"bones\":[");
        boolean firstBone = true;
        for (Map.Entry<String, List<double[]>> part : capture.parts.entrySet()) {
            if (!firstBone) {
                json.append(',');
            }
            firstBone = false;
            json.append("{\"name\":\"").append(part.getKey()).append("\",\"cubes\":[");
            List<double[]> vertices = part.getValue();
            if (vertices.size() % 4 != 0) {
                throw new IOException("Vanilla renderer emitted an incomplete quad: " + path);
            }
            if (name.equals("rail_ascending")) {
                appendAscendingRail(json);
            } else if (name.equals("torch_wall")) {
                appendWallTorch(json);
            } else if (name.startsWith("lever")) {
                appendLever(json, vertices, capture, part.getKey());
            } else {
                for (int offset = 0; offset < vertices.size(); offset += 4) {
                    if (offset != 0) {
                        json.append(',');
                    }
                    appendFace(json, vertices.subList(offset, offset + 4), capture, part.getKey());
                }
            }
            json.append("]}");
        }
        json.append("]}]}");
        Files.write(path, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendFace(StringBuilder json, List<double[]> vertices, Capture capture, String part) {
        double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double minU = 1;
        double minV = 1;
        for (double[] vertex : vertices) {
            for (int axis = 0; axis < 3; axis++) {
                low[axis] = Math.min(low[axis], vertex[axis]);
                high[axis] = Math.max(high[axis], vertex[axis]);
            }
            minU = Math.min(minU, vertex[3]);
            minV = Math.min(minV, vertex[4]);
        }
        int tileX = Math.max(0, (int) Math.floor(minU * 16 + 0.001));
        int tileY = Math.max(0, (int) Math.floor(minV * 16 + 0.001));
        String material = capture.material(tileX + 16 * tileY, part);
        String face = face(vertices);
        double[][] coordinates = orderedUv(vertices, face, low, high, 0);
        int turns = 0;
        if ((face.equals("up") || face.equals("down")) && Math.abs(high[0] - low[0] - high[2] + low[2]) < 0.000001) {
            while (turns < 3 && !rectangularUv(coordinates, true)) {
                turns++;
                coordinates = orderedUv(vertices, face, low, high, turns);
            }
        }
        json.append("{\"origin\":[").append(8 - high[0] * 16).append(',').append(low[1] * 16).append(',')
                .append(low[2] * 16 - 8).append("],\"size\":[").append((high[0] - low[0]) * 16).append(',')
                .append((high[1] - low[1]) * 16).append(',').append((high[2] - low[2]) * 16).append(']');
        if (turns != 0) {
            json.append(",\"pivot\":[").append(8 - (low[0] + high[0]) * 8).append(',').append(low[1] * 16).append(',')
                    .append((low[2] + high[2]) * 8 - 8).append("],\"rotation\":[0,").append(turns * 90).append(",0]");
        }
        json.append(",\"uv\":{\"").append(face).append("\":{");
        double u0 = coordinates[1][0] * 16 - tileX;
        double v0 = coordinates[0][1] * 16 - tileY;
        double u1 = coordinates[0][0] * 16 - tileX;
        double v1 = coordinates[2][1] * 16 - tileY;
        if (face.equals("up") || face.equals("down")) {
            u0 = coordinates[2][0] * 16 - tileX;
            v0 = coordinates[1][1] * 16 - tileY;
            u1 = coordinates[0][0] * 16 - tileX;
            v1 = coordinates[0][1] * 16 - tileY;
        }
        json.append("\"uv\":[").append(u0 * 16).append(',').append(v0 * 16).append("],\"uv_size\":[")
                .append((u1 - u0) * 16).append(',').append((v1 - v0) * 16).append("],\"material_instance\":\"")
                .append(material).append("\"}}}");
    }

    private static String face(List<double[]> vertices) {
        double[] a = vertices.get(0);
        double[] b = vertices.get(1);
        double[] d = vertices.get(3);
        double ax = b[0] - a[0];
        double ay = b[1] - a[1];
        double az = b[2] - a[2];
        double bx = d[0] - a[0];
        double by = d[1] - a[1];
        double bz = d[2] - a[2];
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        if (Math.abs(nx) >= Math.abs(ny) && Math.abs(nx) >= Math.abs(nz)) {
            return nx >= 0 ? "east" : "west";
        }
        if (Math.abs(ny) >= Math.abs(nz)) {
            return ny >= 0 ? "up" : "down";
        }
        return nz >= 0 ? "south" : "north";
    }

    private static double[][] orderedUv(List<double[]> vertices, String face, double[] low, double[] high, int turns) {
        double x0 = low[0];
        double x1 = high[0];
        double y0 = low[1];
        double y1 = high[1];
        double z0 = low[2];
        double z1 = high[2];
        double[][] corners;
        switch (face) {
            case "east":
                corners = new double[][]{{x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}};
                break;
            case "west":
                corners = new double[][]{{x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}};
                break;
            case "up":
                corners = new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                break;
            case "down":
                corners = new double[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
                break;
            case "south":
                corners = new double[][]{{x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}};
                break;
            default:
                corners = new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}};
        }
        double[][] result = new double[4][2];
        for (int i = 0; i < corners.length; i++) {
            for (int turn = 0; turn < turns; turn++) {
                double centerX = (low[0] + high[0]) / 2;
                double centerZ = (low[2] + high[2]) / 2;
                double x = corners[i][0] - centerX;
                double z = corners[i][2] - centerZ;
                corners[i][0] = centerX - z;
                corners[i][2] = centerZ + x;
            }
            double distance = Double.POSITIVE_INFINITY;
            for (double[] vertex : vertices) {
                double next = Math.abs(vertex[0] - corners[i][0]) + Math.abs(vertex[1] - corners[i][1])
                        + Math.abs(vertex[2] - corners[i][2]);
                if (next < distance) {
                    distance = next;
                    result[i][0] = vertex[3];
                    result[i][1] = vertex[4];
                }
            }
        }
        return result;
    }

    private static boolean rectangularUv(double[][] uv, boolean horizontal) {
        double tolerance = 0.0001;
        if (horizontal) {
            return Math.abs(uv[0][0] - uv[1][0]) < tolerance && Math.abs(uv[1][1] - uv[2][1]) < tolerance
                    && Math.abs(uv[2][0] - uv[3][0]) < tolerance && Math.abs(uv[3][1] - uv[0][1]) < tolerance;
        }
        return Math.abs(uv[0][1] - uv[1][1]) < tolerance && Math.abs(uv[1][0] - uv[2][0]) < tolerance
                && Math.abs(uv[2][1] - uv[3][1]) < tolerance && Math.abs(uv[3][0] - uv[0][0]) < tolerance;
    }

    private static void appendAscendingRail(StringBuilder json) {
        double length = Math.sqrt(512);
        json.append("{\"origin\":[-8,9,").append(-length / 2);
        json.append("],\"size\":[16,0,").append(length);
        json.append("],\"pivot\":[0,9,0],\"rotation\":[45,0,0],\"uv\":{");
        appendFaceUv(json, "up", "default");
        json.append(',');
        appendFaceUv(json, "down", "default");
        json.append("}}");
    }

    private static void appendWallTorch(StringBuilder json) {
        json.append("{\"origin\":[-1,2,4],\"size\":[2,12,2],");
        json.append("\"pivot\":[0,3,5],\"rotation\":[22.5,0,0],\"uv\":{");
        appendAllFaces(json);
        json.append("}}");
    }

    private static void appendLever(StringBuilder json, List<double[]> vertices, Capture capture, String part) {
        for (int offset = 0; offset < 24; offset += 4) {
            if (offset != 0) {
                json.append(',');
            }
            appendFace(json, vertices.subList(offset, offset + 4), capture, part);
        }
        double[] bottom = center(vertices.subList(24, 28));
        double[] top = center(vertices.subList(28, 32));
        double dx = (top[0] - bottom[0]) * 16;
        double dy = (top[1] - bottom[1]) * 16;
        double dz = (top[2] - bottom[2]) * 16;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double pivotX = (bottom[0] - 0.5) * 16;
        double pivotY = bottom[1] * 16;
        double pivotZ = (bottom[2] - 0.5) * 16;
        double rotationX = Math.abs(dz) > Math.abs(dx) ? -Math.toDegrees(Math.atan2(dz, dy)) : 0;
        double rotationZ = Math.abs(dx) >= Math.abs(dz) ? -Math.toDegrees(Math.atan2(dx, dy)) : 0;
        json.append(", {\"origin\":[").append(-pivotX - 1).append(',').append(pivotY).append(',').append(pivotZ - 1)
                .append("],\"size\":[2,").append(length).append(",2],");
        json.append("\"pivot\":[").append(-pivotX).append(',').append(pivotY).append(',').append(pivotZ);
        json.append("],\"rotation\":[").append(rotationX).append(",0,").append(rotationZ);
        json.append("],\"uv\":{");
        appendAllFaces(json);
        json.append("}}");
    }

    private static double[] center(List<double[]> vertices) {
        double[] result = new double[3];
        for (double[] vertex : vertices) {
            for (int axis = 0; axis < 3; axis++) {
                result[axis] += vertex[axis] / vertices.size();
            }
        }
        return result;
    }

    private static void verifyCapturedFaces(String name, Capture capture, ModelGeometry geometry) throws IOException {
        for (Map.Entry<String, List<double[]>> part : capture.parts.entrySet()) {
            ModelGeometry.Bone bone = geometry.bones.get(geometry.index(part.getKey()));
            if (bone.faces.size() != part.getValue().size() / 4) {
                throw new IOException(name + ": generated face count differs from Beta rendering");
            }
            for (int index = 0; index < bone.faces.size(); index++) {
                ModelGeometry.Quad face = bone.faces.get(index);
                List<double[]> nativeVertices = part.getValue().subList(index * 4, index * 4 + 4);
                double minU = 1;
                double minV = 1;
                for (double[] vertex : nativeVertices) {
                    minU = Math.min(minU, vertex[3]);
                    minV = Math.min(minV, vertex[4]);
                }
                int tileX = Math.max(0, (int) Math.floor(minU * 16 + 0.001));
                int tileY = Math.max(0, (int) Math.floor(minV * 16 + 0.001));
                for (double[] vertex : nativeVertices) {
                    double x = (vertex[0] - 0.5) * 16;
                    double y = vertex[1] * 16;
                    double z = (vertex[2] - 0.5) * 16;
                    boolean matched = false;
                    for (int corner = 0; corner < 4; corner++) {
                        ModelVector position = face.vertices.get(corner);
                        ModelVector uv = face.uv.get(corner);
                        if (Math.abs(position.x - x) < 0.0001 && Math.abs(position.y - y) < 0.0001
                                && Math.abs(position.z - z) < 0.0001
                                && Math.abs(uv.x - (vertex[3] * 16 - tileX)) < 0.0001
                                && Math.abs(uv.y - (vertex[4] * 16 - tileY)) < 0.0001) {
                            matched = true;
                        }
                    }
                    if (!matched) {
                        throw new IOException(name + ": geometry or UV differs from Beta face " + index + " in part "
                                + part.getKey() + "; native=" + x + "," + y + "," + z + "/" + (vertex[3] * 16 - tileX)
                                + "," + (vertex[4] * 16 - tileY) + "; imported=" + face.vertices.get(0).x + ","
                                + face.vertices.get(0).y + "," + face.vertices.get(0).z + "/" + face.uv.get(0).x + ","
                                + face.uv.get(0).y);
                    }
                }
            }
        }
    }

    private static void appendFaceUv(StringBuilder json, String face, String material) {
        json.append('"').append(face).append("\":{\"uv\":[0,0],\"uv_size\":[16,16],");
        json.append("\"material_instance\":\"").append(material).append("\"}");
    }

    private static void appendAllFaces(StringBuilder json) {
        String[] faces = {"east", "west", "up", "down", "south", "north"};
        for (int i = 0; i < faces.length; i++) {
            if (i != 0) {
                json.append(',');
            }
            appendFaceUv(json, faces[i], "default");
        }
    }

    private static final class Capture extends Tessellator {
        private final Map<String, List<double[]>> parts = new LinkedHashMap<>();
        private String current = "root";

        private void part(String name) {
            current = name;
            parts.computeIfAbsent(name, ignored -> new ArrayList<>());
        }

        @Override
        public void addVertexWithUV(double x, double y, double z, double u, double v) {
            parts.computeIfAbsent(current, ignored -> new ArrayList<>()).add(new double[]{x, y, z, u, v});
        }

        @Override
        public void setColorOpaque_F(float red, float green, float blue) {
        }

        private String material(int tile, String part) {
            if (part.equals("upper") || part.equals("lower")) {
                return part;
            }
            if (part.equals("head") || part.equals("foot")) {
                if (tile == 4) {
                    return "bottom";
                }
                return part + (tile == 134 || tile == 135 ? "_top" : "_side");
            }
            return "default";
        }
    }
}
