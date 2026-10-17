package betamoon.gui;

import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.widget.GuiCheckbox;
import betamoon.gui.widget.GuiMinecraftButton;
import betamoon.gui.widget.GuiTextBox;
import betamoon.worldgen.creation.GenerationDraft;
import betamoon.worldgen.creation.GenerationOption;
import betamoon.worldgen.creation.GenerationSelection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Searchable generation hierarchy with separate checkboxes and expandable
 * labels.
 */
public final class GuiGenerationSettings extends GuiScreen {
    private final GuiWorldCreation parent;
    private final GenerationDraft draft;
    private final GenerationSettingsTree tree;
    private final List<GenerationSettingsTree.Row> filtered = new ArrayList<GenerationSettingsTree.Row>();
    private GuiTextBox search;
    private int offset;
    private int focused;
    private int keyboardFocus;
    private int rows;
    private int left;
    private int listWidth;
    private String problem;

    public GuiGenerationSettings(GuiWorldCreation parent, GenerationDraft draft) {
        this.parent = parent;
        this.draft = draft;
        tree = new GenerationSettingsTree(draft.catalog);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        String query = search == null ? "" : search.getText();
        listWidth = Math.min(420, width - 32);
        left = (width - listWidth) / 2;
        search = new GuiTextBox(this, fontRenderer, left + 46, 31, listWidth - 46, 20, 128);
        search.setText(query);
        search.setFocused(keyboardFocus == 0);
        rows = Math.max(1, (height - 106) / 22);
        filter(false);
    }

    private void filter(boolean reset) {
        filtered.clear();
        filtered.addAll(tree.rows(search.getText()));
        if (reset) {
            offset = 0;
            focused = 0;
        }
        refresh();
    }

    private void refresh() {
        offset = Math.max(0, Math.min(offset, Math.max(0, filtered.size() - rows)));
        focused = Math.max(0, Math.min(focused, filtered.size() - 1));
        controlList.clear();
        for (int i = 0; i < rows && i + offset < filtered.size(); i++) {
            GenerationSettingsTree.Row row = filtered.get(i + offset);
            int x = left + Math.min(row.depth, 5) * 12;
            int y = 60 + i * 22;
            GenerationOption option = row.entry.option;
            if (option != null) {
                controlList.add(new GuiCheckbox(100 + i * 2, x, y, draft.selected(option.key), draft.editable(option)));
            }
            controlList.add(new EntryButton(101 + i * 2, x + 24, y, listWidth - 34 - (x - left), row));
        }
        int buttonWidth = (listWidth - 8) / 3;
        controlList.add(new GuiMinecraftButton(0, left, height - 28, buttonWidth, 20, "Done"));
        controlList.add(new GuiMinecraftButton(1, left + buttonWidth + 4, height - 28, buttonWidth, 20, "Cancel"));
        controlList.add(new GuiMinecraftButton(3, left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20,
                "Reset to Preset"));
    }

    private String fit(String value, int pixels) {
        return GuiRenderer.trimToWidth(fontRenderer, value, pixels);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        problem = null;
        if (button.id == 0) {
            try {
                GenerationSelection.create(draft, 0);
            } catch (IllegalArgumentException exception) {
                problem = exception.getMessage();
                return;
            }
            parent.accept(draft);
            mc.displayGuiScreen(parent);
        } else if (button.id == 1) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 3) {
            draft.reset();
            refresh();
        } else if (button.id >= 100) {
            int index = (button.id - 100) / 2 + offset;
            if (index >= filtered.size()) {
                return;
            }
            focused = index;
            GenerationSettingsTree.Entry entry = filtered.get(index).entry;
            if (button.id % 2 == 0 && entry.option != null) {
                draft.toggle(entry.option.key);
                refresh();
            } else {
                tree.toggle(entry);
                filter(false);
            }
        }

    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (key == Keyboard.KEY_TAB) {
            boolean reverse = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            keyboardFocus = (keyboardFocus + (reverse ? 4 : 1)) % 5;
            search.setFocused(keyboardFocus == 0);
        } else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_PRIOR
                || key == Keyboard.KEY_NEXT || key == Keyboard.KEY_HOME || key == Keyboard.KEY_END) {
            search.setFocused(false);
            keyboardFocus = 1;
            if (key == Keyboard.KEY_HOME) {
                focused = 0;
            } else if (key == Keyboard.KEY_END) {
                focused = filtered.size() - 1;
            } else {
                int step = key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT ? rows : 1;
                focused += key == Keyboard.KEY_UP || key == Keyboard.KEY_PRIOR ? -step : step;
            }
            focused = Math.max(0, Math.min(focused, filtered.size() - 1));
            if (focused < offset) {
                offset = focused;
            } else if (focused >= offset + rows) {
                offset = focused - rows + 1;
            }
            refresh();
        } else if (!search.isFocused && (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) && keyboardFocus == 1
                && !filtered.isEmpty()) {
            GenerationSettingsTree.Row row = filtered.get(focused);
            if (row.open != (key == Keyboard.KEY_RIGHT) && !row.entry.children.isEmpty()
                    && search.getText().trim().isEmpty()) {
                tree.toggle(row.entry);
                filter(false);
            }
        } else if (!search.isFocused && (key == Keyboard.KEY_SPACE || key == Keyboard.KEY_RETURN)) {
            if (keyboardFocus != 1) {
                int[] ids = {-1, -1, 0, 1, 3};
                for (Object control : controlList) {
                    GuiButton button = (GuiButton) control;
                    if (button.id == ids[keyboardFocus]) {
                        actionPerformed(button);
                        break;
                    }
                }
            } else if (!filtered.isEmpty()) {
                GenerationSettingsTree.Entry entry = filtered.get(focused).entry;
                if (key == Keyboard.KEY_SPACE && entry.option != null) {
                    draft.toggle(entry.option.key);
                    refresh();
                } else {
                    tree.toggle(entry);
                    filter(false);
                }
            }
        } else if (search.isFocused) {
            search.textboxKeyTyped(character, key);
            filter(true);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        search.mouseClicked(x, y, button);
        keyboardFocus = search.isFocused ? 0 : 1;
        if (button == 0 && x >= left + listWidth - 8 && x < left + listWidth && y >= 60 && y < 60 + rows * 22) {
            offset = (int) ((y - 60) / (double) (rows * 22) * Math.max(0, filtered.size() - rows));
            refresh();
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            offset += wheel > 0 ? -3 : 3;
            refresh();
        }
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, fit("Generation Settings: " + draft.catalog.label, width - 16), width / 2, 12,
                0xffffff);
        drawString(fontRenderer, "Search", left, 37, 0xa0a0a0);
        search.drawTextBox();
        drawRect(left - 2, 58, left + listWidth + 2, 60 + rows * 22, 0x60000000);
        if (keyboardFocus == 1 && !filtered.isEmpty() && focused >= offset && focused < offset + rows) {
            int y = 60 + (focused - offset) * 22;
            drawRect(left - 2, y - 1, left + listWidth - 8, y + 21, 0xffaaaaaa);
            drawRect(left, y, left + listWidth - 10, y + 20, 0xff202020);
        }
        if (keyboardFocus >= 2) {
            int[] ids = {-1, -1, 0, 1, 3};
            for (Object control : controlList) {
                GuiButton button = (GuiButton) control;
                if (button.id == ids[keyboardFocus]) {
                    drawRect(button.xPosition - 1, button.yPosition - 1, button.xPosition + (listWidth - 8) / 3 + 1,
                            button.yPosition + 21, 0xffaaaaaa);
                }
            }
        }
        super.drawScreen(mouseX, mouseY, partialTick);
        if (filtered.size() > rows) {
            int track = rows * 22;
            int thumb = Math.max(12, track * rows / filtered.size());
            int y = 60 + (track - thumb) * offset / (filtered.size() - rows);
            drawRect(left + listWidth - 6, 60, left + listWidth - 1, 60 + track, 0xff000000);
            drawRect(left + listWidth - 6, y, left + listWidth - 1, y + thumb, 0xff888888);
        }
        if (filtered.isEmpty()) {
            drawCenteredString(fontRenderer, "No matching settings", left + listWidth / 2, 66, 0xa0a0a0);
        }
        int hovered = (mouseY - 60) / 22 + offset;
        if (mouseY >= 60 && mouseY < 60 + rows * 22 && mouseX >= left && mouseX < left + listWidth
                && hovered < filtered.size()) {
            GenerationOption option = filtered.get(hovered).entry.option;
            if (option != null && option.key.indexOf(':') >= 0) {
                String displayKey = option.key.substring(option.key.indexOf('/') + 1);
                GenerationTooltips.draw(fontRenderer, width, height, mouseX, mouseY, option.label, displayKey,
                        option.lockedReason != null
                                ? option.lockedReason
                                : !draft.editable(option) ? "Disabled by parent" : null);
            }
        }
        if (problem != null) {
            drawCenteredString(fontRenderer, fit(problem, width - 16), width / 2, height - 42, 0xff7777);
        }
    }

    private final class EntryButton extends GuiButton {
        private final GenerationSettingsTree.Row row;

        private EntryButton(int id, int x, int y, int width, GenerationSettingsTree.Row row) {
            super(id, x, y, width, 20, row.entry.label);
            this.row = row;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            boolean parentEntry = !row.entry.children.isEmpty();
            boolean hover = mouseX >= xPosition && mouseX < xPosition + width && mouseY >= yPosition
                    && mouseY < yPosition + height;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height,
                    hover && parentEntry ? 0xb0555555 : row.entry.option == null ? 0xc0383838 : 0x80222222);
            GenerationOption option = row.entry.option;
            int color = option != null && !draft.editable(option)
                    ? 0x777777
                    : hover && parentEntry ? 0xffffa0 : 0xe0e0e0;
            drawCenteredString(fontRenderer, fit(displayString, width - 32), xPosition + width / 2, yPosition + 6,
                    color);
            if (parentEntry) {
                int x = xPosition + width - 14;
                for (int i = 0; i < 4; i++) {
                    int dy = row.open ? i : 3 - i;
                    drawRect(x + i, yPosition + 8 + dy, x + i + 1, yPosition + 9 + dy, 0xff000000 | color);
                    drawRect(x + 6 - i, yPosition + 8 + dy, x + 7 - i, yPosition + 9 + dy, 0xff000000 | color);
                }
            }
        }

        @Override
        public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
            return !row.entry.children.isEmpty() && super.mousePressed(minecraft, mouseX, mouseY);
        }
    }

}
