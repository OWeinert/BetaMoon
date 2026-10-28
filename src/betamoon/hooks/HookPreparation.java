package betamoon.hooks;

import betamoon.instrumentation.agent.AgentLoadedTypes;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.mapping.TinyMappingSnapshot;
import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.diagnostics.NativeDiagnosticEvidence;
import betamoon.interop.diagnostics.NativeDiagnosticGuidance;
import betamoon.luamodloader.HookPackages;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import org.luaj.vm2.Globals;

/**
 * Frozen startup owner authorization and independently diagnosed user
 * transformation plans.
 */
public final class HookPreparation {
    private static final AtomicLong ATTEMPTS = new AtomicLong();
    private static volatile HookPreparation current;
    private static volatile String unavailableReason;
    private static volatile String unavailableMessage;
    private final File instance;
    private final TinyMappingResolver mappings;
    private final TinyMappingSnapshot mappingSnapshot;
    private final Map<String, Set<String>> allowed = new HashMap<>();
    private final Map<String, List<Site>> classes = new HashMap<>();
    private final Map<String, Site> sites = new LinkedHashMap<>();
    private final Map<String, List<HookDefinition>> definitions = new TreeMap<>();
    private final Map<String, List<String>> definitionTargets = new HashMap<>();
    private final List<Map<String, Object>> diagnostics = new ArrayList<>();
    private final AtomicLong revision = new AtomicLong();
    private final String mappingResourceHash;
    private boolean remote;
    private long siteRevision = Long.MIN_VALUE;

    private HookPreparation(File instance, TinyMappingResolver mappings) {
        this.instance = instance;
        this.mappings = mappings;
        this.mappingSnapshot = mappings.snapshot();
        try (InputStream input = HookPreparation.class
                .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            mappingResourceHash = input == null
                    ? "unavailable"
                    : CatalogFingerprint.sha256(CatalogValues.bytes(input, 4 * 1024 * 1024));
        } catch (IOException error) {
            throw new HookFailure("agent_unavailable", "cannot verify hook mapping resource", error);
        }
    }

    public static HookPreparation prepare(TinyMappingResolver mappings) throws IOException {
        String override = System.getProperty("betamoon.instanceDir");
        File instance = new File(override == null ? System.getProperty("user.dir") : override);
        if (!instance.isAbsolute()) {
            throw new HookFailure("hook_file_invalid", "betamoon.instanceDir must be absolute");
        }
        HookPreparation result = new HookPreparation(instance.getCanonicalFile(), mappings);
        List<String> failures = new ArrayList<>();
        List<HookPackages.Snapshot> packages = HookPackages.discover(HookPackages.scriptsDirectory(instance), failures);
        int declarationCount = 0;
        for (String failure : failures) {
            result.diagnose("", "", "hook_file_invalid", failure);
        }
        for (HookPackages.Snapshot snapshot : packages) {
            try {
                List<HookFileCollectionContext.Registration> registrations = result.evaluate(snapshot, null);
                if (declarationCount + registrations.size() > 512) {
                    throw new HookFailure("hook_file_invalid", "session exceeds 512 hook definitions");
                }
                Set<String> selectors = new HashSet<>();
                Map<String, HookTarget> targets = new LinkedHashMap<>();
                Map<String, List<HookPoint.Member>> pointAlternatives = new HashMap<>();
                for (HookFileCollectionContext.Registration registration : registrations) {
                    HookPoint point = registration.definition.point;
                    if (point != null && !pointAlternatives.containsKey(point.key())) {
                        pointAlternatives.put(point.key(), point.resolve(mappings, result.mappingSnapshot));
                    }
                    for (HookTarget target : result.resolve(registration.definition)) {
                        selectors.add(target.key());
                        targets.put(target.key(), target);
                    }
                }
                int additions = 0;
                for (String key : targets.keySet()) {
                    additions += result.sites.containsKey(key) ? 0 : 1;
                }
                if (result.sites.size() + additions > 1024) {
                    throw new HookFailure("hook_file_invalid", "session exceeds 512 dual-namespace method sites");
                }
                result.allowed.put(snapshot.owner, Collections.unmodifiableSet(selectors));
                declarationCount += registrations.size();
                List<HookDefinition> declared = new ArrayList<>();
                for (HookFileCollectionContext.Registration registration : registrations) {
                    declared.add(registration.definition);
                }
                result.definitions.put(snapshot.owner, Collections.unmodifiableList(declared));
                for (HookTarget target : targets.values()) {
                    Site site = result.sites.get(target.key());
                    if (site == null) {
                        site = new Site(result.sites.size(), target);
                        result.sites.put(target.key(), site);
                        result.classes.computeIfAbsent(target.owner, ignored -> new ArrayList<>()).add(site);
                    }
                    site.owners.add(snapshot.owner);
                }
                for (HookFileCollectionContext.Registration registration : registrations) {
                    List<HookTarget> choices = result.resolve(registration.definition);
                    Set<String> alternatives = new HashSet<>();
                    for (HookTarget choice : choices) {
                        alternatives.add(choice.key());
                    }
                    Set<String> frozen = Collections.unmodifiableSet(alternatives);
                    List<String> targetNames = new ArrayList<>(alternatives);
                    Collections.sort(targetNames);
                    result.definitionTargets.put(registration.definition.identity(),
                            Collections.unmodifiableList(targetNames));
                    for (HookTarget choice : choices) {
                        result.sites.get(choice.key()).alternatives.add(frozen);
                        HookPoint point = registration.definition.point;
                        if (point != null) {
                            Site site = result.sites.get(choice.key());
                            HookPointSite selected = site.points.get(point.key());
                            if (selected == null) {
                                selected = new HookPointSite(point, pointAlternatives.get(point.key()));
                                site.points.put(point.key(), selected);
                            }
                            selected.owners.add(snapshot.owner);
                        }
                        if (registration.definition.at.equals("around")) {
                            result.sites.get(choice.key()).around = true;
                        }
                    }
                    for (RuntimeNamespace namespace : new RuntimeNamespace[]{RuntimeNamespace.NAMED,
                            RuntimeNamespace.CLIENT}) {
                        for (HookTarget choice : HookTarget.resolve(registration.definition.target, mappings,
                                result.mappingSnapshot, namespace)) {
                            result.sites.get(choice.key()).namespaces.add(namespace.getMappingName());
                        }
                    }
                }
            } catch (RuntimeException error) {
                result.diagnose(snapshot.owner, "", reason(error), NativeDiagnosticEvidence.message(error));
            }
        }
        AgentLoadedTypes.visit(type -> {
            List<Site> loaded = result.classes.get(type.getName().replace('.', '/'));
            if (loaded != null) {
                for (Site site : loaded) {
                    site.status = "target_loaded_too_early";
                }
            }
        });
        current = result;
        HookDispatcher.prepare(new ArrayList<>(result.sites.values()));
        return result;
    }

    public static HookPreparation current() {
        if (current == null && unavailableReason == null && HookAgentBridge.remote() != null) {
            synchronized (HookPreparation.class) {
                if (current == null && unavailableReason == null) {
                    try {
                        Map<?, ?> plan = (Map<?, ?>) HookAgentBridge.remote().plan.invokeExact();
                        if (Boolean.TRUE.equals(plan.get("available"))) {
                            current = importPlan(plan);
                        }
                    } catch (VirtualMachineError | ThreadDeath fatal) {
                        throw fatal;
                    } catch (Throwable error) {
                        unavailableReason = "agent_unavailable";
                        unavailableMessage = "Cannot import the agent hook plan: "
                                + NativeDiagnosticEvidence.message(error);
                    }
                }
            }
        }
        return current;
    }

    public static List<Map<String, Object>> unavailableRecords() {
        String reason = unavailableReason == null ? HookAgentBridge.unavailableReason() : unavailableReason;
        String message = unavailableReason == null ? HookAgentBridge.unavailableMessage() : unavailableMessage;
        if (reason == null) {
            return Collections.emptyList();
        }
        String bounded = message == null ? "" : NativeDiagnosticEvidence.bounded(message, 2048);
        return Collections.singletonList(CatalogValues.record("id", "hook-agent-unavailable", "name", "agent", "kind",
                "HOOK_DIAGNOSTIC", "owner", "", "status", reason, "reason", reason, "message", bounded, "guidance",
                NativeDiagnosticGuidance.forReason(reason)));
    }

    private static HookPreparation importPlan(Map<?, ?> plan) throws IOException {
        HookPreparation result;
        try (InputStream input = HookPreparation.class
                .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            result = new HookPreparation(new File((String) plan.get("instance")).getCanonicalFile(),
                    TinyMappingResolver.readStrict(input));
        }
        if (!result.mappingResourceHash.equals(plan.get("mappingResourceHash"))) {
            throw new HookFailure("agent_unavailable", "the agent and game have different native mappings");
        }
        Map<?, ?> owners = (Map<?, ?>) plan.get("owners");
        for (Map.Entry<?, ?> owner : owners.entrySet()) {
            result.allowed.put((String) owner.getKey(),
                    Collections.unmodifiableSet(new HashSet<>(CatalogValues.strings(owner.getValue()))));
        }
        for (Object value : (List<?>) plan.get("sites")) {
            Map<String, Object> row = CatalogValues.object(value);
            HookTarget target = new HookTarget(CatalogValues.text(row, "owner"), CatalogValues.text(row, "method"),
                    CatalogValues.text(row, "descriptor"));
            Site site = new Site(CatalogValues.integer(row, "id"), target);
            site.owners.addAll(CatalogValues.strings(row.get("owners")));
            site.around = Boolean.TRUE.equals(row.get("around"));
            site.importOperations(row);
            if (row.get("namespaces") instanceof List) {
                site.namespaces.addAll(CatalogValues.strings(row.get("namespaces")));
            }
            result.sites.put(target.key(), site);
            result.classes.computeIfAbsent(target.owner, ignored -> new ArrayList<>()).add(site);
        }
        result.remote = true;
        result.refreshSites();
        return result;
    }

    public synchronized void refreshSites() {
        if (!remote) {
            return;
        }
        try {
            long observed = (long) HookAgentBridge.remote().revision.invokeExact();
            if (siteRevision == observed) {
                return;
            }
            List<?> rows = (List<?>) HookAgentBridge.remote().states.invokeExact();
            for (Object value : rows) {
                Map<String, Object> row = CatalogValues.object(value);
                Site site = sites.get(CatalogValues.text(row, "target"));
                if (site != null) {
                    site.status = CatalogValues.text(row, "status");
                    site.normalReturns = CatalogValues.integer(row, "normalReturns");
                    site.fingerprint = (String) row.get("fingerprint");
                    site.inputHash = (String) row.get("inputHash");
                    site.outputHash = (String) row.get("outputHash");
                    site.methodMatches = row.get("methodMatches") instanceof Number
                            ? CatalogValues.integer(row, "methodMatches")
                            : -1;
                    site.structureAnalyzed = Boolean.TRUE.equals(row.get("structureAnalyzed"));
                    site.around = Boolean.TRUE.equals(row.get("around"));
                    site.helperName = (String) row.get("helperName");
                    site.importOperations(row);
                }
            }
            siteRevision = observed;
        } catch (Throwable error) {
            throw HookAgentBridge.failure(error);
        }
    }

    public static Map<String, Object> planTransport() {
        HookPreparation preparation = current;
        if (preparation == null) {
            return CatalogValues.record("available", false);
        }
        Map<String, Object> owners = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : preparation.allowed.entrySet()) {
            owners.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        List<Map<String, Object>> sites = new ArrayList<>();
        for (Site site : preparation.sites.values()) {
            sites.add(CatalogValues.record("id", site.id, "owner", site.target.owner, "method", site.target.method,
                    "descriptor", site.target.descriptor, "owners", new ArrayList<>(site.owners), "namespaces",
                    new ArrayList<>(site.namespaces), "around", site.around, "points", site.pointsTransport(),
                    "operations", site.operationsTransport()));
        }
        return CatalogValues.object(
                CatalogValues.freeze(CatalogValues.record("available", true, "instance", preparation.instance.getPath(),
                        "mappingResourceHash", preparation.mappingResourceHash, "owners", owners, "sites", sites)));
    }

    public static List<Map<String, Object>> statesTransport() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (current != null) {
            for (Site site : current.sites.values()) {
                result.add(CatalogValues.record("target", site.target.key(), "status", site.status, "normalReturns",
                        site.normalReturns, "fingerprint", site.fingerprint, "inputHash", site.inputHash, "outputHash",
                        site.outputHash, "methodMatches", site.methodMatches, "structureAnalyzed",
                        site.structureAnalyzed, "around", site.around, "helperName", site.helperName, "points",
                        site.pointsTransport(), "operations", site.operationsTransport()));
            }
        }
        return result;
    }

    public static List<Map<String, Object>> statusTransport() {
        return current == null ? Collections.emptyList() : current.status();
    }

    public static long revisionTransport() {
        return current == null ? 0 : current.revision();
    }

    public static void diagnoseTransport(String owner, String hook, String reason, String message) {
        if (current != null) {
            current.diagnose(owner, hook, reason, message);
        }
    }

    public List<Site> sites(String className) {
        List<Site> result = classes.get(className);
        return result == null ? Collections.emptyList() : result;
    }

    public List<HookFileCollectionContext.Registration> preflight(HookPackages.Snapshot snapshot) throws IOException {
        File actualScripts = snapshot.scripts.getCanonicalFile();
        File actualInstance = actualScripts.getName().equals("scripts")
                && actualScripts.getParentFile().getName().equals("betamoon")
                        ? actualScripts.getParentFile().getParentFile()
                        : actualScripts.getParentFile();
        if (!actualInstance.equals(instance)) {
            throw new HookFailure("declaration_mismatch", "instance root differs from agent preparation; launch with "
                    + "-Dbetamoon.instanceDir=" + actualInstance);
        }
        List<HookFileCollectionContext.Registration> result = evaluate(snapshot, null);
        Set<String> prepared = allowed.get(snapshot.owner);
        if (prepared == null && !result.isEmpty()) {
            throw new HookFailure("restart_required", "owner was not prepared at startup: " + snapshot.owner);
        }
        for (HookFileCollectionContext.Registration registration : result) {
            for (HookTarget target : resolve(registration.definition)) {
                Site site = sites.get(target.key());
                if (!prepared.contains(target.key())
                        || registration.definition.at.equals("around") && (site == null || !site.around)
                        || registration.definition.point != null
                                && (site == null || !site.points.containsKey(registration.definition.point.key()))) {
                    throw new HookFailure("restart_required",
                            "unprepared target for " + snapshot.owner + ": " + target.key());
                }
            }
        }
        return result;
    }

    public List<HookFileCollectionContext.Registration> runtime(HookPackages.Snapshot snapshot, Globals globals,
            List<HookFileCollectionContext.Registration> expected) {
        List<HookFileCollectionContext.Registration> actual = evaluate(snapshot, globals);
        if (!metadata(actual).equals(metadata(expected))) {
            throw new HookFailure("declaration_mismatch",
                    snapshot.owner + ": normalized declarations differ between phases");
        }
        return actual;
    }

    public List<Site> targets(HookDefinition definition) {
        refreshSites();
        List<Site> result = new ArrayList<>();
        for (HookTarget target : resolve(definition)) {
            Site site = sites.get(target.key());
            if (site != null) {
                result.add(site);
            }
        }
        return result;
    }

    public List<Site> targets(HookDefinition definition, String namespace) {
        refreshSites();
        if (!namespace.equals("named") && !namespace.equals("client")) {
            throw new HookFailure("agent_unavailable", "the current client namespace is not verified");
        }
        List<Site> result = new ArrayList<>();
        RuntimeNamespace selected = namespace.equals("named") ? RuntimeNamespace.NAMED : RuntimeNamespace.CLIENT;
        for (HookTarget target : HookTarget.resolve(definition.target, mappings, mappingSnapshot, selected)) {
            Site site = sites.get(target.key());
            if (site != null) {
                result.add(site);
            }
        }
        return result;
    }

    private List<HookTarget> resolve(HookDefinition definition) {
        return HookTarget.resolve(definition.target, mappings, mappingSnapshot);
    }

    private Map<String, String> metadata(List<HookFileCollectionContext.Registration> registrations) {
        Map<String, String> result = new TreeMap<>();
        for (HookFileCollectionContext.Registration registration : registrations) {
            HookDefinition definition = registration.definition;
            List<String> targets = new ArrayList<>();
            for (HookTarget target : resolve(definition)) {
                targets.add(target.key());
            }
            result.put(definition.key(),
                    definition.file + ":" + targets + ":" + definition.at + ":" + definition.edit + ":"
                            + definition.priority + ":" + definition.required + ":" + definition.nativeErrors + ":"
                            + definition.reentry);
        }
        return result;
    }

    public static List<HookFileCollectionContext.Registration> evaluate(HookPackages.Snapshot snapshot,
            Globals runtime) {
        long attempt = ATTEMPTS.incrementAndGet();
        HookDeclarations.Budget budget = new HookDeclarations.Budget();
        List<HookFileCollectionContext.Registration> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, String> file : snapshot.files.entrySet()) {
            List<HookFileCollectionContext.Registration> collected = HookDeclarations.evaluate(snapshot.owner,
                    snapshot.owner + "/" + file.getKey(), file.getValue(), attempt, runtime, budget);
            for (HookFileCollectionContext.Registration registration : collected) {
                if (!ids.add(registration.definition.id) || result.size() >= 64) {
                    throw new HookFailure("hook_file_invalid",
                            "duplicate id or more than 64 hooks/package: " + snapshot.owner);
                }
                result.add(registration);
            }
        }
        return result;
    }

    public synchronized void diagnose(String owner, String hook, String reason, String message) {
        if (remote) {
            try {
                HookAgentBridge.remote().diagnostics.invokeExact(owner, hook, reason, message);
                return;
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        String bounded = message == null ? "" : NativeDiagnosticEvidence.bounded(message, 2048);
        if (diagnostics.size() == 128) {
            diagnostics.remove(0);
        }
        Site site = sites.get(hook);
        List<String> owners = site == null ? Collections.singletonList(owner) : new ArrayList<>(site.owners);
        Map<String, Object> diagnostic = CatalogValues.record("id", "diagnostic-" + revision.incrementAndGet(), "name",
                hook, "kind", "HOOK_DIAGNOSTIC", "owner", owner, "owners", owners, "hook", hook, "status", reason,
                "reason", reason, "message", bounded, "guidance", NativeDiagnosticGuidance.forReason(reason));
        if (site != null) {
            diagnostic.put("target", site.target.key());
        }
        diagnostics.add(diagnostic);
        System.err.println("[BetaMoon Hooks] " + owner + "/" + hook + ": " + reason + ": " + bounded);
    }

    public synchronized List<Map<String, Object>> status() {
        if (remote) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = (List<Map<String, Object>>) HookAgentBridge.remote().status
                        .invokeExact();
                return result;
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(diagnostics);
        for (Site site : sites.values()) {
            Map<String, Object> record = CatalogValues.record("id", "script-site-" + site.id, "name", site.target.key(),
                    "kind", "HOOK_SITE", "declaringType", site.target.owner.replace('/', '.'), "descriptor",
                    site.target.descriptor, "executableKind", site.target.constructor() ? "constructor" : "method",
                    "completion", site.target.constructor() ? "declaring_constructor_return" : null, "target",
                    site.target.key(), "status", site.status, "owners", new ArrayList<>(site.owners), "envelopeVersion",
                    site.envelopeVersion(), "normalReturns", site.normalReturns, "structuralFingerprint",
                    site.fingerprint, "observedInputSha256", site.inputHash, "outputSha256", site.outputHash,
                    "runtimeOwner", site.target.owner.replace('/', '.'), "runtimeMember", site.target.method,
                    "runtimeDescriptor", site.target.descriptor, "namespaceCandidates",
                    new ArrayList<>(site.namespaces), "installedEnvelopeVersion",
                    site.status.equals("applied") ? site.envelopeVersion() : null, "matchCounts",
                    CatalogValues.record("methods", site.methodMatches < 0 ? null : site.methodMatches, "normalReturns",
                            site.structureAnalyzed ? site.normalReturns : null),
                    "provenance",
                    CatalogValues.record("boundary", "engine_output_before_user_hooks", "observed",
                            site.inputHash != null, "structureAnalyzed", site.structureAnalyzed,
                            "laterTransformersObserved", false),
                    "generatedBody", site.helperName, "points", site.pointsTransport(), "operations",
                    site.operationsTransport(), "installedCapabilities", site.capabilities(), "guidance",
                    NativeDiagnosticGuidance.forStatus(site.status));
            result.add(record);
        }
        for (List<HookDefinition> owner : definitions.values()) {
            for (HookDefinition definition : owner) {
                result.add(CatalogValues.record("id", "prepared-" + definition.identity(), "name", definition.id,
                        "kind", "HOOK_DECLARATION", "owner", definition.owner, "source", definition.file, "at",
                        definition.at, "point", definition.point == null ? null : definition.point.transport(),
                        "nativeErrors", definition.nativeErrors, "reentry", definition.reentry, "effects",
                        definition.edit ? "edit" : "observe", "priority", definition.priority, "required",
                        definition.required, "status", "prepared", "sourceSha256", definition.sourceSha256, "targets",
                        new ArrayList<>(allowedTargets(definition))));
            }
        }
        for (Map<String, Object> subscription : HookDispatcher.status()) {
            Map<String, Object> row = new LinkedHashMap<>(subscription);
            row.put("guidance", NativeDiagnosticGuidance.forStatus((String) row.get("status")));
            result.add(row);
        }
        return result;
    }

    private List<String> allowedTargets(HookDefinition definition) {
        return definitionTargets.getOrDefault(definition.identity(), Collections.emptyList());
    }

    public long revision() {
        if (remote) {
            try {
                return (long) HookAgentBridge.remote().revision.invokeExact();
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        return revision.get();
    }

    public void changed() {
        revision.incrementAndGet();
    }

    private static String reason(RuntimeException error) {
        return error instanceof HookFailure ? ((HookFailure) error).reason : "declaration_failed";
    }

    public static final class Site {
        public final int id;
        public final HookTarget target;
        public final Set<String> owners = new TreeSet<>();
        public final Set<String> namespaces = new TreeSet<>();
        public final List<Set<String>> alternatives = new ArrayList<>();
        public volatile String status = "target_pending_load";
        public volatile int normalReturns;
        public volatile int methodMatches = -1;
        public volatile boolean structureAnalyzed;
        public volatile boolean around;
        public volatile String helperName;
        public final Map<String, HookPointSite> points = new TreeMap<>();
        public volatile List<HookOperationSite> operations = Collections.emptyList();
        public volatile String fingerprint;
        public volatile String inputHash;
        public volatile String outputHash;

        Site(int id, HookTarget target) {
            this.id = id;
            this.target = target;
            fingerprint = CatalogFingerprint.sha256(("envelope:2:" + target.key()).getBytes(StandardCharsets.UTF_8));
        }

        public int envelopeVersion() {
            return target.constructor() ? 5 : points.isEmpty() ? around ? 3 : 2 : 4;
        }

        public String operationFingerprint() {
            return CatalogFingerprint
                    .sha256(CatalogJson.encode(operationsTransport()).getBytes(StandardCharsets.UTF_8));
        }

        private List<Map<String, Object>> pointsTransport() {
            List<Map<String, Object>> result = new ArrayList<>();
            for (HookPointSite point : points.values()) {
                result.add(point.transport());
            }
            return result;
        }

        private List<Map<String, Object>> operationsTransport() {
            List<Map<String, Object>> result = new ArrayList<>();
            for (HookOperationSite operation : operations) {
                result.add(operation.transport());
            }
            return result;
        }

        private void importOperations(Map<String, Object> row) {
            if (row.get("points") instanceof List) {
                points.clear();
                for (Object item : (List<?>) row.get("points")) {
                    HookPointSite point = HookPointSite.transport(CatalogValues.object(item));
                    points.put(point.point.key(), point);
                }
            }
            List<HookOperationSite> imported = new ArrayList<>();
            if (row.get("operations") instanceof List) {
                for (Object item : (List<?>) row.get("operations")) {
                    imported.add(HookOperationSite.transport(CatalogValues.object(item)));
                }
            }
            operations = Collections.unmodifiableList(imported);
        }

        private List<String> capabilities() {
            if (!status.equals("applied")) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            if (target.constructor()) {
                result.add("constructed");
                return result;
            }
            if (!operations.isEmpty()) {
                result.add("operation_hooks");
            }
            if (around) {
                Collections.addAll(result, "around", "continuation", "native_error_recovery");
            }
            Collections.addAll(result, "before", "error", "argument_edit", "early_return");
            if (normalReturns > 0) {
                result.add("after");
                if (!target.descriptor.endsWith(")V")) {
                    result.add("result_edit");
                }
            }
            return result;
        }
    }
}
