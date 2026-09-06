package betamoon.instrumentation.registry;

import betamoon.instrumentation.api.HookDefinition;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.MappingResolver;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mutable-at-startup registry that freezes into immutable per-class plans. */
public final class HookRegistry implements HookRegistrar {
    private final Map<String, HookDefinition> definitions = new LinkedHashMap<String, HookDefinition>();
    private final Set<String> moduleIds = new LinkedHashSet<String>();
    private final TransformationReport report;
    private boolean frozen;

    public HookRegistry(TransformationReport report) {
        if (report == null) {
            throw new IllegalArgumentException("Transformation report is required");
        }
        this.report = report;
    }

    public void registerModule(HookModule module) {
        ensureOpen();
        if (module == null) {
            throw new IllegalArgumentException("Hook module is required");
        }
        String moduleId = module.getId();
        if (moduleId == null || moduleId.trim().length() == 0) {
            throw new IllegalArgumentException("Hook module id is required");
        }
        if (!moduleIds.add(moduleId)) {
            throw new IllegalArgumentException("Duplicate hook module id: " + moduleId);
        }
        module.register(this);
    }

    public void register(HookDefinition definition) {
        ensureOpen();
        if (definition == null) {
            throw new IllegalArgumentException("Hook definition is required");
        }
        String hookId = definition.getId();
        if (hookId == null || hookId.trim().length() == 0) {
            throw new IllegalArgumentException("Hook id is required");
        }
        if (definition.getTarget() == null) {
            throw new IllegalArgumentException("Target is required for hook " + hookId);
        }
        if (definition.getMatchRequirement() == null) {
            throw new IllegalArgumentException("Match requirement is required for hook " + hookId);
        }
        if (definitions.containsKey(hookId)) {
            throw new IllegalArgumentException("Duplicate hook id: " + hookId);
        }
        definitions.put(hookId, definition);
        report.registered(hookId);
    }

    public Map<String, ClassTransformPlan> freeze(MappingResolver mappings, RuntimeNamespace... namespaces) {
        ensureOpen();
        if (mappings == null) {
            throw new IllegalArgumentException("Mapping resolver is required");
        }
        if (namespaces == null || namespaces.length == 0) {
            throw new IllegalArgumentException("At least one runtime namespace is required");
        }
        for (RuntimeNamespace namespace : namespaces) {
            if (namespace == null) {
                throw new IllegalArgumentException("Runtime namespaces cannot contain null");
            }
        }
        frozen = true;

        Map<String, Map<String, PlannedHook>> mutablePlans =
            new LinkedHashMap<String, Map<String, PlannedHook>>();
        for (HookDefinition definition : definitions.values()) {
            for (RuntimeNamespace namespace : namespaces) {
                String className = mappings.resolveClass(definition.getTarget().getOwner(), namespace);
                Map<String, PlannedHook> hooksById = mutablePlans.get(className);
                if (hooksById == null) {
                    hooksById = new LinkedHashMap<String, PlannedHook>();
                    mutablePlans.put(className, hooksById);
                }
                PlannedHook planned = hooksById.get(definition.getId());
                if (planned == null) {
                    planned = new PlannedHook(definition);
                    hooksById.put(definition.getId(), planned);
                }
                planned.addNamespace(namespace);
                report.waiting(definition.getId(), definition.getTarget().toString());
            }
        }

        Map<String, ClassTransformPlan> frozenPlans = new LinkedHashMap<String, ClassTransformPlan>();
        for (Map.Entry<String, Map<String, PlannedHook>> entry : mutablePlans.entrySet()) {
            List<PlannedHook> hooks = new ArrayList<PlannedHook>(entry.getValue().values());
            frozenPlans.put(entry.getKey(), new ClassTransformPlan(entry.getKey(), hooks));
        }
        return Collections.unmodifiableMap(frozenPlans);
    }

    private void ensureOpen() {
        if (frozen) {
            throw new IllegalStateException("Hook registry is already frozen");
        }
    }
}
