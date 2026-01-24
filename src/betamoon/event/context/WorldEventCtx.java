package betamoon.event.context;

import net.minecraft.src.World;

public final class WorldEventCtx extends EventContext {
    private final World world;

    public WorldEventCtx(net.minecraft.client.Minecraft mc, World world) {
        super(mc);
        this.world = world;
    }

    public World getWorld() {
        return world;
    }
}
