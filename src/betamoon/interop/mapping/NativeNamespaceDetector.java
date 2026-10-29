package betamoon.interop.mapping;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.util.Map;

/** Uses several already linked identities; no short-name guessing or probing class initialization. */
public final class NativeNamespaceDetector {
    private NativeNamespaceDetector() {
    }

    public static String detect(Map<String, String> canonicalToObserved, TinyMappingResolver mappings) {
        String selected = null;
        for (RuntimeNamespace namespace : new RuntimeNamespace[] {RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT}) {
            boolean matches = !canonicalToObserved.isEmpty();
            for (Map.Entry<String, String> entry : canonicalToObserved.entrySet()) {
                matches &= mappings.resolveClass(new ClassRef(entry.getKey()), namespace).replace('/', '.')
                        .equals(entry.getValue());
            }
            if (matches) {
                if (selected != null) {
                    throw new IllegalArgumentException("namespace_ambiguous");
                }
                selected = namespace.getMappingName();
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("namespace_conflict");
        }
        return selected;
    }
}
