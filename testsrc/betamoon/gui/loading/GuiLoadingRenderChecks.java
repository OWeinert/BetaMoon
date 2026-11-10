package betamoon.gui.loading;

import betamoon.assets.io.BuiltinAssetProvider;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.NativeTextureResources;
import betamoon.gui.GuiTeleportProgress;
import betamoon.gui.interop.GuiResourceBudget;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.luamodloader.LoadingAssetFixture;
import betamoon.teleport.TeleportTicket;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.LoadingScreenRenderer;
import net.minecraft.src.MinecraftError;
import net.minecraft.src.ScaledResolution;
import net.minecraft.src.TexturePackBase;
import org.lwjgl.opengl.GL11;

/**
 * Driver-backed fake-client checks. No Minecraft process, display or world is
 * launched.
 */
public final class GuiLoadingRenderChecks {
    private static final String OVERRIDE = "loading-override-render-fixture";
    private static final String DIMENSION = "loading-dimension-render-fixture";
    private static final String ARTIST = "loading-asset-render-fixture";

    public static void run(Minecraft client, File output, BiFunction<Integer, Integer, BufferedImage> capture)
            throws Exception {
        LoadingScreenRenderer original = client.loadingScreen;
        int previousWidth = client.displayWidth;
        int previousHeight = client.displayHeight;
        boolean running = client.running;
        int previousScale = client.gameSettings.guiScale;
        long budget = GuiResourceBudget.allocated();
        ClientAssets.useProviders(BuiltinAssetProvider.INSTANCE, null, message -> {
        });
        NativeTextureResources.useTexturePack(new TexturePackBase() {
            @Override
            public InputStream getResourceAsStream(String path) {
                return LoadingScreenRenderer.class.getResourceAsStream(path);
            }
        });
        client.loadingScreen = new LoadingScreenRenderer(client);
        client.running = true;
        client.displayWidth = 320;
        client.displayHeight = 240;
        client.gameSettings.guiScale = 1;
        try {
            GuiLoadingAppearance override = appearance(OVERRIDE, "betamoon:gui/controls/button/normal", "tile",
                    "#FF224466");
            GuiLoadingAppearance dimension = appearance(DIMENSION, "minecraft:image/gui/background", "cover",
                    "#FF668822");
            GuiLoadingStatus status = new GuiLoadingStatus("Entering the Moon");
            status.message = "Preparing terrain";
            status.progress(63);
            try (GuiLoadingResources resources = new GuiLoadingResources(client, override, dimension)) {
                require(resources.selected().appearance == override && resources.selected().image != null,
                        "Per-transfer artwork did not precede the destination default");
                int ownedImage = resources.selected().image.textureId();
                require(GuiResourceBudget.allocated() > budget,
                        "Owned loading artwork bypassed the shared resource budget");
                GuiLoadingPainter painter = new GuiLoadingPainter(client, resources, status);
                painter.paint(320, 240);
                BufferedImage staged = capture.apply(320, 240);
                ImageIO.write(staged, "png", new File(output, "loading-styled.png"));
                AtomicLong now = new AtomicLong(1000);
                AtomicInteger swaps = new AtomicInteger();
                LoadingScreenRenderer previous = client.loadingScreen;
                try (GuiLoadingRenderer renderer = new GuiLoadingRenderer(client, painter, status, now::get,
                        swaps::incrementAndGet)) {
                    renderer.printText(status.title);
                    renderer.displayLoadingString(status.message);
                    renderer.setLoadingProgress(63);
                    BufferedImage blocking = capture.apply(320, 240);
                    require(equal(staged, blocking), "Blocking and staged loading painters produced different pixels");
                    int frames = swaps.get();
                    now.addAndGet(19);
                    renderer.setLoadingProgress(64);
                    require(swaps.get() == frames, "Blocking loading bypassed native 20ms throttling");
                    now.incrementAndGet();
                    renderer.setLoadingProgress(65);
                    require(swaps.get() == frames + 1 && status.percent() == 65,
                            "Native progress did not reach the shared Java status");
                    client.displayWidth = 640;
                    client.displayHeight = 360;
                    now.addAndGet(20);
                    renderer.setLoadingProgress(75);
                    require(swaps.get() == frames + 2, "Resized blocking loading did not paint");
                    ImageIO.write(capture.apply(640, 360), "png", new File(output, "loading-styled-wide.png"));
                    for (int scale : new int[]{0, 1, 2, 3}) {
                        client.gameSettings.guiScale = scale;
                        ScaledResolution resolution = new ScaledResolution(client.gameSettings, client.displayWidth,
                                client.displayHeight);
                        painter.paint(resolution.getScaledWidth(), resolution.getScaledHeight());
                        BufferedImage scaled = capture.apply(640, 360);
                        now.addAndGet(20);
                        renderer.setLoadingProgress(75);
                        require(equal(scaled, capture.apply(640, 360)),
                                "Blocking/staged loading diverged at GUI scale " + scale);
                    }
                    ScriptResourceTracker.unload(OVERRIDE);
                    require(!GL11.glIsTexture(ownedImage) && resources.selected().appearance == dimension,
                            "Owner unload retained owned GPU artwork or skipped the prepared destination fallback");
                    ScriptResourceTracker.unload(DIMENSION);
                    require(resources.selected().appearance == GuiLoadingAppearance.MINECRAFT,
                            "Expired destination appearance did not select prepared Minecraft fallback");
                    client.running = false;
                    try {
                        renderer.setLoadingProgress(1);
                        throw new AssertionError("Stopped Minecraft did not preserve native abort semantics");
                    } catch (MinecraftError expected) {
                        // The adapter delegates stopped-client policy to the original renderer.
                    }
                    renderer.func_594_b("ignored");
                    renderer.setLoadingProgress(2);
                    client.running = true;
                }
                require(client.loadingScreen == previous, "Owned adapter did not restore the original native renderer");
            }
            require(GuiResourceBudget.allocated() == budget, "Loading fallback chain retained resource reservations");
            LoadingAssetFixture.publish(ARTIST);
            GuiLoadingAppearance sharedArt = appearance(OVERRIDE, "fixture:loading-background", "tile", "#FFFFFFFF");
            try (GuiLoadingResources crossOwner = new GuiLoadingResources(client, sharedArt, null)) {
                require(crossOwner.selected().appearance == sharedArt,
                        "Separately owned registered artwork was not prepared");
                int texture = crossOwner.selected().image.textureId();
                ScriptResourceTracker.unload(ARTIST);
                require(sharedArt.available() && !GL11.glIsTexture(texture)
                        && crossOwner.selected().appearance == GuiLoadingAppearance.MINECRAFT,
                        "Asset owner unload retained an otherwise live appearance's artwork");
            }
            try (GuiLoadingResources missing = new GuiLoadingResources(client,
                    appearance(OVERRIDE, "fixture:missing", "stretch", "#FFFFFFFF"), null)) {
                require(missing.selected().appearance == GuiLoadingAppearance.MINECRAFT,
                        "Missing artwork prevented prepared native fallback");
            }
            screenLifetime(client);
        } finally {
            ScriptResourceTracker.unload(OVERRIDE);
            ScriptResourceTracker.unload(DIMENSION);
            ScriptResourceTracker.unload(ARTIST);
            NativeTextureResources.useTexturePack(null);
            client.loadingScreen = original;
            client.running = running;
            client.displayWidth = previousWidth;
            client.displayHeight = previousHeight;
            client.gameSettings.guiScale = previousScale;
        }
        require(GuiResourceBudget.allocated() == budget, "Loading screen disposal retained owned resources");
        System.out.println("Loading presentation passed: shared staged/blocking pixels, prepared fallback precedence, "
                + "resize, native throttle/abort, owner unload, resource budgets and explicit transfer lifetime.");
    }

    private static void screenLifetime(Minecraft client) {
        GuiLoadingAppearance style = appearance(OVERRIDE, "betamoon:gui/controls/button/normal", "stretch",
                "#FFFFFFFF");
        LoadingScreenRenderer previous = client.loadingScreen;
        GuiTeleportProgress screen = new GuiTeleportProgress(new TeleportTicket(), "Moon", "Entering", style, null);
        screen.setWorldAndResolution(client, 320, 240);
        LoadingScreenRenderer adapted = client.loadingScreen;
        require(adapted != previous, "Styled transfer did not install the owned native adapter");
        screen.onGuiClosed();
        screen.setWorldAndResolution(client, 640, 360);
        require(client.loadingScreen == adapted, "Native screen closure/resize disposed a live transfer's adapter");
        LoadingScreenRenderer replacement = new LoadingScreenRenderer(client);
        client.loadingScreen = replacement;
        screen.closePresentation();
        screen.closePresentation();
        require(client.loadingScreen == replacement, "Closing a transfer overwrote another native renderer owner");
        client.loadingScreen = previous;
    }

    private static GuiLoadingAppearance appearance(String owner, String image, String mode, String tint) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("background", image);
        value.put("mode", mode);
        value.put("tint", tint);
        return new GuiLoadingAppearance(owner, value);
    }

    private static boolean equal(BufferedImage left, BufferedImage right) {
        for (int y = 0; y < left.getHeight(); y++) {
            for (int x = 0; x < left.getWidth(); x++) {
                if (left.getRGB(x, y) != right.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
