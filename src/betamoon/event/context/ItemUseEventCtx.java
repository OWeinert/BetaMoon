package betamoon.event.context;

public final class ItemUseEventCtx extends EventContext {
    private final net.minecraft.src.ItemStack stack;

    public ItemUseEventCtx(net.minecraft.client.Minecraft minecraft, net.minecraft.src.ItemStack stack) {
        super(minecraft);
        this.stack = stack;
    }

    public net.minecraft.src.ItemStack getItemStack() {
        return stack;
    }
}
