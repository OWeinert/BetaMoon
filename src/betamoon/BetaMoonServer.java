package betamoon;

import betamoon.config.BetaMoonConfig;
import betamoon.io.BetaMoonDirectories;
import betamoon.luaapi.config.ScriptConfigRegistry;
import java.io.File;

/** Dedicated-server entry point. It contains no client or GUI dependencies. */
public final class BetaMoonServer {
    private static BetaMoonServer instance;

    private final BetaMoonCommon common;
    private final BetaMoonConfig config;
    private boolean started;

    private BetaMoonServer(File gameDirectory) {
        if (gameDirectory == null) {
            throw new IllegalArgumentException("gameDirectory cannot be null");
        }
        File scriptsDirectory = BetaMoonDirectories.scriptsDirectory(gameDirectory, true);
        this.common = new BetaMoonCommon(scriptsDirectory);
        this.config = new BetaMoonConfig(new File(gameDirectory, "config"));
        common.configure(config);
    }

    public static synchronized BetaMoonServer create(File gameDirectory) {
        if (instance == null) {
            instance = new BetaMoonServer(gameDirectory);
        } else {
            BetaMoonCommon.LOGGER.warning("External source tried to re-initialize the BetaMoon server.");
        }
        return instance;
    }

    public static BetaMoonServer getInstance() {
        return instance;
    }

    /** Starts shared gameplay after the server's native registries are available. */
    public synchronized void start() {
        if (started) {
            return;
        }
        common.loadScripts();
        started = true;
    }

    /** Runs server-side maintenance on the Minecraft server thread. */
    public void tick() {
        ScriptConfigRegistry.tick();
        if (started && config.getHotReloadOnFileChange().getValue()) {
            common.pollScripts();
        }
    }

    public void reloadLuaScripts() {
        if (started) {
            common.reloadScripts();
        }
    }

    public String version() {
        return common.version();
    }
}
