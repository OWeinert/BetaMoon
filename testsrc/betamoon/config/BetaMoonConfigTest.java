package betamoon.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Verifies the persisted automatic-reload setting and its conservative default. */
public final class BetaMoonConfigTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("betamoon-config-test").toFile();
        try {
            BetaMoonConfig defaults = new BetaMoonConfig(root);
            require(!defaults.getHotReloadOnFileChange().getValue(),
                    "Automatic file-change reload must default to disabled");
            require(!defaults.getShowScriptWarningsOnLoad().getValue(),
                    "Script warning notifications must default to disabled");

            File configFile = new File(root, BetaMoonConfig.FILE_NAME);
            String contents = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            require(contents.contains("hotReloadOnFileChange=false"), "Config must persist the disabled default");
            require(contents.contains("showScriptWarningsOnLoad=false"), "Config must persist the errors-only default");
            String defaultContents = contents;
            contents = contents.replace("hotReloadOnFileChange=false", "hotReloadOnFileChange=true");
            contents = contents.replace("showScriptWarningsOnLoad=false", "showScriptWarningsOnLoad=true");
            Files.write(configFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));

            BetaMoonConfig enabled = new BetaMoonConfig(root);
            require(enabled.getHotReloadOnFileChange().getValue(), "Config must accept enabled file-change reload");
            require(enabled.getShowScriptWarningsOnLoad().getValue(), "Config must accept script warning opt-in");

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

            contents = contents.replace("showScriptWarningsOnLoad=true", "showPopupOnWarnings=true");
            Files.write(configFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));
            BetaMoonConfig migrated = new BetaMoonConfig(root);
            require(!migrated.getShowScriptWarningsOnLoad().getValue(),
                    "Existing unused warning setting must not keep notifications enabled");
            contents = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            require(!contents.contains("showPopupOnWarnings"), "Obsolete warning setting must be removed");
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
