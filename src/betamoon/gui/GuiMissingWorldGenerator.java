package betamoon.gui;

import betamoon.worldgen.creation.MissingWorldGenerator;

import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiMessage;
import net.minecraft.src.GuiScreen;

/**
 * Missing-generator details followed by a separate, initially unapproved
 * confirmation.
 */
public final class GuiMissingWorldGenerator extends GuiDialogScreen {
    private final Runnable load;

    public GuiMissingWorldGenerator(GuiScreen parent, String worldName, MissingWorldGenerator missing, Runnable load) {
        super(parent, "Missing world generator");
        this.load = load;
        dialog.setPanelSize(480, 290, 120);
        dialog.setScreenMargins(24, 24);
        dialog.setTitleColor(0xffffcc55);
        StringBuilder message = new StringBuilder("World: ").append(worldName).append("\n\nMissing generator: ")
                .append(missing.profileKey);
        for (String requirement : missing.missing) {
            if (!requirement.equals(missing.profileKey)) {
                message.append("\nMissing: ").append(requirement);
            }
        }
        message.append("\n\nLoad Anyway uses Minecraft terrain, biomes and population for this session. "
                + "Your saved generator selection is kept. Restore its script and reopen the world to use it again."
                + "\n\nNew chunks generated now stay saved, even after the original generator returns. "
                + "Terrain borders and biome differences can occur. Consider making a world backup first.");
        dialog.setBody(new GuiMessage(message::toString));
        dialog.addFooterButton(new GuiButton("Cancel", () -> showScreen(parent)));
        dialog.addFooterButton(new GuiButton("Load Anyway", () -> showScreen(new Confirmation(this))));
    }

    private static final class Confirmation extends GuiDialogScreen {
        private boolean accepted;

        private Confirmation(GuiMissingWorldGenerator warning) {
            super(warning, "Load with Minecraft generation?");
            dialog.setPanelSize(340, 180, 100);
            dialog.setScreenMargins(32, 32);
            dialog.setBody(new GuiMessage(() -> "Are you sure? New Minecraft-generated chunks will remain saved "
                    + "after the original generator is restored. The saved generator selection will not be replaced."));
            dialog.addFooterButton(new GuiButton("Cancel", () -> showScreen(warning)));
            dialog.addFooterButton(new GuiButton("Yes", () -> {
                if (!accepted) {
                    accepted = true;
                    warning.load.run();
                }
            }));
        }
    }
}
