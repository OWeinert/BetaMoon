package betamoon.config;

import betamoon.BetaMoonCommon;
import betamoon.io.IoUtils;
import forge.Configuration;
import forge.Property;
import java.io.File;
import java.util.Optional;

public final class BetaMoonConfig {
    private Configuration config;
    private ConfigField<Boolean> showPopupOnWarnings;
    private ConfigField<Boolean> checkForUpdates;
    private ConfigField<Boolean> notifyUpdatesOnWorldJoin;
    private ConfigField<Boolean> hotReloadOnFileChange;

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
        config = new Configuration(new File(directory, configFileName));
        updateConfigFields();
    }

    public void updateConfigFields() {
        config.load();

        // Assign config fields
        showPopupOnWarnings = getOrCreateBooleanProperty("showPopupOnWarnings", Configuration.GENERAL_PROPERTY,
                Optional.of(true));
        checkForUpdates = getOrCreateBooleanProperty("checkForUpdates", Configuration.GENERAL_PROPERTY,
                Optional.of(true));
        notifyUpdatesOnWorldJoin = getOrCreateBooleanProperty("notifyUpdatesOnWorldJoin",
                Configuration.GENERAL_PROPERTY, Optional.of(true));
        hotReloadOnFileChange = getOrCreateBooleanProperty("hotReloadOnFileChange",
                Configuration.GENERAL_PROPERTY, Optional.of(false));

        config.save();
    }

    public ConfigField<Boolean> getShowPopupWarnings() {
        return showPopupOnWarnings;
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
