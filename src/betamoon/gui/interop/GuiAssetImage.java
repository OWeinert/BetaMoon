package betamoon.gui.interop;

import betamoon.assets.NativeTextureSource;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.NativeTextureResources;
import betamoon.client.assets.TextureAsset;
import betamoon.client.assets.TextureImage;
import betamoon.client.assets.TexturePixelUpload;
import betamoon.gui.graphics.GraphicsStateGuard;
import betamoon.gui.graphics.GuiResourceDefinitions.Image;
import betamoon.gui.style.GuiAtlasLease;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Prepared image over the existing asset lifecycle; animation uploads occur
 * only at logical updates.
 */
public final class GuiAssetImage implements GuiAtlasLease, AutoCloseable {
    private final Minecraft client;
    private final Image definition;
    private TextureAsset asset;
    private NativeTextureResources.Selection nativeSelection;
    private GuiResourceBudget.Reservation allocation;
    private Runnable validate = () -> {
    };
    private int texture;
    private int width;
    private int height;
    private boolean dirty;
    private long revision;
    private final Consumer<TextureImage> listener = ignored -> {
        dirty = true;
        revision++;
    };

    public GuiAssetImage(Minecraft client, String owner, Image definition) {
        this.client = client;
        this.definition = definition;
        boolean complete = false;
        try {
            NativeTextureSource nativeSource = definition.source.getNativeTexture();
            if (nativeSource != null) {
                nativeSelection = NativeTextureResources.inspect(nativeSource);
                width = nativeSelection.width;
                height = nativeSelection.height;
                // Minecraft retains ownership; neither disposal nor filtering changes this
                // shared texture.
                texture = client.renderEngine.getTexture(nativeSource.getResource());
            } else {
                asset = ClientAssets.acquireTexture(definition.source);
                BufferedImage image = asset.currentFrame();
                width = image.getWidth();
                height = image.getHeight();
                allocation = GuiResourceBudget.reserve(owner, (long) width * height * 4);
                try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
                    texture = GL11.glGenTextures();
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                    int filter = definition.nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA,
                            GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                    upload(image);
                }
                asset.listen(listener);
            }
            complete = true;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot prepare UI image: " + failure.getMessage(), failure);
        } finally {
            if (!complete) {
                close();
            }
        }
    }

    void liveness(Runnable validate) {
        this.validate = validate;
    }

    public long revision() {
        return revision;
    }

    public boolean needsRevision() {
        if (asset == null) {
            return dirty;
        }
        return asset.currentFrame().getWidth() != width || asset.currentFrame().getHeight() != height;
    }

    public boolean needsUpload() {
        return dirty && asset != null && !needsRevision();
    }

    public void observe() {
        if (nativeSelection != null) {
            try {
                NativeTextureResources.Selection current = NativeTextureResources
                        .inspect(definition.source.getNativeTexture());
                if (current != nativeSelection && !dirty) {
                    dirty = true;
                    revision++;
                }
            } catch (IOException changed) {
                if (!dirty) {
                    dirty = true;
                    revision++;
                }
            }
        }
    }

    public void update() {
        if (!needsUpload()) {
            return;
        }
        try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            upload(asset.currentFrame());
            dirty = false;
        }
    }

    private void upload(BufferedImage image) {
        TexturePixelUpload.upload(image, 0, 0, width, height,
                client.gameSettings != null && client.gameSettings.anaglyph, false);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int textureId() {
        requireActive();
        return texture;
    }

    public void requireActive() {
        validate.run();
        if (texture == 0) {
            throw new IllegalStateException("Prepared UI image is disposed");
        }
    }

    @Override
    public void close() {
        try {
            if (asset != null) {
                asset.unlisten(listener);
                asset.close();
                asset = null;
            }
            if (texture != 0 && nativeSelection == null) {
                GL11.glDeleteTextures(texture);
            }
        } finally {
            texture = 0;
            nativeSelection = null;
            if (allocation != null) {
                allocation.close();
                allocation = null;
            }
            validate = () -> {
                throw new IllegalStateException("Prepared UI image is disposed");
            };
        }
    }
}
