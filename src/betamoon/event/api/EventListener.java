package betamoon.event.api;

import betamoon.event.context.EventContext;
import java.util.function.Consumer;

public final class EventListener<C extends EventContext> implements IEventListener<C> {
    private final Consumer<C> consumer;

    public EventListener(Consumer<C> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void invoke(C context) {
        consumer.accept(context);
    }
}
