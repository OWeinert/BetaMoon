package betamoon.client;

import betamoon.luamodloader.LuaLoaderFeedback;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;

/** Client chat presentation for loader errors and reload completion. */
public final class LuaLoaderClientFeedback {
    private LuaLoaderClientFeedback() {
    }

    public static void initialize() {
        LuaLoaderFeedback.install(new LuaLoaderFeedback.Sink() {
            @Override
            public void reportIssues() {
                Minecraft minecraft = ModLoader.getMinecraftInstance();
                if (minecraft == null || minecraft.thePlayer == null) {
                    return;
                }
                List<LuaScriptErrors.ScriptIssue> issues = LuaScriptErrors.getEntries();
                for (LuaScriptErrors.ScriptIssue issue : issues) {
                    addColoredChatMessage(minecraft, issue.getMessage(), issue.isWarning() ? "\u00a76" : "\u00a7c");
                }
            }

            @Override
            public void reportReloadSummary() {
                Minecraft minecraft = ModLoader.getMinecraftInstance();
                if (minecraft == null || minecraft.thePlayer == null) {
                    return;
                }
                int errors = LuaScriptErrors.getErrorCount();
                String color = errors == 0 ? "\u00a7f" : "\u00a7c";
                minecraft.thePlayer.addChatMessage(
                        "\u00a7fBetaMoon reloaded with: " + color + errors + " Error/s\u00a7r");
            }
        });
    }

    private static void addColoredChatMessage(Minecraft minecraft, String message, String color) {
        String[] logicalLines = message.replace("\r", "").split("\n", -1);
        for (String logicalLine : logicalLines) {
            String remaining = logicalLine;
            if (remaining.length() == 0) {
                minecraft.thePlayer.addChatMessage(color + "\u00a7r");
                continue;
            }
            while (minecraft.fontRenderer.getStringWidth(remaining) > 320) {
                int end = 1;
                while (end < remaining.length()
                        && minecraft.fontRenderer.getStringWidth(remaining.substring(0, end + 1)) <= 320) {
                    end++;
                }
                int space = remaining.lastIndexOf(' ', end - 1);
                int split = space > 0 ? space : end;
                minecraft.thePlayer.addChatMessage(color + remaining.substring(0, split) + "\u00a7r");
                remaining = remaining.substring(split);
                while (remaining.startsWith(" ")) {
                    remaining = remaining.substring(1);
                }
            }
            if (remaining.length() > 0) {
                minecraft.thePlayer.addChatMessage(color + remaining + "\u00a7r");
            }
        }
    }
}
