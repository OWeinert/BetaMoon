package betamoon.worldgen.creation;

import betamoon.worldgen.GenerationDimension;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.terrain.GeneratorProfileRegistry;
import betamoon.worldgen.terrain.PopulationPolicy;
import betamoon.worldgen.terrain.ProfileWorldChunkManager;
import betamoon.worldgen.terrain.TerrainFoundationTest;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.ChunkCoordinates;

/**
 * Saved eligibility, parent precedence, migration/corruption, and world
 * isolation.
 */
public final class GenerationCreationTest {
    public static void main(String[] args) throws Exception {
        require(Block.stone != null, "Initialize native content");
        drafts();
        storage();
        biomes();
        worlds();
        content();
        System.out.println("Creation settings passed: drafts, parent precedence, biome coverage, schema migration, "
                + "corruption rejection, world isolation and native reload.");
    }

    private static GenerationDraft minecraft() {
        return new GenerationDraft(new GenerationCatalog(null));
    }

    private static void drafts() {
        GenerationDraft draft = minecraft();
        require(!draft.modified(), "Opening settings does not mark a preset modified");
        draft.toggle("native/ores");
        require(draft.modified(), "Remembered child edits mark the draft");
        draft.toggle("group/ores");
        require(!draft.editable(draft.catalog.options.get("native/ores")), "Disabled parents lock children");
        draft.toggle("native/ores");
        require(!draft.selected("native/ores"), "Locked child cannot be changed");
        draft.toggle("group/ores");
        require(!draft.enabled("native/ores"), "Re-enabling parent restores child's own choice");
        draft.toggle("native/ores");
        require(!draft.modified(), "Manual baseline restoration clears modified state");
        GenerationDraft copy = draft.copy();
        copy.toggle("native/trees");
        require(!draft.modified() && copy.modified(), "Cancel discards independent visit edits");
        copy.reset();
        require(!copy.modified(), "Reset clears remembered edits");
        copy.toggle("group/ores");
        GenerationSelection selection = GenerationSelection.create(copy, 13);
        require(copy.selected("native/ores") && !selection.enabled("native/ores"),
                "Parent precedence survives resolution independently of remembered checkmarks");
        require(!selection.contentEnabled("new:placement/ore"), "New entries do not enter a resolved save");
        try (WorldCreationSelection pending = WorldCreationSelection.begin(selection)) {
            require(WorldCreationSelection.consume() == selection && WorldCreationSelection.consume() == null,
                    "Creation handoff is consumed once");
        }
        require(WorldCreationSelection.consume() == null, "Creation handoff cannot leak into another world");
    }

    private static void storage() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-creation-settings-");
        Path file = directory.resolve("settings.dat");
        GenerationSelectionStore store = new GenerationSelectionStore(file.toFile());
        require(store.read() == null, "Missing settings stay missing");
        GenerationDraft draft = minecraft();
        draft.toggle("group/ores");
        draft.toggle("biome/minecraft:biome/desert");
        GenerationSelection original = GenerationSelection.create(draft, Long.MIN_VALUE + 9);
        store.write(original);
        GenerationSelection loaded = store.read();
        require(loaded.seed == original.seed && loaded.choices.equals(original.choices)
                && loaded.enabled.equals(original.enabled) && loaded.climate.equals(original.climate),
                "Complete resolved configuration round trips");
        require(loaded.choices.get("native/ores") && !loaded.enabled("native/ores"),
                "Stored parent state dominates remembered child");
        byte[] valid = Files.readAllBytes(file);
        for (int length : new int[]{0, 8, valid.length - 1}) {
            Files.write(file, Arrays.copyOf(valid, length));
            expect(IOException.class, store::read);
        }
        byte[] damaged = valid.clone();
        damaged[20] ^= 1;
        Files.write(file, damaged);
        expect(IOException.class, store::read);
        Files.write(file, new byte[4 * 1024 * 1024 + 1]);
        expect(IOException.class, store::read);
        Files.write(file, GenerationSelectionStore.encode(original, 1));
        require(store.read().enabled.equals(original.enabled), "Version 1 migration preserves resolved choices");
        require(Files.exists(directory.resolve("settings.dat.schema-1.bak")), "Migration keeps a backup");
        require(Files.readAllBytes(file)[7] == GenerationSelectionStore.SCHEMA_VERSION,
                "Migration publishes the current schema atomically");
        damaged = valid.clone();
        damaged[7] = 99;
        Files.write(file, damaged);
        expect(IOException.class, store::read);
        require(Files.readAllBytes(file)[7] == 99, "Future schemas are not rewritten or reset");
        Map<String, List<String>> cycle = new LinkedHashMap<String, List<String>>(original.parents);
        cycle.put("group/ores", Collections.singletonList("native/ores"));
        expect(IllegalArgumentException.class, () -> new GenerationSelection(original.seed, original.preset,
                original.choices, cycle, original.dependencies, original.profileDependencies, original.climate));
    }

    private static void biomes() throws Exception {
        GenerationDraft draft = minecraft();
        draft.toggle("biome/minecraft:biome/desert");
        GenerationSelection selection = GenerationSelection.create(draft, 47);
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(null, 47);
        ProfileWorldChunkManager manager = new ProfileWorldChunkManager(world.getWorldChunkManager(), null, selection);
        for (BiomeGenBase biome : manager.loadBlockGeneratorData(null, -320, 71, 16, 11)) {
            require(biome != BiomeGenBase.desert, "Disabled biome cannot appear in rectangular live queries");
        }
        for (GenerationOption option : draft.catalog.options.values()) {
            if (option.key.startsWith("biome/") && draft.selected(option.key)) {
                draft.toggle(option.key);
            }
        }
        expect(IllegalArgumentException.class, () -> GenerationSelection.create(draft, 47));
    }

    private static void worlds() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-selected-world-");
        TerrainFoundationTest.Save save = new TerrainFoundationTest.Save(directory);
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(save, 5);
        world.isNewWorld = true;
        GenerationDraft draft = minecraft();
        draft.toggle("group/ores");
        draft.toggle("stage/vegetation");
        GenerationSelection selected = GenerationSelection.create(draft, 5);
        try (WorldCreationSelection pending = WorldCreationSelection.begin(selected)) {
            WorldGenerationRuntime.prepare(world, save);
        }
        require(!WorldGenerationRuntime.nativeCategoryEnabled(world, "ores")
                && !WorldGenerationRuntime.stageEnabled(world, GenerationStage.VEGETATION),
                "Saved settings route into native categories and owned stages");
        world.setSpawnPoint(new ChunkCoordinates(1, 65, 2));
        WorldGenerationRuntime.recordNativeSpawn(world);
        TerrainFoundationTest.Fixture reopen = new TerrainFoundationTest.Fixture(save, 5);
        WorldGenerationRuntime.prepare(reopen, save);
        require(!WorldGenerationRuntime.nativeCategoryEnabled(reopen, "ores"), "Reload restores saved toggles");
        expect(IllegalStateException.class,
                () -> WorldGenerationRuntime.prepare(new TerrainFoundationTest.Fixture(save, 6), save));
        TerrainFoundationTest.Save another = new TerrainFoundationTest.Save(directory.resolve("another"));
        TerrainFoundationTest.Fixture other = new TerrainFoundationTest.Fixture(another, 5);
        other.isNewWorld = true;
        WorldGenerationRuntime.prepare(other, another);
        require(WorldGenerationRuntime.nativeCategoryEnabled(other, "ores"), "Selections do not leak across worlds");
        WorldGeneratorProfile profile = new WorldGeneratorProfile("test:generator/named", "creation.lua",
                Collections.<String, String>emptyMap());
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.singletonList(profile));
        GenerationSelection custom = GenerationSelection.create(new GenerationDraft(new GenerationCatalog(profile)), 2);
        WorldGeneratorProfile changedDefaults = new WorldGeneratorProfile(profile.key, profile.owner,
                Collections.<String, String>emptyMap(), null,
                new PopulationPolicy(profile.key, Collections.<String, Object>singletonMap("native", "none")), null,
                null, true, true);
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.singletonList(changedDefaults));
        custom.validate(2);
        require(custom.profile().population.nativeCategories.size() == 9,
                "Saved switches take precedence over changed preset population defaults");
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.<WorldGeneratorProfile>emptyList());
        expect(IllegalStateException.class, () -> custom.validate(2));
    }

    private static void content() throws Exception {
        WorldGenRegistry.clear();
        publishOres(3, true, false, "Copper");
        GenerationDraft draft = minecraft();
        String alpha = "placement/test:placement/alpha";
        String beta = "placement/test:placement/beta";
        require(draft.catalog.options.containsKey(alpha) && draft.catalog.options.containsKey(beta),
                "Overworld ore placements must appear individually");
        require(draft.catalog.options.get(alpha).label.equals("Copper"), "Optional display names reach the catalog");
        draft.toggle(beta);
        GenerationSelection selection = GenerationSelection.create(draft, 9);
        draft.toggle("group/ores");
        GenerationSelection noOres = GenerationSelection.create(draft, 9);
        require(draft.selected(alpha) && !noOres.contentEnabled("test:placement/alpha"),
                "The ore parent disables registered ore children");
        publishOres(3, false, true, "Renamed Copper");
        selection.validate(9);
        WorldGenRegistry.OreGenEntry entry = WorldGenRegistry
                .oreEntry(WorldGenKey.parse("test:alpha", WorldGenKind.PLACEMENT));
        entry.enabled = false;
        WorldGenRegistry.refreshOre(entry);
        selection.validate(9);
        require(selection.contentEnabled("test:placement/alpha"), "Changed defaults cannot erase saved ore choices");
        require(!selection.contentEnabled("test:placement/gamma"), "New registrations stay out of saved worlds");
        publishOres(4, false, true, "Renamed Copper");
        expect(IllegalStateException.class, () -> selection.validate(9));
        noOres.validate(9);
        WorldGenRegistry.clear();
        noOres.validate(9);
        expect(IllegalStateException.class, () -> selection.validate(9));
    }

    private static void publishOres(int veins, boolean beta, boolean gamma, String name) {
        try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication("creation_ores.lua",
                "Creation ores")) {
            WorldGenKey alpha = WorldGenRegistry.addOreGen("test:alpha", Block.oreIron.blockID, veins, 8, 2, 40,
                    GenerationDimension.OVERWORLD, null, null, 1);
            WorldGenRegistry.setDisplayName(alpha, name);
            if (beta) {
                WorldGenRegistry.addOreGen("test:beta", Block.oreGold.blockID, 1, 4, 2, 40,
                        GenerationDimension.OVERWORLD, null, null, 2);
            }
            if (gamma) {
                WorldGenRegistry.addOreGen("test:gamma", Block.oreCoal.blockID, 1, 4, 2, 40,
                        GenerationDimension.OVERWORLD, null, null, 3);
            }
            batch.publish();
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void expect(Class<? extends Exception> type, Checked action) throws Exception {
        try {
            action.run();
        } catch (Exception exception) {
            if (type.isInstance(exception)) {
                return;
            }
            throw exception;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private interface Checked {
        void run() throws Exception;
    }
}
