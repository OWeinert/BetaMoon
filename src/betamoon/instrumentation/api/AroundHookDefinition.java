package betamoon.instrumentation.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable definition of an around-method hook.
 *
 * The capture callback runs at method entry and returns state stored in a new
 * local. The return callback runs before every normal return and can receive
 * arguments, the original return value, and the captured state.
 */
public final class AroundHookDefinition implements HookDefinition {
    private final String id;
    private final MethodRef target;
    private final HandlerRef captureHandler;
    private final List<ValueBinding> captureBindings;
    private final HandlerRef returnHandler;
    private final List<ValueBinding> returnBindings;
    private final MatchRequirement matchRequirement;
    private final int priority;

    private AroundHookDefinition(Builder builder) {
        this.id = builder.id;
        this.target = builder.target;
        this.captureHandler = builder.captureHandler;
        this.captureBindings = immutableCopy(builder.captureBindings);
        this.returnHandler = builder.returnHandler;
        this.returnBindings = immutableCopy(builder.returnBindings);
        this.matchRequirement = builder.matchRequirement;
        this.priority = builder.priority;
    }

    public static Builder builder(String id, MethodRef target) {
        return new Builder(id, target);
    }

    public String getId() {
        return id;
    }

    public MethodRef getTarget() {
        return target;
    }

    public HandlerRef getCaptureHandler() {
        return captureHandler;
    }

    public List<ValueBinding> getCaptureBindings() {
        return captureBindings;
    }

    public HandlerRef getReturnHandler() {
        return returnHandler;
    }

    public List<ValueBinding> getReturnBindings() {
        return returnBindings;
    }

    public MatchRequirement getMatchRequirement() {
        return matchRequirement;
    }

    public int getPriority() {
        return priority;
    }

    private static List<ValueBinding> immutableCopy(List<ValueBinding> values) {
        return Collections.unmodifiableList(new ArrayList<ValueBinding>(values));
    }

    public static final class Builder {
        private final String id;
        private final MethodRef target;
        private HandlerRef captureHandler;
        private List<ValueBinding> captureBindings = Collections.emptyList();
        private HandlerRef returnHandler;
        private List<ValueBinding> returnBindings = Collections.emptyList();
        private MatchRequirement matchRequirement = MatchRequirement.exactly(1);
        private int priority;

        private Builder(String id, MethodRef target) {
            if (id == null || id.trim().length() == 0) {
                throw new IllegalArgumentException("Hook id is required");
            }
            if (target == null) {
                throw new IllegalArgumentException("Hook target is required");
            }
            this.id = id;
            this.target = target;
        }

        public Builder capture(HandlerRef handler, ValueBinding... bindings) {
            if (handler == null) {
                throw new IllegalArgumentException("Capture handler is required");
            }
            this.captureHandler = handler;
            this.captureBindings = copyBindings(bindings);
            return this;
        }

        public Builder onReturn(HandlerRef handler, ValueBinding... bindings) {
            if (handler == null) {
                throw new IllegalArgumentException("Return handler is required");
            }
            this.returnHandler = handler;
            this.returnBindings = copyBindings(bindings);
            return this;
        }

        public Builder requireMatches(MatchRequirement requirement) {
            if (requirement == null) {
                throw new IllegalArgumentException("Match requirement is required");
            }
            this.matchRequirement = requirement;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public AroundHookDefinition build() {
            if (captureHandler == null || returnHandler == null) {
                throw new IllegalStateException("Around hooks require capture and return handlers");
            }
            return new AroundHookDefinition(this);
        }

        private static List<ValueBinding> copyBindings(ValueBinding[] bindings) {
            List<ValueBinding> copy = new ArrayList<ValueBinding>();
            if (bindings != null) {
                for (ValueBinding binding : bindings) {
                    if (binding == null) {
                        throw new IllegalArgumentException("Hook bindings cannot contain null");
                    }
                    copy.add(binding);
                }
            }
            return copy;
        }
    }
}
