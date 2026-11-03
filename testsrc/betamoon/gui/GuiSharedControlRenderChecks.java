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
import betamoon.gui.widget.GuiRadioView;
import betamoon.gui.widget.GuiNumberBoxView;
import betamoon.gui.widget.GuiTextAreaView;
import betamoon.gui.widget.GuiBuiltInViews;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
        numericSteppers(minecraft, font, output);
        radioSelection(minecraft, font, output);
        multilineTextareas(minecraft, font, output);
        numericEntries(minecraft, font, output);
        selectionsAndStatus(minecraft, font, output);
        bitmapAtlas(minecraft, font, output);
        phaseSafeOwnershipLoss(minecraft, font);
    }

    private static void selectionsAndStatus(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        List<GuiStyledView> controls = new ArrayList<>();
        List<Rect> rectangles = new ArrayList<>();
        GuiContainer content = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                for (int index = 0; index < controls.size(); index++) {
                    controls.get(index).arrange(context, rectangles.get(index));
                }
            }
        };
        for (String presentation : Arrays.asList("linear", "segmented", "radial", "label")) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("presentation", presentation);
            values.put("value", 62.5);
            values.put("segments", 8);
            GuiStyledView status = GuiBuiltInViews.createControl("progress", GuiStyleSheet.MINIMAL,
                    GuiDefaultTemplate.INSTANCE, (event, value) -> true).configure(values);
            rectangles.add(presentation.equals("radial")
                    ? Rect.fromPositionAndSize(215, 65, 64, 64)
                    : Rect.fromPositionAndSize(30, 65 + controls.size() * 30, 150, 16));
            controls.add(status);
            content.add(status);
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("options",
                Arrays.asList(Collections.singletonMap("key", "Audio"), Collections.singletonMap("key", "Video")));
        options.put("selectedKey", "Audio");
        GuiStyledView tabs = GuiBuiltInViews
                .createControl("tabs", GuiStyleSheet.MINIMAL, GuiDefaultTemplate.INSTANCE, (event, value) -> true)
                .configure(options);
        rectangles.add(Rect.fromPositionAndSize(30, 20, 250, 20));
        controls.add(tabs);
        content.add(tabs);
        GuiStyledView select = GuiBuiltInViews
                .createControl("dropdown", GuiStyleSheet.MINIMAL, GuiDefaultTemplate.INSTANCE, (event, value) -> true)
                .configure(options);
        rectangles.add(Rect.fromPositionAndSize(30, 190, 150, 20));
        controls.add(select);
        content.add(select);
        GuiScene scene = new GuiScene();
        scene.setContent(content);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                new File(output, "shared-controls-status-tabs.png"));
        for (GuiStyledView control : controls) {
            if (control.kind().equals("progress")) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("presentation", control.templateValues(true).get("presentation"));
                values.put("indeterminate", true);
                control.configure(values);
            }
        }
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                new File(output, "shared-controls-status-indeterminate.png"));
        scene.getContext().requestFocus(select);
        scene.keyTyped('\r', Keyboard.KEY_RETURN, false);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png", new File(output, "shared-controls-dropdown.png"));
        scene.keyTyped('\0', Keyboard.KEY_ESCAPE, false);
        require(scene.getFocusedElement() == select && GL11.glGetError() == GL11.GL_NO_ERROR,
                "Rendered popup lost focus or status rendering leaked GL state");
        for (GuiStyledView control : controls) {
            control.close();
        }
    }

    private static void numericEntries(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        List<GuiNumberBoxView> controls = new ArrayList<>();
        GuiContainer content = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                for (int i = 0; i < controls.size(); i++) {
                    controls.get(i).arrange(context, Rect.fromPositionAndSize(70, 65 + i * 50, 180, 24));
                }
            }
        };
        for (GuiStyleSheet theme : Arrays.asList(GuiStyleSheet.MENU, GuiStyleSheet.MINIMAL)) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("value", 12.5);
            GuiNumberBoxView box = new GuiNumberBoxView(theme, GuiDefaultTemplate.INSTANCE, (event, value) -> true)
                    .configure(values);
            controls.add(box);
            content.add(box);
        }
        GuiScene scene = new GuiScene();
        scene.setContent(content);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        GuiNumberBoxView valid = controls.get(0);
        scene.getContext().requestFocus(valid);
        scene.keyTyped('\0', Keyboard.KEY_A, false, true);
        for (char character : "0.125".toCharArray()) {
            scene.keyTyped(character, 0, false);
        }
        scene.keyTyped('\0', Keyboard.KEY_RETURN, false);
        require(valid.templateValues(true).get("value").equals(0.125), "Rendered numberbox rounded unstepped input");
        GuiNumberBoxView invalid = controls.get(1);
        scene.getContext().requestFocus(invalid);
        scene.keyTyped('\0', Keyboard.KEY_A, false, true);
        for (char character : "1e-".toCharArray()) {
            scene.keyTyped(character, 0, false);
        }
        scene.keyTyped('\0', Keyboard.KEY_RETURN, false);
        require(invalid.interaction("invalid") && invalid.text().equals("1e-"), "Invalid numeric draft was discarded");
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                new File(output, "shared-controls-numberbox.png"));
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Numeric entry painter leaked invalid GL operations");
        for (GuiNumberBoxView box : controls) {
            box.close();
        }
    }

    private static void numericSteppers(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        List<GuiStyledView> controls = new ArrayList<>();
        GuiContainer content = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                for (int i = 0; i < controls.size(); i++) {
                    controls.get(i).arrange(context, Rect.fromPositionAndSize(70, 65 + i * 50, 180, 24));
                }
            }
        };
        for (GuiStyleSheet theme : Arrays.asList(GuiStyleSheet.MENU, GuiStyleSheet.MINIMAL)) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("text", "Amount ");
            values.put("step", 0.1);
            values.put("value", 0.2);
            GuiStyledView control = new GuiStyledView("stepper", theme, GuiDefaultTemplate.INSTANCE,
                    (event, value) -> true).configure(values);
            controls.add(control);
            content.add(control);
        }
        GuiScene scene = new GuiScene();
        scene.setContent(content);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        GuiStyledView stepper = controls.get(0);
        scene.mousePressed(stepper.getRight() - 3, stepper.getTop() + 5, 0);
        scene.mouseReleased(stepper.getRight() - 3, stepper.getTop() + 5, 0);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        require(stepper.text().equals("Amount 0.3"), "Rendered numeric stepper did not use shared decimal stepping");
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png", new File(output, "shared-controls-stepper.png"));
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Numeric stepper painter leaked invalid GL operations");
        for (GuiStyledView control : controls) {
            control.close();
        }
    }

    private static void radioSelection(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        List<GuiRadioView> controls = new ArrayList<>();
        GuiContainer content = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                controls.get(0).arrange(context, Rect.fromPositionAndSize(60, 30, 200, 80));
                controls.get(1).arrange(context, Rect.fromPositionAndSize(60, 140, 200, 24));
            }
        };
        for (GuiStyleSheet theme : Arrays.asList(GuiStyleSheet.MENU, GuiStyleSheet.MINIMAL)) {
            Map<String, Object> values = new LinkedHashMap<>();
            List<Map<String, Object>> options = new ArrayList<>();
            for (String key : Arrays.asList("Low", "Medium", "High")) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("key", key);
                option.put("enabled", !key.equals("Medium"));
                options.add(option);
            }
            values.put("options", options);
            values.put("selectedKey", "Low");
            values.put("optionHeight", 24);
            values.put("orientation", controls.isEmpty() ? "vertical" : "horizontal");
            GuiRadioView radio = new GuiRadioView(theme, GuiDefaultTemplate.INSTANCE, (event, value) -> true)
                    .configure(values);
            controls.add(radio);
            content.add(radio);
        }
        GuiScene scene = new GuiScene();
        scene.setContent(content);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        GuiRadioView radio = controls.get(0);
        for (int index : new int[]{2, 0}) {
            Rect bounds = radio.optionBounds(index);
            scene.mousePressed(bounds.getLeft() + 4, bounds.getTop() + 4, 0);
            scene.mouseReleased(bounds.getLeft() + 4, bounds.getTop() + 4, 0);
            scene.prepare();
            GuiShowcaseRenderTest.setupFrame();
            scene.render(0, 0, 0);
            require(radio.selectedKey().equals(index == 0 ? "Low" : "High"),
                    "Radio selection did not refresh its displayed input revision");
        }
        scene.getContext().requestFocus(controls.get(1));
        scene.keyTyped('\0', Keyboard.KEY_END, false);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png", new File(output, "shared-controls-radio.png"));
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Radio painter leaked invalid GL operations");
        for (GuiRadioView control : controls) {
            control.close();
        }
    }

    private static void multilineTextareas(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        List<GuiTextAreaView> controls = new ArrayList<>();
        AtomicInteger firstWidth = new AtomicInteger(200);
        GuiContainer content = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                controls.get(0).arrange(context, Rect.fromPositionAndSize(60, 20, firstWidth.get(), 90));
                controls.get(1).arrange(context, Rect.fromPositionAndSize(60, 130, 200, 85));
            }
        };
        for (GuiStyleSheet theme : Arrays.asList(GuiStyleSheet.MENU, GuiStyleSheet.MINIMAL)) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("text", controls.isEmpty()
                    ? "First line\nSecond line wraps across the available width.\nThird line\nFourth line\nFifth line"
                    : "Codes: a\u00a7cb\nA long unwrapped line keeps its selection and horizontal scrolling.\nLast line");
            values.put("wrap", controls.isEmpty());
            GuiTextAreaView area = new GuiTextAreaView(theme, GuiDefaultTemplate.INSTANCE, (event, value) -> true)
                    .configure(values);
            controls.add(area);
            content.add(area);
        }
        GuiScene scene = new GuiScene();
        scene.setContent(content);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        GuiTextAreaView area = controls.get(1);
        scene.getContext().requestFocus(area);
        scene.keyTyped('\0', Keyboard.KEY_HOME, false, true);
        scene.keyTyped('\0', Keyboard.KEY_DOWN, true, false);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        BufferedImage pixels = GuiShowcaseRenderTest.capture(320, 240);
        ImageIO.write(pixels, "png", new File(output, "shared-controls-textarea.png"));
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++) {
                int color = pixels.getRGB(x, y);
                require((color >> 16 & 255) <= (color >> 8 & 255) + 50,
                        "Plain textarea text was interpreted as a Minecraft color escape");
            }
        }
        Object cached = controls.get(0).frame().layout;
        for (int i = 0; i < 3; i++) {
            scene.prepare();
            GuiShowcaseRenderTest.setupFrame();
            scene.render(0, 0, 0);
        }
        require(controls.get(0).frame().layout == cached, "Idle rendering rebuilt textarea line layout");
        scene.getContext().requestFocus(controls.get(0));
        int caret = controls.get(0).editor().caret();
        int rows = controls.get(0).frame().layout.rows.size();
        firstWidth.set(120);
        scene.getContext().requestLayout();
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        require(controls.get(0).editor().caret() == caret && controls.get(0).frame().layout.rows.size() > rows,
                "Narrow reflow changed the caret or failed to wrap");
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                new File(output, "shared-controls-textarea-narrow.png"));
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Textarea painting leaked invalid GL operations");
        for (GuiTextAreaView control : controls) {
            control.close();
        }
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
