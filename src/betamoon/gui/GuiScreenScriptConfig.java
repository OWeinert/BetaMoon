package betamoon.gui;

import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDropdownButton;
import betamoon.gui.widget.GuiDropdownMenu;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luaapi.config.ScriptConfigChoice;
import betamoon.luaapi.config.ScriptConfigField;
import betamoon.luaapi.config.ScriptConfigRegistration;
import betamoon.luaapi.config.ScriptConfigRegistry;
import betamoon.luaapi.config.ScriptConfigValidationException;
import betamoon.luamodloader.ScriptMod;
import betamoon.utils.McColors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final int FIELD_MARKER_WIDTH = 10;
    private static final int FIELD_MARKER_GAP = 3;
    private static final int FIELD_NESTED_INDENT = 12;
    private static final int FIELD_MIN_CONTROL_WIDTH = 52;
    private static final int FIELD_CONTROL_TOP_OFFSET = 3;
    private static final int FIELD_INPUT_HEIGHT = 18;
    private static final int FIELD_BUTTON_HEIGHT = 20;
    private static final int FIELD_ROW_HEIGHT = 28;
    private static final int FIELD_HEADING_TOP_GAP = 4;
    private static final int FIELD_SEPARATOR_COLOR = 0x55777777;
    private static final int FIELD_HEADING_TEXT_COLOR = McColors.AQUA.getArgb(0xFF);
    private static final int FIELD_HEADING_HOVER_TEXT_COLOR = 0xFF88FFFF;
    private static final int FIELD_GROUP_BRANCH_COLOR = 0x44777777;
    private static final int FIELD_GROUP_EDGE_COLOR = 0x66888888;
    private static final int FIELD_GROUP_TITLE_OFFSET = 10;
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
        String suffix = section.dirty() || form.hasModifiedEntries() ? " *" : "";
        String sectionLabel = section.registration.label() + suffix + "  (" + (current + 1) + "/" + sections.size()
                + ")";
        String displayedLabel = getGuiContext().getRenderer().trimToWidth(sectionLabel, Math.max(0, width - 40));
        if (!displayedLabel.equals(next.getLabel())) {
            next.setLabel(displayedLabel);
        }
        next.setEnabled(sections.size() > 1);
        reload.setVisible(
                message.contains("disk") || message.contains("memory") || section.registration.malformedAtOpen());
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
            if (row.keyInput != null) {
                row.keyInput.tick();
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
        } catch (ScriptConfigValidationException error) {
            message = error.getMessage();
            form.revealInvalidField(error.path());
        } catch (IOException | LuaError error) {
            message = error.getMessage();
        }
    }

    private void reset() {
        dropdown.close();
        replaceDeclared(section().draft, section().registration.defaults(), section().registration.fields());
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

    private static void replaceDeclared(Map<String, Object> target, Map<String, Object> defaults,
            Map<String, ScriptConfigField> fields) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            Object existing = target.get(entry.getKey());
            ScriptConfigField field = fields.get(entry.getKey());
            if (existing instanceof Map && entry.getValue() instanceof Map
                    && !"dictionary".equals(field.collectionKind())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) existing;
                @SuppressWarnings("unchecked")
                Map<String, Object> fallback = (Map<String, Object>) entry.getValue();
                replaceDeclared(nested, fallback, field.children());
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
            scroll.arrange(context,
                    new Rect(left, getTop() + formTop, left + usable, Math.max(getTop() + formTop, footerTop - 8)));
            int centerX = getLeft() + width / 2;
            if (hasSecondaryAction) {
                int reloadWidth = Math.min(130, usable);
                int retryWidth = Math.min(100, usable);
                if (reload.isVisible() && retry.isVisible()) {
                    reloadWidth = Math.min(reloadWidth, Math.max(0, (usable - FOOTER_BUTTON_GAP) / 2));
                    retryWidth = Math.min(retryWidth, Math.max(0, usable - FOOTER_BUTTON_GAP - reloadWidth));
                    int pairLeft = centerX - (reloadWidth + FOOTER_BUTTON_GAP + retryWidth) / 2;
                    reload.arrange(context,
                            Rect.fromPositionAndSize(pairLeft, footerTop, reloadWidth, FOOTER_BUTTON_HEIGHT));
                    retry.arrange(context, Rect.fromPositionAndSize(pairLeft + reloadWidth + FOOTER_BUTTON_GAP,
                            footerTop, retryWidth, FOOTER_BUTTON_HEIGHT));
                } else {
                    reload.arrange(context, Rect.fromPositionAndSize(centerX - reloadWidth / 2, footerTop, reloadWidth,
                            FOOTER_BUTTON_HEIGHT));
                    retry.arrange(context, Rect.fromPositionAndSize(centerX - retryWidth / 2, footerTop, retryWidth,
                            FOOTER_BUTTON_HEIGHT));
                }
            }
            int resetTop = footerTop + (hasSecondaryAction ? FOOTER_BUTTON_HEIGHT + FOOTER_ROW_SPACING : 0);
            int resetWidth = Math.min(RESET_BUTTON_WIDTH, usable);
            int rowLeft = centerX - resetWidth / 2;
            reset.arrange(context, Rect.fromPositionAndSize(rowLeft, resetTop, resetWidth, FOOTER_BUTTON_HEIGHT));
            int pairGap = Math.min(FOOTER_BUTTON_GAP, resetWidth);
            int saveWidth = (resetWidth - pairGap) / 2;
            int cancelWidth = resetWidth - pairGap - saveWidth;
            int pairTop = resetTop + FOOTER_BUTTON_HEIGHT + FOOTER_ROW_SPACING;
            save.arrange(context, Rect.fromPositionAndSize(rowLeft, pairTop, saveWidth, FOOTER_BUTTON_HEIGHT));
            cancel.arrange(context, Rect.fromPositionAndSize(rowLeft + saveWidth + pairGap, pairTop, cancelWidth,
                    FOOTER_BUTTON_HEIGHT));
            dropdown.arrange(context, getBounds());
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            int color = context.getRenderer().getTheme().textPrimary;
            String title = "Configure " + script.getDisplayName();
            context.getRenderer().drawText(context.getRenderer().trimToWidth(title, getWidth() - 24), getLeft() + 12,
                    getTop() + 10, color);
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
        private final List<Integer> rowOffsets = new ArrayList<Integer>();
        private int measuredHeight = 8;

        private String fieldPath(String parentPath, String key) {
            return parentPath + "/f" + key.length() + ":" + key;
        }

        private String itemPath(String parentPath, int index) {
            return parentPath + "/i" + index;
        }

        private void rebuild() {
            dropdown.close();
            clear();
            rows.clear();
            rowOffsets.clear();
            measuredHeight = 8;
            if (sections.isEmpty()) {
                return;
            }
            Section section = section();
            for (Map.Entry<String, ScriptConfigField> entry : section.registration.fields().entrySet()) {
                addField(entry.getValue(), section.draft, section.baseline, entry.getKey(),
                        fieldPath("", entry.getKey()), 0, false, false, false);
            }
            measureRows();
            requestLayout();
        }

        private void measureRows() {
            int offset = 4;
            List<FieldRow> openCollections = new ArrayList<FieldRow>();
            for (int index = 0; index < rows.size(); index++) {
                FieldRow row = rows.get(index);
                while (!openCollections.isEmpty()
                        && openCollections.get(openCollections.size() - 1).depth >= row.depth) {
                    FieldRow collection = openCollections.remove(openCollections.size() - 1);
                    collection.addTopOffset = offset;
                    offset += FIELD_ROW_HEIGHT;
                }
                if (index > 0 && row.toggle != null) {
                    offset += FIELD_HEADING_TOP_GAP;
                }
                rowOffsets.add(Integer.valueOf(offset));
                offset += FIELD_ROW_HEIGHT;
                if (row.toggle != null && row.action != null && isExpanded(row)) {
                    openCollections.add(row);
                }
            }
            while (!openCollections.isEmpty()) {
                FieldRow collection = openCollections.remove(openCollections.size() - 1);
                collection.addTopOffset = offset;
                offset += FIELD_ROW_HEIGHT;
            }
            measuredHeight = offset + 4;
        }

        private int rowTop(int index) {
            return getTop() + rowOffsets.get(index).intValue();
        }

        private void addField(ScriptConfigField field, Object parent, Object baselineParent, Object slot, String path,
                int depth, boolean removable, boolean inheritedReadOnly, boolean dictionaryEntry) {
            Object value = value(parent, slot);
            Object baselineValue = baselineValue(baselineParent, slot);
            boolean readOnly = inheritedReadOnly || field.readOnly();
            FieldRow row = new FieldRow(field, parent, slot, baselineValue, path, depth, removable, readOnly,
                    inheritedReadOnly, dictionaryEntry);
            rows.add(row);
            if (("text".equals(field.input()) && field.collectionKind() != null)
                    || row.toggle != null && !isExpanded(row)) {
                return;
            }
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) value;
                if ("dictionary".equals(field.collectionKind())) {
                    for (String key : object.keySet()) {
                        addField(field.valueField(), object, baselineValue, key, fieldPath(path, key), depth + 1, true,
                                readOnly, true);
                    }
                } else {
                    for (Map.Entry<String, ScriptConfigField> entry : field.children().entrySet()) {
                        addField(entry.getValue(), object, baselineValue, entry.getKey(),
                                fieldPath(path, entry.getKey()), depth + 1, false, readOnly, false);
                    }
                }
            } else if (value instanceof List) {
                List<?> values = (List<?>) value;
                for (int index = 0; index < values.size(); index++) {
                    Object item = values.get(index);
                    ScriptConfigField descriptor = field.element();
                    if (descriptor == null || !sameType(item, descriptor.defaultValue())) {
                        descriptor = ScriptConfigField.inferred("Item " + (index + 1), item);
                    }
                    addField(descriptor, value, baselineValue, Integer.valueOf(index), itemPath(path, index), depth + 1,
                            true, readOnly, false);
                }
            }
        }

        private boolean isExpanded(FieldRow row) {
            return row.field.expanded() != section().toggledExpansionPaths.contains(row.path);
        }

        private void setExpanded(FieldRow row, boolean expanded) {
            if (row.field.expanded() == expanded) {
                section().toggledExpansionPaths.remove(row.path);
            } else {
                section().toggledExpansionPaths.add(row.path);
            }
        }

        private void toggleSection(FieldRow toggled, GuiContext context) {
            if (!commitInputs()) {
                return;
            }
            String path = toggled.path;
            setExpanded(toggled, !isExpanded(toggled));
            rebuild();
            for (FieldRow row : rows) {
                if (path.equals(row.path) && row.toggle != null) {
                    context.requestFocus(row.toggle);
                    break;
                }
            }
        }

        private boolean commitInputs() {
            try {
                Map<FieldRow, Object> edits = new LinkedHashMap<FieldRow, Object>();
                Map<Map<String, Object>, Map<String, String>> keyEdits = new IdentityHashMap<Map<String, Object>, Map<String, String>>();
                if (!collectInputEdits(edits, keyEdits) || !uniqueKeys(keyEdits)) {
                    return false;
                }
                applyInputEdits(edits);
                applyKeyEdits(keyEdits);
                return true;
            } catch (NumberFormatException error) {
                message = "Enter a valid number before continuing.";
                return false;
            } catch (IllegalArgumentException error) {
                message = error.getMessage();
                return false;
            }
        }

        private boolean collectInputEdits(Map<FieldRow, Object> edits,
                Map<Map<String, Object>, Map<String, String>> keyEdits) {
            for (FieldRow row : rows) {
                if (row.keyInput != null && !row.structureReadOnly) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dictionary = (Map<String, Object>) row.parent;
                    Map<String, String> changes = keyEdits.get(dictionary);
                    if (changes == null) {
                        changes = new LinkedHashMap<String, String>();
                        keyEdits.put(dictionary, changes);
                    }
                    String key = row.keyInput.value();
                    if (key.trim().isEmpty()) {
                        return inputError(row, row.keyInput, "Dictionary keys cannot be empty.");
                    }
                    changes.put((String) row.slot, key);
                }
                if (row.input != null && !row.readOnly) {
                    String typed = row.input.value();
                    Object fallback = row.field.defaultValue();
                    try {
                        Object parsed = row.field.collectionKind() != null
                                ? ScriptConfigTextValues.parse(typed, row.field)
                                : fallback instanceof Number ? Double.valueOf(Double.parseDouble(typed)) : typed;
                        edits.put(row, parsed);
                    } catch (NumberFormatException error) {
                        return inputError(row, row.input, "Enter a valid number.");
                    } catch (IllegalArgumentException error) {
                        return inputError(row, row.input, error.getMessage());
                    }
                }
            }
            return true;
        }

        private boolean uniqueKeys(Map<Map<String, Object>, Map<String, String>> keyEdits) {
            for (Map.Entry<Map<String, Object>, Map<String, String>> entry : keyEdits.entrySet()) {
                Map<String, FieldRow> seen = new LinkedHashMap<String, FieldRow>();
                for (FieldRow row : rows) {
                    if (row.parent != entry.getKey() || row.keyInput == null) {
                        continue;
                    }
                    String key = entry.getValue().get((String) row.slot);
                    FieldRow previous = seen.putIfAbsent(key, row);
                    if (previous != null) {
                        FieldRow changed = key.equals(row.slot) ? previous : row;
                        return inputError(changed, changed.keyInput, "Dictionary keys must be unique.");
                    }
                }
            }
            return true;
        }

        private boolean inputError(FieldRow row, GuiElement control, String detail) {
            message = validationPath(row.path) + ": " + detail;
            scroll.reveal(control.getBounds());
            getGuiContext().requestFocus(control);
            return false;
        }

        private void revealInvalidField(String path) {
            while (true) {
                FieldRow hiddenParent = null;
                for (FieldRow row : rows) {
                    String visiblePath = validationPath(row.path);
                    if (visiblePath.equals(path)) {
                        GuiElement control = row.input != null ? row.input
                                : row.keyInput != null ? row.keyInput
                                        : row.slider != null ? row.slider
                                                : row.action != null && row.action.isVisible()
                                                        ? row.action : row.toggle;
                        if (control != null) {
                            getGuiContext().deferAfterRender(() -> {
                                scroll.reveal(control.getBounds());
                                getGuiContext().requestFocus(control);
                            });
                        }
                        return;
                    }
                    if (row.toggle != null && !isExpanded(row)
                            && (path.startsWith(visiblePath + ".") || path.startsWith(visiblePath + "["))
                            && (hiddenParent == null || row.depth > hiddenParent.depth)) {
                        hiddenParent = row;
                    }
                }
                if (hiddenParent == null) {
                    return;
                }
                setExpanded(hiddenParent, true);
                rebuild();
            }
        }

        private String validationPath(String encoded) {
            StringBuilder result = new StringBuilder();
            int index = 0;
            while (index < encoded.length()) {
                char kind = encoded.charAt(index + 1);
                if (kind == 'f') {
                    int colon = encoded.indexOf(':', index + 2);
                    int length = Integer.parseInt(encoded.substring(index + 2, colon));
                    if (result.length() > 0) {
                        result.append('.');
                    }
                    result.append(encoded, colon + 1, colon + 1 + length);
                    index = colon + 1 + length;
                } else {
                    int next = encoded.indexOf('/', index + 2);
                    if (next < 0) {
                        next = encoded.length();
                    }
                    result.append('[').append(Integer.parseInt(encoded.substring(index + 2, next)) + 1)
                            .append(']');
                    index = next;
                }
            }
            return result.toString();
        }

        private void applyInputEdits(Map<FieldRow, Object> edits) {
            for (Map.Entry<FieldRow, Object> entry : edits.entrySet()) {
                setValue(entry.getKey().parent, entry.getKey().slot, entry.getValue());
            }
        }

        private void applyKeyEdits(Map<Map<String, Object>, Map<String, String>> keyEdits) {
            for (Map.Entry<Map<String, Object>, Map<String, String>> entry : keyEdits.entrySet()) {
                Map<String, Object> dictionary = entry.getKey();
                Map<String, String> renames = entry.getValue();
                Map<String, String> pathChanges = new LinkedHashMap<String, String>();
                for (FieldRow row : rows) {
                    if (row.parent == dictionary && row.keyInput != null) {
                        String oldKey = (String) row.slot;
                        String newKey = renames.get(oldKey);
                        if (!oldKey.equals(newKey)) {
                            String suffix = "/f" + oldKey.length() + ":" + oldKey;
                            String parentPath = row.path.substring(0, row.path.length() - suffix.length());
                            pathChanges.put(row.path, fieldPath(parentPath, newKey));
                        }
                    }
                }
                Map<String, Object> reordered = new LinkedHashMap<String, Object>();
                for (Map.Entry<String, Object> item : dictionary.entrySet()) {
                    reordered.put(renames.get(item.getKey()), item.getValue());
                }
                dictionary.clear();
                dictionary.putAll(reordered);
                for (FieldRow row : rows) {
                    if (row.parent == dictionary && row.keyInput != null) {
                        row.slot = renames.get(row.slot);
                    }
                }
                remapPaths(pathChanges);
            }
        }

        private void remapPaths(Map<String, String> changes) {
            if (changes.isEmpty()) {
                return;
            }
            for (FieldRow row : rows) {
                row.path = remappedPath(row.path, changes);
            }
            Set<String> toggled = section().toggledExpansionPaths;
            Set<String> remapped = new HashSet<String>();
            for (String path : toggled) {
                remapped.add(remappedPath(path, changes));
            }
            toggled.clear();
            toggled.addAll(remapped);
        }

        private String remappedPath(String path, Map<String, String> changes) {
            for (Map.Entry<String, String> change : changes.entrySet()) {
                String oldPath = change.getKey();
                if (path.equals(oldPath) || path.startsWith(oldPath + "/")) {
                    return change.getValue() + path.substring(oldPath.length());
                }
            }
            return path;
        }

        private void removeListItemState(List<?> list, int removedIndex) {
            String removedPath = null;
            Map<String, String> shiftedPaths = new LinkedHashMap<String, String>();
            for (FieldRow row : rows) {
                if (row.parent != list || !(row.slot instanceof Integer)) {
                    continue;
                }
                int index = ((Integer) row.slot).intValue();
                String suffix = "/i" + index;
                String parentPath = row.path.substring(0, row.path.length() - suffix.length());
                if (index == removedIndex) {
                    removedPath = row.path;
                } else if (index > removedIndex) {
                    shiftedPaths.put(row.path, itemPath(parentPath, index - 1));
                }
            }
            if (removedPath != null) {
                discardExpansionPath(removedPath);
            }
            remapPaths(shiftedPaths);
        }

        private void discardExpansionPath(String removedPath) {
            String removedPrefix = removedPath + "/";
            section().toggledExpansionPaths
                    .removeIf(path -> path.equals(removedPath) || path.startsWith(removedPrefix));
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
            return measuredHeight;
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            FormColumns columns = columns();
            for (int index = 0; index < rows.size(); index++) {
                FieldRow row = rows.get(index);
                int y = rowTop(index);
                int indentation = indentation(row, columns);
                int controlLeft = columns.controlLeft + indentation;
                int controlWidth = controlWidth(row, columns, indentation);
                if (row.toggle != null) {
                    int toggleRight = row.remove == null
                            ? columns.controlLeft + columns.controlWidth
                            : controlLeft + controlWidth;
                    row.toggle.arrange(context, Rect.fromPositionAndSize(columns.left, y,
                            Math.max(0, toggleRight - columns.left), FIELD_ROW_HEIGHT));
                }
                if (row.input != null) {
                    row.input.arrange(context, Rect.fromPositionAndSize(controlLeft, y + FIELD_CONTROL_TOP_OFFSET,
                            controlWidth, FIELD_INPUT_HEIGHT));
                }
                if (row.keyInput != null) {
                    int keyInset = row.remove == null ? 0 : FIELD_NESTED_INDENT;
                    row.keyInput.arrange(context,
                            Rect.fromPositionAndSize(columns.left + indentation + keyInset,
                                    y + FIELD_CONTROL_TOP_OFFSET, Math.max(0, columns.labelWidth - keyInset),
                                    FIELD_INPUT_HEIGHT));
                }
                if (row.action != null) {
                    if (row.addTopOffset >= 0) {
                        int childIndent = Math.min((row.depth + 2) * FIELD_NESTED_INDENT,
                                Math.max(0, columns.controlWidth - 22 - FIELD_COLUMN_GAP / 2
                                        - FIELD_MIN_CONTROL_WIDTH));
                        boolean dictionary = "dictionary".equals(row.field.collectionKind());
                        int addLeft = dictionary
                                ? columns.left + childIndent + FIELD_NESTED_INDENT
                                : columns.controlLeft + childIndent;
                        int availableWidth = dictionary
                                ? columns.labelWidth - FIELD_NESTED_INDENT
                                : columns.controlWidth - childIndent - 22 - FIELD_COLUMN_GAP / 2;
                        row.action.arrange(context, Rect.fromPositionAndSize(addLeft,
                                getTop() + row.addTopOffset + FIELD_CONTROL_TOP_OFFSET,
                                Math.min(94, Math.max(0, availableWidth)), FIELD_BUTTON_HEIGHT));
                    } else if (row.toggle == null) {
                        row.action.arrange(context, Rect.fromPositionAndSize(controlLeft,
                                y + FIELD_CONTROL_TOP_OFFSET, controlWidth, FIELD_BUTTON_HEIGHT));
                    }
                }
                if (row.slider != null) {
                    row.slider.arrange(context, Rect.fromPositionAndSize(controlLeft, y + FIELD_CONTROL_TOP_OFFSET,
                            controlWidth, FIELD_BUTTON_HEIGHT));
                }
                if (row.remove != null) {
                    row.remove.arrange(context,
                            Rect.fromPositionAndSize(controlLeft + controlWidth + FIELD_COLUMN_GAP / 2,
                                    y + FIELD_CONTROL_TOP_OFFSET, 22, FIELD_BUTTON_HEIGHT));
                }
            }
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            FormColumns columns = columns();
            drawNestedGroupEdges(context, columns);
            for (int index = 0; index < rows.size(); index++) {
                FieldRow row = rows.get(index);
                int y = rowTop(index);
                if (index > 0 && row.depth == 0 && columns.groupWidth > 16) {
                    context.getRenderer().drawHorizontalLine(columns.left + 8, columns.left + columns.groupWidth - 8,
                            y - 2, FIELD_SEPARATOR_COLOR);
                }
                int indentation = indentation(row, columns);
                String visibleLabel = visibleLabel(context, columns, row);
                int labelRight = columns.controlLeft + indentation - FIELD_COLUMN_GAP;
                int controlHeight = row.input == null ? FIELD_BUTTON_HEIGHT : FIELD_INPUT_HEIGHT;
                int labelY = y + FIELD_CONTROL_TOP_OFFSET
                        + (controlHeight - context.getRenderer().lineHeight() + 1) / 2;
                if (row.keyInput == null) {
                    int labelLeft = labelRight - context.getRenderer().textWidth(visibleLabel);
                    int labelColor = context.getRenderer().getTheme().textPrimary;
                    if (row.readOnly) {
                        labelColor = context.getRenderer().getTheme().textMuted;
                    } else if (row.toggle != null) {
                        labelColor = headingHovered(context, row) || row.toggle.isFocused()
                                ? FIELD_HEADING_HOVER_TEXT_COLOR
                                : FIELD_HEADING_TEXT_COLOR;
                    }
                    context.getRenderer().drawText(visibleLabel, labelLeft, labelY, labelColor);
                }
                if (row.keyModified()) {
                    int markerX = row.keyInput.getRight() + FIELD_MARKER_GAP;
                    int markerY = y + FIELD_CONTROL_TOP_OFFSET - context.getRenderer().lineHeight() / 2;
                    context.getRenderer().drawText("*", markerX, markerY, context.getRenderer().getTheme().textWarning);
                }
                if (row.valueModified()
                        && (row.input != null || row.action != null || row.slider != null || row.toggle != null)) {
                    GuiElement markedControl = row.remove != null
                            ? row.remove
                            : row.input != null
                                    ? row.input
                                    : row.toggle != null
                                            ? row.toggle
                                            : row.action != null ? row.action : row.slider;
                    int markerX = markedControl.getRight() + FIELD_MARKER_GAP;
                    int markerY = y + FIELD_CONTROL_TOP_OFFSET - context.getRenderer().lineHeight() / 2;
                    context.getRenderer().drawText("*", markerX, markerY, context.getRenderer().getTheme().textWarning);
                }
                if (!dropdown.isVisible()
                        && Rect.fromPositionAndSize(columns.left, y, columns.groupWidth, FIELD_ROW_HEIGHT)
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
            }
        }

        private boolean headingHovered(GuiContext context, FieldRow row) {
            int mouseX = context.getMouseX();
            int mouseY = context.getMouseY();
            return row.toggle.getBounds().contains(mouseX, mouseY)
                    && context.getRenderer().isPointVisible(mouseX, mouseY)
                    && (row.action == null || !row.action.getBounds().contains(mouseX, mouseY))
                    && (row.keyInput == null || !row.keyInput.getBounds().contains(mouseX, mouseY));
        }

        private void drawNestedGroupEdges(GuiContext context, FormColumns columns) {
            for (int start = 0; start < rows.size(); start++) {
                int depth = rows.get(start).depth;
                FieldRow sectionRow = rows.get(start);
                boolean hasChildren = start + 1 < rows.size() && rows.get(start + 1).depth > depth;
                if (!hasChildren && sectionRow.addTopOffset < 0) {
                    continue;
                }
                int end = start;
                while (end + 1 < rows.size() && rows.get(end + 1).depth > depth) {
                    end++;
                }
                int titleLeft = labelLeft(context, columns, sectionRow);
                int lastDirectChild = start;
                for (int child = start + 1; child <= end; child++) {
                    FieldRow childRow = rows.get(child);
                    if (childRow.depth == depth + 1) {
                        lastDirectChild = child;
                    }
                }
                int edgeX = titleLeft - FIELD_GROUP_TITLE_OFFSET;
                int top = rowTop(start) + FIELD_CONTROL_TOP_OFFSET + FIELD_BUTTON_HEIGHT / 2;
                int bottom = sectionRow.addTopOffset >= 0
                        ? getTop() + sectionRow.addTopOffset + FIELD_CONTROL_TOP_OFFSET + FIELD_BUTTON_HEIGHT / 2
                        : rowTop(lastDirectChild) + FIELD_CONTROL_TOP_OFFSET + FIELD_BUTTON_HEIGHT / 2;
                context.getRenderer().drawVerticalLine(top, bottom + 1, edgeX, FIELD_GROUP_EDGE_COLOR);
                if (titleLeft > edgeX + 2) {
                    context.getRenderer().drawHorizontalLine(edgeX, titleLeft - 2, top, FIELD_GROUP_EDGE_COLOR);
                }
                for (int child = start + 1; child <= end; child++) {
                    FieldRow childRow = rows.get(child);
                    if (childRow.depth != depth + 1) {
                        continue;
                    }
                    int childLeft = labelLeft(context, columns, childRow);
                    int childY = rowTop(child) + FIELD_CONTROL_TOP_OFFSET + FIELD_BUTTON_HEIGHT / 2;
                    if (childLeft > edgeX + 2) {
                        context.getRenderer().drawHorizontalLine(edgeX, childLeft - 2, childY,
                                FIELD_GROUP_BRANCH_COLOR);
                    }
                }
                if (sectionRow.addTopOffset >= 0) {
                    int addLeft = sectionRow.action.getLeft();
                    if (addLeft > edgeX + 2) {
                        context.getRenderer().drawHorizontalLine(edgeX, addLeft - 2, bottom,
                                FIELD_GROUP_BRANCH_COLOR);
                    }
                }
            }
        }

        private int labelLeft(GuiContext context, FormColumns columns, FieldRow row) {
            if (row.keyInput != null) {
                return row.keyInput.getLeft();
            }
            int labelRight = columns.controlLeft + indentation(row, columns) - FIELD_COLUMN_GAP;
            return labelRight - context.getRenderer().textWidth(visibleLabel(context, columns, row));
        }

        private String visibleLabel(GuiContext context, FormColumns columns, FieldRow row) {
            String label = row.slot instanceof Integer
                    ? "Item " + (((Integer) row.slot).intValue() + 1)
                    : row.field.label();
            label += row.readOnly ? " (locked)" : "";
            return context.getRenderer().trimToWidth(label, columns.labelWidth);
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
            int controlWidth = Math.min(FIELD_CONTROL_WIDTH, Math.max(0, halfWidth - halfGap - FIELD_MARKER_WIDTH - 4));
            int left = center - halfGap - labelWidth;
            int controlLeft = center + halfGap;
            int groupWidth = labelWidth + FIELD_COLUMN_GAP + controlWidth + FIELD_MARKER_WIDTH;
            return new FormColumns(left, groupWidth, labelWidth, controlLeft, controlWidth);
        }

        private int indentation(FieldRow row, FormColumns columns) {
            int removeWidth = row.remove == null ? 0 : 22 + FIELD_COLUMN_GAP / 2;
            int available = Math.max(0, columns.controlWidth - removeWidth - FIELD_MIN_CONTROL_WIDTH);
            int requested = row.depth * FIELD_NESTED_INDENT;
            if (row.remove != null) {
                requested += FIELD_NESTED_INDENT;
            }
            return Math.min(requested, available);
        }

        private int controlWidth(FieldRow row, FormColumns columns, int indentation) {
            int removeWidth = row.remove == null ? 0 : 22 + FIELD_COLUMN_GAP / 2;
            return Math.max(0, columns.controlWidth - indentation - removeWidth);
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

    private final class SectionToggle extends GuiElement {
        private final FieldRow row;

        private SectionToggle(FieldRow row) {
            this.row = row;
            setFocusable(true);
        }

        @Override
        protected void renderElement(GuiContext context) {
            int centerX = getRight() - 9;
            int centerY = getTop() + FIELD_CONTROL_TOP_OFFSET + FIELD_BUTTON_HEIGHT / 2;
            int color = isHovered(context) || isFocused()
                    ? context.getRenderer().getTheme().textPrimary
                    : context.getRenderer().getTheme().textMuted;
            boolean collapsed = !form.isExpanded(row);
            for (int offset = -3; offset <= 3; offset++) {
                int y = collapsed ? centerY + 1 - Math.abs(offset) : centerY - 2 + Math.abs(offset);
                context.getRenderer().drawRect(centerX + offset, y, centerX + offset + 1, y + 1, color);
            }
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            boolean clicked = event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0;
            boolean activated = event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()
                    && (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                            || event.getKeyCode() == Keyboard.KEY_SPACE);
            if (!clicked && !activated) {
                return false;
            }
            if (context.getMinecraft() != null) {
                context.getMinecraft().sndManager.playSoundFX("random.click", 1.0F, 1.0F);
            }
            form.toggleSection(row, context);
            return true;
        }
    }

    private final class FieldRow {
        private final ScriptConfigField field;
        private final Object parent;
        private Object slot;
        private final String originalKey;
        private final Object baselineValue;
        private String path;
        private final int depth;
        private int addTopOffset = -1;
        private final boolean readOnly;
        private final boolean structureReadOnly;
        private GuiConfigInput input;
        private GuiConfigInput keyInput;
        private GuiConfigSlider slider;
        private GuiButton action;
        private GuiButton remove;
        private SectionToggle toggle;

        private FieldRow(ScriptConfigField field, Object parent, Object slot, Object baselineValue, String path,
                int depth, boolean removable, boolean readOnly, boolean structureReadOnly, boolean dictionaryEntry) {
            this.field = field;
            this.parent = parent;
            this.slot = slot;
            this.originalKey = dictionaryEntry ? (String) slot : null;
            this.baselineValue = baselineValue;
            this.path = path;
            this.depth = depth;
            this.readOnly = readOnly;
            this.structureReadOnly = structureReadOnly;
            Object currentValue = value(parent, slot);
            if ((currentValue instanceof Map || currentValue instanceof List)
                    && !("text".equals(field.input()) && field.collectionKind() != null)) {
                toggle = form.add(new SectionToggle(this));
            }
            if (dictionaryEntry) {
                keyInput = form.add(new GuiConfigInput(GuiScreenScriptConfig.this, (String) slot));
                keyInput.setEnabled(!readOnly);
            }
            if ("text".equals(field.input()) && field.collectionKind() != null) {
                input = form.add(new GuiConfigInput(GuiScreenScriptConfig.this,
                        ScriptConfigTextValues.format(currentValue, field)));
            } else if (currentValue instanceof Boolean) {
                action = form.add(new GuiButton((Boolean) currentValue ? "True" : "False", () -> {
                    setValue(parent, this.slot, !((Boolean) value(parent, this.slot)).booleanValue());
                    action.setLabel((Boolean) value(parent, this.slot) ? "True" : "False");
                }));
            } else if ("slider".equals(field.input())) {
                slider = form.add(new GuiConfigSlider(field.minimum(), field.maximum(), field.step(),
                        ((Number) currentValue).doubleValue(),
                        nextValue -> setValue(parent, this.slot, Double.valueOf(nextValue))));
            } else if ("choice".equals(field.input())) {
                List<GuiDropdownButton.Option<ScriptConfigChoice>> options = new ArrayList<GuiDropdownButton.Option<ScriptConfigChoice>>();
                for (ScriptConfigChoice choice : field.choices()) {
                    options.add(new GuiDropdownButton.Option<ScriptConfigChoice>(choice, choice.label()));
                }
                GuiDropdownButton<ScriptConfigChoice> button = new GuiDropdownButton<ScriptConfigChoice>(dropdown,
                        options, field.choice(currentValue), selected -> setValue(parent, this.slot, selected.value()));
                button.setBeforeOpen(form::commitInputs);
                action = form.add(button);
            } else if ("cycle".equals(field.input())) {
                action = form.add(new GuiButton("", () -> {
                    Object selected = value(parent, this.slot);
                    List<ScriptConfigChoice> choices = field.choices();
                    int index = -1;
                    for (int candidate = 0; candidate < choices.size(); candidate++) {
                        if (choices.get(candidate).matches(selected)) {
                            index = candidate;
                            break;
                        }
                    }
                    setValue(parent, this.slot, choices.get((index + 1) % choices.size()).value());
                    updateChoiceLabel();
                }));
                updateChoiceLabel();
            } else if (currentValue instanceof String || currentValue instanceof Number) {
                input = form.add(new GuiConfigInput(GuiScreenScriptConfig.this, String.valueOf(currentValue)));
            } else if ("collection".equals(field.input())) {
                action = form.add(new GuiButton("dictionary".equals(field.collectionKind())
                        ? "+ Add Entry" : "+ Add Item", () -> {
                    if (!form.commitInputs()) {
                        return;
                    }
                    if ("list".equals(field.collectionKind())) {
                        @SuppressWarnings("unchecked")
                        List<Object> array = (List<Object>) value(parent, this.slot);
                        if (array.size() >= field.maxItems()) {
                            message = "This list has reached its limit.";
                            return;
                        }
                        array.add(copyValue(field.element().defaultValue()));
                    } else {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dictionary = (Map<String, Object>) value(parent, this.slot);
                        if (dictionary.size() >= field.maxItems()) {
                            message = "This dictionary has reached its limit.";
                            return;
                        }
                        String key = "new_key";
                        for (int index = 2; dictionary.containsKey(key); index++) {
                            key = "new_key_" + index;
                        }
                        dictionary.put(key, copyValue(field.valueField().defaultValue()));
                    }
                    form.setExpanded(this, true);
                    form.rebuild();
                }));
                int size = currentValue instanceof List
                        ? ((List<?>) currentValue).size()
                        : ((Map<?, ?>) currentValue).size();
                action.setEnabled(!readOnly && size < field.maxItems());
            } else if (currentValue instanceof List) {
                action = form.add(new GuiButton("+ Add Item", () -> {
                    if (!form.commitInputs()) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<Object> array = (List<Object>) value(parent, this.slot);
                    if (array.size() >= field.maxItems() || field.element() == null) {
                        message = "This array needs element metadata or has reached its limit.";
                        return;
                    }
                    array.add(copyValue(field.element().defaultValue()));
                    form.setExpanded(this, true);
                    form.rebuild();
                }));
                action.setEnabled(
                        !readOnly && field.element() != null && ((List<?>) currentValue).size() < field.maxItems());
            }
            if (action != null) {
                action.setEnabled(!readOnly && action.isEnabled());
                if (toggle != null) {
                    action.setVisible(form.isExpanded(this));
                }
            }
            if (input != null) {
                input.setEnabled(!readOnly);
            }
            if (slider != null) {
                slider.setEnabled(!readOnly);
            }
            if (removable) {
                remove = form.add(new GuiButton("x", () -> {
                    if (!form.commitInputs()) {
                        return;
                    }
                    if (parent instanceof Map) {
                        form.discardExpansionPath(this.path);
                        ((Map<?, ?>) parent).remove(this.slot);
                    } else {
                        List<?> list = (List<?>) parent;
                        int removedIndex = ((Integer) this.slot).intValue();
                        form.removeListItemState(list, removedIndex);
                        list.remove(removedIndex);
                    }
                    form.rebuild();
                }));
                remove.setEnabled(!readOnly && !structureReadOnly);
            }
        }

        private void updateChoiceLabel() {
            ScriptConfigChoice selected = field.choice(value(parent, slot));
            String label = selected == null ? String.valueOf(value(parent, slot)) : selected.label();
            action.setLabel(label);
        }

        private boolean modified() {
            return keyModified() || valueModified();
        }

        private boolean keyModified() {
            return keyInput != null && !Objects.equals(originalKey, keyInput.value());
        }

        private boolean valueModified() {
            Object currentValue = value(parent, slot);
            if (input != null && field.collectionKind() != null) {
                try {
                    return !Objects.equals(ScriptConfigTextValues.parse(input.value(), field), baselineValue);
                } catch (IllegalArgumentException error) {
                    return true;
                }
            }
            if (currentValue instanceof Map) {
                return ("dictionary".equals(field.collectionKind()) || !form.isExpanded(this))
                        && !Objects.equals(currentValue, baselineValue);
            }
            if (currentValue instanceof List && baselineValue instanceof List) {
                return !form.isExpanded(this)
                        ? !Objects.equals(currentValue, baselineValue)
                        : ((List<?>) currentValue).size() != ((List<?>) baselineValue).size();
            }
            if (input != null && !readOnly) {
                String typed = input.value();
                if (baselineValue instanceof Number) {
                    try {
                        return Double.compare(Double.parseDouble(typed), ((Number) baselineValue).doubleValue()) != 0;
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
        return left instanceof Number && right instanceof Number
                || left != null && right != null && left.getClass() == right.getClass();
    }

    private static String display(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value ? "True" : "False";
        }
        return value instanceof Map ? "{...}" : value instanceof List ? "[...]" : String.valueOf(value);
    }

    private static final class Section {
        private final ScriptConfigRegistration registration;
        private final Set<String> toggledExpansionPaths = new HashSet<String>();
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
