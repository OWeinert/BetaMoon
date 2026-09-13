package betamoon.utils;

import java.lang.reflect.Method;

public final class ClassUtils {
    private ClassUtils() {
    }

    public static Object tryInvokeStatic(Class<?> target, String methodName) {
        try {
            Method method = target.getMethod(methodName, new Class<?>[0]);
            return method.invoke(null, new Object[0]);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object tryInvokeStaticClass(String className, String methodName) {
        try {
            return tryInvokeStatic(Class.forName(className), methodName);
        } catch (Exception e) {
            return null;
        }
    }
}
