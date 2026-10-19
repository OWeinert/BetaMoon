package betamoon.event.context;

import net.minecraft.client.Minecraft;

public final class DimensionEventCtx extends EventContext {
    private final int previousDimension;
    private final int currentDimension;
    private final String previousKey;
    private final String currentKey;
    private final String reason;
    private final String transferId;

    public DimensionEventCtx(Minecraft minecraft, int previousDimension, int currentDimension) {
        this(minecraft, previousDimension, currentDimension, null, null, "native", null);
    }

    public DimensionEventCtx(Minecraft minecraft, int previousDimension, int currentDimension, String previousKey,
            String currentKey, String reason, String transferId) {
        super(minecraft);
        this.previousDimension = previousDimension;
        this.currentDimension = currentDimension;
        this.previousKey = previousKey;
        this.currentKey = currentKey;
        this.reason = reason;
        this.transferId = transferId;
    }

    public int getPreviousDimension() {
        return previousDimension;
    }

    public int getCurrentDimension() {
        return currentDimension;
    }

    public String getPreviousKey() {
        return previousKey;
    }

    public String getCurrentKey() {
        return currentKey;
    }

    public String getReason() {
        return reason;
    }

    public String getTransferId() {
        return transferId;
    }
}
