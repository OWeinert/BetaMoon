package betamoon.event.context;

import net.minecraft.client.Minecraft;

public abstract class EventContext {
    private final Minecraft minecraft;

    protected EventContext(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public Minecraft getMinecraft() {
        return minecraft;
    }
}
