package betamoon.entity;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.src.EntityList;

/** Registers stable bridge save IDs without assigning numeric IDs to Lua types. */
public final class EntityBootstrap {
    private static boolean registered;

    private EntityBootstrap() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        try {
            Map<String, Class<?>> byName = mapping("stringToClassMapping", "a");
            Map<Class<?>, String> byClass = mapping("classToStringMapping", "b");
            register(byName, byClass, "BetaMoonProp", LuaPropEntity.class);
            register(byName, byClass, "BetaMoonProjectile", LuaProjectileEntity.class);
            register(byName, byClass, "BetaMoonLiving", LuaLivingEntity.class);
            register(byName, byClass, "BetaMoonPickup", LuaPickupEntity.class);
            registered = true;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot register BetaMoon entity save bridges", error);
        }
    }

    private static void register(Map<String, Class<?>> byName, Map<Class<?>, String> byClass, String saveId,
            Class<?> bridge) {
        Class<?> existingClass = byName.get(saveId);
        String existingName = byClass.get(bridge);
        if (existingClass != null && existingClass != bridge || existingName != null && !existingName.equals(saveId)) {
            throw new IllegalStateException("Entity save ID collision: " + saveId);
        }
        byName.put(saveId, bridge);
        byClass.put(bridge, saveId);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapping(String readable, String obfuscated) throws ReflectiveOperationException {
        Field field;
        try {
            field = EntityList.class.getDeclaredField(readable);
        } catch (NoSuchFieldException ignored) {
            field = EntityList.class.getDeclaredField(obfuscated);
        }
        field.setAccessible(true);
        return (Map<K, V>) field.get(null);
    }
}
