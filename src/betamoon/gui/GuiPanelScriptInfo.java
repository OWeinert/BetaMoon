package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiTextureCache;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Retained script detail panel with cached measurement and owned image textures. */
final class GuiPanelScriptInfo extends GuiContainer {
    private static final String LABEL_DESCRIPTION = "Description";
    private static final String LABEL_ERRORS = "Errors";
    private static final String LABEL_DEPENDENCIES = "Dependencies";
    private static final String SUFFIX_DEPENDENCY_MISSING = "(Missing)";
    private static final int SECTION_PADDING = 64;
    private static final int CONTENT_PADDING = 6;
    private static final int LINE_SPACING = 16;
    private static final int DEPENDENCY_LINE_HEIGHT = 12;
    private static final int IMAGE_PADDING = 12;
    private static final int IMAGE_FIXED_SIZE = 72;
    private static final int WARNING_SIZE = GuiScriptRestartIndicator.DEFAULT_SIZE;
    private static final int WARNING_GAP = 4;

    private final ScriptInfoContent content = new ScriptInfoContent();
    private final GuiScrollView scrollView = new GuiScrollView(content, GuiScrollView.Policy.VERTICAL);
    private final GuiScriptRestartIndicator warningIndicator = new GuiScriptRestartIndicator(null);
    private ScriptMod selected;
    private ScriptInfoLayout currentLayout;
    private int headerY;
    private float headerScale = 1.0F;

    GuiPanelScriptInfo() {
        add(scrollView);
    }

    void setSelected(ScriptMod selected) {
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
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
        if (selected == null) {
            scrollView.setVisible(false);
            return;
        }
        scrollView.setVisible(true);
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
            warningIndicator.arrange(context,
                    Rect.fromPositionAndSize(getLeft(), iconY, WARNING_SIZE, WARNING_SIZE));
            warningIndicator.render(context);
            titleX += WARNING_SIZE + WARNING_GAP;
        }
        context.getRenderer().drawScaledText(title, titleX, headerY,
                context.getRenderer().getTheme().textPrimary, headerScale);
    }

    private final class ScriptInfoContent extends GuiElement {
        @Override
        protected void renderElement(GuiContext context) {
            if (selected == null || currentLayout == null) {
                return;
            }
            int contentWidth = getWidth();
            int y = getTop();
            ScriptInfoLayout layout = currentLayout;

            if (layout.imageSize > 0) {
                drawImage(context, layout.image, getLeft(), y, layout.imageSize);
                y += layout.imageSize + IMAGE_PADDING;
            }

            float sectionScale = 1.25F;
            context.getRenderer().drawScaledUnderlinedText(LABEL_DESCRIPTION, getLeft(), y,
                    context.getRenderer().getTheme().textPrimary, sectionScale);
            y += (int) (10 * sectionScale) + CONTENT_PADDING;
            if (layout.hasDescription) {
                context.getRenderer().drawWrappedText(layout.description, getLeft(), y, contentWidth,
                        context.getRenderer().getTheme().textPrimary);
                y += context.getRenderer().wrappedTextHeight(layout.description, contentWidth) + CONTENT_PADDING;
            }
            y += LINE_SPACING;

            if (layout.hasDescription && layout.hasIssues()) {
                context.getRenderer().drawHorizontalLine(getLeft(), getRight(), y + 2,
                        context.getRenderer().getTheme().listSeparator);
                y += issueSectionPadding(layout.hasErrors, layout.hasWarnings);
            }

            if (layout.hasIssues()) {
                int headerColor = layout.hasErrors ? context.getRenderer().getTheme().textError
                        : context.getRenderer().getTheme().textWarning;
                context.getRenderer().drawScaledUnderlinedText(LABEL_ERRORS, getLeft(), y, headerColor,
                        sectionScale);
                y += (int) (10 * sectionScale) + CONTENT_PADDING;
                if (!layout.errorIssues.getEntries().isEmpty()) {
                    y += drawIssues(context, layout.errorIssues, getLeft(), y, contentWidth,
                            context.getRenderer().getTheme().textError) + CONTENT_PADDING + LINE_SPACING;
                } else if (layout.hasErrors) {
                    context.getRenderer().drawWrappedText(layout.failure, getLeft(), y, contentWidth,
                            context.getRenderer().getTheme().textError);
                    y += context.getRenderer().wrappedTextHeight(layout.failure, contentWidth) + CONTENT_PADDING
                            + LINE_SPACING;
                }
                if (!layout.warningIssues.getEntries().isEmpty()) {
                    y += drawIssues(context, layout.warningIssues, getLeft(), y, contentWidth,
                            context.getRenderer().getTheme().textWarning) + CONTENT_PADDING + LINE_SPACING;
                }
            }

            if (layout.hasDependencies) {
                context.getRenderer().drawScaledUnderlinedText(LABEL_DEPENDENCIES, getLeft(), y,
                        context.getRenderer().getTheme().textPrimary, sectionScale);
                y += (int) (10 * sectionScale) + CONTENT_PADDING;
                drawDependencies(context, layout.dependencies, selected.getMissingDependencies(), getLeft(), y,
                        contentWidth);
            }
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() != GuiInputEvent.Type.POINTER_DOWN || currentLayout == null) {
                return false;
            }
            return clickIssues(currentLayout.errorIssues, event) || clickIssues(currentLayout.warningIssues, event);
        }
    }

    private final class ScriptInfoLayout {
        private final String description;
        private final String failure;
        private final List<String> dependencies;
        private final GuiIssueLayout errorIssues;
        private final GuiIssueLayout warningIssues;
        private final GuiTextureCache.Texture image;
        private final int imageSize;
        private final boolean hasDescription;
        private final boolean hasErrors;
        private final boolean hasWarnings;
        private final boolean hasDependencies;
        private final int contentHeight;

        private ScriptInfoLayout(GuiContext context, int contentWidth) {
            description = selected.getDescription();
            failure = selected.getFailureReason();
            dependencies = selected.getDependencies() == null ? Collections.<String>emptyList()
                    : selected.getDependencies();
            List<ScriptIssue> issues = LuaScriptErrors.getIssuesFor(selected.getDisplayName(),
                    selected.getSourceFileName());
            errorIssues = GuiIssueLayout.prepare(context.getFont(), filterIssues(issues, false), contentWidth, 0);
            warningIssues = GuiIssueLayout.prepare(context.getFont(), filterIssues(issues, true), contentWidth, 0);
            image = resolveImageTexture(selected.getImagePath());
            imageSize = IMAGE_FIXED_SIZE;
            hasDescription = hasText(description);
            hasErrors = !errorIssues.getEntries().isEmpty() || (selected.isFailed() && hasText(failure));
            hasWarnings = !warningIssues.getEntries().isEmpty();
            hasDependencies = !dependencies.isEmpty();
            contentHeight = calculateHeight(context, contentWidth);
        }

        private boolean hasIssues() {
            return hasErrors || hasWarnings;
        }

        private int calculateHeight(GuiContext context, int contentWidth) {
            int height = imageSize + IMAGE_PADDING;
            int headerHeight = (int) (10 * 1.25F) + CONTENT_PADDING;
            height += headerHeight;
            if (hasDescription) {
                height += context.getRenderer().wrappedTextHeight(description, contentWidth) + CONTENT_PADDING;
            }
            height += LINE_SPACING;
            if (hasDescription && hasIssues()) {
                height += issueSectionPadding(hasErrors, hasWarnings);
            }
            if (hasIssues()) {
                height += headerHeight;
                if (!errorIssues.getEntries().isEmpty()) {
                    height += errorIssues.getHeight() + CONTENT_PADDING + LINE_SPACING;
                } else if (hasErrors) {
                    height += context.getRenderer().wrappedTextHeight(failure, contentWidth) + CONTENT_PADDING
                            + LINE_SPACING;
                }
                if (!warningIssues.getEntries().isEmpty()) {
                    height += warningIssues.getHeight() + CONTENT_PADDING + LINE_SPACING;
                }
            }
            if (hasDependencies) {
                height += headerHeight + dependencies.size() * DEPENDENCY_LINE_HEIGHT + CONTENT_PADDING;
            }
            return height;
        }
    }

    private static boolean clickIssues(GuiIssueLayout layout, GuiInputEvent event) {
        List<GuiIssueLayout.Entry> entries = layout.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
                return true;
            }
        }
        return false;
    }

    private static int drawIssues(GuiContext context, GuiIssueLayout layout, int x, int y, int width, int color) {
        int entryY = y;
        List<GuiIssueLayout.Entry> entries = layout.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).draw(context, x, entryY, width, color);
            entryY += entries.get(i).getHeight();
        }
        return layout.getHeight();
    }

    private static void drawImage(GuiContext context, GuiTextureCache.Texture image, int x, int y, int size) {
        Rect bounds = Rect.fromPositionAndSize(x, y, size, size);
        if (image != null) {
            context.getRenderer().drawTexture(image, bounds);
            return;
        }
        context.getRenderer().drawRect(bounds, context.getRenderer().getTheme().buttonBackground);
        String mark = "?";
        int textWidth = context.getRenderer().textWidth(mark);
        float scale = size * 0.6F / Math.max(textWidth, 8);
        if (scale < 1.0F) {
            scale = 1.0F;
        }
        context.getRenderer().drawScaledCenteredText(mark, bounds,
                context.getRenderer().getTheme().textPrimary, scale, false);
    }

    private static GuiTextureCache.Texture resolveImageTexture(String imagePath) {
        if (!hasText(imagePath) || LuaModLoader.getLuaModsDir() == null) {
            return null;
        }
        String relative = imagePath.trim();
        while (relative.startsWith("/") || relative.startsWith("\\")) {
            relative = relative.substring(1);
        }
        File imageFile = new File(LuaModLoader.getLuaModsDir(), relative);
        GuiTextureCache.Texture texture = GuiTextureCache.shared().get(imageFile);
        return texture != null && texture.getWidth() == texture.getHeight() ? texture : null;
    }

    private static List<ScriptIssue> filterIssues(List<ScriptIssue> issues, boolean warning) {
        List<ScriptIssue> filtered = new ArrayList<ScriptIssue>();
        if (issues != null) {
            for (int i = 0; i < issues.size(); i++) {
                if (issues.get(i).isWarning() == warning) {
                    filtered.add(issues.get(i));
                }
            }
        }
        return filtered;
    }

    private static int issueSectionPadding(boolean hasErrors, boolean hasWarnings) {
        return !hasErrors && hasWarnings ? LINE_SPACING : SECTION_PADDING;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static int drawDependencies(GuiContext context, List<String> dependencies, List<String> missing,
            int x, int y, int width) {
        int startY = y;
        for (int i = 0; i < dependencies.size(); i++) {
            String dependency = dependencies.get(i);
            if (dependency == null) {
                continue;
            }
            boolean missingDependency = missing != null ? missing.contains(dependency)
                    : !LuaScriptRegistry.hasScriptName(dependency);
            int color = missingDependency ? context.getRenderer().getTheme().textError
                    : context.getRenderer().getTheme().textPrimary;
            String label = missingDependency ? "- " + dependency + " " + SUFFIX_DEPENDENCY_MISSING
                    : "- " + dependency;
            context.getRenderer().drawText(context.getRenderer().trimToWidth(label, width), x, y, color);
            y += DEPENDENCY_LINE_HEIGHT;
        }
        return y - startY;
    }
}
