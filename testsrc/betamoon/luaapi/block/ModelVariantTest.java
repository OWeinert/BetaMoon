package betamoon.luaapi.block;

import betamoon.assets.AssetPath;
import betamoon.assets.io.AssetProvider;
import betamoon.assets.model.ModelFoundationTest;
import betamoon.client.assets.ClientAssets;
import betamoon.client.render.ModelAppearanceSet;
import betamoon.luaapi.item.ItemModelVariantTest;
import betamoon.wrappers.BlockWrapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.Block;
import net.minecraft.src.Material;
import net.minecraft.src.RenderBlocks;
import net.minecraft.src.Tessellator;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Exercises metadata selection, native fallback, and the block-wide rendering
 * policy.
 */
public final class ModelVariantTest {
    private ModelVariantTest() {
    }

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), "png", png);
        ClientAssets.useProviders(new AssetProvider() {
            public boolean exists(AssetPath path) {
                return !path.toString().equals("missing.json");
            }

            public String getName() {
                return "variant fixture";
            }

            public byte[] read(AssetPath path, int limit) {
                if (path.toString().equals("missing.json")) {
                    return null;
                }
                return path.toString().endsWith(".png")
                        ? png.toByteArray()
                        : (path.toString().endsWith(".animation.json")
                                ? ModelFoundationTest.ANIMATIONS
                                : ModelFoundationTest.GEOMETRY).getBytes(StandardCharsets.UTF_8);
            }
        }, null, message -> {
        });
        Field type = BlockModelRegistry.class.getDeclaredField("renderType");
        type.setAccessible(true);
        type.setInt(null, 777);
        Globals lua = JsePlatform.standardGlobals();
        lua.load("base={model='test.json',texture='test.png'}; "
                + "shifted={model='test.json',texture='test.png',position={x=16,y=0,z=0}}; "
                + "definition={id=221,key='variants',material='rock',appearance=base,render={variants={"
                + "[1]={appearance=shifted},[2]={appearance=false,texture=5},[3]={color=0x123456}}}}").call();
        verifyStatic(lua);
        verifyDynamic(lua);
        verifyDeclarationErrors(lua);
        ItemModelVariantTest.run(lua);
        System.out.println("Model variants passed: metadata selection, fallback, static/dynamic block policy, "
                + "hot reload, and item selection.");
    }

    private static void verifyStatic(Globals lua) throws Exception {
        BlockDeclaration declaration = new BlockDeclaration(lua.get("definition"));
        require(!declaration.callbacks.visual.isDynamic(), "Static variants must not require dynamic rendering");
        BlockWrapper block = new BlockWrapper(221, 0, Material.rock, "variants");
        int[] metadata = {0};
        IBlockAccess world = world(metadata);
        Capture capture = new Capture();
        Tessellator previous = Tessellator.instance;
        try (ModelAppearanceSet appearances = new ModelAppearanceSet(declaration.appearance,
                declaration.callbacks.visual.appearances())) {
            BlockModelRegistry.install(221, appearances);
            BlockCallbackRegistry.install(221, declaration.callbacks);
            require(!BlockModelRegistry.isDynamic(221), "All-static block must stay static");
            require(appearances.select(0) == appearances.select(3), "Omitted appearance inherits default");
            require(appearances.select(2) == null, "False explicitly selects ordinary rendering");
            require(appearances.select(1).geometry() == appearances.select(0).geometry(), "Geometry cache is shared");
            RenderBlocks renderer = new RenderBlocks(world);
            renderer.overrideBlockTexture = 240;
            Tessellator.instance = capture;
            require(BlockModelRegistry.renderWorld(renderer, world, 0, 0, 0, block), "Default emits chunk geometry");
            double baseX = capture.vertices.get(0)[0];
            capture.vertices.clear();
            metadata[0] = 1;
            require(BlockModelRegistry.renderWorld(renderer, world, 0, 0, 0, block), "Variant emits chunk geometry");
            require(Math.abs(capture.vertices.get(0)[0] - baseX - 1) < 0.00001, "Metadata selects shifted geometry");
            metadata[0] = 2;
            RenderBlocks ordinary = new RenderBlocks(world) {
                public boolean renderBlockByRenderType(Block selected, int x, int y, int z) {
                    require(selected.getRenderType() == 0, "Fallback must use native cube renderer without recursion");
                    return true;
                }
            };
            require(BlockModelRegistry.renderWorld(ordinary, world, 0, 0, 0, block),
                    "False variant routes to native renderer");
            require(block.getRenderType() == 777, "Fallback routing restores model dispatch");
            try {
                BlockModelRegistry.renderOrdinaryWorld(new RenderBlocks(world) {
                    public boolean renderBlockByRenderType(Block selected, int x, int y, int z) {
                        throw new IllegalStateException("expected native failure");
                    }
                }, block, 0, 0, 0);
                throw new AssertionError("Expected native renderer failure");
            } catch (IllegalStateException expected) {
                require(block.getRenderType() == 777, "Failure must restore render dispatch");
            }
            BlockModelRegistry.renderOrdinaryInventory(new RenderBlocks() {
                public void renderBlockOnInventory(Block selected, int damage, float brightness) {
                    require(damage == 2 && selected.getRenderType() == 0,
                            "Inventory fallback keeps metadata and native type");
                }
            }, block, 2);
            Field dirty = BlockModelRegistry.class.getDeclaredField("chunksDirty");
            dirty.setAccessible(true);
            dirty.setBoolean(null, false);
            ClientAssets.refresh();
            require(dirty.getBoolean(null), "Variant asset refresh invalidates static chunks");
        } finally {
            BlockModelRegistry.install(221, null);
            Tessellator.instance = previous;
        }
    }

    private static void verifyDynamic(Globals lua) throws Exception {
        lua.load("calls=0; dynamic={model='test.json',texture='test.png',"
                + "onPose=function(ctx) calls=calls+1; error('expected variant callback error') end}; "
                + "definition.render.variants[1].appearance=dynamic").call();
        BlockDeclaration declaration = new BlockDeclaration(lua.get("definition"));
        require(declaration.callbacks.visual.isDynamic(), "One callback makes the entire block dynamic");
        try (ModelAppearanceSet appearances = new ModelAppearanceSet(declaration.appearance,
                declaration.callbacks.visual.appearances())) {
            BlockModelRegistry.install(221, appearances);
            int[] metadata = {0};
            IBlockAccess world = world(metadata);
            for (int variant = 0; variant < 16; variant++) {
                metadata[0] = variant;
                require(BlockModelRegistry.isDynamic(221), "Render mode must not depend on current state");
                require(!BlockModelRegistry.renderWorld(new RenderBlocks(world), world, 0, 0, 0, Block.blocksList[221]),
                        "Dynamic block must never enter chunk geometry");
            }
            require(lua.get("calls").toint() == 0, "Chunk builds must not invoke variant callbacks");
            appearances.select(1).evaluate("block", 0, 1, 0, 0, 0);
            require(appearances.isDynamic(), "Disabled callbacks must not change the rendering path");
            require(!Block.isBlockContainer[221], "Dynamic rendering must not turn blocks into gameplay tile entities");
        } finally {
            BlockModelRegistry.install(221, null);
        }
        lua.load("definition.appearance=nil; definition.render.variants[1].appearance="
                + "{model='test.json',texture='test.png',layers={{model='test.json',texture='test.png',mode='dynamic'}}}")
                .call();
        require(new BlockVisualDefinition(lua.get("definition")).isDynamic(), "Dynamic layers promote the block type");
        lua.load("definition.render.variants[1].appearance={model='test.json',texture='test.png',"
                + "animation={asset='test.animation.json',clip='wave'}}").call();
        require(new BlockVisualDefinition(lua.get("definition")).isDynamic(), "Animation promotes the block type");
    }

    private static void verifyDeclarationErrors(Globals lua) {
        lua.load("definition.render.variants[1].appearance=true").call();
        reject(() -> new BlockVisualDefinition(lua.get("definition")));
        lua.load("definition.render.variants[1].appearance={texture='test.png'}").call();
        reject(() -> new BlockVisualDefinition(lua.get("definition")));
        lua.load("definition.render.variants[1].appearance=false; definition.render.variants[16]={appearance=base}")
                .call();
        reject(() -> new BlockVisualDefinition(lua.get("definition")));
    }

    private static IBlockAccess world(int[] metadata) {
        return (IBlockAccess) Proxy.newProxyInstance(ModelVariantTest.class.getClassLoader(),
                new Class<?>[]{IBlockAccess.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockMetadata")) {
                        return metadata[0];
                    }
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
    }

    private static final class Capture extends Tessellator {
        final List<double[]> vertices = new ArrayList<>();

        public void addVertexWithUV(double x, double y, double z, double u, double v) {
            vertices.add(new double[]{x, y, z});
        }

        public void setColorOpaque_F(float r, float g, float b) {
        }
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected declaration rejection");
        } catch (LuaError expected) {
        }
    }

    public static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
