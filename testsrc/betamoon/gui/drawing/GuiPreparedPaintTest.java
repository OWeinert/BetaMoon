package betamoon.gui.drawing;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.graphics.GraphicsAffine;
import betamoon.gui.graphics.GraphicsExpression;
import betamoon.gui.graphics.GraphicsHitRegion;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiFontFace;
import betamoon.gui.widget.GuiBuiltInViews;
import betamoon.luaapi.client.ui.GuiLuaPaintSource;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real Lua builds detached paint once; the production frame handles geometry
 * and input without Lua.
 */
public final class GuiPreparedPaintTest {
    public static void main(String[] args) {
        geometry();
        validation();
        invalidIntermediateSample();
        try (Fixture fixture = new Fixture()) {
            fixture.checkPreparationAndInput();
        }
        System.out
                .println("Prepared paint passed: bounded Lua compilation, bulk commands, affine/polygon hit geometry, "
                        + "displayed-frame input, dirty/resize preparation and rejected-refresh retention.");
    }

    private static void geometry() {
        require(!GraphicsHitRegion.circle(0, 0, 0).contains(0, 0), "Zero-radius region captured a pointer");
        GraphicsAffine transform = GraphicsAffine.transform(20, 30, 2, 3, 37);
        double x = transform.mapX(5, 7);
        double y = transform.mapY(5, 7);
        require(Math.abs(transform.localX(x, y) - 5) < 1e-8 && Math.abs(transform.localY(x, y) - 7) < 1e-8,
                "Affine forward/inverse transforms disagree");
        double[] triangle = {0, 0, 10, 0, 0, 10};
        GraphicsHitRegion region = GraphicsHitRegion.polygon(triangle).transformed(transform);
        triangle[0] = 1000;
        require(region.contains(transform.mapX(2, 3), transform.mapY(2, 3))
                && !region.contains(transform.mapX(9, 9), transform.mapY(9, 9)),
                "Polygon copy or transformed hit failed");
        expect(() -> GraphicsHitRegion.polygon(new double[]{0, 0, 10, 10, 0, 10, 10, 0}));
        expect(() -> new GuiPaintProgram.Builder().popClip());
        expect(() -> new GuiPaintProgram.Builder().pushTransform(c(0), c(0), c(1), c(1), c(0)).build());
        GuiPaintProgram clipped = new GuiPaintProgram.Builder().pushClip(c(0.5), c(0), c(0), c(10))
                .hitRectangle("empty", c(0), c(0), c(20), c(20)).popClip().build();
        require(clipped.sample(20, 20, Collections.emptyMap()).hit(1, 1) == null,
                "Empty fractional clip became a hit pixel");
        GuiPaintProgram rotated = new GuiPaintProgram.Builder().pushTransform(c(10), c(0), c(1), c(1), c(90))
                .hitPolygon("triangle", new double[]{0, 0, 10, 0, 0, 10}).popTransform().build();
        require("triangle".equals(rotated.sample(20, 20, Collections.emptyMap()).hit(7, 2)),
                "Prepared transformed polygon failed");
    }

    private static void validation() {
        Globals lua = JsePlatform.standardGlobals();
        GuiPaintCompiler compiler = compiler(properties());
        for (String invalid : new String[]{"{{op='rect',width=4,height=4,typo=true}}", "{{op='popTransform'}}",
                "{{op='pushClip',width=4,height=4}}",
                "{{op='rect',width={source='property',property='missing'},height=4}}",
                "{{op='mesh',vertices={0,0,1,1}}}", "{[2]={op='rect',width=4,height=4}}"}) {
            expect(() -> compiler.compile(lua.load("return " + invalid).call(), "test.paint"));
        }
        expect(() -> compiler.compile(lua.load("return {{op='mesh',mode=12,vertices={0,0,1,0,0,1}}}").call(),
                "test.mode"));
        expect(() -> compiler.compile(lua.load("return {{op='text',font=12,text='bad'}}").call(), "test.font"));
        GuiPaintProgram.Builder oversized = new GuiPaintProgram.Builder();
        expect(() -> {
            for (int index = 0; index < 1025; index++) {
                oversized.rectangle(c(0), c(0), c(1), c(1), -1);
            }
        });
        GuiPaintProgram invalidSize = compiler.compile(lua.load("return {{op='rect',width=-1,height=4}}").call(),
                "test.paint");
        expect(() -> invalidSize.sample(20, 20, properties().baseline()));
    }

    private static void invalidIntermediateSample() {
        Globals lua = JsePlatform.standardGlobals();
        List<GuiPropertyDescriptor> descriptors = new ArrayList<>(
                GuiBuiltInViews.properties("panel").descriptors().values());
        descriptors.add(GuiPropertyDescriptor.number("phase", -1, -1, 1, GuiPropertyDescriptor.Stage.PAINT));
        GuiProperties properties = new GuiProperties("test:sample", descriptors)
                .withDefaults(map("width", 120, "height", 120));
        LuaValue binding = lua.load("return function(v)return {phase=v.data:get('phase')}end").call();
        GuiTransitionPolicy transition = GuiTransitionPolicy
                .parse(lua.load("return {phase={duration=1,easing='linear'}}").call(), Collections.singleton("phase"));
        GuiNodeDefinition node = new GuiNodeDefinition("sample", "sample", "test:sample", -1, null, null, properties,
                binding, transition, false);
        GuiDocumentDefinition definition = new GuiDocumentDefinition("test:sample", "sample-test.lua", 320, 240, null,
                null, Collections.emptyList(), new GuiPresentationDefinition(Collections.singletonList(node),
                        LuaValue.NIL, Collections.emptySet(), "sample-test.lua"),
                true);
        double[] phase = {-1};
        double[] clock = {0};
        int[] builds = {0};
        List<GuiPresentationHost.Observer> observers = new ArrayList<>();
        Host host = new Host() {
            public GuiPresentationSources snapshot() {
                return new GuiPresentationSources(Collections.emptyMap(), Collections.singletonMap("phase", phase[0]),
                        Collections.emptyMap());
            }

            public List<AutoCloseable> observe(Observer observer) {
                observers.add(observer);
                return Collections.singletonList(() -> observers.remove(observer));
            }
        };
        List<Throwable> failures = new ArrayList<>();
        GuiPaintProgram program = new GuiPaintProgram.Builder()
                .pushTransform(c(60), c(60), GraphicsExpression.property("phase", -1, 1, 1, 0), c(1), c(0))
                .hitCircle("center", c(0), c(0), c(40)).popTransform().build();
        try (GuiPresentationTree tree = new GuiPresentationTree(definition, host,
                ignored -> new GuiPaintView((w, h, p) -> {
                    builds[0]++;
                    return program;
                }, (event, region) -> GuiPaintView.Decision.CONSUME, failures::add, false) {
                    protected boolean retainFrameProperties() {
                        return true;
                    }
                }, Collections.emptySet(), String::length, () -> clock[0])) {
            GuiScene scene = new GuiScene();
            scene.updateEnvironment(null, null, 320, 240, 320, 240);
            scene.setDocument(tree);
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
            phase[0] = 1;
            for (GuiPresentationHost.Observer observer : observers) {
                observer.changed("data:phase", -1D, 1D);
            }
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
            GuiPaintProgram.Frame before = (GuiPaintProgram.Frame) scene.getDisplayedFrame()
                    .node(tree.element("sample")).content;
            clock[0] = 0.5;
            tree.sample();
            HeadlessGuiFrames.layout(scene);
            require(scene.getDisplayedFrame().node(tree.element("sample")).content == before && failures.isEmpty(),
                    "Invalid intermediate geometry replaced its usable frame or reported from paint");
            require(((Number) before.properties.get("phase")).doubleValue() == -1,
                    "Fallback raw properties diverged from hit geometry");
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
            scene.prepare();
            require(failures.size() == 1, "Invalid intermediate sample did not report once at preparation");
            clock[0] = 1;
            tree.sample();
            HeadlessGuiFrames.layout(scene);
            require(scene.getDisplayedFrame().node(tree.element("sample")).content != before && builds[0] == 2,
                    "Java animation did not recover without another Lua paint build");
        }
    }

    public static final class Fixture implements AutoCloseable {
        public final Globals lua = JsePlatform.standardGlobals();
        public final GuiScene scene = new GuiScene();
        public GuiPresentationTree tree;
        public final List<Throwable> failures = new ArrayList<>();
        public final List<String> inputs = new ArrayList<>();
        private final GuiProperties properties = properties();
        private final GuiPaintCompiler compiler = compiler(properties);
        private final LuaValue paint;
        private final double[] time = {0};

        public Fixture() {
            paint = lua.load("paintCalls=0; bad=false; return function(v) paintCalls=paintCalls+1; "
                    + "assert(v.width>0 and v.height>0); if bad then return {{op='rect',width=-1,height=4}} end; return {"
                    + "{op='ring',x={source='width',factor=0.5},y={source='height',factor=0.5},inner=30,outer=50,"
                    + "sweep={source='property',property='value',factor=360},color='#FF55AAFF'},"
                    + "{op='hitCircle',name='wheel',x={source='width',factor=0.5},y={source='height',factor=0.5},radius=50}} end")
                    .call();
            tree = new GuiPresentationTree(definition(), new Host(), node -> new GuiPaintView(
                    new GuiLuaPaintSource("prepared-paint-test.lua", "wheel", paint, compiler, scope -> new LuaTable()),
                    (event, region) -> {
                        inputs.add(event.getType().name() + ":" + region);
                        return event.getType() == GuiInputEvent.Type.POINTER_DOWN
                                ? new GuiPaintView.Decision(true, true, false, true)
                                : GuiPaintView.Decision.CONSUME;
                    }, failures::add, true), Collections.emptySet(), text -> text.length() * 6, () -> time[0]);
            scene.setDocument(tree);
            scene.updateEnvironment(null, null, 320, 240, 320, 240);
        }

        public void prepareHeadless() {
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
        }

        public void checkPreparationAndInput() {
            prepareHeadless();
            GuiElement wheel = tree.element("wheel");
            require(lua.get("paintCalls").checkint() == 1, "Paint builder did not prepare once after initial layout");
            for (int index = 0; index < 10; index++) {
                prepareHeadless();
            }
            require(lua.get("paintCalls").checkint() == 1, "Idle preparation polled a paint builder");
            require(!scene.mousePressed(wheel.getLeft() + 1, wheel.getTop() + 1, 0),
                    "Circle hit used the enclosing rectangle");
            require(scene.mousePressed(wheel.getLeft() + 60, wheel.getTop() + 60, 0),
                    "Circle center was not interactive");
            require(inputs.get(0).equals("POINTER_DOWN:wheel"), "Custom input lost its named displayed hit region");
            scene.cancelPointerCapture();
            scene.mouseReleased(wheel.getLeft() + 60, wheel.getTop() + 60, 0);
            require(inputs.size() == 2 && inputs.get(1).startsWith("POINTER_CANCEL"),
                    "Cancelled custom gesture delivered an activation release");
            int oldX = wheel.getLeft() + 60;
            int oldY = wheel.getTop() + 60;
            wheel.arrange(scene.getContext(), Rect.fromPositionAndSize(wheel.getLeft() + 150, wheel.getTop() + 100,
                    wheel.getWidth(), wheel.getHeight()));
            require(scene.mousePressed(oldX, oldY, 0), "Relayout before paint discarded the displayed hit geometry");
            require(inputs.get(inputs.size() - 1).equals("POINTER_DOWN:wheel"),
                    "Input used newly arranged bounds with an old frame");
            scene.cancelPointerCapture();
            scene.updateEnvironment(null, null, 400, 300, 400, 300);
            prepareHeadless();
            require(lua.get("paintCalls").checkint() == 1,
                    "Viewport-only relocation rebuilt a size-independent paint leaf");
            lua.set("bad", LuaValue.TRUE);
            GuiElement root = tree.root();
            try {
                tree.reload(definition(), scene::prepareDetached);
                throw new AssertionError("Invalid prepared paint retired the active revision");
            } catch (IllegalArgumentException expected) {
                require(tree.root() == root && !failures.isEmpty(), "Rejected paint refresh discarded the usable tree");
            }
            lua.set("bad", LuaValue.FALSE);
            require(tree.root() == wheel, "Failed paint preparation changed view identity");
        }

        private GuiDocumentDefinition definition() {
            GuiNodeDefinition node = new GuiNodeDefinition("wheel", "wheel", "test:radial", -1, null, null, properties,
                    LuaValue.NIL, GuiTransitionPolicy.DEFAULT, false);
            GuiPresentationDefinition presentation = new GuiPresentationDefinition(Collections.singletonList(node),
                    LuaValue.NIL, Collections.emptySet(), "prepared-paint-test.lua");
            return new GuiDocumentDefinition("test:radial", "prepared-paint-test.lua", 320, 240, null, null,
                    Collections.emptyList(), presentation, true);
        }

        public void close() {
            tree.close();
        }
    }

    private static GuiProperties properties() {
        List<GuiPropertyDescriptor> descriptors = new ArrayList<>(
                GuiBuiltInViews.properties("panel").descriptors().values());
        descriptors.add(GuiPropertyDescriptor.number("value", 0.75, 0, 1, GuiPropertyDescriptor.Stage.PAINT));
        return new GuiProperties("test:radial", descriptors).withDefaults(map("width", 120, "height", 120));
    }

    private static Map<String, Object> map(String first, Object a, String second, Object b) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put(first, a);
        result.put(second, b);
        return result;
    }

    private static GuiPaintCompiler compiler(GuiProperties properties) {
        return new GuiPaintCompiler(properties, new GuiPaintCompiler.Resources() {
            public GuiFontFace font(String key) {
                if (!"native".equals(key)) {
                    throw new IllegalArgumentException("Unknown prepared font: " + key);
                }
                return GuiFontFace.NATIVE;
            }

            public GuiDrawable image(String key) {
                throw new IllegalArgumentException("Unknown prepared image: " + key);
            }
        });
    }

    private static class Host implements GuiPresentationHost {
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

        public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
            return new Host();
        }
    }

    private static GraphicsExpression c(double value) {
        return GraphicsExpression.constant(value);
    }

    private static void expect(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid prepared geometry was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
