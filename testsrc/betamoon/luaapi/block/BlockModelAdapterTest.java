package betamoon.luaapi.block;

import betamoon.assets.AssetPath;
import betamoon.assets.io.AssetProvider;
import betamoon.assets.model.ModelFoundationTest;
import betamoon.client.assets.ClientAssets;
import betamoon.client.render.ModelAppearanceSet;
import java.util.Collections;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.src.Block;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.RenderBlocks;
import net.minecraft.src.Tessellator;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Checks chunk vertex coordinates, native tessellator ownership, and the
 * static/dynamic boundary.
 */
public final class BlockModelAdapterTest {
    private BlockModelAdapterTest() {
    }

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), "png", png);
        ClientAssets.useProviders(new AssetProvider() {
            public boolean exists(AssetPath path) {
                return true;
            }

            public String getName() {
                return "fixture";
            }

            public byte[] read(AssetPath path, int limit) {
                return path.toString().endsWith(".png")
                        ? png.toByteArray()
                        : ModelFoundationTest.GEOMETRY.getBytes(StandardCharsets.UTF_8);
            }
        }, null, message -> {
            throw new AssertionError(message);
        });
        Field type = BlockModelRegistry.class.getDeclaredField("renderType");
        type.setAccessible(true);
        type.setInt(null, 777);
        Globals lua = JsePlatform.standardGlobals();
        lua.load("definition={model='test.json',texture='test.png'}; calls=0").call();
        ModelAppearanceDeclaration definition = new ModelAppearanceDeclaration(lua.get("definition"));
        BlockModelRegistry.validate(definition);
        IBlockAccess world = (IBlockAccess) Proxy.newProxyInstance(BlockModelAdapterTest.class.getClassLoader(),
                new Class<?>[]{IBlockAccess.class}, (proxy, method, arguments) -> {
                    if (method.getReturnType() == float.class) {
                        return 1f;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                });
        RenderBlocks renderer = new RenderBlocks(world);
        renderer.overrideBlockTexture = 240;
        Capture capture = new Capture();
        Tessellator original = Tessellator.instance;
        Tessellator.instance = capture;
        try (ModelAppearanceSet appearance = new ModelAppearanceSet(definition, Collections.emptyMap())) {
            BlockModelRegistry.install(1, appearance);
            require(BlockModelRegistry.renderWorld(renderer, world, 10, 20, 30, Block.stone),
                    "Static model must emit chunk geometry");
            require(capture.vertices.size() == 96, "Two cubes must emit front/back quads");
            require(Tessellator.instance == capture, "Native tessellator must be restored");
            double[] first = capture.vertices.get(0);
            require(Math.abs(first[0] - 10.5625) < 0.00001 && Math.abs(first[1] - 20.5) < 0.00001,
                    "Model units must convert to block bottom-center coordinates");
            require(first[3] >= 0 && first[3] <= 1 && first[4] >= 0.9375 && first[4] <= 1,
                    "Breaking overlay UV must use the native atlas slot");
            Field dirty = BlockModelRegistry.class.getDeclaredField("chunksDirty");
            dirty.setAccessible(true);
            dirty.setBoolean(null, false);
            ClientAssets.refresh();
            require(dirty.getBoolean(null), "Model replacement must request chunk invalidation");
        } finally {
            Tessellator.instance = original;
        }
        lua.load("definition.onPose=function(ctx) calls=calls+1 end").call();
        try (ModelAppearanceSet animated = new ModelAppearanceSet(new ModelAppearanceDeclaration(lua.get("definition")),
                Collections.emptyMap())) {
            BlockModelRegistry.install(1, animated);
            require(!BlockModelRegistry.renderWorld(renderer, world, 10, 20, 30, Block.stone),
                    "Dynamic models must stay out of chunk geometry");
            require(lua.get("calls").toint() == 0, "Chunk builds must not invoke pose animation");
        }
        System.out.println(
                "Block model adapters passed: units, breaking UVs, tessellator ownership, invalidation and dynamic separation.");
    }

    private static final class Capture extends Tessellator {
        private final List<double[]> vertices = new ArrayList<>();
        public void addVertexWithUV(double x, double y, double z, double u, double v) {
            vertices.add(new double[]{x, y, z, u, v});
        }

        public void setColorOpaque_F(float r, float g, float b) {
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
