package betamoon.config;

import betamoon.BetaMoonCommon;
import forge.Configuration;
import forge.Property;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;

/** Forge 1.0.6 format with closed readers, readable property groups, and replace-on-success saves. */
public final class GroupedForgeConfiguration extends Configuration {
    private static final String NEWLINE = "\r\n";
    private final Path configurationFile;

    public GroupedForgeConfiguration(File file) {
        super(file);
        configurationFile = file.toPath().toAbsolutePath().normalize();
    }

    @Override
    public void load() {
        try {
            Files.createDirectories(configurationFile.getParent());
            if (!Files.exists(configurationFile)) {
                Files.createFile(configurationFile);
            }
            try (BufferedReader reader = Files.newBufferedReader(configurationFile, StandardCharsets.ISO_8859_1)) {
                Map<String, Property> currentSection = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    currentSection = readLine(line, currentSection);
                }
            }
        } catch (IOException | SecurityException failure) {
            BetaMoonCommon.LOGGER.log(Level.WARNING, "Could not read configuration " + configurationFile, failure);
        }
    }

    /** Matches Forge's names, fixed sections, whitespace, and untrimmed values after '='. */
    private Map<String, Property> readLine(String line, Map<String, Property> currentSection) {
        int nameStart = -1;
        int nameEnd = -1;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '.') {
                if (nameStart == -1) {
                    nameStart = index;
                }
                nameEnd = index;
            } else if (!Character.isWhitespace(character)) {
                switch (character) {
                    case '#':
                        return currentSection;
                    case '=':
                        String name = line.substring(nameStart, nameEnd + 1);
                        if (currentSection == null) {
                            throw new IllegalArgumentException("property " + name + " has no scope");
                        }
                        Property property = new Property();
                        property.name = name;
                        property.value = line.substring(index + 1);
                        currentSection.put(name, property);
                        return currentSection;
                    case '{':
                        currentSection = section(line.substring(nameStart, nameEnd + 1));
                        break;
                    case '}':
                        currentSection = null;
                        break;
                    default:
                        throw new IllegalArgumentException("unknown character " + character);
                }
            }
        }
        return currentSection;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Property> section(String name) {
        switch (name) {
            case "general":
                return generalProperties;
            case "block":
                return blockProperties;
            case "item":
                return itemProperties;
            default:
                throw new IllegalArgumentException("unknown section " + name);
        }
    }

    @Override
    public void save() {
        Path temporary = null;
        try {
            Path destination = Files.isSymbolicLink(configurationFile)
                    ? configurationFile.toRealPath() : configurationFile;
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), ".betamoon-config-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.ISO_8859_1)) {
                line(writer, "# Configuration file");
                line(writer, "# Generated on " + DateFormat.getInstance().format(new Date()));
                line(writer, "");
                writeSection(writer, "general", "General");
                writeSection(writer, "block", "Block");
                writeSection(writer, "item", "Item");
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException | SecurityException failure) {
            BetaMoonCommon.LOGGER.log(Level.WARNING, "Could not save configuration " + configurationFile, failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException failure) {
                    BetaMoonCommon.LOGGER.log(Level.WARNING, "Could not remove temporary configuration " + temporary,
                            failure);
                }
            }
        }
    }

    private void writeSection(BufferedWriter writer, String name, String title) throws IOException {
        char[] frame = new char[title.length() + 4];
        Arrays.fill(frame, '#');
        String border = new String(frame);
        line(writer, border);
        line(writer, "# " + title + " #");
        line(writer, border);
        line(writer, "");
        line(writer, name + " {");
        String previousGroup = null;
        for (Property property : section(name).values()) {
            String group = group(property.name);
            if (!group.equals(previousGroup)) {
                if (previousGroup != null) {
                    line(writer, "");
                }
                line(writer, "   # " + heading(group));
                previousGroup = group;
            }
            if (property.comment != null) {
                for (String commentLine : property.comment.split("\\r\\n|\\r|\\n", -1)) {
                    line(writer, commentLine.isEmpty() ? "" : "   # " + commentLine);
                }
            }
            line(writer, "   " + property.name + "=" + property.value);
        }
        line(writer, "}");
        line(writer, "");
    }

    private static String group(String name) {
        int separator = name.indexOf('.');
        return separator < 0 ? "" : name.substring(0, separator);
    }

    private static String heading(String group) {
        if (group.isEmpty()) {
            return "Other settings";
        }
        char first = group.charAt(0);
        if (first >= 'a' && first <= 'z') {
            first = (char) (first - 'a' + 'A');
        }
        return first + group.substring(1);
    }

    private static void line(BufferedWriter writer, String text) throws IOException {
        writer.write(text);
        writer.write(NEWLINE);
    }
}
