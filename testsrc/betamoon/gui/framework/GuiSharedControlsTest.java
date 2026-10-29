package betamoon.gui.framework;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.control.GuiActivation;
import betamoon.gui.control.GuiNumericRange;
import betamoon.gui.control.GuiTextEditOperations;
import betamoon.gui.control.GuiTextEditorModel;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.style.GuiBitmapFont;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiAtlasLease;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.widget.GuiBuiltInViews;
import betamoon.gui.widget.GuiStyledView;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;

/**
 * Behavioral contracts run through the production scene with both Java and real
 * Lua-derived properties.
 */
public final class GuiSharedControlsTest {
    public static void main(String[] args) {
        commonModels();
        styleAndFontContracts();
        javaAndLuaControls();
        visibilityAndClipping();
        rejectedValues();
        controlledModeAndInvalidRefresh();
        System.out.println("Shared controls passed: Java/Lua views, activation timing/cancel, draft conflicts, "
                + "numeric ranges, visibility, templates, style inheritance and bitmap metrics.");
    }

    private static void commonModels() {
        GuiActivation legacy = new GuiActivation(GuiActivation.Timing.LEGACY_PRESS);
        require((legacy.input(GuiInputEvent.pointerDown(2, 2, 0), true, false) & GuiActivation.ACTIVATE) != 0,
                "Legacy button lost press-time activation");
        require((legacy.input(GuiInputEvent.pointerCancel(0), false, false) & GuiActivation.ACTIVATE) == 0,
                "Cancellation activated a legacy button");
        GuiNumericRange decimal = new GuiNumericRange(0, 1, 0.1);
        require(decimal.valueAt(3) == 0.3 && decimal.index(0.3) == 3, "Decimal config steps drifted");
        require(GuiNumericRange.snap(0, 10, 2, 3) == 4, "Legacy midpoint snapping changed");
        GuiTextEditOperations.Result bounded = GuiTextEditOperations.replace("abcdef", 1, 3,
                new String(new char[100000]).replace('\0', 'x'), 6);
        require(bounded.text.equals("axxxxx") && bounded.caret == 6, "Bounded paste changed truncation semantics");
        GuiTextEditorModel editor = new GuiTextEditorModel("base", 20);
        editor.insert(" draft");
        editor.observe("server");
        require(editor.conflict() && editor.text().equals("base draft"), "External publication discarded a draft");
        editor.commit(false);
        require(editor.invalid() && editor.text().equals("base draft"), "Rejected commit discarded editing state");
        editor.cancel();
        require(editor.text().equals("server") && !editor.conflict() && !editor.invalid(),
                "Cancel missed current committed value");
        GuiTextEditorModel clean = new GuiTextEditorModel("old", 20);
        GuiTextEditorModel replacement = new GuiTextEditorModel("new", 20);
        replacement.retainFrom(clean);
        require(replacement.text().equals("new"), "Clean reload retained stale text over a new value");
        editor.insert(" edit");
        editor.commit(true, true);
        editor.observe("server");
        require(editor.pending() && editor.text().equals("server edit"),
                "Controlled commit lost its draft before acknowledgment");
        editor.observe("server edit");
        require(!editor.pending() && !editor.conflict() && editor.text().equals(editor.committed()),
                "Model acknowledgment did not finish the controlled commit");
    }

    private static void styleAndFontContracts() {
        Map<String, GuiStyleSheet.Rule> rules = new LinkedHashMap<>();
        rules.put("default", new GuiStyleSheet.Rule(null, GuiControlStyle.of("foreground", 0xFFFFFFFF, "shadow", true),
                Collections.singletonMap(GuiStyleSheet.HOVERED, GuiControlStyle.of("foreground", 0xFF0000FF))));
        rules.put("plain", new GuiStyleSheet.Rule("default", GuiControlStyle.of("shadow", false, "background", 0),
                Collections.singletonMap(GuiStyleSheet.DISABLED, GuiControlStyle.of("foreground", 0xFF888888))));
        GuiStyleSheet styles = new GuiStyleSheet(rules);
        GuiControlStyle disabled = styles.resolve("plain", GuiStyleSheet.HOVERED | GuiStyleSheet.DISABLED);
        require(disabled.number("foreground", 0) == 0xFF888888 && !disabled.flag("shadow", true)
                && disabled.number("background", -1) == 0, "Style precedence lost disabled/false/transparent values");
        rules.put("default", new GuiStyleSheet.Rule("plain", GuiControlStyle.of(), Collections.emptyMap()));
        expect(() -> new GuiStyleSheet(rules));
        GuiAtlasLease atlas = atlas();
        Map<Integer, GuiBitmapFont.Glyph> glyphs = new LinkedHashMap<>();
        glyphs.put((int) '?', new GuiBitmapFont.Glyph(0, 0, 2, 3, 4, 0, 0));
        glyphs.put(0x1F600, new GuiBitmapFont.Glyph(2, 0, 3, 3, 6, 0, 0));
        GuiBitmapFont font = new GuiBitmapFont(atlas, 5, '?', glyphs);
        require(font.width(null, "A\uD83D\uDE00") == 10 && font.lineHeight(null) == 5,
                "Bitmap fallback or supplementary-codepoint metrics failed");
        glyphs.put((int) 'x', new GuiBitmapFont.Glyph(7, 0, 3, 3, 3, 0, 0));
        expect(() -> new GuiBitmapFont(atlas, 5, '?', glyphs));
        GuiVisualTemplate incomplete = new GuiVisualTemplate() {
            public Set<String> parts() {
                return Collections.singleton("content");
            }

            public void paint(GuiContext context, GuiStyledView view, GuiControlStyle style) {
            }
        };
        expect(() -> new GuiStyledView("slider", styles, incomplete, (event, value) -> true));
    }

    private static GuiAtlasLease atlas() {
        return new GuiAtlasLease() {
            public int width() {
                return 8;
            }

            public int height() {
                return 8;
            }

            public int textureId() {
                return 1;
            }

            public void requireActive() {
            }
        };
    }

    private static void javaAndLuaControls() {
        for (GuiStyleSheet theme : Arrays.asList(GuiStyleSheet.MENU, GuiStyleSheet.MINIMAL)) {
            List<String> events = new ArrayList<>();
            GuiStyledView javaButton = new GuiStyledView("button", theme, GuiDefaultTemplate.INSTANCE,
                    (event, value) -> events.add(event)).configure(values("text", "Java", "width", 100, "height", 24));
            GuiScene javaScene = scene(javaButton);
            activation(javaScene, javaButton, events);
            Globals lua = JsePlatform.standardGlobals();
            LuaValue binding = lua.load("calls=0; return function(v) calls=calls+1; return {text='Lua'} end").call();
            try (GuiPresentationTree tree = document(theme, events, binding)) {
                GuiScene scene = new GuiScene();
                scene.setDocument(tree);
                scene.updateEnvironment(null, null, 320, 240, 320, 240);
                scene.prepare();
                HeadlessGuiFrames.layout(scene);
                GuiStyledView button = (GuiStyledView) tree.element("button");
                activation(scene, button, events);
                require(button.text().equals("Lua"), "Lua binding did not feed the shared Java control");
                GuiStyledView checkbox = (GuiStyledView) tree.element("checkbox");
                click(scene, checkbox);
                tree.sample();
                require(checkbox.selected(), "Uncontrolled selection was reset by sampling");
                GuiStyledView choice = (GuiStyledView) tree.element("choice");
                click(scene, choice);
                require(choice.text().equals("two"), "Choice did not advance its local selection");
                click(scene, choice);
                require(choice.text().equals("one"), "Choice did not cycle");
                GuiStyledView slider = (GuiStyledView) tree.element("slider");
                click(scene, slider);
                scene.keyTyped('\0', Keyboard.KEY_END, false);
                require(slider.fraction() == 1, "Shared slider did not handle keyboard endpoints");
                GuiStyledView editor = (GuiStyledView) tree.element("textbox");
                click(scene, editor);
                scene.keyTyped('x', Keyboard.KEY_X, false);
                int before = events.size();
                scene.keyTyped('\0', Keyboard.KEY_LEFT, false);
                require(events.size() == before, "Caret movement emitted a text change");
                scene.keyTyped('\0', Keyboard.KEY_ESCAPE, false);
                require(editor.text().equals("seed"), "Text cancel did not restore the committed value");
                GuiPresentationTree next = tree.reload(definition(binding));
                require(((GuiStyledView) next.element("checkbox")).selected(), "Reload lost local selection");
                next.close();
                require(lua.get("calls").checkint() == 2, "Sampling, input or paint evaluated a binding");
            }
        }
    }

    private static void activation(GuiScene scene, GuiStyledView button, List<String> events) {
        int x = button.getLeft() + 2;
        int y = button.getTop() + 2;
        int before = events.size();
        scene.mousePressed(x, y, 0);
        require(events.size() == before, "Release-time control activated on press");
        scene.cancelPointerCapture();
        scene.mouseReleased(x, y, 0);
        require(events.size() == before, "Cancelled gesture activated a button");
        click(scene, button);
        require(events.size() == before + 1, "Pointer release failed to activate once");
        scene.keyTyped(' ', Keyboard.KEY_SPACE, false);
        scene.keyTyped(' ', Keyboard.KEY_SPACE, false);
        require(events.size() == before + 1, "Key repeat activated a release-time button");
        scene.keyReleased(Keyboard.KEY_SPACE, false);
        require(events.size() == before + 2, "Keyboard release did not activate exactly once");
    }

    private static void visibilityAndClipping() {
        GuiStyledView root = view("column", values("width", 100, "height", 100, "gap", 5));
        GuiStyledView first = root.add(view("button", values("height", 20)));
        GuiStyledView second = root.add(view("button", values("height", 20)));
        first.setHidden(true);
        GuiScene scene = scene(root);
        require(second.getTop() - root.getTop() == 25, "Hidden view did not reserve layout space");
        first.setVisible(false);
        HeadlessGuiFrames.layout(scene);
        require(second.getTop() == root.getTop(), "Collapsed view retained size or a layout gap");
        GuiStyledView overlay = view("overlay", values("width", 40, "height", 40, "align", "start"));
        GuiStyledView outside = overlay.add(view("button", values("width", 30, "height", 20, "x", 35)));
        scene = scene(overlay);
        require(scene.mousePressed(outside.getRight() - 1, outside.getTop() + 1, 0),
                "Unclipped visible child could not receive input beyond its parent");
        scene.cancelPointerCapture();
        overlay.configure(values("width", 40, "height", 40, "align", "start", "clip", true));
        HeadlessGuiFrames.layout(scene);
        require(!scene.mousePressed(outside.getRight() - 1, outside.getTop() + 1, 0),
                "Clipped child received outside input");
    }

    private static void rejectedValues() {
        expect(() -> view("textbox", values("maximumLength", 2, "text", "long")));
        expect(() -> view("slider", values("minimum", 10, "maximum", 0)));
        expect(() -> view("choice", values("options", Collections.emptyList())));
        expect(() -> view("panel", values("variant", "missing")));
        expect(() -> view("panel", values("scaleX", 0)));
    }

    private static void controlledModeAndInvalidRefresh() {
        Globals lua = JsePlatform.standardGlobals();
        LuaValue binding = lua.load("return function(v) if v:interaction('selected','checkbox') then "
                + "return {value=25} else return {} end end").call();
        GuiDocumentDefinition base = definition(LuaValue.NIL);
        List<GuiNodeDefinition> nodes = new ArrayList<>();
        for (GuiNodeDefinition node : base.presentation.nodes) {
            nodes.add(new GuiNodeDefinition(node.id, node.path, node.type, node.parent, node.element, node.condition,
                    node.properties, "slider".equals(node.id) ? binding : node.binding, node.transition,
                    node.explicitTransition));
        }
        GuiPresentationDefinition presentation = new GuiPresentationDefinition(nodes, LuaValue.NIL,
                Collections.emptySet(), base.owner);
        GuiDocumentDefinition definition = new GuiDocumentDefinition(base.name, base.owner, base.width, base.height,
                null, null, Collections.emptyList(), presentation, true);
        try (GuiPresentationTree tree = new GuiPresentationTree(definition, new Host(),
                new GuiBuiltInViews(GuiStyleSheet.MINIMAL, node -> (event, value) -> true), Collections.emptySet(),
                text -> text.length() * 6, () -> 0)) {
            GuiScene scene = new GuiScene();
            scene.setDocument(tree);
            scene.updateEnvironment(null, null, 320, 240, 320, 240);
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
            GuiStyledView slider = (GuiStyledView) tree.element("slider");
            click(scene, slider);
            scene.keyTyped('\0', Keyboard.KEY_END, false);
            require(slider.fraction() == 1, "Uncontrolled mode did not retain a local proposal");
            click(scene, tree.element("checkbox"));
            tree.update();
            require(slider.fraction() == 0.25,
                    "Binding gained control at an unchanged target without publishing ownership");
            List<GuiNodeDefinition> invalid = new ArrayList<>();
            for (GuiNodeDefinition node : base.presentation.nodes) {
                Map<String, Object> properties = new LinkedHashMap<>(node.properties.baseline());
                properties.put("scaleX", 1000);
                invalid.add(new GuiNodeDefinition(node.id, node.path, node.type, node.parent, node.element,
                        node.condition, node.properties.withDefaults(properties), node.binding, node.transition,
                        node.explicitTransition));
            }
            GuiDocumentDefinition bad = new GuiDocumentDefinition(base.name, base.owner, base.width, base.height, null,
                    null, Collections.emptyList(),
                    new GuiPresentationDefinition(invalid, LuaValue.NIL, Collections.emptySet(), base.owner), true);
            GuiElement activeRoot = tree.root();
            try {
                tree.reload(bad);
                throw new AssertionError("Unsafe nested transform refresh was accepted");
            } catch (LuaError expected) {
                require(tree.root() == activeRoot && ((GuiStyledView) tree.element("checkbox")).selected(),
                        "Invalid transform refresh discarded the usable tree or its state");
            }
        }
    }

    private static GuiStyledView view(String kind, Map<String, Object> properties) {
        return new GuiStyledView(kind, GuiStyleSheet.MINIMAL, GuiDefaultTemplate.INSTANCE, (event, value) -> true)
                .configure(properties);
    }

    private static GuiScene scene(GuiElement content) {
        GuiScene scene = new GuiScene();
        scene.setContent(content);
        scene.updateEnvironment(null, null, 320, 240, 320, 240);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        return scene;
    }

    private static void click(GuiScene scene, GuiElement element) {
        int x = element.getLeft() + Math.max(1, element.getWidth() / 2);
        int y = element.getTop() + Math.max(1, element.getHeight() / 2);
        scene.mousePressed(x, y, 0);
        scene.mouseReleased(x, y, 0);
    }

    public static GuiPresentationTree document(GuiStyleSheet theme, List<String> events, LuaValue binding) {
        return document(theme, events, binding, node -> GuiDefaultTemplate.INSTANCE);
    }

    public static GuiPresentationTree document(GuiStyleSheet theme, List<String> events, LuaValue binding,
            Function<GuiNodeDefinition, GuiVisualTemplate> templates) {
        return new GuiPresentationTree(definition(binding), new Host(),
                new GuiBuiltInViews(theme, templates, node -> (event, value) -> events.add(node.id + ":" + event)),
                Collections.emptySet(), text -> text.length() * 6, () -> 0);
    }

    public static GuiPresentationTree document(Supplier<GuiStyleSheet> theme) {
        return new GuiPresentationTree(definition(LuaValue.NIL), new Host(),
                node -> new GuiStyledView(node.type, theme.get(), GuiDefaultTemplate.INSTANCE, (event, value) -> true),
                Collections.emptySet(), text -> text.length() * 6, () -> 0);
    }

    public static GuiDocumentDefinition definition(LuaValue binding) {
        List<GuiNodeDefinition> nodes = new ArrayList<>();
        nodes.add(
                node("root", "column", -1, values("width", 180, "height", 192, "padding", 6, "gap", 5), LuaValue.NIL));
        nodes.add(node("button", "button", 0, values("text", "Action", "height", 24), binding));
        nodes.add(node("checkbox", "checkbox", 0, values("text", "Enabled", "height", 24), LuaValue.NIL));
        nodes.add(node("choice", "choice", 0, values("options", Arrays.asList("one", "two"), "height", 24),
                LuaValue.NIL));
        nodes.add(node("slider", "slider", 0, values("value", 25, "height", 24), LuaValue.NIL));
        nodes.add(node("textbox", "textbox", 0, values("text", "seed", "height", 24), LuaValue.NIL));
        nodes.add(node("progress", "progress", 0, values("value", 65, "height", 8), LuaValue.NIL));
        GuiPresentationDefinition presentation = new GuiPresentationDefinition(nodes, LuaValue.NIL,
                Collections.emptySet(), "shared_control_fixture.lua");
        return new GuiDocumentDefinition("test:shared_controls", "shared_control_fixture.lua", 320, 240, null, null,
                Collections.emptyList(), presentation, true);
    }

    private static GuiNodeDefinition node(String id, String kind, int parent, Map<String, Object> values,
            LuaValue binding) {
        return new GuiNodeDefinition(id, id, kind, parent, null, null,
                GuiBuiltInViews.properties(kind).withDefaults(values), binding, GuiTransitionPolicy.DEFAULT, false);
    }

    private static final class Host implements GuiPresentationHost {
        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources values) {
            return Collections.emptyMap();
        }

        public List<AutoCloseable> observe(Observer observer) {
            return Collections.emptyList();
        }

        public LuaTable context(LuaCallbackScope scope) {
            return new LuaTable();
        }

        public GuiPresentationHost forDefinition(GuiDocumentDefinition definition) {
            return new Host();
        }
    }

    public static Map<String, Object> values(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
    }

    private static void expect(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid control, style, template or font was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
