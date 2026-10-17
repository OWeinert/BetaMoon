package betamoon.gui;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.InputRouter;
import betamoon.client.control.input.InputBinding;
import betamoon.client.control.input.InputMapRegistration;
import betamoon.client.control.input.InputActionDefinition;
import betamoon.client.control.input.InputActivationMode;

import betamoon.gui.widget.GuiTextBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.src.GameSettings;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Searchable Controls tab for named, player-configurable BetaMoon actions. */
final class GuiBetaMoonActions extends GuiScreen {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 78;
    private static final int BUTTON_BASE = 1000;
    private final GuiScreen parent;
    private final GameSettings settings;
    private final InputRouter router;
    private final List<Row> rows = new ArrayList<Row>();
    private final List<Line> lines = new ArrayList<Line>();
    private GuiTextBox searchBox;
    private String message = "";
    private boolean messageIsError;
    private int scroll;
    private boolean resetPending;
    private Row capture;
    private int captureSlot;
    private boolean adding;
    private Row conflict;
    private List<InputBinding> conflictBindings;
    private long seenRevision;

    GuiBetaMoonActions(GuiScreen parent, GameSettings settings) {
        this(parent, settings, ClientInputRuntime.router());
    }

    GuiBetaMoonActions(GuiScreen parent, GameSettings settings, InputRouter router) {
        this.parent = parent;
        this.settings = settings;
        this.router = router;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        String query = searchBox == null ? "" : searchBox.getText();
        boolean focused = searchBox != null && searchBox.isFocused;
        int left = (width - rowWidth()) / 2;
        searchBox = new GuiTextBox(this, fontRenderer, left + 48, 54, rowWidth() - 49, 18, 64);
        searchBox.setText(query);
        searchBox.setFocused(focused);
        rebuildRows();
        rebuildButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private int visibleCount() {
        return Math.max(1, (height - LIST_TOP - 52) / ROW_HEIGHT);
    }

    private int rowWidth() {
        return Math.min(360, width - 8);
    }

    private void rebuildRows() {
        seenRevision = router.revision();
        rows.clear();
        String query = searchBox.getText().toLowerCase(Locale.ENGLISH);
        for (InputMapRegistration map : router.registrations()) {
            if (!map.isActive() || !map.definition().configurable()) {
                continue;
            }
            List<Row> matching = new ArrayList<Row>();
            for (InputActionDefinition action : map.definition().actions()) {
                if (!action.configurable()) {
                    continue;
                }
                String haystack = (map.definition().key() + " " + map.definition().label() + " " + action.name() + " "
                        + action.label()).toLowerCase(Locale.ENGLISH);
                if (haystack.contains(query)) {
                    matching.add(new Row(map, action));
                }
            }
            rows.addAll(matching);
        }
    }

    private void rebuildLines() {
        lines.clear();
        for (Row row : rows) {
            int count = row.map.bindings(row.action.name()).size();
            for (int slot = 0; slot < Math.max(1, count); slot++) {
                lines.add(new Line(row, slot));
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visibleCount())));
    }

    @Override
    public void updateScreen() {
        searchBox.updateCursorCounter();
        if (seenRevision != router.revision()) {
            if (capture != null && !capture.map.isActive()) {
                capture = null;
                message = "Capture cancelled because the script unloaded.";
                messageIsError = true;
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
        rebuildLines();
        controlList.clear();
        controlList.add(new GuiButton(GuiTabbedControls.MINECRAFT_TAB, width / 2 - GuiTabbedControls.TAB_WIDTH - 2,
                GuiTabbedControls.TAB_Y, GuiTabbedControls.TAB_WIDTH, 20, "Minecraft"));
        controlList.add(new GuiButton(GuiTabbedControls.BETAMOON_TAB, width / 2 + 2, GuiTabbedControls.TAB_Y,
                GuiTabbedControls.TAB_WIDTH, 20, "BetaMoon"));
        ((GuiButton) controlList.get(1)).enabled = false;
        controlList.add(new GuiButton(1, width / 2 - 105, height - 27, 100, 20, "Done"));
        controlList.add(
                new GuiButton(2, width / 2 + 5, height - 27, 100, 20, resetPending ? "Confirm reset" : "Reset all"));
        if (capture != null) {
            controlList.add(new GuiButton(7, width / 2 + 4, height - 50, 100, 18, "Bind Escape"));
            controlList.add(new GuiButton(10, width / 2 - 104, height - 50, 100, 18, "Cancel binding"));
        }
        if (conflict != null) {
            controlList.add(new GuiButton(8, width / 2 + 4, height - 50, 100, 18, "Use anyway"));
            controlList.add(new GuiButton(9, width / 2 - 104, height - 50, 100, 18, "Cancel"));
        }
        int start = scroll;
        int end = Math.min(lines.size(), start + visibleCount());
        int rowWidth = rowWidth();
        int left = (width - rowWidth) / 2;
        int extraWidth = rowWidth - 312;
        int actionWidth = 75 + extraWidth / 2;
        int inputWidth = 101 + extraWidth - extraWidth / 2;
        int inputX = left + actionWidth + 4;
        int addX = inputX + inputWidth + 3;
        int removeX = addX + 23;
        int resetX = removeX + 23;
        int modeX = resetX + 25;
        for (int index = start; index < end; index++) {
            Line line = lines.get(index);
            Row row = line.row;
            int visible = index - start;
            int y = LIST_TOP + visible * ROW_HEIGHT;
            int base = BUTTON_BASE + visible * 5;
            List<InputBinding> bindings = row.map.bindings(row.action.name());
            String binding = bindings.isEmpty() ? "Unbound" : bindings.get(line.slot).displayName();
            controlList.add(new GuiButton(base, inputX, y + 1, inputWidth, 20, bindingButtonText(binding, inputWidth)));
            controlList.add(new GuiButton(base + 1, addX, y + 1, 20, 20, "+"));
            GuiButton remove = new GuiButton(base + 2, removeX, y + 1, 20, 20, "-");
            remove.enabled = !bindings.isEmpty();
            controlList.add(remove);
            controlList.add(new GuiButton(base + 3, resetX, y + 1, 22, 20, "R"));
            if (row.action.modes().size() > 1) {
                boolean hold = row.map.mode(row.action.name()) == InputActivationMode.HOLD;
                controlList.add(new GuiButton(base + 4, modeX, y + 1, 58, 20, hold ? "Hold" : "Toggle"));
            }
        }
        if (capture != null || conflict != null) {
            for (Object entry : controlList) {
                GuiButton button = (GuiButton) entry;
                if (button.id != 7 && button.id != 8 && button.id != 9 && button.id != 10) {
                    button.enabled = false;
                }
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (conflict != null && button.id != 8 && button.id != 9) {
            return;
        }
        messageIsError = false;
        if (button.id == GuiTabbedControls.MINECRAFT_TAB) {
            mc.displayGuiScreen(new GuiTabbedControls(parent, settings));
            return;
        }
        if (button.id == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 2) {
            if (!resetPending) {
                resetPending = true;
                message = "Click Confirm reset to restore every BetaMoon action.";
            } else {
                try {
                    for (InputMapRegistration map : router.registrations()) {
                        if (map.isActive() && map.definition().configurable()) {
                            map.resetAllPreferences();
                        }
                    }
                    message = "All BetaMoon actions restored to defaults.";
                } catch (RuntimeException error) {
                    message = error.getMessage();
                    messageIsError = true;
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
        if (button.id == 10 && capture != null) {
            capture = null;
            message = "Binding change cancelled.";
            rebuildButtons();
            return;
        }
        if (button.id == 8 && conflict != null) {
            try {
                conflict.map.setPreferredBindings(conflict.action.name(), conflictBindings, true);
                message = "Binding saved. Both actions now use this input.";
            } catch (RuntimeException error) {
                message = error.getMessage();
                messageIsError = true;
            }
            clearConflict();
            return;
        }
        if (button.id == 9) {
            message = "Binding change cancelled.";
            clearConflict();
            return;
        }
        if (button.id < BUTTON_BASE) {
            return;
        }
        int visible = (button.id - BUTTON_BASE) / 5;
        int operation = (button.id - BUTTON_BASE) % 5;
        int index = scroll + visible;
        if (index < 0 || index >= lines.size()) {
            return;
        }
        Line line = lines.get(index);
        Row row = line.row;
        if (!row.map.isActive()) {
            return;
        }
        List<InputBinding> bindings = new ArrayList<InputBinding>(row.map.bindings(row.action.name()));
        try {
            if (operation == 0) {
                beginCapture(row, line.slot, bindings.isEmpty());
            } else if (operation == 1) {
                beginCapture(row, -1, true);
            } else if (operation == 2 && !bindings.isEmpty()) {
                bindings.remove(line.slot);
                row.map.setPreferredBindings(row.action.name(), bindings, false);
                message = "Binding removed.";
            } else if (operation == 3) {
                row.map.resetPreferredBindings(row.action.name());
                row.map.resetMode(row.action.name());
                message = "Action restored to its default bindings and mode.";
            } else if (operation == 4) {
                row.map.setMode(row.action.name(),
                        row.map.mode(row.action.name()) == InputActivationMode.HOLD
                                ? InputActivationMode.TOGGLE
                                : InputActivationMode.HOLD);
                message = row.map.mode(row.action.name()) == InputActivationMode.HOLD
                        ? "Hold: active while pressed; wheel acts as a pulse."
                        : "Toggle: each press or wheel pulse switches the action.";
            }
            resetPending = false;
        } catch (RuntimeException error) {
            message = error.getMessage();
            messageIsError = true;
        }
        rebuildButtons();
    }

    private void beginCapture(Row row, int slot, boolean add) {
        capture = row;
        captureSlot = slot;
        adding = add;
        messageIsError = false;
        message = "Press a key, click a mouse button, or scroll. Esc cancels.";
    }

    private String bindingButtonText(String label, int width) {
        if (fontRenderer != null) {
            return fitToWidth(label, width - 8);
        }
        return label.length() <= 15 ? label : label.substring(0, 12) + "...";
    }

    private void clearConflict() {
        conflict = null;
        conflictBindings = null;
        rebuildButtons();
    }

    private void finishCapture(InputBinding binding) {
        Row row = capture;
        capture = null;
        messageIsError = false;
        if (row == null || !row.map.isActive()) {
            rebuildButtons();
            return;
        }
        List<InputBinding> bindings = new ArrayList<InputBinding>(row.map.bindings(row.action.name()));
        if (adding || bindings.isEmpty()) {
            bindings.add(binding);
        } else {
            bindings.set(captureSlot, binding);
        }
        try {
            row.map.setPreferredBindings(row.action.name(), bindings, false);
            message = "Saved " + row.action.label() + ": " + binding.displayName() + ".";
        } catch (IllegalArgumentException error) {
            if (error.getMessage() != null && error.getMessage().contains("conflicts with action")) {
                conflict = row;
                conflictBindings = bindings;
                int quote = error.getMessage().indexOf('\'');
                int endQuote = quote < 0 ? -1 : error.getMessage().indexOf('\'', quote + 1);
                String action = endQuote > quote ? error.getMessage().substring(quote + 1, endQuote) : "another action";
                InputActionDefinition other = row.map.definition().action(action);
                message = "Conflict with " + (other == null ? action : other.label()) + ".";
            } else {
                message = error.getMessage();
                messageIsError = true;
            }
        } catch (RuntimeException error) {
            message = error.getMessage();
            messageIsError = true;
        }
        rebuildButtons();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        messageIsError = false;
        if (capture != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                capture = null;
                message = "Binding change cancelled.";
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
                if (captureSlot >= 0 && captureSlot < bindings.size()) {
                    bindings.remove(captureSlot);
                }
                try {
                    row.map.setPreferredBindings(row.action.name(), bindings, false);
                    message = "Binding removed.";
                } catch (RuntimeException error) {
                    message = error.getMessage();
                    messageIsError = true;
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
            } else if (searchBox.isFocused) {
                searchBox.setFocused(false);
            } else {
                mc.displayGuiScreen(parent);
            }
            return;
        }
        if (conflict != null) {
            return;
        }
        if (keyCode == Keyboard.KEY_TAB && !searchBox.isFocused) {
            searchBox.setFocused(true);
            return;
        }
        String previous = searchBox.getText();
        searchBox.textboxKeyTyped(character, keyCode);
        if (!previous.equals(searchBox.getText())) {
            scroll = 0;
            rebuildRows();
            rebuildButtons();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (capture != null && button == 0 && mouseX >= width / 2 - 104 && mouseX < width / 2 + 104
                && mouseY >= height - 50 && mouseY < height - 32) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (capture != null && button >= 0) {
            captureMouseButton(button, heldModifiers());
            return;
        }
        if (conflict == null) {
            searchBox.mouseClicked(mouseX, mouseY, button);
        }
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
        finishCapture(InputBinding.parse(
                modifierPrefix(modifierMask) + "key." + Keyboard.getKeyName(keyCode).toLowerCase(Locale.ENGLISH)));
    }

    void captureMouseButton(int button, int modifierMask) {
        finishCapture(InputBinding.parse(modifierPrefix(modifierMask) + "mouse.button" + (button + 1)));
    }

    void captureWheel(int wheel, int modifierMask) {
        if (wheel == 0) {
            return;
        }
        if (capture != null) {
            finishCapture(InputBinding
                    .parse(modifierPrefix(modifierMask) + (wheel > 0 ? "mouse.wheel_up" : "mouse.wheel_down")));
        } else {
            scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visibleCount()), scroll + (wheel > 0 ? -1 : 1)));
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
        drawCenteredString(fontRenderer, "Controls", width / 2, 10, 0xffffff);
        int rowWidth = rowWidth();
        int left = (width - rowWidth) / 2;
        drawString(fontRenderer, "Search:", left + 2, 59, 0xA0A0A0);
        searchBox.isEnabled = capture == null && conflict == null;
        searchBox.drawTextBox();
        int start = scroll;
        int end = Math.min(lines.size(), start + visibleCount());
        for (int index = start; index < end; index++) {
            Line line = lines.get(index);
            int y = LIST_TOP + (index - start) * ROW_HEIGHT;
            drawRect(left, y, left + rowWidth, y + ROW_HEIGHT - 1, 0x66333333);
            int bindingCount = line.row.map.bindings(line.row.action.name()).size();
            String label = line.row.action.label()
                    + (bindingCount > 1 ? " " + (line.slot + 1) + "/" + bindingCount : "");
            int actionWidth = 75 + (rowWidth - 312) / 2;
            drawString(fontRenderer, fitToWidth(label, actionWidth - 5), left + 3, y + 7, 0xffffff);
        }
        if (rows.isEmpty()) {
            drawCenteredString(fontRenderer, "No active matching actions", width / 2, 105, 0xaaaaaa);
        }
        if (capture != null || conflict != null || messageIsError) {
            drawCenteredString(fontRenderer, fitToWidth(message, width - 12), width / 2, height - 46, 0xffdddd);
        }
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    String searchDisplayText() {
        return searchBox.getText();
    }

    @Override
    public void selectNextField() {
        searchBox.setFocused(!searchBox.isFocused);
    }

    private String fitToWidth(String value, int pixels) {
        String result = value == null ? "" : value;
        if (fontRenderer.getStringWidth(result) <= pixels) {
            return result;
        }
        while (!result.isEmpty() && fontRenderer.getStringWidth(result + "...") > pixels) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private static final class Row {
        private final InputMapRegistration map;
        private final InputActionDefinition action;

        private Row(InputMapRegistration map, InputActionDefinition action) {
            this.map = map;
            this.action = action;
        }
    }

    private static final class Line {
        private final Row row;
        private final int slot;

        private Line(Row row, int slot) {
            this.row = row;
            this.slot = slot;
        }
    }
}
