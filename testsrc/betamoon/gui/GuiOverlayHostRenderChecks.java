package betamoon.gui;

import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.graphics.GuiResourceDefinitions;
import betamoon.gui.interop.GuiOwnedResources;
import betamoon.gui.interop.NativeClientScreenHost;
import betamoon.gui.screen.GuiAttachedDocument;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiHostElementBudget;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luaapi.client.ui.GuiComponentParser;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real compiled passive/interactive overlay documents with no native screen
 * installed.
 */
final class GuiOverlayHostRenderChecks {
    private static final String OWNER = "overlay-host-fixture";

    static void run(Minecraft client, File output) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(GuiOverlayHostRenderChecks.class.getClassLoader());
        TinyMappingResolver mappings;
        try (FileInputStream input = new FileInputStream("tools/rmcp/conf/mappings.tiny")) {
            mappings = TinyMappingResolver.read(input);
        }
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        Field installed = NativeDiscoveryRuntime.class.getDeclaredField("bindings");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        GuiScreen previousScreen = client.currentScreen;
        installed.set(null, bindings);
        client.currentScreen = null;
        Surface surface = new Surface(client);
        GuiAttachedDocument document = null;
        NativeClientScreenHost engine = null;
        try {
            Globals lua = JsePlatform.standardGlobals();
            GuiClientUiRuntime
                    .components().add(
                            GuiComponentParser
                                    .parse(OWNER,
                                            lua.load("return {key='fixture:overlay-raw',size={width=140,height=8},"
                                                    + "rawDraw=function(frame) rawCalls=(rawCalls or 0)+1 end}")
                                                    .call()));
            GuiClientUiRuntime.screens()
                    .add(GuiScreenParser.parse(OWNER,
                            lua.load("return {key='fixture:overlay-content',root={type='column',width=140,children={"
                                    + "{type='button',width=140,height=20,"
                                    + "bind=function(ctx) bindingSeen=ctx.overlay; return {text=ctx.overlay} end,"
                                    + "on={activate=function() clicks=(clicks or 0)+1 end}},"
                                    + "{component='fixture:overlay-raw'}}}}").call(),
                            GuiClientUiRuntime.components()));
            engine = NativeClientScreenHost.prepareOverlay(client, surface.viewport, "<overlay-test>",
                    surface::presentationAvailable);
            engine.initialize();
            document = new GuiAttachedDocument(surface, OWNER, "fixture:overlay-content");
            surface.scene.setContent(document.mount());
            require(document.open().instance != null, "Worldless overlay document was refused");
            document.tick();
            surface.scene.prepare();
            require(document.usable() && !document.inputAvailable() && client.currentScreen == null,
                    "Passive overlay either acquired a native screen or lost presentation eligibility");
            require(lua.get("bindingSeen").tojstring().equals("Overlay settings"),
                    "Attachment host observations were absent from the ordinary binding context");
            GuiAttachedDocument active = document;
            GuiShowcaseRenderTest.setupFrame();
            engine.paint(() -> {
                active.page().sampleAttached();
                surface.scene.render(0, 0, 0);
            });
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png", new File(output, "overlay-passive-host.png"));
            require(lua.get("rawCalls").toint() == 1,
                    "Overlay raw graphics authority still required an actual current GuiScreen");
            require(!surface.scene.mousePressed(160, 120, 0), "Passive overlay captured a gameplay pointer press");
            require(document.controller().current().dialog("fixture:overlay-content", Collections.emptyMap(),
                    Collections.emptyMap()).reason.equals("input_unavailable"),
                    "Passive overlay admitted an interactive layer");
            require(lua.get("clicks").isnil(), "Passive overlay dispatched an authored action");
            surface.interactive = true;
            surface.scene.prepare();
            engine.input(permit -> {
                require(surface.scene.mousePressed(160, 120, 0), "Explicit interaction did not use displayed geometry");
                surface.scene.mouseReleased(160, 120, 0);
            });
            require(lua.get("clicks").toint() == 1, "Interactive overlay control did not activate exactly once");
            GuiScreenActivation queued = document.controller().current().dialog("fixture:overlay-content",
                    Collections.emptyMap(), Collections.emptyMap()).instance;
            surface.interactive = false;
            document.tick();
            require(queued.status() == GuiScreenActivation.Status.CANCELLED
                    && queued.reason().equals("input_unavailable"),
                    "Queued layer acquired ownership after cursor interaction ended");
            surface.scene.prepare();
            client.currentScreen = new GuiScreen();
            require(!engine.session().active() && !document.usable(),
                    "Overlay resources ignored replacement of the actual native screen root");
        } finally {
            surface.alive = false;
            if (document != null) {
                document.close();
            }
            surface.rows.close();
            if (engine != null) {
                engine.close();
            }
            ScriptResourceTracker.unload(OWNER);
            require(((Number) bindings.clients.diagnostics().get("sessions")).intValue() == 0,
                    "Closed overlay retained native resource sessions");
            client.currentScreen = previousScreen;
            installed.set(null, previous);
            bindings.close();
            loaders.close();
        }
        System.out.println("Overlay host passed: null native screen, shared compiled controls, passive input, "
                + "explicit interaction, layer refusal, attachment binding context and actual-root expiration.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Surface implements GuiAttachedDocument.Host {
        final Minecraft client;
        final GuiScreen viewport = new GuiScreen();
        final GuiScene scene = new GuiScene();
        final GuiRowBudget rows = new GuiRowBudget();
        final GuiHostElementBudget budget = new GuiHostElementBudget(rows);
        boolean alive = true;
        boolean interactive;

        Surface(Minecraft client) {
            this.client = client;
            viewport.width = 320;
            viewport.height = 240;
            scene.updateEnvironment(client, client.fontRenderer, 320, 240, client.displayWidth, client.displayHeight);
        }

        public Minecraft client() {
            return client;
        }

        public GuiScreen screen() {
            return viewport;
        }

        public GuiScene scene() {
            return scene;
        }

        public GuiRowBudget rowBudget() {
            return rows;
        }

        public GuiHostElementBudget elementBudget() {
            return budget;
        }

        public boolean available() {
            return alive;
        }

        public boolean attached() {
            return presentationAvailable();
        }

        public boolean presentationAvailable() {
            return alive && client.currentScreen == null;
        }

        public boolean inputAvailable() {
            return presentationAvailable() && interactive;
        }

        public long environment() {
            return presentationAvailable() ? 1 : 2;
        }

        public boolean worldAvailable() {
            return false;
        }

        public boolean paused() {
            return false;
        }

        public void closed() {
            alive = false;
        }

        public GuiOwnedResources resources(String owner, GuiResourceDefinitions definitions) {
            return GuiOwnedResources.overlay(client, viewport, owner, definitions, this::presentationAvailable);
        }

        public void context(LuaTable value, LuaCallbackScope scope, GuiScreenActivation activation) {
            value.set("overlay", "Overlay settings");
        }

        public void failure(String owner, String path, Throwable failure) {
            throw new AssertionError(path, failure);
        }
    }
}
