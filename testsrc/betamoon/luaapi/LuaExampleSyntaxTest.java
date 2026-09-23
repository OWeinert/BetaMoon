package betamoon.luaapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Compiles all distributed Lua examples without executing their Minecraft
 * calls.
 */
public final class LuaExampleSyntaxTest {
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
        for (int i = 0; i < sorted.length; i++) {
            File script = sorted[i];
            Reader reader = new InputStreamReader(new FileInputStream(script), "UTF-8");
            try {
                globals.load(reader, script.getName());
                compiled++;
            } finally {
                reader.close();
            }
        }
        System.out.println("Compiled " + compiled + " Lua examples.");
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
