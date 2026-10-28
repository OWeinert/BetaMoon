package betamoon.hooks;

import betamoon.interop.catalog.CatalogValues;
import java.util.Arrays;

/**
 * Per-callback enclosing data. Native arguments remain separate from the
 * selected operation operands.
 */
public final class HookOperationInvocation implements AutoCloseable {
    public final HookOperationSite operation;
    private Object enclosingReceiver;
    private final Object[] enclosingArguments;

    public HookOperationInvocation(HookOperationSite operation, Object enclosingReceiver, Object[] enclosingArguments) {
        this.operation = operation;
        this.enclosingReceiver = enclosingReceiver;
        this.enclosingArguments = enclosingArguments.clone();
    }

    public Object enclosingReceiver() {
        return enclosingReceiver;
    }

    public int enclosingArgumentCount() {
        return enclosingArguments.length;
    }

    public Object enclosingArgument(int index) {
        return enclosingArguments[index];
    }

    public Object[] enclosingArguments() {
        return enclosingArguments.clone();
    }

    public Object[] transport() {
        return new Object[]{operation.transport(), enclosingReceiver, enclosingArguments.clone()};
    }

    public static HookOperationInvocation transport(Object[] value) {
        return new HookOperationInvocation(HookOperationSite.transport(CatalogValues.object(value[0])), value[1],
                (Object[]) value[2]);
    }

    @Override
    public void close() {
        enclosingReceiver = null;
        Arrays.fill(enclosingArguments, null);
    }
}
