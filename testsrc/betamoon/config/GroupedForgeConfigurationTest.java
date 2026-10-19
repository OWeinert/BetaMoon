package betamoon.config;

import forge.Configuration;
import forge.Property;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

/** Round trips the Forge format and verifies that formatting cannot replace a file with partial output. */
final class GroupedForgeConfigurationTest {
    private GroupedForgeConfigurationTest() {
    }

    static void run(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path file = directory.resolve("grouped.cfg");
        GroupedForgeConfiguration configuration = new GroupedForgeConfiguration(file.toFile());
        configuration.getOrCreateProperty("scripts.disabledScripts", Configuration.GENERAL_PROPERTY, "P\u00e4ck.zip")
                .comment = "First line.\nSecond line: {with punctuation}.";
        configuration.getOrCreateProperty("logging.fileMinLevel", Configuration.GENERAL_PROPERTY, "info")
                .comment = "File severity.";
        configuration.getOrCreateProperty("logging.consoleMinLevel", Configuration.GENERAL_PROPERTY, "warn");
        configuration.getOrCreateProperty("custom.path", Configuration.GENERAL_PROPERTY, "C:\\mods\\P\u00e4ck.zip # = ;")
                .comment = "A path.\r\n\rA final line.";
        configuration.getOrCreateProperty("custom.empty", Configuration.GENERAL_PROPERTY, "");
        configuration.getOrCreateProperty("flat", Configuration.GENERAL_PROPERTY, "  spaced value  ");
        configuration.getOrCreateProperty("example.block", Configuration.BLOCK_PROPERTY, "201");
        configuration.getOrCreateProperty("example.item", Configuration.ITEM_PROPERTY, "5001");
        configuration.save();
        String contents = read(file);
        require(contents.contains("\r\n\r\n   # Logging\r\n")
                && contents.contains("\r\n\r\n   # Scripts\r\n"), "Prefix groups must have a heading and blank line");
        require(contents.contains("   # First line.\r\n   # Second line: {with punctuation}.\r\n"),
                "Every continuation line must remain a comment");
        require(!contents.contains("   # \r\n") && contents.contains("   # Other settings\r\n"),
                "Group spacing must not rely on empty comment markers");
        require(contents.indexOf("logging.consoleMinLevel") < contents.indexOf("logging.fileMinLevel"),
                "Properties must keep Forge's alphabetical order");
        Path nativeCopy = directory.resolve("native-reader.cfg");
        Files.write(nativeCopy, Files.readAllBytes(file));
        Configuration nativeConfiguration = new Configuration(nativeCopy.toFile());
        nativeConfiguration.load();
        GroupedForgeConfiguration reloaded = new GroupedForgeConfiguration(file.toFile());
        reloaded.load();
        compare(nativeConfiguration.generalProperties, reloaded.generalProperties);
        compare(nativeConfiguration.blockProperties, reloaded.blockProperties);
        compare(nativeConfiguration.itemProperties, reloaded.itemProperties);
        require(property(reloaded.generalProperties, "custom.path").value.equals("C:\\mods\\P\u00e4ck.zip # = ;")
                && property(reloaded.generalProperties, "flat").value.equals("  spaced value  "),
                "Values, punctuation, Latin-1 characters, and surrounding spaces must survive loading");
        reloaded.save();
        String regenerated = read(file);
        reloaded.load();
        reloaded.save();
        require(body(regenerated).equals(body(read(file))), "Repeated saves must keep stable, non-accumulating spacing");
        require(read(file).contains("   # Logging\r\n"), "Windows reload must release the handle before replacement");
        byte[] previous = Files.readAllBytes(file);
        property(reloaded.generalProperties, "custom.path").comment = "Unsupported character: \u2603";
        reloaded.save();
        require(Arrays.equals(previous, Files.readAllBytes(file)), "Failed encoding must preserve the original config");
        try (Stream<Path> files = Files.list(directory)) {
            require(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                    "Failed saves must clean up temporary files");
        }
        reject(directory.resolve("unscoped.cfg"), "outside=true\n", "has no scope");
        reject(directory.resolve("unsupported-section.cfg"), "logging {\n value=info\n}\n", "unknown section logging");
    }

    private static void compare(Map<?, ?> expected, Map<?, ?> actual) {
        require(expected.keySet().equals(actual.keySet()), "Both readers must retain the same properties");
        for (Map.Entry<?, ?> entry : expected.entrySet()) {
            Property first = (Property) entry.getValue();
            Property second = (Property) actual.get(entry.getKey());
            require(first.name.equals(second.name) && first.value.equals(second.value),
                    "Both readers must agree on " + first.name);
        }
    }

    private static void reject(Path file, String text, String reason) throws Exception {
        Files.write(file, text.getBytes(StandardCharsets.ISO_8859_1));
        try {
            new GroupedForgeConfiguration(file.toFile()).load();
            throw new AssertionError("Invalid config was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains(reason), "Malformed config must report its cause");
        }
    }

    private static Property property(Map<?, ?> properties, String name) {
        return (Property) properties.get(name);
    }

    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
    }

    private static String body(String contents) {
        return contents.substring(contents.indexOf("general {"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
