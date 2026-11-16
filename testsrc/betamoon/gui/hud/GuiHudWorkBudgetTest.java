package betamoon.gui.hud;

import betamoon.gui.presentation.GuiWorkReservation;

/**
 * Shared budgets count documents/owners and reserve refresh alternatives
 * without double charging.
 */
public final class GuiHudWorkBudgetTest {
    public static void run() {
        GuiHudWorkBudget budget = new GuiHudWorkBudget();
        Object first = new Object();
        Object second = new Object();
        Object revision = new Object();
        try (GuiWorkReservation old = budget.reserve(first, revision, "old", "a", 0, 32);
                GuiWorkReservation fresh = budget.reserve(first, new Object(), "fresh", "a", 0, 32)) {
            refused(() -> budget.reserve(second, revision, "extra", "a", 0, 1));
            try (GuiWorkReservation other = budget.reserve(second, revision, "other", "b", 0, 32)) {
                refused(() -> budget.reserve(new Object(), revision, "overflow", "c", 0, 1));
            }
            try (GuiWorkReservation duplicate = budget.reserve(first, revision, "old", "a", 0, 32)) {
                // A prepared row replacement of the same stable path never paints beside its
                // predecessor.
            }
        }
        try (GuiWorkReservation old = budget.reserve(first, revision, "icons", "a", 4095, 0);
                GuiWorkReservation fresh = budget.reserve(first, new Object(), "icons", "a", 4095, 0)) {
            refused(() -> budget.reserve(second, revision, "overflow", "b", 2, 0));
        }
        try (GuiWorkReservation released = budget.reserve(second, revision, "all", "b", 4096, 32)) {
            // Rejected/retired candidates released their entire charge.
        }
    }

    private static void refused(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Aggregate HUD work admission allowed a fresh per-part/per-owner quota");
    }
}
