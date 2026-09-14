package betamoon.luaapi;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockCallback;
import betamoon.luaapi.block.BlockFace;
import betamoon.luaapi.block.BlockAttachment;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.block.BlockStateSchema;
import betamoon.luaapi.block.BlockTickRegistry;
import betamoon.luaapi.block.LuaBlockActionContext;
import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.item.ItemCallback;
import betamoon.luaapi.item.InventoryTickMode;
import betamoon.luaapi.item.ProjectileType;
import betamoon.worldgen.BiomeSpawnGroup;
import betamoon.worldgen.BiomeTreeMode;
import betamoon.luaapi.item.ItemBehavior;
import betamoon.luaapi.item.ItemUseHandler;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.utils.LuaCallbackDeclarations;
import betamoon.luaapi.utils.LuaCallbackDispatcher;
import betamoon.luaapi.item.ItemDefinition;
import betamoon.luaapi.item.ItemInteractionRouting;
import betamoon.luaapi.item.LuaItemActionContext;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.wrappers.BlockWrapper;
import betamoon.wrappers.ItemFoodWrapper;
import betamoon.wrappers.ItemWrapper;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.CraftingManager;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.Session;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ItemBlock;
import net.minecraft.src.Item;
import net.minecraft.src.Material;
import net.minecraft.src.World;
import net.minecraft.src.Vec3D;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Exercises the actual wrapper paths with live Lua callbacks and a real
 * in-memory chunk.
 */
public final class BlockItemApiTest {
    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks were not initialized");
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "block_item_api_test.lua");
        Globals lua = JsePlatform.standardGlobals();
        verifyTypedCallbackContracts(lua);
        verifyCallbackExecutionState(lua);
        TestWorld world = new TestWorld();
        EntityPlayer player = new EntityPlayer(world) {
            public void func_6420_o() {
            }
        };
        verifyScopedContexts(lua, world, player);

        LuaValue declaration = lua.load("return {key='switch', state={active={type='boolean',default=false}},"
                + "redstone={strongPower={state='active',sides={'down'}}},"
                + "canPlace={action=function(ctx) return ctx.id==254 and not ctx.state:get('active') end},"
                + "onActivate={action=function(ctx) saved=ctx.state; ctx.state:set('active',not ctx.state:get('active')); return 'handled' end},"
                + "collision={boxes={{min={0,0,0},max={1,0.5,1}}}}," + "selection={min={0,0,0},max={1,0.5,1}},"
                + "getDrops={action=function(ctx) "
                + "assert(not pcall(function() ctx.state:set('active',false) end)); "
                + "return {{item=4,min=ctx.state:get('active') and 2 or 1}} end}}").call();
        int id = 254;
        require(Block.blocksList[id] == null, "Expected unused test block");
        BlockWrapper block = new BlockWrapper(id, 1, Material.rock, "switch");
        BlockDefinition definition = new BlockDefinition(declaration);
        BlockCallbackRegistry.install(id, definition);
        require(!block.renderAsNormalBlock(), "Partial collision must default to a non-normal cube");
        require(new BlockDefinition(lua.load("return {normalCube=true,collision={boxes={}}}").call()).normalCube,
                "Explicit normalCube must override shape inference");
        world.chunk.blocks[64] = (byte) id;
        block.onBlockAdded(world, 0, 64, 0);
        require(!Block.isBlockContainer[id], "Metadata switch should not require a tile entity");
        world.renderUpdates = 0;
        require(block.blockActivated(world, 0, 64, 0, player), "Switch activation was not handled");
        require(world.renderUpdates > 0, "State mutation did not request a world redraw");
        require(definition.state.get(world.getBlockMetadata(0, 64, 0), "active").toboolean(),
                "Switch did not change state");
        require(world.isBlockProvidingPowerTo(0, 64, 0, 1), "Switch must strongly power its lower face");
        require(!world.isBlockProvidingPowerTo(0, 64, 0, 0), "Switch must not power its upper face");
        lua.load("local ok=pcall(function() saved:get('active') end); assert(not ok)").call();
        ArrayList boxes = new ArrayList();
        block.getCollidingBoundingBoxes(world, 0, 64, 0, AxisAlignedBB.getBoundingBoxFromPool(-1, 63, -1, 2, 66, 2),
                boxes);
        require(boxes.size() == 1 && ((AxisAlignedBB) boxes.get(0)).maxY == 64.5, "Half-height collision was not used");
        require(block.collisionRayTrace(world, 0, 64, 0, Vec3D.createVector(-1, 64.75, 0.5),
                Vec3D.createVector(2, 64.75, 0.5)) == null, "Ray hit the empty space above the selection box");
        require(block.collisionRayTrace(world, 0, 64, 0, Vec3D.createVector(-1, 64.25, 0.5),
                Vec3D.createVector(2, 64.25, 0.5)) != null, "Ray missed the declared selection box");
        require(BlockCallbackRegistry.drops(id, world, 0, 64, 0, 1, 1).get(0).stackSize == 2,
                "Drop query did not use the block's named state");
        block.onBlockAdded(world, 0, 64, 0);
        require(world.getBlockMetadata(0, 64, 0) == 1, "Adding a block overwrote supplied metadata");

        BlockStateSchema schema = new BlockStateSchema(
                lua.load("return {facing={type='enum',values={'north','east','south','west'}},active={type='boolean'}}")
                        .call());
        int metadata = schema.set(schema.defaults, "facing", LuaValue.valueOf("west"));
        metadata = schema.set(metadata, "active", LuaValue.TRUE);
        require(schema.get(metadata, "facing").tojstring().equals("west"), "Setting one field overwrote another");

        boolean invalidSupport = false;
        try {
            new BlockDefinition(lua.load("return {placement={requiresSolidSupport=true}}").call());
        } catch (LuaError expected) {
            invalidSupport = true;
        }
        require(invalidSupport, "Wall support without persisted attachment was accepted");

        world.chunk.blocks[64] = 0;
        require(BlockCallbackRegistry.canPlace(id, definition, world, 0, 64, 0, 1),
                "Placement query did not receive the candidate ID and default state");
        LuaBlockActionContext removed = new LuaBlockActionContext(world, 0, 64, 0, player, null, -1, true, id, 1);
        lua.set("removed", removed);
        lua.load("assert(removed.id==254 and removed.state:get('active')); "
                + "assert(not pcall(function() removed.state:set('active',false) end))").call();
        removed.close();
        world.chunk.blocks[64] = (byte) id;

        BlockTickRegistry.register(block,
                lua.load("return {mode='scheduled',action=function(ctx) oldTick=true end}").call(), LuaValue.NIL);
        BlockTickRegistry.schedule(world, 0, 64, 0, id, 5);
        BlockTickRegistry.register(block,
                lua.load("return {mode='scheduled',action=function(ctx) newTick=true; "
                        + "savedTickWorld=ctx.world; savedTickSchedule=ctx.schedule; savedTickRandom=ctx.random end}")
                        .call(),
                LuaValue.NIL);
        world.time = 4;
        block.updateTick(world, 0, 64, 0, world.rand);
        require(lua.get("newTick").isnil(), "Scheduled tick ran too early");
        world.time = 5;
        block.updateTick(world, 0, 64, 0, world.rand);
        require(lua.get("newTick").toboolean() && lua.get("oldTick").isnil(),
                "A pending tick did not use the replacement script definition");
        lua.load("assert(not pcall(function() savedTickWorld:getBlock(0,64,0) end)); "
                + "assert(not pcall(function() savedTickSchedule(1) end)); "
                + "assert(not pcall(function() savedTickRandom() end))").call();

        ItemWrapper item = new ItemWrapper(5100 - 256, "test_item");
        ItemDefinition itemDefinition = new ItemDefinition(lua.load(
                "return {key='test_item',onUseFirst={action=function(ctx) assert(ctx.state==nil); ctx.stack:consume(1); return 'deny' end}}")
                .call());
        ItemCallbackRegistry.install(5100, itemDefinition);
        ItemStack held = new ItemStack(item, 2);
        player.inventory.mainInventory[0] = held;
        ItemInteractionRouting.begin(player, world);
        require(item.onItemUseFirst(held, player, world, 0, 64, 0, 1), "Denied use must stop block activation");
        require(!ItemInteractionRouting.complete(true), "Denied use was reported as success");
        require(ItemInteractionRouting.denyFallback(player, world), "Denied use allowed general-use fallback");
        require(held.stackSize == 1, "Live held-stack change was not applied exactly once");

        ItemFoodWrapper food = new ItemFoodWrapper(5101 - 256, 4, false);
        ItemCallbackRegistry.install(5101, new ItemDefinition(
                lua.load("return {key='food',type='food',use={consume=1,remainder=281,cooldown=20}}").call()));
        ItemStack meal = new ItemStack(food, 1);
        player.health = 10;
        ItemStack bowl = food.onItemRightClick(meal, world, player);
        require(player.health == 14 && bowl.itemID == 281 && bowl.stackSize == 1,
                "Food healing/consumption/remainder changed");
        ItemStack secondMeal = new ItemStack(food, 1);
        food.onItemRightClick(secondMeal, world, player);
        require(secondMeal.stackSize == 1 && player.health == 14, "Cooldown failed");

        ItemWrapper launcher = new ItemWrapper(5102 - 256, "launcher");
        ItemCallbackRegistry.install(5102, new ItemDefinition(
                lua.load("return {key='launcher',use={consume=1,projectile='snowball',ammunition=5102}}").call()));
        ItemStack charges = new ItemStack(launcher, 1);
        player.inventory.mainInventory[0] = charges;
        require(ItemUseHandler.suppressDefaultUse(charges, world, player),
                "A shot accepted insufficient ammunition plus consumption");
        player.inventory.mainInventory[1] = new ItemStack(launcher, 1);
        ItemStack empty = launcher.onItemRightClick(charges, world, player);
        require(empty.stackSize == 0 && player.inventory.mainInventory[1] == null && world.projectiles == 1,
                "Projectile must consume ammunition and the declared charge exactly once");
        verifyExamples(owner, world, player);
        verifyInventoryFacing(lua, world);
        verifyItemBehavior(lua, world, player);
        System.out.println(
                "Block/item API checks passed: state, directional power, live access, shapes, use routing and food.");
    }

    private static void verifyTypedCallbackContracts(Globals lua) {
        require(BlockCallback.fromLuaName("onActivate") == BlockCallback.ACTIVATE,
                "Block callback Lua name did not resolve to its enum identity");
        require(BlockFace.fromNative(2) == BlockFace.NORTH && BlockFace.NORTH.opposite() == BlockFace.SOUTH,
                "Native block-face ordering or opposites changed");
        require(BlockFace.resolve("left", BlockFace.NORTH.nativeSide) == BlockFace.WEST
                && BlockFace.resolve("right", BlockFace.WEST.nativeSide) == BlockFace.NORTH,
                "Relative block-face resolution changed");
        require(BlockAttachment.forFace(BlockFace.UP) == BlockAttachment.FLOOR
                && BlockAttachment.forFace(BlockFace.DOWN) == BlockAttachment.CEILING
                && BlockAttachment.forFace(BlockFace.NORTH) == BlockAttachment.WALL,
                "Block attachment categories changed");
        require(InventoryTickMode.parse("selected") == InventoryTickMode.SELECTED
                && !InventoryTickMode.SELECTED.accepts(false), "Inventory tick selection changed");
        require(ProjectileType.parse("snowball") == ProjectileType.SNOWBALL,
                "Projectile name did not resolve to its enum identity");
        require(BiomeTreeMode.parse(" BIG ") == BiomeTreeMode.BIG
                && BiomeSpawnGroup.parse("animals") == BiomeSpawnGroup.CREATURE,
                "Biome aliases did not resolve to their enum identities");
        require(ItemCallback.fromLuaName("onUseOnBlock") == ItemCallback.USE_ON_BLOCK,
                "Item callback Lua name did not resolve to its enum identity");
        require(BlockCallback.fromLuaName("missing") == null && ItemCallback.fromLuaName("missing") == null,
                "Unknown callback names must stay outside the typed callback set");
        require(InteractionOutcome.fromLua(LuaValue.NIL, "test") == InteractionOutcome.PASS,
                "Nil interaction results must retain pass semantics");
        require(InteractionOutcome.fromLua(LuaValue.valueOf("deny"), "test").toNativeCode() == -1,
                "Denied interaction did not retain its native boundary code");
        require(InteractionOutcome.fromLua(LuaValue.valueOf("handled"), "test").toNativeCode() == 1,
                "Handled interaction did not retain its native boundary code");
        boolean rejected = false;
        try {
            InteractionOutcome.fromLua(lua.load("return {} ").call(), "test");
        } catch (LuaError expected) {
            rejected = true;
        }
        require(rejected, "Invalid interaction results must be rejected");
    }

    private static void verifyScopedContexts(Globals lua, TestWorld world, EntityPlayer player) {
        LuaValue nestedBlock;
        try (LuaItemActionContext context = new LuaItemActionContext(world, 0, 64, 0, player, new ItemStack(Item.stick),
                1, true)) {
            nestedBlock = context.get("target").get("block");
        }
        lua.set("expiredNestedBlock", nestedBlock);
        lua.load("assert(not pcall(function() expiredNestedBlock.state:get('active') end))").call();

        player.motionX = 0.35D;
        player.motionY = -0.2D;
        player.motionZ = -0.45D;
        LuaValue entityAccess;
        try (LuaItemActionContext context = new LuaItemActionContext(world, 0, 64, 0, player, new ItemStack(Item.stick),
                -1, true)) {
            entityAccess = context.get("player");
            lua.set("movingPlayer", entityAccess);
            lua.load("assert(movingPlayer:isPlayer()); local motion=movingPlayer:getVelocity(); "
                    + "movingPlayer:setVelocity(motion.x,0.8032,motion.z)").call();
        }
        require(player.motionX == 0.35D && player.motionY == 0.8032D && player.motionZ == -0.45D,
                "Entity velocity snapshot did not preserve horizontal motion");
        lua.set("expiredEntity", entityAccess);
        lua.load("assert(not pcall(function() expiredEntity:getVelocity() end))").call();

        LuaValue expiredWorld = LuaValue.NIL;
        try {
            try (LuaBlockActionContext context = new LuaBlockActionContext(world, 0, 64, 0, player, null, -1, true)) {
                expiredWorld = context.get("world");
                throw new IllegalStateException("expected test exit");
            }
        } catch (IllegalStateException expected) {
            require("expected test exit".equals(expected.getMessage()), "Unexpected exceptional-scope test failure");
        }
        lua.set("expiredWorld", expiredWorld);
        lua.load("assert(not pcall(function() expiredWorld:getBlock(0,64,0) end))").call();
    }

    private static void verifyCallbackExecutionState(Globals lua) {
        LuaScriptErrors.clear();
        LuaValue definition = lua.load("callbackCalls=0; return {onUse={action=function() "
                + "callbackCalls=callbackCalls+1; error('expected failure') end}}").call();
        LuaCallbackDeclarations.Builder<ItemCallback> builder = LuaCallbackDeclarations.builder(ItemCallback.class,
                "state-test");
        LuaCallbackDeclarations<ItemCallback> declarations = builder.parse(definition, ItemCallback.USE).build();
        LuaCallbackDispatcher<ItemCallback> first = new LuaCallbackDispatcher<ItemCallback>(declarations);
        first.call(ItemCallback.USE, new LuaTable(), LuaValue.NIL);
        first.call(ItemCallback.USE, new LuaTable(), LuaValue.NIL);
        require(lua.get("callbackCalls").checkint() == 1, "A failed callback must be disabled for its dispatcher");
        require(LuaScriptErrors.getIssuesFor("block_item_api_test", "block_item_api_test.lua").size() == 1,
                "Callback failure must retain declaration owner attribution");

        LuaCallbackDispatcher<ItemCallback> reloaded = new LuaCallbackDispatcher<ItemCallback>(declarations);
        reloaded.call(ItemCallback.USE, new LuaTable(), LuaValue.NIL);
        require(lua.get("callbackCalls").checkint() == 2, "A fresh dispatcher must reset callback failure state");
        LuaScriptErrors.clear();
    }

    private static void verifyInventoryFacing(Globals lua, TestWorld world) {
        int id = 253;
        String[] directions = {"north", "east", "south", "west"};
        int[] faces = {2, 5, 3, 4};
        for (String enumValues : new String[]{"'north','east','south','west'", "'west','south','east','north'"}) {
            require(Block.blocksList[id] == null, "Expected unused inventory test block");
            BlockWrapper block = new BlockWrapper(id, 45, Material.rock, "inventory_furnace");
            block.setSideTextureIndex(0, 62);
            block.setSideTextureIndex(1, 62);
            LuaValue declaration = lua.load("return {state={active={type='boolean'}," + "facing={type='enum',values={"
                    + enumValues + "}}}," + "placement={facing='horizontal',facingFrom='player'}}").call();
            BlockStateSchema schema = new BlockStateSchema(declaration.get("state"));
            LuaValue variants = LuaValue.tableOf();
            for (int active = 0; active < 2; active++) {
                for (String direction : directions) {
                    int metadata = schema.set(schema.defaults, "facing", LuaValue.valueOf(direction));
                    metadata = schema.set(metadata, "active", LuaValue.valueOf(active == 1));
                    LuaValue textures = LuaValue.tableOf();
                    textures.set(direction, 44 + active * 16);
                    LuaValue variant = LuaValue.tableOf();
                    variant.set("textures", textures);
                    variants.set(metadata, variant);
                }
            }
            LuaValue render = LuaValue.tableOf();
            render.set("variants", variants);
            declaration.set("render", render);
            BlockCallbackRegistry.install(id, new BlockDefinition(declaration));
            world.chunk.blocks[64] = (byte) id;
            for (int active = 0; active < 2; active++) {
                for (int direction = 0; direction < directions.length; direction++) {
                    int metadata = schema.set(schema.defaults, "facing", LuaValue.valueOf(directions[direction]));
                    metadata = schema.set(metadata, "active", LuaValue.valueOf(active == 1));
                    world.setBlockMetadataWithNotify(0, 64, 0, metadata);
                    for (int side = 0; side < 6; side++) {
                        int base = side < 2 ? 62 : 45;
                        require(block.getBlockTextureFromSideAndMetadata(side,
                                metadata) == (side == 3 ? 44 + active * 16 : base),
                                "Inventory facing lost the front, other state, or base textures");
                        require(block.getBlockTexture(world, 0, 64, 0,
                                side) == (side == faces[direction] ? 44 + active * 16 : base),
                                "Inventory facing changed world textures");
                        require(block.getBlockTextureFromSide(side) == Block.stoneOvenIdle
                                .getBlockTextureFromSide(side),
                                "Default item textures differ from the vanilla furnace");
                    }
                    require(world.getBlockMetadata(0, 64, 0) == metadata, "Rendering mutated placed block state");
                }
            }
            declaration.set("placement", LuaValue.NIL);
            BlockCallbackRegistry.install(id, new BlockDefinition(declaration));
            int north = schema.set(schema.defaults, "facing", LuaValue.valueOf("north"));
            require(block.getBlockTextureFromSideAndMetadata(2, north) == 44
                    && block.getBlockTextureFromSideAndMetadata(3, north) == 45,
                    "A block without horizontal placement had its item textures reoriented");
            id--;
        }
    }

    private static void verifyItemBehavior(Globals lua, TestWorld world, EntityPlayer player) {
        int id = 31000;
        ItemWrapper item = new ItemWrapper(id - 256, "quality_behavior");
        item.setMaxDamageValue(100);
        for (String result : new String[]{"pass", "handled", "deny"}) {
            LuaValue declaration = lua
                    .load("return {key='quality_behavior', "
                            + "onUse={action=function() useCalls=(useCalls or 0)+1; return '" + result + "' end}}")
                    .call();
            ItemCallbackRegistry.install(id, new ItemDefinition(declaration));
            ItemStack stack = new ItemStack(item);
            int[] baseCalls = {0};
            int previousCalls = lua.get("useCalls").optint(0);
            ItemStack returned = ItemBehavior.use(stack, world, player, () -> {
                require(lua.get("useCalls").toint() == previousCalls + 1,
                        "Use callback must run before the superclass action");
                baseCalls[0]++;
                return stack;
            });
            require(returned == stack && baseCalls[0] == (result.equals("pass") ? 1 : 0),
                    "Superclass use must run exactly once for pass, and never for handled or deny");
        }

        LuaValue declaration = lua.load("return {key='quality_behavior', tool={durabilityCost={hit=2,mine=3}}, "
                + "onHitEntity={action=function() hitCalls=(hitCalls or 0)+1 end}, "
                + "onBlockDestroyed={action=function() mineCalls=(mineCalls or 0)+1 end}}").call();
        ItemCallbackRegistry.install(id, new ItemDefinition(declaration));
        ItemStack stack = new ItemStack(item);
        require(ItemBehavior.hit(id, stack, player, player, () -> {
            throw new AssertionError("Explicit hit durability must suppress superclass damage");
        }), "Explicit hit durability should report handled");
        require(stack.getItemDamage() == 2 && lua.get("hitCalls").toint() == 1,
                "Hit policy must apply durability and dispatch its callback once");
        require(ItemBehavior.destroyedBlock(id, stack, 0, 64, 0, player, () -> {
            throw new AssertionError("Explicit mine durability must suppress superclass damage");
        }), "Explicit mine durability should report handled");
        require(stack.getItemDamage() == 5 && lua.get("mineCalls").toint() == 1,
                "Mining policy must apply durability and dispatch its callback once");
    }

    private static void verifyExamples(Method owner, TestWorld world, EntityPlayer player) throws Exception {
        Globals examples = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, examples);
        final LuaValue blocks = examples.get("betamoon").get("blocks");
        final LuaValue items = examples.get("betamoon").get("items");

        // Substitute client registration only. Parse real declarations, install real
        // wrappers/callbacks and register recipes through the actual public API.
        blocks.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                LuaValue declaration = arguments.arg(arguments.arg1() == blocks ? 2 : 1);
                int id = declaration.get("id").checkint();
                BlockDefinition definition = new BlockDefinition(declaration);
                Material material = declaration.get("material").optjstring("").equals("glass")
                        ? Material.glass
                        : Material.rock;
                BlockWrapper block = new BlockWrapper(id, 1, material, declaration.get("key").checkjstring());
                new ItemBlock(id - 256);
                BlockCallbackRegistry.install(id, definition);
                BlockTickRegistry.register(block, declaration.get("onTick"), declaration.get("onDisplayTick"));
                return blocks.get("getRequired").call(blocks, LuaValue.valueOf(id));
            }
        });
        items.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                LuaValue declaration = arguments.arg(arguments.arg1() == items ? 2 : 1);
                int id = declaration.get("id").checkint();
                ItemWrapper item = new ItemWrapper(id - 256, declaration.get("key").checkjstring());
                item.setMaxDamageValue(declaration.get("maxDamage").optint(0));
                ItemCallbackRegistry.install(id, new ItemDefinition(declaration));
                return items.get("getRequired").call(items, LuaValue.valueOf(id));
            }
        });
        int recipesBefore = CraftingManager.getInstance().getRecipeList().size();
        String[] files = {"01_beg_16_projectile.lua", "01_beg_22_first_block_interaction.lua",
                "01_beg_23_first_item_use.lua", "02_int_08_block_interactions.lua", "02_int_09_attached_blocks.lua",
                "02_int_10_block_lifecycle.lua", "02_int_11_item_interactions.lua",
                "02_int_12_targeted_item_actions.lua", "02_int_13_tool_interactions.lua",
                "02_int_14_dynamic_tool_callbacks.lua", "02_int_15_block_shapes.lua", "02_int_16_launch_pad.lua",
                "02_int_17_special_blocks.lua", "02_int_18_random_and_continuous_ticks.lua",
                "02_int_20_redstone_switch.lua", "03_adv_01_redstone_timer.lua"};
        for (String file : files) {
            owner.invoke(null, file);
            FileReader reader = new FileReader(new File("examples", file));
            try {
                examples.load(reader, file).call();
            } finally {
                reader.close();
            }
            examples.get("modInit").call();
        }
        require(CraftingManager.getInstance().getRecipeList().size() == recipesBefore + 16,
                "Customization examples did not register their sixteen crafting recipes");
        verifyExampleRendering(examples, world);

        world.chunk.blocks[64] = (byte) 213;
        world.setBlockMetadataWithNotify(0, 64, 0, 0);
        world.time = 100;
        BlockCallbackRegistry.neighbor(world, 0, 64, 0, 213, 1);
        world.inputMask = 1 << 2;
        BlockCallbackRegistry.neighbor(world, 0, 64, 0, 213, 1);
        require(BlockCallbackRegistry.get(213).state.get(world.getBlockMetadata(0, 64, 0), "powered").toboolean(),
                "Example timer did not react to a rising north input");
        world.time = 119;
        Block.blocksList[213].updateTick(world, 0, 64, 0, world.rand);
        require(world.getBlockMetadata(0, 64, 0) == 1, "Example pulse ended early");
        world.time = 120;
        Block.blocksList[213].updateTick(world, 0, 64, 0, world.rand);
        require(world.getBlockMetadata(0, 64, 0) == 0, "Example pulse did not end after 20 ticks");
        world.inputMask = 0;

        world.chunk.blocks[64] = (byte) 211;
        world.setBlockMetadataWithNotify(0, 64, 0, 0);
        ItemStack wrench = new ItemStack(Item.itemsList[5021], 1);
        player.inventory.mainInventory[0] = wrench;
        world.renderUpdates = 0;
        require(Item.itemsList[5021].onItemUse(wrench, player, world, 0, 64, 0, 1) == false,
                "The wrench unexpectedly intercepted the later item-use path");
        require(((ItemWrapper) Item.itemsList[5021]).onItemUseFirst(wrench, player, world, 0, 64, 0, 1),
                "Example wrench did not handle an oriented block");
        require(world.getBlockMetadata(0, 64, 0) == 1 && wrench.getItemDamage() == 1,
                "Example wrench did not rotate and spend exactly one durability");
        require(world.renderUpdates > 0, "Wrench rotation did not request a world redraw");
        Block practice = Block.blocksList[211];
        require(practice.getBlockTexture(world, 0, 64, 0, 5) == 61
                && practice.getBlockTexture(world, 0, 64, 0, 2) != 61,
                "Wrench rotation did not move the front texture from north to east");
        for (int quarterTurn = 0; quarterTurn < 4; quarterTurn++) {
            player.rotationYaw = quarterTurn * 90;
            practice.onBlockPlaced(world, 0, 64, 0, 1);
            world.renderUpdates = 0;
            practice.onBlockPlacedBy(world, 0, 64, 0, player);
            require(world.getBlockMetadata(0, 64, 0) == quarterTurn && world.renderUpdates > 0,
                    "Player placement did not update facing and rendering for yaw " + player.rotationYaw);
        }
        verifyLaunchPad(world, player);
        verifyPartialBlockSolidity(world);
        System.out.println(
                "Loaded sixteen customization examples; verified declarations, recipes, timer, wrench, launch and solidity.");
    }

    private static void verifyExampleRendering(Globals lua, TestWorld world) {
        for (int id : new int[]{214, 215}) {
            Block block = Block.blocksList[id];
            block.setBlockBoundsForItemRender();
            double inset = id == 215 ? 0.375 : 0;
            double height = id == 214 ? 0.25 : 1;
            require(block.minX == inset && block.minZ == inset && block.minY == 0 && block.maxX == 1 - inset
                    && block.maxZ == 1 - inset && block.maxY == height,
                    "Inventory bounds require world placement for example block " + id);
            block.setBlockBoundsBasedOnState(world, 0, 64, 0);
            block.setBlockBoundsForItemRender();
            require(block.minX == inset && block.maxY == height,
                    "World rendering changed inventory bounds for example block " + id);
        }

        Block glass = Block.blocksList[220];
        BlockDefinition original = BlockCallbackRegistry.get(220);
        try {
            for (int neighbor : new int[]{0, 1, 20, 220}) {
                world.chunk.blocks[64] = (byte) neighbor;
                for (int side = 0; side < 6; side++) {
                    require(glass.shouldSideBeRendered(world, 0, 64, 0, side) == (neighbor == 0 || neighbor == 20),
                            "Incorrect glass face culling for neighbor " + neighbor + " side " + side);
                }
            }
            BlockCallbackRegistry.install(220, new BlockDefinition(
                    lua.load("return {opaque=false,render={bounds={min={0.25,0,0.25},max={0.75,1,0.75}}}}").call()));
            glass.setBlockBoundsBasedOnState(world, 0, 64, 0);
            require(glass.shouldSideBeRendered(world, 0, 64, 0, 2),
                    "Partial glass lost an exposed inset face beside the same block");
        } finally {
            BlockCallbackRegistry.install(220, original);
            glass.setBlockBoundsForItemRender();
            world.chunk.blocks[64] = 0;
        }
        require(glass.minX == 0 && glass.maxX == 1 && glass.maxY == 1,
                "Inventory rendering retained bounds from an older declaration");
    }

    private static void verifyLaunchPad(TestWorld world, EntityPlayer player) {
        world.chunk.blocks[64] = (byte) 214;
        player.motionX = 0.35D;
        player.motionY = -0.6D;
        player.motionZ = -0.45D;

        Block.blocksList[214].onEntityWalking(world, 0, 64, 0, player);

        require(player.motionX == 0.35D && player.motionZ == -0.45D,
                "Example launch pad changed horizontal player motion");
        require(Math.abs(player.motionY - 0.8032D) < 0.0000001D,
                "Example launch pad did not apply the four-block launch velocity");
    }

    private static void verifyPartialBlockSolidity(TestWorld world) {
        TestPlayer player = new TestPlayer(world);
        player.setPosition(0.5, 64.5 - player.getEyeHeight(), 0.5);
        for (int id : new int[]{211, 214, 215}) {
            world.chunk.blocks[64] = (byte) id;
            boolean fullCube = id == 211;
            require(world.isBlockNormalCube(0, 64, 0) == fullCube,
                    "Incorrect normal-cube classification for example block " + id);
            require(player.isEntityInsideOpaqueBlock() == fullCube,
                    "Incorrect suffocation check for example block " + id);
            player.motionX = 0;
            player.motionZ = 0;
            player.checkPushOut();
            require((player.motionX != 0 || player.motionZ != 0) == fullCube,
                    "Incorrect player push-out for example block " + id);

            ArrayList boxes = new ArrayList();
            Block.blocksList[id].getCollidingBoundingBoxes(world, 0, 64, 0,
                    AxisAlignedBB.getBoundingBoxFromPool(-1, 63, -1, 2, 66, 2), boxes);
            require(boxes.size() == 1, "Physical collision was removed for example block " + id);
            AxisAlignedBB box = (AxisAlignedBB) boxes.get(0);
            if (id == 214) {
                require(box.maxY == 64.25, "Bounce pad lost its quarter-height collision");
            } else if (id == 215) {
                require(box.minX == 0.375 && box.maxX == 0.625, "Climbing post lost its narrow collision");
            }
        }
    }

    private static final class TestPlayer extends EntityPlayerSP {
        private TestPlayer(World world) {
            super(null, world, new Session("test", ""), 0);
        }

        private void checkPushOut() {
            pushOutOfBlocks(0.1, 64.5, 0.5);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private final Chunk chunk;
        private long time;
        private int projectiles;
        private int inputMask;
        private int renderUpdates;

        private TestWorld() {
            super(null, "block_item_api_test", new WorldProvider() {
            }, 0L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
        }

        protected IChunkProvider getChunkProvider() {
            return null;
        }

        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }

        public long getWorldTime() {
            return time;
        }

        public void scheduleBlockUpdate(int x, int y, int z, int id, int delay) {
            // Tests deliver the scheduled update at an explicitly controlled world time.
        }

        public boolean isBlockIndirectlyProvidingPowerTo(int x, int y, int z, int side) {
            return (inputMask & (1 << side)) != 0;
        }

        public boolean entityJoinedWorld(Entity entity) {
            projectiles++;
            return true;
        }

        public void markBlockNeedsUpdate(int x, int y, int z) {
            renderUpdates++;
        }

        public void notifyBlocksOfNeighborChange(int x, int y, int z, int id) {
            // Other chunk coordinates alias this test chunk, so do not propagate
            // recursively.
        }
    }
}
