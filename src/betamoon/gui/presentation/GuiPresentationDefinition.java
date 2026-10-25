package betamoon.gui.presentation;

import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.luaj.vm2.LuaValue;

/** Immutable presentation contract, including event-family subscriptions. */
public final class GuiPresentationDefinition {
    public static final Set<String> EVENT_TYPES;
    static {
        Set<String> events = new LinkedHashSet<>();
        Collections.addAll(events, "open", "change", "interaction", "control", "animation", "animation_property",
                "reload", "close");
        EVENT_TYPES = Collections.unmodifiableSet(events);
    }

    public final List<GuiNodeDefinition> nodes;
    public final LuaValue onEvent;
    public final Set<String> events;
    public final String callbackOwner;
    public final long ownerGeneration;

    public GuiPresentationDefinition(List<GuiNodeDefinition> nodes, LuaValue onEvent, Set<String> events,
            String callbackOwner) {
        this.callbackOwner = callbackOwner;
        this.ownerGeneration = ScriptResourceTracker.ownerGeneration(callbackOwner);
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.onEvent = onEvent;
        this.events = Collections.unmodifiableSet(new LinkedHashSet<>(events));
    }

    public boolean listens(String type) {
        return !onEvent.isnil() && events.contains(type);
    }
}
