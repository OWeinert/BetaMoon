package betamoon.luaapi.block;

import betamoon.client.render.ModelAppearanceSet;
import betamoon.wrappers.BlockWrapper;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkPosition;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderEngine;
import net.minecraft.src.TileEntityChest;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;
import net.minecraft.src.UnexpectedThrowable;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import sun.misc.Unsafe;
import static betamoon.luaapi.block.ModelVariantTest.require;

/**
 * Runs within ModelRenderTest's real GL context; world data must remain
 * untouched.
 */
public final class ModelVariantRenderTest {
    private ModelVariantRenderTest() {
    }

    public static void run(int texture) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        TestWorld world = (TestWorld) unsafe.allocateInstance(TestWorld.class);
        world.chunk = new Chunk(world, new byte[32768], 0, 0);
        world.chunk.isChunkLoaded = true;
        world.chunk.blocks[0] = (byte) 222;
        TileEntityChest inventory = new TileEntityChest();
        inventory.setInventorySlotContents(0, new ItemStack(1, 12, 0));
        world.chunk.chunkTileEntityMap.put(new ChunkPosition(0, 0, 0), inventory);
        byte[] originalBlocks = world.chunk.blocks.clone();
        BlockWrapper block = new BlockWrapper(222, 1, Material.rock, "visual_reload");
        Globals lua = JsePlatform.standardGlobals();
        lua.load("base={model='test.json',texture='test.png'}; "
                + "animated={model='test.json',texture='test.png',onPose=function(ctx) drawn=ctx.state.metadata end}; "
                + "definition={appearance=base,render={variants={[1]={appearance=animated},[2]={appearance=false}}}}")
                .call();
        BlockVisualDefinition dynamic = new BlockVisualDefinition(lua.get("definition"));
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(TestMinecraft.class);
        minecraft.renderEngine = new RenderEngine(null, null) {
            public int getTexture(String path) {
                return texture;
            }
        };
        Field instance = ModLoader.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        instance.set(null, minecraft);
        Field type = BlockModelRegistry.class.getDeclaredField("renderType");
        type.setAccessible(true);
        type.setInt(null, 777);
        try {
            BlockModelRegistry.install(222, new ModelAppearanceSet(dynamic.appearance, dynamic.appearances()));
            BlockCallbackRegistry.install(222, new BlockDefinition(lua.get("definition")));
            BlockModelScene.loaded(world.chunk);
            require(draw(world) > 100, "Static default must draw through the dynamic world renderer");
            world.chunk.data.setNibble(0, 0, 0, 1);
            require(draw(world) > 100 && lua.get("drawn").toint() == 1, "Current metadata selects dynamic variant");
            world.chunk.data.setNibble(0, 0, 0, 2);
            require(draw(world) > 100, "Ordinary fallback still draws dynamically for this block type");

            lua.load("definition.render.variants[1].appearance=base").call();
            BlockVisualDefinition fixed = new BlockVisualDefinition(lua.get("definition"));
            BlockModelRegistry.install(222, new ModelAppearanceSet(fixed.appearance, fixed.appearances()));
            BlockModelScene.rebuildLoaded();
            require(!BlockModelRegistry.isDynamic(222) && draw(world) == 0,
                    "Dynamic-to-static reload removes only dynamic drawing");
            BlockModelRegistry.install(222, new ModelAppearanceSet(dynamic.appearance, dynamic.appearances()));
            BlockModelScene.rebuildLoaded();
            require(draw(world) > 100, "Static-to-dynamic reload restores existing positions without replacing blocks");
            require(Block.blocksList[222] == block && !Block.isBlockContainer[222],
                    "Native block identity and flags survive");
            require(Arrays.equals(originalBlocks, world.chunk.blocks), "Reload must not rewrite block IDs");
            require(world.chunk.getBlockMetadata(0, 0, 0) == 2, "Reload must not rewrite metadata");
            require(world.chunk.chunkTileEntityMap.get(new ChunkPosition(0, 0, 0)) == inventory
                    && inventory.getStackInSlot(0).stackSize == 12, "Existing tile identity and inventory survive");
            require(!world.chunk.isModified, "Visual updates must not dirty saved chunk data");

            BlockModelScene.unloaded(world.chunk);
            require(draw(world) == 0, "Unloaded chunks leave the draw index");
            BlockModelScene.loaded(world.chunk);
            world.chunk.blocks[0] = 0;
            BlockModelScene.changed(world.chunk, 0, 0, 0, true);
            require(draw(world) == 0, "Removed blocks leave the draw index");
            world.chunk.blocks[0] = (byte) 222;
            BlockModelScene.changed(world.chunk, 0, 0, 0, true);
            require(draw(world) > 100, "New block positions enter the draw index");
        } finally {
            BlockModelScene.unloaded(world.chunk);
            BlockModelRegistry.install(222, null);
            instance.set(null, previous);
        }
    }

    private static int draw(TestWorld world) {
        GL11.glDepthMask(true);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        BlockModelScene.render(world, Vec3D.createVector(0.5, 0, 0.5), 0);
        require(GL11.glIsEnabled(GL11.GL_BLEND) && !GL11.glIsEnabled(GL11.GL_CULL_FACE)
                && !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "Block rendering restores GL state");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Block rendering must not produce GL errors");
        ByteBuffer pixels = BufferUtils.createByteBuffer(256 * 256 * 4);
        GL11.glReadPixels(0, 0, 256, 256, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        int count = 0;
        for (int i = 0; i < 256 * 256; i++) {
            if (pixels.get(i * 4) != 0 || pixels.get(i * 4 + 1) != 0 || pixels.get(i * 4 + 2) != 0) {
                count++;
            }
        }
        return count;
    }

    private static final class TestMinecraft extends Minecraft {
        private TestMinecraft() {
            super(null, null, null, 320, 240, false);
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
        }
    }

    private static final class TestWorld extends World {
        Chunk chunk;

        private TestWorld() {
            super((ISaveHandler) null, "unused", 0);
        }

        public int getBlockId(int x, int y, int z) {
            return x == 0 && y == 0 && z == 0 ? chunk.getBlockID(0, 0, 0) : 0;
        }

        public int getBlockMetadata(int x, int y, int z) {
            return chunk.getBlockMetadata(x & 15, y, z & 15);
        }

        public float getLightBrightness(int x, int y, int z) {
            return 1;
        }

        public float getBrightness(int x, int y, int z, int emitted) {
            return 1;
        }

        public long getWorldTime() {
            return 0;
        }
    }
}
