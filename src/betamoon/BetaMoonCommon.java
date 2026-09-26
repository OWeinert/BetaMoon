package betamoon;

import betamoon.entity.EntityBootstrap;
import betamoon.instrumentation.agent.AgentStatus;
import betamoon.instrumentation.agent.BetaMoonAgent;
import betamoon.luamodloader.LuaModLoader;
import betamoon.network.transport.PacketRegistration;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.worldgen.WorldGenRegistry;
import java.io.File;
import java.util.Random;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import net.minecraft.src.World;

/**
 * Side-neutral BetaMoon lifecycle shared by the client and dedicated server.
 *
 * <p>This class must remain free of client-only and dedicated-server-only types.
 */
public final class BetaMoonCommon {
    public static final String VERSION = "0.7.2";
    public static final String LUA_SCRIPTS_DIR = "lua_scripts";
    public static final Logger LOGGER = Logger.getLogger("BetaMoon");

    private final LuaModLoader luaModLoader;
    private final boolean agentRegistered;
    private volatile boolean scriptsLoaded;

    static {
        configureLogger();
    }

    public BetaMoonCommon() {
        this(new LuaModLoader());
    }

    public BetaMoonCommon(File scriptsDirectory) {
        this(new LuaModLoader(scriptsDirectory));
    }

    private BetaMoonCommon(LuaModLoader luaModLoader) {
        EntityBootstrap.register();
        PacketRegistration.register();
        this.luaModLoader = luaModLoader;
        this.agentRegistered = inspectAgentStatus();
    }

    public synchronized void loadScripts() {
        if (scriptsLoaded) {
            return;
        }

        RecipeModificationHandler.createRecipeMap();
        luaModLoader.loadAndRun();
        scriptsLoaded = true;
    }

    public void pollScripts() {
        if (scriptsLoaded) {
            luaModLoader.pollForChanges();
        }
    }

    public void reloadScripts() {
        if (scriptsLoaded) {
            luaModLoader.reloadAll();
        }
    }

    public boolean areScriptsLoaded() {
        return scriptsLoaded;
    }

    public boolean isAgentRegistered() {
        return agentRegistered;
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

    private static boolean inspectAgentStatus() {
        boolean registered = BetaMoonAgent.isRegistered();
        if (!registered) {
            String failure = BetaMoonAgent.getFailureMessage();
            if (BetaMoonAgent.getStatus() == AgentStatus.FAILED && failure != null) {
                LOGGER.warning("The BetaMoon Java agent failed to initialize: " + failure);
            } else {
                LOGGER.warning("The BetaMoon Java agent is not enabled. Some BetaMoon features may be unavailable!");
            }
        } else if (BetaMoonAgent.getStatus() == AgentStatus.DEGRADED) {
            LOGGER.warning("The BetaMoon Java agent is active with hook failures: "
                    + BetaMoonAgent.getFailureMessage());
        }
        return registered;
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
                return "[BetaMoon] " + level + ": " + record.getMessage() + System.lineSeparator();
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
