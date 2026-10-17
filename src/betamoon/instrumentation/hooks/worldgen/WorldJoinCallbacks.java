package betamoon.instrumentation.hooks.worldgen;

import betamoon.gui.GuiMissingWorldGenerator;
import betamoon.gui.GuiWorldGenerationError;
import betamoon.worldgen.creation.MissingWorldGenerator;
import betamoon.worldgen.creation.WorldGeneratorFallback;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiSelectWorld;
import net.minecraft.src.PlayerControllerSP;
import net.minecraft.src.SaveFormatComparator;
import net.minecraft.src.WorldInfo;

public final class WorldJoinCallbacks {
    private WorldJoinCallbacks() {
    }

    public static void worldJoinFinished() {
    }

    public static int startingWorldLoad() {
        WorldGeneratorFallback.startingWorld();
        return 0;
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
            Path root = new File(Minecraft.getMinecraftDir(), "saves").toPath().toAbsolutePath().normalize();
            Path directory = root.resolve(folder).normalize();
            if (!directory.startsWith(root) || directory.equals(root)) {
                throw new IOException("Invalid world save folder");
            }
            WorldInfo info = minecraft.getSaveLoader().func_22173_b(folder);
            if (info == null) {
                return 0;
            }
            MissingWorldGenerator missing = MissingWorldGenerator.inspect(
                    directory.resolve("data/betamoon_generation_settings.dat").toFile(),
                    directory.resolve("data/betamoon_generator_overworld.dat").toFile(), info.getRandomSeed());
            if (missing == null) {
                return 0;
            }
            minecraft.displayGuiScreen(
                    new GuiMissingWorldGenerator(parent, name, missing, () -> load(minecraft, folder, name, missing)));
        } catch (IOException | IllegalArgumentException exception) {
            minecraft.displayGuiScreen(new GuiWorldGenerationError(parent, exception.getMessage()));
        }
        return 1;
    }

    private static void load(Minecraft minecraft, String folder, String name, MissingWorldGenerator missing) {
        minecraft.displayGuiScreen(null);
        minecraft.playerController = new PlayerControllerSP(minecraft);
        try (WorldGeneratorFallback permission = WorldGeneratorFallback.begin(missing)) {
            minecraft.startWorld(folder, name, 0L);
        }
        minecraft.displayGuiScreen(null);
    }

}
