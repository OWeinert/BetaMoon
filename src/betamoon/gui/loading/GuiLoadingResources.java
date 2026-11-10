package betamoon.gui.loading;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetRegistration;
import betamoon.client.assets.AssetLocation;
import betamoon.gui.graphics.GuiResourceDefinitions.Image;
import betamoon.gui.interop.GuiAssetImage;
import betamoon.luamodloader.ScriptAssetScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Transfer-owned prepared fallback chain, independent from world and native
 * screen sessions.
 */
public final class GuiLoadingResources implements AutoCloseable {
    private static final AssetId BACKGROUND = new AssetId(AssetKind.TEXTURE,
            AssetKey.parse("minecraft:image/gui/background"));
    private final List<Candidate> candidates = new ArrayList<>();
    private boolean closed;

    public GuiLoadingResources(Minecraft client, GuiLoadingAppearance override, GuiLoadingAppearance dimension) {
        prepare(client, override);
        if (dimension != override) {
            prepare(client, dimension);
        }
        prepare(client, GuiLoadingAppearance.MINECRAFT);
    }

    /**
     * Only reads prepared identities/liveness. Never resolves, decodes, uploads or
     * calls Lua.
     */
    public Candidate selected() {
        if (!closed) {
            for (Candidate candidate : candidates) {
                if (candidate.available()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private void prepare(Minecraft client, GuiLoadingAppearance appearance) {
        if (appearance == null || !appearance.available()) {
            return;
        }
        GuiAssetImage image = null;
        String assetOwner = null;
        try {
            AssetId id = appearance.background == null ? BACKGROUND : appearance.background;
            AssetDefinition definition = ScriptAssetScope.findVisible(id);
            if (definition != null) {
                AssetRegistration registration = ScriptAssetScope.find(id);
                assetOwner = registration == null ? null : registration.getOwner();
                image = new GuiAssetImage(client, appearance.owner, new Image(new AssetLocation(definition), true),
                        appearance.mode == GuiLoadingAppearance.Mode.TILE);
            } else if (appearance.background != null) {
                return;
            }
        } catch (RuntimeException preparationFailure) {
            // Cosmetic failure must not prevent saving, recovery or entering the
            // destination.
            if (appearance != GuiLoadingAppearance.MINECRAFT) {
                return;
            }
        }
        candidates.add(new Candidate(appearance, image, assetOwner));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            for (Candidate candidate : candidates) {
                candidate.close();
            }
        } finally {
            candidates.clear();
        }
    }

    public static final class Candidate implements AutoCloseable {
        public final GuiLoadingAppearance appearance;
        public final GuiAssetImage image;
        private final String assetOwner;
        private final ScriptResourceTracker.OwnerGeneration assetGeneration;
        private final long assetToken;
        private final ScriptResourceTracker.Cleanup cleanup = this::close;
        private boolean closed;

        private Candidate(GuiLoadingAppearance appearance, GuiAssetImage image, String assetOwner) {
            this.appearance = appearance;
            this.image = image;
            this.assetOwner = assetOwner;
            assetGeneration = assetOwner == null ? null : ScriptResourceTracker.ownerToken(assetOwner);
            assetToken = assetGeneration == null ? 0 : assetGeneration.current();
            track(appearance.owner);
            if (assetOwner != null && !assetOwner.equals(appearance.owner)) {
                track(assetOwner);
            }
        }

        private void track(String owner) {
            if (owner != null) {
                ScriptExecution.withOwner(owner, () -> {
                    ScriptResourceTracker.track(cleanup);
                    return null;
                });
            }
        }

        public boolean available() {
            return !closed && appearance.available()
                    && (assetGeneration == null || assetGeneration.matches(assetToken));
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (image != null) {
                    image.close();
                }
            } finally {
                ScriptResourceTracker.untrack(appearance.owner, cleanup);
                if (assetOwner != null && !assetOwner.equals(appearance.owner)) {
                    ScriptResourceTracker.untrack(assetOwner, cleanup);
                }
            }
        }
    }
}
