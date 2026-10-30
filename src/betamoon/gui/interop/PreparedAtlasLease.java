package betamoon.gui.interop;

import betamoon.gui.graphics.GraphicsFramebuffer;
import betamoon.gui.style.GuiAtlasLease;
import betamoon.interop.client.NativeClientResource;

/**
 * Prepared texture metadata plus the existing native resource identity's Java
 * liveness check.
 */
public final class PreparedAtlasLease implements GuiAtlasLease {
    private final int width;
    private final int height;
    private final int texture;
    private final Runnable validate;

    private PreparedAtlasLease(int width, int height, int texture, Runnable validate) {
        this.width = width;
        this.height = height;
        this.texture = texture;
        this.validate = validate;
    }

    public static PreparedAtlasLease image(NativeClientResource<PreparedClientImage> resource) {
        PreparedClientImage image = resource.use();
        return new PreparedAtlasLease(image.width(), image.height(), image.textureId(), resource::requireActive);
    }

    public static PreparedAtlasLease target(NativeClientResource<GraphicsFramebuffer> resource) {
        GraphicsFramebuffer target = resource.use();
        return new PreparedAtlasLease(target.width(), target.height(), target.textureId(), resource::requireActive);
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
    }
}
