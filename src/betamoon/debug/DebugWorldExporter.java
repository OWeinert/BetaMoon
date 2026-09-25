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
        exportFeatures(session);
        exportPlacements(session);
        exportStructures(session);
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
                return left.key.compareTo(right.key);
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
                    writer.write("key: " + generator.key + " | feature: " + generator.featureKey + " | owner: "
                            + safe(generator.owner));
                    writer.newLine();
                    writer.write("stage: " + generator.stage + " | source: " + safe(generator.sourceLocation)
                            + " | salt: " + generator.salt);
                    writer.newLine();
                    writer.write("placed block ID: " + generator.blockId + " | dimension: " + generator.dimension);
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

    private static void exportFeatures(DebugExportSession session) throws Exception {
        final List<WorldGenRegistry.FeatureDescription> features = WorldGenRegistry.featureSnapshot();
        session.writeTextFile("worldgen_features.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (WorldGenRegistry.FeatureDescription feature : features) {
                    writer.write("key: " + feature.key + " | type: " + feature.type + " | owner: "
                            + safe(feature.owner));
                    writer.newLine();
                    writer.write("source: " + safe(feature.source) + " | max blocks: " + feature.maxBlocks
                            + " | max radius: " + feature.maxRadius + " | dependencies: "
                            + (feature.dependencies.isEmpty() ? "none" : String.join(", ", feature.dependencies)));
                    writer.newLine();
                }
                return features.size();
            }
        });
    }

    private static void exportPlacements(DebugExportSession session) throws Exception {
        final List<WorldGenRegistry.PlacementDescription> placements = WorldGenRegistry.placementSnapshot();
        session.writeTextFile("worldgen_placements.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (WorldGenRegistry.PlacementDescription placement : placements) {
                    writer.write("key: " + placement.key + " | feature: " + placement.feature + " | owner: "
                            + safe(placement.owner));
                    writer.newLine();
                    writer.write("requested stage: " + placement.stage + " | actual stage: " + placement.actualStage
                            + " | dimensions: " + String.join(", ", placement.dimensions) + " | salt: "
                            + placement.salt);
                    writer.newLine();
                    writer.write("accepted: " + placement.accepted + " | rejected: " + placement.rejected
                            + " | blocks changed: " + placement.blocksChanged + " | disabled: "
                            + placement.disabled + " | rejection reasons: " + placement.rejectionReasons);
                    writer.newLine();
                }
                return placements.size();
            }
        });
    }

    private static void exportStructures(DebugExportSession session) throws Exception {
        final List<WorldGenRegistry.StructureDescription> structures = WorldGenRegistry.structureSnapshot();
        session.writeTextFile("worldgen_structures.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (WorldGenRegistry.StructureDescription structure : structures) {
                    writer.write("key: " + structure.key + " | owner: " + safe(structure.owner) + " | source: "
                            + safe(structure.assetSource));
                    writer.newLine();
                    writer.write("dimensions: " + structure.dimensions + " | blocks: " + structure.blocks
                            + " | markers: " + structure.markers + " | palette entries: "
                            + structure.paletteEntries + " | variants: " + structure.paletteVariants);
                    writer.newLine();
                    writer.write("rotation: " + structure.rotation + " | mirror: " + structure.mirror
                            + " | include air: " + structure.includeAir + " | decay: " + structure.decay
                            + " | tile collision: " + structure.tileCollision + " | unknown metadata: "
                            + structure.unknownMetadata + " | custom metadata transforms: "
                            + structure.customMetadataTransforms);
                    writer.newLine();
                }
                return structures.size();
            }
        });
    }
}
