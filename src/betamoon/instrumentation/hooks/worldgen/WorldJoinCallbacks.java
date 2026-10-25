package betamoon.instrumentation.hooks.worldgen;

import betamoon.gui.GuiMissingWorldGenerator;
import betamoon.gui.GuiWorldGenerationError;
import betamoon.luaapi.material.WorldMaterialSignatures;
import betamoon.teleport.SavedDimensionLoad;
import betamoon.teleport.TeleportRuntime;
import betamoon.worldgen.creation.MissingWorldGenerator;
import betamoon.worldgen.creation.WorldGeneratorFallback;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiSelectWorld;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.PlayerControllerSP;
import net.minecraft.src.SaveFormatComparator;
import net.minecraft.src.WorldInfo;

public final class WorldJoinCallbacks {
    private static final ThreadLocal<Boolean> LOADING = new ThreadLocal<Boolean>();
    private WorldJoinCallbacks() {
    }

    public static void worldJoinFinished() {
    }

    public static int startingWorldLoad(Minecraft minecraft, String folder, String name, long seed) {
        if (Boolean.TRUE.equals(LOADING.get())) {
            return 0;
        }
        if (!TeleportRuntime.startingWorldLoad()) {
            return 1;
        }
        Path directory;
        try {
            directory = worldDirectory(folder);
            WorldMaterialSignatures.preflight(directory);
        } catch (IOException error) {
            minecraft.displayGuiScreen(new GuiWorldGenerationError(minecraft.currentScreen, error.getMessage()));
            return 1;
        }
        WorldGeneratorFallback.startingWorld();
        if (SavedDimensionLoad.start(minecraft, folder, name) != 0) {
            return 1;
        }
        GuiScreen parent = minecraft.currentScreen;
        LOADING.set(Boolean.TRUE);
        try {
            minecraft.startWorld(folder, name, seed);
            if (minecraft.theWorld != null) {
                try {
                    WorldMaterialSignatures.record(directory);
                } catch (IOException error) {
                    minecraft.changeWorld1(null);
                    minecraft.displayGuiScreen(new GuiWorldGenerationError(parent,
                            "Could not save world material dependencies: " + error.getMessage()));
                }
            }
        } catch (RuntimeException exception) {
            // Native construction has not installed a world yet. Never save a partially
            // constructed replacement while reporting an incompatible generator.
            if (minecraft.theWorld != null) {
                throw exception;
            }
            minecraft.displayGuiScreen(new GuiWorldGenerationError(parent, exception.getMessage()));
        } finally {
            LOADING.remove();
        }
        return 1;
    }

    public static int preflight(Minecraft minecraft, GuiSelectWorld parent, List<?> saves, int index) {
        if (index < 0 || index >= saves.size() || !(saves.get(index) instanceof SaveFormatComparator)) {
            return 1;
        }
        SaveFormatComparator save = (SaveFormatComparator) saves.get(index);
        String folder = save.getFileName();
        String name = save.getDisplayName() == null || save.getDisplayName().trim().isEmpty()
                ? folder
                : save.getDisplayName();
        try {
            Path directory = worldDirectory(folder);
            WorldMaterialSignatures.preflight(directory);
            WorldInfo info = minecraft.getSaveLoader().func_22173_b(folder);
            if (info == null) {
                return 0;
            }
            MissingWorldGenerator missing = MissingWorldGenerator.inspect(
                    directory.resolve("data/betamoon_generation_settings.dat").toFile(),
                    directory.resolve("data/betamoon_generator_overworld.dat").toFile(), info.getRandomSeed());
            if (missing == null) {
                load(minecraft, folder, name, null);
                return 1;
            }
            minecraft.displayGuiScreen(
                    new GuiMissingWorldGenerator(parent, name, missing, () -> load(minecraft, folder, name, missing)));
        } catch (IOException | RuntimeException exception) {
            minecraft.displayGuiScreen(new GuiWorldGenerationError(parent, exception.getMessage()));
        }
        return 1;
    }

    private static void load(Minecraft minecraft, String folder, String name, MissingWorldGenerator missing) {
        minecraft.playerController = new PlayerControllerSP(minecraft);
        try (WorldGeneratorFallback permission = missing == null ? null : WorldGeneratorFallback.begin(missing)) {
            minecraft.startWorld(folder, name, 0L);
        }
        if (minecraft.theWorld != null) {
            minecraft.displayGuiScreen(null);
        }
    }

    private static Path worldDirectory(String folder) throws IOException {
        Path root = new File(Minecraft.getMinecraftDir(), "saves").toPath().toAbsolutePath().normalize();
        Path directory = root.resolve(folder).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) {
            throw new IOException("Invalid world save folder");
        }
        return directory;
    }

}
