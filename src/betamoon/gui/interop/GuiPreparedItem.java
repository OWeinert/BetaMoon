package betamoon.gui.interop;

import betamoon.gui.control.GuiItemModel;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.luamodloader.ScriptExecution;
import net.minecraft.src.ItemStack;

/**
 * Native item and overlay snapshots prepared outside painting, owned by the
 * existing resource host.
 */
public final class GuiPreparedItem {
    public interface Service {
        Object revision(int hotbarSlot);

        GuiPreparedItem prepare(GuiItemModel model);

        double pickup(int hotbarSlot);
    }

    private final String owner;
    private final NativeClientScreenHost host;
    private final ItemStack item;
    private final ItemStack overlay;
    private final int damageWidth;
    private final int damageColor;
    private final int damageBackground;

    GuiPreparedItem(String owner, NativeClientScreenHost host, ItemStack source, boolean count, boolean durability) {
        this.owner = owner;
        this.host = host;
        item = source == null ? null : source.copy();
        overlay = source == null ? null : source.copy();
        if (overlay != null) {
            if (!count) {
                overlay.stackSize = 1;
            }
            overlay.setItemDamage(0);
        }
        int maximum = source == null ? 0 : source.getMaxDamage();
        int damage = source == null ? 0 : source.getItemDamageForDisplay();
        boolean bar = durability && maximum > 0 && damage > 0;
        damageWidth = bar ? Math.max(0, Math.min(13, (int) Math.round(13.0 - damage * 13.0 / maximum))) : -1;
        int green = maximum > 0 ? Math.max(0, Math.min(255, (int) Math.round(255.0 - damage * 255.0 / maximum))) : 0;
        damageColor = (255 - green) << 16 | green << 8;
        damageBackground = (255 - green) / 4 << 16 | 16128;
    }

    public void draw(GuiContext context, double pickup) {
        if (item == null) {
            return;
        }
        ScriptExecution.withOwner(owner, () -> {
            host.session().paint(() -> host.drawItemLayers(item, overlay, 0, 0, (float) context.getOpacity(), pickup));
            return null;
        });
        if (damageWidth >= 0) {
            int alpha = (int) Math.round(context.getOpacity() * 255) << 24;
            context.getRenderer().drawRect(Rect.fromPositionAndSize(2, 13, 13, 2), alpha);
            context.getRenderer().drawRect(Rect.fromPositionAndSize(2, 13, 12, 1), alpha | damageBackground);
            context.getRenderer().drawRect(Rect.fromPositionAndSize(2, 13, Math.max(0, damageWidth), 1),
                    alpha | damageColor);
        }
    }
}
