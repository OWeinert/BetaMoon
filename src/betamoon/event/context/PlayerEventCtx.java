package betamoon.event.context;

import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;

public final class PlayerEventCtx extends EventContext {
    private final EntityPlayer player;
    private final World world;

    public PlayerEventCtx(net.minecraft.client.Minecraft minecraft, EntityPlayer player, World world) {
        super(minecraft);
        this.player = player;
        this.world = world;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public World getWorld() {
        return world;
    }
}
