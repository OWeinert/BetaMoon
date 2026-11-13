package betamoon.gui.hud;

import betamoon.client.render.FluidCameraRuntime;
import betamoon.luaapi.client.ui.GuiScopedSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;

/**
 * Logical, detached player publications; native item copies never enter Lua.
 */
final class GuiHudObservationPublisher {
    static final class Publication {
        private final String source;
        Map<String, Object> values = Collections.emptyMap();
        GuiScopedSnapshot snapshot;
        private long revision;

        Publication(String source) {
            this.source = source;
            snapshot = new GuiScopedSnapshot(values, source, values);
        }

        void publish(Map<String, Object> next) {
            if (!values.equals(next)) {
                values = Collections.unmodifiableMap(new LinkedHashMap<>(next));
                Map<String, Object> published = values;
                if (source.equals("native:hud.layout")) {
                    published = new LinkedHashMap<>(values);
                    published.put("revision", ++revision);
                }
                snapshot = new GuiScopedSnapshot(published, source, values);
            }
        }
    }

    final Publication stats = new Publication("native:hud.stats");
    final Publication hotbar = new Publication("native:hud.hotbar");
    final Publication layout = new Publication("native:hud.layout");
    private final ItemStack[] items = new ItemStack[9];
    private Map<String, Object> player;

    void update(Minecraft client, boolean available) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> bar = new LinkedHashMap<>();
        List<Map<String, Object>> slots = new ArrayList<>();
        EntityPlayerSP actor = available ? client.thePlayer : null;
        if (actor != null) {
            values.put("health", actor.health);
            values.put("healthCapacity", 20);
            values.put("previousHealth", actor.prevHealth);
            values.put("damageFlashTicks", actor.heartsLife);
            values.put("armor", actor.getPlayerArmorValue());
            values.put("armorCapacity", 20);
            values.put("air", actor.air);
            values.put("airCapacity", 300);
            values.put("showSurvivalStats", client.playerController != null && client.playerController.shouldDrawHUD());
            values.put("showBreath", FluidCameraRuntime.showAirHud(actor, Material.water));
            bar.put("selectedSlot", actor.inventory.currentItem + 1);
        }
        for (int index = 0; index < 9; index++) {
            ItemStack stack = actor == null ? null : actor.inventory.mainInventory[index];
            if (!same(items[index], stack)) {
                items[index] = stack == null ? null : stack.copy();
            }
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("slot", index + 1);
            slot.put("present", stack != null);
            if (stack != null) {
                slot.put("id", stack.itemID);
                slot.put("damage", stack.getItemDamageForDisplay());
                slot.put("count", stack.stackSize);
                slot.put("maximumDamage", stack.getMaxDamage());
                slot.put("pickupTicks", stack.animationsToGo);
            }
            slots.add(Collections.unmodifiableMap(slot));
        }
        bar.put("slots", Collections.unmodifiableList(slots));
        stats.publish(values);
        hotbar.publish(bar);
        player = null;
        if (actor != null) {
            Map<String, Object> full = new LinkedHashMap<>(values);
            full.put("selectedSlot", actor.inventory.currentItem + 1);
            full.put("hotbar", bar.get("slots"));
            full.put("onGround", actor.onGround);
            full.put("x", actor.posX);
            full.put("y", actor.posY);
            full.put("z", actor.posZ);
            full.put("motionX", actor.motionX);
            full.put("motionY", actor.motionY);
            full.put("motionZ", actor.motionZ);
            player = Collections.unmodifiableMap(full);
        }
    }

    Map<String, Object> player() {
        return player;
    }

    ItemStack copyItem(int slot) {
        if (slot < 1 || slot > 9) {
            throw new IllegalArgumentException("HUD hotbar slot must be within 1..9");
        }
        ItemStack item = items[slot - 1];
        return item == null ? null : item.copy();
    }

    private static boolean same(ItemStack first, ItemStack second) {
        return first == null ? second == null : second != null && ItemStack.areItemStacksEqual(first, second);
    }
}
