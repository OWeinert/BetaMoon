import betamoon.BetaMoonMain;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.src.BaseMod;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.World;

public class mod_BetaMoon extends BaseMod {
    private BetaMoonMain betaMoon;


    public mod_BetaMoon() {
        this.betaMoon = new BetaMoonMain(this);
    }

    @Override
    public void ModsLoaded() {
        this.betaMoon.ModsLoaded();
    }

    @Override
    public boolean OnTickInGUI(Minecraft mc, GuiScreen screen) {
        return this.betaMoon.OnTickInGUI(mc, screen);
    }

    @Override
    public void GenerateSurface(World world, Random random, int chunkX, int chunkZ) {
        this.betaMoon.GenerateSurface(world, random, chunkX, chunkZ);
    }

    @Override
    public void GenerateNether(World world, Random random, int chunkX, int chunkZ) {
        this.betaMoon.GenerateNether(world, random, chunkX, chunkZ);
    }

    @Override
    public String Version() {
        return this.betaMoon.Version();
    }

}