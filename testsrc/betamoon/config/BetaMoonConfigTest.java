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

            File configFile = new File(root, "betamoon.config");
            String contents = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            require(contents.contains("hotReloadOnFileChange=false"), "Config must persist the disabled default");
            contents = contents.replace("hotReloadOnFileChange=false", "hotReloadOnFileChange=true");
            Files.write(configFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));

            BetaMoonConfig enabled = new BetaMoonConfig(root, "betamoon.config");
            require(enabled.getHotReloadOnFileChange().getValue(), "Config must accept enabled file-change reload");
            System.out.println("BetaMoon config passed: automatic hot reload defaults off and can be enabled.");
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
