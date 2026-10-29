package betamoon.gui.interop;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.framework.GuiTheme;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsRenderScope;
import betamoon.gui.graphics.GraphicsStateGuard;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.client.NativeClientAction;
import betamoon.interop.client.NativeClientActionPermit;
import betamoon.interop.client.NativeClientResource;
import betamoon.interop.client.NativeClientSession;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ItemStack;
import net.minecraft.src.RenderHelper;
import net.minecraft.src.RenderItem;
import org.lwjgl.opengl.GL11;

/**
 * Curated Java screen integration. Ordinary Lua receives no generic client task
 * or trusted-method assertion.
 */
public final class NativeClientScreenHost implements AutoCloseable {
    private final Minecraft client;
    private final GuiScreen screen;
    private final NativeClientSession session;
    private final GuiRenderer renderer = new GuiRenderer(GuiTheme.DEFAULT);
    private NativeClientResource<FontRenderer> font;
    private NativeClientResource<RenderItem> items;
    private NativeClientAction close;

    public NativeClientScreenHost(Minecraft client, GuiScreen screen, String owner) {
        NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        if (bindings == null || client == null || screen == null || client.currentScreen != screen) {
            throw new NativeAccessFailure("native_client_unavailable", "initialize an active native client screen");
        }
        this.client = client;
        this.screen = screen;
        session = bindings.clients.create(owner, this::roots);
    }

    public void initialize() {
        session.prepare(() -> {
            if (client.fontRenderer == null || client.renderEngine == null) {
                throw new NativeAccessFailure("native_client_resource_unavailable",
                        "client rendering resources are not ready");
            }
            font = session.resource(() -> client.fontRenderer, ignored -> {
                // Minecraft owns its shared font; only this host's lease is released.
            });
            items = session.resource(RenderItem::new, ignored -> {
                // The GUI item adapter has no independently allocated native resource.
            });
            close = session.action("close-owned-screen", () -> {
                if (client.currentScreen == screen) {
                    client.displayGuiScreen(null);
                }
            }, true);
        });
    }

    public NativeClientSession session() {
        return session;
    }

    public NativeClientAction closeAction() {
        return close;
    }

    public void input(Consumer<NativeClientActionPermit> callback) {
        session.input(callback);
    }

    public void paint(Runnable painter) {
        session.paint(() -> {
            GraphicsClip.Pixels clip = new GraphicsClip(0, 0, screen.width, screen.height).pixels(screen.width,
                    screen.height, client.displayWidth, client.displayHeight);
            try (GraphicsRenderScope scope = GraphicsRenderScope.open(this::requirePaint, GraphicsStateGuard::enter,
                    GraphicsTransform.IDENTITY, clip)) {
                painter.run();
            }
        });
    }

    public NativeClientResource<PreparedClientImage> prepareImage(byte[] png, boolean nearest) {
        return session.resource(() -> new PreparedClientImage(png, nearest), PreparedClientImage::close);
    }

    public int textWidth(String text) {
        if (text == null || text.length() > 65536) {
            throw new NativeAccessFailure("invalid_client_text", "text must contain at most 65536 UTF-16 units");
        }
        return font.use().getStringWidth(text);
    }

    public void drawText(String text, int x, int y, int color) {
        requirePaint();
        textWidth(text);
        font.use().drawStringWithShadow(text, x, y, color);
    }

    public void drawImage(NativeClientResource<PreparedClientImage> image, int x, int y, int width, int height) {
        requirePaint();
        if (width < 0 || height < 0 || width > 16384 || height > 16384) {
            throw new NativeAccessFailure("invalid_client_image", "image display dimensions must be within 0..16384");
        }
        renderer.drawTexture(image.use().texture(), new Rect(x, y, width, height));
    }

    public void drawItem(ItemStack stack, int x, int y) {
        requirePaint();
        if (stack == null) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT
                | GL11.GL_LIGHTING_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            RenderHelper.enableStandardItemLighting();
            RenderItem renderer = items.use();
            renderer.renderItemIntoGUI(font.use(), client.renderEngine, stack, x, y);
            renderer.renderItemOverlayIntoGUI(font.use(), client.renderEngine, stack, x, y);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    @Override
    public void close() {
        session.close();
    }

    private void requirePaint() {
        NativeContext context = NativeContext.current();
        if (context.phase != NativeEntry.Phase.QUERY) {
            throw new NativeAccessFailure("native_client_paint_required",
                    "draw through the owning host's paint boundary");
        }
        context.check();
    }

    private Map<String, Object> roots() {
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("client", client);
        roots.put("world", client.theWorld);
        roots.put("player", client.thePlayer);
        roots.put("screen", client.currentScreen);
        roots.put("font", client.fontRenderer);
        roots.put("textures", client.renderEngine);
        return roots;
    }
}
