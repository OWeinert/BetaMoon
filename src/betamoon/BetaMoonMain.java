package betamoon;

import java.util.Random;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import betamoon.config.BetaMoonConfig;
import betamoon.gui.GuiBetaMoonMainMenu;
import betamoon.gui.GuiPopupScriptErrors;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.scriptloader.LuaModLoader;
import betamoon.scriptloader.LuaScriptErrors;
import betamoon.worldgen.WorldGenRegistry;
import net.minecraft.src.BaseMod;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;

public final class BetaMoonMain {
    private static BetaMoonMain instance;

    private static final String VERSION = "0.5.0";
    public static final String LUA_SCRIPTS_DIR = "lua_scripts";
    public static final Logger LOGGER = Logger.getLogger("BetaMoon");
    static {
        configureLogger();
    }

    private final BetaMoonEventHandler eventHandler;
    private final BetaMoonConfig config;
    private final LuaModLoader luaModLoader;
    private final BaseMod betaMoonBaseMod;

    private boolean finishedLoading = false;
    private boolean loadedScripts = false;

    private BetaMoonMain(BaseMod baseMod) {
        this.betaMoonBaseMod = baseMod;
        this.config = new BetaMoonConfig("betamoon.config");
        this.eventHandler = new BetaMoonEventHandler();
        this.luaModLoader  = new LuaModLoader();
        setInitHooks(this.betaMoonBaseMod);
    }

    public static synchronized BetaMoonMain create(BaseMod baseMod) {
        if (instance == null) {
            instance = new BetaMoonMain(baseMod);
        } else if (baseMod != null) {
            LOGGER.warning("External source tried to re-initialize BetaMoon from: "
                + baseMod.getClass().getName() + "!");
        } else {
            LOGGER.warning("Unknown external source tried to re-initialize BetaMoon!");
        }
        return instance;
    }

    public static BetaMoonMain getInstance() {
        return instance;
    }

    public void setInitHooks(BaseMod baseMod) {
        ModLoader.SetInGUIHook(baseMod, true, false);
        ModLoader.SetInGameHook(baseMod, true, false);
    }

    public void modsLoaded() {
        finishedLoading = true;
    }

    public boolean onTickInGUI(net.minecraft.client.Minecraft mc, GuiScreen current) {
        eventHandler.handleGuiEvents(mc, current);
        addBetamoonMenues(mc, current);
        return true;
    }

    public boolean onTickInGame(net.minecraft.client.Minecraft mc) {
        eventHandler.handleGameEvents(mc);
        return true;
    }

    public void generateSurface(World world, Random random, int chunkX, int chunkZ) {
        WorldGenRegistry.generateSurface(world, random, chunkX, chunkZ);
    }

    public void generateNether(World world, Random random, int chunkX, int chunkZ) {
        WorldGenRegistry.generateNether(world, random, chunkX, chunkZ);
    }

    public String version() {
        return VERSION;
    }

    
    private void addBetamoonMenues(net.minecraft.client.Minecraft mc, GuiScreen current) {
        if (current instanceof GuiMainMenu) {
            // onTickInGUI runs on every game tick when a GUI is open, 
            // which is first after ModLoader/MinecraftForge loaded every mod and Minecraft shows the main menu.
            // So we only call loadAndRun() once here to ensure BetaMoon loads and executes the scripts after every other mod.
            // This makes sure that any content from other mods that might be referenced by scripts is present.
            if(finishedLoading && !loadedScripts) {
                // create recipe map before loading scripts to ensure recipe creation/override is possible
                RecipeModificationHandler.createRecipeMap();
                luaModLoader.loadAndRun();
                loadedScripts = true;
            }

            // Render custom Main Menu
            if (!(current instanceof GuiBetaMoonMainMenu)) {
                mc.displayGuiScreen(new GuiBetaMoonMainMenu());
                return;
            }
            // Render script error Popup
            if (LuaScriptErrors.shouldShowPopup()) {
                mc.displayGuiScreen(new GuiPopupScriptErrors(current));
                return;
            }
        }
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
        outHandler.setFilter(record -> record.getLevel().intValue() < Level.WARNING.intValue());
        Handler errHandler = new StreamHandler(System.err, formatter) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        errHandler.setLevel(Level.WARNING);
        errHandler.setFilter(record -> record.getLevel().intValue() >= Level.WARNING.intValue());
        LOGGER.addHandler(outHandler);
        LOGGER.addHandler(errHandler);
    }
}
