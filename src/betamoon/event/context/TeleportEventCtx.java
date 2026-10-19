package betamoon.event.context;

import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Detached terminal data; live callback access is attached by the Lua event
 * scope.
 */
public final class TeleportEventCtx extends EventContext {
    private final Map<String, Object> result;

    public TeleportEventCtx(Minecraft minecraft, Map<String, Object> result) {
        super(minecraft);
        this.result = result;
    }

    public Map<String, Object> result() {
        return result;
    }
}
