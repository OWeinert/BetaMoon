package betamoon.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.minecraft.src.EnumJsonNodeType;
import net.minecraft.src.J_InvalidSyntaxException;
import net.minecraft.src.J_JdomParser;
import net.minecraft.src.J_JsonNode;
import net.minecraft.src.J_JsonNodeFactories;

/**
 * Performs one bounded startup lookup; consumers only read its published
 * result.
 */
public final class UpdateChecker {
    static final String MODRINTH = "https://api.modrinth.com/v2/project/BwTdIvv1/version?include_changelog=false";
    static final String GITHUB = "https://api.github.com/repos/OWeinert/BetaMoon/releases?per_page=100&page=";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PAGES = 5;

    private final Transport transport;
    private final Logger logger;
    private boolean started;
    private volatile UpdateRelease availableUpdate;

    public UpdateChecker(Logger logger) {
        this(UpdateChecker::readResponse, logger);
    }

    UpdateChecker(Transport transport, Logger logger) {
        this.transport = transport;
        this.logger = logger;
    }

    public synchronized void startOnce(String installedVersion) {
        if (started) {
            return;
        }
        started = true;
        Thread lookup = new Thread(() -> checkOnce(installedVersion), "BetaMoon update check");
        lookup.start();
    }

    public UpdateRelease getAvailableUpdate() {
        return availableUpdate;
    }

    private void checkOnce(String installedText) {
        try {
            SemanticVersion installed = SemanticVersion.parse(installedText);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            String userAgent = "OWeinert/BetaMoon/" + installedText + " (https://github.com/OWeinert/BetaMoon)";
            UpdateRelease release;
            try {
                release = findModrinthRelease(userAgent, deadline);
            } catch (IOException | RuntimeException failure) {
                release = null;
            }
            if (release == null) {
                release = findGitHubRelease(userAgent, deadline);
            }
            if (release != null && release.getVersion().comparePrecedence(installed) > 0) {
                availableUpdate = release;
            }
        } catch (IOException | RuntimeException failure) {
            logger.fine("Update check unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private UpdateRelease findModrinthRelease(String userAgent, long deadline) throws IOException {
        Response response = transport.fetch(MODRINTH, userAgent, deadline);
        UpdateRelease newest = null;
        for (J_JsonNode entry : parseEntries(response.body)) {
            try {
                if (!"listed".equals(stringField(entry, "status"))
                        || !contains(arrayField(entry, "game_versions"), "b1.7.3")
                        || !contains(arrayField(entry, "loaders"), "modloader")
                        || !hasModrinthJar(arrayField(entry, "files"))) {
                    continue;
                }
                SemanticVersion version = SemanticVersion.parse(stringField(entry, "version_number"));
                URI page = URI.create("https://modrinth.com/mod/betamoon/version/" + stringField(entry, "id"));
                newest = newer(newest, new UpdateRelease(version, "Modrinth", page));
            } catch (RuntimeException invalidEntry) {
                // One malformed entry must not hide other valid published versions.
            }
        }
        return newest;
    }

    private UpdateRelease findGitHubRelease(String userAgent, long deadline) throws IOException {
        UpdateRelease newest = null;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Response response = transport.fetch(GITHUB + page, userAgent, deadline);
            for (J_JsonNode entry : parseEntries(response.body)) {
                try {
                    J_JsonNode draft = field(entry, "draft");
                    if (draft == null || draft.func_27218_a() != EnumJsonNodeType.FALSE
                            || stringField(entry, "published_at").isEmpty()) {
                        continue;
                    }
                    String tag = stringField(entry, "tag_name");
                    String versionText = tag.startsWith("v") ? tag.substring(1) : tag;
                    SemanticVersion version = SemanticVersion.parse(versionText);
                    if (!hasGitHubJar(arrayField(entry, "assets"), versionText)) {
                        continue;
                    }
                    URI uri = URI.create(stringField(entry, "html_url"));
                    if (!uri.getPath().equals("/OWeinert/BetaMoon/releases/tag/" + tag)) {
                        continue;
                    }
                    newest = newer(newest, new UpdateRelease(version, "GitHub", uri));
                } catch (RuntimeException invalidEntry) {
                    // Ignore invalid versions and entries without a usable download.
                }
            }
            if (!response.hasNextPage) {
                return newest;
            }
        }
        throw new IOException("Release pagination limit exceeded");
    }

    private static UpdateRelease newer(UpdateRelease current, UpdateRelease candidate) {
        if (current == null) {
            return candidate;
        }
        int precedence = candidate.getVersion().comparePrecedence(current.getVersion());
        if (precedence > 0 || (precedence == 0
                && candidate.getPageUri().toString().compareTo(current.getPageUri().toString()) < 0)) {
            return candidate;
        }
        return current;
    }

    private static boolean contains(List<J_JsonNode> values, String expected) {
        for (J_JsonNode value : values) {
            if (value.func_27218_a() == EnumJsonNodeType.STRING && expected.equals(value.func_27216_b())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasModrinthJar(List<J_JsonNode> files) {
        for (J_JsonNode file : files) {
            if (stringField(file, "filename").endsWith(".jar") && stringField(file, "url").startsWith("https://")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGitHubJar(List<J_JsonNode> assets, String version) {
        for (J_JsonNode asset : assets) {
            if (("betamoon-" + version + ".jar").equals(stringField(asset, "name"))
                    && stringField(asset, "browser_download_url")
                            .startsWith("https://github.com/OWeinert/BetaMoon/releases/download/")) {
                return true;
            }
        }
        return false;
    }

    /** Minecraft Beta 1.7.3 already ships Argo's JSON tree parser. */
    private static List<J_JsonNode> parseEntries(String json) throws IOException {
        try {
            return array(new J_JdomParser().func_27367_a(json));
        } catch (J_InvalidSyntaxException invalidJson) {
            throw new IOException("Invalid release JSON", invalidJson);
        }
    }

    private static J_JsonNode field(J_JsonNode object, String name) {
        return (J_JsonNode) object.func_27214_c().get(J_JsonNodeFactories.func_27316_a(name));
    }

    private static String stringField(J_JsonNode object, String name) {
        J_JsonNode value = field(object, name);
        return value != null && value.func_27218_a() == EnumJsonNodeType.STRING ? value.func_27216_b() : "";
    }

    private static List<J_JsonNode> arrayField(J_JsonNode object, String name) {
        return array(field(object, name));
    }

    @SuppressWarnings("unchecked")
    private static List<J_JsonNode> array(J_JsonNode node) {
        if (node == null || node.func_27218_a() != EnumJsonNodeType.ARRAY) {
            throw new IllegalArgumentException("Expected release JSON array");
        }
        return (List<J_JsonNode>) node.func_27215_d();
    }

    static Response readResponse(String address, String userAgent, long deadline) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(remainingTimeout(deadline));
        connection.setReadTimeout(remainingTimeout(deadline));
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Release API HTTP " + connection.getResponseCode());
            }
            if (connection.getContentLengthLong() > MAX_BYTES) {
                throw new IOException("Release response too large");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                while (true) {
                    connection.setReadTimeout(remainingTimeout(deadline));
                    int count = input.read(buffer);
                    if (count < 0) {
                        break;
                    }
                    if (bytes.size() + count > MAX_BYTES) {
                        throw new IOException("Release response too large");
                    }
                    bytes.write(buffer, 0, count);
                }
            }
            remainingTimeout(deadline);
            String link = connection.getHeaderField("Link");
            return new Response(new String(bytes.toByteArray(), StandardCharsets.UTF_8),
                    link != null && link.contains("rel=\"next\""));
        } finally {
            connection.disconnect();
        }
    }

    private static int remainingTimeout(long deadline) throws IOException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) {
            throw new IOException("Update check timed out");
        }
        return (int) Math.min(5000, remaining);
    }

    interface Transport {
        Response fetch(String address, String userAgent, long deadline) throws IOException;
    }

    static final class Response {
        final String body;
        final boolean hasNextPage;

        Response(String body, boolean hasNextPage) {
            this.body = body;
            this.hasNextPage = hasNextPage;
        }
    }
}
