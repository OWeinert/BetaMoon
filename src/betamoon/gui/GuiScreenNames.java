package betamoon.gui;

import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.src.GuiScreen;

/** Readable screen identities for diagnostics in named and obfuscated clients. */
public final class GuiScreenNames {
    private GuiScreenNames() {
    }

    public static String name(GuiScreen screen) {
        String named = className(screen);
        return named.substring(named.lastIndexOf('.') + 1);
    }

    public static String className(GuiScreen screen) {
        return screen == null ? "none" : namedClassName(screen.getClass().getName(), Holder.MAPPINGS);
    }

    static String namedClassName(String runtimeName, TinyMappingResolver mappings) {
        if (mappings == null) {
            return runtimeName;
        }
        return mappings.resolveNamedClass(runtimeName.replace('.', '/'), RuntimeNamespace.CLIENT).replace('/', '.');
    }

    private static TinyMappingResolver loadMappings() {
        try (InputStream input = GuiScreenNames.class
                .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            return input == null ? null : TinyMappingResolver.read(input);
        } catch (IOException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    private static final class Holder {
        private static final TinyMappingResolver MAPPINGS = loadMappings();
    }
}
