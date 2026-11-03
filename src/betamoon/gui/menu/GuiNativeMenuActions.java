package betamoon.gui.menu;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;

/**
 * Curated semantic commands dispatch the actual native button flow, including
 * its validation.
 */
public final class GuiNativeMenuActions {
    private static Method nativeAction;
    private static Field buttonWidth;
    private static Field buttonHeight;

    public static Rect bounds(GuiButton button) {
        try {
            if (buttonWidth == null) {
                buttonWidth = dimension("width");
                buttonHeight = dimension("height");
            }
            return Rect.fromPositionAndSize(button.xPosition, button.yPosition, buttonWidth.getInt(button),
                    buttonHeight.getInt(button));
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Native menu dimensions unavailable", failure);
        }
    }

    private static Field dimension(String name) {
        NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        for (Field field : GuiButton.class.getDeclaredFields()) {
            if (field.getType() == int.class && (bindings == null
                    ? field.getName().equals(name)
                    : bindings.names.fieldMatches(GuiButton.class, field.getName(), "I", name, "named"))) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Native menu dimension did not match: " + name);
    }

    public static Map<String, Integer> commands(String target) {
        Map<String, Integer> result = new LinkedHashMap<>();
        switch (target) {
            case "minecraft:main":
                result.put("options", 0);
                result.put("worlds", 1);
                result.put("multiplayer", 2);
                result.put("texture_packs", 3);
                result.put("quit", 4);
                break;
            case "minecraft:pause":
                result.put("options", 0);
                result.put("disconnect", 1);
                result.put("resume", 4);
                result.put("achievements", 5);
                result.put("statistics", 6);
                break;
            case "minecraft:options":
                result.put("back", 200);
                result.put("controls", 100);
                result.put("video", 101);
                break;
            case "minecraft:world_select":
                result.put("back", 0);
                result.put("play", 1);
                result.put("delete", 2);
                result.put("create", 3);
                result.put("rename", 6);
                break;
            case "betamoon:world_create":
                result.put("create", 0);
                result.put("back", 1);
                break;
            default:
                throw new IllegalArgumentException("Unsupported native menu: " + target);
        }
        return Collections.unmodifiableMap(result);
    }

    public static boolean enabled(String target, String name, List<?> buttons) {
        Integer id = commands(target).get(name);
        if (id == null) {
            throw new IllegalArgumentException("Unsupported native menu action: " + name);
        }
        GuiButton button = button(buttons, id);
        return button != null && button.enabled;
    }

    public static void invoke(GuiScreen screen, String target, String name, List<?> buttons) {
        Integer id = commands(target).get(name);
        if (id == null) {
            throw new IllegalArgumentException("Unsupported native menu action: " + name);
        }
        GuiButton button = button(buttons, id);
        if (button == null || !button.enabled) {
            throw new IllegalStateException("Native menu action is currently unavailable: " + name);
        }
        try {
            method().invoke(screen, button);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Native menu action failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native menu action adapter unavailable", failure);
        }
    }

    private static GuiButton button(List<?> buttons, int id) {
        GuiButton result = null;
        for (Object value : buttons) {
            if (value instanceof GuiButton && ((GuiButton) value).id == id) {
                if (result != null) {
                    throw new IllegalStateException("Native menu action has ambiguous button identity: " + id);
                }
                result = (GuiButton) value;
            }
        }
        return result;
    }

    private static Method method() {
        if (nativeAction != null) {
            return nativeAction;
        }
        NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        if (bindings == null) {
            throw new IllegalStateException("Native menu action mappings unavailable");
        }
        for (Method method : GuiScreen.class.getDeclaredMethods()) {
            if (method.getReturnType() == void.class && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0] == GuiButton.class
                    && bindings.names.methodMatches(method, "actionPerformed", "named")) {
                method.setAccessible(true);
                nativeAction = method;
                return method;
            }
        }
        throw new IllegalStateException("Native menu action method did not match the supported client");
    }

    private GuiNativeMenuActions() {
    }
}
