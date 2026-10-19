package betamoon.luaapi.entity;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/** Verifies packaged mapped fields and Lua access without inheriting named game classes. */
public final class PackagedEntityTransformTest {
    private PackagedEntityTransformTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(arguments.length >= 2, "Expected the packaged mod and obfuscated game JARs");
        URL[] classpath = new URL[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            File file = new File(arguments[i]);
            require(file.isFile(), "Missing runtime dependency: " + file);
            classpath[i] = file.toURI().toURL();
        }

        try (URLClassLoader runtime = new URLClassLoader(classpath, null)) {
            TinyMappingResolver mappings;
            try (InputStream input = runtime.getResourceAsStream("betamoon/instrumentation/mapping/b1.7.3.tiny")) {
                require(input != null, "Packaged mappings are missing");
                mappings = TinyMappingResolver.read(input);
            }
            Class<?> entity = nativeClass(runtime, mappings, "Entity");
            Class<?> world = nativeClass(runtime, mappings, "World");
            Class<?> item = nativeClass(runtime, mappings, "EntityItem");
            require(!entity.getName().equals("net.minecraft.src.Entity"), "Runtime is not obfuscated");
            try {
                runtime.loadClass("net.minecraft.src.Entity");
                throw new AssertionError("Isolated runtime unexpectedly contains named game classes");
            } catch (ClassNotFoundException expected) {
                // Named test dependencies must not hide packaged mapping failures.
            }

            Object body = item.getConstructor(world).newInstance(new Object[] {null});
            Class<?> fields = runtime.loadClass("betamoon.entity.motion.NativeEntityFields");
            FieldRef reference = new FieldRef(new ClassRef("net/minecraft/src/Entity"), "fallDistance", "F");
            String fieldName = mappings.resolveField(reference, RuntimeNamespace.CLIENT).getName();
            Field nativeFall = entity.getDeclaredField(fieldName);
            nativeFall.setAccessible(true);
            Method readFall = fields.getMethod("fallDistance", entity);
            Method writeFall = fields.getMethod("fallDistance", entity, Float.TYPE);
            writeFall.invoke(null, body, 7.25F);
            near(nativeFall.getFloat(body), 7.25);
            nativeFall.setFloat(body, 9.5F);
            near(((Number) readFall.invoke(null, body)).doubleValue(), 9.5);

            runtime.loadClass("betamoon.entity.motion.EntitySimulationThread").getMethod("initialize").invoke(null);
            Class<?> scopeType = runtime.loadClass("betamoon.luaapi.utils.LuaCallbackScope");
            Object scope = scopeType.getConstructor(Boolean.TYPE).newInstance(true);
            try {
                Class<?> tableType = runtime.loadClass("org.luaj.vm2.LuaTable");
                Class<?> valueType = runtime.loadClass("org.luaj.vm2.LuaValue");
                Object api = tableType.getConstructor().newInstance();
                Class<?> facade = runtime.loadClass("betamoon.luaapi.entity.LuaEntityTransforms");
                Method attach = facade.getDeclaredMethod("attach", tableType, scopeType, entity);
                attach.setAccessible(true);
                attach.invoke(null, api, scope, body);

                Method get = valueType.getMethod("get", String.class);
                Object query = get.invoke(api, "getMovementState");
                Method call = valueType.getMethod("call", valueType);
                Object snapshot = call.invoke(query, api);
                Object distance = get.invoke(snapshot, "fallDistance");
                near(((Number) valueType.getMethod("todouble").invoke(distance)).doubleValue(), 9.5);
                Object reason = get.invoke(snapshot, "supportReason");
                require(valueType.getMethod("tojstring").invoke(reason).equals("entity_unavailable"),
                        "Packaged observation did not retain unavailable-world semantics");

                scopeType.getMethod("close").invoke(scope);
                try {
                    call.invoke(query, api);
                    throw new AssertionError("Packaged entity handle remained usable after scope close");
                } catch (InvocationTargetException expected) {
                    require(expected.getCause().getClass().getName().equals("org.luaj.vm2.LuaError"),
                            "Scope expiry raised an unexpected error: " + expected.getCause());
                }
            } finally {
                scopeType.getMethod("close").invoke(scope);
            }
        }
        System.out.println("Packaged entity access passed: isolated obfuscated mapping, native field identity, "
                + "Lua movement snapshot and expired scope.");
    }

    private static Class<?> nativeClass(URLClassLoader runtime, TinyMappingResolver mappings, String name)
            throws ClassNotFoundException {
        String mapped = mappings.resolveClass(new ClassRef("net/minecraft/src/" + name), RuntimeNamespace.CLIENT);
        return runtime.loadClass(mapped.replace('/', '.'));
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 0.00001, actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
