package betamoon.instrumentation.hooks.particle;

import betamoon.client.particles.ClientParticles;
import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.particles.ParticleDefinition;
import betamoon.particles.ParticleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Entity;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;

/**
 * Native callbacks contain no Lua rendering or modifications to vanilla
 * particles.
 */
public final class ParticleCallbacks {
    private ParticleCallbacks() {
    }

    public static int pass() {
        return 0;
    }

    public static void afterSpawn() {
        // The capture path owns custom requests; native returns require no additional
        // work.
    }

    public static void tick() {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft != null && ClientParticles.route(minecraft.theWorld) == null) {
            ClientParticles.runtime().tick(minecraft.isGamePaused);
            ClientParticles.renderer().prune(ClientParticles.runtime().sprites());
        }
    }

    public static void clear(World world) {
        ClientParticles.clear(world);
    }

    public static void render(Entity camera, float partialTick) {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft != null) {
            ClientParticles.renderer().render(ClientParticles.runtime().sprites(), camera, partialTick,
                    minecraft.renderEngine, ClientParticles.runtime());
        }
    }

    public static int spawn(String name, double x, double y, double z, double vx, double vy, double vz) {
        if (name == null || name.indexOf(':') < 0) {
            return 0;
        }
        ParticleDefinition definition;
        try {
            definition = ParticleRegistry.particle(ContentKey.parseForType(name, ContentType.PARTICLE));
        } catch (IllegalArgumentException error) {
            return 0;
        }
        if (definition == null) {
            return 0;
        }
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft != null && ClientParticles.route(minecraft.theWorld) == null) {
            ClientParticles.runtime().spawn(definition, definition.owner, x, y, z, vx, vy, vz, 1,
                    new double[]{1, 1, 1, 1}, System.nanoTime(), 16);
        }
        return 1;
    }
}
