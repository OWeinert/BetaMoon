package betamoon.luaapi.client.ui;

import betamoon.gui.hud.GuiNotificationCorner;
import betamoon.gui.hud.GuiNotificationDefinition;
import betamoon.gui.hud.GuiNotificationOptions;
import betamoon.gui.hud.GuiNotificationOptions.Lifetime;
import betamoon.gui.hud.GuiNotificationOptions.Urgency;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenDefinition;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/**
 * Strict template and typed-data/options parsing, independent of queue
 * admission.
 */
public final class GuiNotificationParser {
    public static GuiNotificationDefinition definition(String owner, LuaValue value) {
        String path = "ui.notification";
        LuaDeclarationValues.fields(value, path, "key", "screen", "width", "height", "corner", "interactive",
                "regions");
        try {
            return new GuiNotificationDefinition(owner, LuaDeclarationValues.string(value.get("key"), path + ".key"),
                    LuaDeclarationValues.string(value.get("screen"), path + ".screen"),
                    integer(value.get("width"), path + ".width", 160, 80, 512),
                    integer(value.get("height"), path + ".height", 40, 20, 256),
                    corner(value.get("corner"), path + ".corner", GuiNotificationCorner.TOP_RIGHT),
                    LuaDeclarationValues.bool(value.get("interactive"), path + ".interactive", false),
                    GuiComponentParser.strings(value.get("regions"), path + ".regions", 32));
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    public static GuiNotificationOptions options(GuiNotificationDefinition definition, LuaValue value,
            GuiNotificationOptions previous) {
        String path = "notification options";
        if (value.isnil()) {
            value = LuaValue.tableOf();
        }
        LuaDeclarationValues.fields(value, path, "data", "duration", "corner", "urgency", "lifetime", "dedupKey",
                "restartDuration", "pauseOnHover", "pauseOnFocus");
        GuiScreenDefinition screen = GuiClientUiRuntime.screens().getRequired(definition.screen);
        if (!screen.owner.equals(definition.owner)) {
            throw LuaDeclarationValues.error(path, "notification screen belongs to another owner");
        }
        Map<String, Object> data = data(screen, value.get("data"), previous);
        double seconds = value.get("duration").isnil()
                ? previous == null ? 5 : previous.durationMillis / 1000.0
                : LuaDeclarationValues.number(value.get("duration"), path + ".duration");
        if (!Double.isFinite(seconds) || seconds < 0.5 || seconds > 60) {
            throw LuaDeclarationValues.error(path + ".duration", "expected 0.5..60 seconds");
        }
        try {
            Lifetime lifetime = lifetime(value.get("lifetime"), previous == null ? Lifetime.CLIENT : previous.lifetime);
            Urgency urgency = urgency(value.get("urgency"), previous == null ? Urgency.NORMAL : previous.urgency);
            String dedup = value.get("dedupKey").isnil()
                    ? previous == null ? null : previous.dedupKey
                    : LuaDeclarationValues.string(value.get("dedupKey"), path + ".dedupKey");
            return new GuiNotificationOptions(data, Math.round(seconds * 1000),
                    corner(value.get("corner"), path + ".corner",
                            previous == null ? definition.corner : previous.corner),
                    urgency, lifetime, dedup,
                    LuaDeclarationValues.bool(value.get("restartDuration"), path + ".restartDuration",
                            previous == null),
                    LuaDeclarationValues.bool(value.get("pauseOnHover"), path + ".pauseOnHover",
                            previous != null && previous.pauseOnHover),
                    LuaDeclarationValues.bool(value.get("pauseOnFocus"), path + ".pauseOnFocus",
                            previous != null && previous.pauseOnFocus));
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    private static int integer(LuaValue value, String path, int fallback, int minimum, int maximum) {
        return value.isnil() ? fallback : LuaDeclarationValues.integer(value, path, minimum, maximum);
    }

    private static Map<String, Object> data(GuiScreenDefinition screen, LuaValue value,
            GuiNotificationOptions previous) {
        try {
            return screen.data.patch(previous == null ? Collections.emptyMap() : previous.data,
                    value.isnil() ? Collections.emptyMap() : GuiLuaData.record(value, "notification options.data"));
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error("notification options.data", invalid.getMessage());
        }
    }

    private static Lifetime lifetime(LuaValue value, Lifetime fallback) {
        if (value.isnil()) {
            return fallback;
        }
        switch (LuaDeclarationValues.string(value, "notification options.lifetime")) {
            case "client":
                return Lifetime.CLIENT;
            case "world":
                return Lifetime.WORLD;
            default:
                throw LuaDeclarationValues.error("notification options.lifetime", "expected client or world");
        }
    }

    private static Urgency urgency(LuaValue value, Urgency fallback) {
        if (value.isnil()) {
            return fallback;
        }
        switch (LuaDeclarationValues.string(value, "notification options.urgency")) {
            case "low":
                return Urgency.LOW;
            case "normal":
                return Urgency.NORMAL;
            case "high":
                return Urgency.HIGH;
            default:
                throw LuaDeclarationValues.error("notification options.urgency", "expected low, normal or high");
        }
    }

    private static GuiNotificationCorner corner(LuaValue value, String path, GuiNotificationCorner fallback) {
        return value.isnil() ? fallback : GuiNotificationCorner.named(LuaDeclarationValues.string(value, path));
    }

    private GuiNotificationParser() {
    }
}
