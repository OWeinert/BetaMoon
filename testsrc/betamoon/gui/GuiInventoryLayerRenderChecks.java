package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiFrameSnapshot;
import betamoon.gui.framework.GuiInputLifetime;
import betamoon.gui.framework.GuiLayer;
import betamoon.gui.framework.GuiScene;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;

/**
 * Native item/cursor painting can split a scene without creating another
 * displayed frame.
 */
final class GuiInventoryLayerRenderChecks {
    static void run(Minecraft client, FontRenderer font, File output) throws Exception {
        int[] samples = {0};
        int[] paints = {0, 0};
        GuiInputLifetime lifetime = new GuiInputLifetime();
        lifetime.activate();
        GuiElement content = new GuiElement() {
            @Override
            protected GuiFrameContent sampleFrameContent() {
                samples[0]++;
                return null;
            }

            @Override
            protected void renderElement(GuiContext context) {
                paints[0]++;
                context.getRenderer().drawRect(getBounds(), 0xFF202020);
            }
        };
        content.inputLifetime(lifetime);
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(client, font, 320, 240, 320, 240);
        scene.setContent(content);
        GuiElement dialog = new GuiElement() {
            @Override
            protected GuiFrameContent sampleFrameContent() {
                samples[0]++;
                return null;
            }

            @Override
            protected void renderElement(GuiContext context) {
                paints[1]++;
                context.getRenderer().drawRect(100, 80, 220, 160, 0xFFCC3344);
            }
        };
        GuiLayer layer = scene.openLayer(dialog, true);
        layer.ownedBy(content);
        scene.prepare();
        GuiShowcaseRenderTest.setupFrame();
        scene.renderContent(110, 90, 0.5F);
        GuiFrameSnapshot frame = scene.getDisplayedFrame();
        long time = scene.getContext().getCurrentTimeMillis();
        scene.getContext().getRenderer().drawRect(20, 20, 60, 60, 0xFF0066CC);
        scene.renderLayers();
        scene.getContext().getRenderer().drawRect(110, 90, 114, 94, 0xFFFFFFFF);
        require(scene.getDisplayedFrame() == frame && scene.getContext().getCurrentTimeMillis() == time
                && samples[0] == 2 && paints[0] == 1 && paints[1] == 1,
                "Split inventory paint resampled geometry/time or painted a scene part twice");
        BufferedImage image = GuiShowcaseRenderTest.capture(320, 240);
        require(image.getRGB(120, 100) == 0xFFCC3344 && image.getRGB(111, 91) == 0xFFFFFFFF,
                "Dialog/native cursor ordering changed across scene paint passes");
        require(image.getRGB(30, 30) != 0xFF0066CC && (image.getRGB(30, 30) & 255) > 0,
                "A scene-owned modal did not dim the native item pass");
        require(scene.mousePressed(30, 30, 0), "A split-painted modal leaked its native slot gesture");
        scene.mouseReleased(30, 30, 0);
        ImageIO.write(image, "png", new File(output, "inventory-layer-order.png"));
        lifetime.close();
        GuiShowcaseRenderTest.setupFrame();
        scene.renderContent(110, 90, 0);
        scene.getContext().getRenderer().drawRect(20, 20, 60, 60, 0xFF0066CC);
        scene.renderLayers();
        require(paints[1] == 1 && GuiShowcaseRenderTest.capture(320, 240).getRGB(30, 30) == 0xFF0066CC,
                "An expired inventory owner retained dialog paint/dimming before logical disposal");
        scene.setContent(null);
        System.out.println("Inventory split painting passed: one sampled frame/time, content/items/modal/cursor order "
                + "and immediate expired-owner fallback.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
