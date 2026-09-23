package betamoon.debug;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.src.BiomeGenBase;

/** Exports biome identities, Lua climate overlays, and world generators. */
final class DebugWorldExporter implements DebugExporter {
    @Override
    public void export(DebugExportSession session) throws Exception {
        exportBiomes(session);
        exportWorldGeneration(session);
    }

    private static void exportBiomes(DebugExportSession session) throws Exception {
        final List<String> rows = new ArrayList<String>();
        for (Field field : BiomeGenBase.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !BiomeGenBase.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                BiomeGenBase biome = (BiomeGenBase) field.get(null);
                if (biome != null) {
                    rows.add("origin: minecraft/foreign | lookup name: " + safe(field.getName()) + " | biome name: "
                            + safe(biome.biomeName) + " | implementation: " + safe(biome.getClass().getName()));
                }
            } catch (IllegalAccessException ignored) {
                // Public fields should be accessible; inaccessible mod fields are omitted.
            }
        }
        for (BiomeGenRegistry.Description biome : BiomeGenRegistry.snapshot()) {
            rows.add("origin: Lua overlay | owner: " + safe(biome.owner) + " | biome name: " + safe(biome.name)
                    + " | implementation: " + safe(biome.implementation) + " | temperature: "
                    + biome.minTemperature + ".." + biome.maxTemperature + " | humidity: " + biome.minHumidity
                    + ".." + biome.maxHumidity);
        }
        Collections.sort(rows);
        session.writeTextFile("biomes.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (String row : rows) {
                    writer.write(row);
                    writer.newLine();
                }
                return rows.size();
            }
        });
    }

    private static void exportWorldGeneration(DebugExportSession session) throws Exception {
        final List<WorldGenRegistry.Description> generators = new ArrayList<WorldGenRegistry.Description>(
                WorldGenRegistry.snapshot());
        Collections.sort(generators, new Comparator<WorldGenRegistry.Description>() {
            @Override
            public int compare(WorldGenRegistry.Description left, WorldGenRegistry.Description right) {
                int block = Integer.compare(left.blockId, right.blockId);
                if (block != 0) {
                    return block;
                }
                int dimension = left.dimension.compareTo(right.dimension);
                return dimension != 0 ? dimension : Integer.compare(left.minY, right.minY);
            }
        });
        session.writeTextFile("world_generation.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (int index = 0; index < generators.size(); index++) {
                    if (index > 0) {
                        writer.newLine();
                    }
                    WorldGenRegistry.Description generator = generators.get(index);
                    writer.write("owner: " + safe(generator.owner) + " | placed block ID: " + generator.blockId
                            + " | dimension: " + generator.dimension);
                    writer.newLine();
                    writer.write("veins per chunk: " + generator.veinsPerChunk + " | vein size: "
                            + generator.veinSize + " | height: " + generator.minY + ".." + generator.maxY);
                    writer.newLine();
                    writer.write("replacement block ID: "
                            + (generator.targetBlockId == null ? "dimension default" : generator.targetBlockId)
                            + " | biome filter: "
                            + (generator.biomes.isEmpty() ? "all" : String.join(", ", generator.biomes)));
                    writer.newLine();
                }
                return generators.size();
            }
        });
    }

    private static String safe(String value) {
        return DebugExportNames.safeString(value == null || value.isEmpty() ? "unavailable" : value);
    }
}
