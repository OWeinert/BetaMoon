package betamoon.gui.hud;

import betamoon.client.render.FluidCameraRuntime;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.Material;

/**
 * Java-only sampled native effects; no Lua callback or native object escapes.
 */
public final class GuiHudFrameState {
    private final Random random = new Random();
    private final int[] heartOffsets = new int[10];
    private final double[] pickup = new double[9];
    private int health;
    private int previousHealth;
    private boolean flash;
    private int fullAir;
    private int poppingAir;
    private boolean survival;
    private boolean airVisible;

    void capture(Minecraft client, int counter, float partialTicks) {
        EntityPlayerSP actor = client.thePlayer;
        health = actor.health;
        previousHealth = actor.prevHealth;
        flash = actor.heartsLife >= 10 && actor.heartsLife / 3 % 2 == 1;
        random.setSeed((long) (counter * 312871));
        for (int index = 0; index < heartOffsets.length; index++) {
            heartOffsets[index] = health <= 4 ? random.nextInt(2) : 0;
        }
        fullAir = (int) Math.ceil((actor.air - 2) * 10.0 / 300.0);
        poppingAir = (int) Math.ceil(actor.air * 10.0 / 300.0) - fullAir;
        survival = client.playerController != null && client.playerController.shouldDrawHUD();
        airVisible = FluidCameraRuntime.showAirHud(actor, Material.water);
        for (int index = 0; index < pickup.length; index++) {
            pickup[index] = actor.inventory.mainInventory[index] == null
                    ? 0
                    : Math.max(0, Math.min(5, actor.inventory.mainInventory[index].animationsToGo - partialTicks));
        }
    }

    public double pickup(int slot) {
        return slot >= 1 && slot <= 9 ? pickup[slot - 1] : 0;
    }

    public int health() {
        return health;
    }

    public int previousHealth() {
        return previousHealth;
    }

    public boolean flash() {
        return flash;
    }

    public int heartOffset(int index) {
        return heartOffsets[index];
    }

    public int fullAir() {
        return fullAir;
    }

    public int poppingAir() {
        return poppingAir;
    }

    public boolean survival() {
        return survival;
    }

    public boolean airVisible() {
        return airVisible;
    }
}
