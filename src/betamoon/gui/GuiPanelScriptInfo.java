package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiTextureCache;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Script details with measured, consecutive sections and the same interactive entries as the popup. */
final class GuiPanelScriptInfo extends GuiContainer {
    private static final int CONTENT_PADDING = 6;
    private static final int SECTION_GAP = 16;
    private static final int HEADER_HEIGHT = 18;
    private static final int DEPENDENCY_LINE_HEIGHT = 12;
    private static final int IMAGE_SIZE = 72;
    private static final int IMAGE_GAP = 12;
    private static final float SECTION_SCALE = 1.25F;
    private static final int WARNING_SIZE = GuiScriptRestartIndicator.DEFAULT_SIZE;
    private final ScriptInfoContent content;
    private final GuiScrollView scrollView;
    private final GuiScriptRestartIndicator warningIndicator = new GuiScriptRestartIndicator(null);
    private ScriptMod selected;
    private ScriptInfoLayout currentLayout;
    private long issueRevision = -1;
    private int headerY;
    private float headerScale = 1.0F;

    GuiPanelScriptInfo() {
        this(new GuiScriptIssueActions());
    }

    GuiPanelScriptInfo(GuiScriptIssueActions actions) {
        content = new ScriptInfoContent(actions);
        scrollView = add(new GuiScrollView(content, GuiScrollView.Policy.VERTICAL));
    }

    void setSelected(ScriptMod selected) {
        long revision = LuaScriptErrors.getRevision();
        if (this.selected == selected && issueRevision == revision) {
            return;
        }
        if (this.selected != selected) {
            scrollView.resetScroll();
        }
        this.selected = selected;
        issueRevision = revision;
        warningIndicator.setSourceFileName(selected == null ? null : selected.getSourceFileName());
        currentLayout = null;
        requestLayout();
    }

    void setHeaderY(int headerY) {
        this.headerY = headerY;
    }

    void setHeaderScale(float headerScale) {
        this.headerScale = headerScale;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        scrollView.setVisible(selected != null);
        if (selected == null) {
            return;
        }
        currentLayout = new ScriptInfoLayout(context, getWidth());
        scrollView.setContentSize(getWidth(), currentLayout.contentHeight);
        scrollView.arrange(context, getBounds());
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        if (selected == null) {
            return;
        }
        String title = selected.getDisplayName() + "  v" + selected.getVersion();
        int titleX = getLeft();
        if (warningIndicator.shouldRender()) {
            int iconY = headerY + ((int) (8 * headerScale) - WARNING_SIZE) / 2;
            warningIndicator.arrange(context, Rect.fromPositionAndSize(getLeft(), iconY, WARNING_SIZE, WARNING_SIZE));
            warningIndicator.render(context);
            titleX += WARNING_SIZE + 4;
        }
        context.getRenderer().drawScaledText(title, titleX, headerY, context.getRenderer().getTheme().textPrimary,
                headerScale);
    }

    private final class ScriptInfoContent extends GuiContainer {
        private final GuiScriptIssueEntries errors;
        private final GuiScriptIssueEntries warnings;

        private ScriptInfoContent(GuiScriptIssueActions actions) {
            errors = add(new GuiScriptIssueEntries(actions));
            warnings = add(new GuiScriptIssueEntries(actions));
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            if (currentLayout == null) {
                return;
            }
            ScriptInfoLayout layout = currentLayout;
            errors.setVisible(layout.errorY >= 0);
            warnings.setVisible(layout.warningY >= 0);
            if (layout.errorY >= 0) {
                errors.arrange(context, Rect.fromPositionAndSize(getLeft(), getTop() + layout.errorY,
                        getWidth(), layout.errorHeight));
            }
            if (layout.warningY >= 0) {
                warnings.arrange(context, Rect.fromPositionAndSize(getLeft(), getTop() + layout.warningY,
                        getWidth(), layout.warningHeight));
            }
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            if (selected == null || currentLayout == null) {
                return;
            }
            ScriptInfoLayout layout = currentLayout;
            drawImage(context, layout.image, getLeft(), getTop(), IMAGE_SIZE);
            heading(context, "Description", layout.descriptionY, context.getRenderer().getTheme().textPrimary);
            if (hasText(layout.description)) {
                context.getRenderer().drawWrappedText(layout.description, getLeft(),
                        getTop() + layout.descriptionY + HEADER_HEIGHT, getWidth(),
                        context.getRenderer().getTheme().textPrimary);
            }
            if (layout.separatorY >= 0) {
                context.getRenderer().drawHorizontalLine(getLeft(), getRight(), getTop() + layout.separatorY,
                        context.getRenderer().getTheme().listSeparator);
            }
            if (layout.errorY >= 0) {
                heading(context, "Errors", layout.errorY - HEADER_HEIGHT, context.getRenderer().getTheme().textError);
            }
            if (layout.warningY >= 0) {
                heading(context, "Warnings", layout.warningY - HEADER_HEIGHT,
                        context.getRenderer().getTheme().textWarning);
            }
            if (layout.dependencyY >= 0) {
                heading(context, "Dependencies", layout.dependencyY, context.getRenderer().getTheme().textPrimary);
                drawDependencies(context, layout.dependencies, selected.getMissingDependencies(), getLeft(),
                        getTop() + layout.dependencyY + HEADER_HEIGHT, getWidth());
            }
        }

        private void heading(GuiContext context, String text, int y, int color) {
            context.getRenderer().drawScaledUnderlinedText(text, getLeft(), getTop() + y, color, SECTION_SCALE);
        }
    }

    private final class ScriptInfoLayout {
        private final String description;
        private final List<String> dependencies;
        private final GuiTextureCache.Texture image;
        private final int descriptionY = IMAGE_SIZE + IMAGE_GAP;
        private final int separatorY;
        private final int errorY;
        private final int errorHeight;
        private final int warningY;
        private final int warningHeight;
        private final int dependencyY;
        private final int contentHeight;

        private ScriptInfoLayout(GuiContext context, int width) {
            description = selected.getDescription();
            dependencies = selected.getDependencies() == null ? Collections.emptyList() : selected.getDependencies();
            List<ScriptIssue> issues = LuaScriptErrors.getIssuesFor(selected.getDisplayName(), selected.getSourceFileName());
            List<ScriptIssue> errors = filterIssues(issues, false);
            List<ScriptIssue> warnings = filterIssues(issues, true);
            if (errors.isEmpty() && selected.isFailed() && hasText(selected.getFailureReason())) {
                errors.add(ScriptIssue.describe(selected.getSourceFileName(), selected.getFailureReason(), false));
            }
            content.errors.setIssues(errors);
            content.warnings.setIssues(warnings);
            content.errors.setVisible(!errors.isEmpty());
            content.warnings.setVisible(!warnings.isEmpty());
            errorHeight = content.errors.measure(context, Constraints.upTo(width, Integer.MAX_VALUE)).getHeight();
            warningHeight = content.warnings.measure(context, Constraints.upTo(width, Integer.MAX_VALUE)).getHeight();
            image = resolveImageTexture(selected);
            int y = descriptionY + HEADER_HEIGHT;
            if (hasText(description)) {
                y += context.getRenderer().wrappedTextHeight(description, width) + CONTENT_PADDING;
            }
            y += SECTION_GAP;
            boolean hasIssues = !errors.isEmpty() || !warnings.isEmpty();
            separatorY = hasIssues ? y + 2 : -1;
            if (hasIssues) {
                y += SECTION_GAP;
            }
            errorY = errors.isEmpty() ? -1 : y + HEADER_HEIGHT;
            if (!errors.isEmpty()) {
                y = errorY + errorHeight + SECTION_GAP;
            }
            warningY = warnings.isEmpty() ? -1 : y + HEADER_HEIGHT;
            if (!warnings.isEmpty()) {
                y = warningY + warningHeight + SECTION_GAP;
            }
            dependencyY = dependencies.isEmpty() ? -1 : y;
            if (!dependencies.isEmpty()) {
                y += HEADER_HEIGHT + dependencies.size() * DEPENDENCY_LINE_HEIGHT + CONTENT_PADDING;
            }
            contentHeight = y;
        }
    }

    private static List<ScriptIssue> filterIssues(List<ScriptIssue> issues, boolean warning) {
        List<ScriptIssue> filtered = new ArrayList<>();
        for (ScriptIssue issue : issues) {
            if (issue.isWarning() == warning) {
                filtered.add(issue);
            }
        }
        return filtered;
    }

    private static void drawImage(GuiContext context, GuiTextureCache.Texture image, int x, int y, int size) {
        Rect bounds = Rect.fromPositionAndSize(x, y, size, size);
        if (image != null) {
            context.getRenderer().drawTexture(image, bounds);
            return;
        }
        context.getRenderer().drawPopupPanel(bounds);
        context.getRenderer().drawScaledCenteredText("?", bounds, context.getRenderer().getTheme().textMuted, 1.0F, true);
    }

    private static GuiTextureCache.Texture resolveImageTexture(ScriptMod mod) {
        if (!hasText(mod.getImagePath()) || LuaModLoader.getLuaModsDir() == null) {
            return null;
        }
        try {
            byte[] archived = mod.readArchivedImage(8 * 1024 * 1024);
            if (archived != null) {
                GuiTextureCache.Texture texture = GuiTextureCache.shared().get(mod.getImageCacheKey(), archived,
                        mod.getStorageRevision());
                return texture != null && texture.getWidth() == texture.getHeight() ? texture : null;
            }
        } catch (IOException ignored) {
            return null;
        }
        File file = mod.resolveImageFile(LuaModLoader.getLuaModsDir());
        if (file == null) {
            return null;
        }
        GuiTextureCache.Texture texture = GuiTextureCache.shared().get(file);
        return texture != null && texture.getWidth() == texture.getHeight() ? texture : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void drawDependencies(GuiContext context, List<String> dependencies, List<String> missing,
            int x, int y, int width) {
        for (String dependency : dependencies) {
            if (dependency == null) {
                continue;
            }
            boolean unavailable = missing != null ? missing.contains(dependency)
                    : !LuaScriptRegistry.hasScriptName(dependency);
            int color = unavailable ? context.getRenderer().getTheme().textError
                    : context.getRenderer().getTheme().textPrimary;
            String label = "- " + dependency + (unavailable ? " (Missing)" : "");
            context.getRenderer().drawText(context.getRenderer().trimToWidth(label, width), x, y, color);
            y += DEPENDENCY_LINE_HEIGHT;
        }
    }
}
