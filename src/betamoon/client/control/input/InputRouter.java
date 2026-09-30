package betamoon.client.control.input;

import betamoon.content.ContentKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Game-thread router for physical state, exact action transitions, and ordered
 * consumable contexts.
 */
public final class InputRouter {
    public interface FailureHandler {
        void failed(InputContext context, String action, Throwable error);
    }

    public static final int MAX_MAPS = 256;
    public static final int MAX_CONTEXTS = 256;
    public static final int MAX_TRANSITIONS_PER_CYCLE = 1024;
    public static final int MIN_PRIORITY = -10000;
    public static final int MAX_PRIORITY = 10000;

    private static final Comparator<InputContext> CONTEXT_ORDER = new Comparator<InputContext>() {
        public int compare(InputContext left, InputContext right) {
            int priority = Integer.compare(right.priority(), left.priority());
            return priority != 0 ? priority : Long.compare(right.sequence(), left.sequence());
        }
    };

    private final Map<ContentKey, InputMapRegistration> registrations =
            new LinkedHashMap<ContentKey, InputMapRegistration>();
    private final List<InputContext> contexts = new ArrayList<InputContext>();
    private final Map<ActionIdentity, MutableActionState> states = new HashMap<ActionIdentity, MutableActionState>();
    private final InputPhysicalState physical = new InputPhysicalState();
    private final FailureHandler failures;
    private long cycle;
    private long nextToken;
    private long nextSequence;
    private int transitions;
    private Object world;
    private Object player;
    private Object screen;
    private boolean focused = true;

    public InputRouter(FailureHandler failures) {
        this.failures = failures;
    }

    public InputMapRegistration register(InputMapDefinition definition, String owner) {
        if (definition == null || owner == null || owner.trim().length() == 0) {
            throw new IllegalArgumentException("Input map definition and owner are required");
        }
        if (registrations.size() >= MAX_MAPS) {
            throw new IllegalStateException("Input map limit reached: " + MAX_MAPS);
        }
        if (registrations.containsKey(definition.key())) {
            throw new IllegalStateException("Input map is already registered: " + definition.key());
        }
        InputMapRegistration registration = new InputMapRegistration(this, definition, owner);
        registrations.put(definition.key(), registration);
        for (InputActionDefinition action : definition.actions()) {
            states.put(new ActionIdentity(registration, action.name()), new MutableActionState());
        }
        return registration;
    }

    InputContext activate(InputMapRegistration registration, int priority, EnumSet<InputFamily> captures) {
        if (contexts.size() >= MAX_CONTEXTS) {
            throw new IllegalStateException("Input context limit reached: " + MAX_CONTEXTS);
        }
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "Input context priority must be within " + MIN_PRIORITY + ".." + MAX_PRIORITY);
        }
        InputContext context = new InputContext(this, registration, registration.owner(), ++nextToken, ++nextSequence,
                priority, captures);
        contexts.add(context);
        Collections.sort(contexts, CONTEXT_ORDER);
        return context;
    }

    public void beginCycle(Object currentWorld, Object currentPlayer, Object currentScreen, boolean hasFocus) {
        cycle++;
        transitions = 0;
        for (MutableActionState state : states.values()) {
            state.beginCycle();
        }
        boolean lifecycleChanged = world != currentWorld || player != currentPlayer || screen != currentScreen
                || focused != hasFocus;
        world = currentWorld;
        player = currentPlayer;
        screen = currentScreen;
        focused = hasFocus;
        if (lifecycleChanged || !hasFocus) {
            releaseAll(null);
        }
    }

    public boolean accept(InputDeviceEvent event) {
        if (event == null) {
            return false;
        }
        physical.apply(event);
        EventUpdate update = new EventUpdate();
        for (InputMapRegistration registration : new ArrayList<InputMapRegistration>(registrations.values())) {
            for (InputActionDefinition action : registration.definition().actions()) {
                collectUpdate(registration, action, event, update);
            }
        }
        boolean consumed = dispatch(update.transitions);
        for (InputMapRegistration registration : update.matchedRegistrations) {
            consumed |= captures(registration, InputFamily.NAMED_ACTIONS);
        }
        return consumed || update.retainedConsumption;
    }

    private void collectUpdate(InputMapRegistration registration, InputActionDefinition action, InputDeviceEvent source,
            EventUpdate update) {
        ActionIdentity identity = new ActionIdentity(registration, action.name());
        MutableActionState state = states.get(identity);
        if (source.device() == InputDeviceEvent.Device.MOUSE_WHEEL) {
            for (InputBinding binding : registration.bindings(action.name())) {
                if (binding.matchesWheel(source, physical.modifiers())) {
                    update.matched(registration);
                    state.pressed = true;
                    state.released = true;
                    state.amount += source.amount();
                    addTransition(update.transitions, registration, action.name(), InputPhase.PRESSED, source.amount(),
                            source, state, false);
                    break;
                }
            }
            return;
        }

        boolean active = false;
        boolean sourceMatches = false;
        for (InputBinding binding : registration.bindings(action.name())) {
            if (binding.isActive(physical)) {
                active = true;
            }
            if (binding.matchesDevice(source, physical.modifiers())) {
                sourceMatches = true;
            }
        }
        if (sourceMatches) {
            update.matched(registration);
        }
        if (active == state.held) {
            update.retainedConsumption |= sourceMatches && state.consumed;
            return;
        }
        state.held = active;
        state.amount = active ? 1 : 0;
        if (active) {
            state.pressed = true;
            addTransition(update.transitions, registration, action.name(), InputPhase.PRESSED, 1, source, state, false);
        } else {
            state.released = true;
            addTransition(update.transitions, registration, action.name(), InputPhase.RELEASED, 0, source, state,
                    state.consumed);
        }
    }

    private void addTransition(List<PendingTransition> pending, InputMapRegistration registration, String action,
            InputPhase phase, float amount, InputDeviceEvent source, MutableActionState state,
            boolean previouslyConsumed) {
        if (transitions >= MAX_TRANSITIONS_PER_CYCLE) {
            return;
        }
        transitions++;
        pending.add(new PendingTransition(registration, action, phase, amount, source, state, previouslyConsumed));
    }

    private boolean dispatch(List<PendingTransition> pending) {
        boolean consumed = false;
        for (PendingTransition transition : pending) {
            consumed |= transition.previouslyConsumed;
        }
        boolean handled = false;
        for (InputContext context : new ArrayList<InputContext>(contexts)) {
            if (!context.isActive()) {
                continue;
            }
            for (PendingTransition transition : pending) {
                if (context.registration() != transition.registration) {
                    continue;
                }
                InputActionListener listener = context.listener(transition.action);
                if (listener == null) {
                    continue;
                }
                InputActionEvent event = transition.event(cycle);
                try {
                    InputDisposition result = listener.handle(event);
                    if (result != null && result.consumesNativeInput()) {
                        consumed = true;
                        if (transition.phase == InputPhase.PRESSED && transition.source != null
                                && transition.source.device() != InputDeviceEvent.Device.MOUSE_WHEEL) {
                            transition.state.consumed = true;
                        }
                        handled = true;
                        break;
                    }
                } catch (Throwable error) {
                    context.disableAfterFailure();
                    contexts.remove(context);
                    if (failures != null) {
                        failures.failed(context, transition.action, error);
                    }
                    break;
                }
            }
            if (handled) {
                break;
            }
        }
        for (PendingTransition transition : pending) {
            if (transition.phase == InputPhase.RELEASED) {
                transition.state.consumed = false;
            }
        }
        return consumed;
    }

    private void releaseAll(InputDeviceEvent source) {
        physical.clear();
        List<PendingTransition> pending = new ArrayList<PendingTransition>();
        List<Map.Entry<ActionIdentity, MutableActionState>> entries =
                new ArrayList<Map.Entry<ActionIdentity, MutableActionState>>(states.entrySet());
        for (Map.Entry<ActionIdentity, MutableActionState> entry : entries) {
            MutableActionState state = entry.getValue();
            if (!state.held) {
                continue;
            }
            state.held = false;
            state.released = true;
            state.amount = 0;
            ActionIdentity identity = entry.getKey();
            addTransition(pending, identity.registration, identity.action, InputPhase.RELEASED, 0, source, state,
                    state.consumed);
        }
        dispatch(pending);
    }

    public boolean captures(InputFamily family) {
        for (InputContext context : contexts) {
            if (context.captures(family)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPhysicalConsumed(InputDeviceEvent.Device device, int code) {
        for (Map.Entry<ActionIdentity, MutableActionState> entry : states.entrySet()) {
            if (!entry.getValue().consumed) {
                continue;
            }
            ActionIdentity identity = entry.getKey();
            for (InputBinding binding : identity.registration.bindings(identity.action)) {
                if (binding.device() == device && binding.code() == code && binding.isActive(physical)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean captures(InputMapRegistration registration, InputFamily family) {
        for (InputContext context : contexts) {
            if (context.registration() == registration && context.captures(family)) {
                return true;
            }
        }
        return false;
    }

    void refresh(InputMapRegistration registration) {
        List<PendingTransition> pending = new ArrayList<PendingTransition>();
        for (InputActionDefinition action : registration.definition().actions()) {
            ActionIdentity identity = new ActionIdentity(registration, action.name());
            MutableActionState state = states.get(identity);
            boolean active = false;
            for (InputBinding binding : registration.bindings(action.name())) {
                if (binding.isActive(physical)) {
                    active = true;
                    break;
                }
            }
            if (state != null && state.held != active) {
                state.held = active;
                state.amount = active ? 1 : 0;
                state.pressed |= active;
                state.released |= !active;
                addTransition(pending, registration, action.name(), active ? InputPhase.PRESSED : InputPhase.RELEASED,
                        state.amount, null, state, state.consumed);
            }
        }
        dispatch(pending);
    }

    InputActionState state(InputMapRegistration registration, String action) {
        MutableActionState state = states.get(new ActionIdentity(registration, action));
        return state == null ? new InputActionState(cycle, false, false, false, 0) : state.snapshot(cycle);
    }

    void removeContext(InputContext context) {
        contexts.remove(context);
    }

    void unregister(InputMapRegistration registration) {
        if (registrations.get(registration.definition().key()) != registration) {
            registration.deactivate();
            return;
        }
        for (InputContext context : new ArrayList<InputContext>(contexts)) {
            if (context.registration() == registration) {
                context.close();
            }
        }
        for (InputActionDefinition action : registration.definition().actions()) {
            states.remove(new ActionIdentity(registration, action.name()));
        }
        registrations.remove(registration.definition().key());
        registration.deactivate();
    }

    public void unloadOwner(String owner) {
        if (owner == null) {
            return;
        }
        for (InputMapRegistration registration : new ArrayList<InputMapRegistration>(registrations.values())) {
            if (owner.equals(registration.owner())) {
                unregister(registration);
            }
        }
    }

    public void clear() {
        for (InputMapRegistration registration : new ArrayList<InputMapRegistration>(registrations.values())) {
            unregister(registration);
        }
        physical.clear();
        world = null;
        player = null;
        screen = null;
        focused = true;
    }

    public long cycle() {
        return cycle;
    }

    public int registrationCount() {
        return registrations.size();
    }

    public int contextCount() {
        return contexts.size();
    }

    private static final class ActionIdentity {
        private final InputMapRegistration registration;
        private final String action;

        private ActionIdentity(InputMapRegistration registration, String action) {
            this.registration = registration;
            this.action = action;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ActionIdentity && registration == ((ActionIdentity) other).registration
                    && action.equals(((ActionIdentity) other).action);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(registration) + action.hashCode();
        }
    }

    private static final class MutableActionState {
        private boolean pressed;
        private boolean held;
        private boolean released;
        private boolean consumed;
        private float amount;

        private void beginCycle() {
            pressed = false;
            released = false;
            amount = held ? 1 : 0;
        }

        private InputActionState snapshot(long cycle) {
            return new InputActionState(cycle, pressed, held, released, amount);
        }
    }

    private static final class EventUpdate {
        private final List<PendingTransition> transitions = new ArrayList<PendingTransition>();
        private final List<InputMapRegistration> matchedRegistrations = new ArrayList<InputMapRegistration>();
        private boolean retainedConsumption;

        private void matched(InputMapRegistration registration) {
            if (!matchedRegistrations.contains(registration)) {
                matchedRegistrations.add(registration);
            }
        }
    }

    private static final class PendingTransition {
        private final InputMapRegistration registration;
        private final String action;
        private final InputPhase phase;
        private final float amount;
        private final InputDeviceEvent source;
        private final MutableActionState state;
        private final boolean previouslyConsumed;

        private PendingTransition(InputMapRegistration registration, String action, InputPhase phase, float amount,
                InputDeviceEvent source, MutableActionState state, boolean previouslyConsumed) {
            this.registration = registration;
            this.action = action;
            this.phase = phase;
            this.amount = amount;
            this.source = source;
            this.state = state;
            this.previouslyConsumed = previouslyConsumed;
        }

        private InputActionEvent event(long cycle) {
            return new InputActionEvent(registration.definition().key(), action, phase, amount, cycle, source,
                    state.snapshot(cycle));
        }
    }
}
