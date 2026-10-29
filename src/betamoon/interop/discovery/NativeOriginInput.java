package betamoon.interop.discovery;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-captured provenance; a worker receives paths/strings, never live game or loader objects. */
public final class NativeOriginInput {
    public final String id;
    public final Path path;
    public final List<String> loaderIds;
    public final List<String> modEntries;
    public final boolean gameOrigin;
    public final String evidence;

    public NativeOriginInput(String id, Path path, List<String> loaderIds, List<String> modEntries,
            boolean gameOrigin, String evidence) {
        this.id = id;
        this.path = path.toAbsolutePath().normalize();
        this.loaderIds = Collections.unmodifiableList(new ArrayList<>(loaderIds));
        this.modEntries = Collections.unmodifiableList(new ArrayList<>(modEntries));
        this.gameOrigin = gameOrigin;
        this.evidence = evidence;
    }
}
