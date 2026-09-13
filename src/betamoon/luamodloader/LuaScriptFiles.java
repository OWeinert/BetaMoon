package betamoon.luamodloader;

import betamoon.io.FileIo;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Discovers Lua source files and performs source-only loader checks. */
final class LuaScriptFiles {
    private static final long EMPTY_FINGERPRINT = 1125899906842597L;

    List<File> list(File directory) {
        List<File> scripts = new ArrayList<>();
        File[] files = directory == null ? null : directory.listFiles();
        if (files == null) {
            return scripts;
        }

        for (int i = 0; i < files.length; i++) {
            if (isLuaScript(files[i])) {
                scripts.add(files[i]);
            }
        }
        return scripts;
    }

    List<PreflightFailure> preflight(File directory) {
        List<PreflightFailure> failures = new ArrayList<>();
        List<File> scripts = list(directory);
        for (int i = 0; i < scripts.size(); i++) {
            File script = scripts.get(i);
            try {
                String source = FileIo.readUtf8Normalized(script);
                JsePlatform.standardGlobals().load(source, script.getName());
            } catch (Throwable error) {
                failures.add(new PreflightFailure(script.getName(), error.getMessage()));
            }
        }
        return failures;
    }

    long fingerprint(File directory) {
        List<File> scripts = list(directory);
        File[] sorted = scripts.toArray(new File[scripts.size()]);
        Arrays.sort(sorted, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });

        long value = EMPTY_FINGERPRINT;
        for (int i = 0; i < sorted.length; i++) {
            File file = sorted[i];
            value = value * 31L + file.getName().hashCode();
            value = value * 31L + file.lastModified();
            value = value * 31L + file.length();
        }
        return value;
    }

    private boolean isLuaScript(File file) {
        return file.isFile() && file.getName().endsWith(".lua");
    }

    static final class PreflightFailure {
        private final String fileName;
        private final String cause;

        private PreflightFailure(String fileName, String cause) {
            this.fileName = fileName;
            this.cause = cause;
        }

        String getFileName() {
            return fileName;
        }

        String getCause() {
            return cause;
        }
    }
}
