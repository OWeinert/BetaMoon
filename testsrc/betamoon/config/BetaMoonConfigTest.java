package betamoon.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Verifies the persisted automatic-reload setting and its conservative default. */
public final class BetaMoonConfigTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("betamoon-config-test").toFile();
        try {
            BetaMoonConfig defaults = new BetaMoonConfig(root, "betamoon.config");
            require(!defaults.getHotReloadOnFileChange().getValue(),
                    "Automatic file-change reload must default to disabled");
            require(!defaults.getShowScriptWarningsOnLoad().getValue(),
                    "Script warning notifications must default to disabled");

            File configFile = new File(root, "betamoon.config");
            String contents = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            require(contents.contains("hotReloadOnFileChange=false"), "Config must persist the disabled default");
            require(contents.contains("showScriptWarningsOnLoad=false"), "Config must persist the errors-only default");
            contents = contents.replace("hotReloadOnFileChange=false", "hotReloadOnFileChange=true");
            contents = contents.replace("showScriptWarningsOnLoad=false", "showScriptWarningsOnLoad=true");
            Files.write(configFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));

            BetaMoonConfig enabled = new BetaMoonConfig(root, "betamoon.config");
            require(enabled.getHotReloadOnFileChange().getValue(), "Config must accept enabled file-change reload");
            require(enabled.getShowScriptWarningsOnLoad().getValue(), "Config must accept script warning opt-in");

            contents = contents.replace("showScriptWarningsOnLoad=true", "showPopupOnWarnings=true");
            Files.write(configFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));
            BetaMoonConfig migrated = new BetaMoonConfig(root, "betamoon.config");
            require(!migrated.getShowScriptWarningsOnLoad().getValue(),
                    "Existing unused warning setting must not keep notifications enabled");
            contents = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            require(!contents.contains("showPopupOnWarnings"), "Obsolete warning setting must be removed");
            System.out.println("BetaMoon config passed: conservative reload and script warning defaults, opt-in and migration.");
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
