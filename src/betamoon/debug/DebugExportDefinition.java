package betamoon.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable menu and execution metadata for one debug-export category. */
public final class DebugExportDefinition {
    private final String id;
    private final String title;
    private final String description;
    private final List<String> fileNames;
    private final boolean includedInComplete;
    private final DebugExporter exporter;

    DebugExportDefinition(String id, String title, String description, boolean includedInComplete,
            DebugExporter exporter, String... fileNames) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.includedInComplete = includedInComplete;
        this.exporter = exporter;
        List<String> files = new ArrayList<String>();
        Collections.addAll(files, fileNames);
        this.fileNames = Collections.unmodifiableList(files);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public String getFileSummary() {
        return String.join(", ", fileNames);
    }

    public boolean isIncludedInComplete() {
        return includedInComplete;
    }

    DebugExporter getExporter() {
        return exporter;
    }
}
