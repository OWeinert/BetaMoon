package betamoon.gui.presentation;

import java.util.Map;

/**
 * Engine-owned admission of a reversible local-control or complete binding
 * proposal. Never a Lua callback.
 */
@FunctionalInterface
public interface GuiViewStateAdmission {
    Proposal begin(Map<String, Object> prospective);

    /**
     * Expected engine validation refusal; a synchronous event/control proposal must
     * roll back and remain usable.
     */
    final class Refusal extends IllegalArgumentException {
        public Refusal(IllegalArgumentException cause) {
            super(cause.getMessage(), cause);
        }
    }

    interface Proposal extends AutoCloseable {
        void commit(Map<String, Object> accepted);

        @Override
        void close();
    }

    Proposal UNRESTRICTED = new Proposal() {
        public void commit(Map<String, Object> accepted) {
        }

        public void close() {
        }
    };
}
