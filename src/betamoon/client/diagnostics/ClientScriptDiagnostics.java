package betamoon.client.diagnostics;

import betamoon.dimension.DimensionGenerationBinding;
import betamoon.dimension.DimensionIdentity;
import betamoon.dimension.DimensionRuntime;
import betamoon.gui.GuiScreenNames;
import betamoon.luamodloader.ScriptDiagnostics;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.BaseMod;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;
import org.lwjgl.Sys;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/** Supplies read-only client identity; never loads chunks, opens saves, or requests a GL context. */
public final class ClientScriptDiagnostics implements ScriptDiagnostics.ClientContext {
    private ClientScriptDiagnostics() {
    }

    public static void initialize() {
        ScriptDiagnostics.initializeClient(new ClientScriptDiagnostics());
    }

    @Override
    public Map<String, String> occurrence() {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        return occurrence(minecraft, minecraft == null ? null : minecraft.theWorld);
    }

    public static Map<String, String> occurrence(Minecraft minecraft, World world) {
        Map<String, String> values = new LinkedHashMap<>();
        if (minecraft == null) {
            values.put("Game context", "unavailable");
            return values;
        }
        values.put("World context available", Boolean.toString(world != null));
        values.put("Client world loaded", Boolean.toString(minecraft.theWorld != null));
        GuiScreen screen = minecraft.currentScreen;
        values.put("Current screen", screen == null ? "none"
                : GuiScreenNames.name(screen) + " | " + GuiScreenNames.className(screen));
        values.put("Game paused", Boolean.toString(minecraft.isGamePaused));
        if (world != null) {
            values.put("Multiplayer world", Boolean.toString(world.multiplayerWorld));
            if (world.worldProvider != null) {
                values.put("Dimension numeric ID", Integer.toString(DimensionIdentity.numericId(world)));
                try {
                    values.put("Dimension", DimensionIdentity.key(world).toString());
                } catch (IllegalArgumentException foreignDimension) {
                    values.put("Dimension", "unavailable (foreign dimension)");
                }
            }
            if (!world.multiplayerWorld) {
                WorldGeneratorProfile profile = WorldGenerationRuntime.profile(world);
                DimensionGenerationBinding custom = DimensionRuntime.generation(world);
                values.put("Active worldgen profile", profile == null ? "Minecraft" : profile.key);
                values.put("Native generation type", custom == null ? "dimension default" : custom.nativeBase);
                values.put("Temporary generator fallback", Boolean.toString(WorldGenerationRuntime.isFallback(world)));
            }
        }
        return values;
    }

    @Override
    public Map<String, List<String>> environment() {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        List<String> game = new ArrayList<>();
        try {
            for (Map.Entry<String, String> entry : occurrence().entrySet()) {
                game.add(entry.getKey() + ": " + entry.getValue());
            }
        } catch (Throwable failure) {
            ScriptDiagnostics.rethrowFatal(failure);
            game.add("Game context unavailable (" + failure.getClass().getSimpleName() + ")");
        }
        sections.put("Game", game);
        sections.put("Graphics", graphics());
        sections.put("Java mods", mods());
        return sections;
    }

    private static List<String> graphics() {
        List<String> values = new ArrayList<>();
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft != null && minecraft.texturePackList != null
                && minecraft.texturePackList.selectedTexturePack != null) {
            values.add("Texture pack: " + minecraft.texturePackList.selectedTexturePack.texturePackFileName);
        } else {
            values.add("Texture pack: unavailable");
        }
        try {
            values.add("LWJGL: " + Sys.getVersion());
            GLContext.getCapabilities();
            values.add("GPU vendor: " + GL11.glGetString(GL11.GL_VENDOR));
            values.add("GPU renderer: " + GL11.glGetString(GL11.GL_RENDERER));
            values.add("OpenGL: " + GL11.glGetString(GL11.GL_VERSION));
        } catch (Throwable failure) {
            ScriptDiagnostics.rethrowFatal(failure);
            values.add("OpenGL details: unavailable (" + failure.getClass().getSimpleName() + ")");
        }
        return values;
    }

    private static List<String> mods() {
        List<String> values = new ArrayList<>();
        values.add("Loader: " + ModLoader.VERSION);
        try {
            for (Object loaded : ModLoader.getLoadedMods()) {
                if (!(loaded instanceof BaseMod)) {
                    continue;
                }
                BaseMod mod = (BaseMod) loaded;
                String version;
                try {
                    version = mod.Version();
                } catch (Throwable failure) {
                    ScriptDiagnostics.rethrowFatal(failure);
                    version = "unavailable";
                }
                values.add(mod.getClass().getSimpleName() + " | version: " + version);
            }
        } catch (Throwable failure) {
            ScriptDiagnostics.rethrowFatal(failure);
            values.add("Mod inventory unavailable (" + failure.getClass().getSimpleName() + ")");
        }
        return values;
    }
}
