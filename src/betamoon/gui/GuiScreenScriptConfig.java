package betamoon.gui;

import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDropdownButton;
import betamoon.gui.widget.GuiDropdownMenu;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luaapi.config.ScriptConfigChoice;
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
import org.lwjgl.input.Keyboard;
import org.luaj.vm2.LuaError;

/** Edits one script's registered config files as independent save units. */
public final class GuiScreenScriptConfig extends GuiComponentScreen {
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int FOOTER_ROW_SPACING = 4;
    private static final int FOOTER_BUTTON_GAP = 6;
    private static final int RESET_BUTTON_WIDTH = 110;
    private static final int FIELD_LABEL_WIDTH = 100;
    private static final int FIELD_CONTROL_WIDTH = 116;
    private static final int FIELD_COLUMN_GAP = 10;
    private static final int FIELD_MARKER_WIDTH = 8;
    private static final int FIELD_CONTROL_TOP_OFFSET = 3;
    private static final int FIELD_INPUT_HEIGHT = 18;
    private static final int FIELD_BUTTON_HEIGHT = 20;
    private static final int FIELD_ROW_HEIGHT = 28;
    private static final Object MISSING_BASELINE = new Object();

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
    private final GuiDropdownMenu dropdown = new GuiDropdownMenu();
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
        layout.add(dropdown);
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
        String suffix = form.hasModifiedEntries() ? " *" : "";
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

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (dropdown.isVisible() && keyCode == Keyboard.KEY_ESCAPE) {
            dropdown.close();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private Section section() {
        return sections.get(current);
    }

    private void nextSection() {
        dropdown.close();
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
        dropdown.close();
        replaceDeclared(section().draft, section().registration.defaults());
        message = "Defaults are in the draft. Save to apply them.";
        form.rebuild();
    }

    private void reload() {
        dropdown.close();
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
        dropdown.close();
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
            dropdown.arrange(context, getBounds());
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
            dropdown.close();
            clear();
            rows.clear();
            if (sections.isEmpty()) {
                return;
            }
            Section section = section();
            for (Map.Entry<String, ScriptConfigField> entry : section.registration.fields().entrySet()) {
                addField(entry.getValue(), section.draft, section.baseline, entry.getKey(), entry.getKey(),
                        0, false, false);
            }
            requestLayout();
        }

        private void addField(ScriptConfigField field, Object parent, Object baselineParent, Object slot,
                String path, int depth, boolean removable, boolean inheritedReadOnly) {
            Object value = value(parent, slot);
            Object baselineValue = baselineValue(baselineParent, slot);
            boolean readOnly = inheritedReadOnly || field.readOnly();
            FieldRow row = new FieldRow(field, parent, slot, baselineValue, path, depth, removable, readOnly);
            rows.add(row);
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) value;
                for (Map.Entry<String, ScriptConfigField> entry : field.children().entrySet()) {
                    addField(entry.getValue(), object, baselineValue, entry.getKey(),
                            path + "." + entry.getKey(), depth + 1, false, readOnly);
                }
            } else if (value instanceof List) {
                List<?> values = (List<?>) value;
                for (int index = 0; index < values.size(); index++) {
                    Object item = values.get(index);
                    ScriptConfigField descriptor = field.element();
                    if (descriptor == null || !sameType(item, descriptor.defaultValue())) {
                        descriptor = ScriptConfigField.inferred("Item " + (index + 1), item);
                    }
                    addField(descriptor, value, baselineValue, Integer.valueOf(index),
                            path + "[" + (index + 1) + "]", depth + 1, true, readOnly);
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

        private boolean hasModifiedEntries() {
            for (FieldRow row : rows) {
                if (row.modified()) {
                    return true;
                }
            }
            return false;
        }

        private int contentHeight() {
            return rows.size() * FIELD_ROW_HEIGHT + 8;
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            FormColumns columns = columns();
            int y = getTop() + 4;
            for (FieldRow row : rows) {
                int controlWidth = controlWidth(row, columns);
                if (row.input != null) {
                    row.input.arrange(context, Rect.fromPositionAndSize(columns.controlLeft,
                            y + FIELD_CONTROL_TOP_OFFSET, controlWidth, FIELD_INPUT_HEIGHT));
                }
                if (row.action != null) {
                    row.action.arrange(context, Rect.fromPositionAndSize(columns.controlLeft,
                            y + FIELD_CONTROL_TOP_OFFSET, controlWidth, FIELD_BUTTON_HEIGHT));
                }
                if (row.slider != null) {
                    row.slider.arrange(context, Rect.fromPositionAndSize(columns.controlLeft,
                            y + FIELD_CONTROL_TOP_OFFSET, controlWidth, FIELD_BUTTON_HEIGHT));
                }
                if (row.remove != null) {
                    row.remove.arrange(context, Rect.fromPositionAndSize(columns.controlLeft + controlWidth
                            + FIELD_COLUMN_GAP / 2, y + FIELD_CONTROL_TOP_OFFSET, 22, FIELD_BUTTON_HEIGHT));
                }
                y += FIELD_ROW_HEIGHT;
            }
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            FormColumns columns = columns();
            int y = getTop() + 4;
            for (FieldRow row : rows) {
                int inset = Math.min(row.depth * 12, columns.labelWidth);
                int labelWidth = Math.max(0, columns.labelWidth - inset);
                String label = row.slot instanceof Integer ? "Item " + (((Integer) row.slot).intValue() + 1)
                        : row.field.label();
                label += row.readOnly ? " (locked)" : "";
                String visibleLabel = context.getRenderer().trimToWidth(label, labelWidth);
                int labelRight = columns.controlLeft - FIELD_COLUMN_GAP;
                int controlHeight = row.input == null ? FIELD_BUTTON_HEIGHT : FIELD_INPUT_HEIGHT;
                int labelY = y + FIELD_CONTROL_TOP_OFFSET
                        + (controlHeight - context.getRenderer().lineHeight() + 1) / 2;
                context.getRenderer().drawText(visibleLabel,
                        labelRight - context.getRenderer().textWidth(visibleLabel), labelY,
                        context.getRenderer().getTheme().textPrimary);
                if (row.modified() && (row.input != null || row.action != null || row.slider != null)) {
                    int markerX = columns.controlLeft + controlWidth(row, columns)
                            - context.getRenderer().textWidth("*") / 2;
                    int markerY = y + FIELD_CONTROL_TOP_OFFSET - context.getRenderer().lineHeight() / 2;
                    context.getRenderer().drawText("*", markerX, markerY,
                            context.getRenderer().getTheme().textWarning);
                }
                if (!dropdown.isVisible() && Rect.fromPositionAndSize(columns.left, y,
                        columns.groupWidth, FIELD_ROW_HEIGHT)
                        .contains(context.getMouseX(), context.getMouseY())
                        && context.getRenderer().isPointVisible(context.getMouseX(), context.getMouseY())) {
                    List<String> details = new ArrayList<String>();
                    if (!row.field.description().isEmpty()) {
                        addTooltipText(context, details, row.field.description());
                    }
                    if (row.readOnly && !row.field.readOnlyReason().isEmpty()) {
                        addTooltipText(context, details, row.field.readOnlyReason());
                    }
                    addTooltipText(context, details, "Default: " + display(row.field.defaultValue()));
                    context.showTooltip(details);
                }
                y += FIELD_ROW_HEIGHT;
            }
        }

        private void addTooltipText(GuiContext context, List<String> lines, String text) {
            int maximumWidth = Math.max(1, context.getScreenWidth() - 24);
            for (String paragraph : text.split("\\r?\\n", -1)) {
                String line = "";
                for (int index = 0; index < paragraph.length(); index++) {
                    char character = paragraph.charAt(index);
                    String next = line + character;
                    if (!line.isEmpty() && context.getRenderer().textWidth(next) > maximumWidth) {
                        int space = line.lastIndexOf(' ');
                        if (space > 0) {
                            lines.add(line.substring(0, space));
                            line = line.substring(space + 1) + character;
                        } else {
                            lines.add(line);
                            line = String.valueOf(character);
                        }
                    } else {
                        line = next;
                    }
                }
                lines.add(line.trim());
            }
        }

        private FormColumns columns() {
            int center = getLeft() + getWidth() / 2;
            int halfWidth = getWidth() / 2;
            int halfGap = FIELD_COLUMN_GAP / 2;
            int labelWidth = Math.min(FIELD_LABEL_WIDTH, Math.max(0, halfWidth - halfGap - 4));
            int controlWidth = Math.min(FIELD_CONTROL_WIDTH,
                    Math.max(0, halfWidth - halfGap - FIELD_MARKER_WIDTH - 4));
            int left = center - halfGap - labelWidth;
            int controlLeft = center + halfGap;
            int groupWidth = labelWidth + FIELD_COLUMN_GAP + controlWidth + FIELD_MARKER_WIDTH;
            return new FormColumns(left, groupWidth, labelWidth, controlLeft, controlWidth);
        }

        private int controlWidth(FieldRow row, FormColumns columns) {
            int removeWidth = row.remove == null ? 0 : 22 + FIELD_COLUMN_GAP / 2;
            return Math.max(0, columns.controlWidth - removeWidth);
        }
    }

    private static final class FormColumns {
        private final int left;
        private final int groupWidth;
        private final int labelWidth;
        private final int controlLeft;
        private final int controlWidth;

        private FormColumns(int left, int groupWidth, int labelWidth, int controlLeft, int controlWidth) {
            this.left = left;
            this.groupWidth = groupWidth;
            this.labelWidth = labelWidth;
            this.controlLeft = controlLeft;
            this.controlWidth = controlWidth;
        }
    }

    private final class FieldRow {
        private final ScriptConfigField field;
        private final Object parent;
        private final Object slot;
        private final Object baselineValue;
        private final String path;
        private final int depth;
        private final boolean readOnly;
        private GuiConfigInput input;
        private GuiConfigSlider slider;
        private GuiButton action;
        private GuiButton remove;

        private FieldRow(ScriptConfigField field, Object parent, Object slot, Object baselineValue,
                String path, int depth, boolean removable, boolean readOnly) {
            this.field = field;
            this.parent = parent;
            this.slot = slot;
            this.baselineValue = baselineValue;
            this.path = path;
            this.depth = depth;
            this.readOnly = readOnly;
            Object currentValue = value(parent, slot);
            if (currentValue instanceof Boolean) {
                action = form.add(new GuiButton((Boolean) currentValue ? "True" : "False", () -> {
                    setValue(parent, slot, !((Boolean) value(parent, slot)).booleanValue());
                    action.setLabel((Boolean) value(parent, slot) ? "True" : "False");
                }));
            } else if ("slider".equals(field.input())) {
                slider = form.add(new GuiConfigSlider(field.minimum(), field.maximum(), field.step(),
                        ((Number) currentValue).doubleValue(), nextValue -> setValue(parent, slot,
                                Double.valueOf(nextValue))));
            } else if ("choice".equals(field.input())) {
                List<GuiDropdownButton.Option<ScriptConfigChoice>> options =
                        new ArrayList<GuiDropdownButton.Option<ScriptConfigChoice>>();
                for (ScriptConfigChoice choice : field.choices()) {
                    options.add(new GuiDropdownButton.Option<ScriptConfigChoice>(choice, choice.label()));
                }
                GuiDropdownButton<ScriptConfigChoice> button = new GuiDropdownButton<ScriptConfigChoice>(dropdown,
                        options, field.choice(currentValue), selected -> setValue(parent, slot, selected.value()));
                button.setBeforeOpen(form::commitInputs);
                action = form.add(button);
            } else if ("cycle".equals(field.input())) {
                action = form.add(new GuiButton("", () -> {
                    Object selected = value(parent, slot);
                    List<ScriptConfigChoice> choices = field.choices();
                    int index = -1;
                    for (int candidate = 0; candidate < choices.size(); candidate++) {
                        if (choices.get(candidate).matches(selected)) {
                            index = candidate;
                            break;
                        }
                    }
                    setValue(parent, slot, choices.get((index + 1) % choices.size()).value());
                    updateChoiceLabel();
                }));
                updateChoiceLabel();
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
            if (slider != null) {
                slider.setEnabled(!readOnly);
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

        private void updateChoiceLabel() {
            ScriptConfigChoice selected = field.choice(value(parent, slot));
            String label = selected == null ? String.valueOf(value(parent, slot)) : selected.label();
            action.setLabel(label);
        }

        private boolean modified() {
            Object currentValue = value(parent, slot);
            if (currentValue instanceof Map) {
                return false;
            }
            if (currentValue instanceof List && baselineValue instanceof List) {
                return ((List<?>) currentValue).size() != ((List<?>) baselineValue).size();
            }
            if (input != null && !readOnly) {
                String typed = input.value();
                if (baselineValue instanceof Number) {
                    try {
                        return Double.compare(Double.parseDouble(typed),
                                ((Number) baselineValue).doubleValue()) != 0;
                    } catch (NumberFormatException error) {
                        return true;
                    }
                }
                return !Objects.equals(typed, baselineValue);
            }
            return !Objects.equals(currentValue, baselineValue);
        }
    }

    private static Object baselineValue(Object parent, Object slot) {
        if (parent instanceof Map) {
            Map<?, ?> object = (Map<?, ?>) parent;
            return object.containsKey(slot) ? object.get(slot) : MISSING_BASELINE;
        }
        if (parent instanceof List) {
            List<?> array = (List<?>) parent;
            int index = ((Integer) slot).intValue();
            return index < array.size() ? array.get(index) : MISSING_BASELINE;
        }
        return MISSING_BASELINE;
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
        if (value instanceof Boolean) {
            return (Boolean) value ? "True" : "False";
        }
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
