package betamoon.luaapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Comparator;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Compiles all distributed Lua examples without executing their Minecraft calls. */
public final class LuaExampleSyntaxTest {
    private LuaExampleSyntaxTest() {
    }

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        File[] scripts = directory.listFiles();
        if (scripts == null) throw new IllegalStateException("Example directory is unavailable.");
        Arrays.sort(scripts, new Comparator<File>() {
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        Globals globals = JsePlatform.standardGlobals();
        int compiled = 0;
        for (int i = 0; i < scripts.length; i++) {
            File script = scripts[i];
            if (!script.isFile() || !script.getName().endsWith(".lua")) continue;
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
}
