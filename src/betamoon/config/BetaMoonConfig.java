package betamoon.config;

import betamoon.BetaMoonCommon;
import betamoon.io.IoUtils;
import forge.Configuration;
import forge.Property;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

public final class BetaMoonConfig {
    public static final String FILE_NAME = "betamoon.cfg";
    private static final String LEGACY_FILE_NAME = "betamoon.config";

    private Configuration config;
    private ConfigField<Boolean> showScriptWarningsOnLoad;
    private ConfigField<Boolean> checkForUpdates;
    private ConfigField<Boolean> notifyUpdatesOnWorldJoin;
    private ConfigField<Boolean> hotReloadOnFileChange;

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
        config = new Configuration(resolveConfigFile(directory, configFileName));
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
                BetaMoonCommon.LOGGER.warning("Both BetaMoon config files exist; using " + currentPath
                        + " and leaving " + legacyPath + " unchanged.");
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
        config.load();

        // The old setting was persisted but never read by the UI. Replace it so
        // existing generated configs also receive the new errors-only default.
        config.generalProperties.remove("showPopupOnWarnings");

        // Assign config fields
        showScriptWarningsOnLoad = getOrCreateBooleanProperty("showScriptWarningsOnLoad",
                Configuration.GENERAL_PROPERTY,
                Optional.of(false));
        checkForUpdates = getOrCreateBooleanProperty("checkForUpdates", Configuration.GENERAL_PROPERTY,
                Optional.of(true));
        notifyUpdatesOnWorldJoin = getOrCreateBooleanProperty("notifyUpdatesOnWorldJoin",
                Configuration.GENERAL_PROPERTY, Optional.of(true));
        hotReloadOnFileChange = getOrCreateBooleanProperty("hotReloadOnFileChange",
                Configuration.GENERAL_PROPERTY, Optional.of(false));

        config.save();
    }

    public ConfigField<Boolean> getShowScriptWarningsOnLoad() {
        return showScriptWarningsOnLoad;
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

    private static File resolveDefaultConfigDirectory() {
        File minecraftDir = IoUtils.resolveMinecraftDirFromCodeSource(BetaMoonCommon.class);
        if (minecraftDir == null) {
            throw new IllegalStateException("Unable to resolve Minecraft directory for config.");
        }
        return new File(minecraftDir, "config");
    }

    private ConfigField<String> getOrCreateStringProperty(String name, int type, Optional<String> defaultValue) {
        String defValInternal = defaultValue.orElse("");
        Property prop = config.getOrCreateProperty(name, type, defValInternal);
        return new ConfigField<String>(name, prop == null ? defValInternal : prop.value);
    }

    private ConfigField<Integer> getOrCreateIntProperty(String name, int type, Optional<Integer> defaultValue) {
        Property prop = config.getOrCreateIntProperty(name, type, defaultValue.orElse(0));
        return new ConfigField<Integer>(name, parseInt(prop.value, defaultValue.orElse(0)));
    }

    private ConfigField<Boolean> getOrCreateBooleanProperty(String name, int type, Optional<Boolean> defaultValue) {
        Property prop = config.getOrCreateBooleanProperty(name, type, defaultValue.orElse(false));
        return new ConfigField<Boolean>(name, "true".equalsIgnoreCase(prop.value));
    }

    private ConfigField<Integer> getOrCreateBlockIdProperty(String name, Optional<Integer> defaultValue) {
        Property prop = config.getOrCreateBlockIdProperty(name, defaultValue.orElse(0));
        return new ConfigField<Integer>(name, parseInt(prop.value, defaultValue.orElse(0)));
    }

    private ConfigField<Integer> getOrCreateItemIdProperty(String name, Optional<Integer> defaultValue) {
        Property prop = config.getOrCreateIntProperty(name, Configuration.ITEM_PROPERTY, defaultValue.orElse(0));
        return new ConfigField<Integer>(name, parseInt(prop.value, defaultValue.orElse(0)));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
