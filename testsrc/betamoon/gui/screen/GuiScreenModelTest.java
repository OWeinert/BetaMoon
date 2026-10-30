package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentCompiler;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiFontFace;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiThemeDefinition;
import betamoon.luaapi.client.ui.GuiComponentParser;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luaapi.client.ui.GuiStyleParser;
import betamoon.luaapi.client.ui.GuiResourceParser;
import betamoon.luaapi.client.ui.GuiModelLuaAccess;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luaapi.interop.NativeCallbackTable;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Screen contracts use the production component compiler, local store and
 * retained scene.
 */
public final class GuiScreenModelTest {
    private static final String OWNER = "screen-model.lua";

    public static void main(String[] arguments) {
        appearanceAndAuthority();
        Globals lua = JsePlatform.standardGlobals();
        GuiComponentRegistry components = new GuiComponentRegistry();
        components.add(GuiComponentParser.parse(OWNER,
                lua.load("builds=0; return {key='test:label',props={value={type='string',default='0'}},"
                        + "exports={label='label'},build=function(p)builds=builds+1;return {type='text',id='label',text=p.value}end}")
                        .call()));
        GuiScreenDefinition definition = GuiScreenParser.parse(OWNER,
                lua.load("fail=false;return {key='test:form',state={count={type='integer',default=0,min=0,max=100}},"
                        + "on={open=function(ctx)ctx.state:set('count',ctx.state:get('count')+1)end},root={type='column',width=160,height=100,children={"
                        + "{component='test:label',id='readout',bind=function(v)return {props={value=tostring(v.state:get('count'))}}end},"
                        + "{type='button',id='add',text='Add',on={activate=function(ctx)ctx.state:set('count',ctx.state:get('count')+1);if fail then error('form failure')end end}}}}}")
                        .call(),
                components);
        GuiScreenRegistry registry = new GuiScreenRegistry();
        registry.add(definition);
        require(registry.getRequired("test:form") == definition && !definition.pauseGame,
                "Screen registry or pause default failed");
        expect(() -> GuiScreenParser.parse(OWNER,
                lua.load("return {key='test:bad',context='other',root={type='panel'}}").call(), components));
        expect(() -> GuiScreenParser.parse(OWNER,
                lua.load("return {key='test:bad',on={draw=function()end},root={type='panel'}}").call(), components));
        expect(() -> GuiScreenParser.parse(OWNER,
                lua.load("return {key='test:bad',actions={bad={scope='world',handler=12}},root={type='panel'}}").call(),
                components));
        List<Throwable> failures = new ArrayList<>();
        Services services = new Services(definition, failures);
        Host host = new Host();
        GuiComponentPlan plan = GuiComponentCompiler.prepare(components, OWNER, definition.key, definition.root, host,
                services, EnumSet.allOf(GuiCapability.class), null, definition.state,
                Collections.singletonMap("count", 7), Collections.emptyMap());
        GuiPresentationTree tree = plan.createTree(EnumSet.allOf(GuiCapability.class), String::length, () -> 0);
        GuiScene scene = scene(tree);
        require(text(tree, plan).equals("7") && lua.get("builds").checkint() == 1,
                "Initial screen options were applied after component expansion");
        require(value(plan) == 8 && plan.requiresRecompile(components, host.snapshot()),
                "Open callback did not publish root state/dependency changes");
        GuiComponentPlan next = GuiComponentCompiler.prepare(components, OWNER, definition.key, definition.root, host,
                services, EnumSet.allOf(GuiCapability.class), plan, definition.state, Collections.emptyMap(),
                Collections.emptyMap());
        tree = next.reloadTree(tree, scene::prepareDetached);
        plan.close();
        plan = next;
        scene.setDocument(tree);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(value(plan) == 8 && text(tree, plan).equals("8"), "Refresh reopened the screen or lost root state");
        lua.set("fail", LuaValue.TRUE);
        GuiElement add = tree.element(plan.elementId("add"));
        scene.mousePressed(add.getLeft() + 3, add.getTop() + 3, 0);
        scene.mouseReleased(add.getLeft() + 3, add.getTop() + 3, 0);
        require(value(plan) == 8 && failures.size() == 1, "Failed form handler leaked screen-local state");
        Map<String, Object> saved = plan.state().snapshot();
        plan.close();
        tree.close();
        plan = GuiComponentCompiler.prepare(components, OWNER, definition.key, definition.root, host, services,
                EnumSet.allOf(GuiCapability.class), null, definition.state, Collections.emptyMap(), saved);
        tree = plan.createTree(EnumSet.allOf(GuiCapability.class), String::length, () -> 0);
        // Preflight intentionally does not activate; detached history must influence
        // the first built component.
        GuiScene preflight = new GuiScene();
        preflight.updateEnvironment(null, null, 320, 240, 320, 240);
        preflight.prepareDetached(tree);
        require(text(tree, plan).equals("8") && value(plan) == 8,
                "History was restored after the first prepared frame");
        plan.close();
        tree.close();
        slotState(lua, components);
        ScriptResourceTracker.unload(OWNER);
        require(registry.get("test:form") == null && !definition.available(),
                "Owner unload retained registered screen contracts");
        System.out.println("Screen models passed: strict declarations, root schemas/options/history before expansion, "
                + "open/refresh separation, local rollback and owner-cleaned registration.");
    }

    private static void slotState(Globals lua, GuiComponentRegistry components) {
        components
                .add(GuiComponentParser
                        .parse(OWNER,
                                lua.load("return {key='test:slot',slots={body={min=1,max=1}},"
                                        + "build=function()return {type='column',children={{slot='body'}}}end}")
                                        .call()));
        GuiScreenDefinition definition = GuiScreenParser.parse(OWNER, lua.load("return {key='test:slot_screen',"
                + "state={count={type='integer',default=12}},root={component='test:slot',slots={body={{type='text',"
                + "bind=function(v)return {text=tostring(v.state:get('count'))}end}}}}}").call(), components);
        List<Throwable> failures = new ArrayList<>();
        GuiComponentPlan plan = GuiComponentCompiler.prepare(components, OWNER, definition.key, definition.root,
                new Host(), new Services(definition, failures), EnumSet.allOf(GuiCapability.class), null,
                definition.state, Collections.emptyMap(), Collections.emptyMap());
        GuiPresentationTree tree = plan.createTree(EnumSet.allOf(GuiCapability.class), String::length, () -> 0);
        scene(tree);
        GuiNodeDefinition text = plan.document().presentation.nodes.stream().filter(node -> node.type.equals("text"))
                .findFirst().orElseThrow(() -> new AssertionError("Slot content was not expanded"));
        require(tree.runtime().element(text.id).get("text").equals("12") && failures.isEmpty(),
                "Caller-owned slot lost its screen state schema");
        plan.close();
        tree.close();
    }

    private static void appearanceAndAuthority() {
        Globals lua = JsePlatform.standardGlobals();
        GuiThemeDefinition theme = GuiStyleParser.theme(OWNER, lua.load("return {key='test:skin',base='menu',"
                + "variants={default={values={foreground='#FF123456',shadow=false},states={hovered={foreground='#FFABCDEF'}}}}}")
                .call());
        GuiStyleSheet sheet = theme.prepare(new GuiPaintCompiler.Resources() {
            public GuiFontFace font(String key) {
                return GuiFontFace.NATIVE;
            }

            public GuiDrawable image(String key) {
                return GuiDrawable.solid(0);
            }
        });
        GuiStyleSheet inline = sheet
                .withInline(GuiStyleParser.values(lua.load("return {foreground=0,shadow=false}").call(), "inline")
                        .prepare(new GuiPaintCompiler.Resources() {
                            public GuiFontFace font(String key) {
                                return GuiFontFace.NATIVE;
                            }

                            public GuiDrawable image(String key) {
                                return GuiDrawable.solid(0);
                            }
                        }));
        require(sheet.resolve("default", GuiStyleSheet.HOVERED).number("foreground", 0) == 0xFFABCDEF
                && inline.resolve("default", GuiStyleSheet.HOVERED).number("foreground", -1) == 0
                && !inline.resolve("default", 0).flag("shadow", true),
                "Inline transparent/false style precedence failed");
        expect(() -> GuiStyleParser.theme(OWNER,
                lua.load("return {key='test:cycle',variants={a={extends='b'},b={extends='a'}}}").call()));
        expect(() -> GuiStyleParser.theme(OWNER,
                lua.load("return {key='test:metrics',variants={default={states={hovered={padding=10}}}}}").call()));
        expect(() -> GuiStyleParser.theme(OWNER,
                lua.load("return {key='test:template',templates={button={paint=12}}}").call()));
        expect(() -> new GuiResourceParser().parse(lua.load("return {targets={bad={width=4096,height=4096}}}").call(),
                "resources"));
        expect(() -> new GuiResourceParser()
                .parse(lua.load("return {fonts={bad={image='missing',lineHeight=8,fallback=63,"
                        + "glyphs={{codepoint=65,x=0,y=0,width=4,height=8,advance=4}}}}}").call(), "resources"));
        try (LuaCallbackScope scope = new LuaCallbackScope(false, true)) {
            LuaTable model = GuiModelLuaAccess.create(scope, GuiComponentSchema.EMPTY, Collections::emptyMap,
                    values -> {
                        throw new AssertionError("Foreign owner published a UI model");
                    }, OWNER);
            ScriptExecution.withOwner("foreign-ui.lua", () -> {
                expect(() -> model.get("set").call(model, new LuaTable()));
                return null;
            });
            expect(LuaCallbackScope::requireGameplayWrite);
            LuaCallbackScope.requireUiStateWrite();
        }
        ScriptResourceTracker.unload(OWNER);
        expect(theme::requireActive);
    }

    private static int value(GuiComponentPlan plan) {
        return ((Number) plan.state().get(GuiComponentPlan.SCREEN_STATE, "count")).intValue();
    }

    private static String text(GuiPresentationTree tree, GuiComponentPlan plan) {
        return String.valueOf(tree.runtime().element(plan.elementId("readout.label")).get("text"));
    }

    private static GuiScene scene(GuiPresentationTree tree) {
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 240, 320, 240);
        scene.setDocument(tree);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        return scene;
    }
    private static final class Host implements GuiPresentationHost {
        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources) {
            return Collections.emptyMap();
        }

        public List<AutoCloseable> observe(Observer observer) {
            return Collections.emptyList();
        }

        public LuaTable context(LuaCallbackScope scope) {
            return new NativeCallbackTable(scope);
        }

        public GuiPresentationHost forDefinition(GuiDocumentDefinition definition) {
            return this;
        }
    }
    private static final class Services implements GuiComponentPlan.Services {
        final GuiScreenDefinition definition;
        final List<Throwable> failures;
        Services(GuiScreenDefinition definition, List<Throwable> failures) {
            this.definition = definition;
            this.failures = failures;
        }

        public Set<String> events() {
            return new HashSet<>(Arrays.asList("control", "open", "reload"));
        }

        public LuaValue event(LuaTable context, LuaTable event) {
            String type = event.get("type").checkjstring();
            LuaValue handler = definition.callbacks.get(type.equals("reload") ? "refresh" : type);
            return handler == null ? LuaValue.NIL : handler.call(context, event);
        }

        public GuiPaintCompiler.Resources paintResources(String owner) {
            return new GuiPaintCompiler.Resources() {
                public GuiFontFace font(String key) {
                    return GuiFontFace.NATIVE;
                }

                public GuiDrawable image(String key) {
                    throw new IllegalArgumentException("No image");
                }
            };
        }

        public void requireHost() {
        }

        public void failure(String owner, String path, Throwable failure) {
            failures.add(failure);
        }
    }
    private static void expect(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid screen was accepted");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
