package betamoon.event;

import betamoon.event.api.EventChannel;
import betamoon.event.context.WorldEventCtx;

/** Published once after a native client-world simulation update completes. */
public final class WorldTickEvent extends EventChannel<WorldEventCtx> {
}
