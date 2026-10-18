package betamoon.gui;

import betamoon.worldgen.creation.GenerationCatalog;
import betamoon.worldgen.creation.GenerationDraft;
import betamoon.worldgen.creation.GenerationSelection;
import betamoon.worldgen.creation.WorldCreationSelection;

import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.widget.GuiTextBox;
import betamoon.worldgen.terrain.GeneratorProfileRegistry;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Random;
import net.minecraft.src.ChatAllowedCharacters;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiCreateWorld;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.PlayerControllerSP;
import net.minecraft.src.StringTranslate;
import org.lwjgl.input.Keyboard;

/** Native-style creation flow. All drafts belong to this screen instance. */
public final class GuiWorldCreation extends GuiScreen {
    private final GuiScreen parent;
    private final Map<String, GenerationDraft> drafts = new LinkedHashMap<String, GenerationDraft>();
    private final List<WorldGeneratorProfile> profiles = new ArrayList<WorldGeneratorProfile>();
    private int selected;
    private GuiTextBox name;
    private GuiTextBox seed;
    private String folder;
    private String problem;
    private boolean starting;
    private int top;
    private GuiButton preset;
    private GuiButton create;
    private int focus;

    public GuiWorldCreation(GuiScreen parent) {
        this.parent = parent;
        profiles.add(null);
        List<WorldGeneratorProfile> registered = new ArrayList<WorldGeneratorProfile>(
                GeneratorProfileRegistry.snapshot().profiles());
        Collections.sort(registered, (a, b) -> {
            int order = a.displayName.compareToIgnoreCase(b.displayName);
            return order == 0 ? a.key.compareTo(b.key) : order;
        });
        profiles.addAll(registered);
    }

    @Override
    public void initGui() {
        String oldName = name == null ? text("selectWorld.newWorld") : name.getText();
        String oldSeed = seed == null ? "" : seed.getText();
        boolean nameFocused = name == null || name.isFocused;
        Keyboard.enableRepeatEvents(true);
        controlList.clear();
        top = Math.max(28, Math.min(47, height - 210));
        int x = width / 2 - 100;
        name = new GuiTextBox(this, fontRenderer, x, top + 12, 200, 20, 32);
        name.setText(oldName);
        name.setFocused(focus == 0 && nameFocused);
        seed = new GuiTextBox(this, fontRenderer, x, top + 68, 200, 20, 64);
        seed.setText(oldSeed);
        seed.setFocused(focus == 1 && !nameFocused);
        preset = new GuiButton(2, x, top + 110, 200, 20, "");
        controlList.add(preset);
        int settingsY = preset.yPosition + 30;
        controlList.add(new GuiButton(3, x, settingsY, 200, 20, "Generation Settings..."));
        int bottom = Math.max(settingsY + 24, height - 52);
        create = new GuiButton(0, x, bottom, text("selectWorld.create"));
        controlList.add(create);
        controlList.add(new GuiButton(1, x, bottom + 24, text("gui.cancel")));
        updateLabels();
    }

    void accept(GenerationDraft draft) {
        drafts.put(draft.catalog.key, draft);
    }

    private GenerationDraft draft() {
        WorldGeneratorProfile profile = profiles.get(selected);
        String key = profile == null ? GenerationCatalog.MINECRAFT : profile.key;
        GenerationDraft draft = drafts.get(key);
        if (draft == null) {
            draft = new GenerationDraft(new GenerationCatalog(profile));
            drafts.put(key, draft);
        }
        return draft;
    }

    private void updateLabels() {
        GenerationDraft draft = draft();
        String marker = draft.modified() ? " \u00a7e*" : "";
        preset.displayString = fit("World Generation: " + draft.catalog.label,
                186 - fontRenderer.getStringWidth(draft.modified() ? " *" : "")) + marker;
        create.enabled = !starting && !name.getText().trim().isEmpty();
        String value = name.getText().trim();
        for (char invalid : ChatAllowedCharacters.allowedCharactersArray) {
            value = value.replace(invalid, '_');
        }
        folder = GuiCreateWorld.generateUnusedFolderName(mc.getSaveLoader(), value.isEmpty() ? "World" : value);
    }

    private String fit(String value, int pixels) {
        return GuiRenderer.trimToWidth(fontRenderer, value, pixels);
    }

    private static String text(String key) {
        return StringTranslate.getInstance().translateKey(key);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled || starting) {
            return;
        }
        problem = null;
        if (button.id == 1) {
            drafts.clear();
            mc.displayGuiScreen(parent);
        } else if (button.id == 2) {
            cycleProfile(1);
        } else if (button.id == 3) {
            mc.displayGuiScreen(new GuiGenerationSettings(this, draft().copy()));
        } else if (button.id == 0) {
            long worldSeed = new Random().nextLong();
            String entered = seed.getText();
            if (!entered.isEmpty()) {
                try {
                    long parsed = Long.parseLong(entered);
                    if (parsed != 0) {
                        worldSeed = parsed;
                    }
                } catch (NumberFormatException exception) {
                    worldSeed = entered.hashCode();
                }
            }
            GenerationSelection selection;
            try {
                selection = GenerationSelection.create(draft(), worldSeed);
                selection.validate(worldSeed);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                problem = exception.getMessage();
                return;
            }
            starting = true;
            String worldName = name.getText();
            String worldFolder = folder;
            drafts.clear();
            mc.playerController = new PlayerControllerSP(mc);
            try (WorldCreationSelection ignored = WorldCreationSelection.begin(selection)) {
                mc.startWorld(worldFolder, worldName, worldSeed);
            }
            if (mc.theWorld != null) {
                mc.displayGuiScreen(null);
            } else {
                starting = false;
            }
        }
    }

    private void cycleProfile(int direction) {
        selected = (selected + direction + profiles.size()) % profiles.size();
        problem = null;
        updateLabels();
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            actionPerformed(new GuiButton(1, 0, 0, ""));
        } else if (key == Keyboard.KEY_TAB) {
            selectNextField();
        } else if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_SPACE)
                && focus >= 2) {
            int[] ids = {2, 3, 0, 1};
            for (Object control : controlList) {
                GuiButton button = (GuiButton) control;
                if (button.id == ids[focus - 2]) {
                    actionPerformed(button);
                    break;
                }
            }
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            actionPerformed(create);
        } else {
            (name.isFocused ? name : seed).textboxKeyTyped(character, key);
            updateLabels();
        }
    }

    @Override
    public void selectNextField() {
        boolean reverse = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        focus = (focus + (reverse ? 5 : 1)) % 6;
        name.setFocused(focus == 0);
        seed.setFocused(focus == 1);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        name.mouseClicked(x, y, button);
        seed.mouseClicked(x, y, button);
        focus = name.isFocused ? 0 : seed.isFocused ? 1 : 2;
        if (button == 1 && !starting && preset.mousePressed(mc, x, y)) {
            mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
            cycleProfile(-1);
            return;
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        name.updateCursorCounter();
        seed.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, text("selectWorld.create"), width / 2, Math.max(8, top - 22), 0xffffff);
        int x = width / 2 - 100;
        drawString(fontRenderer, text("selectWorld.enterName"), x, top, 0xa0a0a0);
        drawString(fontRenderer, fit(text("selectWorld.resultFolder") + " " + folder, 200), x, top + 37, 0xa0a0a0);
        drawString(fontRenderer, text("selectWorld.enterSeed"), x, top + 56, 0xa0a0a0);
        drawString(fontRenderer, fit(text("selectWorld.seedInfo"), 200), x, top + 92, 0xa0a0a0);
        name.drawTextBox();
        seed.drawTextBox();
        if (focus >= 2) {
            int[] ids = {2, 3, 0, 1};
            for (Object control : controlList) {
                GuiButton button = (GuiButton) control;
                if (button.id == ids[focus - 2]) {
                    drawRect(button.xPosition - 1, button.yPosition - 1, button.xPosition + 201, button.yPosition + 21,
                            0xffaaaaaa);
                }
            }
        }
        super.drawScreen(mouseX, mouseY, partialTick);
        if (mouseX >= preset.xPosition && mouseX < preset.xPosition + 200 && mouseY >= preset.yPosition
                && mouseY < preset.yPosition + 20) {
            GenerationDraft draft = draft();
            GenerationTooltips.draw(fontRenderer, width, height, mouseX, mouseY, draft.catalog.label, draft.catalog.key,
                    draft.modified() ? "* Modified generation settings" : null);
        }
        if (problem != null) {
            drawCenteredString(fontRenderer, fit(problem, width - 12), width / 2, top - 10, 0xff7777);
        }
    }
}
