package betamoon.event.api;

import betamoon.event.context.EventContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventChannel<TContext extends EventContext> {
    private final List<IEventListener<TContext>> listeners = new CopyOnWriteArrayList<IEventListener<TContext>>();

    public void subscribe(IEventListener<TContext> listener) {
        listeners.add(listener);
    }

    public boolean unsubscribe(IEventListener<TContext> listener) {
        return listeners.remove(listener);
    }

    public void publish(TContext context) {
        for (IEventListener<TContext> listener : listeners) {
            listener.invoke(context);
        }
    }
}
