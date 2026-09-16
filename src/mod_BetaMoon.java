import betamoon.BetaMoonMain;
import betamoon.luaapi.block.BlockModelRegistry;
import net.minecraft.src.Block;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.RenderBlocks;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.src.BaseMod;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.KeyBinding;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;
import org.lwjgl.input.Keyboard;

public class mod_BetaMoon extends BaseMod {
    private static final KeyBinding RELOAD_SCRIPTS_KEY = new KeyBinding("key.betamoon.reload_scripts", Keyboard.KEY_R);
    private final BetaMoonMain betaMoon;

    public mod_BetaMoon() {
        this.betaMoon = BetaMoonMain.create(this);

        // register reload keybind
        ModLoader.AddLocalization("key.betamoon.reload_scripts", "Reload BetaMoon Scripts");
        ModLoader.RegisterKey(this, RELOAD_SCRIPTS_KEY, false);
    }

    @Override
    public void KeyboardEvent(KeyBinding key) {
        if (key == RELOAD_SCRIPTS_KEY) {
            this.betaMoon.handleReloadHotkey(key);
        }
    }

    @Override
    public void ModsLoaded() {
        this.betaMoon.modsLoaded();
    }

    @Override
    public boolean OnTickInGUI(Minecraft mc, GuiScreen screen) {
        return this.betaMoon.onTickInGUI(mc, screen);
    }

    @Override
    public boolean OnTickInGame(Minecraft mc) {
        return this.betaMoon.onTickInGame(mc);
    }

    @Override
    public void GenerateSurface(World world, Random random, int chunkX, int chunkZ) {
        this.betaMoon.generateSurface(world, random, chunkX, chunkZ);
    }

    @Override
    public void GenerateNether(World world, Random random, int chunkX, int chunkZ) {
        this.betaMoon.generateNether(world, random, chunkX, chunkZ);
    }

    @Override
    public void RenderInvBlock(RenderBlocks renderer, Block block, int metadata, int type) {
        BlockModelRegistry.renderOrdinaryInventory(renderer, block, metadata);
    }

    @Override
    public boolean RenderWorldBlock(RenderBlocks renderer, IBlockAccess world, int x, int y, int z, Block block,
            int type) {
        return BlockModelRegistry.renderWorld(renderer, world, x, y, z, block);
    }

    @Override
    public String Version() {
        return this.betaMoon.version();
    }

}
