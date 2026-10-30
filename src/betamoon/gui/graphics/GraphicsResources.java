package betamoon.gui.graphics;

import betamoon.gui.style.GuiAtlasLease;

/**
 * Named access to host-prepared resources. Implementations reuse
 * NativeClientResource.use lifetime checks.
 */
public interface GraphicsResources {
    default GraphicsMesh mesh(String key) {
        throw missing("mesh", key);
    }

    default GraphicsVertexBuffer buffer(String key) {
        throw missing("buffer", key);
    }

    default GuiAtlasLease image(String key) {
        throw missing("image", key);
    }

    default GraphicsShaderProgram program(String key) {
        throw missing("program", key);
    }

    default GraphicsFramebuffer target(String key) {
        throw missing("target", key);
    }

    static IllegalArgumentException missing(String type, String key) {
        return new IllegalArgumentException("Unknown prepared graphics " + type + ": " + key);
    }
}
