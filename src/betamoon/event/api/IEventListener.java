package betamoon.event.api;

import betamoon.event.context.EventContext;

@FunctionalInterface
public interface IEventListener<C extends EventContext> {
    void invoke(C context);
}
