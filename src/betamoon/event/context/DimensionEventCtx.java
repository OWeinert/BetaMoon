package betamoon.event.context;

public final class DimensionEventCtx extends EventContext {
    private final int previousDimension;
    private final int currentDimension;

    public DimensionEventCtx(net.minecraft.client.Minecraft minecraft, int previousDimension, int currentDimension) {
        super(minecraft);
        this.previousDimension = previousDimension;
        this.currentDimension = currentDimension;
    }

    public int getPreviousDimension() {
        return previousDimension;
    }

    public int getCurrentDimension() {
        return currentDimension;
    }
}
