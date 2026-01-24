package betamoon.utils;

public final class ClassNameUtils {
    private ClassNameUtils() {
    }

    public static String toUnobfuscated(String className) {
        return mapName(className, true);
    }

    public static String toObfuscated(String className) {
        return mapName(className, false);
    }

    private static String mapName(String className, boolean toUnobfuscated) {
        if (className == null) {
            return null;
        }
        String direct = toUnobfuscated
            ? ClassNameTable.getUnobfuscated(className)
            : ClassNameTable.getObfuscated(className);
        if (direct != null) {
            return direct;
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return className;
        }
        String simple = className.substring(lastDot + 1);
        String mapped = toUnobfuscated
            ? ClassNameTable.getUnobfuscated(simple)
            : ClassNameTable.getObfuscated(simple);
        if (mapped == null) {
            return className;
        }
        return className.substring(0, lastDot + 1) + mapped;
    }
}
