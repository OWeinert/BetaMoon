package betamoon.hooks;

import betamoon.interop.catalog.CatalogValues;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Per-callback enclosing data. Native arguments remain separate from the
 * selected operation operands.
 */
public final class HookOperationInvocation implements AutoCloseable {
    public final HookOperationSite operation;
    private Object enclosingReceiver;
    private List<?> enclosingArguments;

    public HookOperationInvocation(HookOperationSite operation, Object enclosingReceiver, Object[] enclosingArguments) {
        this.operation = operation;
        this.enclosingReceiver = enclosingReceiver;
        this.enclosingArguments = Collections.unmodifiableList(Arrays.asList(enclosingArguments.clone()));
    }

    static HookOperationInvocation borrow(HookOperationSite operation, Object receiver, Object[] arguments) {
        return new HookOperationInvocation(operation, receiver, Collections.unmodifiableList(Arrays.asList(arguments)));
    }

    private HookOperationInvocation(HookOperationSite operation, Object receiver, List<?> arguments) {
        this.operation = operation;
        enclosingReceiver = receiver;
        enclosingArguments = arguments;
    }

    public Object enclosingReceiver() {
        return enclosingReceiver;
    }

    public int enclosingArgumentCount() {
        return enclosingArguments.size();
    }

    public Object enclosingArgument(int index) {
        return enclosingArguments.get(index);
    }

    public Object[] enclosingArguments() {
        return enclosingArguments.toArray();
    }

    public Object[] transport() {
        return new Object[]{operation.transport(), enclosingReceiver, enclosingArguments};
    }

    public static HookOperationInvocation transport(Object[] value) {
        return transport(value, HookOperationSite.transport(CatalogValues.object(value[0])));
    }

    static HookOperationInvocation transport(Object[] value, HookOperationSite operation) {
        return new HookOperationInvocation(operation, value[1], (List<?>) value[2]);
    }

    @Override
    public void close() {
        enclosingReceiver = null;
        enclosingArguments = null;
    }
}
