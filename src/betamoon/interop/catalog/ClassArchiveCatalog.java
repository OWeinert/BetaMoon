package betamoon.interop.catalog;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** One verified archive's class metadata. Runtime loader visibility is deliberately not inferred. */
public final class ClassArchiveCatalog {
    private final String contentSha256;
    private final Map<String, NativeClassInfo> classes;
    private final Map<String, String> classFingerprints;

    ClassArchiveCatalog(String contentSha256, Map<String, NativeClassInfo> classes,
            Map<String, String> classFingerprints) {
        this.contentSha256 = contentSha256;
        this.classes = Collections.unmodifiableMap(new TreeMap<>(classes));
        this.classFingerprints = Collections.unmodifiableMap(new TreeMap<>(classFingerprints));
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public Map<String, NativeClassInfo> getClasses() {
        return classes;
    }

    public Map<String, String> getClassFingerprints() {
        return classFingerprints;
    }
}
