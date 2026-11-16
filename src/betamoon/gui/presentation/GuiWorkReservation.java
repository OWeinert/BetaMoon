package betamoon.gui.presentation;

/**
 * Engine-only prepared presentation work; closing a rejected candidate releases
 * its claim.
 */
@FunctionalInterface
public interface GuiWorkReservation extends AutoCloseable {
    GuiWorkReservation NONE = () -> {
    };

    @Override
    void close();
}
