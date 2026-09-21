package betamoon;

import betamoon.client.assets.ClientAssets;
import betamoon.client.audio.ClientAudio;
import betamoon.client.audio.ClientEntityEffects;
import betamoon.client.network.ClientNetworkSession;
import betamoon.client.render.ClientEntityPresentationResources;
import betamoon.client.LuaLoaderClientFeedback;
import betamoon.config.BetaMoonConfig;
import betamoon.luaapi.block.BlockModelRegistry;
import betamoon.gui.GuiBetaMoonIngameMenu;
import betamoon.gui.GuiBetaMoonMainMenu;
import betamoon.gui.GuiPopupAgentWarning;
import betamoon.gui.GuiPopupScriptErrors;
import betamoon.luaapi.chat.ChatApi;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.update.UpdateChecker;
import betamoon.update.UpdateRelease;
import java.util.Random;
import java.util.logging.Logger;
import net.minecraft.src.BaseMod;
import net.minecraft.src.GuiIngameMenu;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.KeyBinding;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;
import org.lwjgl.input.Keyboard;

/** Client entry point and owner of all Minecraft client integrations. */
public final class BetaMoonClient {
    private static BetaMoonClient instance;

    public static final Logger LOGGER = BetaMoonCommon.LOGGER;

    private final BetaMoonCommon common;
    private final BetaMoonEventHandler eventHandler;
    private final BetaMoonConfig config;
    private final BaseMod betaMoonBaseMod;
    private final boolean agentRegistered;
    private final UpdateChecker updateChecker = new UpdateChecker(LOGGER);
    private final ClientNetworkSession networkSession;
    private boolean updateNotifiedInWorld;

    private boolean finishedLoading = false;
    private boolean agentWarningShown = false;

    private BetaMoonClient(BaseMod baseMod) {
        this.betaMoonBaseMod = baseMod;
        this.common = new BetaMoonCommon();
        ClientEntityEffects.initialize();
        ClientEntityPresentationResources.initialize();
        LuaLoaderClientFeedback.initialize();
        this.networkSession = ClientNetworkSession.initialize();
        BlockModelRegistry.initialize(baseMod);
        this.agentRegistered = common.isAgentRegistered();
        this.config = new BetaMoonConfig("betamoon.config");
        this.eventHandler = new BetaMoonEventHandler();
        setInitHooks(this.betaMoonBaseMod);
    }

    public static synchronized BetaMoonClient create(BaseMod baseMod) {
        if (instance == null) {
            instance = new BetaMoonClient(baseMod);
        } else if (baseMod != null) {
            LOGGER.warning(
                    "External source tried to re-initialize BetaMoon from: " + baseMod.getClass().getName() + "!");
        } else {
            LOGGER.warning("Unknown external source tried to re-initialize BetaMoon!");
        }
        return instance;
    }

    public static BetaMoonClient getInstance() {
        return instance;
    }

    public void setInitHooks(BaseMod baseMod) {
        ModLoader.SetInGUIHook(baseMod, true, false);
        ModLoader.SetInGameHook(baseMod, true, false);
    }

    public void modsLoaded() {
        finishedLoading = true;
        if (config.getCheckForUpdates().getValue()) {
            updateChecker.startOnce(version());
        }
    }

    public UpdateRelease getAvailableUpdate() {
        return updateChecker.getAvailableUpdate();
    }

    public boolean onTickInGUI(net.minecraft.client.Minecraft mc, GuiScreen current) {
        if (common.areScriptsLoaded()) {
            pollScriptChanges();
            ClientAssets.poll();
        }
        ClientAudio.tick();
        eventHandler.handleGuiEvents(mc, current);
        updateJoinNotification(mc);
        addBetamoonMenues(mc, current);
        return true;
    }

    public boolean onTickInGame(net.minecraft.client.Minecraft mc) {
        if (common.areScriptsLoaded()) {
            pollScriptChanges();
            ClientAssets.poll();
        }
        ClientAudio.tick();
        ChatApi.flushPendingMessages();
        eventHandler.handleGameEvents(mc);
        updateJoinNotification(mc);
        return true;
    }

    public void reloadLuaScripts() {
        common.reloadScripts();
    }

    private void pollScriptChanges() {
        if (config.getHotReloadOnFileChange().getValue()) {
            common.pollScripts();
        }
    }

    private void updateJoinNotification(net.minecraft.client.Minecraft mc) {
        if (mc.theWorld == null) {
            updateNotifiedInWorld = false;
            return;
        }
        UpdateRelease release = getAvailableUpdate();
        if (updateNotifiedInWorld || release == null || mc.thePlayer == null || mc.ingameGUI == null
                || !config.getNotifyUpdatesOnWorldJoin().getValue()) {
            return;
        }
        mc.thePlayer.addChatMessage(
                "\u00a76[BetaMoon] Update available: \u00a7f" + version() + " -> " + release.getVersion());
        mc.thePlayer.addChatMessage(
                "\u00a77[BetaMoon] Download via \"View on " + release.getSourceName() + "\" in the main menu.");
        updateNotifiedInWorld = true;
    }

    /**
     * Reloads scripts for Ctrl+Shift+R while normal gameplay has keyboard focus.
     */
    public void handleReloadHotkey(KeyBinding key) {
        net.minecraft.client.Minecraft mc = ModLoader.getMinecraftInstance();
        if (!common.areScriptsLoaded() || mc == null || mc.currentScreen != null) {
            return;
        }
        boolean control = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (!control || !shift) {
            return;
        }
        LOGGER.info("Reloading Lua scripts from Ctrl+Shift+" + Keyboard.getKeyName(key.keyCode) + ".");
        reloadLuaScripts();
    }

    public void generateSurface(World world, Random random, int chunkX, int chunkZ) {
        common.generateSurface(world, random, chunkX, chunkZ);
    }

    public void generateNether(World world, Random random, int chunkX, int chunkZ) {
        common.generateNether(world, random, chunkX, chunkZ);
    }

    public String version() {
        return BetaMoonCommon.VERSION;
    }

    private void addBetamoonMenues(net.minecraft.client.Minecraft mc, GuiScreen current) {
        if (current instanceof GuiIngameMenu && !(current instanceof GuiBetaMoonIngameMenu)) {
            mc.displayGuiScreen(new GuiBetaMoonIngameMenu());
            return;
        }
        if (current instanceof GuiMainMenu) {
            // onTickInGUI runs on every game tick when a GUI is open,
            // which is first after ModLoader/MinecraftForge loaded every mod and Minecraft
            // shows the main menu.
            // So we only call loadAndRun() once here to ensure BetaMoon loads and executes
            // the scripts after every other mod.
            // This makes sure that any content from other mods that might be referenced by
            // scripts is present.
            if (finishedLoading && !common.areScriptsLoaded()) {
                common.loadScripts();
            }

            // Render custom Main Menu
            if (!(current instanceof GuiBetaMoonMainMenu)) {
                mc.displayGuiScreen(new GuiBetaMoonMainMenu());
                return;
            }
            // Warn once after the custom main menu is ready so the popup has a stable
            // parent screen.
            if (!agentRegistered && !agentWarningShown) {
                agentWarningShown = true;
                mc.displayGuiScreen(new GuiPopupAgentWarning(current));
                return;
            }
            // Render script error Popup
            if (LuaScriptErrors.shouldShowPopup()) {
                mc.displayGuiScreen(new GuiPopupScriptErrors(current));
                return;
            }
        }
    }

}
