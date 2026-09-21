package betamoon.client.audio;

import betamoon.BetaMoonCommon;

import betamoon.entity.EntityPresentationEvents;
import betamoon.luaapi.audio.SoundEvents;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Client consumer for neutral entity presentation events. */
public final class ClientEntityEffects {
    private static final Set<String> WARNED = new HashSet<>();

    private ClientEntityEffects() {
    }

    public static void initialize() {
        EntityPresentationEvents.install(ClientEntityEffects::play);
    }

    private static void play(EntityPresentationEvents.SoundRequest request) {
        try {
            SoundEvents.play(request.event, request.x, request.y, request.z,
                    request.volume, request.pitch, request.range);
        } catch (IOException | RuntimeException error) {
            String message = request.event + ": " + error.getMessage();
            if (WARNED.add(message)) {
                BetaMoonCommon.LOGGER.warning("Entity sound unavailable: " + message);
            }
        }
    }
}
