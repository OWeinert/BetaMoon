package betamoon.interop;

import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.NativeClassInfo;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Build-JDK-only parse pass. No analyze/compile call, processor execution, or source-body export. */
public final class SourceManifestGenerator {
    private static final long MAX_SOURCE_BYTES = 16 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    private final String rootId;
    private final Map<String, SourceFile> files;

    private SourceManifestGenerator(String rootId, Map<String, SourceFile> files) {
        this.rootId = rootId;
        this.files = files;
    }

    public static SourceManifestGenerator scan(String rootId, Path root) throws IOException {
        Path realRoot = root.toRealPath();
        Map<String, SourceFile> files = new TreeMap<>();
        long[] totalBytes = {0};
        Files.walkFileTree(realRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                if (attributes.isRegularFile() && path.getFileName().toString().endsWith(".java")) {
                    if (files.size() >= 100000 || attributes.size() > MAX_SOURCE_BYTES
                            || (totalBytes[0] += attributes.size()) > MAX_TOTAL_BYTES) {
                        throw new IOException("Source manifest size limit exceeded");
                    }
                    String relative = realRoot.relativize(path).toString().replace('\\', '/');
                    files.put(relative, new SourceFile(path, relative, CatalogFingerprint.sha256(path)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        parse(files);
        for (SourceFile file : files.values()) {
            if (!Files.isRegularFile(file.path, LinkOption.NOFOLLOW_LINKS)
                    || !file.sha256.equals(CatalogFingerprint.sha256(file.path))) {
                throw new IOException("Source changed during manifest extraction: " + file.relative);
            }
        }
        return new SourceManifestGenerator(rootId, files);
    }

    public List<Map<String, Object>> records() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SourceFile file : files.values()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("rootId", rootId);
            record.put("path", file.relative);
            record.put("sha256", file.sha256);
            record.put("parseStatus", file.status);
            record.put("declaredTypes", file.types);
            result.add(record);
        }
        return result;
    }

    public Map<String, Object> association(NativeClassInfo type) {
        List<String> declared = new ArrayList<>();
        List<String> sourceFileMatches = new ArrayList<>();
        String name = type.getInternalName();
        String packagePath = name.substring(0, name.lastIndexOf('/') + 1);
        for (SourceFile file : files.values()) {
            if (file.types.contains(name)) {
                declared.add(file.relative);
            }
            if (type.getSourceFile() != null && file.relative.equals(packagePath + type.getSourceFile())) {
                sourceFileMatches.add(file.relative);
            }
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("type", name);
        record.put("rootId", rootId);
        record.put("paths", declared.isEmpty() ? sourceFileMatches : declared);
        record.put("confidence", !declared.isEmpty() ? "declared_type"
                : sourceFileMatches.isEmpty() ? "unavailable" : "package_sourcefile");
        record.put("memberLine", null);
        record.put("memberLineConfidence", "unknown");
        return record;
    }

    private static void parse(Map<String, SourceFile> files) throws IOException {
        if (files.isEmpty()) {
            return;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("Native source indexing requires the build JDK compiler (tools.jar on Java 8)");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<Path, SourceFile> byPath = new TreeMap<>();
        List<File> inputs = new ArrayList<>();
        for (SourceFile file : files.values()) {
            byPath.put(file.path, file);
            inputs.add(file.path.toFile());
        }
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    Arrays.asList("-proc:none", "-encoding", "UTF-8"), null,
                    manager.getJavaFileObjectsFromFiles(inputs));
            for (CompilationUnitTree unit : task.parse()) {
                SourceFile file = byPath.get(Paths.get(unit.getSourceFile().toUri()));
                if (file == null) {
                    throw new IOException("Compiler returned a source outside its manifest");
                }
                String packageName = unit.getPackageName() == null ? ""
                        : unit.getPackageName().toString().replace('.', '/') + "/";
                for (Tree declaration : unit.getTypeDecls()) {
                    collectTypes(declaration, packageName, file.types);
                }
                Collections.sort(file.types);
                file.status = "parsed";
            }
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR && diagnostic.getSource() != null) {
                SourceFile file = byPath.get(Paths.get(diagnostic.getSource().toUri()));
                if (file != null) {
                    file.status = "parse_error";
                    // Recovered AST names are not established declaration evidence.
                    file.types.clear();
                }
            }
        }
    }

    private static void collectTypes(Tree declaration, String prefix, List<String> types) {
        if (!(declaration instanceof ClassTree)) {
            return;
        }
        ClassTree type = (ClassTree) declaration;
        String simpleName = type.getSimpleName().toString();
        if (simpleName.isEmpty()) {
            return;
        }
        String name = prefix + simpleName;
        types.add(name);
        for (Tree member : type.getMembers()) {
            // Local/anonymous binary names need compiler attribution; do not invent their numeric suffixes.
            collectTypes(member, name + "$", types);
        }
    }

    private static final class SourceFile {
        private final Path path;
        private final String relative;
        private final String sha256;
        private final List<String> types = new ArrayList<>();
        private String status = "not_parsed";

        private SourceFile(Path path, String relative, String sha256) {
            this.path = path;
            this.relative = relative;
            this.sha256 = sha256;
        }
    }
}
