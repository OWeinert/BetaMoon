package betamoon.gui;

import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luaapi.config.ScriptConfigField;
import betamoon.luaapi.config.ScriptConfigRegistration;
import betamoon.luaapi.config.ScriptConfigRegistry;
import betamoon.luamodloader.ScriptMod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.src.GuiScreen;
import org.luaj.vm2.LuaError;

/** Edits one script's registered config files as independent save units. */
public final class GuiScreenScriptConfig extends GuiComponentScreen {
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int FOOTER_ROW_SPACING = 4;
    private static final int FOOTER_BUTTON_GAP = 6;
    private static final int RESET_BUTTON_WIDTH = 110;

    private final GuiScreen parent;
    private final ScriptMod script;
    private final List<Section> sections = new ArrayList<Section>();
    private final EditorLayout layout = new EditorLayout();
    private final FormContent form = new FormContent();
    private final GuiScrollView scroll = new GuiScrollView(form, GuiScrollView.Policy.VERTICAL);
    private final GuiButton next = new GuiButton("", this::nextSection);
    private final GuiButton save = new GuiButton("Save", this::save);
    private final GuiButton reset = new GuiButton("Reset to Defaults", this::reset);
    private final GuiButton reload = new GuiButton("Reload Draft", this::reload);
    private final GuiButton retry = new GuiButton("Retry Apply", this::retry);
    private final GuiButton cancel = new GuiButton("Cancel", this::cancel);
    private int current;
    private String message = "";
    private boolean discardPending;

    public GuiScreenScriptConfig(GuiScreen parent, ScriptMod script) {
        this.parent = parent;
        this.script = script;
        for (ScriptConfigRegistration registration : ScriptConfigRegistry.forScript(script.getSourceFileName())) {
            if (registration.active()) {
                sections.add(new Section(registration));
            }
        }
        layout.add(next);
        layout.add(scroll);
        layout.add(save);
        layout.add(reset);
        layout.add(reload);
        layout.add(retry);
        layout.add(cancel);
        if (!sections.isEmpty()) {
            form.rebuild();
        }
    }

    @Override
    protected GuiElement createContent() {
        return layout;
    }

    @Override
    protected void drawSceneBackground(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
    }

    @Override
    protected void updateSceneState(int mouseX, int mouseY, float partialTicks) {
        if (!script.isLoaded() || sections.isEmpty() || !section().registration.active()) {
            showScreen(parent);
            return;
        }
        Section section = section();
        String suffix = section.dirty() || form.hasTextChanges() ? " *" : "";
        String sectionLabel = section.registration.label() + suffix + "  (" + (current + 1) + "/"
                + sections.size() + ")";
        String displayedLabel = getGuiContext().getRenderer().trimToWidth(sectionLabel, Math.max(0, width - 40));
        if (!displayedLabel.equals(next.getLabel())) {
            next.setLabel(displayedLabel);
        }
        next.setEnabled(sections.size() > 1);
        reload.setVisible(message.contains("disk") || message.contains("memory")
                || section.registration.malformedAtOpen());
        String reloadLabel = section.registration.malformedAtOpen() ? "Replace Invalid File" : "Reload Draft";
        if (!reloadLabel.equals(reload.getLabel())) {
            reload.setLabel(reloadLabel);
        }
        retry.setVisible(section.registration.applyError() != null);
        if (section.registration.applyError() != null) {
            message = "Saved, but apply failed: " + section.registration.applyError();
        }
    }

    @Override
    protected void onSceneUpdate() {
        for (FieldRow row : form.rows) {
            if (row.input != null) {
                row.input.tick();
            }
        }
    }

    private Section section() {
        return sections.get(current);
    }

    private void nextSection() {
        if (!form.commitInputs()) {
            return;
        }
        current = (current + 1) % sections.size();
        message = "Each section saves to its own file.";
        scroll.resetScroll();
        form.rebuild();
    }

    private void save() {
        if (!form.commitInputs()) {
            return;
        }
        try {
            Section section = section();
            section.registration.saveDraft(section.draft, section.baseline, section.diskDraft);
            section.baseline = section.registration.draft();
            section.draft = copy(section.baseline);
            section.diskDraft = null;
            message = "Saved " + section.registration.key() + ".";
            discardPending = false;
            form.rebuild();
        } catch (IOException | LuaError error) {
            message = error.getMessage();
        }
    }

    private void reset() {
        replaceDeclared(section().draft, section().registration.defaults());
        message = "Defaults are in the draft. Save to apply them.";
        form.rebuild();
    }

    private void reload() {
        try {
            Section section = section();
            if (section.registration.malformedAtOpen()) {
                section.registration.resolveMalformedFile();
                message = "The invalid file will be replaced only when you Save.";
            } else if (message.contains("memory")) {
                section.baseline = section.registration.draft();
                section.draft = copy(section.baseline);
                section.diskDraft = null;
                message = "Current script values loaded into the draft.";
                form.rebuild();
            } else {
                section.diskDraft = section.registration.reloadDraftFromDisk();
                section.draft = section.diskDraft.values();
                message = "Disk values loaded into the draft. Save to apply them.";
                form.rebuild();
            }
        } catch (IOException | LuaError error) {
            message = error.getMessage();
        }
    }

    private void retry() {
        section().registration.retryApply();
        message = "Apply will retry on the next update.";
    }

    private void cancel() {
        if (!form.commitInputs()) {
            if (discardPending) {
                showScreen(parent);
            } else {
                discardPending = true;
                message = "Invalid draft. Press Cancel again to discard it.";
            }
            return;
        }
        if (!discardPending && anyDirty()) {
            discardPending = true;
            message = "Unsaved changes will be lost. Press Cancel again to discard.";
            return;
        }
        showScreen(parent);
    }

    private boolean anyDirty() {
        for (Section section : sections) {
            if (section.dirty()) {
                return true;
            }
        }
        return false;
    }

    private static void replaceDeclared(Map<String, Object> target, Map<String, Object> defaults) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            Object existing = target.get(entry.getKey());
            if (existing instanceof Map && entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) existing;
                @SuppressWarnings("unchecked")
                Map<String, Object> fallback = (Map<String, Object>) entry.getValue();
                replaceDeclared(nested, fallback);
            } else {
                target.put(entry.getKey(), copyValue(entry.getValue()));
            }
        }
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) copyValue(source);
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                result.put(entry.getKey(), copyValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object entry : (List<?>) value) {
                result.add(copyValue(entry));
            }
            return result;
        }
        return value;
    }

    private final class EditorLayout extends GuiContainer {
        @Override
        protected void arrangeChildren(GuiContext context) {
            int width = getWidth();
            int left = getLeft() + 12;
            int usable = Math.max(0, width - 24);
            next.arrange(context, Rect.fromPositionAndSize(left, getTop() + 28, usable, 20));
            boolean hasSecondaryAction = reload.isVisible() || retry.isVisible();
            int footerRows = 2 + (hasSecondaryAction ? 1 : 0);
            int footerTop = getBottom() - 10 - footerRows * (FOOTER_BUTTON_HEIGHT + FOOTER_ROW_SPACING);
            scroll.setContentSize(usable, form.contentHeight());
            int formTop = sections.isEmpty() || section().registration.description().isEmpty() ? 54 : 66;
            scroll.arrange(context, new Rect(left, getTop() + formTop, left + usable,
                    Math.max(getTop() + formTop, footerTop - 8)));
            int centerX = getLeft() + width / 2;
            if (hasSecondaryAction) {
                int reloadWidth = Math.min(130, usable);
                int retryWidth = Math.min(100, usable);
                if (reload.isVisible() && retry.isVisible()) {
                    reloadWidth = Math.min(reloadWidth, Math.max(0, (usable - FOOTER_BUTTON_GAP) / 2));
                    retryWidth = Math.min(retryWidth, Math.max(0, usable - FOOTER_BUTTON_GAP - reloadWidth));
                    int pairLeft = centerX - (reloadWidth + FOOTER_BUTTON_GAP + retryWidth) / 2;
                    reload.arrange(context, Rect.fromPositionAndSize(pairLeft, footerTop,
                            reloadWidth, FOOTER_BUTTON_HEIGHT));
                    retry.arrange(context, Rect.fromPositionAndSize(pairLeft + reloadWidth + FOOTER_BUTTON_GAP,
                            footerTop, retryWidth, FOOTER_BUTTON_HEIGHT));
                } else {
                    reload.arrange(context, Rect.fromPositionAndSize(centerX - reloadWidth / 2,
                            footerTop, reloadWidth, FOOTER_BUTTON_HEIGHT));
                    retry.arrange(context, Rect.fromPositionAndSize(centerX - retryWidth / 2,
                            footerTop, retryWidth, FOOTER_BUTTON_HEIGHT));
                }
            }
            int resetTop = footerTop + (hasSecondaryAction ? FOOTER_BUTTON_HEIGHT + FOOTER_ROW_SPACING : 0);
            int resetWidth = Math.min(RESET_BUTTON_WIDTH, usable);
            int rowLeft = centerX - resetWidth / 2;
            reset.arrange(context, Rect.fromPositionAndSize(rowLeft,
                    resetTop, resetWidth, FOOTER_BUTTON_HEIGHT));
            int pairGap = Math.min(FOOTER_BUTTON_GAP, resetWidth);
            int saveWidth = (resetWidth - pairGap) / 2;
            int cancelWidth = resetWidth - pairGap - saveWidth;
            int pairTop = resetTop + FOOTER_BUTTON_HEIGHT + FOOTER_ROW_SPACING;
            save.arrange(context, Rect.fromPositionAndSize(rowLeft, pairTop,
                    saveWidth, FOOTER_BUTTON_HEIGHT));
            cancel.arrange(context, Rect.fromPositionAndSize(rowLeft + saveWidth + pairGap,
                    pairTop, cancelWidth, FOOTER_BUTTON_HEIGHT));
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            int color = context.getRenderer().getTheme().textPrimary;
            String title = "Configure " + script.getDisplayName();
            context.getRenderer().drawText(context.getRenderer().trimToWidth(title, getWidth() - 24),
                    getLeft() + 12, getTop() + 10, color);
            if (!sections.isEmpty()) {
                String description = section().registration.description();
                if (!description.isEmpty()) {
                    context.getRenderer().drawText(context.getRenderer().trimToWidth(description, getWidth() - 24),
                            getLeft() + 12, getTop() + 51, context.getRenderer().getTheme().textMuted);
                }
            }
            if (!message.isEmpty()) {
                context.getRenderer().drawText(context.getRenderer().trimToWidth(message, getWidth() - 24),
                        getLeft() + 12, getBottom() - 12, context.getRenderer().getTheme().textWarning);
            }
        }
    }

    private final class FormContent extends GuiContainer {
        private final List<FieldRow> rows = new ArrayList<FieldRow>();

        private void rebuild() {
            clear();
            rows.clear();
            if (sections.isEmpty()) {
                return;
            }
            Section section = section();
            for (Map.Entry<String, ScriptConfigField> entry : section.registration.fields().entrySet()) {
                addField(entry.getValue(), section.draft, entry.getKey(), entry.getKey(), 0, false, false);
            }
            requestLayout();
        }

        private void addField(ScriptConfigField field, Object parent, Object slot, String path, int depth,
                boolean removable, boolean inheritedReadOnly) {
            Object value = value(parent, slot);
            boolean readOnly = inheritedReadOnly || field.readOnly();
            FieldRow row = new FieldRow(field, parent, slot, path, depth, removable, readOnly);
            rows.add(row);
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) value;
                for (Map.Entry<String, ScriptConfigField> entry : field.children().entrySet()) {
                    addField(entry.getValue(), object, entry.getKey(), path + "." + entry.getKey(), depth + 1,
                            false, readOnly);
                }
            } else if (value instanceof List) {
                List<?> values = (List<?>) value;
                for (int index = 0; index < values.size(); index++) {
                    Object item = values.get(index);
                    ScriptConfigField descriptor = field.element();
                    if (descriptor == null || !sameType(item, descriptor.defaultValue())) {
                        descriptor = ScriptConfigField.inferred("Item " + (index + 1), item);
                    }
                    addField(descriptor, value, Integer.valueOf(index), path + "[" + (index + 1) + "]",
                            depth + 1, true, readOnly);
                }
            }
        }

        private boolean commitInputs() {
            try {
                for (FieldRow row : rows) {
                    if (row.input == null || row.readOnly) {
                        continue;
                    }
                    String text = row.input.value();
                    Object fallback = row.field.defaultValue();
                    Object parsed = fallback instanceof Number ? Double.valueOf(Double.parseDouble(text)) : text;
                    setValue(row.parent, row.slot, parsed);
                }
                return true;
            } catch (NumberFormatException error) {
                message = "Enter a valid number before continuing.";
                return false;
            }
        }

        private boolean hasTextChanges() {
            for (FieldRow row : rows) {
                if (row.input != null && !row.readOnly
                        && !row.input.value().equals(String.valueOf(value(row.parent, row.slot)))) {
                    return true;
                }
            }
            return false;
        }

        private int contentHeight() {
            return rows.size() * 42 + 8;
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            int y = getTop() + 4;
            for (FieldRow row : rows) {
                int inset = Math.min(row.depth * 12, Math.max(0, getWidth() / 3));
                int controlLeft = getLeft() + Math.max(getWidth() / 2, inset + 100);
                int controlWidth = Math.max(40, getRight() - controlLeft - 7);
                if (row.input != null) {
                    row.input.arrange(context, Rect.fromPositionAndSize(controlLeft, y + 3,
                            row.remove != null ? Math.max(20, controlWidth - 28) : controlWidth, 18));
                }
                if (row.action != null) {
                    row.action.arrange(context, Rect.fromPositionAndSize(controlLeft, y + 3,
                            row.remove != null ? Math.max(20, controlWidth - 28) : controlWidth, 20));
                }
                if (row.remove != null) {
                    row.remove.arrange(context, Rect.fromPositionAndSize(getRight() - 29, y + 3, 22, 20));
                }
                y += 42;
            }
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            int y = getTop() + 4;
            for (FieldRow row : rows) {
                int inset = Math.min(row.depth * 12, Math.max(0, getWidth() / 3));
                int labelWidth = Math.max(20, getWidth() / 2 - inset - 8);
                String label = row.slot instanceof Integer ? "Item " + (((Integer) row.slot).intValue() + 1)
                        : row.field.label();
                label += row.readOnly ? " (locked)" : "";
                context.getRenderer().drawText(context.getRenderer().trimToWidth(label, labelWidth),
                        getLeft() + inset + 2, y + 4, context.getRenderer().getTheme().textPrimary);
                String details = row.field.description();
                if (details.isEmpty()) {
                    details = row.field.readOnly() && !row.field.readOnlyReason().isEmpty()
                            ? row.field.readOnlyReason() : "Default: " + display(row.field.defaultValue());
                }
                context.getRenderer().drawText(context.getRenderer().trimToWidth(details, getWidth() - inset - 8),
                        getLeft() + inset + 2, y + 27, context.getRenderer().getTheme().textMuted);
                y += 42;
            }
        }
    }

    private final class FieldRow {
        private final ScriptConfigField field;
        private final Object parent;
        private final Object slot;
        private final String path;
        private final int depth;
        private final boolean readOnly;
        private GuiConfigInput input;
        private GuiButton action;
        private GuiButton remove;

        private FieldRow(ScriptConfigField field, Object parent, Object slot, String path, int depth,
                boolean removable, boolean readOnly) {
            this.field = field;
            this.parent = parent;
            this.slot = slot;
            this.path = path;
            this.depth = depth;
            this.readOnly = readOnly;
            Object currentValue = value(parent, slot);
            if (currentValue instanceof Boolean) {
                action = form.add(new GuiButton((Boolean) currentValue ? "On" : "Off", () -> {
                    setValue(parent, slot, !((Boolean) value(parent, slot)).booleanValue());
                    action.setLabel((Boolean) value(parent, slot) ? "On" : "Off");
                }));
            } else if (currentValue instanceof String && !field.choices().isEmpty()) {
                action = form.add(new GuiButton((String) currentValue, () -> {
                    String selected = (String) value(parent, slot);
                    List<String> choices = field.choices();
                    setValue(parent, slot, choices.get((choices.indexOf(selected) + 1) % choices.size()));
                    action.setLabel((String) value(parent, slot));
                }));
            } else if (currentValue instanceof String || currentValue instanceof Number) {
                input = form.add(new GuiConfigInput(GuiScreenScriptConfig.this, String.valueOf(currentValue)));
            } else if (currentValue instanceof List) {
                action = form.add(new GuiButton("Add Item", () -> {
                    if (!form.commitInputs()) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<Object> array = (List<Object>) value(parent, slot);
                    if (array.size() >= field.maxItems() || field.element() == null) {
                        message = "This array needs element metadata or has reached its limit.";
                        return;
                    }
                    array.add(copyValue(field.element().defaultValue()));
                    form.rebuild();
                }));
                action.setEnabled(!readOnly && field.element() != null
                        && ((List<?>) currentValue).size() < field.maxItems());
            }
            if (action != null) {
                action.setEnabled(!readOnly && action.isEnabled());
            }
            if (input != null) {
                input.setEnabled(!readOnly);
            }
            if (removable) {
                remove = form.add(new GuiButton("-", () -> {
                    if (!form.commitInputs()) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<Object> array = (List<Object>) parent;
                    array.remove(((Integer) slot).intValue());
                    form.rebuild();
                }));
                remove.setEnabled(!readOnly);
            }
        }
    }

    private static Object value(Object parent, Object slot) {
        return parent instanceof Map ? ((Map<?, ?>) parent).get(slot) : ((List<?>) parent).get((Integer) slot);
    }

    private static void setValue(Object parent, Object slot, Object value) {
        if (parent instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) parent;
            object.put((String) slot, value);
        } else {
            @SuppressWarnings("unchecked")
            List<Object> array = (List<Object>) parent;
            array.set((Integer) slot, value);
        }
    }

    private static boolean sameType(Object left, Object right) {
        return left instanceof Number && right instanceof Number || left != null && right != null
                && left.getClass() == right.getClass();
    }

    private static String display(Object value) {
        return value instanceof Map ? "{...}" : value instanceof List ? "[...]" : String.valueOf(value);
    }

    private static final class Section {
        private final ScriptConfigRegistration registration;
        private Map<String, Object> baseline;
        private Map<String, Object> draft;
        private ScriptConfigRegistration.DiskDraft diskDraft;

        private Section(ScriptConfigRegistration registration) {
            this.registration = registration;
            baseline = registration.draft();
            draft = copy(baseline);
        }

        private boolean dirty() {
            return !Objects.equals(baseline, draft);
        }
    }
}
