package betamoon.gui;

import betamoon.gui.component.GuiComponentTest.PaintFixture;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.graphics.GraphicsTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Driver-backed component pixels, hit geometry and deep retained composition.
 */
final class GuiPreparedPaintRenderChecks {
    static void run(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        try (PaintFixture fixture = new PaintFixture()) {
            fixture.scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
            fixture.scene.prepare();
            GuiShowcaseRenderTest.setupFrame();
            fixture.scene.render(0, 0, 0);
            GuiElement first = fixture.first();
            int x = first.getLeft() + 100;
            int y = first.getTop() + 60;
            require((GuiShowcaseRenderTest.capture(320, 240).getRGB(x, y) & 0xFFFFFF) == 0x55AAFF,
                    "Prepared ring did not render its declared color");
            require(!fixture.scene.mousePressed(first.getLeft() + 12, first.getTop() + 12, 0),
                    "Irregular painted control captured its corner");
            fixture.scene.mousePressed(first.getLeft() + 60, y, 0);
            fixture.scene.mouseReleased(first.getLeft() + 60, y, 0);
            fixture.scene.prepare();
            for (int frame = 0; frame < 10; frame++) {
                GuiShowcaseRenderTest.setupFrame();
                fixture.scene.render(x, y, 0);
            }
            BufferedImage image = GuiShowcaseRenderTest.capture(320, 240);
            require((image.getRGB(x, y) & 0xFFFFFF) == 0x88FF55, "Custom input did not update its prepared visual");
            require(fixture.lua.get("paintCalls").checkint() == 3 && fixture.failures.isEmpty(),
                    "Rendering evaluated a prepared Lua builder or failed");
            ImageIO.write(image, "png", new File(output, "prepared-component-radial.png"));
        }
        deepComposition(minecraft, font);
    }

    private static void deepComposition(Minecraft minecraft, FontRenderer font) {
        GuiContainer root = new Clipped();
        GuiContainer parent = root;
        for (int index = 0; index < 40; index++) {
            GuiContainer child = new Clipped();
            child.setVisualTransform(new GraphicsTransform(1, 0, 1, 1));
            parent.add(child);
            parent = child;
        }
        parent.add(new GuiElement() {
            @Override
            protected void renderElement(GuiContext context) {
                context.getRenderer().drawRect(Rect.fromPositionAndSize(0, 0, 100, 100), 0xFF55AAFF);
            }
        });
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.setContent(root);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(0, 0, 0);
        BufferedImage pixels = GuiShowcaseRenderTest.capture(320, 240);
        require((pixels.getRGB(50, 50) & 0xFFFFFF) == 0x55AAFF && (pixels.getRGB(10, 50) & 0xFFFFFF) != 0x55AAFF,
                "Deep retained transforms/clips changed painted geometry");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Retained depth exhausted a native graphics stack");
        scene.setContent(null);
    }

    private static final class Clipped extends GuiContainer {
        @Override
        protected void renderElement(GuiContext context) {
            context.getRenderer().pushClip(getBounds());
            try {
                super.renderElement(context);
            } finally {
                context.getRenderer().popClip();
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
