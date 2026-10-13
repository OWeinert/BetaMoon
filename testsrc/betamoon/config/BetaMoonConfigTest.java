package betamoon.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Verifies grouped Forge settings, defaults, validation, and legacy migration. */
public final class BetaMoonConfigTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("betamoon-config-test").toFile();
        try {
            BetaMoonConfig defaults = new BetaMoonConfig(root);
            require(!defaults.getHotReloadOnFileChange().getValue(),
                    "Automatic file-change reload must default to disabled");
            require(!defaults.getShowScriptWarningsOnLoad().getValue(),
                    "Script warning notifications must default to disabled");
            require(defaults.getShowScriptErrorPopupOnStartup().getValue(), "Startup error popup must default on");
            require(defaults.getReloadDebounceMs().getValue() == 500, "Reload debounce must default to 500 ms");
            require(defaults.getShowSuccessfulReloadMessages().getValue(), "Successful reload notice must default on");
            require(defaults.getDisabledScripts().getValue().isEmpty(), "No scripts should be disabled by default");
            require(!defaults.getLogScriptLoadTimings().getValue(), "Timing log must default off");
            require(!defaults.getAutoExportDiagnosticsOnScriptError().getValue(), "Auto report must default off");
            require(defaults.getSlowCallbackWarningMs().getValue() == 0, "Slow callback warning must default off");
            require(!defaults.getVerboseAssetDiagnostics().getValue(), "Verbose assets must default off");

            File configFile = new File(root, BetaMoonConfig.FILE_NAME);
            String contents = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            require(contents.contains("scripts.hotReloadOnFileChange=false"),
                    "Config must persist the disabled default");
            require(contents.contains("notifications.showScriptWarningsOnLoad=false"),
                    "Config must persist the errors-only default");
            require(contents.contains("# Automatically reload scripts after their files change."),
                    "Forge property comments must be written");
            require(contents.contains("diagnostics.verboseAssetDiagnostics=false"),
                    "Diagnostic settings must be grouped by prefix");
            String defaultContents = contents;
            contents = contents.replace("scripts.hotReloadOnFileChange=false", "scripts.hotReloadOnFileChange=true");
            contents = contents.replace("notifications.showScriptWarningsOnLoad=false",
                    "notifications.showScriptWarningsOnLoad=true");
            contents = contents.replace("scripts.disabledScripts=", "scripts.disabledScripts=foo.lua, Pack, Pack.zip");
            contents = contents.replace("scripts.reloadDebounceMs=500", "scripts.reloadDebounceMs=99999");
            Files.write(configFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));

            BetaMoonConfig enabled = new BetaMoonConfig(root);
            require(enabled.getHotReloadOnFileChange().getValue(), "Config must accept enabled file-change reload");
            require(enabled.getShowScriptWarningsOnLoad().getValue(), "Config must accept script warning opt-in");
            require(enabled.getReloadDebounceMs().getValue() == 500, "Out-of-range debounce must reset to default");
            require(enabled.getDisabledScripts().getValue().contains("Pack.zip"),
                    "Disabled script names must be parsed from the config");

            File migrationDirectory = new File(root, "migration");
            Files.createDirectories(migrationDirectory.toPath());
            File legacyFile = new File(migrationDirectory, "betamoon.config");
            Files.write(legacyFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));
            BetaMoonConfig extensionMigrated = new BetaMoonConfig(migrationDirectory);
            require(extensionMigrated.getHotReloadOnFileChange().getValue(),
                    "Migrating the config filename must preserve existing settings");
            require(extensionMigrated.getShowScriptWarningsOnLoad().getValue(),
                    "Migrating the config filename must preserve script warning opt-in");
            require(new File(migrationDirectory, BetaMoonConfig.FILE_NAME).isFile(),
                    "Migration must create the .cfg file");
            require(!legacyFile.exists(), "Migration must remove the old filename");

            File coexistenceDirectory = new File(root, "coexistence");
            Files.createDirectories(coexistenceDirectory.toPath());
            File existingCurrent = new File(coexistenceDirectory, BetaMoonConfig.FILE_NAME);
            File existingLegacy = new File(coexistenceDirectory, "betamoon.config");
            Files.write(existingCurrent.toPath(), defaultContents.getBytes(StandardCharsets.UTF_8));
            Files.write(existingLegacy.toPath(), contents.getBytes(StandardCharsets.UTF_8));
            BetaMoonConfig preferred = new BetaMoonConfig(coexistenceDirectory);
            require(!preferred.getHotReloadOnFileChange().getValue(),
                    "An existing .cfg file must take precedence over the legacy file");
            require(existingLegacy.isFile(), "A conflicting legacy file must remain untouched");

            contents = contents.replace("notifications.showScriptWarningsOnLoad=true", "showPopupOnWarnings=true");
            Files.write(configFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));
            BetaMoonConfig migrated = new BetaMoonConfig(root);
            require(!migrated.getShowScriptWarningsOnLoad().getValue(),
                    "Existing unused warning setting must not keep notifications enabled");
            contents = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            require(!contents.contains("showPopupOnWarnings"), "Obsolete warning setting must be removed");
            String flat = defaultContents.replace("scripts.hotReloadOnFileChange=false", "hotReloadOnFileChange=true")
                    .replace("notifications.showScriptWarningsOnLoad=false", "showScriptWarningsOnLoad=true")
                    .replace("updates.checkForUpdates=true", "checkForUpdates=false")
                    .replace("updates.notifyUpdatesOnWorldJoin=true", "notifyUpdatesOnWorldJoin=false");
            File flatFile = new File(root, "flat.cfg");
            Files.write(flatFile.toPath(), flat.getBytes(StandardCharsets.UTF_8));
            BetaMoonConfig flatMigrated = new BetaMoonConfig(root, "flat.cfg");
            require(flatMigrated.getHotReloadOnFileChange().getValue(), "Flat reload setting must migrate");
            require(flatMigrated.getShowScriptWarningsOnLoad().getValue(), "Flat warning setting must migrate");
            require(!flatMigrated.getCheckForUpdates().getValue(), "Flat update check setting must migrate");
            require(!flatMigrated.getNotifyUpdatesOnWorldJoin().getValue(), "Flat join notice must migrate");
            String migratedContents = new String(Files.readAllBytes(flatFile.toPath()), StandardCharsets.UTF_8);
            require(!migratedContents.contains("   hotReloadOnFileChange="),
                    "Flat keys must be removed after migration");
            System.out.println("BetaMoon config passed: defaults, opt-in, legacy settings and filename migration.");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                deleteRecursively(children[i]);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
