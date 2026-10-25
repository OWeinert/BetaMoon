package betamoon;

import betamoon.config.BetaMoonConfig;
import betamoon.entity.EntityBootstrap;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.item.FluidBucketHandler;
import betamoon.instrumentation.agent.AgentStatus;
import betamoon.instrumentation.agent.BetaMoonAgent;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.SlowScriptCallbackMonitor;
import betamoon.logging.RuntimeLogs;
import betamoon.network.transport.PacketRegistration;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.worldgen.WorldGenRegistry;
import java.io.File;
import java.util.Random;
import java.util.logging.Logger;
import net.minecraft.src.World;

/**
 * Side-neutral BetaMoon lifecycle shared by the client and dedicated server.
 *
 * <p>
 * This class must remain free of client-only and dedicated-server-only types.
 */
public final class BetaMoonCommon {
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = RuntimeLogs.logger();

    private final LuaModLoader luaModLoader;
    private final boolean agentRegistered;
    private volatile boolean scriptsLoaded;

    public BetaMoonCommon() {
        this(new LuaModLoader());
    }

    public BetaMoonCommon(File scriptsDirectory) {
        this(new LuaModLoader(scriptsDirectory));
    }

    private BetaMoonCommon(LuaModLoader luaModLoader) {
        EntityBootstrap.register();
        PacketRegistration.register();
        FluidRegistry.instance().registerVanilla();
        FluidBucketHandler.register();
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

    public void configure(BetaMoonConfig config) {
        RuntimeLogs.configure(config.getLogDirectory().toPath(), config.getFileMinLogLevel().getValue(),
                config.getConsoleMinLogLevel().getValue());
        luaModLoader.configure(config);
        SlowScriptCallbackMonitor.configure(config.getSlowCallbackWarningMs().getValue());
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
            LOGGER.warning(
                    "The BetaMoon Java agent is active with hook failures: " + BetaMoonAgent.getFailureMessage());
        }
        return registered;
    }

}
