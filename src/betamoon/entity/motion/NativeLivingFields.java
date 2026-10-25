package betamoon.entity.motion;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.MathHelper;

/**
 * Exact mapped access to protected native intent and shared animation
 * bookkeeping.
 */
public final class NativeLivingFields {
    private static final TinyMappingResolver MAPPINGS = mappings();
    private static final Field FORWARD = field("moveForward", "F");
    private static final Field STRAFE = field("moveStrafing", "F");
    private static final Field JUMP = field("isJumping", "Z");
    private static final Field LIMB_PREVIOUS = field("field_705_Q", "F");
    private static final Field LIMB_AMOUNT = field("field_704_R", "F");
    private static final Field LIMB_POSITION = field("field_703_S", "F");

    private NativeLivingFields() {
    }

    public static float forward(EntityLiving entity) {
        return number(FORWARD, entity);
    }

    public static float strafe(EntityLiving entity) {
        return number(STRAFE, entity);
    }

    public static boolean jumping(EntityLiving entity) {
        try {
            return JUMP.getBoolean(entity);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Native jump intent is unavailable", error);
        }
    }

    public static void intent(EntityLiving entity, float forward, float strafe) {
        number(FORWARD, entity, forward);
        number(STRAFE, entity, strafe);
    }

    /**
     * The native heading method's shared animation tail, also needed by a manual
     * collision step.
     */
    public static void animate(EntityLiving entity) {
        float amount = number(LIMB_AMOUNT, entity);
        number(LIMB_PREVIOUS, entity, amount);
        double x = entity.posX - entity.prevPosX;
        double z = entity.posZ - entity.prevPosZ;
        float moved = MathHelper.sqrt_double(x * x + z * z) * 4;
        if (moved > 1) {
            moved = 1;
        }
        amount += (moved - amount) * 0.4F;
        number(LIMB_AMOUNT, entity, amount);
        number(LIMB_POSITION, entity, number(LIMB_POSITION, entity) + amount);
    }

    public static String canonicalClass(Class<?> type) {
        String name = type.getName().replace('.', '/');
        for (RuntimeNamespace namespace : RuntimeNamespace.values()) {
            String named = MAPPINGS.resolveNamedClass(name, namespace);
            if (!named.equals(name)) {
                return named;
            }
        }
        return name;
    }

    public static Class<?> methodOwner(Class<?> type, String name, String descriptor, Class<?>... arguments) {
        MethodRef reference = new MethodRef(new ClassRef("net/minecraft/src/EntityLiving"), name, descriptor);
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (RuntimeNamespace namespace : RuntimeNamespace.values()) {
                String runtimeName = MAPPINGS.resolveMethod(reference, namespace).getName();
                try {
                    Method method = current.getDeclaredMethod(runtimeName, arguments);
                    return method.getDeclaringClass();
                } catch (NoSuchMethodException ignored) {
                    // Continue through exact mapped names and the native inheritance chain.
                }
            }
        }
        return null;
    }

    private static Field field(String name, String descriptor) {
        FieldRef reference = new FieldRef(new ClassRef("net/minecraft/src/EntityLiving"), name, descriptor);
        for (RuntimeNamespace namespace : RuntimeNamespace.values()) {
            try {
                Field result = EntityLiving.class
                        .getDeclaredField(MAPPINGS.resolveField(reference, namespace).getName());
                if (!(descriptor.equals("F") && result.getType() == float.class
                        || descriptor.equals("Z") && result.getType() == boolean.class)) {
                    continue;
                }
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) {
                // No field-order or numeric-name guesses.
            }
        }
        throw new IllegalStateException("Unsupported native living field: " + name);
    }

    private static float number(Field field, EntityLiving entity) {
        try {
            return field.getFloat(entity);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Native living state is unavailable", error);
        }
    }

    private static void number(Field field, EntityLiving entity, float value) {
        try {
            field.setFloat(entity, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Native living state is unavailable", error);
        }
    }

    private static TinyMappingResolver mappings() {
        try (InputStream input = NativeLivingFields.class
                .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            if (input == null) {
                throw new IOException("Native movement mappings are missing");
            }
            return TinyMappingResolver.read(input);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load native movement mappings", error);
        }
    }
}
