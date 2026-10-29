package betamoon.interop;

import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogJson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Checks actual JAR contents rather than a loose build resource directory. */
public final class NativeCatalogArtifactTest {
    private static final String ROOT = "betamoon/interop/catalog/b1.7.3/";

    private NativeCatalogArtifactTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the BetaMoon JAR path");
        }
        try (ZipFile jar = new ZipFile(args[0])) {
            Map<?, ?> manifest = object(CatalogJson.decode(read(jar, ROOT + "manifest.json")));
            require("b1.7.3".equals(manifest.get("target")), "Wrong Minecraft catalog target");
            require(Long.valueOf(1).equals(manifest.get("schemaVersion")), "Unsupported catalog schema");
            Map<?, ?> files = object(manifest.get("files"));
            for (String required : new String[] {"classes-named.jsonl", "classes-client.jsonl", "classes-server.jsonl",
                    "classes-named-server.jsonl", "tiny-mappings.jsonl", "symbols.jsonl", "source-files.jsonl",
                    "source-associations.jsonl", "coverage.json"}) {
                require(files.containsKey(required), "Catalog manifest is missing " + required);
            }
            int records = 0;
            for (Map.Entry<?, ?> file : files.entrySet()) {
                String name = (String) file.getKey();
                require(name.matches("[a-z-]+\\.jsonl?"), "Unexpected catalog resource name");
                String text = read(jar, ROOT + name);
                require(CatalogFingerprint.sha256(text.getBytes(StandardCharsets.UTF_8)).equals(file.getValue()),
                        "Packaged catalog hash mismatch: " + name);
                if (name.endsWith(".jsonl")) {
                    for (String line : text.split("\n")) {
                        object(CatalogJson.decode(line));
                        records++;
                    }
                } else {
                    object(CatalogJson.decode(text));
                }
            }
            for (String buildClass : new String[] {"NativeCatalogGenerator", "CatalogBundleWriter",
                    "CatalogDiffReport", "SourceManifestGenerator"}) {
                require(jar.getEntry("betamoon/interop/" + buildClass + ".class") == null,
                        "Build-only tooling leaked into the mod JAR: " + buildClass);
            }
            System.out.println("Native catalog artifact verified: " + files.size() + " hashed resources, "
                    + records + " JSONL records.");
        }
    }

    private static String read(ZipFile jar, String name) throws IOException {
        ZipEntry entry = jar.getEntry(name);
        if (entry == null) {
            throw new IOException("Missing packaged resource: " + name);
        }
        try (InputStream input = jar.getInputStream(entry);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if ((long) output.size() + count > 32 * 1024 * 1024) {
                    throw new IOException("Packaged catalog resource limit exceeded");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static Map<?, ?> object(Object value) {
        require(value instanceof Map, "Expected a catalog JSON object");
        return (Map<?, ?>) value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
