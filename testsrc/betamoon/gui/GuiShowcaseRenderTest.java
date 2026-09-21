package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.GuiTextureCache;
import betamoon.gui.widget.GuiIcon;
import betamoon.io.IoUtils;
import betamoon.luaapi.minecraft.MinecraftApi;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luamodloader.NonReloadableScriptRegistry;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import betamoon.resources.LuaTextureResources;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.GuiLuaContainer;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.update.SemanticVersion;
import betamoon.update.UpdateRelease;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.GameSettings;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.RenderEngine;
import net.minecraft.src.Tessellator;
import net.minecraft.src.UnexpectedThrowable;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

/**
 * Optional off-screen integration check; requires LWJGL 2 natives and Minecraft
 * resources.
 */
public final class GuiShowcaseRenderTest {
    public static void main(String[] args) throws Exception {
        File examples = new File(args[0]);
        File output = new File(args[1]);
        output.mkdirs();
        // Use only a build-owned scripts directory, never an installed player's files.
        File scriptRoot = new File(IoUtils.resolveMinecraftDirFromCodeSource(LuaTextureResources.class), "lua_scripts");
        if (!scriptRoot.getCanonicalPath().startsWith(new File("build").getCanonicalPath() + File.separator)) {
            throw new IllegalStateException("Run with the Gradle build/classes outputs on the classpath");
        }
        File assets = new File(scriptRoot, "gui_showcase");
        assets.mkdirs();
        for (File asset : new File(examples, "gui_showcase").listFiles()) {
            Files.copy(asset.toPath(), new File(assets, asset.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        // Skip only ModLoader's startup registration; definitions use the actual Lua
        // API.
        Field registered = TileEntityRegistry.class.getDeclaredField("minecraftTypeRegistered");
        registered.setAccessible(true);
        registered.setBoolean(null, true);
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "03_adv_06_gui_showcase.lua");
        Globals globals = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        TileEntityApi.attach(api);
        MinecraftApi.attach(api);
        globals.set("betamoon", api);
        // Block/item/recipe registration needs a running ModLoader client. Capture
        // those calls, while parsing tile, container and GUI definitions unchanged.
        globals.load("betamoon.blocks = {getRequired=function(_, id) return {id=id} end, "
                + "add=function(_, def) betamoon.result=def; return {id=def.id} end}; "
                + "betamoon.items = {getRequired=betamoon.blocks.getRequired}; "
                + "betamoon.recipes = {add=function(_, def) betamoon.recipe=def end}; "
                + "betamoon.stack=function(item, count) return {id=item.id, count=count} end").call();
        FileReader reader = new FileReader(new File(examples, "03_adv_06_gui_showcase.lua"));
        try {
            globals.load(reader, "03_adv_06_gui_showcase.lua").call();
        } finally {
            reader.close();
        }
        globals.get("modInit").call();
        owner.invoke(null, new Object[]{null});
        ContainerGuiDefinition definition = ((TileEntityApi.GuiHandle) api.get("result").get("gui")).definition;
        LuaTileEntity entity = new LuaTileEntity(definition.container.tileEntity.name);
        Set<Integer> pages = new HashSet<Integer>();
        Set<Integer> values = new HashSet<Integer>();
        for (int tick = 0; tick < 640; tick++) {
            definition.container.tileEntity.tickAction.call(entity.createContext());
            pages.add(entity.getDataInt("page"));
            values.add(entity.getDataInt("progress"));
        }
        require(pages.size() == 4 && values.containsAll(Arrays.asList(0, 1, 100)),
                "Tour or progress edge cases failed");
        for (int page = 1; page <= 4; page++) {
            entity.setInventorySlotContents(0, new ItemStack(4, page, 0));
            definition.container.tileEntity.tickAction.call(entity.createContext());
            require(entity.getDataInt("page") == page && entity.getStackInSlot(0).stackSize == page,
                    "Page selection consumed or ignored items");
        }
        entity.setInventorySlotContents(1, new ItemStack(331, 7, 0));

        Pbuffer buffer = new Pbuffer(320, 240, new PixelFormat(8, 24, 0), null, null);
        try {
            buffer.makeCurrent();
            GameSettings settings = new GameSettings();
            AssetEngine engine = new AssetEngine(settings);
            FontRenderer font = new FontRenderer(settings, "/font/default.png", engine);
            Minecraft minecraft = new TestMinecraft();
            minecraft.gameSettings = settings;
            minecraft.renderEngine = engine;
            minecraft.fontRenderer = font;
            verifyIssueLayout(font);
            verifyTextureRenderState(minecraft, font);
            renderFrameworkScreens(minecraft, font, output);
            renderUpdateNotice(minecraft, font, output);
            setupFrame();
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LESS);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GuiRenderer.drawTooltipLines(font, 320, 240, Arrays.asList("Visible tooltip", "Second line"), 20, 20);
            require(GL11.glIsEnabled(GL11.GL_LIGHTING) && GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                    && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == 0,
                    "Tooltip leaked render state");
            BufferedImage tooltip = capture();
            require(whitePixels(tooltip, 36, 32, 120, 8) > 20, "First tooltip line is missing");
            require(whitePixels(tooltip, 36, 42, 120, 8) > 20, "Second tooltip line is missing");
            ImageIO.write(tooltip, "png", new File(output, "tooltip.png"));

            // Reproduce the former state-dependent path to ensure this is a regression
            // check.
            setupFrame();
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LESS);
            drawLegacyStateLeakingRect(32, 28, 160, 54, 0xF0100010);
            font.drawStringWithShadow("Visible tooltip", 36, 32, 0xFFFFFF);
            require(whitePixels(capture(), 36, 32, 120, 8) == 0, "Regression setup did not reproduce missing text");

            GuiLuaContainer gui = new GuiLuaContainer(new InventoryPlayer(null), entity, definition);
            setField(GuiScreen.class, gui, "mc", minecraft);
            setField(GuiScreen.class, gui, "fontRenderer", font);
            gui.width = 320;
            gui.height = 240;
            Method background = GuiLuaContainer.class.getDeclaredMethod("drawGuiContainerBackgroundLayer", float.class);
            Method foreground = GuiLuaContainer.class.getDeclaredMethod("drawGuiContainerForegroundLayer");
            Method hover = GuiLuaContainer.class.getDeclaredMethod("drawElementTooltip", int.class, int.class);
            background.setAccessible(true);
            foreground.setAccessible(true);
            hover.setAccessible(true);
            for (int page = 1; page <= 4; page++) {
                entity.setSyncedData("page", page);
                entity.setSyncedData("progress", 75);
                entity.setSyncedData("mode", 2);
                entity.setSyncedData("active", 1);
                setupFrame();
                background.invoke(gui, 0.0F);
                GL11.glPushMatrix();
                GL11.glTranslatef(32, 4, 0);
                foreground.invoke(gui);
                GL11.glPopMatrix();
                ImageIO.write(capture(), "png", new File(output, "page-" + page + ".png"));
                if (page == 1) {
                    GL11.glEnable(GL11.GL_LIGHTING);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    hover.invoke(gui, 44, 124);
                    ImageIO.write(capture(), "png", new File(output, "page-tooltip.png"));
                }
            }
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error during showcase rendering");
            System.out.println(
                    "Showcase parsed; 640 ticks and four selectors passed; framework screens, tooltip state and four "
                            + "GUI pages rendered.");
        } finally {
            buffer.destroy();
        }
    }

    private static void verifyIssueLayout(FontRenderer font) {
        LuaScriptErrors.clear();
        try {
            LuaScriptErrors.add("broken.lua", "broken.lua:12 first line with wrapped details\nsecond line");
            LuaScriptErrors.addWarning("warning.lua", "warning.lua:4 retained warning");
            GuiIssueLayout layout = GuiIssueLayout.prepare(font, LuaScriptErrors.getEntries(), 80, 10);
            List<GuiIssueLayout.Entry> entries = layout.getEntries();
            require(entries.size() == 2, "Issue layout omitted an error or warning");
            require(layout.getHeight() == entries.get(0).getHeight() + 10 + entries.get(1).getHeight(),
                    "Issue list height must use the prepared entry heights and gap");

            ScriptMod selected = LuaScriptRegistry.updateParsed("broken.lua", "broken",
                    Collections.singletonList("missing"), LuaValue.NIL, LuaValue.NIL, LuaValue.NIL,
                    "A long description that wraps consistently while the details panel is scrolled.", "1.0.0", null);
            LuaScriptRegistry.markFailedByFile("broken.lua", "Fallback failure text");
            setupFrame();
            GuiScene scene = new GuiScene();
            scene.setContent(new IssuePanelLayout(selected));
            scene.updateEnvironment(null, font, 320, 240, 320, 240);
            scene.render(0, 0, 0.0F);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Prepared script issue panels produced an OpenGL error");
        } finally {
            LuaScriptErrors.clear();
            LuaScriptRegistry.clear();
        }
    }

    private static void verifyTextureRenderState(Minecraft minecraft, FontRenderer font) {
        setupFrame();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GuiScene scene = new GuiScene();
        scene.setContent(new GuiIcon("/betamoon/gui/symbol_success.png"));
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.render(0, 0, 0.0F);
        require(GL11.glIsEnabled(GL11.GL_LIGHTING) && GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == 0,
                "Texture rendering leaked OpenGL state");
    }

    private static void renderUpdateNotice(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        UpdateRelease release = new UpdateRelease(SemanticVersion.parse("0.7.0-alpha.2"), "Modrinth",
                URI.create("https://modrinth.com/mod/betamoon/version/Test"));
        GuiUpdateNotice notice = new GuiUpdateNotice(null, "0.6.0");
        notice.setRelease(release);
        GuiContainer panel = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                notice.arrange(context, Rect.fromPositionAndSize(10, 194 - GuiUpdateNotice.CARD_HEIGHT,
                        GuiUpdateNotice.CARD_WIDTH, GuiUpdateNotice.CARD_HEIGHT));
            }
        };
        panel.add(notice);
        GuiScene scene = new GuiScene();
        scene.setContent(panel);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        setupFrame();
        scene.render(0, 0, 0);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Update notice render state");
        ImageIO.write(capture(), "png", new File(output, "update-notice.png"));
        renderScreen(new GuiUpdateDetails(null, "0.6.0", release, false), minecraft, output, "update-details.png");
    }

    private static void renderFrameworkScreens(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        LuaScriptErrors.clear();
        LuaScriptRegistry.clear();
        String restartFile = "broken.lua";
        try {
            LuaScriptRegistry.updateParsed("loaded.lua", "Advanced Fabrication Recipes Example",
                    Collections.<String>emptyList(), LuaValue.NIL, LuaValue.NIL, LuaValue.NIL,
                    "A successfully loaded script.", "1.2.3", null);
            ScriptMod failed = LuaScriptRegistry.updateParsed(restartFile, "Broken Example",
                    Collections.singletonList("missing_dependency"), LuaValue.NIL, LuaValue.NIL, LuaValue.NIL,
                    "This script demonstrates failure and restart-required states.", "0.4.0", null);
            LuaScriptRegistry.markFailedByFile(failed.getSourceFileName(), "A representative load failure.");
            LuaScriptErrors.add(restartFile, restartFile + ":12 representative load failure");
            LuaScriptErrors.addWarning("loaded.lua", "loaded.lua:5 representative warning");
            NonReloadableScriptRegistry.mark(restartFile, "registered startup-only content");

            GuiScreenScripts scripts = new GuiScreenScripts(new GuiScreen());
            renderScreen(scripts, minecraft, output, "scripts-screen.png");
            renderScreen(scripts, minecraft, output, "scripts-name-tooltip.png", 45, 57);
            renderScreen(new GuiPopupDebugMenu(scripts), minecraft, output, "debug-menu.png");
            renderScreen(new GuiPopupDebugExport(scripts, null), minecraft, output, "debug-export.png");
            renderScreen(new GuiPopupScriptErrors(scripts), minecraft, output, "script-errors.png");
            renderScreen(new GuiPopupAgentWarning(scripts), minecraft, output, "agent-warning.png");
        } finally {
            NonReloadableScriptRegistry.unmark(restartFile);
            LuaScriptErrors.clear();
            LuaScriptRegistry.clear();
            GuiTextureCache.shared().clear();
        }
    }

    private static void renderScreen(GuiScreen screen, Minecraft minecraft, File output, String fileName)
            throws Exception {
        renderScreen(screen, minecraft, output, fileName, 0, 0);
    }

    private static void renderScreen(GuiScreen screen, Minecraft minecraft, File output, String fileName, int mouseX,
            int mouseY) throws Exception {
        screen.setWorldAndResolution(minecraft, 320, 240);
        setupFrame();
        screen.drawScreen(mouseX, mouseY, 0.0F);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, fileName + " produced an OpenGL error");
        ImageIO.write(capture(), "png", new File(output, fileName));
    }

    /**
     * Recreates the old rectangle helper's leaked state for the regression setup
     * above.
     */
    private static void drawLegacyStateLeakingRect(int left, int top, int right, int bottom, int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(red, green, blue, alpha);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static final class IssuePanelLayout extends GuiContainer {
        private final GuiPanelScriptInfo infoPanel = add(new GuiPanelScriptInfo());
        private final GuiPanelScriptErrorList errorPanel = add(new GuiPanelScriptErrorList());

        private IssuePanelLayout(ScriptMod selected) {
            infoPanel.setHeaderY(12);
            infoPanel.setSelected(selected);
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            infoPanel.arrange(context, new Rect(10, 35, 155, 150));
            errorPanel.arrange(context, new Rect(165, 10, 310, 150));
        }
    }

    private static void setupFrame() {
        GL11.glViewport(0, 0, 320, 240);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 320, 240, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glDepthMask(true);
        GL11.glClearDepth(1);
        GL11.glClearColor(0.12F, 0.12F, 0.12F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static BufferedImage capture() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(320 * 240 * 4);
        GL11.glReadPixels(0, 0, 320, 240, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 240; y++) {
            for (int x = 0; x < 320; x++) {
                int i = ((239 - y) * 320 + x) * 4;
                image.setRGB(x, y, 0xFF000000 | (pixels.get(i) & 255) << 16 | (pixels.get(i + 1) & 255) << 8
                        | pixels.get(i + 2) & 255);
            }
        }
        return image;
    }

    private static int whitePixels(BufferedImage image, int x, int y, int width, int height) {
        int count = 0;
        for (int row = y; row < y + height; row++) {
            for (int col = x; col < x + width; col++) {
                if ((image.getRGB(col, row) & 0xFFFFFF) == 0xFFFFFF) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void setField(Class type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class AssetEngine extends RenderEngine {
        private final Map<String, Integer> textures = new HashMap<String, Integer>();
        AssetEngine(GameSettings settings) {
            super(null, settings);
        }

        public int getTexture(String path) {
            Integer existing = textures.get(path);
            if (existing != null) {
                return existing;
            }
            try {
                BufferedImage image = LuaTextureResources.load(path);
                if (image == null) {
                    URL resource = RenderEngine.class.getResource(path);
                    if (resource == null && path.startsWith("/resources/")) {
                        resource = RenderEngine.class.getResource(path.substring("/resources".length()));
                    }
                    if (resource == null) {
                        throw new IllegalArgumentException("Missing texture resource: " + path);
                    }
                    image = ImageIO.read(resource);
                }
                int texture = allocateAndSetupTexture(image);
                textures.put(path, texture);
                return texture;
            } catch (Exception error) {
                throw new RuntimeException(path, error);
            }
        }
    }

    private static final class TestMinecraft extends Minecraft {
        TestMinecraft() {
            super(null, null, null, 320, 240, false);
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
        }
    }
}
