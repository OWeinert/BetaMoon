package betamoon.client.control.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Scrollable native-widget editor for live, configurable input actions. */
final class GuiBetaMoonActions extends GuiScreen {
    private static final int ROW_HEIGHT = 25;
    private final GuiScreen parent;
    private final InputRouter router;
    private final List<Row> rows = new ArrayList<Row>();
    private final Map<String, Integer> selectedSlots = new HashMap<String, Integer>();
    private String search = "";
    private String message = "";
    private int scroll;
    private boolean searchFocused;
    private boolean resetPending;
    private Row capture;
    private boolean adding;
    private Row conflict;
    private List<InputBinding> conflictBindings;
    private long seenRevision;

    GuiBetaMoonActions(GuiScreen parent) {
        this(parent, ClientInputRuntime.router());
    }

    GuiBetaMoonActions(GuiScreen parent, InputRouter router) {
        this.parent = parent;
        this.router = router;
    }

    @Override
    public void initGui() {
        rebuildRows();
        rebuildButtons();
    }

    private int visibleCount() {
        return Math.max(1, (height - 116) / rowHeight());
    }

    private int rowHeight() {
        return width < 400 ? 37 : ROW_HEIGHT;
    }

    private void rebuildRows() {
        seenRevision = router.revision();
        rows.clear();
        String query = search.toLowerCase(Locale.ENGLISH);
        for (InputMapRegistration map : router.registrations()) {
            if (!map.isActive() || !map.definition().configurable()) {
                continue;
            }
            List<Row> matching = new ArrayList<Row>();
            for (InputActionDefinition action : map.definition().actions()) {
                if (!action.configurable()) {
                    continue;
                }
                String haystack = (map.definition().key() + " " + map.definition().label() + " "
                        + action.name() + " " + action.label()).toLowerCase(Locale.ENGLISH);
                if (haystack.contains(query)) {
                    matching.add(new Row(map, action));
                }
            }
            if (!matching.isEmpty()) {
                rows.add(new Row(map, null));
                rows.addAll(matching);
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleCount())));
    }

    @Override
    public void updateScreen() {
        if (seenRevision != router.revision()) {
            if (capture != null && !capture.map.isActive()) {
                capture = null;
                message = "Capture cancelled because the script unloaded.";
            }
            if (conflict != null && !conflict.map.isActive()) {
                conflict = null;
                conflictBindings = null;
            }
            rebuildRows();
            rebuildButtons();
        }
    }

    private void rebuildButtons() {
        controlList.clear();
        controlList.add(new GuiButton(1, width / 2 - 155, height - 27, 95, 20, "Done"));
        controlList.add(new GuiButton(2, width / 2 - 55, height - 27, 110, 20,
                resetPending ? "Confirm reset" : "Reset all"));
        controlList.add(new GuiButton(3, width / 2 + 60, height - 27, 95, 20, "Back"));
        if (capture != null) {
            controlList.add(new GuiButton(7, width / 2 - 40, height - 51, 80, 18, "Bind Escape"));
        }
        if (conflict != null) {
            controlList.add(new GuiButton(8, width / 2 + 43, height - 51, 85, 18, "Keep both"));
            controlList.add(new GuiButton(9, width / 2 - 128, height - 51, 85, 18, "Cancel"));
        }
        int start = Math.min(scroll, Math.max(0, rows.size() - visibleCount()));
        int end = Math.min(rows.size(), start + visibleCount());
        for (int index = start; index < end; index++) {
            Row row = rows.get(index);
            if (row.action == null) {
                continue;
            }
            int visible = index - start;
            boolean compact = width < 400;
            int y = 59 + visible * rowHeight() + (compact ? 13 : 0);
            int x = width / 2 - (compact ? 145 : 156);
            List<InputBinding> bindings = row.map.bindings(row.action.name());
            int slot = slot(row, bindings.size());
            String text = bindings.isEmpty() ? "Unbound" : (slot + 1) + "/" + bindings.size()
                    + " " + bindings.get(slot);
            int controls = x + (compact ? 0 : 92);
            controlList.add(new GuiButton(1000 + visible * 6, controls, y, 90, 20, shorten(text, 14)));
            controlList.add(new GuiButton(1001 + visible * 6, controls + 92, y, 22, 20, ">"));
            controlList.add(new GuiButton(1002 + visible * 6, controls + 116, y, 22, 20, "+"));
            controlList.add(new GuiButton(1003 + visible * 6, controls + 140, y, 22, 20, "-"));
            if (row.action.modes().size() > 1) {
                controlList.add(new GuiButton(1004 + visible * 6, controls + 164, y, 53, 20,
                        row.map.mode(row.action.name()).luaName()));
            }
            controlList.add(new GuiButton(1005 + visible * 6, controls + 219, y, 43, 20, "Reset"));
        }
    }

    private int slot(Row row, int count) {
        int selected = selectedSlots.containsKey(row.identity()) ? selectedSlots.get(row.identity()) : 0;
        selected = Math.max(0, Math.min(selected, Math.max(0, count - 1)));
        selectedSlots.put(row.identity(), selected);
        return selected;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (conflict != null && button.id != 8 && button.id != 9 && button.id != 1 && button.id != 3) {
            return;
        }
        if (button.id == 1 || button.id == 3) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 2) {
            if (!resetPending) {
                resetPending = true;
                message = "Click Confirm reset to restore all BetaMoon actions.";
            } else {
                try {
                    for (InputMapRegistration map : router.registrations()) {
                        if (map.isActive() && map.definition().configurable()) {
                            map.resetAllPreferences();
                        }
                    }
                    message = "BetaMoon action preferences reset.";
                } catch (RuntimeException error) {
                    message = error.getMessage();
                }
                resetPending = false;
            }
            rebuildButtons();
            return;
        }
        if (button.id == 7 && capture != null) {
            finishCapture(InputBinding.parse("key.escape"));
            return;
        }
        if (button.id == 8 && conflict != null) {
            try {
                conflict.map.setPreferredBindings(conflict.action.name(), conflictBindings, true);
                message = "Binding saved with conflict.";
            } catch (RuntimeException error) {
                message = error.getMessage();
            }
            clearConflict();
            return;
        }
        if (button.id == 9) {
            clearConflict();
            return;
        }
        if (button.id < 1000) {
            return;
        }
        int visible = (button.id - 1000) / 6;
        int operation = (button.id - 1000) % 6;
        int index = scroll + visible;
        if (index < 0 || index >= rows.size()) {
            return;
        }
        Row row = rows.get(index);
        if (row.action == null || !row.map.isActive()) {
            return;
        }
        List<InputBinding> bindings = new ArrayList<InputBinding>(row.map.bindings(row.action.name()));
        int slot = slot(row, bindings.size());
        try {
            if (operation == 0 || operation == 2) {
                capture = row;
                adding = operation == 2 || bindings.isEmpty();
                message = "Press a key, mouse button, or wheel. Esc cancels; Delete clears.";
            } else if (operation == 1) {
                selectedSlots.put(row.identity(), bindings.isEmpty() ? 0 : (slot + 1) % bindings.size());
            } else if (operation == 3) {
                if (!bindings.isEmpty()) {
                    bindings.remove(slot);
                    row.map.setPreferredBindings(row.action.name(), bindings, false);
                }
            } else if (operation == 4) {
                row.map.setMode(row.action.name(), row.map.mode(row.action.name()) == InputActivationMode.HOLD
                        ? InputActivationMode.TOGGLE : InputActivationMode.HOLD);
            } else if (operation == 5) {
                row.map.resetPreferredBindings(row.action.name());
                row.map.resetMode(row.action.name());
            }
            resetPending = false;
        } catch (RuntimeException error) {
            message = error.getMessage();
        }
        rebuildButtons();
    }

    private void clearConflict() {
        conflict = null;
        conflictBindings = null;
        rebuildButtons();
    }

    private void finishCapture(InputBinding binding) {
        Row row = capture;
        capture = null;
        if (row == null || !row.map.isActive()) {
            rebuildButtons();
            return;
        }
        List<InputBinding> bindings = new ArrayList<InputBinding>(row.map.bindings(row.action.name()));
        if (adding) {
            bindings.add(binding);
        } else if (bindings.isEmpty()) {
            bindings.add(binding);
        } else {
            bindings.set(slot(row, bindings.size()), binding);
        }
        try {
            row.map.setPreferredBindings(row.action.name(), bindings, false);
            message = "Saved " + row.action.label() + ".";
        } catch (IllegalArgumentException error) {
            if (error.getMessage() != null && error.getMessage().contains("conflicts with action")) {
                conflict = row;
                conflictBindings = bindings;
                message = shorten(error.getMessage(), 75);
            } else {
                message = error.getMessage();
            }
        } catch (RuntimeException error) {
            message = error.getMessage();
        }
        rebuildButtons();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (capture != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                capture = null;
                message = "Capture cancelled.";
                rebuildButtons();
            } else if (keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
                Row row = capture;
                capture = null;
                if (adding) {
                    message = "Add binding cancelled.";
                    rebuildButtons();
                    return;
                }
                List<InputBinding> bindings = new ArrayList<InputBinding>(row.map.bindings(row.action.name()));
                if (!bindings.isEmpty()) {
                    bindings.remove(slot(row, bindings.size()));
                }
                try {
                    row.map.setPreferredBindings(row.action.name(), bindings, false);
                    message = "Binding cleared.";
                } catch (RuntimeException error) {
                    message = error.getMessage();
                }
                rebuildButtons();
            } else if (keyCode != Keyboard.KEY_NONE) {
                captureKey(keyCode, heldModifiers());
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (conflict != null) {
                clearConflict();
            } else if (searchFocused) {
                searchFocused = false;
            } else {
                mc.displayGuiScreen(parent);
            }
            return;
        }
        if (searchFocused) {
            if (keyCode == Keyboard.KEY_BACK && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            } else if (character >= 32 && character != 127 && search.length() < 64) {
                search += character;
            }
            scroll = 0;
            rebuildRows();
            rebuildButtons();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (capture != null && button == 0 && mouseX >= width / 2 - 40 && mouseX < width / 2 + 40
                && mouseY >= height - 51 && mouseY < height - 33) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (capture != null && button >= 0) {
            captureMouseButton(button, heldModifiers());
            return;
        }
        searchFocused = mouseX >= width / 2 - 156 && mouseX <= width / 2 + 156 && mouseY >= 31 && mouseY < 51;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (capture != null && wheel != 0) {
            captureWheel(wheel, heldModifiers());
            return;
        }
        super.handleMouseInput();
        if (wheel != 0) {
            captureWheel(wheel, heldModifiers());
        }
    }

    void captureKey(int keyCode, int modifierMask) {
        finishCapture(InputBinding.parse(modifierPrefix(modifierMask) + "key."
                + Keyboard.getKeyName(keyCode).toLowerCase(Locale.ENGLISH)));
    }

    void captureMouseButton(int button, int modifierMask) {
        finishCapture(InputBinding.parse(modifierPrefix(modifierMask) + "mouse.button" + (button + 1)));
    }

    void captureWheel(int wheel, int modifierMask) {
        if (wheel == 0) {
            return;
        }
        if (capture != null) {
            finishCapture(InputBinding.parse(modifierPrefix(modifierMask)
                    + (wheel > 0 ? "mouse.wheel_up" : "mouse.wheel_down")));
        } else {
            scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleCount()), scroll + (wheel > 0 ? -2 : 2)));
            rebuildButtons();
        }
    }

    private static int heldModifiers() {
        if (!Keyboard.isCreated()) {
            return 0;
        }
        int modifiers = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            modifiers |= InputBinding.CONTROL;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            modifiers |= InputBinding.SHIFT;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            modifiers |= InputBinding.ALT;
        }
        return modifiers;
    }

    private static String modifierPrefix(int modifiers) {
        String result = "";
        if ((modifiers & InputBinding.CONTROL) != 0) {
            result += "ctrl+";
        }
        if ((modifiers & InputBinding.SHIFT) != 0) {
            result += "shift+";
        }
        if ((modifiers & InputBinding.ALT) != 0) {
            result += "alt+";
        }
        return result;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "BetaMoon Actions", width / 2, 12, 0xffffff);
        int left = width / 2 - 156;
        drawRect(left, 31, left + 312, 51, searchFocused ? 0xff333e5b : 0xff222222);
        drawString(fontRenderer, search.isEmpty() ? "Search maps and actions..." : search, left + 5, 37,
                search.isEmpty() ? 0x999999 : 0xffffff);
        int start = Math.min(scroll, Math.max(0, rows.size() - visibleCount()));
        int end = Math.min(rows.size(), start + visibleCount());
        for (int index = start; index < end; index++) {
            Row row = rows.get(index);
            boolean compact = width < 400;
            int y = 59 + (index - start) * rowHeight();
            if (row.action == null) {
                drawString(fontRenderer, shorten(row.map.definition().key().namespace() + " / "
                        + row.map.definition().label(), 47), left + 3, y + 5, 0xffdd88);
            } else {
                drawString(fontRenderer, shorten(row.action.label(), compact ? 40 : 13), left + 3,
                        y + (compact ? 1 : 6), 0xffffff);
            }
        }
        if (rows.isEmpty()) {
            drawCenteredString(fontRenderer, "No active matching actions", width / 2, 85, 0xaaaaaa);
        }
        drawCenteredString(fontRenderer, shorten(message, 72), width / 2, height - 46, 0xffdddd);
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    private static String shorten(String value, int limit) {
        return value == null ? "" : value.length() <= limit ? value : value.substring(0, Math.max(0, limit - 3))
                + "...";
    }

    private static final class Row {
        private final InputMapRegistration map;
        private final InputActionDefinition action;

        private Row(InputMapRegistration map, InputActionDefinition action) {
            this.map = map;
            this.action = action;
        }

        private String identity() {
            return map.definition().key() + "#" + action.name();
        }
    }
}
