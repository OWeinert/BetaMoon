package betamoon.luaapi.block;

import betamoon.assets.AssetPath;
import betamoon.assets.io.AssetProvider;
import betamoon.client.assets.AtlasTextures;
import betamoon.client.assets.ClientAssets;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luaapi.item.ItemApi;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.query.QueryEntries;
import betamoon.resources.EnumTexAtlas;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Session;
import net.minecraft.src.StatCollector;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Native registration, inventory, placement, rendering, drops and reload
 * contracts.
 */
public final class MetadataVariantsTest {
    private static int allocations;

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Native blocks must initialize first");
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "metadata_variants_test.lua");
        Field blockList = ModLoader.class.getDeclaredField("field_blockList");
        blockList.setAccessible(true);
        Object previous = blockList.get(null);
        blockList.set(null, Session.class.getDeclaredField("registeredBlocksList"));
        try {
            textures();
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            verifyItems(lua);
            verifyBlocks(lua);
            verifyValidation(lua);
            System.out.println("Metadata variants passed: native names, queries, assets, inventory, placement, "
                    + "opt-in drops, state masks and retained reload identities.");
        } finally {
            blockList.set(null, previous);
            owner.invoke(null, new Object[]{null});
        }
    }

    private static void textures() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", png);
        ClientAssets.useProviders(new AssetProvider() {
            public boolean exists(AssetPath path) {
                return !path.toString().equals("missing.png");
            }

            public String getName() {
                return "metadata fixture";
            }

            public byte[] read(AssetPath path, int limit) {
                return exists(path) ? png.toByteArray() : null;
            }
        }, null, message -> {
        });
        AtlasTextures.useBackend(new AtlasTextures.Backend() {
            public int allocate(EnumTexAtlas atlas) {
                return 100 + allocations++;
            }

            public boolean isReady() {
                return false;
            }

            public boolean isAnaglyph() {
                return false;
            }

            public void upload(EnumTexAtlas atlas, int index, byte[] pixels) {
            }
        });
    }

    private static void verifyItems(Globals lua) {
        lua.load("types={'item','food','pickaxe','axe','shovel','hoe','sword','armor'}; "
                + "for i,kind in ipairs(types) do "
                + "local def={id=29000+i,key='variants_'..kind,type=kind,displayName='Fallback',"
                + "displayNames={[1]='Variant One',[32767]='Last Variant'},"
                + "render={variants={[1]={texture='one.png',color=0x123456},[32767]={icon=7}}}}; "
                + "if kind=='item' or kind=='food' then def.hasSubtypes=true else def.material='iron' end; "
                + "if kind=='food' then def.food={healing=2} end; "
                + "if kind=='armor' then def.slot='head' end; betamoon.items:add(def); end").call();
        for (int id = 29001; id <= 29008; id++) {
            Item item = Item.itemsList[id];
            require(name(item, 1).equals("Variant One") && name(item, 2).equals("Fallback")
                    && name(item, 32767).equals("Last Variant"), "Names must work for every native item kind");
            require(item.getIconFromDamage(1) == 100 && item.getIconFromDamage(32767) == 7
                    && item.getColorFromDamage(1) == 0x123456, "Variant assets and numeric icons must coexist");
        }
        require(allocations == 1, "Shared texture assets must reuse an atlas slot");
        lua.load("local v=betamoon.items:getRequired(29001,32767); "
                + "assert(v.damage==32767 and v.displayName=='Last Variant'); "
                + "assert(betamoon.items:getRequired(v).damage==32767); "
                + "assert(betamoon.items:getRequired('variants_item',1).displayName=='Variant One'); "
                + "assert(not pcall(function() betamoon.items:get(29001,0.5) end)); "
                + "assert(not pcall(function() betamoon.blocks:get(betamoon.items:get(35,16)) end)); "
                + "local v=betamoon.blocks:getRequired(35,14); assert(v.damage==14); "
                + "assert(v.displayName~=betamoon.blocks:getRequired(35,0).displayName)").call();
        require(QueryEntries.findById(QueryEntries.buildItemEntries(), 29001, 32767) != null,
                "Queries must include declared item metadata above 15");
        Item retained = Item.itemsList[29001];
        ItemApi.add(lua.load("return {id=29001,key='variants_item',hasSubtypes=true,displayName='New fallback',"
                + "displayNames={[1]='New variant'}}").call());
        require(Item.itemsList[29001] == retained && name(retained, 1).equals("New variant")
                && name(retained, 32767).equals("New fallback"), "Reload must replace labels without stale variants");
    }

    private static void verifyBlocks(Globals lua) {
        LuaValue plain = lua
                .load("return {id=240,key='variants_plain',material='rock',texture=1,subtypes=true,"
                        + "displayName='Plain',displayNames={[1]='Red'},render={variants={"
                        + "[1]={texture='block.png',textures={up=7},color=0xAA1122},[2]={texture='block.png'}}}}")
                .call();
        LuaBlock block = (LuaBlock) BlockApi.add(plain);
        Item item = Item.itemsList[240];
        require(item instanceof LuaBlockItem && item.getHasSubtypes(), "Registration must use the native subtype item");
        require(name(item, 1).equals("Red") && name(item, 3).equals("Plain"), "Block item labels must select metadata");
        require(item.getIconFromDamage(1) == 101 && block.getBlockTextureFromSideAndMetadata(1, 1) == 7
                && item.getColorFromDamage(1) == 0xAA1122, "Block variants must share texture and tint selection");
        require(allocations == 2, "Block variant faces must deduplicate shared assets");
        TestWorld world = new TestWorld();
        EntityPlayer player = new EntityPlayer(world) {
            public void func_6420_o() {
            }
        };
        player.setPosition(8, 70, 8);
        place(world, player, item, 1);
        require(world.getBlockMetadata(0, 64, 0) == 1, "Placement must preserve explicit inventory subtypes");
        drop(world, block, 1, 0);
        plain.set("preserveDropMetadata", LuaValue.TRUE);
        require(BlockApi.add(plain) == block && Item.itemsList[240] == item, "Reload must retain both identities");
        require(world.getBlockMetadata(0, 64, 0) == 1, "Visual/drop reload must preserve placed metadata");
        drop(world, block, 1, 1);
        place(world, player, item, world.dropped.get(0).getItemDamage());
        require(world.getBlockMetadata(0, 64, 0) == 1, "Preserved drops must place the same variant");
        player.inventory.addItemStackToInventory(new ItemStack(item, 2, 1));
        player.inventory.addItemStackToInventory(new ItemStack(item, 3, 2));
        player.inventory.addItemStackToInventory(new ItemStack(item, 4, 1));
        require(player.inventory.mainInventory[0].stackSize == 6
                && player.inventory.mainInventory[1].getItemDamage() == 2,
                "Native inventory must merge equal subtypes and separate different ones");
        ItemStack invalid = new ItemStack(item, 1, 16);
        require(!item.onItemUse(invalid, player, world, 0, 63, 0, 1) && invalid.stackSize == 1,
                "Invalid stack metadata must not be truncated or consumed");

        LuaValue stateful = lua.load("return {id=241,key='variants_state',material='rock',texture=1,"
                + "state={color={type='enum',values={'red','green','blue'}},"
                + "facing={type='enum',values={'north','east','south','west'}}},"
                + "placement={facing='horizontal',facingFrom='player'},subtypes={fields={'color'}},"
                + "render={variantBy='subtype',variants={[1]={texture=9,color=0x112233}}}}").call();
        LuaBlock facing = (LuaBlock) BlockApi.add(stateful);
        Item facingItem = Item.itemsList[241];
        player.rotationYaw = 90;
        place(world, player, facingItem, 1);
        int metadata = world.getBlockMetadata(0, 64, 0);
        require((metadata & 3) == 1 && metadata != 1, "Facing placement must retain color and initialize direction");
        require(facing.getBlockTextureFromSideAndMetadata(0, metadata) == 9
                && facing.getRenderColor(metadata) == 0x112233, "State bits must not change subtype visuals");
        drop(world, facing, metadata, 0);
        stateful.set("preserveDropMetadata", LuaValue.TRUE);
        BlockApi.add(stateful);
        drop(world, facing, metadata, 1);
        drop(world, facing, 15, 0);
        ItemStack invalidEnum = new ItemStack(facingItem, 1, 3);
        require(!facingItem.onItemUse(invalidEnum, player, world, 0, 63, 0, 1) && invalidEnum.stackSize == 1,
                "Unused enum encodings must refuse placement");
        stateful.set("drops", lua.load("return {{item=263,damage=1}}").call());
        BlockApi.add(stateful);
        world.dropped.clear();
        facing.dropBlockAsItemWithChance(world, 0, 64, 0, metadata, 1);
        require(world.dropped.size() == 1 && world.dropped.get(0).itemID == 263
                && world.dropped.get(0).getItemDamage() == 1, "Explicit drops must retain precedence");
        stateful.set("drops", lua.load("return {}").call());
        BlockApi.add(stateful);
        world.dropped.clear();
        facing.dropBlockAsItemWithChance(world, 0, 64, 0, metadata, 1);
        require(world.dropped.isEmpty(), "Empty explicit drops must still suppress all drops");
        plain.set("subtypes", lua.load("return {mask=3}").call());
        try {
            BlockApi.add(plain);
            throw new AssertionError("Changing subtype identity on reload must be rejected");
        } catch (LuaError expected) {
            require(expected.getMessage().contains("restart"), "Schema rejection must explain restart requirement");
        }
        require(Block.blocksList[240] == block && item.getHasSubtypes(), "Rejected reload must retain active identity");
        require(QueryEntries.findById(QueryEntries.buildBlockEntries(), 240, 15) != null,
                "Declared block subtypes need not have distinct labels to be discoverable");
        int before = allocations;
        for (int repeat = 0; repeat < 100; repeat++) {
            item.getIconFromDamage(1);
            name(item, 1);
        }
        require(allocations == before, "Native render/name queries must not allocate textures");
    }

    private static void verifyValidation(Globals lua) {
        for (String fields : new String[]{"displayNames={[-1]='bad'}", "displayNames={[0.5]='bad'}",
                "displayNames={['1']='bad'}", "displayNames={[16]='bad'}", "displayNames={[1]=' '}",
                "preserveDropMetadata=true", "subtypes={mask=16}", "subtypes={mask=0}", "subtypes={fields={'missing'}}",
                "subtypes={fields={'color','color'}}," + "state={color={type='boolean'}}",
                "state={color={type='enum',values={'a','b','c'}}},subtypes={mask=1}",
                "state={facing={type='enum',values={'north','east','south','west'}}},"
                        + "placement={facing='horizontal'},subtypes={fields={'facing'}}",
                "render={variantBy='subtype'}", "subtypes={mask=3},displayNames={[4]='bad'}",
                "subtypes={mask=3},render={variantBy='subtype',variants={[4]={texture=1}}}"}) {
            try {
                new BlockDefinition(lua.load("return {" + fields + "}").call());
                throw new AssertionError("Invalid declaration accepted: " + fields);
            } catch (LuaError expected) {
                // All failures occur during parsing, before touching native content.
            }
        }
        BlockDefinition state = new BlockDefinition(lua
                .load("return {" + "state={color={type='boolean',default=true},powered={type='boolean',default=true}},"
                        + "subtypes={fields={'color'}}}")
                .call());
        require(state.subtypes.placedMetadata(0) == 2 && state.subtypes.variant(3) == 1,
                "Non-subtype state must use defaults without leaking into drops");
        Block fallback = BlockApi.add(lua.load(
                "return {id=242,key='bad_asset',material='rock'," + "render={variants={[1]={texture='missing.png'}}}}")
                .call());
        require(fallback.getBlockTextureFromSideAndMetadata(0, 1) == 253,
                "Missing variant textures must retain the ordinary texture API's fallback");
    }

    private static void place(TestWorld world, EntityPlayer player, Item item, int metadata) {
        world.chunk.blocks[63] = (byte) Block.stone.blockID;
        world.chunk.blocks[64] = 0;
        ItemStack stack = new ItemStack(item, 1, metadata);
        require(item.onItemUse(stack, player, world, 0, 63, 0, 1) && stack.stackSize == 0,
                "Native block item placement failed");
    }

    private static void drop(TestWorld world, LuaBlock block, int metadata, int expected) {
        world.dropped.clear();
        block.dropBlockAsItemWithChance(world, 0, 64, 0, metadata, 1);
        require(world.dropped.size() == 1 && world.dropped.get(0).getItemDamage() == expected,
                "Unexpected default/self-drop metadata: " + metadata + " -> " + expected);
    }

    private static String name(Item item, int metadata) {
        return StatCollector.translateToLocal(item.getItemNameIS(new ItemStack(item, 1, metadata)) + ".name");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private final Chunk chunk;
        private final List<ItemStack> dropped = new ArrayList<ItemStack>();

        private TestWorld() {
            super(null, "metadata_test", new WorldProvider() {
            }, 0L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
        }

        protected IChunkProvider getChunkProvider() {
            return (IChunkProvider) Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),
                    new Class<?>[]{IChunkProvider.class}, (proxy, method, arguments) -> {
                        if (method.getReturnType() == Chunk.class) {
                            return chunk;
                        }
                        if (method.getReturnType() == boolean.class) {
                            return true;
                        }
                        return method.getReturnType() == int.class ? 0 : null;
                    });
        }

        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }

        public boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2) {
            return true;
        }

        public void scheduleBlockUpdate(int x, int y, int z, int id, int delay) {
        }

        public void notifyBlocksOfNeighborChange(int x, int y, int z, int id) {
        }

        public boolean entityJoinedWorld(Entity entity) {
            if (entity instanceof EntityItem) {
                dropped.add(((EntityItem) entity).item);
            }
            return true;
        }
    }
}
