package betamoon.entity.modifier;

import betamoon.content.ContentKey;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.EntityTypeRegistry;
import betamoon.entity.LuaLivingEntity;
import betamoon.entity.motion.EntitySimulationThread;
import betamoon.entity.motion.NativeEntityFields;
import betamoon.entity.motion.NativeLivingFields;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.NativeFieldAccess;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPig;
import net.minecraft.src.EntitySquid;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.Material;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real transformed native bodies driven through the public Lua modifier facade.
 */
public final class EntityMotionApiTest {
    private static final String OWNER = "motion-test.lua";

    private EntityMotionApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (Boolean.getBoolean("betamoon.motion.requireObfuscated")) {
            require(!Entity.class.getName().startsWith("net.minecraft."), "Packaged test inherited named classes");
            try {
                Class.forName("net.minecraft.src.Entity");
                throw new AssertionError("Packaged runtime contains named game classes");
            } catch (ClassNotFoundException expected) {
                // The child JVM must exercise the shipped mappings and native hooks.
            }
        }
        EntitySimulationThread.initialize();
        nativeRules();
        mediumRules();
        manualMotion();
        arbitrationAndForces();
        faultsAndAtomicUpdates();
        committedContactEdits();
        nativeParity();
        collisionsAndFall();
        lifecycleAndAuthority();
        nativeLivingAndLimits();
        EntityPlayerFlightTest.verify();
        System.out.println("Entity movement API passed: native parity, rules, arbitration, forces, faults, collision, "
                + "fall handling, ownership, lifetimes and Lua-authored player flight.");
    }

    private static void nativeRules() {
        FixtureWorld world = new FixtureWorld();
        EntityPig body = body(world, 8, 80, 8);
        Globals lua = lua(body);
        execute(lua, "source=entity:modify{key='test:modifier/gravity',physics={gravityMultiplier=0}}; "
                + "assert(entity:supportsModification('onMotion'))");
        body.onLivingUpdate();
        near(body.motionY, 0);
        execute(lua, "assert(source:getStatus().applied); source:setEnabled(false)");
        body.onLivingUpdate();
        near(body.motionY, -0.08 * (double) 0.98F);
        execute(lua, "source:setEnabled(true); source:update{physics={gravityMultiplier=0.5}};");
        body.motionY = 0;
        body.onLivingUpdate();
        near(body.motionY, -0.04 * (double) 0.98F);
        execute(lua, "source:clear('physics.gravityMultiplier'); assert(source:isValid())");
        body.motionY = 0;
        body.onLivingUpdate();
        near(body.motionY, -0.08 * (double) 0.98F);
        execute(lua, "source:close(); source:close(); assert(not source:isValid())");
        require(!EntityModifierRuntime.hasSources(body), "Closed source retained its entity binding");
        ScriptResourceTracker.unload(OWNER);
    }

    private static void mediumRules() throws Exception {
        FixtureWorld world = new FixtureWorld();
        EntityPig body = body(world, 8, 80, 8);
        Globals lua = lua(body);
        execute(lua, "source=entity:modify{key='test:modifier/medium',physics={airDrag={horizontal=0,vertical=1}}}");
        body.motionX = 1;
        body.motionY = 1;
        body.onLivingUpdate();
        near(body.motionX, 0);
        near(body.motionY, 0.92);
        world.lava = true;
        execute(lua, "source:update{physics={gravityMultiplier=0,fluidDrag={horizontal=0.25,vertical=0.5}}}");
        body.motionX = body.motionY = 1;
        body.onLivingUpdate();
        near(body.motionX, 0.25);
        near(body.motionY, 0.5);
        Field jumping = NativeFieldAccess.field(EntityLiving.class,
                new FieldRef(new ClassRef("net/minecraft/src/EntityLiving"), "isJumping", "Z"));
        jumping.setBoolean(body, true);
        execute(lua, "source:update{physics={fluidDrag={vertical=1}},movement={swimLiftEnabled=false}}");
        body.motionY = 0;
        body.onLivingUpdate();
        near(body.motionY, 0);
        execute(lua, "source:update{movement={swimLiftEnabled=true}}");
        body.onLivingUpdate();
        near(body.motionY, (double) 0.04F);
        ScriptResourceTracker.unload(OWNER);
    }

    private static void manualMotion() {
        FixtureWorld world = new FixtureWorld();
        EntityPig body = body(world, 8, 80, 8);
        Globals lua = lua(body);
        execute(lua,
                "calls=0; observations=0; source=entity:modify{key='test:modifier/manual', "
                        + "onMotion=function(ctx) calls=calls+1; return {mode='manual',velocity={x=0.25,y=0,z=0},"
                        + "rules={physics={trackFallDistance=false},damage={fallMultiplier=0}}} end,"
                        + "onAfterMotion=function(ctx,result) observations=observations+1; last=result; "
                        + "assert(ctx.ownsMotion and result.mode=='manual'); assert(not pcall(function() "
                        + "ctx.entity:setVelocity(0,0,0) end)) end}");
        for (int i = 0; i < 8; i++) {
            body.onLivingUpdate();
        }
        near(body.posX, 10);
        near(body.posY, 80);
        near(body.motionY, 0);
        require(lua.get("calls").toint() == 8 && lua.get("observations").toint() == 8,
                "Native motion must run producers and observations exactly once per step");
        require(!lua.get("last").get("support").get("supported").toboolean(), "Hover falsely reported support");
        NativeEntityFields.fallDistance(body, 30);
        body.onLivingUpdate();
        near(NativeEntityFields.fallDistance(body), 0);
        execute(lua, "source:close()");
        body.onLivingUpdate();
        require(body.motionY < 0, "Removing manual motion did not restore native gravity");
        ScriptResourceTracker.unload(OWNER);
    }

    private static EntityPig body(FixtureWorld world, double x, double y, double z) {
        EntityPig result = new EntityPig(world);
        result.isMultiplayerEntity = true;
        result.health = 20;
        result.setPosition(x, y, z);
        return result;
    }

    private static void arbitrationAndForces() {
        EntityPig body = body(new FixtureWorld(), 8, 80, 8);
        Globals lua = lua(body);
        execute(lua,
                "order=''; a=entity:modify{key='test:modifier/z',priority=10,"
                        + "physics={gravityMultiplier=0.5},onMotion=function(ctx) order=order..'z'; "
                        + "return {mode='manual',velocity={x=1,y=0,z=0}} end}; "
                        + "b=entity:modify{key='test:modifier/a',priority=10,onMotion=function(ctx) order=order..'a'; "
                        + "return {mode='manual',velocity={x=0.25,y=0,z=0},rotation={yaw=45},fallDistance=2,"
                        + "rules={physics={gravityMultiplier=0}}} end}; "
                        + "c=entity:modify{key='test:modifier/f',onForces=function(ctx) "
                        + "assert(ctx.movement.velocity.x==0.25 and ctx.movement.rotation.yaw==45 "
                        + "and ctx.movement.fallDistance==2); return {velocityDelta={x=0.125,y=0,z=0}} end,"
                        + "onAfterMotion=function(ctx,result) observed=result; "
                        + "assert(not ctx.ownsMotion and result.sourceKey=='test:modifier/a') end}");
        body.onLivingUpdate();
        near(body.posX, 8.375);
        require(lua.get("order").tojstring().equals("a"), "Losing producer ran after an accepted command");
        execute(lua,
                "b:clear('onMotion'); a:update{onMotion=function() return nil end}; "
                        + "c:clear('onForces'); c:clear('onAfterMotion'); "
                        + "d=entity:modify{key='test:modifier/force_a',onForces=function(ctx) "
                        + "forceSnapshot=ctx.movement.velocity.x; return {velocityDelta={x=10,y=0,z=0}} end}; "
                        + "e=entity:modify{key='test:modifier/force_b',onForces=function(ctx) "
                        + "assert(ctx.movement.velocity.x==forceSnapshot); return {velocityDelta={x=10,y=0,z=0}} end,"
                        + "onAfterMotion=function(ctx,result) forceStatus=result.forceStatus end}");
        body.motionX = 0;
        body.onLivingUpdate();
        near(body.motionX, 0);
        require(lua.get("forceStatus").tojstring().equals("limit_exceeded"), "Oversized force batch was accepted");
        execute(lua, "assert(d:getStatus().applied and e:getStatus().applied)");
        ScriptResourceTracker.unload(OWNER);
    }

    private static void faultsAndAtomicUpdates() {
        EntityPig body = body(new FixtureWorld(), 8, 80, 8);
        Globals lua = lua(body);
        execute(lua,
                "faults=0; observations=0; bad=entity:modify{key='test:modifier/bad',priority=20,"
                        + "physics={gravityMultiplier=0},onMotion=function(ctx) faults=faults+1; "
                        + "return {mode='manual',velocity={x=100,y=0,z=0}} end}; "
                        + "good=entity:modify{key='test:modifier/good',onMotion=function(ctx) "
                        + "saved=ctx.entity; return {mode='native'} end,onAfterMotion=function(ctx,result) "
                        + "observations=observations+1; error('observer fixture') end}");
        body.onLivingUpdate();
        near(body.motionY, -0.08 * (double) 0.98F);
        body.onLivingUpdate();
        execute(lua,
                "assert(faults==1 and observations==1); assert(bad:getStatus().state=='failed'); "
                        + "assert(good:getStatus().applied and good:getStatus().observerFailed); "
                        + "assert(not pcall(function() saved:getVelocity() end)); "
                        + "assert(not pcall(function() good:update{priority=5,physics={gravityMultiplier=-1}} end)); "
                        + "assert(entity:getModifications{sources=true}.sources[2].priority==0); "
                        + "good:update{onAfterMotion=function(ctx,r) observations=observations+1 end}; "
                        + "bad:clear('onMotion'); assert(bad:getStatus().applied)");
        body.motionY = 0;
        body.onLivingUpdate();
        near(body.motionY, 0);
        execute(lua,
                "assert(observations==2); good:close(); bad:close(); "
                        + "forceFault=entity:modify{key='test:modifier/force_fault',physics={gravityMultiplier=0},"
                        + "onForces=function() error('force fixture') end}");
        body.onLivingUpdate();
        near(body.motionY, -0.08 * (double) 0.98F);
        execute(lua, "assert(forceFault:getStatus().state=='failed'); forceFault:retry(); "
                + "forceFault:clear('onForces'); assert(forceFault:getStatus().applied)");
        ScriptResourceTracker.unload(OWNER);
    }

    private static void committedContactEdits() {
        FixtureWorld world = new FixtureWorld();
        EntityPig body = body(world, 8, 80, 8);
        Globals lua = lua(body);
        execute(lua, "observations=0; source=entity:modify{key='test:modifier/contact',"
                + "onMotion=function() return {mode='manual',velocity={x=0.25,y=0,z=0}} end,"
                + "onAfterMotion=function(ctx,result) observations=observations+1; assert(ctx.ownsMotion) end}");
        EntityModifierRuntime.Source source = EntityModifierRuntime.sources(body).get(0);
        world.contact = () -> source.setEnabled(false);
        body.onLivingUpdate();
        near(body.posX, 8.25);
        require(lua.get("observations").toint() == 1, "Contact edit changed the committed observer snapshot");
        body.onLivingUpdate();
        require(body.motionY < 0, "Contact edit did not affect the next step");
        execute(lua, "source:setEnabled(true)");
        world.contact = () -> ScriptResourceTracker.unload(OWNER);
        body.onLivingUpdate();
        require(lua.get("observations").toint() == 1, "Unloaded owner received a remaining observation");
        require(!EntityModifierRuntime.hasSources(body), "Contact-time unload retained its binding");
    }

    private static void nativeParity() throws Exception {
        for (int scenario = 0; scenario < 5; scenario++) {
            FixtureWorld firstWorld = new FixtureWorld();
            FixtureWorld secondWorld = new FixtureWorld();
            firstWorld.lava = secondWorld.lava = scenario == 3;
            EntityPig plain = body(firstWorld, 8, scenario == 1 ? 64 : 80, 8);
            EntityPig modified = body(secondWorld, 8, scenario == 1 ? 64 : 80, 8);
            plain.onGround = modified.onGround = scenario == 1;
            if (scenario == 2) {
                Field water = NativeFieldAccess.field(Entity.class,
                        new FieldRef(new ClassRef("net/minecraft/src/Entity"), "inWater", "Z"));
                water.setBoolean(plain, true);
                water.setBoolean(modified, true);
            }
            if (scenario == 4) {
                firstWorld.blocks.put("8,80,8", Block.ladder.blockID);
                secondWorld.blocks.put("8,80,8", Block.ladder.blockID);
            }
            plain.motionX = modified.motionX = 0.13;
            plain.motionY = modified.motionY = -0.1;
            Globals lua = lua(modified);
            execute(lua, "source=entity:modify{key='test:modifier/neutral',physics={gravityMultiplier=1},"
                    + "movement={accelerationMultiplier=1,jumpMultiplier=1,jumpEnabled=true,swimLiftEnabled=true},"
                    + "damage={fallMultiplier=1},onMotion=function() return nil end}");
            for (int tick = 0; tick < 12; tick++) {
                plain.onLivingUpdate();
                modified.onLivingUpdate();
                near(plain.posX, modified.posX);
                near(plain.posY, modified.posY);
                near(plain.motionY, modified.motionY);
                near(plain.motionX, modified.motionX);
                require(plain.onGround == modified.onGround, "Neutral source changed native ground state");
            }
            NBTTagCompound saved = new NBTTagCompound();
            modified.writeToNBT(saved);
            EntityPig restored = body(secondWorld, 8, 80, 8);
            restored.readFromNBT(saved);
            require(!EntityModifierRuntime.hasSources(restored), "Transient modifiers leaked into native saves");
            execute(lua, "source:close()");
            plain.onLivingUpdate();
            modified.onLivingUpdate();
            near(plain.motionY, modified.motionY);
            ScriptResourceTracker.unload(OWNER);
        }
    }

    private static void collisionsAndFall() {
        FixtureWorld world = new FixtureWorld();
        for (int y = 64; y < 90; y++) {
            world.blocks.put("10," + y + ",8", Block.stone.blockID);
        }
        EntityPig body = body(world, 8, 80, 8);
        Globals lua = lua(body);
        execute(lua,
                "source=entity:modify{key='test:modifier/collision',onMotion=function() "
                        + "return {mode='manual',velocity={x=4,y=0,z=0}} end,"
                        + "onAfterMotion=function(ctx,result) last=result end}");
        body.onLivingUpdate();
        near(body.boundingBox.maxX, 10);
        require(lua.get("last").get("collidedHorizontally").toboolean(), "Wall collision was bypassed");
        execute(lua, "source:update{onMotion=function() return {mode='manual',velocity={x=0,y=-16,z=0}} end}");
        body.onLivingUpdate();
        body.onLivingUpdate();
        near(body.boundingBox.minY, 64);
        require(body.onGround, "Manual collision lost native floor flags");
        world.blocks.put("8,82,8", Block.stone.blockID);
        body.setPosition(8, 80, 8);
        execute(lua, "source:update{onMotion=function() return {mode='manual',velocity={x=0,y=4,z=0}} end}");
        body.onLivingUpdate();
        near(body.boundingBox.maxY, 82);
        near(body.motionY, 0);
        world.blocks.put("9,64,8", Block.stairSingle.blockID);
        body.setPosition(8, 64, 8);
        body.onGround = true;
        execute(lua, "source:update{onMotion=function() return {mode='manual',velocity={x=1.5,y=-0.1,z=0}} end}");
        body.onLivingUpdate();
        require(body.posX > 9.2 && body.boundingBox.minY >= 64.5, "Manual motion lost native slab stepping");
        execute(lua, "source:update{physics={trackFallDistance=false},damage={fallMultiplier=0}}");
        NativeEntityFields.fallDistance(body, 30);
        body.onLivingUpdate();
        near(NativeEntityFields.fallDistance(body), 0);
        execute(lua, "source:close()");
        ScriptResourceTracker.unload(OWNER);

        for (double scale : new double[]{0, 0.5, 1}) {
            EntityPig falling = body(new FixtureWorld(), 8, 65, 8);
            lua = lua(falling);
            execute(lua, "source=entity:modify{key='test:modifier/fall',damage={fallMultiplier=" + scale + "}}");
            NativeEntityFields.fallDistance(falling, 8);
            falling.motionY = -2;
            falling.onLivingUpdate();
            require(falling.health == 20 - (int) Math.ceil(5 * scale), "Fall damage was scaled at the wrong stage");
            ScriptResourceTracker.unload(OWNER);
        }

        world = new FixtureWorld();
        body = body(world, 8, 80, 8);
        lua = lua(body);
        execute(lua,
                "observations=0; source=entity:modify{key='test:modifier/exception',"
                        + "onMotion=function() return {mode='manual',velocity={x=0.1,y=0,z=0}} end,"
                        + "onAfterMotion=function() observations=observations+1 end}");
        world.throwContacts = true;
        try {
            body.onLivingUpdate();
            throw new AssertionError("Expected native contact fixture failure");
        } catch (ContactFailure expected) {
            require(lua.get("observations").toint() == 0, "Failed native step published success");
        }
        world.throwContacts = false;
        body.onLivingUpdate();
        require(lua.get("observations").toint() == 1, "Exceptional step retained temporary state");
        world.maxLoadedX = 20;
        execute(lua, "source:update{onMotion=function() return {mode='manual',velocity={x=16,y=0,z=0}} end,"
                + "onAfterMotion=function(ctx,result) blocked=result.blockedReason end}");
        double position = body.posX;
        body.onLivingUpdate();
        near(body.posX, position);
        near(body.motionX, 0);
        require(lua.get("blocked").tojstring().equals("unloaded_chunk"), "Unsafe selected movement was retried");
        ScriptResourceTracker.unload(OWNER);
    }

    private static void lifecycleAndAuthority() throws Exception {
        FixtureWorld world = new FixtureWorld();
        EntityPig body = body(world, 8, 80, 8);
        Globals lua = lua(body);
        execute(lua, "detaches=0; source=entity:modify{key='test:modifier/life',physics={gravityMultiplier=0},"
                + "onDetach=function(ctx,event) assert(ctx.entity==nil and ctx.world==nil); detaches=detaches+1 end}; "
                + "assert(not pcall(function() entity:modify{key='test:modifier/life'} end)); "
                + "assert(not entity:supportsModification('movement.sneakBodyAdjustment')); "
                + "assert(not pcall(function() entity:supportsModification('unknown') end))");
        body.ridingEntity = body(world, 12, 80, 8);
        body.onLivingUpdate();
        require(body.motionY < 0, "Mounted body applied independent movement rules");
        execute(lua, "assert(source:getStatus().reason=='mounted')");
        body.ridingEntity = null;
        body.motionY = 0;
        body.onLivingUpdate();
        near(body.motionY, 0);
        ScriptExecution.withOwner("foreign.lua",
                () -> lua.load("assert(not pcall(function() source:close() end))").call());
        try (LuaCallbackScope readOnly = new LuaCallbackScope(false)) {
            ScriptExecution.withOwner(OWNER,
                    () -> lua.load("assert(not pcall(function() source:setEnabled(false) end))").call());
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                lua.get("source").get("getStatus").call();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        worker.start();
        worker.join();
        require(failure.get() != null, "Modifier query accepted a foreign thread");
        body.health = 0;
        require(!lua.get("source").get("isValid").call().toboolean(), "Dead direct source remained valid");
        world.loaded = false;
        EntityModifierRuntime.synchronize(null, null);
        require(lua.get("detaches").toint() == 1, "Death failed to detach exactly once without world reads");
        require(!EntityModifierRuntime.hasSources(body), "Dead source retained native body");
        ScriptResourceTracker.unload(OWNER);
        EntitySquid squid = new EntitySquid(new FixtureWorld());
        Globals unsupported = lua(squid);
        execute(unsupported, "assert(not entity:supportsModification('onMotion')); "
                + "assert(not pcall(function() entity:modify{key='test:modifier/unsupported'} end))");
    }

    private static void nativeLivingAndLimits() throws Exception {
        Method read = Class.forName("betamoon.luaapi.entity.EntityDeclaration").getDeclaredMethod("read",
                LuaValue.class);
        read.setAccessible(true);
        Globals definitions = JsePlatform.standardGlobals();
        EntityTypeDefinition definition = (EntityTypeDefinition) read.invoke(null,
                definitions.load("return {key='test:walker',kind='living',appearance={model='fixture/walker.bm.json'},"
                        + "living={ai={mode='manual'}}}").call());
        EntityTypeRegistry.publish(OWNER, Collections.singletonMap(definition.key, definition));
        LuaLivingEntity custom = new LuaLivingEntity(new FixtureWorld());
        custom.entityState().attach(definition);
        custom.setPosition(8, 80, 8);
        require(custom.setManualMovement(1, 0, false), "Manual AI fixture could not submit intent");
        Globals lua = lua(custom);
        execute(lua, "source=entity:modify{key='test:modifier/custom',physics={gravityMultiplier=0},"
                + "onMotion=function(ctx) assert(ctx.input==nil and ctx.nativeIntent.forward>0); intent=true end}");
        custom.onUpdate();
        require(lua.get("intent").toboolean(), "BetaMoon native-lifecycle entity lost AI intent");
        near(custom.motionY, 0);
        require(NativeLivingFields.forward(custom) > 0, "Modifier consumed the native AI movement intent");
        ScriptResourceTracker.unload(OWNER);
        EntityTypeRegistry.clear();

        FixtureWorld world = new FixtureWorld();
        ArrayList<EntityModifierRuntime.Source> sources = new ArrayList<>();
        try {
            EntityPig first = null;
            for (int entity = 0; entity < 64; entity++) {
                EntityPig target = body(world, 8, 80, 8);
                if (first == null) {
                    first = target;
                }
                for (int source = 0; source < 64; source++) {
                    sources.add(EntityModifierRuntime.attach(OWNER, target, definition(source)));
                }
            }
            try {
                EntityModifierRuntime.attach(OWNER, first, definition(64));
                throw new AssertionError("Entity capacity accepted a 65th source");
            } catch (IllegalStateException expected) {
                require(EntityModifierRuntime.sources(first).size() == 64, "Rejected source partially attached");
            }
            try {
                EntityModifierRuntime.attach(OWNER, body(world, 8, 80, 8), definition(0));
                throw new AssertionError("World capacity accepted a 4097th source");
            } catch (IllegalStateException expected) {
                require(sources.size() == 4096, "World capacity fixture was incomplete");
            }
        } finally {
            for (EntityModifierRuntime.Source source : sources) {
                source.close();
            }
            sources.clear();
        }
        try {
            for (int i = 0; i < 256; i++) {
                sources.add(EntityModifierRuntime.registerLocal(OWNER, definition(i)));
            }
            try {
                EntityModifierRuntime.registerLocal(OWNER, definition(256));
                throw new AssertionError("Local capacity accepted a 257th registration");
            } catch (IllegalStateException expected) {
                require(sources.size() == 256, "Local capacity fixture was incomplete");
            }
        } finally {
            for (EntityModifierRuntime.Source source : sources) {
                source.close();
            }
        }
    }

    private static EntityModifierDefinition definition(int index) {
        return new EntityModifierDefinition(
                ContentKey.parseForType("test:modifier/limit_" + index, EntityRuleSource.KEY_TYPE), 0, true,
                EntityRuleSet.EMPTY, EntityModifierCallbacks.EMPTY);
    }

    private static Globals lua(Entity entity) {
        currentTarget = entity;
        Globals globals = JsePlatform.standardGlobals();
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            globals.set("entity", LuaEntityActionAccess.create(scope, null, entity));
            ScriptExecution.withOwner(OWNER, () -> globals.load("assert(entity:isAlive())").call());
        }
        return globals;
    }

    private static void execute(Globals lua, String code) {
        Entity target = currentTarget;
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            if (target != null) {
                lua.set("entity", LuaEntityActionAccess.create(scope, null, target));
            }
            ScriptExecution.withOwner(OWNER, () -> lua.load(code).call());
        }
    }

    private static Entity currentTarget;

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 0.00001, actual + " != " + expected);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    static final class FixtureWorld extends World {
        private final Map<Long, Chunk> chunks = new HashMap<>();
        final Map<String, Integer> blocks = new HashMap<>();
        boolean loaded = true;
        boolean lava;
        boolean throwContacts;
        Runnable contact;
        int maxLoadedX = Integer.MAX_VALUE;

        FixtureWorld() {
            super(null, "motion-fixture", new WorldProvider() {
            }, 0L);
        }

        public boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2) {
            return loaded && x2 <= maxLoadedX;
        }

        public boolean blockExists(int x, int y, int z) {
            return loaded;
        }

        public int getBlockId(int x, int y, int z) {
            if (x > maxLoadedX) {
                throw new AssertionError("Read an unloaded movement destination");
            }
            Integer block = blocks.get(x + "," + y + "," + z);
            return block == null ? y == 63 ? Block.stone.blockID : 0 : block;
        }

        public boolean isMaterialInBB(AxisAlignedBB bounds, Material material) {
            return material == Material.lava && lava;
        }

        public List getEntitiesWithinAABBExcludingEntity(Entity excluded, AxisAlignedBB bounds) {
            if (throwContacts && bounds.minY == excluded.boundingBox.minY) {
                throw new ContactFailure();
            }
            if (contact != null && bounds.minY == excluded.boundingBox.minY) {
                Runnable callback = contact;
                contact = null;
                callback.run();
            }
            return Collections.emptyList();
        }

        public Chunk getChunkFromChunkCoords(int x, int z) {
            if (!loaded) {
                throw new AssertionError("Unexpected unloaded chunk access");
            }
            long key = (long) x << 32 ^ z & 0xffffffffL;
            Chunk chunk = chunks.get(key);
            if (chunk == null) {
                chunk = new Chunk(this, new byte[32768], x, z);
                chunk.isChunkLoaded = true;
                chunks.put(key, chunk);
            }
            return chunk;
        }

        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                public boolean chunkExists(int x, int z) {
                    return loaded;
                }

                public Chunk provideChunk(int x, int z) {
                    return getChunkFromChunkCoords(x, z);
                }

                public Chunk prepareChunk(int x, int z) {
                    return getChunkFromChunkCoords(x, z);
                }

                public void populate(IChunkProvider provider, int x, int z) {
                }

                public boolean saveChunks(boolean force, IProgressUpdate progress) {
                    return true;
                }

                public boolean unload100OldestChunks() {
                    return false;
                }

                public boolean canSave() {
                    return false;
                }

                public String makeString() {
                    return "motion-fixture";
                }
            };
        }
    }

    private static final class ContactFailure extends RuntimeException {
    }
}
