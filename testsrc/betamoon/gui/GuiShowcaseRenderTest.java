package betamoon.gui;

import betamoon.client.control.input.InputActionDefinition;
import betamoon.client.control.input.InputBinding;
import betamoon.client.control.input.InputMapDefinition;
import betamoon.client.control.input.InputMapRegistration;
import betamoon.client.control.input.InputRouter;
import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.gui.framework.GuiAction;
import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.GuiTextureCache;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDropdownButton;
import betamoon.gui.widget.GuiDropdownMenu;
import betamoon.gui.widget.GuiMinecraftButton;
import betamoon.gui.widget.GuiDialog;
import betamoon.gui.widget.GuiIcon;
import betamoon.gui.widget.GuiMessage;
import betamoon.gui.widget.GuiScrollView;
import betamoon.gui.widget.GuiTextBox;
import betamoon.io.IoUtils;
import betamoon.instrumentation.hooks.texture.TextureResourceCallbacks;
import betamoon.luaapi.config.ConfigApi;
import betamoon.luaapi.minecraft.MinecraftApi;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.MultiblockExamplesTest;
import betamoon.luamodloader.NonReloadableScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.resources.LuaTextureResources;
import betamoon.tileentity.ContainerControlRuntime;
import betamoon.tileentity.ContainerPresentationRuntime;
import betamoon.tileentity.LuaContainer;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.GuiLuaContainer;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.update.SemanticVersion;
import betamoon.update.UpdateRelease;
import betamoon.teleport.TeleportTicket;
import betamoon.worldgen.creation.GenerationSelectionStore;
import betamoon.worldgen.creation.GenerationSelection;
import betamoon.worldgen.creation.GenerationDraft;
import betamoon.worldgen.creation.GenerationCatalog;
import betamoon.worldgen.creation.MissingWorldGenerator;
import betamoon.worldgen.creation.GenerationDraft;
import betamoon.worldgen.terrain.GeneratorProfileRegistry;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.PlayerController;
import net.minecraft.src.ItemStack;
import net.minecraft.src.RenderEngine;
import net.minecraft.src.SaveConverterMcRegion;
import net.minecraft.src.Tessellator;
import net.minecraft.src.TexturePackBase;
import net.minecraft.src.TileEntity;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
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
        File scriptRoot = new File(IoUtils.resolveMinecraftDirFromCodeSource(LuaTextureResources.class),
                "betamoon/scripts");
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
        owner.invoke(null, "03_adv_07_gui_showcase.lua");
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
                + "betamoon.callbackResults = {pass='pass', handled='handled', deny='deny'}; "
                + "betamoon.stack=function(item, count) return {id=item.id, count=count} end").call();
        FileReader reader = new FileReader(new File(examples, "03_adv_07_gui_showcase.lua"));
        try {
            globals.load(reader, "03_adv_07_gui_showcase.lua").call();
        } finally {
            reader.close();
        }
        globals.get("modInit").call();
        owner.invoke(null, new Object[]{null});
        ContainerGuiDefinition definition = ((TileEntityApi.GuiHandle) api.get("result").get("gui")).definition;
        LuaTileEntity entity = new LuaTileEntity(definition.container.tileEntity.name);
        entity.worldObj = new TestWorld();
        Set<Integer> pages = new HashSet<Integer>();
        Set<Integer> values = new HashSet<Integer>();
        for (int tick = 0; tick < 800; tick++) {
            try (LuaTileEntity.Context context = entity.createContext()) {
                definition.container.tileEntity.tickAction.call(context);
            }
            pages.add(entity.getDataInt("page"));
            values.add(entity.getDataInt("progress"));
        }
        require(pages.size() == 5 && values.containsAll(Arrays.asList(0, 1, 100)),
                "Tour or progress edge cases failed");
        for (int page = 1; page <= 5; page++) {
            entity.setInventorySlotContents(0, new ItemStack(4, page, 0));
            try (LuaTileEntity.Context context = entity.createContext()) {
                definition.container.tileEntity.tickAction.call(context);
            }
            require(entity.getDataInt("page") == page && entity.getStackInSlot(0).stackSize == page,
                    "Page selection consumed or ignored items");
        }
        entity.setInventorySlotContents(1, new ItemStack(331, 7, 0));
        ContainerGuiDefinition.ControlElement dial = null;
        for (ContainerGuiDefinition.Element element : definition.elements) {
            if (element instanceof ContainerGuiDefinition.ControlElement
                    && "dial".equals(((ContainerGuiDefinition.ControlElement) element).control)) {
                dial = (ContainerGuiDefinition.ControlElement) element;
                break;
            }
        }
        require(dial != null && dial.captureDrag, "Showcase dial must be a drag-capturing custom control");
        LuaTable dragInput = ContainerControlRuntime.input("mouse");
        dragInput.set("phase", "drag");
        dragInput.set("x", 63);
        dragInput.set("y", 8);
        ContainerControlRuntime controls = new ContainerControlRuntime(definition.container, entity, null);
        require(controls.custom(definition.container.controls.get("dial"), dragInput),
                "Showcase dial did not handle drag input");
        require(entity.getDataInt("dial") == 100, "Dragging the showcase dial did not update its visible value");

        Pbuffer buffer = new Pbuffer(640, 360, new PixelFormat(8, 24, 0), null, null);
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
            ScriptIssueRenderChecks.run(minecraft, font, output);
            verifyDialogContentSizing(minecraft, font);
            verifyDropdownButton(minecraft, font);
            verifyConfigSlider(minecraft, font);
            verifyTextureRenderState(minecraft, font);
            renderFrameworkScreens(minecraft, font, output);
            renderDimensionScreens(minecraft, output);
            renderTextBox(minecraft, font, output);
            renderCreationScreens(minecraft, font, output);
            verifyModalScreens(minecraft, output);
            renderControls(minecraft, output);
            renderUpdateNotice(minecraft, font, output);
            verifyVisualSlotFrames(minecraft, font, definition, entity, output);
            verifyExpansionPanels(minecraft, font, output);
            verifyAnimatedPresentation(minecraft, font, output);
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
            for (int page = 1; page <= 5; page++) {
                entity.setSyncedData("page", page);
                entity.setSyncedData("progress", 75);
                entity.setSyncedData("mode", 2);
                entity.setSyncedData("active", 1);
                setupFrame();
                updatePresentation(gui);
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
            verifyTextRepeatEvents(gui, definition);
            renderKiln(minecraft, font, output, background, foreground, hover);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error during showcase rendering");
            System.out.println(
                    "Showcase parsed; 800 ticks and five selectors passed; framework screens, tooltip state and five "
                            + "GUI pages rendered.");
        } finally {
            buffer.destroy();
        }
    }

    private static void updatePresentation(GuiLuaContainer gui) throws Exception {
        // The off-screen fixture has no live player for vanilla GuiContainer.updateScreen.
        Method update = GuiLuaContainer.class.getDeclaredMethod("preparePresentation");
        update.setAccessible(true);
        update.invoke(gui);
    }

    private static void verifyVisualSlotFrames(Minecraft minecraft, FontRenderer font, ContainerGuiDefinition source,
            LuaTileEntity entity, File output) throws Exception {
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, source.owner);
        ContainerGuiDefinition definition;
        try {
            Globals lua = JsePlatform.standardGlobals();
            LuaTable api = new LuaTable();
            TileEntityApi.attach(api);
            lua.set("betamoon", api);
            lua.set("containerName", source.container.name);
            LuaValue handle = lua.load("return betamoon.containerGuis:add{name='visual_slot_render_fixture',"
                    + "container=betamoon.containers:getRequired(containerName),elements={"
                    + "{type='slot',x=8,y=8}, {type='image',builtin='minecraft:slot',x=40,y=8},"
                    + "{type='slot',width=32,height=8,x=8,y=40}," + "{type='slot',width=8,height=32,x=60,y=40},"
                    + "{type='slot',width=1,height=1,x=100,y=40}}}").call();
            definition = ((TileEntityApi.GuiHandle) handle).definition;
        } finally {
            owner.invoke(null, new Object[]{null});
        }
        GuiLuaContainer gui = new GuiLuaContainer(new InventoryPlayer(null), entity, definition);
        setField(GuiScreen.class, gui, "mc", minecraft);
        setField(GuiScreen.class, gui, "fontRenderer", font);
        gui.width = 320;
        gui.height = 240;
        Method draw = GuiLuaContainer.class.getDeclaredMethod("drawElement", ContainerGuiDefinition.Element.class);
        draw.setAccessible(true);
        setupFrame();
        for (ContainerGuiDefinition.Element element : definition.elements) {
            draw.invoke(gui, element);
        }
        BufferedImage pixels = capture();
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                require(pixels.getRGB(8 + x, 8 + y) == pixels.getRGB(40 + x, 8 + y),
                        "Default visual slot must match the legacy sprite pixel for pixel");
            }
        }
        for (int index = 2; index < definition.elements.size(); index++) {
            ContainerGuiDefinition.ImageElement frame = (ContainerGuiDefinition.ImageElement) definition.elements
                    .get(index);
            for (int y = 0; y < frame.height; y++) {
                for (int x = 0; x < frame.width; x++) {
                    int sourceX = x == 0 ? 0 : x == frame.width - 1 ? 17 : 8;
                    int sourceY = y == 0 ? 0 : y == frame.height - 1 ? 17 : 8;
                    require(pixels.getRGB(frame.x + x, frame.y + y) == pixels.getRGB(8 + sourceX, 8 + sourceY),
                            "Resized slot must preserve one-pixel edges, corners and the interior fill");
                }
            }
        }
        ImageIO.write(pixels, "png", new File(output, "visual-slots.png"));
    }

    private static void verifyExpansionPanels(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "expansion_panels_test.lua");
        ContainerGuiDefinition definition;
        try {
            Globals lua = JsePlatform.standardGlobals();
            LuaTable api = new LuaTable();
            TileEntityApi.attach(api);
            lua.set("betamoon", api);
            LuaValue handle = lua.load("local tile=betamoon.tileEntities:add{name='panels',inventory={slots={"
                    + "input={index=0}}}}; local container=betamoon.containers:add{name='panels',tileEntity=tile,"
                    + "slots={{slot='input',x=56,y=17}},playerInventory={x=8,y=84},"
                    + "state={expanded={type='boolean',default=false}},"
                    + "controls={open={type='toggle',bind={state='expanded'}}}};"
                    + "return betamoon.containerGuis:add{name='panels',container=container,"
                    + "layout={preset='minecraft:furnace',title='Panels',playerInventoryLabel=false},elements={"
                    + "{type='panel',x=176,y=8,width=24,height=24,color=0x55AAFF},"
                    + "{type='panel',x=176,y=36,width=24,height=24,color='dark_green',"
                    + "visibleWhen={state='expanded',equals=false}},"
                    + "{type='panel',x=176,y=64,width=24,height=24,color='dark_red',"
                    + "visibleWhen={state='expanded',equals=false}},"
                    + "{type='group',x=176,y=36,visibleWhen={state='expanded',equals=true},elements={"
                    + "{type='panel',width=64,height=114,color=0x55CC55}," + "{type='text',text='Config',x=8,y=8},"
                    + "{type='slot',x=12,y=30}," + "{type='text',text='Enabled',x=8,y=62}}},"
                    + "{type='toggle_button',control='open',x=178,y=10,width=20,height=20,text='+'}}}").call();
            definition = ((TileEntityApi.GuiHandle) handle).definition;
        } finally {
            owner.invoke(null, new Object[]{null});
        }
        LuaTileEntity entity = new LuaTileEntity(definition.container.tileEntity.name);
        entity.worldObj = new TestWorld();
        GuiLuaContainer gui = new GuiLuaContainer(new InventoryPlayer(null), entity, definition);
        setField(GuiScreen.class, gui, "mc", minecraft);
        setField(GuiScreen.class, gui, "fontRenderer", font);
        gui.width = 320;
        gui.height = 240;
        Method background = GuiLuaContainer.class.getDeclaredMethod("drawGuiContainerBackgroundLayer", float.class);
        Method foreground = GuiLuaContainer.class.getDeclaredMethod("drawGuiContainerForegroundLayer");
        Method mouse = GuiLuaContainer.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
        Method release = GuiLuaContainer.class.getDeclaredMethod("mouseMovedOrUp", int.class, int.class, int.class);
        Method expanded = GuiLuaContainer.class.getDeclaredMethod("overExpansionPanel", int.class, int.class);
        background.setAccessible(true);
        foreground.setAccessible(true);
        mouse.setAccessible(true);
        release.setAccessible(true);
        expanded.setAccessible(true);
        int[] drops = {0};
        Field keyboardCreated = Keyboard.class.getDeclaredField("created");
        keyboardCreated.setAccessible(true);
        boolean wasKeyboardCreated = keyboardCreated.getBoolean(null);
        keyboardCreated.setBoolean(null, true);
        PlayerController previous = minecraft.playerController;
        minecraft.playerController = new PlayerController(minecraft) {
            @Override
            public ItemStack func_27174_a(int window, int slot, int button, boolean shift, EntityPlayer player) {
                require(slot == -999, "Panel test must only dispatch true outside clicks to vanilla");
                drops[0]++;
                return null;
            }
        };
        try {
            for (int state = 0; state < 2; state++) {
                setupFrame();
                updatePresentation(gui);
                background.invoke(gui, 0.0F);
                GL11.glPushMatrix();
                GL11.glTranslatef(72, 37, 0);
                foreground.invoke(gui);
                GL11.glPopMatrix();
                BufferedImage pixels = capture();
                ImageIO.write(pixels, "png", new File(output, state == 0 ? "panels-closed.png" : "panels-open.png"));
                require((Boolean) expanded.invoke(gui, 304, 177) == (state == 1),
                        "Only visible expansion panels should count as GUI background");
                if (state == 0) {
                    mouse.invoke(gui, 258, 55, 0);
                    release.invoke(gui, 258, 55, 0);
                    require(drops[0] == 0, "Side-tab input must be handled before vanilla outside clicks");
                } else {
                    int color = pixels.getRGB(304, 177);
                    require((color >> 8 & 255) > (color >> 16 & 255), "Expanded panel tint must be visible");
                    mouse.invoke(gui, 304, 177, 0);
                    mouse.invoke(gui, 304, 177, 1);
                    require(drops[0] == 0, "Empty panel background clicks must not drop a held stack");
                }
            }
            mouse.invoke(gui, 318, 228, 0);
            require(drops[0] == 1, "True outside clicks must retain vanilla handling");
            mouse.invoke(gui, 258, 55, 0);
            release.invoke(gui, 258, 55, 0);
            mouse.invoke(gui, 304, 177, 0);
            require(drops[0] == 2, "Hidden panels must not consume outside clicks");
            gui.onGuiClosed();
        } finally {
            minecraft.playerController = previous;
            keyboardCreated.setBoolean(null, wasKeyboardCreated);
        }
    }

    private static void verifyAnimatedPresentation(Minecraft minecraft, FontRenderer font, File output)
            throws Exception {
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "animated_gui_test.lua");
        ContainerGuiDefinition definition;
        Globals lua = JsePlatform.standardGlobals();
        try {
            LuaTable api = new LuaTable();
            TileEntityApi.attach(api);
            lua.set("betamoon", api);
            LuaValue handle = lua.load("local t=betamoon.tileEntities:add{name='animated_gui',"
                    + "data={progress={type='integer',default=50,sync=true},empty={type='integer',default=0,sync=true}}};"
                    + "local c=betamoon.containers:add{name='animated_gui',tileEntity=t,slots={},"
                    + "playerInventory={x=8,y=84},state={expanded={type='boolean',default=false},"
                    + "hidden={type='boolean',default=false}},controls={drag={type='custom',"
                    + "onInput=function(ctx,input) if input.phase=='drag' then dx=input.deltaX;dy=input.deltaY end;"
                    + "if input.phase=='release' then released=(released or 0)+1 end;return 'handled' end}}};"
                    + "return betamoon.containerGuis:add{name='animated_gui',container=c,background={style='minecraft'},"
                    + "elements={{type='panel',id='p',x=8,y=8,width=24,height=24,tint='dark_green',"
                    + "bind=function(v)local e=v.state:get('expanded');return {width=e and 72 or 24,"
                    + "height=e and 64 or 24}end,transition={width={duration=.2},height={duration=.4}}},"
                    + "{type='group',id='clip',x=96,y=8,width=24,height=24,clip=true,scaleX=1.5,scaleY=1.5,"
                    + "elements={{type='rectangle',width=40,height=16,color=0x80FFFFFF,opacity=.5}}},"
                    + "{type='text',text='\\194\\167cFade',x=96,y=48,color='white',opacity=.5},"
                    + "{type='rectangle',x=96,y=80,width=42,height=28,color=0x403040},"
                    + "{type='progress',id='gauge',value='progress',maximum=100,x=96,y=80,width=42,height=28,"
                    + "builtin='minecraft:furnace_flame',backgroundBuiltin='minecraft:furnace_flame_background',"
                    + "tint=0x80D0FF,backgroundTint=0x70FF70,direction='bottom_to_top'},"
                    + "{type='slot',x=8,y=116,tint='red'},"
                    + "{type='progress',value='empty',maximum=100,x=96,y=120,"
                    + "builtin='minecraft:furnace_flame',backgroundBuiltin='minecraft:furnace_arrow_background'},"
                    + "{type='image',builtin='minecraft:furnace_arrow_background',x=128,y=120},"
                    + "{type='interactive',id='drag',control='drag',x=40,y=76,width=20,height=20,"
                    + "scaleX=2,scaleY=2,originX=0,originY=0,"
                    + "bind=function(v)return {translateX=v.state:get('expanded') and 10 or 0,"
                    + "visible=not v.state:get('hidden')}end,transition={translateX={duration=.1,easing='linear'}}}}}")
                    .call();
            definition = ((TileEntityApi.GuiHandle) handle).definition;
        } finally {
            owner.invoke(null, new Object[]{null});
        }
        LuaTileEntity entity = new LuaTileEntity(definition.container.tileEntity.name);
        entity.worldObj = new TestWorld();
        GuiLuaContainer gui = new GuiLuaContainer(new InventoryPlayer(null), entity, definition);
        setField(GuiScreen.class, gui, "mc", minecraft);
        setField(GuiScreen.class, gui, "fontRenderer", font);
        gui.width = 320;
        gui.height = 240;
        Field containerField = GuiLuaContainer.class.getDeclaredField("luaContainer");
        containerField.setAccessible(true);
        LuaContainer container = (LuaContainer) containerField.get(gui);
        double[] time = {0};
        ContainerPresentationRuntime runtime = new ContainerPresentationRuntime(definition, entity,
                container.controls(), font::getStringWidth, () -> time[0]);
        setField(GuiLuaContainer.class, gui, "presentation", runtime);
        Method background = GuiLuaContainer.class.getDeclaredMethod("drawGuiContainerBackgroundLayer", float.class);
        Method foreground = GuiLuaContainer.class.getDeclaredMethod("drawGuiContainerForegroundLayer");
        background.setAccessible(true);
        foreground.setAccessible(true);
        for (int frame = 0; frame < 3; frame++) {
            if (frame == 1) {
                container.controls().session().set("expanded", true);
                runtime.update();
                time[0] = .1;
            } else if (frame == 2) {
                time[0] = .4;
            }
            updatePresentation(gui);
            setupFrame();
            background.invoke(gui, 0.0F);
            GL11.glPushMatrix();
            GL11.glTranslatef(72, 37, 0);
            foreground.invoke(gui);
            GL11.glPopMatrix();
            BufferedImage image = capture();
            ImageIO.write(image, "png", new File(output, "animated-presentation-" + frame + ".png"));
            require((image.getRGB(169, 144) & 0xFFFFFF) == 0x403040,
                    "Tinted progress padding obscured the surface beneath the sprite");
            int shadow = image.getRGB(175, 120) & 0xFFFFFF;
            require((shadow >> 8 & 255) == 139 && (shadow >> 16 & 255) < 65 && (shadow & 255) < 65,
                    "Unfilled flame shadow must keep its own tint and opaque artwork");
            for (int row = 0; row < 16; row++) {
                for (int column = 0; column < 24; column++) {
                    require(image.getRGB(168 + column, 157 + row) == image.getRGB(200 + column, 157 + row),
                            "Native progress background extents changed when fill dimensions differed");
                }
            }
            int clipped = image.getRGB(205, 50) & 0xFFFFFF;
            int translucent = image.getRGB(173, 50) >> 16 & 255;
            require(clipped == 0xC6C6C6, "Scaled group clip leaked decoration outside its bounds");
            require(translucent >= 210 && translucent <= 215, "Source alpha and opacity were not multiplied");
            int faded = 0;
            for (int y = 85; y < 93; y++) {
                for (int x = 168; x < 210; x++) {
                    int rgb = image.getRGB(x, y);
                    int red = rgb >> 16 & 255;
                    int green = rgb >> 8 & 255;
                    if (red > green + 50 && red < 245) {
                        faded++;
                    }
                }
            }
            require(faded > 5, "Formatted text color codes discarded opacity");
            require(!runtime.geometry(runtime.element("clip")).contains(133, 13),
                    "Clipped region still accepts pointer input");
            if (frame == 1) {
                double width = runtime.element("p").number("width", 0);
                double height = runtime.element("p").number("height", 0);
                require(width > 24 && width < 72 && height > 24 && height < 64,
                        "Independent dimensions were not sampled at intermediate time");
            } else if (frame == 2) {
                require(runtime.element("p").number("height", 0) == 64, "Animation did not reach exact endpoint");
            }
        }
        Field keyboardCreated = Keyboard.class.getDeclaredField("created");
        keyboardCreated.setAccessible(true);
        boolean wasCreated = keyboardCreated.getBoolean(null);
        keyboardCreated.setBoolean(null, true);
        try {
            Method mouse = GuiLuaContainer.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
            Method drag = GuiLuaContainer.class.getDeclaredMethod("drag", ContainerGuiDefinition.ControlElement.class,
                    int.class, int.class);
            Method release = GuiLuaContainer.class.getDeclaredMethod("mouseMovedOrUp", int.class, int.class, int.class);
            mouse.setAccessible(true);
            drag.setAccessible(true);
            release.setAccessible(true);
            ContainerGuiDefinition.ControlElement control = (ContainerGuiDefinition.ControlElement)
                    runtime.element("drag").definition.element;
            require(runtime.element("drag").definition.properties.supports("tint"),
                    "Custom control's configured builtin frame did not expose artwork tint");
            int pointerX = 134;
            int pointerY = 125;
            mouse.invoke(gui, pointerX, pointerY, 0);
            container.controls().session().set("expanded", false);
            runtime.update();
            time[0] = .45;
            updatePresentation(gui);
            drag.invoke(gui, control, pointerX, pointerY);
            require(lua.get("dx").todouble() == 0 && lua.get("dy").todouble() == 0,
                    "Control animation generated synthetic pointer motion");
            drag.invoke(gui, control, pointerX + 6, pointerY);
            require(lua.get("dx").todouble() == 3, "Captured pointer deltas were not transformed to local units");
            container.controls().session().set("hidden", true);
            release.invoke(gui, 318, 228, 0);
            require(lua.get("released").isnil(), "Unavailable captured control received a stale release");
        } finally {
            gui.onGuiClosed();
            keyboardCreated.setBoolean(null, wasCreated);
        }
    }

    private static void renderKiln(Minecraft minecraft, FontRenderer font, File output, Method background,
            Method foreground, Method hover) throws Exception {
        MultiblockExamplesTest.main(new String[0]);
        ContainerGuiDefinition definition = TileEntityRegistry.getGui("example:block/charcoal_kiln");
        LuaTileEntity entity = new LuaTileEntity(definition.container.tileEntity.name);
        entity.worldObj = new TestWorld();
        GuiLuaContainer gui = new GuiLuaContainer(new InventoryPlayer(null), entity, definition);
        setField(GuiScreen.class, gui, "mc", minecraft);
        setField(GuiScreen.class, gui, "fontRenderer", font);
        gui.width = 320;
        gui.height = 240;
        for (boolean formed : new boolean[]{false, true}) {
            entity.setSyncedData("formed", formed ? 1 : 0);
            entity.setSyncedData("duration", 200);
            entity.setSyncedData("progress", formed ? 100 : 0);
            setupFrame();
            updatePresentation(gui);
            background.invoke(gui, 0.0F);
            GL11.glPushMatrix();
            GL11.glTranslatef(72, 37, 0);
            foreground.invoke(gui);
            GL11.glPopMatrix();
            BufferedImage result = capture();
            int amber = 0;
            for (int x = 220; x < 232; x++) {
                for (int y = 73; y < 85; y++) {
                    int rgb = result.getRGB(x, y);
                    if ((rgb >> 16 & 255) > 180 && (rgb >> 8 & 255) > 100 && (rgb & 255) < 100) {
                        amber++;
                    }
                }
            }
            require(formed ? amber == 0 : amber > 20, "Kiln warning must track the synced formed flag");
            ImageIO.write(result, "png", new File(output, formed ? "kiln-processing.png" : "kiln-invalid.png"));
            if (!formed) {
                hover.invoke(gui, 226, 78);
                ImageIO.write(capture(), "png", new File(output, "kiln-warning-tooltip.png"));
            }
        }
    }

    private static void verifyModalScreens(Minecraft minecraft, File output) throws Exception {
        final int[] mouse = new int[2];
        GuiScreen parent = new GuiScreen() {
            @Override
            public void drawScreen(int x, int y, float partialTicks) {
                mouse[0] = x;
                mouse[1] = y;
                drawRect(0, 0, width, height, 0xFF808080);
            }
        };
        GuiDialogScreen first = new GuiDialogScreen(parent, "First") {
        };
        first.setWorldAndResolution(minecraft, 320, 240);
        setupFrame();
        first.drawScreen(100, 100, 0);
        int single = capture().getRGB(2, 2);
        require(mouse[0] == -1 && mouse[1] == -1, "Covered parent must not display hover effects");
        GuiDialogScreen nested = new GuiDialogScreen(first, "Nested") {
        };
        renderScreen(nested, minecraft, output, "nested-dialog.png");
        require(capture().getRGB(2, 2) == single, "Nested modals must apply the dim layer only once");

        GuiButton button = new GuiButton("Remember focus", null);
        GuiComponentScreen retained = new GuiComponentScreen() {
            @Override
            protected GuiElement createContent() {
                return button;
            }
        };
        retained.setWorldAndResolution(minecraft, 320, 240);
        Field sceneField = GuiComponentScreen.class.getDeclaredField("scene");
        sceneField.setAccessible(true);
        GuiScene scene = (GuiScene) sceneField.get(retained);
        scene.keyTyped('\t', Keyboard.KEY_TAB, false);
        require(button.isFocused(), "Tab focuses retained control");
        retained.onGuiClosed();
        retained.setWorldAndResolution(minecraft, 427, 240);
        require(button.isFocused(), "Returning or resizing must preserve focus in an existing scene");
    }

    private static void renderTextBox(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        GuiScreen parent = new GuiScreen();
        GuiTextBox field = new GuiTextBox(parent, font, 30, 40, 180, 20, 64);
        field.setFocused(true);
        field.appendText("abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ!?");
        String fullText = field.getText();
        require(fullText.length() == 64, "Textbox supports the configured limit beyond native paste's 32");
        field.appendText("overflow");
        require(fullText.equals(field.getText()), "Paste cannot exceed the configured limit");
        setupFrame();
        field.drawTextBox();
        BufferedImage focused = capture();
        ImageIO.write(focused, "png", new File(output, "text-box-focused.png"));
        require(fullText.equals(field.getText()), "Clipping must not modify the query");
        require(focused.getRGB(29, 39) == 0xFFA0A0A0 && focused.getRGB(30, 40) == 0xFF000000,
                "Textbox retains the native gray frame and black interior");
        for (int tick = 0; tick < 6; tick++) {
            field.updateCursorCounter();
        }
        setupFrame();
        field.drawTextBox();
        BufferedImage blink = capture();
        boolean different = false;
        for (int x = 34; x < 206; x++) {
            for (int y = 46; y < 55; y++) {
                different |= focused.getRGB(x, y) != blink.getRGB(x, y);
            }
        }
        require(different, "Caret blinks after six logical ticks");
        field.setFocused(false);
        setupFrame();
        field.drawTextBox();
        ImageIO.write(capture(), "png", new File(output, "text-box-unfocused.png"));
        field.setFocused(true);
        field.setText("");
        field.appendText("ab\n\tc");
        require("abc".equals(field.getText()), "Paste filters unsupported control characters");
        field.textboxKeyTyped('\b', Keyboard.KEY_BACK);
        require("ab".equals(field.getText()), "Native backspace edits pasted text");
        field.isEnabled = false;
        field.appendText("ignored");
        field.textboxKeyTyped('x', Keyboard.KEY_X);
        require("ab".equals(field.getText()), "Disabled textboxes reject typing and paste");
    }

    private static void renderControls(Minecraft minecraft, File output) throws Exception {
        InputRouter router = new InputRouter(null);
        InputMapRegistration map = router
                .register(
                        new InputMapDefinition(ContentKey.parseForType("test:controls", ContentType.INPUT_MAP),
                                Arrays.asList(
                                        new InputActionDefinition("jump", Arrays.asList(InputBinding.parse("key.j"))))),
                        "test.lua");
        try {
            Class<?> type = Class.forName("betamoon.gui.GuiBetaMoonActions");
            Constructor<?> constructor = type.getDeclaredConstructor(GuiScreen.class, GameSettings.class,
                    InputRouter.class);
            constructor.setAccessible(true);
            GuiScreen controls = (GuiScreen) constructor.newInstance(null, minecraft.gameSettings, router);
            renderScreen(controls, minecraft, output, "controls-search.png");
            Method mouse = type.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
            mouse.setAccessible(true);
            mouse.invoke(controls, 100, 60, 0);
            renderScreen(controls, minecraft, output, "controls-search-focused.png");
            controls.onGuiClosed();
        } finally {
            map.close();
        }
    }

    private static void verifyTextRepeatEvents(GuiLuaContainer gui, ContainerGuiDefinition definition)
            throws Exception {
        ContainerGuiDefinition.ControlElement textBox = null;
        for (ContainerGuiDefinition.Element element : definition.elements) {
            if (element instanceof ContainerGuiDefinition.ControlElement
                    && ((ContainerGuiDefinition.ControlElement) element).presentation == ContainerGuiDefinition.ControlElement.Presentation.TEXT_BOX) {
                textBox = (ContainerGuiDefinition.ControlElement) element;
                break;
            }
        }
        require(textBox != null, "Showcase text box is missing");

        Method focus = GuiLuaContainer.class.getDeclaredMethod("focus", ContainerGuiDefinition.ControlElement.class);
        Method blur = GuiLuaContainer.class.getDeclaredMethod("blurFocused");
        focus.setAccessible(true);
        blur.setAccessible(true);
        Keyboard.enableRepeatEvents(false);
        focus.invoke(gui, textBox);
        require(Keyboard.areRepeatEventsEnabled(), "Focused container text boxes must enable held-key repeats");
        blur.invoke(gui);
        require(!Keyboard.areRepeatEventsEnabled(), "Leaving a container text box must disable held-key repeats");

        focus.invoke(gui, textBox);
        gui.onGuiClosed();
        require(!Keyboard.areRepeatEventsEnabled(), "Closing a container GUI must disable held-key repeats");
    }

    private static void verifyDialogContentSizing(Minecraft minecraft, FontRenderer font) {
        final String[] text = {"Short message."};
        GuiMessage message = new GuiMessage(() -> text[0]);
        GuiDialog dialog = new GuiDialog("Measured content");
        dialog.setPanelSize(280, 190, 80);
        dialog.setBody(message);
        GuiButton action = dialog.addFooterButton(new GuiButton("Close", null));
        GuiScene scene = new GuiScene();
        scene.setContent(dialog);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        setupFrame();
        scene.render(0, 0, 0);
        int shortHeight = dialog.getPanelBounds().getHeight();
        require(shortHeight < 190, "Short dialog uses content height");
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            longText.append("A wrapped diagnostic line with details.\n");
        }
        text[0] = longText.toString();
        scene.render(0, 0, 0);
        scene.render(0, 0, 0);
        require(dialog.getPanelBounds().getHeight() == 190 && message.getBottom() < action.getTop(),
                "Changed message expands to the cap without covering actions");
        GuiScrollView scroll = (GuiScrollView) message.getChildren().get(0);
        require(scene.mouseScrolled(message.getLeft() + 5, message.getTop() + 5, -120, false)
                && scroll.getScrollY() > 0, "Overflow message remains reachable by scrolling");
        text[0] = "Short again.";
        scene.render(0, 0, 0);
        scene.render(0, 0, 0);
        require(dialog.getPanelBounds().getHeight() == shortHeight && scroll.getScrollY() == 0,
                "A shorter status message shrinks the panel and clamps old scroll state");
    }

    private static void verifyDropdownButton(Minecraft minecraft, FontRenderer font) {
        GuiDropdownMenu menu = new GuiDropdownMenu();
        final String[] selected = {"one"};
        GuiDropdownButton<String> button = new GuiDropdownButton<String>(menu,
                Arrays.asList(new GuiDropdownButton.Option<String>("one", "First"),
                        new GuiDropdownButton.Option<String>("two", "Second")),
                "one", value -> selected[0] = value);
        GuiContainer layout = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                button.arrange(context, Rect.fromPositionAndSize(100, 70, 116, 20));
                menu.arrange(context, getBounds());
            }
        };
        layout.add(button);
        layout.add(menu);
        GuiScene scene = new GuiScene();
        scene.setContent(layout);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        setupFrame();
        scene.render(0, 0, 0);
        scene.mousePressed(110, 80, 0);
        scene.render(0, 0, 0);
        require(menu.isVisible(), "Dropdown button opens a separate popup menu");
        scene.mousePressed(110, 120, 0);
        require("two".equals(selected[0]) && "two".equals(button.getSelected()) && !menu.isVisible(),
                "Dropdown selection updates its typed value and closes the menu");
        scene.mousePressed(110, 80, 0);
        scene.render(0, 0, 0);
        scene.mousePressed(20, 20, 0);
        require(!menu.isVisible() && "two".equals(selected[0]),
                "Clicking outside the dropdown dismisses it without changing the selection");
    }

    private static void verifyConfigSlider(Minecraft minecraft, FontRenderer font) {
        final double[] selected = {0.5D};
        GuiConfigSlider slider = new GuiConfigSlider(0, 1, 0.25D, 0.5D, value -> selected[0] = value);
        GuiContainer layout = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                slider.arrange(context, Rect.fromPositionAndSize(100, 70, 116, 20));
            }
        };
        layout.add(slider);
        GuiScene scene = new GuiScene();
        scene.setContent(layout);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        setupFrame();
        scene.render(0, 0, 0);
        scene.mouseDragged(171, 80, true);
        require(selected[0] == 0.5D, "Slider ignores drags that did not start on it");
        scene.mousePressed(171, 80, 0);
        scene.mouseReleased(171, 80, 0);
        require(selected[0] == 1.0D, "Slider reaches its upper bound");
        scene.mousePressed(105, 80, 0);
        scene.mouseReleased(105, 80, 0);
        scene.keyTyped('\0', Keyboard.KEY_RIGHT, false);
        require(selected[0] == 0.25D, "Slider arrow key follows its decimal step exactly");
    }

    private static void verifyIssueLayout(FontRenderer font) {
        LuaScriptErrors.clear();
        try {
            LuaScriptErrors.add("broken.lua", "broken.lua:12 first line with wrapped details\nsecond line");
            LuaScriptErrors.addWarning("warning.lua", "warning.lua:4 retained warning");
            GuiIssueLayout layout = GuiIssueLayout.prepare(font, LuaScriptErrors.getEntries(), 80, 10);
            List<GuiIssueLayout.Entry> entries = layout.getEntries();
            require(entries.size() == 2, "Issue layout omitted an error or warning");
            int innerWidth = 80 - GuiIssueLayout.PADDING * 2;
            int expectedHeight = GuiIssueLayout.PADDING * 2 + font.func_27277_a("Error", innerWidth) + 3
                    + font.func_27277_a("broken.lua:12", innerWidth) + 3
                    + font.func_27277_a("first line with wrapped details", innerWidth)
                    + font.func_27277_a("second line", innerWidth);
            require(entries.get(0).getHeight() == expectedHeight,
                    "Source is presented once while complete multiline diagnostics retain their measured space");
            require("Error".equals(entries.get(0).getSeverityLabel())
                    && "Warning".equals(entries.get(1).getSeverityLabel()),
                    "Severity is readable without relying on color");
            GuiIssueLayout narrow = GuiIssueLayout.prepare(font, LuaScriptErrors.getEntries(), 30, 10);
            require(narrow.getHeight() > layout.getHeight(),
                    "Narrow source links and messages wrap into measured rows");
            require(GuiIssueLayout.prepare(font, LuaScriptErrors.getEntries(), 1, 10).getHeight() > 0,
                    "Temporarily constrained layout must not enter the native text wrapper's zero-progress loop");
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
        File configRoot = new File(output, "script-config-fixture");
        configRoot.mkdirs();
        try {
            ScriptMod loaded = LuaScriptRegistry.updateParsed("loaded.lua", "Advanced Fabrication Recipes Example",
                    Collections.<String>emptyList(), LuaValue.NIL, LuaValue.NIL, LuaValue.NIL,
                    "A successfully loaded script.", "1.2.3", null);
            LuaScriptRegistry.markLoadedByFile("loaded.lua");
            LuaTable configModule = new LuaTable();
            ConfigApi.attach(configModule, configRoot, "loaded.lua");
            Globals configLua = JsePlatform.standardGlobals();
            configLua.set("betamoon", configModule);
            Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
            owner.setAccessible(true);
            owner.invoke(null, "loaded.lua");
            try {
                configLua.load("betamoon.config('showcase', {enabled=true, strength=2, "
                        + "advanced={rate=0.5}, tags={'starter'}}, "
                        + "{label='Gameplay', description='Settings available in game', "
                        + "fields={strength={input='slider',min=0,max=10,step=1}, "
                        + "advanced={fields={rate={input='choice',choices={0.25,0.5,0.75}}}}, "
                        + "tags={maxItems=6}}}); "
                        + "betamoon.config('showcase:audio', {volume=0.7}, {label='Audio'})").call();
            } finally {
                owner.invoke(null, new Object[] { null });
            }
            ScriptMod failed = LuaScriptRegistry.updateParsed(restartFile, "Broken Example",
                    Collections.singletonList("missing_dependency"), LuaValue.NIL, LuaValue.NIL, LuaValue.NIL,
                    "This script demonstrates failure and restart-required states.", "0.4.0", null);
            LuaScriptRegistry.markFailedByFile(failed.getSourceFileName(), "A representative load failure.");
            LuaScriptErrors.add(restartFile, restartFile + ":12 representative load failure");
            LuaScriptErrors.addWarning("loaded.lua", "loaded.lua:5 representative warning");
            NonReloadableScriptRegistry.mark(restartFile, "registered startup-only content");

            GuiScreenScripts scripts = new GuiScreenScripts(new GuiScreen());
            renderScreen(scripts, minecraft, output, "scripts-screen.png");
            GuiScreenScriptConfig configuration = new GuiScreenScriptConfig(scripts, loaded);
            renderScreen(configuration, minecraft, output, "script-config.png");
            Field sceneField = GuiComponentScreen.class.getDeclaredField("scene");
            sceneField.setAccessible(true);
            GuiScene configScene = (GuiScene) sceneField.get(configuration);
            GuiDropdownButton<?> choice = findDropdownButton(configScene.getContent());
            require(choice != null, "Config choice field uses the reusable dropdown button");
            configScene.mousePressed(choice.getLeft() + 5, choice.getTop() + 10, 0);
            Field menuField = GuiScreenScriptConfig.class.getDeclaredField("dropdown");
            menuField.setAccessible(true);
            require(((GuiDropdownMenu) menuField.get(configuration)).isVisible(),
                    "Config choice opens its anchored dropdown menu");
            setupFrame();
            configuration.drawScreen(choice.getLeft() + 5, choice.getTop() + 10, 0.0F);
            ImageIO.write(capture(), "png", new File(output, "script-config-dropdown.png"));
            renderScreen(scripts, minecraft, output, "scripts-name-tooltip.png", 45, 57);
            renderScreen(new GuiPopupDebugMenu(scripts), minecraft, output, "debug-menu.png");
            renderScreen(new GuiPopupDebugExport(scripts, (Exception) null), minecraft, output, "debug-export.png");
            renderScreen(new GuiPopupScriptErrors(scripts), minecraft, output, "script-errors.png");
            renderScreen(new GuiPopupAgentWarning(scripts), minecraft, output, "agent-warning.png");
        } finally {
            ScriptResourceTracker.unload("loaded.lua");
            Files.deleteIfExists(new File(configRoot, "showcase.json").toPath());
            Files.deleteIfExists(new File(configRoot, "showcase/audio.json").toPath());
            Files.deleteIfExists(new File(configRoot, "showcase").toPath());
            Files.deleteIfExists(configRoot.toPath());
            NonReloadableScriptRegistry.unmark(restartFile);
            LuaScriptErrors.clear();
            LuaScriptRegistry.clear();
            GuiTextureCache.shared().clear();
        }
    }

    private static GuiDropdownButton<?> findDropdownButton(GuiElement element) {
        if (element instanceof GuiDropdownButton) {
            return (GuiDropdownButton<?>) element;
        }
        if (element instanceof GuiContainer) {
            for (GuiElement child : ((GuiContainer) element).getChildren()) {
                GuiDropdownButton<?> match = findDropdownButton(child);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static void renderDimensionScreens(Minecraft minecraft, File output) throws Exception {
        TeleportTicket ticket = new TeleportTicket();
        GuiTeleportProgress loading = new GuiTeleportProgress(ticket, "The Moon");
        renderScreen(loading, minecraft, output, "teleport-progress.png");
        Method advance = TeleportTicket.class.getDeclaredMethod("update", String.class, String.class, int.class,
                int.class, int.class);
        advance.setAccessible(true);
        advance.invoke(ticket, "preparing", "spawn", 3, 12, 0);
        loading.showStage("spawn", 60);
        renderScreen(loading, minecraft, output, "teleport-generation-progress.png");
        advance.invoke(ticket, "preparing", "destination", 4, 14, 0);
        loading.showStage("destination", 70);
        renderScreen(loading, minecraft, output, "teleport-restore-transition.png");
        Field percent = GuiTeleportProgress.class.getDeclaredField("percent");
        percent.setAccessible(true);
        require(percent.getInt(loading) == 70, "An intervening GUI frame must retain the published restore progress");
        Field controls = GuiScreen.class.getDeclaredField("controlList");
        controls.setAccessible(true);
        require(((List<?>) controls.get(loading)).isEmpty(), "The loading screen cannot offer cancellation controls");
        Method key = GuiTeleportProgress.class.getDeclaredMethod("keyTyped", char.class, int.class);
        key.setAccessible(true);
        key.invoke(loading, '\0', Keyboard.KEY_ESCAPE);
        Field cancellation = TeleportTicket.class.getDeclaredField("cancellationRequested");
        cancellation.setAccessible(true);
        require(!cancellation.getBoolean(ticket), "Escape cannot cancel a transfer from its loading screen");
        GuiDimensionRecovery recovery = new GuiDimensionRecovery(null,
                "The Moon cannot be loaded because example:generator/moon is unavailable.", () -> {
                }, () -> {
                });
        renderScreen(recovery, minecraft, output, "dimension-recovery.png");
        renderScreen(new GuiDimensionRescueConfirmation(recovery, () -> {
        }), minecraft, output, "dimension-rescue-confirmation.png");
    }

    private static void renderCreationScreens(Minecraft base, FontRenderer font, File output) throws Exception {
        CreationMinecraft minecraft = new CreationMinecraft();
        minecraft.fontRenderer = font;
        minecraft.renderEngine = base.renderEngine;
        minecraft.gameSettings = base.gameSettings;
        setField(Minecraft.class, minecraft, "saveLoader",
                new SaveConverterMcRegion(Files.createTempDirectory("betamoon-creation-gui-").toFile()));
        WorldGeneratorProfile profile = new WorldGeneratorProfile("test:generator/alternative", "creation_gui.lua",
                Collections.<String, String>emptyMap());
        WorldGeneratorProfile otherProfile = new WorldGeneratorProfile("test:generator/third", profile.owner,
                Collections.<String, String>emptyMap());
        GeneratorProfileRegistry.replaceOwner(profile.owner, Arrays.asList(profile, otherProfile));
        verifyMissingGeneratorDialog(minecraft, output);
        GuiScreen previous = new GuiScreen();
        GuiScreen creation = new GuiWorldCreation(previous);
        minecraft.displayGuiScreen(creation);
        verifyProfileCycleDirections((GuiWorldCreation) creation);
        renderScreen(creation, minecraft, output, "world-creation.png");
        Field name = creation.getClass().getDeclaredField("name");
        name.setAccessible(true);
        ((GuiTextBox) name.get(creation)).setText("Remember My World");
        creation.setWorldAndResolution(minecraft, 640, 360);
        require(((GuiTextBox) name.get(creation)).getText().equals("Remember My World"),
                "Creation resize erased the world name");
        creation.setWorldAndResolution(minecraft, 320, 240);
        creationAction(creation, 3);
        GuiScreen settings = minecraft.currentScreen;
        verifyGenerationSettingsAccordion((GuiGenerationSettings) settings);
        renderScreen(settings, minecraft, output, "generation-settings.png");
        verifyWideMinecraftButtons(minecraft, output);
        creationAction(settings, 0);
        require(presetCaption(creation).endsWith("*"), "Done failed to mark the preset modified");
        creationAction(creation, 2);
        require(!presetCaption(creation).endsWith("*"), "Draft modifications leaked to another preset");
        Field presetField = GuiWorldCreation.class.getDeclaredField("preset");
        presetField.setAccessible(true);
        net.minecraft.src.GuiButton preset = (net.minecraft.src.GuiButton) presetField.get(creation);
        ((GuiWorldCreation) creation).mouseClicked(preset.xPosition + 10, preset.yPosition + 10, 1);
        require(presetCaption(creation).endsWith("*"), "Cycling lost the Minecraft draft");
        creationAction(creation, 3);
        creationAction(minecraft.currentScreen, 3);
        creationAction(minecraft.currentScreen, 1);
        require(presetCaption(creation).endsWith("*"), "Cancel committed the reset");
        creationAction(creation, 3);
        creationAction(minecraft.currentScreen, 3);
        creationAction(minecraft.currentScreen, 0);
        require(!presetCaption(creation).endsWith("*"), "Reset did not clear the modified marker");
        creationAction(creation, 1);
        require(minecraft.currentScreen == previous, "Cancel lost the world-selection parent");
        GuiScreen fresh = new GuiWorldCreation(previous);
        minecraft.displayGuiScreen(fresh);
        require(presetCaption(fresh).equals("World Generation: Minecraft"), "A fresh creation flow did not reset");
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.<WorldGeneratorProfile>emptyList());
    }

    private static void verifyProfileCycleDirections(GuiWorldCreation screen) throws Exception {
        Field presetField = GuiWorldCreation.class.getDeclaredField("preset");
        presetField.setAccessible(true);
        net.minecraft.src.GuiButton preset = (net.minecraft.src.GuiButton) presetField.get(screen);
        Field selected = GuiWorldCreation.class.getDeclaredField("selected");
        selected.setAccessible(true);
        int x = preset.xPosition + 10;
        int y = preset.yPosition + 10;
        screen.mouseClicked(x, y, 0);
        require(selected.getInt(screen) == 1, "Left click must select the next profile");
        screen.mouseClicked(x, y, 1);
        require(selected.getInt(screen) == 0, "Right click must select the previous profile");
        screen.mouseClicked(x, y, 1);
        require(selected.getInt(screen) == 2, "Right click must wrap from Minecraft to the last profile");
        screen.mouseClicked(x - 250, y, 1);
        screen.mouseClicked(x, y, 2);
        require(selected.getInt(screen) == 2, "Outside and middle clicks must not cycle profiles");
        screen.mouseClicked(x, y, 0);
        require(selected.getInt(screen) == 0, "Left click must wrap back to Minecraft");
    }

    private static void verifyWideMinecraftButtons(Minecraft minecraft, File output) throws Exception {
        for (int width : new int[]{201, 288, 420}) {
            setupFrame();
            new GuiMinecraftButton(0, 0, 90, width, 20, "").drawButton(minecraft, -1, -1);
            BufferedImage image = capture();
            require(whitePixels(image, width / 2 - 4, 92, 8, 16) == 0,
                    "Wide native-style buttons must not sample white pixels outside the button atlas strip");
        }
        ImageIO.write(capture(), "png", new File(output, "wide-button-atlas-check.png"));
    }

    private static void verifyGenerationSettingsAccordion(GuiGenerationSettings screen) throws Exception {
        Field treeField = GuiGenerationSettings.class.getDeclaredField("tree");
        treeField.setAccessible(true);
        GenerationSettingsTree tree = (GenerationSettingsTree) treeField.get(screen);
        Field draftField = GuiGenerationSettings.class.getDeclaredField("draft");
        draftField.setAccessible(true);
        GenerationDraft draft = (GenerationDraft) draftField.get(screen);
        int collapsedCount = tree.rows("").size();
        creationAction(screen, 103);
        require(tree.rows("").size() > collapsedCount, "Ore label must expand its children");
        require(draft.selected("group/ores"), "Expanding a label must not toggle its checkbox");
        creationAction(screen, 102);
        require(!draft.enabled("native/ores") && draft.selected("native/ores"),
                "Disabling a parent must preserve the child's remembered checkbox");
        creationAction(screen, 103);
        require(tree.rows("").size() == collapsedCount, "Disabled parent labels must still collapse");
        require(tree.rows("Minecraft ores").stream().anyMatch(row -> row.entry.key.equals("native/ores")),
                "Search must reveal children beneath collapsed parents");
        require(tree.rows("").size() == collapsedCount, "Search must preserve the previous expansion choices");
        creationAction(screen, 103);
        require(!draft.selected("group/ores"), "Expansion must preserve a disabled parent's choice");
    }

    private static void verifyMissingGeneratorDialog(CreationMinecraft minecraft, File output) throws Exception {
        java.nio.file.Path directory = Files.createTempDirectory("betamoon-fallback-gui-");
        File settings = directory.resolve("settings.dat").toFile();
        File manifest = directory.resolve("generator.dat").toFile();
        WorldGeneratorProfile missingProfile = new WorldGeneratorProfile("test:generator/uninstalled", "absent.lua",
                Collections.<String, String>emptyMap());
        GeneratorProfileRegistry.replaceOwner(missingProfile.owner, Collections.singletonList(missingProfile));
        new GenerationSelectionStore(settings)
                .write(GenerationSelection.create(new GenerationDraft(new GenerationCatalog(missingProfile)), 42));
        GeneratorProfileRegistry.replaceOwner(missingProfile.owner, Collections.<WorldGeneratorProfile>emptyList());
        new betamoon.worldgen.terrain.GeneratorManifestStore(manifest)
                .write(new betamoon.worldgen.terrain.WorldGeneratorManifest(42, 0, missingProfile.key,
                        missingProfile.signature, null));
        MissingWorldGenerator missing = MissingWorldGenerator.inspect(settings, manifest, 42);
        GuiScreen selection = new GuiScreen();
        final int[] loads = {0};
        GuiScreen warning = new GuiMissingWorldGenerator(selection, "Missing preset world", missing, () -> loads[0]++);
        minecraft.displayGuiScreen(warning);
        renderScreen(warning, minecraft, output, "missing-generator-warning.png");
        dialogAction(warning, "Load Anyway");
        GuiScreen confirmation = minecraft.currentScreen;
        require(confirmation != warning && loads[0] == 0, "Load Anyway must only open a confirmation");
        renderScreen(confirmation, minecraft, output, "missing-generator-confirmation.png");
        dialogAction(confirmation, "Cancel");
        require(minecraft.currentScreen == warning && loads[0] == 0, "Popup Cancel must only dismiss the popup");
        dialogAction(warning, "Load Anyway");
        Method escape = GuiDialogScreen.class.getDeclaredMethod("keyTyped", char.class, int.class);
        escape.setAccessible(true);
        escape.invoke(minecraft.currentScreen, (char) 0, org.lwjgl.input.Keyboard.KEY_ESCAPE);
        require(minecraft.currentScreen == warning && loads[0] == 0, "Popup Escape must not grant fallback consent");
        dialogAction(warning, "Load Anyway");
        confirmation = minecraft.currentScreen;
        dialogAction(confirmation, "Yes");
        dialogAction(confirmation, "Yes");
        require(loads[0] == 1, "Confirmation Yes starts exactly one load");
        minecraft.displayGuiScreen(warning);
        dialogAction(warning, "Cancel");
        require(minecraft.currentScreen == selection && loads[0] == 1, "Warning Cancel returns to world selection");
    }

    private static void dialogAction(GuiScreen screen, String label) throws Exception {
        Field field = GuiDialogScreen.class.getDeclaredField("dialog");
        field.setAccessible(true);
        GuiDialog dialog = (GuiDialog) field.get(screen);
        for (GuiElement element : dialog.getChildren()) {
            if (element instanceof GuiButton && ((GuiButton) element).getLabel().equals(label)) {
                Field action = GuiButton.class.getDeclaredField("action");
                action.setAccessible(true);
                ((GuiAction) action.get(element)).run();
                return;
            }
        }
        throw new AssertionError("Missing dialog action: " + label);
    }

    private static String presetCaption(GuiScreen screen) throws Exception {
        Field field = screen.getClass().getDeclaredField("preset");
        field.setAccessible(true);
        return ((net.minecraft.src.GuiButton) field.get(screen)).displayString;
    }

    private static void creationAction(GuiScreen screen, int id) throws Exception {
        Method method = screen.getClass().getDeclaredMethod("actionPerformed", net.minecraft.src.GuiButton.class);
        method.setAccessible(true);
        method.invoke(screen, new net.minecraft.src.GuiButton(id, 0, 0, ""));
    }

    private static final class CreationMinecraft extends Minecraft {
        CreationMinecraft() {
            super(null, null, null, 320, 240, false);
        }

        @Override
        public void displayGuiScreen(GuiScreen screen) {
            if (currentScreen != null) {
                currentScreen.onGuiClosed();
            }
            currentScreen = screen;
            if (screen != null) {
                screen.setWorldAndResolution(this, 320, 240);
            }
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
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
        private final GuiPanelScriptErrorList errorPanel = add(new GuiPanelScriptErrorList(true));

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
        setupFrame(320, 240);
    }

    static void setupFrame(int width, int height) {
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1000, 1000);
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
        return capture(320, 240);
    }

    static BufferedImage capture(int width, int height) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = ((height - 1 - y) * width + x) * 4;
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
        private final TexturePackBase resources = new TexturePackBase() {
            @Override
            public InputStream getResourceAsStream(String path) {
                InputStream input = RenderEngine.class.getResourceAsStream(path);
                if (input == null && path.startsWith("/resources/")) {
                    input = RenderEngine.class.getResourceAsStream(path.substring("/resources".length()));
                }
                return input;
            }
        };
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
                    try (InputStream input = TextureResourceCallbacks.openTexture(resources, path)) {
                        if (input == null) {
                            throw new IllegalArgumentException("Missing texture resource: " + path);
                        }
                        image = ImageIO.read(input);
                    }
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

    private static final class TestWorld extends World {
        private TestWorld() {
            super(null, "gui_showcase_test", new WorldProvider() {
            }, 0L);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return null;
        }

        @Override
        public int getBlockId(int x, int y, int z) {
            return 1;
        }

        @Override
        public void func_698_b(int x, int y, int z, TileEntity entity) {
        }
    }
}
