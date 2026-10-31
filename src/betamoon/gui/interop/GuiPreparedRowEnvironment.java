package betamoon.gui.interop;

import betamoon.gui.action.GuiActionOrigin;
import betamoon.gui.collection.GuiRowFactory;
import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentRowFactory;
import betamoon.gui.drawing.GuiPaintBudget;
import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.graphics.GraphicsResources;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiPreparedTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiThemeDefinition;
import betamoon.gui.style.GuiThemeRegistry;
import betamoon.gui.style.GuiViewAppearance;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.client.ui.GuiTemplateSource;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Candidate-owned theme references, resource leases and shared paint accounting
 * for one realized row.
 */
public final class GuiPreparedRowEnvironment<T extends GuiPreparedRowEnvironment.Resources>
        implements
            GuiComponentRowFactory.Environment,
            GuiComponentPlan.Services {
    /**
     * Implementations retain their existing native/resource authority and disposal
     * checks.
     */
    public interface Resources extends GraphicsResources, AutoCloseable {
        GuiPaintCompiler.Resources painting();

        void bindContext(LuaCallbackScope scope);

        @Override
        void close();
    }

    private final GuiPresentationHost host;
    private final GuiComponentPlan.Services parent;
    private final GuiResourceOwners<T> resources;
    private final GuiPaintBudget paint;
    private final String defaultTheme;
    private final GuiThemeRegistry registry;
    private final String diagnosticPath;
    private final BooleanSupplier hostAlive;
    private final Map<String, GuiThemeDefinition> themes = new LinkedHashMap<>();
    private final Map<String, Map<String, GuiStyleSheet>> preparedThemes = new LinkedHashMap<>();
    private GuiResourceOwners<T>.Lease lease;
    private Set<String> owners = Collections.emptySet();
    private boolean preparing;
    private boolean prepared;
    private boolean closed;

    public GuiPreparedRowEnvironment(GuiPresentationHost host, GuiComponentPlan.Services parent,
            GuiResourceOwners<T> resources, GuiPaintBudget paint, String defaultTheme, GuiThemeRegistry registry,
            String diagnosticPath, BooleanSupplier hostAlive) {
        this.host = Objects.requireNonNull(host, "row presentation host");
        this.parent = Objects.requireNonNull(parent, "row host services");
        this.resources = Objects.requireNonNull(resources, "row resource owners");
        this.paint = Objects.requireNonNull(paint, "candidate paint scope");
        this.defaultTheme = Objects.requireNonNull(defaultTheme, "row default theme");
        this.registry = Objects.requireNonNull(registry, "row theme registry");
        this.diagnosticPath = Objects.requireNonNull(diagnosticPath, "row diagnostic path");
        this.hostAlive = Objects.requireNonNull(hostAlive, "row host lifetime");
    }

    public GuiPresentationHost host() {
        requireOpen();
        return host;
    }

    public GuiComponentPlan.Services services() {
        requireOpen();
        return this;
    }

    public void prepare(GuiComponentPlan plan) {
        requireOpen();
        if (preparing || prepared) {
            throw new IllegalStateException("Prepare one complete dependency group per row candidate");
        }
        preparing = true;
        try {
            for (GuiViewAppearance appearance : plan.appearances()) {
                String reference = reference(appearance);
                if (!preset(reference)) {
                    GuiThemeDefinition theme = registry.getRequired(reference);
                    themes.put(reference, theme);
                    plan.dependOnOwner(theme.owner);
                }
            }
            owners = plan.owners();
            lease = resources.acquire(owners);
            validate();
            prepared = true;
        } finally {
            preparing = false;
        }
    }

    public void validate() {
        requireOpen();
        if (lease == null) {
            throw new IllegalStateException("Row resource dependencies have not been prepared");
        }
        for (String owner : owners) {
            lease.resource(owner);
        }
        for (Map.Entry<String, GuiThemeDefinition> entry : themes.entrySet()) {
            entry.getValue().requireActive();
            if (registry.get(entry.getKey()) != entry.getValue()) {
                throw new IllegalStateException("Row theme changed during candidate preparation: " + entry.getKey());
            }
        }
    }

    public boolean needsRefresh() {
        requireOpen();
        for (Map.Entry<String, GuiThemeDefinition> entry : themes.entrySet()) {
            if (registry.get(entry.getKey()) != entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    public Object refreshStamp() {
        requireOpen();
        Map<String, GuiThemeDefinition> values = new LinkedHashMap<>();
        for (String reference : themes.keySet()) {
            values.put(reference, registry.get(reference));
        }
        return Collections.unmodifiableMap(values);
    }

    public List<GuiActionOrigin> actionOrigins() {
        requireOpen();
        return parent.actionOrigins();
    }

    public GuiPaintBudget paintBudget() {
        return paint;
    }

    public GuiRowBudget rowBudget() {
        return parent.rowBudget();
    }

    public GuiComponentRowFactory.Environment rowEnvironment(GuiRowFactory.Context context, String theme) {
        requireOpen();
        return new GuiPreparedRowEnvironment<>(host, this, resources, paint.scope(),
                theme == null ? defaultTheme : theme, registry, diagnosticPath + "/row/" + context.record.key,
                () -> !closed && hostAlive.getAsBoolean());
    }

    private T required(String owner) {
        requireOpen();
        if (lease == null) {
            throw new IllegalStateException("Row resource dependencies have not been prepared");
        }
        return lease.resource(owner);
    }

    public GuiPaintCompiler.Resources paintResources(String owner) {
        return required(owner).painting();
    }

    public GraphicsResources rawResources(String owner) {
        return required(owner);
    }

    private String reference(GuiViewAppearance appearance) {
        return appearance.theme == null ? defaultTheme : appearance.theme;
    }

    private static boolean preset(String reference) {
        return reference.equals("menu") || reference.equals("minimal") || reference.equals("container_legacy");
    }

    public GuiStyleSheet theme(String owner, GuiViewAppearance appearance) {
        requireOpen();
        String reference = reference(appearance);
        Map<String, GuiStyleSheet> cache = preparedThemes.computeIfAbsent(owner, ignored -> new LinkedHashMap<>());
        GuiStyleSheet base = cache.get(reference);
        if (base == null) {
            base = preset(reference)
                    ? GuiThemeDefinition.preset(reference)
                    : themes.get(reference).prepare(required(owner).painting());
            cache.put(reference, base);
        }
        return appearance.appearance.style.values.isEmpty()
                ? base
                : base.withInline(appearance.appearance.style.prepare(required(owner).painting()));
    }

    public GuiVisualTemplate template(String owner, GuiViewAppearance appearance) {
        requireOpen();
        GuiThemeDefinition theme = themes.get(reference(appearance));
        String name = appearance.appearance.template == null ? appearance.node.type : appearance.appearance.template;
        GuiThemeDefinition.Template template = theme == null ? null : theme.templates.get(name);
        if (template == null) {
            if (appearance.appearance.template != null) {
                throw new IllegalArgumentException("Unknown template in row theme: " + name);
            }
            return GuiDefaultTemplate.INSTANCE;
        }
        GuiTemplateSource source = new GuiTemplateSource(appearance.node.path, theme, template,
                appearance.node.properties, required(template.owner).painting(), paint,
                scope -> required(template.owner).bindContext(scope));
        return new GuiPreparedTemplate(template.parts, source,
                failure -> parent.failure(owner, diagnosticPath + "/" + appearance.node.path, failure));
    }

    public void paintAccepted(String path, GuiPaintProgram[] programs) {
        requireOpen();
        paint.accept(path, programs);
    }

    public void requireHost() {
        requireOpen();
        parent.requireHost();
    }

    public void failure(String owner, String path, Throwable failure) {
        parent.failure(owner, path, failure);
    }

    private void requireOpen() {
        if (closed || !hostAlive.getAsBoolean()) {
            throw new IllegalStateException("Prepared row environment has expired");
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        GuiResourceOwners<T>.Lease retiring = lease;
        lease = null;
        try {
            if (retiring != null) {
                retiring.close();
            }
        } finally {
            paint.close();
            owners = Collections.emptySet();
            themes.clear();
            preparedThemes.clear();
        }
    }
}
