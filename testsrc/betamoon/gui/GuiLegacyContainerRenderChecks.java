package betamoon.gui;

import betamoon.gui.container.GuiLegacyContainerViews;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.presentation.GuiControlType;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.tileentity.ContainerControlRuntime;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.ContainerPresentationRuntime;
import betamoon.tileentity.LuaTileEntity;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;

/**
 * Actual retained dispatch against parsed legacy declarations in the pbuffer.
 */
final class GuiLegacyContainerRenderChecks {
    private static final String OWNER = "retained-container-input-fixture.lua";

    static void run(Minecraft client) throws Exception {
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        TileEntityApi.attach(api);
        lua.set("betamoon", api);
        ContainerGuiDefinition definition;
        owner.invoke(null, OWNER);
        try {
            LuaValue handle = lua.load("local t=betamoon.tileEntities:add{name='retained_input'};"
                    + "local c=betamoon.containers:add{name='retained_input',tileEntity=t,slots={},"
                    + "playerInventory={x=8,y=84},state={label={type='string',default='old',maxLength=20},"
                    + "speed={type='integer',default=0},locked={type='boolean',default=false}},controls={"
                    + "front={type='action',enabledWhen={state='locked',equals=false},"
                    + "onActivate=function() front=(front or 0)+1 end},"
                    + "back={type='action',onActivate=function() back=(back or 0)+1 end},"
                    + "text={type='text',bind={state='label'},maxLength=20,"
                    + "beforeChange=function(ctx,v) if v=='deny' then return 'deny' end return 'pass' end,"
                    + "onCommit=function() commits=(commits or 0)+1 end},"
                    + "number={type='number',bind={state='speed'},minimum=0,maximum=10,step=1}}};"
                    + "return betamoon.containerGuis:add{name='retained_input',container=c,elements={"
                    + "{type='button',control='front',x=8,y=8,width=40,layer='foreground'},"
                    + "{type='button',control='back',x=8,y=8,width=40,layer='background'},"
                    + "{type='text_box',control='text',x=60,y=8,width=100},"
                    + "{type='slider',control='number',x=8,y=40,width=100}}}").call();
            definition = ((TileEntityApi.GuiHandle) handle).definition;
        } finally {
            owner.invoke(null, new Object[]{null});
        }
        LuaTileEntity tile = new LuaTileEntity(definition.container.tileEntity.name);
        ContainerControlRuntime controls = new ContainerControlRuntime(definition.container, tile, null);
        ContainerPresentationRuntime presentation = new ContainerPresentationRuntime(definition, tile, controls,
                client.fontRenderer::getStringWidth);
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(client, client.fontRenderer, 320, 240, 320, 240);
        try (GuiLegacyContainerViews views = new GuiLegacyContainerViews(scene, definition, presentation, controls,
                () -> {
                })) {
            scene.setContent(views.root());
            prepare(scene, views, presentation);
            paint(scene);
            require(scene.mousePressed(12, 12, 0) && lua.get("front").isnil(),
                    "Legacy action activated before release or escaped the retained route");
            scene.mouseReleased(12, 12, 0);
            require(lua.get("front").toint() == 1 && lua.get("back").isnil(),
                    "Retained hit order ignored legacy global paint layers");
            scene.mousePressed(12, 12, 0);
            scene.mouseReleased(200, 200, 0);
            require(lua.get("front").toint() == 1, "Release outside activated or retargeted a legacy control");
            scene.cancelInput();
            scene.keyTyped('\t', Keyboard.KEY_TAB, false, false);
            require(scene.getFocusedElement() == views.element(control(definition, "front")),
                    "Legacy declaration focus order changed with paint layers");
            scene.keyTyped('\0', Keyboard.KEY_RETURN, false, false);
            require(lua.get("front").toint() == 2, "Legacy keyboard activation timing changed");

            scene.mousePressed(65, 12, 0);
            scene.mouseReleased(65, 12, 0);
            replace(scene, "fresh");
            require(controls.session().get("label").equals("old"), "Typing bypassed legacy commit validation");
            scene.keyTyped('\0', Keyboard.KEY_RETURN, false, false);
            require(controls.session().get("label").equals("fresh") && lua.get("commits").toint() == 1,
                    "Shared editor did not commit through the domain runtime");
            replace(scene, "discard");
            scene.keyTyped('\0', Keyboard.KEY_ESCAPE, false, false);
            require(scene.getFocusedElement() == null && controls.session().get("label").equals("fresh")
                    && lua.get("commits").toint() == 1, "Escape committed a cancelled draft or retained focus");

            scene.mousePressed(65, 12, 0);
            scene.mouseReleased(65, 12, 0);
            replace(scene, "local");
            controls.session().set("label", "external");
            scene.mousePressed(220, 200, 0);
            require(controls.session().get("label").equals("external") && lua.get("commits").toint() == 2,
                    "Blur overwrote an external text change with a stale draft");
            scene.mousePressed(65, 12, 0);
            scene.mouseReleased(65, 12, 0);
            replace(scene, "deny");
            scene.keyTyped('\0', Keyboard.KEY_RETURN, false, false);
            require(controls.session().get("label").equals("external") && views.invalid(control(definition, "text")),
                    "Denied text commit bypassed validation/feedback");
            scene.keyTyped('\0', Keyboard.KEY_ESCAPE, false, false);

            scene.mousePressed(8, 44, 0);
            scene.mouseDragged(200, 44, true);
            scene.mouseReleased(200, 44, 0);
            require(((Number) controls.session().get("speed")).intValue() == 10,
                    "Shared capture lost the legacy slider outside its bounds");
            scene.mousePressed(12, 12, 0);
            controls.session().set("locked", true);
            prepare(scene, views, presentation);
            require(scene.mouseReleased(12, 12, 0) && lua.get("front").toint() == 2,
                    "Disabled gesture reached native fallback or another control");
            require(views.editor(control(definition, "text")) == null,
                    "Unfocused legacy editor retained a cancelled draft");
            require(control(definition, "text").type == GuiControlType.TEXT, "Fixture text contract changed");
        } finally {
            presentation.close();
            controls.close();
            ScriptResourceTracker.unload(OWNER);
        }
        System.out.println("Legacy container scene passed: displayed layer hits, release/keyboard timing, declaration "
                + "focus order, shared editor commit/cancel/conflict/denial, slider capture and stale-release suppression.");
    }

    private static void prepare(GuiScene scene, GuiLegacyContainerViews views, ContainerPresentationRuntime runtime) {
        runtime.viewport(0, 0, 320, 240);
        runtime.update();
        views.update(0, 0);
        scene.prepare();
    }

    private static void paint(GuiScene scene) {
        GuiShowcaseRenderTest.setupFrame();
        scene.renderContent(0, 0, 0);
    }

    private static void replace(GuiScene scene, String value) {
        scene.keyTyped('\0', Keyboard.KEY_A, false, true);
        for (char character : value.toCharArray()) {
            scene.keyTyped(character, 0, false, false);
        }
    }

    private static ContainerGuiDefinition.ControlElement control(ContainerGuiDefinition definition, String name) {
        for (ContainerGuiDefinition.Element element : definition.elements) {
            if (element instanceof ContainerGuiDefinition.ControlElement
                    && ((ContainerGuiDefinition.ControlElement) element).control.equals(name)) {
                return (ContainerGuiDefinition.ControlElement) element;
            }
        }
        throw new AssertionError("Missing fixture control " + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
