package betamoon.gui.component;

import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiPresentationDefinition;
import java.util.Collections;

/**
 * Per-instance compiled visual definition; registered author definitions remain
 * reusable and state-free.
 */
final class GuiComponentDocument extends GuiDocumentDefinition {
    final GuiComponentPlan plan;

    GuiComponentDocument(String key, String owner, GuiPresentationDefinition presentation, GuiComponentPlan plan) {
        super(key, owner, 0, 0, null, null, Collections.emptyList(), presentation, true);
        this.plan = plan;
    }
}
