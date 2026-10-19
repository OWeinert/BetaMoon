package betamoon.worldgen.terrain;

import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.ScalarField;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Bounded CSV exports with generated names contained in the caller's explicit
 * debug directory.
 */
public final class GenerationDiagnosticExport {
    private GenerationDiagnosticExport() {
    }

    public static String field(Path root, ScalarField field, FieldContext context, Map<String, Object> options)
            throws IOException {
        fields(options, "field.exportSlice", "axis", "startX", "startY", "startZ", "width", "height", "step");
        String axis = string(options, "axis", "xz", "field.exportSlice");
        if (!axis.equals("xz") && !axis.equals("xy") && !axis.equals("yz")) {
            throw error("field.exportSlice.axis", "expected xz, xy or yz");
        }
        double x = number(options, "startX", 0, "field.exportSlice");
        double y = number(options, "startY", 0, "field.exportSlice");
        double z = number(options, "startZ", 0, "field.exportSlice");
        int width = integer(options.getOrDefault("width", 16.0), "field.exportSlice.width", 1, 256);
        int height = integer(options.getOrDefault("height", 16.0), "field.exportSlice.height", 1, 256);
        double step = number(options, "step", 1, "field.exportSlice");
        if (step <= 0 || step > 1024 || (long) width * height * field.sampleWork > 8000000) {
            throw error("field.exportSlice", "sampling step or work budget exceeded");
        }
        StringBuilder csv = new StringBuilder("x,y,z,value\n");
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                double sx = x + (axis.equals("yz") ? 0 : column * step);
                double sy = y + (axis.equals("xy") ? row * step : axis.equals("yz") ? column * step : 0);
                double sz = z + (axis.equals("xz") || axis.equals("yz") ? row * step : 0);
                csv.append(sx).append(',').append(sy).append(',').append(sz).append(',')
                        .append(field.sample(context, sx, sy, sz)).append('\n');
            }
        }
        return write(root, "field-slice", csv.toString());
    }

    public static String chunk(Path root, Map<String, Object> report) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> maps = (Map<String, Object>) report.get("heightmaps");
        List<?> surface = (List<?>) maps.get("world_surface");
        List<?> floor = (List<?>) maps.get("ocean_floor");
        List<?> fluid = (List<?>) maps.get("fluid_surface");
        List<?> motion = (List<?>) maps.get("motion_blocking");
        int chunkX = ((Number) report.get("chunkX")).intValue();
        int chunkZ = ((Number) report.get("chunkZ")).intValue();
        StringBuilder csv = new StringBuilder(
                "profile,signature,rawHash,x,z,world_surface,ocean_floor,fluid_surface,motion_blocking\n");
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = z * 16 + x;
                csv.append(report.get("key")).append(',').append(report.get("signature")).append(',')
                        .append(report.get("rawHash")).append(',').append(chunkX * 16 + x).append(',')
                        .append(chunkZ * 16 + z).append(',').append(surface.get(index)).append(',')
                        .append(floor.get(index)).append(',').append(fluid.get(index)).append(',')
                        .append(motion.get(index)).append('\n');
            }
        }
        return write(root, "terrain-chunk", csv.toString());
    }

    private static String write(Path root, String prefix, String csv) throws IOException {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 8 * 1024 * 1024) {
            throw new IOException("Generation export exceeds its 8 MiB budget");
        }
        Files.createDirectories(root);
        Path directory = root.toRealPath();
        Path output = directory.resolve(prefix + "-" + UUID.randomUUID() + ".csv").normalize();
        if (!output.getParent().equals(directory)) {
            throw new IOException("Generation export escaped its debug directory");
        }
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return output.toString();
    }
}
