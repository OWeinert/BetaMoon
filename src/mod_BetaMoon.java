import betamoon.scriptloader.LuaModLoader;
import betamoon.scriptloader.LuaScriptErrors;
import betamoon.gui.GuiBetaMoonMainMenu;
import betamoon.gui.GuiScriptErrorPopup;
import betamoon.worldgen.WorldGenRegistry;
import java.util.Random;
import java.util.logging.Formatter;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import net.minecraft.src.BaseMod;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;

public class mod_BetaMoon extends BaseMod {
    private static final String VERSION = "0.4.0";
    private static final Logger LOGGER = Logger.getLogger("BetaMoon");
    static {
        configureLogger();
    }
    
    private final LuaModLoader luaModLoader = new LuaModLoader();

    @Override
    public void ModsLoaded() {
        luaModLoader.loadAndRun();
        ModLoader.SetInGUIHook(this, true, false);
    }

    @Override
    public boolean OnTickInGUI(net.minecraft.client.Minecraft mc, GuiScreen current) {
        if (current instanceof GuiMainMenu) {
            if (!(current instanceof GuiBetaMoonMainMenu)) {
                mc.displayGuiScreen(new GuiBetaMoonMainMenu());
                return true;
            }
            if (LuaScriptErrors.shouldShowPopup()) {
                mc.displayGuiScreen(new GuiScriptErrorPopup(current));
                return true;
            }
        }
        return true;
    }

    @Override
    public void GenerateSurface(World world, Random random, int chunkX, int chunkZ) {
        WorldGenRegistry.generateSurface(world, random, chunkX, chunkZ);
    }

    @Override
    public void GenerateNether(World world, Random random, int chunkX, int chunkZ) {
        WorldGenRegistry.generateNether(world, random, chunkX, chunkZ);
    }

    @Override
    public String Version() {
        return VERSION;
    }

    private static void configureLogger() {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);
        for (Handler handler : LOGGER.getHandlers()) {
            LOGGER.removeHandler(handler);
        }
        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                String level = record.getLevel().getName();
                return "[BetaMoon] " + level + ": " + record.getMessage()
                    + System.lineSeparator();
            }
        };
        Handler outHandler = new StreamHandler(System.out, formatter) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        outHandler.setLevel(Level.INFO);
        outHandler.setFilter(new Filter() {
            public boolean isLoggable(LogRecord record) {
                return record.getLevel().intValue() < Level.WARNING.intValue();
            }
        });
        Handler errHandler = new StreamHandler(System.err, formatter) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        errHandler.setLevel(Level.WARNING);
        errHandler.setFilter(new Filter() {
            public boolean isLoggable(LogRecord record) {
                return record.getLevel().intValue() >= Level.WARNING.intValue();
            }
        });
        LOGGER.addHandler(outHandler);
        LOGGER.addHandler(errHandler);
    }

}
