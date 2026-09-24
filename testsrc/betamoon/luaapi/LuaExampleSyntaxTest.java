package betamoon.luaapi;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Compiles all distributed Lua examples without executing their Minecraft
 * calls.
 */
public final class LuaExampleSyntaxTest {
    private static final Pattern ID_CONSTANT = Pattern.compile(
            "\\blocal\\s+([A-Z][A-Z0-9_]*_ID)\\s*=\\s*(\\d+)\\b");
    private static final Pattern LITERAL_ID_FIELD = Pattern.compile("\\bid\\s*=\\s*(\\d+)\\s*[,}]");
    private static final Pattern CONSTANT_ID_FIELD = Pattern.compile(
            "\\bid\\s*=\\s*([A-Z][A-Z0-9_]*_ID)\\s*[,}]");
    private static final Pattern BLOCK_HELPER = Pattern.compile("\\bblock\\s*\\(\\s*(\\d+)\\s*,");

    private LuaExampleSyntaxTest() {
    }

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        if (!directory.isDirectory()) {
            throw new IllegalStateException("Example directory is unavailable.");
        }
        List<File> scripts = new ArrayList<File>();
        collectScripts(directory, scripts);
        File[] sorted = scripts.toArray(new File[scripts.size()]);
        Arrays.sort(sorted, new Comparator<File>() {
            public int compare(File left, File right) {
                return left.getPath().compareTo(right.getPath());
            }
        });
        Globals globals = JsePlatform.standardGlobals();
        int compiled = 0;
        Map<Integer, String> contentIds = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < sorted.length; i++) {
            File script = sorted[i];
            String source = new String(Files.readAllBytes(script.toPath()), StandardCharsets.UTF_8);
            globals.load(source, script.getName());
            collectContentIds(script, source, contentIds);
            compiled++;
        }
        System.out.println("Compiled " + compiled + " Lua examples and verified unique literal content IDs.");
    }

    private static void collectContentIds(File script, String source, Map<Integer, String> contentIds) {
        Map<String, Integer> constants = new LinkedHashMap<String, Integer>();
        Matcher constantsMatcher = ID_CONSTANT.matcher(source);
        while (constantsMatcher.find()) {
            constants.put(constantsMatcher.group(1), Integer.valueOf(constantsMatcher.group(2)));
        }

        Matcher literalFields = LITERAL_ID_FIELD.matcher(source);
        while (literalFields.find()) {
            addContentId(script, Integer.parseInt(literalFields.group(1)), contentIds);
        }
        Matcher constantFields = CONSTANT_ID_FIELD.matcher(source);
        while (constantFields.find()) {
            Integer id = constants.get(constantFields.group(1));
            if (id != null) {
                addContentId(script, id.intValue(), contentIds);
            }
        }
        Matcher blockHelpers = BLOCK_HELPER.matcher(source);
        while (blockHelpers.find()) {
            addContentId(script, Integer.parseInt(blockHelpers.group(1)), contentIds);
        }
    }

    private static void addContentId(File script, int id, Map<Integer, String> contentIds) {
        String current = script.getPath().replace('\\', '/');
        String previous = contentIds.put(Integer.valueOf(id), current);
        if (previous != null) {
            throw new IllegalStateException("Example content ID " + id + " is declared by " + previous
                    + " and " + current + ".");
        }
    }

    private static void collectScripts(File directory, List<File> scripts) {
        File[] children = directory.listFiles();
        if (children == null) {
            throw new IllegalStateException("Could not read example directory " + directory);
        }
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child.isDirectory()) {
                collectScripts(child, scripts);
            } else if (child.getName().endsWith(".lua")) {
                scripts.add(child);
            }
        }
    }
}
