package betamoon.debug;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable outcome of a complete or category-specific debug export run. */
public final class DebugExportResult {
    public enum Status {
        COMPLETE,
        INCOMPLETE,
        FAILED
    }

    public static final class FileResult {
        private final String name;
        private final int records;

        FileResult(String name, int records) {
            this.name = name;
            this.records = records;
        }

        public String getName() {
            return name;
        }

        public int getRecords() {
            return records;
        }
    }

    public static final class CategoryResult {
        private final String id;
        private final String title;
        private final List<FileResult> files;
        private final List<String> warnings;
        private final String failure;

        CategoryResult(String id, String title, List<FileResult> files, List<String> warnings, String failure) {
            this.id = id;
            this.title = title;
            this.files = immutable(files);
            this.warnings = immutable(warnings);
            this.failure = failure;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public List<FileResult> getFiles() {
            return files;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getFailure() {
            return failure;
        }

        public boolean isComplete() {
            return failure == null;
        }
    }

    private final Status status;
    private final File directory;
    private final List<CategoryResult> categories;
    private final String failure;
    private final long elapsedMillis;

    DebugExportResult(Status status, File directory, List<CategoryResult> categories, String failure,
            long elapsedMillis) {
        this.status = status;
        this.directory = directory;
        this.categories = immutable(categories);
        this.failure = failure;
        this.elapsedMillis = elapsedMillis;
    }

    static DebugExportResult failed(File directory, String failure, long elapsedMillis) {
        return new DebugExportResult(Status.FAILED, directory, Collections.<CategoryResult>emptyList(), failure,
                elapsedMillis);
    }

    public Status getStatus() {
        return status;
    }

    public File getDirectory() {
        return directory;
    }

    public List<CategoryResult> getCategories() {
        return categories;
    }

    public String getFailure() {
        return failure;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public int getFileCount() {
        if (status == Status.FAILED) {
            return 0;
        }
        int count = 1;
        for (CategoryResult category : categories) {
            count += category.files.size();
        }
        return count;
    }

    public int getRecordCount() {
        int count = 0;
        for (CategoryResult category : categories) {
            for (FileResult file : category.files) {
                count += file.records;
            }
        }
        return count;
    }

    public int getWarningCount() {
        int count = 0;
        for (CategoryResult category : categories) {
            count += category.warnings.size();
        }
        return count;
    }

    public int getFailureCount() {
        int count = 0;
        for (CategoryResult category : categories) {
            if (!category.isComplete()) {
                count++;
            }
        }
        return count;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
