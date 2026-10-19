package betamoon.instrumentation.api;

import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

/**
 * Mapped native field boundary for contracts which cannot be identified by
 * unique Java field type.
 */
public final class NativeFieldAccess {
    private NativeFieldAccess() {
    }

    private static TinyMappingResolver mappings() {
        try (InputStream input = NativeFieldAccess.class
                .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            if (input == null) {
                throw new IllegalStateException("Bundled native mappings are unavailable");
            }
            return TinyMappingResolver.read(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot resolve native field mappings", exception);
        }
    }

    public static Field field(Class<?> runtimeClass, FieldRef reference) {
        String actual = runtimeClass.getName().replace('.', '/');
        if (actual.equals(reference.getOwner().getInternalName())) {
            return declared(runtimeClass, reference.getName(), reference);
        }
        TinyMappingResolver mappings = Holder.MAPPINGS;
        for (RuntimeNamespace namespace : RuntimeNamespace.values()) {
            if (!actual.equals(mappings.resolveClass(reference.getOwner(), namespace))) {
                continue;
            }
            return declared(runtimeClass, mappings.resolveField(reference, namespace).getName(), reference);
        }
        throw new IllegalStateException("Native field owner has an unsupported runtime identity: " + actual);
    }

    private static Field declared(Class<?> owner, String name, FieldRef reference) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Required native field is unavailable: " + reference, exception);
        }
    }

    private static final class Holder {
        private static final TinyMappingResolver MAPPINGS = mappings();
    }

    public static Object get(Field field, Object receiver) {
        try {
            return field.get(receiver);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access mapped native field", exception);
        }
    }
}
