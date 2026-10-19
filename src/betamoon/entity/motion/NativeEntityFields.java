package betamoon.entity.motion;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import net.minecraft.src.Entity;

/** Cached exact mapped access to protected entity state; no field-order guessing. */
public final class NativeEntityFields {
    private static final Field FALL_DISTANCE = resolveFloat("fallDistance");

    private NativeEntityFields() {
    }

    public static float fallDistance(Entity entity) {
        try {
            return FALL_DISTANCE.getFloat(entity);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Native fall distance is unavailable", error);
        }
    }

    public static void fallDistance(Entity entity, float value) {
        try {
            FALL_DISTANCE.setFloat(entity, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Native fall distance is unavailable", error);
        }
    }

    private static Field resolveFloat(String name) {
        Field field = find(name);
        if (field != null) {
            return field;
        }
        try (InputStream input = NativeEntityFields.class.getResourceAsStream(
                "/betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            if (input != null) {
                TinyMappingResolver mappings = TinyMappingResolver.read(input);
                FieldRef reference = new FieldRef(new ClassRef("net/minecraft/src/Entity"), name, "F");
                for (RuntimeNamespace namespace : RuntimeNamespace.values()) {
                    field = find(mappings.resolveField(reference, namespace).getName());
                    if (field != null) {
                        return field;
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read entity field mappings", error);
        }
        throw new IllegalStateException("Unsupported native entity field: " + name);
    }

    private static Field find(String name) {
        try {
            Field field = Entity.class.getDeclaredField(name);
            if (field.getType() != Float.TYPE) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException error) {
            return null;
        }
    }
}
