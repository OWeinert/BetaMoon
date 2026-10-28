package betamoon.config;

import betamoon.BetaMoonCommon;
import betamoon.multiblock.MultiblockLimits;
import betamoon.io.IoUtils;
import betamoon.io.BetaMoonDirectories;
import betamoon.logging.LogLevel;
import forge.Configuration;
import forge.Property;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class BetaMoonConfig {
    public static final String FILE_NAME = "betamoon.cfg";
    private static final String LEGACY_FILE_NAME = "betamoon.config";

    private final Configuration config;
    private final File logDirectory;
    private ConfigField<LogLevel> fileMinLogLevel;
    private ConfigField<LogLevel> consoleMinLogLevel;
    private ConfigField<Boolean> showScriptWarningsOnLoad;
    private ConfigField<Boolean> showScriptErrorPopupOnStartup;
    private ConfigField<Boolean> checkForUpdates;
    private ConfigField<Boolean> notifyUpdatesOnWorldJoin;
    private ConfigField<Boolean> hotReloadOnFileChange;
    private ConfigField<Integer> reloadDebounceMs;
    private ConfigField<Boolean> showSuccessfulReloadMessages;
    private ConfigField<Set<String>> disabledScripts;
    private ConfigField<Boolean> logScriptLoadTimings;
    private ConfigField<Boolean> autoExportDiagnosticsOnScriptError;
    private ConfigField<Integer> slowCallbackWarningMs;
    private ConfigField<String> hookTimingMode;
    private ConfigField<Integer> hookTimingSampleEvery;
    private ConfigField<Boolean> verboseAssetDiagnostics;
    private ConfigField<Integer> particleMaxActive;
    private ConfigField<Integer> particleMaxPerTick;
    private ConfigField<Integer> particleViewDistance;

    public BetaMoonConfig() {
        this(resolveDefaultConfigDirectory(), FILE_NAME);
    }

    public BetaMoonConfig(File configDirectory) {
        this(configDirectory, FILE_NAME);
    }

    public BetaMoonConfig(String configFileName) {
        this(resolveDefaultConfigDirectory(), configFileName);
    }

    public BetaMoonConfig(File configDirectory, String configFileName) {
        if (configDirectory == null) {
            throw new IllegalArgumentException("configDirectory cannot be null");
        }
        if (configFileName == null || configFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("configFileName cannot be empty");
        }

        File directory = IoUtils.ensureDirectory(configDirectory);
        if (directory == null) {
            throw new IllegalStateException("Unable to create BetaMoon config directory: " + configDirectory);
        }
        logDirectory = BetaMoonDirectories.logsDirectoryForInstance(directory.getAbsoluteFile().getParentFile());
        config = new GroupedForgeConfiguration(resolveConfigFile(directory, configFileName));
        updateConfigFields();
    }

    private static File resolveConfigFile(File directory, String configFileName) {
        File current = new File(directory, configFileName);
        if (!FILE_NAME.equals(configFileName)) {
            return current;
        }

        Path currentPath = current.toPath();
        Path legacyPath = new File(directory, LEGACY_FILE_NAME).toPath();
        if (Files.exists(currentPath, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(legacyPath, LinkOption.NOFOLLOW_LINKS)) {
                BetaMoonCommon.LOGGER.warning("Both BetaMoon config files exist; using " + currentPath + " and leaving "
                        + legacyPath + " unchanged.");
            }
            return current;
        }
        if (!Files.exists(legacyPath, LinkOption.NOFOLLOW_LINKS)) {
            return current;
        }
        if (!Files.isRegularFile(legacyPath)) {
            BetaMoonCommon.LOGGER.warning("Legacy BetaMoon config is not a regular file: " + legacyPath);
            return current;
        }

        try {
            Files.move(legacyPath, currentPath);
            BetaMoonCommon.LOGGER.info("Moved BetaMoon config from " + legacyPath + " to " + currentPath);
            return current;
        } catch (IOException | SecurityException e) {
            BetaMoonCommon.LOGGER.log(Level.WARNING,
                    "Could not move BetaMoon config to " + currentPath + "; using " + legacyPath + " instead.", e);
            return legacyPath.toFile();
        }
    }

    public void updateConfigFields() {
        config.generalProperties.clear();
        config.blockProperties.clear();
        config.itemProperties.clear();
        config.load();

        fileMinLogLevel = logLevel("logging.fileMinLevel");
        consoleMinLogLevel = logLevel("logging.consoleMinLevel");

        // Forge 1.0.6 only accepts general/block/item sections. Dotted keys group
        // BetaMoon settings without creating sections its parser cannot reload.
        config.generalProperties.remove("showPopupOnWarnings");
        migrate("showScriptWarningsOnLoad", "notifications.showScriptWarningsOnLoad");
        migrate("checkForUpdates", "updates.checkForUpdates");
        migrate("notifyUpdatesOnWorldJoin", "updates.notifyUpdatesOnWorldJoin");
        migrate("hotReloadOnFileChange", "scripts.hotReloadOnFileChange");

        showScriptWarningsOnLoad = bool("notifications.showScriptWarningsOnLoad", false,
                "Show script warnings at startup and after reload; errors always appear.");
        showScriptErrorPopupOnStartup = bool("notifications.showScriptErrorPopupOnStartup", true,
                "Open the script error popup on startup when visible issues exist.");
        showSuccessfulReloadMessages = bool("notifications.showSuccessfulReloadMessages", true,
                "Show a chat summary after a successful script reload; errors still appear.");

        checkForUpdates = bool("updates.checkForUpdates", true, "Check for BetaMoon updates when the client starts.");
        notifyUpdatesOnWorldJoin = bool("updates.notifyUpdatesOnWorldJoin", true,
                "Show an available BetaMoon update when joining a world.");

        hotReloadOnFileChange = bool("scripts.hotReloadOnFileChange", false,
                "Automatically reload scripts after their files change.");
        reloadDebounceMs = boundedInt("scripts.reloadDebounceMs", 500, 250, 5000,
                "Wait this many milliseconds after the last script change before auto reload (250-5000).");
        String disabled = string("scripts.disabledScripts", "",
                "Comma-separated top-level script entries to skip (foo.lua, Pack, Pack.zip). Restart after changing.");
        disabledScripts = new ConfigField<Set<String>>("scripts.disabledScripts", parseDisabledScripts(disabled));
        logScriptLoadTimings = bool("diagnostics.logScriptLoadTimings", false,
                "Log discovery, parse, and initialization times for Lua mods.");

        autoExportDiagnosticsOnScriptError = bool("diagnostics.autoExportDiagnosticsOnScriptError", false,
                "Write a bounded script diagnostics report after a failed load or reload.");
        slowCallbackWarningMs = boundedInt("diagnostics.slowCallbackWarningMs", 0, 0, 60000,
                "Warn when BetaMoon Lua callback dispatches repeatedly exceed this time in ms; 0 disables it.");
        String timing = string("diagnostics.hookTimingMode", "counts",
                "Hook timing: counts (default, no clocks), sampled, or full. Editing behavior is unchanged.").trim()
                .toLowerCase(Locale.ROOT);
        if (!timing.equals("counts") && !timing.equals("sampled") && !timing.equals("full")) {
            BetaMoonCommon.LOGGER.warning("Invalid diagnostics.hookTimingMode; using counts.");
            timing = "counts";
        }
        ((Property) config.generalProperties.get("diagnostics.hookTimingMode")).value = timing;
        hookTimingMode = new ConfigField<>("diagnostics.hookTimingMode", timing);
        hookTimingSampleEvery = boundedInt("diagnostics.hookTimingSampleEvery", 64, 1, 65536,
                "In sampled mode, time every Nth admitted callback per subscription (1-65536). Counts remain exact.");
        verboseAssetDiagnostics = bool("diagnostics.verboseAssetDiagnostics", false,
                "Log selected asset sources and texture-pack fallbacks during asset resolution.");

        particleMaxActive = boundedInt("particles.maxActive", 4096, 1, 65535,
                "Maximum active BetaMoon particle sprites on this client (1-65535; default 4096).");
        particleMaxPerTick = boundedInt("particles.maxPerTick", 1024, 1, 65535,
                "Maximum new BetaMoon particle sprites admitted per client tick (1-65535; default 1024).");
        particleViewDistance = boundedInt("particles.viewDistance", 64, 1, 64,
                "Upper distance limit for custom particle effects (1-64 blocks); ordinary effects default to 16.");

        MultiblockLimits.configure(
                boundedInt("multiblocks.maxRequiredCells", 4096, 1, 32768,
                        "Maximum required cells per expanded multiblock pattern (1-32768).").getValue(),
                boundedInt("multiblocks.visitsPerTick", 65536, 256, 1048576,
                        "Shared recognition cell work per logical world tick (256-1048576).").getValue(),
                boundedInt("multiblocks.maxInstances", 4096, 1, 65535,
                        "Maximum managed multiblock instances in a loaded world.").getValue(),
                boundedInt("multiblocks.maxInstancesPerOwner", 1024, 1, 65535,
                        "Maximum managed multiblock instances belonging to one script.").getValue());

        config.save();
    }

    public ConfigField<Boolean> getShowScriptWarningsOnLoad() {
        return showScriptWarningsOnLoad;
    }

    public ConfigField<Boolean> getShowScriptErrorPopupOnStartup() {
        return showScriptErrorPopupOnStartup;
    }

    public ConfigField<Boolean> getCheckForUpdates() {
        return checkForUpdates;
    }

    public ConfigField<Boolean> getNotifyUpdatesOnWorldJoin() {
        return notifyUpdatesOnWorldJoin;
    }

    public ConfigField<Boolean> getHotReloadOnFileChange() {
        return hotReloadOnFileChange;
    }

    public ConfigField<Integer> getReloadDebounceMs() {
        return reloadDebounceMs;
    }

    public ConfigField<Boolean> getShowSuccessfulReloadMessages() {
        return showSuccessfulReloadMessages;
    }

    public ConfigField<Set<String>> getDisabledScripts() {
        return disabledScripts;
    }

    public ConfigField<Boolean> getLogScriptLoadTimings() {
        return logScriptLoadTimings;
    }

    public ConfigField<Boolean> getAutoExportDiagnosticsOnScriptError() {
        return autoExportDiagnosticsOnScriptError;
    }

    public ConfigField<Integer> getSlowCallbackWarningMs() {
        return slowCallbackWarningMs;
    }

    public ConfigField<String> getHookTimingMode() {
        return hookTimingMode;
    }

    public ConfigField<Integer> getHookTimingSampleEvery() {
        return hookTimingSampleEvery;
    }

    public ConfigField<Boolean> getVerboseAssetDiagnostics() {
        return verboseAssetDiagnostics;
    }

    public ConfigField<LogLevel> getFileMinLogLevel() {
        return fileMinLogLevel;
    }

    public ConfigField<LogLevel> getConsoleMinLogLevel() {
        return consoleMinLogLevel;
    }

    public File getLogDirectory() {
        return logDirectory;
    }

    private static File resolveDefaultConfigDirectory() {
        File minecraftDir = IoUtils.resolveMinecraftDirFromCodeSource(BetaMoonCommon.class);
        if (minecraftDir == null) {
            throw new IllegalStateException("Unable to resolve Minecraft directory for config.");
        }
        return new File(minecraftDir, "config");
    }

    public ConfigField<Integer> getParticleMaxActive() {
        return particleMaxActive;
    }

    public ConfigField<Integer> getParticleMaxPerTick() {
        return particleMaxPerTick;
    }

    public ConfigField<Integer> getParticleViewDistance() {
        return particleViewDistance;
    }

    /**
     * Effective diagnostic settings; excludes arbitrary configuration files and
     * local paths.
     */
    public Map<String, String> diagnosticSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        diagnostic(values, fileMinLogLevel);
        diagnostic(values, consoleMinLogLevel);
        diagnostic(values, hotReloadOnFileChange);
        diagnostic(values, reloadDebounceMs);
        diagnostic(values, disabledScripts);
        diagnostic(values, showScriptWarningsOnLoad);
        diagnostic(values, showScriptErrorPopupOnStartup);
        diagnostic(values, logScriptLoadTimings);
        diagnostic(values, autoExportDiagnosticsOnScriptError);
        diagnostic(values, slowCallbackWarningMs);
        diagnostic(values, hookTimingMode);
        diagnostic(values, hookTimingSampleEvery);
        diagnostic(values, verboseAssetDiagnostics);
        diagnostic(values, particleMaxActive);
        diagnostic(values, particleMaxPerTick);
        diagnostic(values, particleViewDistance);
        values.put("multiblocks.maxRequiredCells", Integer.toString(MultiblockLimits.requiredCells()));
        values.put("multiblocks.visitsPerTick", Integer.toString(MultiblockLimits.visitsPerTick()));
        values.put("multiblocks.maxInstances", Integer.toString(MultiblockLimits.worldInstances()));
        values.put("multiblocks.maxInstancesPerOwner", Integer.toString(MultiblockLimits.ownerInstances()));
        return values;
    }

    private static void diagnostic(Map<String, String> values, ConfigField<?> field) {
        if (field != null) {
            values.put(field.getName(), String.valueOf(field.getValue()));
        }
    }

    private void migrate(String oldKey, String newKey) {
        Property old = (Property) config.generalProperties.remove(oldKey);
        if (old != null && !config.generalProperties.containsKey(newKey)) {
            old.name = newKey;
            config.generalProperties.put(newKey, old);
        }
    }

    private ConfigField<Boolean> bool(String key, boolean fallback, String comment) {
        Property property = config.getOrCreateBooleanProperty(key, Configuration.GENERAL_PROPERTY, fallback);
        property.comment = comment;
        return new ConfigField<Boolean>(key, Boolean.valueOf(property.value));
    }

    private ConfigField<LogLevel> logLevel(String key) {
        Property property = config.getOrCreateProperty(key, Configuration.GENERAL_PROPERTY, "info");
        property.comment = "Minimum log level: trace, debug, info, warn, error, or off. Default: info.";
        LogLevel value;
        try {
            value = LogLevel.parse(property.value);
        } catch (IllegalArgumentException invalid) {
            BetaMoonCommon.LOGGER.warning("Invalid " + key + " value '" + property.value + "'; using info.");
            value = LogLevel.INFO;
        }
        property.value = value.toString();
        return new ConfigField<LogLevel>(key, value);
    }

    private ConfigField<Integer> boundedInt(String key, int fallback, int minimum, int maximum, String comment) {
        Property property = config.getOrCreateIntProperty(key, Configuration.GENERAL_PROPERTY, fallback);
        int value = Integer.parseInt(property.value);
        if (value < minimum || value > maximum) {
            BetaMoonCommon.LOGGER.warning("Invalid " + key + " value " + value + "; using " + fallback + ".");
            value = fallback;
            property.value = Integer.toString(fallback);
        }
        property.comment = comment;
        return new ConfigField<Integer>(key, value);
    }

    private String string(String key, String fallback, String comment) {
        Property property = config.getOrCreateProperty(key, Configuration.GENERAL_PROPERTY, fallback);
        property.comment = comment;
        return property.value;
    }

    private Set<String> parseDisabledScripts(String value) {
        Set<String> entries = new LinkedHashSet<String>();
        for (String part : value.split(",", -1)) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
                BetaMoonCommon.LOGGER.warning("Ignoring invalid disabled script entry: " + name);
                continue;
            }
            entries.add(name);
        }
        return Collections.unmodifiableSet(entries);
    }
}
