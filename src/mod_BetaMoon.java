import betamoon.LuaModLoader;
import betamoon.registry.WorldGenRegistry;
import java.util.Random;
import net.minecraft.src.BaseMod;
import net.minecraft.src.World;

public class mod_BetaMoon extends BaseMod {
    private static final String VERSION = "0.1.0";
    
    private final LuaModLoader luaModLoader = new LuaModLoader();

    @Override
    public void ModsLoaded() {
        luaModLoader.loadAndRun();
    }

    @Override
    public void GenerateSurface(World world, Random random, int chunkX, int chunkZ) {
        WorldGenRegistry.generateSurface(world, random, chunkX, chunkZ);
    }

    @Override
    public void GenerateNether(World world, Random random, int chunkX, int chunkZ) {
        WorldGenRegistry.generateNether(world, random, chunkX, chunkZ);
    }

    @Override
    public String Version() {
        return VERSION;
    }
}
