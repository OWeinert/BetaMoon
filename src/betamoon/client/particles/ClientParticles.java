package betamoon.client.particles;

import betamoon.particles.ParticleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;

/**
 * Client presentation boundary shared by Lua requests and native particle
 * hooks.
 */
public final class ClientParticles {
    private static final ParticleRuntime RUNTIME = new ParticleRuntime();
    private static final ParticleRenderer RENDERER = new ParticleRenderer();

    static {
        ParticleRegistry.setCleanup(owner -> {
            RUNTIME.removeOwner(owner);
            RENDERER.prune(RUNTIME.sprites());
        });
    }

    private ClientParticles() {
    }

    public static ParticleRuntime runtime() {
        return RUNTIME;
    }

    public static ParticleRenderer renderer() {
        return RENDERER;
    }

    public static String route(World world) {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft == null) {
            return "client_unavailable";
        }
        if (world == null || minecraft.theWorld != world) {
            return "world_unavailable";
        }
        if (RUNTIME.world() != world) {
            clear(world);
        }
        RUNTIME.camera(minecraft.renderViewEntity == null ? minecraft.thePlayer : minecraft.renderViewEntity);
        return null;
    }

    public static void clear(World world) {
        RUNTIME.clear(world);
        RENDERER.clear();
    }
}
