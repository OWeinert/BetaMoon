package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.NativeClassInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reusable content metadata; current path, loaders and loaded status are attached separately each session. */
public final class OriginMetadata {
    public final String fingerprint;
    public final String fingerprintKind;
    public final Map<String, NativeClassInfo> classes;
    public final List<Map<String, Object>> files;

    public OriginMetadata(String fingerprint, String fingerprintKind, Map<String, NativeClassInfo> classes,
            List<Map<String, Object>> files) {
        this.fingerprint = fingerprint;
        this.fingerprintKind = fingerprintKind;
        this.classes = Collections.unmodifiableMap(new TreeMap<>(classes));
        List<Map<String, Object>> frozen = new ArrayList<>();
        for (Map<String, Object> file : files) {
            frozen.add(CatalogValues.object(CatalogValues.freeze(file)));
        }
        this.files = Collections.unmodifiableList(frozen);
    }
}
