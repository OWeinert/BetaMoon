package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.source.NativeSourceAttachment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional local discovery/source configuration; it grants no native execution authority. */
public final class NativeDiscoverySettings {
    public final List<NativeSourceAttachment> sources = new ArrayList<>();
    public boolean includeJdk;

    public static NativeDiscoverySettings load(Path instance, List<String> warnings) {
        NativeDiscoverySettings settings = new NativeDiscoverySettings();
        Path file = instance.resolve("betamoon/config/native-access.json");
        if (!Files.exists(file)) {
            return settings;
        }
        try (InputStream input = Files.newInputStream(file)) {
            Map<String, Object> record = CatalogValues.object(CatalogJson.decode(
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(CatalogValues.bytes(input, 65536))).toString()));
            requireKeys(record, "schemaVersion", "includeJdk", "sourceAttachments");
            if (CatalogValues.integer(record, "schemaVersion") != 1) {
                throw new IllegalArgumentException("unsupported_config_schema");
            }
            if (record.containsKey("includeJdk")) {
                if (!(record.get("includeJdk") instanceof Boolean)) {
                    throw new IllegalArgumentException("includeJdk must be boolean");
                }
                settings.includeJdk = (Boolean) record.get("includeJdk");
            }
            Set<String> ids = new HashSet<>();
            if (record.containsKey("sourceAttachments")) {
                if (!(record.get("sourceAttachments") instanceof List)
                        || ((List<?>) record.get("sourceAttachments")).size() > 16) {
                    throw new IllegalArgumentException("source_attachment_limit");
                }
                for (Object value : (List<?>) record.get("sourceAttachments")) {
                    Map<String, Object> attachment = CatalogValues.object(value);
                    requireKeys(attachment, "id", "path", "originSha256");
                    String id = CatalogValues.text(attachment, "id");
                    String hash = (String) attachment.get("originSha256");
                    if (!ids.add(id) || (hash != null && !hash.matches("[0-9a-f]{64}"))) {
                        throw new IllegalArgumentException("invalid_source_attachment_identity");
                    }
                    Path path = Paths.get(CatalogValues.text(attachment, "path"));
                    settings.sources.add(new NativeSourceAttachment(id, path.isAbsolute() ? path : instance.resolve(path),
                            hash));
                }
            }
            return settings;
        } catch (IOException | RuntimeException exception) {
            NativeOriginCache.warn(warnings, "native_config_invalid: " + exception.getMessage());
            return new NativeDiscoverySettings();
        }
    }

    private static void requireKeys(Map<String, Object> values, String... allowed) {
        List<String> keys = Arrays.asList(allowed);
        for (String key : values.keySet()) {
            if (!keys.contains(key)) {
                throw new IllegalArgumentException("Unknown native discovery setting: " + key);
            }
        }
    }
}
