package betamoon.client.control.input;

/** One bounded semantic input callback. */
@FunctionalInterface
public interface InputActionListener {
    InputDisposition handle(InputActionEvent event);
}
