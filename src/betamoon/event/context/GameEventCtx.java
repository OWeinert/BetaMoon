package betamoon.event.context;

import net.minecraft.src.World;

public final class GameEventCtx extends EventContext {
    private final World world;

    public GameEventCtx(net.minecraft.client.Minecraft minecraft, World world) {
        super(minecraft);
        this.world = world;
    }

    public World getWorld() {
        return world;
    }
}
