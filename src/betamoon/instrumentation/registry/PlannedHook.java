package betamoon.instrumentation.registry;

import betamoon.instrumentation.api.HookDefinition;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One hook together with the namespaces that can produce a target class name. */
public final class PlannedHook {
    private final HookDefinition definition;
    private final List<RuntimeNamespace> namespaces = new ArrayList<RuntimeNamespace>();

    PlannedHook(HookDefinition definition) {
        this.definition = definition;
    }

    void addNamespace(RuntimeNamespace namespace) {
        if (!namespaces.contains(namespace)) {
            namespaces.add(namespace);
        }
    }

    public HookDefinition getDefinition() {
        return definition;
    }

    public List<RuntimeNamespace> getNamespaces() {
        return Collections.unmodifiableList(namespaces);
    }
}
