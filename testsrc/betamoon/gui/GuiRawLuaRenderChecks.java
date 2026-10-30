package betamoon.gui;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.drawing.GuiPaintView;
import betamoon.gui.drawing.GuiRawDrawing;
import betamoon.gui.drawing.GuiRawView;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.graphics.GraphicsExpression;
import betamoon.gui.graphics.GraphicsFramebuffer;
import betamoon.gui.graphics.GraphicsResources;
import betamoon.gui.graphics.GraphicsShaderProgram;
import betamoon.gui.graphics.GraphicsVertexBuffer;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.widget.GuiBuiltInViews;
import betamoon.gui.framework.GuiSharedControlsTest;
import betamoon.luaapi.interop.NativeCallbackTable;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Actual Lua raw callbacks, prepared GPU resources, scoped authority and
 * failure isolation in the pbuffer.
 */
final class GuiRawLuaRenderChecks {
    static void run(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        String owner = "raw-ui-render-test.lua";
        List<Throwable> failures = new ArrayList<>();
        Globals lua = JsePlatform.standardGlobals();
        lua.set("writeState", new ZeroArgFunction() {
            public LuaValue call() {
                LuaCallbackScope.requireUiStateWrite();
                return NIL;
            }
        });
        lua.set("writeWorld", new ZeroArgFunction() {
            public LuaValue call() {
                LuaCallbackScope.requireGameplayWrite();
                return NIL;
            }
        });
        LuaValue callback = lua.load("calls=0; bad=false; return function(frame) calls=calls+1; saved=frame.gl; "
                + "assert(frame.bounds.width==100 and frame.bounds.height==80); assert(frame.data.messages[1]=='detached'); "
                + "assert(not pcall(function() frame.data.messages[1]='mutated' end)); "
                + "assert(not pcall(function() table.insert(frame.data.messages,'mutated') end)); "
                + "assert(not pcall(writeState) and not pcall(writeWorld)); "
                + "frame.gl:enable('texture',false); frame.gl:program('shader'); "
                + "frame.gl:uniform('tint',0.25,0.75,0.5,1); frame.gl:uniformMatrix('adjustment',{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}); frame.gl:buffer('quad'); frame.gl:program(nil); "
                + "frame.gl:target('scratch'); frame.gl:clear(0xFF112233); frame.gl:target(nil); "
                + "if bad then frame.gl:push(); frame.gl:translate(4,5); error('raw fixture failure') end end").call();
        try (GraphicsShaderProgram shader = new GraphicsShaderProgram(
                "uniform mat4 adjustment;void main(){gl_Position=gl_ModelViewProjectionMatrix*adjustment*gl_Vertex;}",
                "uniform vec4 tint; void main(){gl_FragColor=tint;}", uniforms());
                GraphicsVertexBuffer buffer = new GraphicsVertexBuffer(GL11.GL_QUADS, 2,
                        new float[]{0, 0, 100, 0, 100, 80, 0, 80});
                GraphicsFramebuffer target = new GraphicsFramebuffer(16, 16, true)) {
            GraphicsResources resources = new GraphicsResources() {
                public GraphicsShaderProgram program(String key) {
                    if (!"shader".equals(key)) {
                        throw GraphicsResources.missing("program", key);
                    }
                    return shader;
                }

                public GraphicsVertexBuffer buffer(String key) {
                    if (!"quad".equals(key)) {
                        throw GraphicsResources.missing("buffer", key);
                    }
                    return buffer;
                }

                public GraphicsFramebuffer target(String key) {
                    if (!"scratch".equals(key)) {
                        throw GraphicsResources.missing("target", key);
                    }
                    return target;
                }
            };
            GuiRawDrawing drawing = new GuiRawDrawing(owner, "test:raw", callback, NativeCallbackTable::new, () -> {
            }, resources, failures::add);
            drawing.publish(Collections.singletonMap("messages", Arrays.asList("detached")));
            GuiScene scene = new GuiScene();
            GuiNodeDefinition node = new GuiNodeDefinition("raw", "raw", "test:raw", -1, null, null,
                    GuiBuiltInViews.properties("panel")
                            .withDefaults(GuiSharedControlsTest.values("width", 100, "height", 80)),
                    LuaValue.NIL, GuiTransitionPolicy.DEFAULT, false);
            GuiDocumentDefinition definition = new GuiDocumentDefinition("test:raw", owner, 320, 240, null, null,
                    Collections.emptyList(), new GuiPresentationDefinition(Collections.singletonList(node),
                            LuaValue.NIL, Collections.emptySet(), owner),
                    true);
            try (GuiPresentationTree tree = new GuiPresentationTree(definition, new Host(),
                    ignored -> new GuiRawView(
                            (width, height,
                                    properties) -> new GuiPaintProgram.Builder().hitRectangle("raw",
                                            GraphicsExpression.constant(0), GraphicsExpression.constant(0),
                                            GraphicsExpression.width(1, 0), GraphicsExpression.height(1, 0)).build(),
                            (event, region) -> GuiPaintView.Decision.CONSUME, failures::add, true, drawing),
                    Collections.emptySet(), text -> 0, () -> 0)) {
                scene.setDocument(tree);
                scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
                scene.prepare();
                try (LuaCallbackScope mutableOuter = new LuaCallbackScope(true, true)) {
                    for (int frame = 0; frame < 3; frame++) {
                        GuiShowcaseRenderTest.setupFrame();
                        scene.render(0, 0, 0);
                        require(!drawing.failed(), "Raw callback failed: " + failures);
                    }
                    LuaCallbackScope.requireUiStateWrite();
                    LuaCallbackScope.requireGameplayWrite();
                }
                require(lua.get("calls").checkint() == 3, "Explicit raw callback did not execute once per paint");
                BufferedImage pixels = GuiShowcaseRenderTest.capture(320, 240);
                int pixel = pixels.getRGB(160, 120) & 0xFFFFFF;
                require(Math.abs((pixel >> 16 & 255) - 64) <= 1 && Math.abs((pixel >> 8 & 255) - 191) <= 1
                        && Math.abs((pixel & 255) - 128) <= 1,
                        "Prepared shader/VBO pixels were incorrect: " + Integer.toHexString(pixel));
                ImageIO.write(pixels, "png", new File(output, "raw-ui-shader-buffer.png"));
                require(!lua.load("return pcall(function() saved:color(0xFFFFFFFF) end)").call().toboolean(),
                        "A retained raw graphics facade worked after its pass");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Raw callbacks/resources produced a GL error");
                int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
                lua.set("bad", LuaValue.TRUE);
                GuiShowcaseRenderTest.setupFrame();
                scene.render(0, 0, 0);
                require(drawing.failed() && GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) == depth,
                        "Raw failure did not disable the callback and unwind its transform stack");
                require(failures.isEmpty(), "Raw failure handler executed during painting");
                scene.prepare();
                require(failures.size() == 1, "Raw failure was not reported exactly once at an update boundary");
                int calls = lua.get("calls").checkint();
                GuiShowcaseRenderTest.setupFrame();
                scene.render(0, 0, 0);
                require(lua.get("calls").checkint() == calls, "Failed raw callback was retried during paint");
            }
            ScriptResourceTracker.unload(owner);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Raw cleanup leaked GL errors");
        }
        require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == 0 && GL11.glGetError() == GL11.GL_NO_ERROR,
                "Prepared raw resource disposal leaked bindings/errors");
    }

    private static Map<String, Integer> uniforms() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("tint", 4);
        values.put("adjustment", 16);
        return values;
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

        public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
            return new Host();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
