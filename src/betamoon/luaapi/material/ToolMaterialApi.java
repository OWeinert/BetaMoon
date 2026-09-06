package betamoon.luaapi.material;

import betamoon.luaapi.LuaApiUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.src.EnumToolMaterial;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import sun.reflect.ReflectionFactory;

public final class ToolMaterialApi {
    private ToolMaterialApi() {
    }

    public static void attach(LuaTable module) {
        module.set("createToolMaterial", new CreateToolMaterial());
    }

    private static final class CreateToolMaterial extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            int harvestLevel = args.checkint(2);
            int maxUses = args.checkint(3);
            float efficiency = (float) args.checkdouble(4);
            int damage = args.checkint(5);
            EnumToolMaterial material = createOrGetMaterial(name, harvestLevel, maxUses, efficiency, damage);
            return LuaValue.userdataOf(material);
        }
    }

    private static EnumToolMaterial createOrGetMaterial(String name, int harvestLevel, int maxUses, float efficiency,
        int damage) {
        String enumName = name.toUpperCase();
        try {
            EnumToolMaterial existing = EnumToolMaterial.valueOf(enumName);
            if (existing.getHarvestLevel() != harvestLevel || existing.getMaxUses() != maxUses
                || existing.getEfficiencyOnProperMaterial() != efficiency
                || existing.getDamageVsEntity() != damage) {
                throw new LuaError("ToolMaterial: changing material '" + name + "' requires a restart.");
            }
            return existing;
        } 
        catch (IllegalArgumentException ignored) { }
        
        EnumToolMaterial[] values = EnumToolMaterial.values();
        EnumToolMaterial created = allocateEnum(enumName, values.length, harvestLevel, maxUses, efficiency, damage);
        EnumToolMaterial[] next = Arrays.copyOf(values, values.length + 1);
        next[values.length] = created;
        setEnumValues(EnumToolMaterial.class, next);
        clearEnumCache(EnumToolMaterial.class);
        return created;
    }

    /**
     * Allocates a new {@link EnumToolMaterial} instance without invoking the enum constructor.
     * This uses the JDK's {@link ReflectionFactory} to build a serialization constructor, then
     * manually injects the enum core fields (name/ordinal) and the material stats fields.
     * This is required because enum constructors cannot be called directly at runtime.
     *
     * @param name The enum constant name to assign (already uppercased).
     * @param ordinal The enum ordinal to assign (typically current values length).
     * @param harvestLevel The harvest level to store on the created material.
     * @param maxUses The durability value to store on the created material.
     * @param efficiency The mining efficiency value to store on the created material.
     * @param damage The base damage value to store on the created material.
     * @return A fully populated {@link EnumToolMaterial} instance not yet registered in the enum values array.
     */
    private static EnumToolMaterial allocateEnum(String name, int ordinal, int harvestLevel, int maxUses,
        float efficiency, int damage) {
        try {
            ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
            Constructor<Object> objCtor = Object.class.getDeclaredConstructor();
            Constructor<?> enumCtor = rf.newConstructorForSerialization(EnumToolMaterial.class, objCtor);
            enumCtor.setAccessible(true);
            EnumToolMaterial result = (EnumToolMaterial) enumCtor.newInstance();
            setField(Enum.class, result, "name", name);
            setField(Enum.class, result, "ordinal", Integer.valueOf(ordinal));
            setMaterialFields(result, harvestLevel, maxUses, efficiency, damage);
            return result;
        } catch (Exception e) {
            throw new LuaError("ToolMaterial: failed to create tool material: " + e.getMessage());
        }
    }

    /**
     * Sets a private field on an enum instance, removing {@code final} if needed.
     * This is used to inject enum core fields (name/ordinal) and tool material stats.
     *
     * @param owner The class declaring the field to be set.
     * @param target The instance to mutate (the new enum constant).
     * @param fieldName The declared field name to update.
     * @param value The value to write into the field.
     * @throws Exception If reflection access fails or the field does not exist.
     */
    private static void setField(Class<?> owner, Object target, String fieldName, Object value) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        removeFinal(field);
        field.set(target, value);
    }

    /**
     * Populates EnumToolMaterial's four instance fields without using their names.
     * In obfuscated runtimes these field names are not stable, so we locate the
     * non-static fields and verify their expected type order:
     * (int harvestLevel, int maxUses, float efficiency, int damage).
     * This ensures the new enum constant has valid stats even when field
     * names differ between MCP and runtime.
     *
     * @param target the EnumToolMaterial instance to populate
     * @param harvestLevel mining harvest level to assign
     * @param maxUses durability value to assign
     * @param efficiency mining efficiency value to assign
     * @param damage base damage value to assign
     * @throws Exception if the fields cannot be located or updated
     */
    private static void setMaterialFields(EnumToolMaterial target, int harvestLevel, int maxUses, float efficiency, int damage) throws Exception {
        List<Field> instanceFields = new ArrayList<Field>();
        for (Field field : EnumToolMaterial.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                instanceFields.add(field);
            }
        }
        if (instanceFields.size() != 4) {
            throw new NoSuchFieldException("EnumToolMaterial instance fields not found");
        }
        Class<?>[] types = new Class<?>[] { int.class, int.class, float.class, int.class };
        Object[] values = new Object[] { Integer.valueOf(harvestLevel), Integer.valueOf(maxUses),
            Float.valueOf(efficiency), Integer.valueOf(damage) };
        for (int i = 0; i < instanceFields.size(); i++) {
            Field field = instanceFields.get(i);
            if (field.getType() != types[i]) {
                throw new NoSuchFieldException("EnumToolMaterial field order mismatch");
            }
            field.setAccessible(true);
            removeFinal(field);
            field.set(target, values[i]);
        }
    }

    /**
     * Replaces the hidden enum values array for the given enum class.
     * Java enums cache the array returned by {@code values()} in a synthetic static field.
     * This method finds that field, removes {@code final}, and writes the new array so
     * future {@code values()} calls include the new material.
     *
     * @param enumClass The enum class whose values array should be replaced.
     * @param values The new values array containing the appended material.
     */
    private static void setEnumValues(Class<?> enumClass, Object values) {
        try {
            Field valuesField = null;
            for (Field field : enumClass.getDeclaredFields()) {
                if (field.getType().isArray() && field.getType().getComponentType() == enumClass) {
                    valuesField = field;
                    break;
                }
            }
            if (valuesField == null) {
                throw new NoSuchFieldException("Enum values field not found");
            }
            valuesField.setAccessible(true);
            removeFinal(valuesField);
            valuesField.set(null, values);
        } catch (Exception e) {
            throw new LuaError("ToolMaterial: failed to set tool material values: " + e.getMessage());
        }
    }

    /**
     * Clears the internal Class caches for enum constants.
     * Without this, {@code Enum.valueOf(..)} and related lookups may still use
     * stale cached data and fail to find the newly injected constant.
     *
     * @param enumClass The enum class whose caches should be cleared.
     */
    private static void clearEnumCache(Class<?> enumClass) {
        try {
            Field enumConstants = Class.class.getDeclaredField("enumConstants");
            enumConstants.setAccessible(true);
            enumConstants.set(enumClass, null);
            Field enumConstantDirectory = Class.class.getDeclaredField("enumConstantDirectory");
            enumConstantDirectory.setAccessible(true);
            enumConstantDirectory.set(enumClass, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Removes the {@code final} modifier from a {@link Field} so it can be reassigned.
     * This is required when mutating enum internals and the synthetic enum values array.
     *
     * @param field The field whose modifiers should be updated.
     * @throws Exception If the modifiers field cannot be accessed or updated.
     */
    private static void removeFinal(Field field) throws Exception {
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }
}
