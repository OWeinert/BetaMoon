package betamoon.gui.interop;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.framework.GuiPreparationPhase;
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
import betamoon.interop.runtime.NativePermissions;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ItemStack;
import net.minecraft.src.RenderHelper;
import net.minecraft.src.RenderItem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

/**
 * Curated Java screen integration. Ordinary Lua receives no generic client task
 * or trusted-method assertion.
 */
public final class NativeClientScreenHost implements AutoCloseable {
    private final Minecraft client;
    private GuiScreen screen;
    private boolean closed;
    private final boolean detached;
    private final boolean overlay;
    private BooleanSupplier overlayAttached;
    private final NativeClientSession session;
    private final GuiRenderer renderer = new GuiRenderer(GuiTheme.DEFAULT);
    private NativeClientResource<FontRenderer> font;
    private NativeClientResource<RenderItem> items;
    private final FloatBuffer itemOpacity = BufferUtils.createFloatBuffer(4);
    private NativeClientAction close;

    public NativeClientScreenHost(Minecraft client, GuiScreen screen, String owner) {
        this(client, screen, owner, false, null);
    }

    private NativeClientScreenHost(Minecraft client, GuiScreen screen, String owner, boolean detached,
            BooleanSupplier overlayAttached) {
        NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        if (bindings == null || client == null || screen == null || !detached && client.currentScreen != screen) {
            throw new NativeAccessFailure("native_client_unavailable", "initialize an active native client screen");
        }
        this.client = client;
        this.screen = screen;
        this.detached = detached;
        this.overlay = overlayAttached != null;
        this.overlayAttached = overlayAttached;
        session = bindings.clients.create(owner, this::roots);
    }

    /**
     * Java-managed candidate preparation; the candidate still cannot paint or
     * receive input.
     */
    public static NativeClientScreenHost prepareDetached(Minecraft client, GuiScreen screen, String owner) {
        return new NativeClientScreenHost(client, screen, owner, true, null);
    }

    /**
     * Java-owned overlay viewport. The existing session still observes the actual
     * current native screen, including a worldless or gameplay null screen.
     */
    public static NativeClientScreenHost prepareOverlay(Minecraft client, GuiScreen viewport, String owner,
            BooleanSupplier attached) {
        if (attached == null) {
            throw new IllegalArgumentException("Overlay attachment predicate is required");
        }
        return new NativeClientScreenHost(client, viewport, owner, true, attached);
    }

    private boolean attached() {
        return !closed && screen != null
                && (overlay
                        ? overlayAttached != null && overlayAttached.getAsBoolean()
                        : client.currentScreen == screen);
    }

    private void requireAttached() {
        if (!attached()) {
            throw new NativeAccessFailure("native_client_screen_inactive", "use an attached owning screen");
        }
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
            if (!overlay) {
                close = session.action("close-owned-screen", () -> {
                    if (attached()) {
                        client.displayGuiScreen(null);
                    }
                }, true);
            }
        });
    }

    public NativeEntry queryEntry(LuaCallbackScope callback) {
        return new NativeEntry(NativeDiscoveryRuntime.bindingsIfAvailable(), callback,
                callback.isBinding() ? NativeEntry.Phase.BINDING : NativeEntry.Phase.QUERY, NativePermissions.READ_ONLY,
                roots(), () -> session.active() && (!overlay || attached()));
    }

    public NativeEntry actionEntry(LuaCallbackScope callback) {
        requireAttached();
        return NativeEntry.clientAction(NativeDiscoveryRuntime.bindingsIfAvailable(), callback, roots(),
                () -> attached() && session.active());
    }

    public NativeClientSession session() {
        return session;
    }

    public NativeClientAction closeAction() {
        return close;
    }

    public void input(Consumer<NativeClientActionPermit> callback) {
        requireAttached();
        session.input(callback);
    }

    public void observe(Runnable observer) {
        requireAttached();
        session.observe(observer);
    }

    /**
     * Logical Java orchestration lets callbacks and prepared resources enter their
     * own audited scopes.
     */
    public void update(Runnable updater) {
        requireAttached();
        if (!session.active()) {
            throw new NativeAccessFailure("native_client_screen_inactive", "owning screen session is unavailable");
        }
        try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
            updater.run();
        }
        requireAttached();
        if (!session.active()) {
            throw new NativeAccessFailure("native_client_screen_inactive",
                    "owning screen session changed during update");
        }
    }

    public void paint(Runnable painter) {
        requireAttached();
        session.paint(() -> {
            GraphicsClip.Pixels clip = new GraphicsClip(0, 0, screen.width, screen.height).pixels(screen.width,
                    screen.height, client.displayWidth, client.displayHeight);
            try (GraphicsRenderScope scope = GraphicsRenderScope.open(this::requirePaint, GraphicsStateGuard::enter,
                    GraphicsTransform.IDENTITY, clip)) {
                if (overlay) {
                    GL11.glViewport(0, 0, client.displayWidth, client.displayHeight);
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glLoadIdentity();
                    GL11.glOrtho(0, screen.width, screen.height, 0, 1000, 3000);
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glLoadIdentity();
                    GL11.glTranslatef(0, 0, -2000);
                }
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

    /**
     * Cosmetic item layers with native pickup motion and an independent opacity
     * multiplier.
     */
    public void drawItemLayers(ItemStack stack, ItemStack overlay, int x, int y, float opacity, double pickup,
            boolean hotbarLighting) {
        requirePaint();
        if (stack == null || opacity <= 0) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT
                | GL11.GL_LIGHTING_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (opacity < 1) {
                if (!GLContext.getCapabilities().OpenGL13 && !GLContext.getCapabilities().GL_ARB_texture_env_combine) {
                    throw new NativeAccessFailure("item_opacity_unavailable", "Item opacity requires texture combine");
                }
                itemOpacity.clear();
                itemOpacity.put(new float[]{1, 1, 1, opacity}).flip();
                GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, itemOpacity);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PRIMARY_COLOR);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_CONSTANT);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
            }
            GL11.glPushMatrix();
            try {
                if (hotbarLighting) {
                    GL11.glRotatef(120, 1, 0, 0);
                }
                RenderHelper.enableStandardItemLighting();
            } finally {
                GL11.glPopMatrix();
            }
            GL11.glPushMatrix();
            try {
                if (pickup > 0) {
                    double factor = 1 + Math.min(5, pickup) / 5;
                    GL11.glTranslated(x + 8, y + 12, 0);
                    GL11.glScaled(1 / factor, (factor + 1) / 2, 1);
                    GL11.glTranslated(-x - 8, -y - 12, 0);
                }
                items.use().renderItemIntoGUI(font.use(), client.renderEngine, stack, x, y);
            } finally {
                GL11.glPopMatrix();
            }
            items.use().renderItemOverlayIntoGUI(font.use(), client.renderEngine, overlay, x, y);
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } finally {
            closed = true;
            screen = null;
            overlayAttached = null;
        }
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
        if (closed) {
            return Collections.emptyMap();
        }
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("client", client);
        roots.put("world", client.theWorld);
        roots.put("player", client.thePlayer);
        roots.put("screen", overlay ? client.currentScreen : detached ? screen : client.currentScreen);
        roots.put("font", client.fontRenderer);
        roots.put("textures", client.renderEngine);
        return roots;
    }
}
