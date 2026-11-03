package betamoon.gui.menu;

import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.screen.GuiHostElementBudget;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate candidate accounting and geometry used by actual attached menu
 * documents.
 */
public final class GuiMenuHostTest {
    public static void main(String[] arguments) {
        sharedCapacity();
        nativeReservations();
        System.out.println(
                "Menu host passed: atomic static/dynamic admission and rollback, cleanup and native-region isolation.");
    }

    private static void sharedCapacity() {
        GuiRowBudget rows = new GuiRowBudget();
        GuiHostElementBudget host = new GuiHostElementBudget(rows);
        Object root = new Object();
        host.prepare(root, 4000).commit();
        GuiRowBudget.Reservation first = rows.reserve(3000);
        rows.activateReplacing(Collections.singleton(first), Collections.emptyList());
        refused(() -> host.prepare(root, 6000));
        require(rows.totalLiveElements() == 7000 && rows.totalReservedElements() == 7000,
                "A rejected candidate changed accepted capacity");
        GuiHostElementBudget.Claim shrink = host.prepare(root, 1000);
        GuiRowBudget.Reservation second = rows.reserve(3000);
        rows.activateReplacing(Collections.singleton(second), Collections.emptyList());
        require(rows.totalLiveElements() == 7000 && rows.totalReservedElements() == 11000,
                "Old and candidate static roots did not share dynamic capacity");
        refused(shrink::close);
        require(rows.totalLiveElements() == 7000, "Failed rollback partly restored static accounting");
        second.close();
        shrink.close();
        require(rows.totalLiveElements() == 7000 && rows.totalReservedElements() == 7000,
                "Candidate disposal did not restore the accepted root");
        first.close();
        host.remove(root);
        require(rows.totalLiveElements() == 0 && rows.totalReservedElements() == 0,
                "Detached owners retained shared capacity");
        rows.close();
    }

    private static void nativeReservations() {
        for (int width : new int[]{320, 427, 640, 1024}) {
            for (int height : new int[]{240, 360, 480}) {
                List<Rect> nativeButtons = Arrays.asList(
                        Rect.fromPositionAndSize(width / 2 - 100, height / 4 + 48, 200, 20),
                        Rect.fromPositionAndSize(width / 2 - 100, height / 4 + 72, 200, 20),
                        Rect.fromPositionAndSize(10, height - 40, 90, 20));
                GuiMenuLayout layout = new GuiMenuLayout(width, height, nativeButtons, 60);
                require(!layout.actions.isEmpty(), "Engine recovery entry erased the entire contribution column");
                for (Rect rail : new Rect[]{layout.actions, layout.footer}) {
                    for (Rect reserved : nativeButtons) {
                        require(rail.intersect(reserved).isEmpty(), "A contribution overlaps a native/recovery action");
                    }
                    require(rail.intersect(layout.recovery).isEmpty(), "Contribution overlaps reserved engine chrome");
                }
                require(layout.actions.intersect(layout.footer).isEmpty(), "Independent contribution regions overlap");
            }
        }
        GuiMenuLayout tiny = new GuiMenuLayout(160, 100,
                Collections.singletonList(Rect.fromPositionAndSize(0, 0, 160, 100)), 60);
        require(tiny.actions.isEmpty() && tiny.footer.isEmpty(),
                "An unsupported tiny viewport acquired a native region");
    }

    private static void refused(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected aggregate capacity refusal");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
