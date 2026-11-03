package betamoon.gui.inventory;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.inventory.GuiInventoryProfile.Position;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Native hit bounds and lifecycle admission, without creating or mutating an
 * inventory.
 */
public final class GuiInventoryLayoutTest {
    private static final String OWNER = "ui-inventory-layout.lua";

    public static void main(String[] args) {
        try {
            semanticGeometry();
            rejectedGeometry();
            ownedClaims();
        } finally {
            ScriptResourceTracker.unload(OWNER);
        }
        System.out.println("Inventory layout passed: semantic groups, native hit margins, complete admission, viewport "
                + "refusal and owned cleanup without inventory mutation.");
    }

    private static void semanticGeometry() {
        require(GuiInventorySchema.SLOTS.size() == 45 && GuiInventorySchema.BY_ID.get("hotbar/1").nativeIndex == 36
                && GuiInventorySchema.BY_ID.get("output/1").nativeIndex == 0, "Native semantic schema drifted");
        GuiInventoryProfile profile = profile(200, 200, Collections.singletonMap("hotbar", new Position(12, 178)),
                Collections.singletonMap("hotbar/9", new Position(176, 178)), "panel");
        GuiInventoryLayout layout = new GuiInventoryLayout(profile, 176, 166, defaults());
        require(layout.slots.get(36).x == 12 && layout.slots.get(44).x == 176 && layout.slots.get(44).y == 178,
                "Group translation or individual semantic override was lost");
        Rect panel = layout.panel(320, 240);
        require(panel.getLeft() == 60 && panel.getTop() == 20,
                "Native centered panel origin disagrees with its extent");
        List<Position> unchanged = defaults();
        GuiInventoryLayout nativeLayout = new GuiInventoryLayout(
                profile(0, 0, Collections.emptyMap(), Collections.emptyMap(), "native"), 176, 166, unchanged);
        require(nativeLayout.slots.get(44).x == unchanged.get(44).x, "Native geometry was changed during proposal");
        refused(() -> layout.panel(180, 180));
    }

    private static void rejectedGeometry() {
        refused(() -> new GuiInventoryLayout(
                profile(0, 0, Collections.singletonMap("hotbar", new Position(8, 84)), Collections.emptyMap(), "panel"),
                176, 166, defaults()));
        refused(() -> new GuiInventoryLayout(profile(0, 0, Collections.emptyMap(),
                Collections.singletonMap("storage/1", new Position(9, 84)), "panel"), 176, 166, defaults()));
        refused(() -> new GuiInventoryLayout(profile(100, 166, Collections.emptyMap(), Collections.emptyMap(), "panel"),
                176, 166, defaults()));
        refused(() -> new GuiInventoryLayout(
                profile(200, 200, Collections.emptyMap(), Collections.emptyMap(), "native"), 176, 166, defaults()));
        refused(() -> profile(0, 0, Collections.singletonMap("invented", new Position(8, 8)), Collections.emptyMap(),
                "panel"));
    }

    private static void ownedClaims() {
        GuiInventoryRegistry registry = new GuiInventoryRegistry();
        GuiInventoryProfile profile = profile(0, 0, Collections.emptyMap(), Collections.emptyMap(), "panel");
        registry.add(profile);
        require(registry.activations().isEmpty(), "Registration acquired inventory authority");
        for (int attempt = 0; attempt < 100; attempt++) {
            GuiInventoryActivation claim = registry.activate(OWNER, profile.key);
            refused(() -> registry.activate(OWNER, profile.key));
            claim.close();
            require(registry.activations().isEmpty() && !claim.input.active(),
                    "Closed claim retained input or registration");
        }
        GuiInventoryActivation claim = registry.activate(OWNER, profile.key);
        ScriptResourceTracker.unload(OWNER);
        require(!profile.available() && claim.status().equals("closed") && registry.get(profile.key) == null,
                "Owner unload retained an inventory profile/claim");
    }

    private static GuiInventoryProfile profile(int width, int height, Map<String, Position> groups,
            Map<String, Position> positions, String background) {
        return new GuiInventoryProfile(OWNER, "test:inventory", null, 0, width, height, groups, positions, background,
                null, true, true, Rect.fromPositionAndSize(21, 15, 60, 60));
    }

    private static List<Position> defaults() {
        List<Position> positions = new ArrayList<>();
        for (GuiInventorySchema.Slot slot : GuiInventorySchema.SLOTS) {
            positions.add(new Position(slot.defaultX(), slot.defaultY()));
        }
        return positions;
    }

    private static void refused(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected complete inventory proposal refusal");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
