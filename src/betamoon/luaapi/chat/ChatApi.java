package betamoon.luaapi.chat;

import betamoon.luaapi.LuaApiUtils;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.utils.ClassUtils;
import betamoon.utils.MinecraftUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Packet;
import net.minecraft.src.Packet3Chat;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class ChatApi {
    private static final char FORMAT_SPECIFIER = '%';
    private static final char COLOR_CODE_CHAR = '\u00a7';
    private static final int MAX_PENDING_MESSAGES = 100;
    private static final List pendingMessages = new ArrayList();

    private ChatApi() {
    }

    public static void attach(LuaTable module) {
        module.set("chat", new Chat());
        module.set("broadcast", new Broadcast());
    }

    private static final class Chat extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            String format = LuaApiUtils.getStringArg(args, 1);
            int offset = (args.narg() >= 1 && args.arg(1).istable()) ? 1 : 0;
            String message = buildFormattedMessage(format, args, 2 + offset);
            if (message != null) {
                sendChat(message);
            }
            return LuaValue.NIL;
        }
    }

    private static final class Broadcast extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            String format = LuaApiUtils.getStringArg(args, 1);
            int offset = (args.narg() >= 1 && args.arg(1).istable()) ? 1 : 0;
            String message = buildFormattedMessage(format, args, 2 + offset);
            if (message != null) {
                sendBroadcast(message);
            }
            return LuaValue.NIL;
        }
    }

    private static void sendChat(String message) {
        message = prefixMessage(message);
        try {
            net.minecraft.client.Minecraft mc = ModLoader.getMinecraftInstance();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(message);
            } else {
                queueMessage(message);
            }
        } catch (Throwable t) {
            LuaApiUtils.warn("Chat", "Chat unavailable: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Delivers messages that scripts sent while Minecraft was still at the main
     * menu. The game tick calls this once a player is available.
     */
    public static void flushPendingMessages() {
        net.minecraft.client.Minecraft mc = ModLoader.getMinecraftInstance();
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        List messages;
        synchronized (pendingMessages) {
            if (pendingMessages.isEmpty()) {
                return;
            }
            messages = new ArrayList(pendingMessages);
            pendingMessages.clear();
        }
        for (int i = 0; i < messages.size(); i++) {
            mc.thePlayer.addChatMessage(((PendingMessage) messages.get(i)).text);
        }
    }

    /** Keeps startup messages bounded so a broken script cannot grow memory forever. */
    private static void queueMessage(String message) {
        final PendingMessage pending = new PendingMessage(message);
        synchronized (pendingMessages) {
            if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
                pendingMessages.remove(0);
            }
            pendingMessages.add(pending);
        }
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                synchronized (pendingMessages) {
                    pendingMessages.remove(pending);
                }
            }
        });
    }

    /** One identity-bearing entry so script cleanup can remove only its own message. */
    private static final class PendingMessage {
        private final String text;

        private PendingMessage(String text) {
            this.text = text;
        }
    }

    private static void sendBroadcast(String message) {
        message = prefixMessage(message);
        if (tryServerBroadcast(message)) {
            return;
        }
        if (MinecraftUtils.isSingleplayerClient()) {
            sendChat(message);
        } else {
            LuaApiUtils.warn("Chat", "Broadcast unavailable: server not accessible.");
        }
    }

    private static boolean tryServerBroadcast(String message) {
        Object server = getServerInstance();
        if (server == null) {
            return false;
        }
        try {
            Field configManagerField = server.getClass().getField("configManager");
            Object configManager = configManagerField.get(server);
            if (configManager == null) {
                return false;
            }
            Method sendPacket = configManager.getClass().getMethod("sendPacketToAllPlayers", Packet.class);
            sendPacket.invoke(configManager, new Packet3Chat(message));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object getServerInstance() {
        Object server = ClassUtils.tryInvokeStatic(ModLoader.class, "getMinecraftServerInstance");
        if (server != null) {
            return server;
        }
        server = ClassUtils.tryInvokeStaticClass("net.minecraft.server.MinecraftServer", "getServer");
        if (server != null) {
            return server;
        }
        return ClassUtils.tryInvokeStaticClass("net.minecraft.server.MinecraftServer", "getMinecraftServer");
    }

    private static String prefixMessage(String message) {
        String scriptName = LuaScriptRegistry.getCurrentScriptFile();
        if (scriptName != null && !scriptName.trim().isEmpty()) {
            return "[" + scriptName + "]: " + message;
        }
        return message;
    }

    private static String buildFormattedMessage(String format, Varargs args, int startIndex) {
        if (format == null) {
            LuaApiUtils.warn("Chat", "Format string cannot be nil.");
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int argIndex = startIndex;
        int usedArgs = 0;
        int length = format.length();
        for (int i = 0; i < length; i++) {
            char ch = format.charAt(i);
            if (ch == COLOR_CODE_CHAR) {
                if (i + 1 < length) {
                    char colorCode = Character.toLowerCase(format.charAt(i + 1));
                    if (isColorCode(colorCode)) {
                        builder.append(COLOR_CODE_CHAR).append(colorCode);
                        i++;
                        continue;
                    }
                }
                builder.append(ch);
                continue;
            }
            if (ch != FORMAT_SPECIFIER) {
                builder.append(ch);
                continue;
            }
            if (i + 1 >= length) {
                LuaApiUtils.warn("Chat", "Dangling '" + FORMAT_SPECIFIER + "' in format string.");
                return null;
            }
            char spec = format.charAt(++i);
            if (spec == FORMAT_SPECIFIER) {
                builder.append(FORMAT_SPECIFIER);
                continue;
            }
            LuaValue value = args.arg(argIndex++);
            if (value.isnil()) {
                LuaApiUtils.warn("Chat", "Not enough arguments for format string.");
                return null;
            }
            usedArgs++;
            switch (spec) {
                case 'i':
                    if (!value.isint()) {
                        LuaApiUtils.warn("Chat", "Expected number for '%i' specifier.");
                        return null;
                    }
                    builder.append(value.checkint());
                    break;
                case 'd':
                    if (!value.isnumber()) {
                        LuaApiUtils.warn("Chat", "Expected number for '%d' specifier.");
                        return null;
                    }
                    builder.append(value.checkdouble());
                    break;
                case 'b':
                    if (!value.isboolean()) {
                        LuaApiUtils.warn("Chat", "Expected boolean for '%b' specifier.");
                        return null;
                    }
                    builder.append(value.checkboolean());
                    break;
                case 's':
                    if (!value.isstring()) {
                        LuaApiUtils.warn("Chat", "Expected string for '%s' specifier.");
                        return null;
                    }
                    builder.append(value.checkjstring());
                    break;
                case 'o':
                    builder.append(value.tojstring());
                    break;
                default:
                    LuaApiUtils.warn("Chat", "Unsupported format specifier '%" + spec + "'.");
                    return null;
            }
        }
        int providedArgs = args.narg() - (startIndex - 1);
        if (providedArgs < 0) {
            providedArgs = 0;
        }
        if (providedArgs > usedArgs) {
            LuaApiUtils.warn("Chat", "Too many arguments for format string. Expected: " + usedArgs + " | Found: " + providedArgs);
            return null;
        }
        if (providedArgs < usedArgs) {
            LuaApiUtils.warn("Chat", "Not enough arguments for format string. Expected: " + usedArgs + " | Found: " + providedArgs);
            return null;
        }
        return builder.toString();
    }

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
    }
}
