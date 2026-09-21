package betamoon.update;

import java.net.URI;

/** The immutable release shared by the menu and local join notification. */
public final class UpdateRelease {
    private final SemanticVersion version;
    private final String sourceName;
    private final URI pageUri;

    public UpdateRelease(SemanticVersion version, String sourceName, URI pageUri) {
        if (version == null || sourceName == null || !isReleasePage(pageUri)) {
            throw new IllegalArgumentException("Invalid update release");
        }
        this.version = version;
        this.sourceName = sourceName;
        this.pageUri = pageUri;
    }

    public SemanticVersion getVersion() {
        return version;
    }

    public String getSourceName() {
        return sourceName;
    }

    public URI getPageUri() {
        return pageUri;
    }

    public static boolean isReleasePage(URI uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
                || uri.getQuery() != null || uri.getFragment() != null) {
            return false;
        }
        String path = uri.getRawPath();
        if (path == null) {
            return false;
        }
        if ("modrinth.com".equals(uri.getHost())) {
            return path.matches("/mod/betamoon/version/[0-9A-Za-z]+");
        }
        return "github.com".equals(uri.getHost()) && path.matches("/OWeinert/BetaMoon/releases/tag/[0-9A-Za-z.+%-]+");
    }
}
