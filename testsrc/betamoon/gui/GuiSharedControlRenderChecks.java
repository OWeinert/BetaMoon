package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.GuiSharedControlsTest;
import betamoon.gui.framework.GuiTextureCache;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.style.GuiAtlasLease;
import betamoon.gui.style.GuiBitmapFont;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.widget.GuiStyledView;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Real pixels and GL resource lifetime for replaceable shared controls. */
final class GuiSharedControlRenderChecks {
    static void run(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        int index = 0;
        for (GuiStyleSheet theme : Arrays.asList(GuiStyleSheet.MENU, GuiStyleSheet.MINIMAL)) {
            Globals lua = JsePlatform.standardGlobals();
            LuaValue bind = lua.load("calls=0; return function(v) calls=calls+1; return {text='Launch'} end").call();
            List<String> events = new ArrayList<>();
            try (GuiPresentationTree tree = GuiSharedControlsTest.document(theme, events, bind,
                    node -> indexTemplate(theme, node.type))) {
                GuiScene scene = new GuiScene();
                scene.setDocument(tree);
                scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
                scene.prepare();
                GuiShowcaseRenderTest.setupFrame();
                scene.render(0, 0, 0);
                GuiStyledView button = (GuiStyledView) tree.element("button");
                scene.mousePressed(button.getLeft() + 3, button.getTop() + 3, 0);
                scene.mouseReleased(button.getLeft() + 3, button.getTop() + 3, 0);
                scene.keyTyped(' ', Keyboard.KEY_SPACE, false);
                scene.keyReleased(Keyboard.KEY_SPACE, false);
                require(events.size() == 2, "Replacing visual templates changed activation behavior");
                for (int frame = 0; frame < 10; frame++) {
                    GuiShowcaseRenderTest.setupFrame();
                    scene.render(button.getLeft() + 4, button.getTop() + 4, 0);
                }
                require(lua.get("calls").checkint() == 1, "Template paint evaluated a Lua binding");
                ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png", new File(output,
                        index++ == 0 ? "shared-controls-menu.png" : "shared-controls-custom-template.png"));
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Shared control painter leaked invalid GL operations");
            }
        }
        bitmapAtlas(minecraft, font, output);
        phaseSafeOwnershipLoss(minecraft, font);
    }

    private static void phaseSafeOwnershipLoss(Minecraft minecraft, FontRenderer font) {
        AtomicBoolean painting = new AtomicBoolean();
        AtomicBoolean fade = new AtomicBoolean();
        AtomicInteger callbacks = new AtomicInteger();
        GuiElement control = new GuiElement() {
            {
                setFocusable(true);
            }

            protected boolean onInput(GuiContext context, GuiInputEvent event) {
                if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                    requestFocus(context);
                    capturePointer(context);
                    return true;
                }
                if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
                    require(!painting.get(), "Opacity sampling delivered pointer cancellation during paint");
                    callbacks.incrementAndGet();
                    return true;
                }
                return false;
            }

            protected void onFocusChanged(GuiContext context, boolean focused) {
                if (!focused) {
                    require(!painting.get(), "Opacity sampling delivered a focus callback during paint");
                    callbacks.incrementAndGet();
                }
            }
        };
        GuiContainer root = new GuiContainer() {
            protected void renderBeforeChildren(GuiContext context) {
                if (fade.get()) {
                    painting.set(true);
                    control.setOpacity(0);
                }
            }

            protected void renderAfterChildren(GuiContext context) {
                painting.set(false);
            }
        };
        root.add(control);
        GuiScene scene = new GuiScene();
        scene.setContent(root);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        scene.mousePressed(5, 5, 0);
        fade.set(true);
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        require(callbacks.get() == 0 && scene.getFocusedElement() == null,
                "Paint did not revoke eligibility immediately while deferring handlers");
        scene.prepare();
        require(callbacks.get() == 2 && scene.mouseReleased(5, 5, 0),
                "Deferred ownership loss missed cleanup or leaked the physical release");
    }

    private static GuiVisualTemplate indexTemplate(GuiStyleSheet theme, String type) {
        if (theme == GuiStyleSheet.MENU || !"button".equals(type)) {
            return GuiDefaultTemplate.INSTANCE;
        }
        return new GuiVisualTemplate() {
            public Set<String> parts() {
                return GuiDefaultTemplate.INSTANCE.parts();
            }

            public void paint(GuiContext context, GuiStyledView view, GuiControlStyle style) {
                Rect bounds = view.getBounds();
                context.getRenderer().drawRect(bounds, view.interaction("hovered") ? 0xFF183F59 : 0xFF102536);
                context.getRenderer().drawRect(bounds.getLeft(), bounds.getTop(), bounds.getLeft() + 4,
                        bounds.getBottom(), 0xFF59D5C2);
                style.font().draw(context, "> " + view.text(), bounds.getLeft() + 12, bounds.getTop() + 8, 0xFF59D5C2,
                        false);
                if (view.isFocused()) {
                    context.getRenderer().drawRect(bounds.getLeft(), bounds.getBottom() - 1, bounds.getRight(),
                            bounds.getBottom(), 0xFF59D5C2);
                }
            }
        };
    }

    private static void bitmapAtlas(Minecraft minecraft, FontRenderer nativeFont, File output) throws Exception {
        GuiTextureCache cache = new GuiTextureCache();
        BufferedImage image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, x < 8 ? 0xFFFFFFFF : 0xFF55AAFF);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        GuiTextureCache.Texture texture = cache.get("shared-controls-font", bytes.toByteArray(), 1);
        AtomicBoolean active = new AtomicBoolean(true);
        GuiAtlasLease lease = new GuiAtlasLease() {
            public int width() {
                return 16;
            }

            public int height() {
                return 8;
            }

            public int textureId() {
                return texture.getTextureId();
            }

            public void requireActive() {
                if (!active.get()) {
                    throw new IllegalStateException("Expired test atlas");
                }
            }
        };
        GuiBitmapFont font = new GuiBitmapFont(lease, 10, '?',
                Collections.singletonMap((int) '?', new GuiBitmapFont.Glyph(0, 0, 8, 8, 10, 0, 0)));
        GuiControlStyle base = GuiStyleSheet.MINIMAL.resolve("default", 0)
                .merge(GuiControlStyle.of("font", font, "backgroundDrawable", GuiDrawable.image(lease, 8, 0, 8, 8, 2)));
        GuiStyleSheet style = new GuiStyleSheet(
                Collections.singletonMap("default", new GuiStyleSheet.Rule(null, base, Collections.emptyMap())));
        refreshedMetrics(minecraft, nativeFont, style);
        GuiStyledView text = new GuiStyledView("text", style, GuiDefaultTemplate.INSTANCE, (event, value) -> true)
                .configure(GuiSharedControlsTest.values("text", "AB?"));
        GuiScene scene = new GuiScene();
        scene.setContent(text);
        scene.updateEnvironment(minecraft, nativeFont, 320, 240, 320, 240);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        require(text.getWidth() == 30 && text.getHeight() == 10, "Layout used native metrics for a bitmap font");
        BufferedImage pixels = GuiShowcaseRenderTest.capture(320, 240);
        require((pixels.getRGB(text.getLeft() + 2, text.getTop() + 2) & 0xFFFFFF) == 0xE0E0E0,
                "Prepared bitmap glyph geometry did not match its metric bounds");
        ImageIO.write(pixels, "png", new File(output, "shared-controls-bitmap-font.png"));
        GuiStyledView panel = new GuiStyledView("panel", style, GuiDefaultTemplate.INSTANCE, (event, value) -> true)
                .configure(GuiSharedControlsTest.values("width", 80, "height", 40));
        scene.setContent(panel);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        require((GuiShowcaseRenderTest.capture(320, 240).getRGB(160, 120) & 0xFFFFFF) == 0x55AAFF,
                "Prepared nine-slice did not draw its center");
        active.set(false);
        cache.clear();
        require(!GL11.glIsTexture(texture.getTextureId()), "Atlas owner retained a disposed GPU texture");
        try {
            font.width(scene.getContext(), "A");
            throw new AssertionError("Expired atlas remained usable through font metrics");
        } catch (IllegalStateException expected) {
            // Borrowed fonts and drawables never extend their owner's lifetime.
        }
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Bitmap/drawable operations leaked invalid GL state");
    }

    private static void refreshedMetrics(Minecraft minecraft, FontRenderer font, GuiStyleSheet bitmapStyle) {
        AtomicReference<GuiStyleSheet> theme = new AtomicReference<>(GuiStyleSheet.MINIMAL);
        GuiPresentationTree tree = GuiSharedControlsTest.document(theme::get);
        GuiScene scene = new GuiScene();
        scene.setDocument(tree);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        int nativeWidth = tree.element("button").getMeasuredSize().getWidth();
        GuiStyledView checkbox = (GuiStyledView) tree.element("checkbox");
        scene.mousePressed(checkbox.getLeft() + 2, checkbox.getTop() + 2, 0);
        scene.mouseReleased(checkbox.getLeft() + 2, checkbox.getTop() + 2, 0);
        require(checkbox.selected(), "Refresh fixture did not select the control");
        theme.set(bitmapStyle);
        tree = tree.reload(GuiSharedControlsTest.definition(LuaValue.NIL));
        scene.setDocument(tree);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        require(tree.element("button").getMeasuredSize().getWidth() != nativeWidth,
                "Font refresh did not invalidate layout metrics");
        require(((GuiStyledView) tree.element("checkbox")).selected(), "Font/skin refresh reset local control state");
        tree.close();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
