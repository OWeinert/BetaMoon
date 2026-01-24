package betamoon.config;

import forge.Configuration;
import forge.Property;
import betamoon.BetaMoonMain;
import betamoon.io.IoUtils;
import java.io.File;
import java.util.Optional;

public final class BetaMoonConfig {
    private Configuration config;
    private ConfigField<Boolean> showPopupOnWarnings;

    public BetaMoonConfig(String configFileName) {
        loadFileIntoConfig(configFileName);
        updateConfigFields();
    }

    public void updateConfigFields() {
        config.load();

        // Assign config fields
        showPopupOnWarnings = getOrCreateBooleanProperty(
            "showPopupOnWarnings",
            Configuration.GENERAL_PROPERTY,
            Optional.of(true)
        );

        config.save();
    }

    public ConfigField<Boolean> getShowPopupWarnings() {
        return showPopupOnWarnings;
    }



    private void loadFileIntoConfig(String configFileName) {
        File minecraftDir = IoUtils.resolveMinecraftDirFromCodeSource(BetaMoonMain.class);
        if (minecraftDir == null) {
            throw new IllegalStateException("Unable to resolve Minecraft directory for config.");
        }
        File configDir = new File(minecraftDir, "config");
        IoUtils.ensureDirectory(configDir);
        File configFile = new File(configDir, configFileName);
        config = new Configuration(configFile);
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
