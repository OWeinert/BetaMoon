package betamoon.worldgen.creation;

import betamoon.worldgen.terrain.GeneratorManifestStore;
import betamoon.worldgen.terrain.GeneratorProfileRegistry;
import betamoon.worldgen.terrain.WorldGeneratorManifest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only preflight: no save handler, session lock, migration or generation.
 */
public final class MissingWorldGenerator {
    public final File settingsFile;
    public final String profileKey;
    public final long seed;
    public final List<String> missing;

    private MissingWorldGenerator(File settingsFile, String profileKey, long seed, List<String> missing) {
        this.settingsFile = settingsFile.getAbsoluteFile();
        this.profileKey = profileKey;
        this.seed = seed;
        this.missing = Collections.unmodifiableList(new ArrayList<String>(missing));
    }

    public static void requireMatchingManifest(File file, String profile, long seed) throws IOException {
        WorldGeneratorManifest manifest = new GeneratorManifestStore(file).read();
        if (manifest == null || manifest.worldSeed != seed || manifest.dimension != 0
                || !manifest.profileKey.equals(profile)) {
            throw new IOException(
                    "Generation settings have no matching generator manifest; restore a consistent backup");
        }
    }

    public static MissingWorldGenerator inspect(File settings, File manifestFile, long seed) throws IOException {
        GenerationSelection selection = new GenerationSelectionStore(settings).read(false);
        if (selection != null) {
            if (selection.seed != seed) {
                throw new IOException("Generation settings belong to a different world seed");
            }
            if (!selection.preset.equals(GenerationCatalog.MINECRAFT)
                    && GeneratorProfileRegistry.snapshot().get(selection.preset) == null) {
                requireMatchingManifest(manifestFile, selection.preset, seed);
                return new MissingWorldGenerator(settings, selection.preset, seed, selection.missingRequirements());
            }
            return null;
        }
        WorldGeneratorManifest manifest = new GeneratorManifestStore(manifestFile).read();
        if (manifest != null) {
            if (manifest.worldSeed != seed || manifest.dimension != 0) {
                throw new IOException("Generator manifest belongs to a different world seed or dimension");
            }
            if (GeneratorProfileRegistry.snapshot().get(manifest.profileKey) == null) {
                return new MissingWorldGenerator(settings, manifest.profileKey, seed,
                        Collections.singletonList(manifest.profileKey));
            }
        }
        return null;
    }
}
