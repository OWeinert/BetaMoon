package betamoon.luamodloader;

import betamoon.assets.AssetKey;
import betamoon.assets.AssetPath;
import betamoon.assets.io.AssetProvider;
import betamoon.assets.model.ModelFoundationTest;
import betamoon.assets.model.ModelPose;
import betamoon.client.assets.AssetResourceTest;
import betamoon.client.assets.ClientAssets;
import betamoon.client.render.EntityVisuals;
import betamoon.client.render.ModelAppearance;
import betamoon.entity.EntityBootstrap;
import betamoon.entity.EntityDataDelta;
import betamoon.entity.EntityLifecycleEvents;
import betamoon.entity.EntityLoot;
import betamoon.entity.EntityPresentationEvents;
import betamoon.entity.EntitySpawner;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.EntityTypeRegistry;
import betamoon.entity.LuaEntityPart;
import betamoon.entity.LuaLivingEntity;
import betamoon.entity.LuaPickupEntity;
import betamoon.entity.LuaProjectileEntity;
import betamoon.entity.LuaPropEntity;
import betamoon.instrumentation.hooks.entity.EntityLifecycleCallbacks;
import betamoon.luaapi.asset.AssetsApi;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import betamoon.luaapi.audio.AudioApi;
import betamoon.luaapi.audio.SoundEvents;
import betamoon.luaapi.entity.EntitiesApi;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.item.ItemUseDefinition;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.network.protocol.WireValue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkLoader;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityList;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.PathEntity;
import net.minecraft.src.PathPoint;
import net.minecraft.src.SaveHandler;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Exercises declarations and native entity behavior in an in-memory Beta world. */
public final class EntityLuaApiTest {
    private EntityLuaApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Minecraft blocks must initialize before item lookup");
        Memory files = new Memory();
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", image);
        files.files.put("test.png", image.toByteArray());
        files.files.put("test.wav", AssetResourceTest.wav());
        files.files.put("entity_test.json", ModelFoundationTest.GEOMETRY.getBytes(StandardCharsets.UTF_8));
        files.files.put("entity_test.animation.json",
                ModelFoundationTest.ANIMATIONS.getBytes(StandardCharsets.UTF_8));
        ClientAssets.useProviders(files, new Memory(), message -> {
        });
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        AssetsApi.attach(api);
        AudioApi.attach(api);
        EntitiesApi.attach(api);
        lua.set("betamoon", api);
        lua.set("spawnCount", org.luaj.vm2.LuaValue.ZERO);
        lua.set("loadCount", org.luaj.vm2.LuaValue.ZERO);
        lua.set("loadSpawnCount", org.luaj.vm2.LuaValue.ZERO);
        lua.set("loadRemoveCount", org.luaj.vm2.LuaValue.ZERO);
        lua.set("removeReason", org.luaj.vm2.LuaValue.NIL);
        lua.set("removeCount", org.luaj.vm2.LuaValue.ZERO);
        lua.set("deathCount", org.luaj.vm2.LuaValue.ZERO);
        lua.set("pickupOrder", org.luaj.vm2.LuaValue.valueOf(""));
        lua.set("reloadTicks", org.luaj.vm2.LuaValue.ZERO);
        lua.set("partDamageCalls", org.luaj.vm2.LuaValue.ZERO);
        lua.set("partInteractions", org.luaj.vm2.LuaValue.ZERO);
        lua.set("damageTrace", org.luaj.vm2.LuaValue.valueOf(""));
        lua.set("killTrace", org.luaj.vm2.LuaValue.valueOf(""));
        lua.set("manualLootAccepted", org.luaj.vm2.LuaValue.FALSE);
        lua.set("manualLootDeaths", org.luaj.vm2.LuaValue.ZERO);
        lua.set("lastDamageCause", org.luaj.vm2.LuaValue.NIL);
        lua.set("lastDirectSource", org.luaj.vm2.LuaValue.NIL);
        lua.set("lastHealthLost", org.luaj.vm2.LuaValue.NIL);
        lua.set("livingTrace", org.luaj.vm2.LuaValue.valueOf(""));
        lua.set("mortalHealthLost", org.luaj.vm2.LuaValue.NIL);
        lua.set("failedDamageCalls", org.luaj.vm2.LuaValue.ZERO);
        lua.set("afterFailedDamageCalls", org.luaj.vm2.LuaValue.ZERO);
        lua.set("manualTargetCalls", org.luaj.vm2.LuaValue.ZERO);
        lua.set("manualAttackCalls", org.luaj.vm2.LuaValue.ZERO);
        lua.set("manualMoveCalls", org.luaj.vm2.LuaValue.ZERO);
        lua.set("manualMoveErrors", org.luaj.vm2.LuaValue.ZERO);
        lua.set("stageFourInteractions", org.luaj.vm2.LuaValue.ZERO);
        lua.set("sensorTrace", org.luaj.vm2.LuaValue.valueOf(""));
        lua.set("behaviorTrace", org.luaj.vm2.LuaValue.valueOf(""));
        lua.set("behaviorKinds", org.luaj.vm2.LuaValue.valueOf(""));
        lua.set("stagePathX", org.luaj.vm2.LuaValue.valueOf(6));
        lua.load("function brokenPartDamage(ctx) partDamageCalls=partDamageCalls+1; "
                + "error('expected part error') end").call();
        List<EntityPresentationEvents.SoundRequest> presentationEvents = new ArrayList<>();
        EntityPresentationEvents.install(presentationEvents::add);

        try {
            EntityBootstrap.register();
            try (ScriptExecutionScope owner = ScriptExecutionScope.open("entities.lua");
                    ScriptAssetScope assets = ScriptAssetScope.open("entities.lua");
                    ScriptEntityScope scope = ScriptEntityScope.open("entities.lua")) {
                lua.load("local sound=betamoon.assets.sounds:add{key='mymod:entity_sound',path='test.wav'}; "
                        + "presentationSound=betamoon.soundEvents:add{key='mymod:entity_sound',sound=sound}")
                        .call();
                lua.load("local prop=betamoon.entities:add{key='mymod:lamp',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "parts={root={hitbox={offset={x=0,y=0.5,z=0},"
                        + "size={x=0.2,y=1,z=0.2}}}},"
                        + "data={uses={type='integer',default=2}},"
                        + "onSpawn=function(ctx) spawnCount=spawnCount+1 end,"
                        + "onLoad=function(ctx) loadCount=loadCount+1 end,"
                        + "onRemove=function(ctx) removeReason=ctx.reason; removeCount=removeCount+1 end}; "
                        + "assert(prop:getKey()=='mymod:lamp'); "
                        + "assert(prop.getTemplate==nil and betamoon.entities.one==nil); "
                        + "assert(betamoon.entities:get('mymod:lamp'):getKey()=='mymod:lamp'); "
                        + "assert(betamoon.entities:get('mymod:missing')==nil); "
                        + "assert(betamoon.entities:get('minecraft:sheep')==nil); "
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad',"
                        + "template=prop,appearance={model='minecraft:block/torch',texture='test.png'}} end)); "
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad2',"
                        + "health={max=0},appearance={model='minecraft:block/torch',texture='test.png'}} end)); "
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:no_model'} end))")
                        .call();
                lua.load("local loadedChild=betamoon.entities:add{key='mymod:loaded_child',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'}};"
                        + "betamoon.entities:add{key='mymod:load_spawner',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "onLoad=function(ctx) local spawned,reason=ctx.world:spawnEntity(loadedChild,{"
                        + "position=ctx.entity:getPosition()}); assert(spawned,reason); "
                        + "loadSpawnCount=loadSpawnCount+1 end};"
                        + "betamoon.entities:add{key='mymod:load_remover',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "onLoad=function(ctx) loadRemoveCount=loadRemoveCount+1; ctx.entity:remove() end}")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:crate',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "physics={mode='dynamic',gravity=0.05,bounce=0.25,pushable=true}};"
                        + "betamoon.entities:add{key='mymod:scripted_crate',kind='prop',lifecycle='manual',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "onTick=function(ctx) end};"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:no_tick',"
                        + "lifecycle='manual',appearance={model='minecraft:block/torch',texture='test.png'}} end));"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:old_lifecycle',"
                        + "lifecycle='lua',appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "onTick=function(ctx) end} end))")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:breakable',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "health={max=6},"
                        + "onActivate=function(ctx) damageTrace=damageTrace..'activate:'..ctx.reason..',' end,"
                        + "onDeactivate=function(ctx) damageTrace=damageTrace..'deactivate:'..ctx.reason..',' end,"
                        + "onBeforeDamage=function(ctx) damageTrace=damageTrace..'before,'; "
                        + "if ctx.amount==2 then return 0 end; return ctx.amount-1 end,"
                        + "onAfterDamage=function(ctx) damageTrace=damageTrace..'after:'..ctx.healthLost..',' end,"
                        + "onDeath=function(ctx) damageTrace=damageTrace..'death,'; "
                        + "assert(ctx.entity:heal(1)==false); ctx.entity:remove() end,"
                        + "onRemove=function(ctx) damageTrace=damageTrace..'remove:'..ctx.reason..',' end}")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:killable_prop',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "drops={{item=264,min=2,max=2}},"
                        + "onBeforeDamage=function(ctx) killAccepted=ctx.entity:kill(ctx.attacker); return 0 end,"
                        + "onDeath=function(ctx) killTrace=killTrace..'death,' end,"
                        + "onRemove=function(ctx) killTrace=killTrace..'remove:'..ctx.reason end}")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:manual_loot_prop',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "drops={{item=264,min=2,max=2}},"
                        + "onBeforeDamage=function(ctx) "
                        + "manualLootAccepted=ctx.entity:dropLoot() and ctx.entity:dropLoot() "
                        + "and ctx.entity:dropItem({id=331,count=3}); ctx.entity:remove(); return 0 end,"
                        + "onDeath=function(ctx) manualLootDeaths=manualLootDeaths+1 end}")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:stack_limited_loot',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "drops={{item=" + Item.swordSteel.shiftedIndex + ",min=3,max=3}}}")
                        .call();
                lua.load("local shot=betamoon.entities:add{key='mymod:shot',kind='projectile',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "projectile={speed=2,gravity=0,lifetimeTicks=40,damage=3},"
                        + "onImpact=function(hit) return 'remove' end}; "
                        + "betamoon.entities:add{key='mymod:default_shot',kind='projectile',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "projectile={speed=4,gravity=0,lifetimeTicks=40,damage=3},"
                        + "sounds={impact=presentationSound}}; "
                        + "betamoon.entities:add{key='mymod:self_removing_shot',kind='projectile',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "projectile={speed=4,gravity=0,lifetimeTicks=40,damage=3},"
                        + "onImpact=function(hit) hit.entity:remove(); return 'default' end}; "
                        + "assert(shot:getKey()=='mymod:shot'); "
                        + "betamoon.entities:add{key='mymod:creature',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={maxHealth=36,movementSpeed=0.5,ai='wander'},"
                        + "onBeforeDamage=function(ctx) lastDamageCause=ctx.cause; "
                        + "lastDirectSource=ctx.directSource and ctx.directSource:getName(); "
                        + "if ctx.amount==7 then return 4 end end,"
                        + "onAfterDamage=function(ctx) lastHealthLost=ctx.healthLost end}; "
                        + "betamoon.entities:add{key='mymod:mortal',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={maxHealth=4,ai='idle'},"
                        + "onBeforeDamage=function(ctx) livingTrace=livingTrace..'before,' end,"
                        + "onAfterDamage=function(ctx) mortalHealthLost=ctx.healthLost; "
                        + "assert(ctx.entity:getHealth()==0); livingTrace=livingTrace..'after,' end,"
                        + "onDeath=function(ctx) livingTrace=livingTrace..'death,'; "
                        + "assert(ctx.entity:heal(1)==false); "
                        + "ctx.entity:remove() end,"
                        + "onDeactivate=function(ctx) livingTrace=livingTrace..'deactivate:'..ctx.reason..',' end,"
                        + "onRemove=function(ctx) livingTrace=livingTrace..'remove:'..ctx.reason..',' end}; "
                        + "betamoon.entities:add{key='mymod:failed_damage',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "health={max=6},"
                        + "onBeforeDamage=function(ctx) failedDamageCalls=failedDamageCalls+1; "
                        + "error('expected damage error') end,"
                        + "onAfterDamage=function(ctx) afterFailedDamageCalls=afterFailedDamageCalls+1 end}; "
                        + "betamoon.entities:add{key='mymod:multipart_creature',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={maxHealth=12,ai='idle'},"
                        + "parts={root={hitbox={offset={x=0,y=0.5,z=0},"
                        + "size={x=0.4,y=0.4,z=0.4}},onDamage=function(ctx) "
                        + "if ctx.amount==9 then ctx.entity:remove() end; return 2 end}}}; "
                        + "betamoon.entities:add{key='mymod:isolated_part',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={maxHealth=12,ai='idle'},"
                        + "parts={root={hitbox={offset={x=0,y=0.5,z=0},"
                        + "size={x=0.4,y=0.4,z=0.4}},"
                        + "onDamage=brokenPartDamage,"
                        + "onInteract=function(ctx) partInteractions=partInteractions+1; return 'handled' end}}}; "
                        + "betamoon.entities:add{key='mymod:drop',kind='pickup',pickup={item=264,count=2},"
                        + "sounds={pickup=presentationSound},"
                        + "onPickup=function(ctx) pickupOrder=pickupOrder..'pickup,' end,"
                        + "onRemove=function(ctx) pickupOrder=pickupOrder..ctx.reason end}; "
                        + "betamoon.entities:add{key='mymod:pilot',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "parts={root={}},"
                        + "physics={mode='static'},"
                        + "living={ai={mode='manual',routine=function(ctx) end}},"
                        + "onDeath=function(ctx) deathCount=deathCount+1 end}; "
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:wrong',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "onImpact=function() end} end)); "
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad_shot',"
                        + "kind='projectile',appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "projectile={speed=-1}} end));"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad_physics',"
                        + "kind='living',appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "physics={mode='dynamic',gravity=0.2}} end));"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:old_ai',"
                        + "kind='living',appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={ai={mode='lua',routine=function(ctx) end}}} end))").call();
                lua.load("betamoon.entities:add{key='mymod:composed_target',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={ai={preset='directed',stages={"
                        + "target={mode='manual',routine=function(ctx) "
                        + "manualTargetCalls=manualTargetCalls+1; "
                        + "return ctx.world:getClosestPlayer(12) end}}}}};"
                        + "betamoon.entities:add{key='mymod:composed_clear_target',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={aggression='players',ai={preset='directed',stages={"
                        + "target={mode='manual',routine=function(ctx) return false end}}}}};"
                        + "betamoon.entities:add{key='mymod:composed_attack',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={aggression='players',ai={preset='directed',stages={"
                        + "attack={mode='manual',routine=function(ctx) "
                        + "manualAttackCalls=manualAttackCalls+1; return 2 end},"
                        + "movement={mode='manual',routine=function(ctx) return false end}}}}};"
                        + "betamoon.entities:add{key='mymod:composed_path',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={ai={preset='idle',stages={"
                        + "path={mode='manual',routine=function(ctx) "
                        + "return {x=stagePathX,y=64,z=2} end}}}}};"
                        + "betamoon.entities:add{key='mymod:composed_movement',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={ai={preset='idle',stages={"
                        + "movement={mode='manual',routine=function(ctx) "
                        + "manualMoveCalls=manualMoveCalls+1; "
                        + "return {forward=1,strafe=0,jump=false} end}}}}};"
                        + "betamoon.entities:add{key='mymod:bad_composed_movement',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={ai={preset='idle',stages={"
                        + "movement={mode='manual',routine=function(ctx) "
                        + "manualMoveErrors=manualMoveErrors+1; "
                        + "return {forward=2,strafe=0,jump=false} end}}}}};"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad_stage',"
                        + "kind='living',appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={ai={stages={target={mode='manual'}}}}} end));"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad_lifecycle_stage',"
                        + "kind='living',lifecycle='manual',onTick=function() end,"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "living={ai={stages={}}}} end))")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:stage_four',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "physics={mode='static'},health={max=10},body={targetable=false},"
                        + "parts={root={"
                        + "hitbox={offset={x=1,y=0.5,z=0},size={x=0.5,y=1,z=0.5}},"
                        + "interactionBox={offset={x=-1,y=0.5,z=0},size={x=0.5,y=1,z=0.5}},"
                        + "onInteract=function(ctx) assert(ctx.hitbox:getPartRole()=='interaction'); "
                        + "stageFourInteractions=stageFourInteractions+1; return 'handled' end}},"
                        + "sensors={nearby={"
                        + "shape={offset={x=0,y=0.5,z=0},size={x=4,y=2,z=4}},"
                        + "filter='players',intervalTicks=1,"
                        + "onEnter=function(ctx) assert(ctx.sensor=='nearby'); "
                        + "sensorTrace=sensorTrace..'enter,' end,"
                        + "onStay=function(ctx) sensorTrace=sensorTrace..'stay,' end,"
                        + "onLeave=function(ctx) sensorTrace=sensorTrace..'leave:'..ctx.reason..',' end}}}")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:presentation',kind='prop',"
                        + "appearance={model='entity_test.json',texture='test.png',"
                        + "animation={asset='entity_test.animation.json',clip='wave'}},"
                        + "physics={mode='static'},health={max=5},"
                        + "render={shadowRadius=0.4,fireOverlay=false},"
                        + "sounds={ambient={event=presentationSound,intervalTicks=1},"
                        + "step={event=presentationSound,distance=0.1},hurt=presentationSound,"
                        + "death=presentationSound}};"
                        + "betamoon.entities:add{key='mymod:custom_drop',kind='pickup',"
                        + "pickup={item=264},appearance={model='entity_test.json',texture='test.png'},"
                        + "render={pickupBobbing=false,pickupSpin=false}}")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:stage_six',kind='living',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "physics={mode='static'},living={maxHealth=10,ai='idle',despawn=false},"
                        + "spawning={category='passive',weight=7,group={min=2,max=3},cap=5,"
                        + "light={min=4,max=12},height={min=32,max=96},dimensions={0},"
                        + "substrates={1},despawn='persistent'},"
                        + "inventory={size=9,title='Companion Pack',openOnInteract=false,dropOnDeath=true},"
                        + "equipment={slots={'hand','head'},dropOnDeath=true},"
                        + "relations={ownership=true,team='mymod:companions'},"
                        + "mount={passengerOffset=1.25,allowPlayers=true,allowEntities=true},"
                        + "behavior={states={'idle','active'},initialState='idle',"
                        + "onStateExit=function(ctx) behaviorTrace=behaviorTrace..'exit:'..ctx.previous..',' end,"
                        + "onStateEnter=function(ctx) behaviorTrace=behaviorTrace..'enter:'..ctx.state..',' end,"
                        + "onTimer=function(ctx) behaviorTrace=behaviorTrace..'timer:'..ctx.name..':'"
                        + "..tostring(ctx.repeating)..',' end}};"
                        + "betamoon.entities:add{key='mymod:timed_prop',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "inventory={size=9},equipment={slots={'hand'}},"
                        + "relations={team='mymod:props'},mount={allowPlayers=true},"
                        + "behavior={onTimer=function(ctx) behaviorKinds=behaviorKinds..'prop,' end}};"
                        + "betamoon.entities:add{key='mymod:timed_projectile',kind='projectile',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "physics={mode='static'},projectile={lifetimeTicks=40},"
                        + "behavior={onTimer=function(ctx) behaviorKinds=behaviorKinds..'projectile,' end}};"
                        + "betamoon.entities:add{key='mymod:timed_pickup',kind='pickup',pickup={item=264},"
                        + "physics={mode='static'},"
                        + "behavior={onTimer=function(ctx) behaviorKinds=behaviorKinds..'pickup,' end}};"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad_spawn_kind',"
                        + "kind='prop',appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "spawning={}} end));"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad_inventory_kind',"
                        + "kind='projectile',appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "inventory={size=9}} end));"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad_manual_despawn',"
                        + "kind='living',lifecycle='manual',onTick=function() end,"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "spawning={despawn='native'}} end));"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:bad_inventory_size',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "inventory={size=10}} end))")
                        .call();
                lua.load("function drivePresentation(entity) "
                        + "assert(entity:playAnimation('wave',{speed=2,startTime=1})); "
                        + "local animation=entity:getAnimation(); "
                        + "assert(animation.clip=='wave' and animation.speed==2 and animation.time==1); "
                        + "entity:setVisible(false); entity:setVisualOffset(1,2,3); "
                        + "entity:setVisualRotation(10,20,30); entity:setVisualScale(2,3,4); "
                        + "assert(entity:playSound(presentationSound,{volume=0.5,range=12})) end")
                        .call();
                lua.load("function exerciseStageSix(entity,owner,passenger) "
                        + "assert(entity:getInventorySize()==9); "
                        + "assert(entity:setInventoryStack(1,{id=264,count=2,damage=0})); "
                        + "local stack=entity:getInventoryStack(1); "
                        + "assert(stack.id==264 and stack.count==2 and stack.damage==0); "
                        + "local removed=entity:removeInventoryStack(1,1); "
                        + "assert(removed.count==1 and entity:getInventoryStack(1).count==1); "
                        + "assert(entity:setEquipmentStack('hand',{id=264,count=1})); "
                        + "assert(entity:getEquipmentStack('hand').id==264); "
                        + "assert(not entity:setEquipmentStack('chest',{id=264,count=1})); "
                        + "assert(not pcall(function() entity:getInventoryStack(0) end)); "
                        + "assert(entity:setOwner(owner)); "
                        + "assert(entity:getOwner():getName()=='collector'); "
                        + "assert(entity:getOwnerIdentity()=='player:collector'); "
                        + "assert(entity:getTeam()=='mymod:companions'); "
                        + "assert(entity:setTeam('mymod:guards') and entity:getTeam()=='mymod:guards'); "
                        + "assert(entity:setTeam(nil) and entity:getTeam()=='mymod:companions'); "
                        + "assert(entity:isAlliedWith(entity)); "
                        + "assert(not entity:addPassenger(entity)); "
                        + "assert(entity:addPassenger(passenger)); "
                        + "assert(entity:getPassenger():getName()=='rider' and passenger:getVehicle()~=nil); "
                        + "assert(passenger:dismount() and entity:getPassenger()==nil); "
                        + "assert(entity:addPassenger(passenger) and entity:removePassenger()); "
                        + "assert(entity:setBehaviorState('active')); "
                        + "assert(entity:getBehaviorState()=='active'); "
                        + "assert(not pcall(function() entity:setBehaviorState('missing') end)); "
                        + "assert(entity:startTimer('pulse',2)); "
                        + "assert(entity:startTimer('heartbeat',1,2)) end")
                        .call();
                lua.load("betamoon.entities:add{key='mymod:stage_seven',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "data={route={type='list',maxLength=4,element={type='vector'}},"
                        + "profile={type='record',fields={label={type='string',default='idle'},"
                        + "origin={type='vector'},samples={type='list',maxLength=3,"
                        + "element={type='integer'}}}},"
                        + "cargo={type='item_stack'},target={type='entity_reference'}}};"
                        + "function exerciseStageSeven(entity,target) local data=entity.data; "
                        + "data:set('route',{{x=1,y=64,z=2},{x=3,y=65,z=4}}); "
                        + "assert(not pcall(function() data:set('route',{{x=1,y=1,z=1},{x=2,y=2,z=2},"
                        + "{x=3,y=3,z=3},{x=4,y=4,z=4},{x=5,y=5,z=5}}) end)); "
                        + "local route=data:get('route'); assert(#route==2 and route[2].y==65); "
                        + "route[1].x=99; assert(data:get('route')[1].x==1); "
                        + "data:set('profile',{label='guard',origin={x=4,y=5,z=6},samples={2,4,6}}); "
                        + "local profile=data:get('profile'); "
                        + "assert(profile.label=='guard' and profile.origin.z==6 and profile.samples[3]==6); "
                        + "data:set('cargo',{id=264,count=2,damage=0}); "
                        + "local cargo=data:get('cargo'); assert(cargo.id==264 and cargo.available); "
                        + "data:set('target',target); local reference=data:get('target'); "
                        + "assert(reference.kind=='entity' and reference.token=='entity:'..target:getIdentity()); "
                        + "assert(reference:isLoaded() and reference:resolve():getIdentity()==target:getIdentity()) end;"
                        + "assert(not pcall(function() betamoon.entities:add{key='mymod:oversized_data',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "data={huge={type='list',maxLength=256,element={type='list',maxLength=256,"
                        + "element={type='integer'}}}}} end))")
                        .call();
                require(EntityTypeRegistry.find(AssetKey.parse("mymod:lamp")) == null,
                        "Unpublished declarations must stay private");
                assets.publish();
                scope.publish();
                SoundEvents.publish("entities.lua");
            }
            EntityTypeDefinition lamp = EntityTypeRegistry.find(AssetKey.parse("mymod:lamp"));
            require(lamp != null && lamp.appearance != null, "The prop type must publish with its appearance");
            EntityTypeDefinition crate = EntityTypeRegistry.find(AssetKey.parse("mymod:crate"));
            require(crate != null && crate.physics.pushable && crate.physics.bounce == 0.25,
                    "Dynamic prop motion policy must publish");
            require(EntityTypeRegistry.find(AssetKey.parse("mymod:scripted_crate")).lifecycle
                    == betamoon.entity.EntityLifecycle.MANUAL, "Manual lifecycle must publish for props");
            LuaPropEntity crateInstance = new LuaPropEntity(null);
            crateInstance.entityState().attach(crate);
            require(crateInstance.canBePushed(), "Dynamic pushable props must participate in native pushes");
            require(lamp.parts.get("root").hittable, "Named model parts must support hitboxes");
            EntityTypeDefinition shot = EntityTypeRegistry.find(AssetKey.parse("mymod:shot"));
            EntityTypeDefinition creature = EntityTypeRegistry.find(AssetKey.parse("mymod:creature"));
            EntityTypeDefinition pickup = EntityTypeRegistry.find(AssetKey.parse("mymod:drop"));
            require(shot != null && shot.projectile.damage == 3, "Projectile policy must publish");
            require(creature != null && creature.living.maxHealth == 36, "Living policy must publish");
            require(pickup != null && pickup.pickup.count == 2 && pickup.appearance == null
                    && pickup.onPickup.isfunction(),
                    "Pickup must use the item renderer");
            EntityTypeDefinition pilot = EntityTypeRegistry.find(AssetKey.parse("mymod:pilot"));
            require(pilot != null && pilot.living.ai == betamoon.entity.LivingDefinition.Ai.MANUAL
                    && pilot.living.routine.isfunction(), "Manual AI routine must publish");
            require(pilot.physics.mode == betamoon.entity.EntityPhysicsDefinition.Mode.STATIC,
                    "Living entities must support stationary motion mode");
            require(!pilot.parts.get("root").hittable, "A named part may remain visual only");
            EntityTypeDefinition stageFour = EntityTypeRegistry.find(AssetKey.parse("mymod:stage_four"));
            require(stageFour != null && !stageFour.body.targetable && stageFour.sensors.size() == 1
                    && stageFour.parts.get("root").hitbox != null
                    && stageFour.parts.get("root").interactionBox != null,
                    "Body, damage, interaction and sensing shapes must compile independently");
            EntityTypeDefinition presentation = EntityTypeRegistry.find(AssetKey.parse("mymod:presentation"));
            EntityTypeDefinition customDrop = EntityTypeRegistry.find(AssetKey.parse("mymod:custom_drop"));
            require(presentation != null && presentation.appearance.animation != null
                    && presentation.sounds.bindings.size() == 4
                    && presentation.render.shadowRadius == 0.4f && !presentation.render.fireOverlay,
                    "Entity presentation declarations must retain animation, sound, and render controls");
            require(customDrop != null && customDrop.appearance != null
                    && !customDrop.render.pickupBobbing && !customDrop.render.pickupSpin,
                    "Pickups must support an optional model while retaining explicit native motion controls");
            EntityTypeDefinition stageSix = EntityTypeRegistry.find(AssetKey.parse("mymod:stage_six"));
            require(stageSix != null && stageSix.spawning != null && stageSix.spawning.weight == 7
                    && stageSix.spawning.groupMin == 2 && stageSix.spawning.groupMax == 3
                    && stageSix.inventory != null && stageSix.inventory.size == 9
                    && stageSix.equipment.slots.size() == 2 && stageSix.relations.ownership
                    && stageSix.mount.allowEntities && stageSix.behavior.states.size() == 2,
                    "Stage 6 capabilities must compile into independent typed definitions");
            require(EntityTypeRegistry.find(AssetKey.parse("mymod:timed_prop")).inventory != null
                    && EntityTypeRegistry.find(AssetKey.parse("mymod:timed_projectile")).behavior != null
                    && EntityTypeRegistry.find(AssetKey.parse("mymod:timed_pickup")).behavior != null,
                    "Content capabilities must validate for their supported entity kinds");
            EntityTypeDefinition stageSeven = EntityTypeRegistry.find(AssetKey.parse("mymod:stage_seven"));
            require(stageSeven != null && stageSeven.data.size() == 4,
                    "Stage 7 structured persistence declarations must publish");
            AiWorld aiWorld = new AiWorld();
            TestPlayer aiPlayer = new TestPlayer(aiWorld);
            aiPlayer.setPosition(4, 64, 2);
            require(aiWorld.entityJoinedWorld(aiPlayer), "AI fixture player must join the world");
            LuaLivingEntity composedTarget = (LuaLivingEntity) EntitySpawner.spawn(aiWorld,
                    AssetKey.parse("mymod:composed_target"), 2, 64, 2, 0, 0).entity;
            require(composedTarget != null, "Composed targeting creature must spawn");
            composedTarget.onUpdate();
            require(composedTarget.getTarget() == aiPlayer && aiWorld.pathCalls == 1
                    && "moving".equals(composedTarget.navigationStatus())
                    && lua.get("manualTargetCalls").toint() == 1,
                    "Manual target selection must feed native bounded path following");
            LuaLivingEntity clearTarget = (LuaLivingEntity) EntitySpawner.spawn(aiWorld,
                    AssetKey.parse("mymod:composed_clear_target"), 2, 64, 4, 0, 0).entity;
            require(clearTarget != null && clearTarget.attackEntityFrom(aiPlayer, 1)
                    && clearTarget.getTarget() == null,
                    "Manual target ownership must suppress automatic retaliation");
            clearTarget.onUpdate();
            require(clearTarget.getTarget() == null,
                    "A manual target stage must override native player acquisition");
            LuaLivingEntity composedAttack = (LuaLivingEntity) EntitySpawner.spawn(aiWorld,
                    AssetKey.parse("mymod:composed_attack"), 3, 64, 2, 0, 0).entity;
            require(composedAttack != null, "Composed attack creature must spawn");
            composedAttack.onUpdate();
            require(composedAttack.getTarget() == aiPlayer && aiPlayer.health == 18
                    && lua.get("manualAttackCalls").toint() == 1,
                    "Native target acquisition must feed a manual attack decision");
            LuaLivingEntity composedPath = (LuaLivingEntity) EntitySpawner.spawn(aiWorld,
                    AssetKey.parse("mymod:composed_path"), 2, 64, 2, 0, 0).entity;
            require(composedPath != null, "Composed path creature must spawn");
            int plansBeforePath = aiWorld.pathCalls;
            composedPath.onUpdate();
            composedPath.onUpdate();
            require(aiWorld.pathCalls == plansBeforePath + 1
                    && "moving".equals(composedPath.navigationStatus())
                    && composedPath.navigationWaypoint() != null,
                    "Manual path decisions must use native movement without recalculating every tick");
            require(composedPath.cancelNavigation()
                    && "cancelled".equals(composedPath.navigationStatus()),
                    "A composed path must support explicit cancellation");
            aiWorld.blockPaths = true;
            require(!composedPath.requestNavigation(7, 64, 2, 16)
                    && "blocked".equals(composedPath.navigationStatus()),
                    "An unsolved loaded path must report blocked");
            aiWorld.blockPaths = false;
            aiWorld.unavailable = true;
            int plansBeforeUnavailable = aiWorld.pathCalls;
            require(!composedPath.requestNavigation(7, 64, 2, 16)
                    && "unavailable".equals(composedPath.navigationStatus())
                    && aiWorld.pathCalls == plansBeforeUnavailable,
                    "A missing path area must report unavailable without planning");
            aiWorld.unavailable = false;
            lua.set("stagePathX", org.luaj.vm2.LuaValue.valueOf(2));
            composedPath.onUpdate();
            require("reached".equals(composedPath.navigationStatus()),
                    "A followed ground route must report reached at its final waypoint");
            int plansAtDestination = aiWorld.pathCalls;
            composedPath.onUpdate();
            require(aiWorld.pathCalls == plansAtDestination,
                    "A reached manual destination must not be planned again without a new request");
            try (LuaCallbackScope aiScope = new LuaCallbackScope(true)) {
                LuaTable aiHandle = LuaEntityActionAccess.create(aiScope, null, composedPath);
                require("reached".equals(aiHandle.get("getNavigationStatus").call(aiHandle).tojstring())
                        && aiHandle.get("getNavigationWaypoint").call(aiHandle).isnil()
                        && aiHandle.get("cancelNavigation").call(aiHandle).toboolean(),
                        "Lua handles must expose composed route outcomes and cancellation");
            }
            LuaLivingEntity composedMovement = (LuaLivingEntity) EntitySpawner.spawn(aiWorld,
                    AssetKey.parse("mymod:composed_movement"), 2, 64, 2, 0, 0).entity;
            require(composedMovement != null, "Composed movement creature must spawn");
            composedMovement.onUpdate();
            require(lua.get("manualMoveCalls").toint() == 1,
                    "Manual movement must run independently of native target and path decisions");
            LuaLivingEntity badMovement = (LuaLivingEntity) EntitySpawner.spawn(aiWorld,
                    AssetKey.parse("mymod:bad_composed_movement"), 2, 64, 2, 0, 0).entity;
            require(badMovement != null, "Invalid composed movement creature must spawn");
            badMovement.onUpdate();
            badMovement.onUpdate();
            require(lua.get("manualMoveErrors").toint() == 1,
                    "Invalid movement must stop safely and disable only its failing stage");
            TestWorld world = new TestWorld();
            TestWorld reloadWorld = new TestWorld();
            EntityTypeDefinition loadSpawner = EntityTypeRegistry.find(AssetKey.parse("mymod:load_spawner"));
            LuaPropEntity savedSpawner = new LuaPropEntity(reloadWorld);
            savedSpawner.entityState().attach(loadSpawner);
            savedSpawner.setPosition(4, 64, 4);
            NBTTagCompound loadSpawnerTag = new NBTTagCompound();
            require(savedSpawner.addEntityID(loadSpawnerTag), "Load-spawner fixture must serialize");
            LuaPropEntity restoredSpawner = (LuaPropEntity) EntityList.createEntityFromNBT(loadSpawnerTag, reloadWorld);
            require(reloadWorld.entityJoinedWorld(restoredSpawner), "Load-spawner fixture must join its chunk");
            restoredSpawner.onUpdate();
            require(lua.get("loadSpawnCount").toint() == 1 && reloadWorld.loadedEntityList.size() == 2,
                    "onLoad may spawn an entity without replaying or invalidating world iteration");
            restoredSpawner.onUpdate();
            require(lua.get("loadSpawnCount").toint() == 1,
                    "A spawning onLoad callback must remain exactly once");

            EntityTypeDefinition loadRemover = EntityTypeRegistry.find(AssetKey.parse("mymod:load_remover"));
            LuaPropEntity savedRemover = new LuaPropEntity(reloadWorld);
            savedRemover.entityState().attach(loadRemover);
            savedRemover.setPosition(6, 64, 4);
            NBTTagCompound loadRemoverTag = new NBTTagCompound();
            require(savedRemover.addEntityID(loadRemoverTag), "Load-remover fixture must serialize");
            LuaPropEntity restoredRemover = (LuaPropEntity) EntityList.createEntityFromNBT(loadRemoverTag, reloadWorld);
            require(reloadWorld.entityJoinedWorld(restoredRemover), "Load-remover fixture must join its chunk");
            restoredRemover.onUpdate();
            require(restoredRemover.isDead && lua.get("loadRemoveCount").toint() == 1,
                    "onLoad may remove its own entity without a later activation callback");
            String[] timedKinds = {"timed_prop", "timed_projectile", "timed_pickup"};
            for (int index = 0; index < timedKinds.length; index++) {
                Entity timed = EntitySpawner.spawn(world, AssetKey.parse("mymod:" + timedKinds[index]),
                        1 + index, 64, 12, 0, 0).entity;
                require(timed != null, "Timed " + timedKinds[index] + " fixture must spawn");
                try (LuaCallbackScope timerScope = new LuaCallbackScope(true)) {
                    LuaTable timerHandle = LuaEntityActionAccess.create(timerScope, null, timed);
                    require(timerHandle.get("startTimer").call(timerHandle,
                            org.luaj.vm2.LuaValue.valueOf("kind_check"),
                            org.luaj.vm2.LuaValue.ONE).toboolean(),
                            "Every entity kind must accept behavior timers");
                }
                timed.onUpdate();
            }
            require("prop,projectile,pickup,".equals(lua.get("behaviorKinds").tojstring()),
                    "Behavior timers must advance exactly once for prop, projectile, and pickup kinds");
            EntityTypeDefinition breakableType = EntityTypeRegistry.find(AssetKey.parse("mymod:breakable"));
            LuaPropEntity breakable = (LuaPropEntity) EntitySpawner.spawn(world, breakableType.key,
                    12, 64, 2, 0, 0).entity;
            require(breakable != null && breakable.entityState().health() == 6
                    && "activate:spawn,".equals(lua.get("damageTrace").tojstring()),
                    "A destructible prop must start at full health and activate after spawning");
            require(!breakable.attackEntityFrom(null, 2) && breakable.entityState().health() == 6
                    && lua.get("damageTrace").tojstring().endsWith("before,after:0,"),
                    "Returning zero before damage must cancel the hit and report zero health lost");
            require(breakable.attackEntityFrom(null, 4) && breakable.entityState().health() == 3
                    && lua.get("damageTrace").tojstring().endsWith("before,after:3,"),
                    "Pre-damage changes must apply to saved prop health");
            NBTTagCompound woundedTag = new NBTTagCompound();
            require(breakable.addEntityID(woundedTag)
                    && ((LuaPropEntity) EntityList.createEntityFromNBT(woundedTag, world))
                            .entityState().health() == 3,
                    "Nonliving health must survive native entity serialization");
            EntityLifecycleCallbacks.afterUpdate(breakable, true, breakable.ticksExisted);
            require(lua.get("damageTrace").tojstring().endsWith("deactivate:simulation_paused,"),
                    "A skipped simulation step must deactivate an active entity");
            breakable.onUpdate();
            require(lua.get("damageTrace").tojstring().endsWith("activate:simulation_resumed,"),
                    "The next simulated tick must reactivate a paused entity");
            EntityLifecycleCallbacks.chunkUnloaded(world.chunk);
            require(lua.get("damageTrace").tojstring().endsWith("deactivate:chunk_unload,"),
                    "Chunk unload must deactivate without removing the entity");
            breakable.onUpdate();
            require(lua.get("damageTrace").tojstring().endsWith("activate:chunk_load,"),
                    "A reloaded chunk must activate its retained entity once");
            require(breakable.attackEntityFrom(null, 4) && breakable.isDead
                    && lua.get("damageTrace").tojstring().endsWith(
                            "before,death,after:3,deactivate:death,remove:death,"),
                    "Fatal prop damage must deliver death, post-damage, deactivation and removal once");
            String completedDamageTrace = lua.get("damageTrace").tojstring();
            require(!breakable.attackEntityFrom(null, 4)
                    && completedDamageTrace.equals(lua.get("damageTrace").tojstring()),
                    "Dead props must reject repeated hits without duplicate callbacks");
            int itemEntitiesBeforeKill = countItemEntities(world, 264);
            LuaPropEntity killable = (LuaPropEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:killable_prop"), 12, 64, 3, 0, 0).entity;
            require(killable != null && killable.attackEntityFrom(null, 1) && killable.isDead
                    && lua.get("killAccepted").toboolean()
                    && "death,remove:death".equals(lua.get("killTrace").tojstring()),
                    "A healthless prop kill must run the death and removal lifecycle exactly once");
            require(countItemEntities(world, 264) == itemEntitiesBeforeKill + 1
                    && hasItemEntity(world, 264, 2),
                    "A healthless prop kill must sample and spawn its declared death drops");
            int diamondsBeforeManualLoot = countItemEntities(world, 264);
            int redstoneBeforeManualLoot = countItemEntities(world, 331);
            LuaPropEntity manualLoot = (LuaPropEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:manual_loot_prop"), 12, 64, 4, 0, 0).entity;
            require(manualLoot != null && !manualLoot.attackEntityFrom(null, 1) && manualLoot.isDead
                    && lua.get("manualLootAccepted").toboolean()
                    && lua.get("manualLootDeaths").toint() == 0,
                    "Explicit loot emission followed by removal must not enter the death lifecycle");
            require(countItemEntities(world, 264) == diamondsBeforeManualLoot + 2
                    && countItemEntities(world, 331) == redstoneBeforeManualLoot + 1
                    && hasItemEntity(world, 331, 3),
                    "Each dropLoot call must resample declared loot and dropItem must emit its requested stack");
            int swordId = Item.swordSteel.shiftedIndex;
            int swordEntitiesBefore = countItemEntities(world, swordId);
            int swordQuantityBefore = countItemQuantity(world, swordId);
            LuaPropEntity stackLimitedLoot = (LuaPropEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:stack_limited_loot"), 12, 64, 5, 0, 0).entity;
            require(stackLimitedLoot != null && EntityLoot.dropLoot(stackLimitedLoot),
                    "A stack-limited declared drop must execute");
            require(countItemEntities(world, swordId) == swordEntitiesBefore + 3
                    && countItemQuantity(world, swordId) == swordQuantityBefore + 3
                    && hasItemEntity(world, swordId, 1),
                    "Declared loot totals must split into legal native stack sizes");
            LuaPropEntity failedDamage = (LuaPropEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:failed_damage"), 13, 64, 2, 0, 0).entity;
            require(failedDamage != null && !failedDamage.attackEntityFrom(null, 2)
                    && failedDamage.entityState().health() == 6
                    && failedDamage.attackEntityFrom(null, 2)
                    && failedDamage.entityState().health() == 4
                    && lua.get("failedDamageCalls").toint() == 1
                    && lua.get("afterFailedDamageCalls").toint() == 2,
                    "A failing pre-damage callback must cancel one hit without disabling post-damage");
            TestPlayer collector = new TestPlayer(world);
            for (int slot = 0; slot < collector.inventory.mainInventory.length; slot++) {
                collector.inventory.mainInventory[slot] = new ItemStack(Block.stone, 64);
            }
            collector.inventory.mainInventory[0] = new ItemStack(264, 63, 0);
            LuaPickupEntity partialDrop = new LuaPickupEntity(world);
            partialDrop.initializeItem(pickup);
            partialDrop.setPosition(2, 64, 2);
            require(world.entityJoinedWorld(partialDrop), "Pickup must join a native chunk");
            require(partialDrop.collectInto(collector) && !partialDrop.isDead && partialDrop.item.stackSize == 1,
                    "Partial inventory acceptance must leave the remaining stack in the world");
            require(lua.get("pickupOrder").tojstring().isEmpty(),
                    "Partial collection must not fire full-collection or removal callbacks");
            collector.inventory.mainInventory[1] = null;
            int soundsBeforePickup = presentationEvents.size();
            require(partialDrop.collectInto(collector) && partialDrop.isDead,
                    "The rest of a partially collected stack must enter the inventory");
            partialDrop.setEntityDead();
            require("pickup,collected".equals(lua.get("pickupOrder").tojstring()),
                    "Full collection must call onPickup before onRemove exactly once");
            require(presentationEvents.size() == soundsBeforePickup + 1,
                    "Full collection must emit the pickup sound binding exactly once");
            lua.set("pickupOrder", org.luaj.vm2.LuaValue.valueOf(""));
            LuaPickupEntity destroyedDrop = new LuaPickupEntity(world);
            destroyedDrop.initializeItem(pickup);
            require(destroyedDrop.attackEntityFrom(null, 5) && destroyedDrop.isDead
                    && "destroyed".equals(lua.get("pickupOrder").tojstring()),
                    "Native pickup damage must report an accepted hit and a destroyed removal reason");
            EntitySpawner.Result spawned = EntitySpawner.spawn(world, lamp.key, 2, 64, 2, 0, 0);
            require(spawned.entity != null && lua.get("spawnCount").toint() == 1,
                    "New instances must dispatch onSpawn only after insertion");
            ((LuaPropEntity) spawned.entity).entityState().removalReason("explicit");
            spawned.entity.setEntityDead();
            require("explicit".equals(lua.get("removeReason").tojstring()),
                    "Removal must deliver the explicit reason");
            LuaLivingEntity nearTarget = (LuaLivingEntity) EntitySpawner.spawn(world, creature.key,
                    4, 64, 2, 0, 0).entity;
            LuaLivingEntity farTarget = (LuaLivingEntity) EntitySpawner.spawn(world, creature.key,
                    7, 64, 2, 0, 0).entity;
            require(nearTarget != null && farTarget != null, "Living targets must join the native chunk");
            collector.setPosition(3, 64, 2);
            collector.username = "collector";
            require(world.entityJoinedWorld(collector), "Projectile owner must join the native chunk");
            TestPlayer rider = new TestPlayer(world);
            rider.username = "rider";
            rider.setPosition(15, 64, 3);
            require(world.entityJoinedWorld(rider), "Mount fixture passenger must join the world");
            LuaLivingEntity capable = (LuaLivingEntity) EntitySpawner.spawn(world, stageSix.key,
                    15, 64, 2, 0, 0).entity;
            require(capable != null, "Capability fixture must spawn");
            try (LuaCallbackScope capabilityScope = new LuaCallbackScope(true)) {
                LuaTable capableHandle = LuaEntityActionAccess.create(capabilityScope, null, capable);
                LuaTable ownerHandle = LuaEntityActionAccess.create(capabilityScope, null, collector);
                LuaTable riderHandle = LuaEntityActionAccess.create(capabilityScope, null, rider);
                lua.get("exerciseStageSix").invoke(org.luaj.vm2.LuaValue.varargsOf(
                        new org.luaj.vm2.LuaValue[]{capableHandle, ownerHandle, riderHandle}));
            }
            LuaPropEntity structured = (LuaPropEntity) EntitySpawner.spawn(world, stageSeven.key,
                    10, 64, 2, 0, 0).entity;
            LuaPropEntity referenceTarget = (LuaPropEntity) EntitySpawner.spawn(world, crate.key,
                    11, 64, 2, 0, 0).entity;
            require(structured != null, "Structured persistence fixture must spawn");
            require(referenceTarget != null, "Stable-reference target must spawn");
            try (LuaCallbackScope dataScope = new LuaCallbackScope(true)) {
                LuaTable structuredHandle = LuaEntityActionAccess.create(dataScope, null, structured);
                LuaTable targetHandle = LuaEntityActionAccess.create(dataScope, null, referenceTarget);
                lua.get("exerciseStageSeven").invoke(org.luaj.vm2.LuaValue.varargsOf(
                        new org.luaj.vm2.LuaValue[]{structuredHandle, targetHandle}));
            }
            long snapshotRevision = structured.entityState().data().revision();
            Map<String, WireValue> networkSnapshot = structured.entityState().data().networkSnapshot(stageSeven);
            structured.entityState().data().takeNetworkDelta(stageSeven);
            LuaPropEntity networkClone = new LuaPropEntity(null);
            networkClone.entityState().attachNetwork(stageSeven, structured.entityState().identity(),
                    snapshotRevision, networkSnapshot);
            require(networkClone.entityState().identity().equals(structured.entityState().identity())
                    && networkClone.entityState().data().networkSnapshot(stageSeven).equals(networkSnapshot),
                    "A full entity network snapshot must preserve stable identity and declared data");
            structured.entityState().data().set(stageSeven, "cargo", LuaValue.NIL);
            EntityDataDelta networkDelta = structured.entityState().data().takeNetworkDelta(stageSeven);
            require(networkDelta != null && networkDelta.baseRevision == snapshotRevision
                    && networkClone.entityState().applyNetworkDelta(networkDelta.baseRevision,
                            networkDelta.revision, networkDelta.changedFields)
                    && networkClone.entityState().data().networkSnapshot(stageSeven)
                            .equals(structured.entityState().data().networkSnapshot(stageSeven)),
                    "A matching entity delta must apply atomically and advance its revision");
            Map<String, WireValue> beforeGap = networkClone.entityState().data().networkSnapshot(stageSeven);
            require(!networkClone.entityState().applyNetworkDelta(networkDelta.baseRevision,
                    networkDelta.revision + 1, networkDelta.changedFields)
                    && networkClone.entityState().data().networkSnapshot(stageSeven).equals(beforeGap),
                    "A revision gap must leave the last complete entity state intact");
            LuaTable restoredCargoValue = new LuaTable();
            restoredCargoValue.set("id", 264);
            restoredCargoValue.set("count", 2);
            restoredCargoValue.set("damage", 0);
            structured.entityState().data().set(stageSeven, "cargo", restoredCargoValue);
            NBTTagCompound structuredTag = new NBTTagCompound();
            require(structured.addEntityID(structuredTag), "Structured data must use native entity persistence");
            structuredTag.getCompoundTag("BetaMoonData").setString("future_field", "preserved");
            LuaPropEntity restoredStructured = (LuaPropEntity) EntityList.createEntityFromNBT(structuredTag, world);
            require(restoredStructured != null && restoredStructured.entityState().definition() == stageSeven,
                    "Compatible structured data must bind after loading");
            try (LuaCallbackScope dataScope = new LuaCallbackScope(true)) {
                LuaTable restoredHandle = LuaEntityActionAccess.create(dataScope, null, restoredStructured);
                LuaTable data = restoredHandle.get("data").checktable();
                require(data.get("get").call(data, org.luaj.vm2.LuaValue.valueOf("route"))
                        .get(2).get("z").toint() == 4,
                        "Structured lists and vectors must survive serialization");
                require(data.get("get").call(data, org.luaj.vm2.LuaValue.valueOf("cargo"))
                        .get("count").toint() == 2,
                        "Saved item stacks must survive serialization");
                LuaValue reference = data.get("get").call(data,
                        org.luaj.vm2.LuaValue.valueOf("target"));
                require(reference.get("isLoaded").call(reference).toboolean(),
                        "Stable entity references must resolve while their target is loaded");
            }
            world.loadedEntityList.remove(referenceTarget);
            try (LuaCallbackScope dataScope = new LuaCallbackScope(true)) {
                LuaTable restoredHandle = LuaEntityActionAccess.create(dataScope, null, restoredStructured);
                LuaTable data = restoredHandle.get("data").checktable();
                LuaValue reference = data.get("get").call(data,
                        org.luaj.vm2.LuaValue.valueOf("target"));
                require(!reference.get("isLoaded").call(reference).toboolean()
                        && reference.get("resolve").call(reference).isnil()
                        && reference.get("token").tojstring().endsWith(referenceTarget.entityState().identity()),
                        "Cross-chunk references must retain identity while their target is unloaded");
            }
            world.loadedEntityList.add(referenceTarget);
            NBTTagCompound preservedTag = new NBTTagCompound();
            require(restoredStructured.addEntityID(preservedTag)
                    && "preserved".equals(preservedTag.getCompoundTag("BetaMoonData")
                            .getString("future_field")),
                    "Unknown saved fields must survive binding and resaving");
            preservedTag.getCompoundTag("BetaMoonData").getCompoundTag("cargo")
                    .setInteger("Id", 32000);
            LuaPropEntity missingItem = (LuaPropEntity) EntityList.createEntityFromNBT(preservedTag, world);
            require(missingItem != null && missingItem.entityState().definition() == stageSeven,
                    "A missing saved item type must not make the entire entity dormant");
            try (LuaCallbackScope dataScope = new LuaCallbackScope(true)) {
                LuaTable missingHandle = LuaEntityActionAccess.create(dataScope, null, missingItem);
                LuaValue cargo = missingHandle.get("data").get("get").call(
                        missingHandle.get("data"), org.luaj.vm2.LuaValue.valueOf("cargo"));
                require(cargo.get("id").toint() == 32000 && !cargo.get("available").toboolean(),
                        "Missing item stacks must remain inspectable and recover when the item returns");
            }
            try (ScriptExecutionScope owner = ScriptExecutionScope.open("entities.lua");
                    ScriptEntityScope scope = ScriptEntityScope.open("entities.lua")) {
                try {
                    lua.load("betamoon.entities:add{key='mymod:stage_seven',kind='prop',"
                            + "appearance={model='minecraft:block/torch',texture='test.png'},"
                            + "data={route={type='list',maxLength=2,element={type='vector'}},"
                            + "profile={type='record',fields={label={type='string'}}},"
                            + "cargo={type='item_stack'},target={type='entity_reference'}}}").call();
                    throw new AssertionError("A reduced saved-list bound must require explicit conversion");
                } catch (org.luaj.vm2.LuaError expected) {
                    require(EntityTypeRegistry.find(stageSeven.key) == stageSeven,
                            "Rejected definition changes must leave the complete registry intact");
                }
            }
            require("exit:idle,enter:active,".equals(lua.get("behaviorTrace").tojstring()),
                    "State transitions must call exit before enter exactly once");
            capable.onUpdate();
            require(lua.get("behaviorTrace").tojstring().endsWith("timer:heartbeat:true,"),
                    "A repeating timer must fire on its initial deadline");
            NBTTagCompound capabilityTag = new NBTTagCompound();
            require(capable.addEntityID(capabilityTag), "Capability state must use normal entity persistence");
            LuaLivingEntity restoredCapability = (LuaLivingEntity) EntityList.createEntityFromNBT(
                    capabilityTag, world);
            require(restoredCapability != null && restoredCapability.ridingEntity == null
                    && restoredCapability.riddenByEntity == null,
                    "Single-seat relationships must remain transient across save and load");
            try (LuaCallbackScope restoredScope = new LuaCallbackScope(true)) {
                LuaTable restoredHandle = LuaEntityActionAccess.create(restoredScope, null, restoredCapability);
                require(restoredHandle.get("getInventoryStack").call(restoredHandle,
                        org.luaj.vm2.LuaValue.ONE).get("count").toint() == 1,
                        "Container contents must survive entity serialization");
                require(restoredHandle.get("getEquipmentStack").call(restoredHandle,
                        org.luaj.vm2.LuaValue.valueOf("hand")).get("id").toint() == 264,
                        "Logical equipment must survive entity serialization");
                org.luaj.vm2.LuaValue resolvedOwner = restoredHandle.get("getOwner").call(restoredHandle);
                require("player:collector".equals(restoredHandle.get("getOwnerIdentity").call(restoredHandle)
                        .tojstring()) && "collector".equals(resolvedOwner.get("getName").call(resolvedOwner)
                                .tojstring()),
                        "Ownership must persist as a stable token and resolve loaded owners");
                require("active".equals(restoredHandle.get("getBehaviorState").call(restoredHandle).tojstring())
                        && restoredHandle.get("getTimer").call(restoredHandle,
                                org.luaj.vm2.LuaValue.valueOf("pulse")).toint() == 1,
                        "Behavior state and remaining simulation time must survive entity serialization");
            }
            restoredCapability.onUpdate();
            require(lua.get("behaviorTrace").tojstring().endsWith("timer:pulse:false,"),
                    "A restored one-shot timer must fire once at its remaining deadline");
            try (LuaCallbackScope timerScope = new LuaCallbackScope(true)) {
                LuaTable timerHandle = LuaEntityActionAccess.create(timerScope, null, restoredCapability);
                require(timerHandle.get("cancelTimer").call(timerHandle,
                        org.luaj.vm2.LuaValue.valueOf("heartbeat")).toboolean()
                        && timerHandle.get("getTimer").call(timerHandle,
                                org.luaj.vm2.LuaValue.valueOf("heartbeat")).isnil(),
                        "Cancelling a repeating timer must remove it immediately");
            }
            require(restoredCapability.attackEntityFrom(null, 100)
                    && restoredCapability.entityState().inventory(restoredCapability, stageSix)
                            .getStackInSlot(0) == null
                    && restoredCapability.entityState().inventory(restoredCapability, stageSix)
                            .equipment(betamoon.entity.EntityEquipmentDefinition.Slot.HAND) == null,
                    "Death must drop and clear only the configured container and equipment contents");
            LuaProjectileEntity defaultShot = (LuaProjectileEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:default_shot"), 2, 64.5, 2, -90, 0, collector).entity;
            require(defaultShot != null, "Projectile must spawn before its owner and targets");
            require(world.getEntitiesWithinAABBExcludingEntity(defaultShot,
                    defaultShot.boundingBox.addCoord(4, 0, 0).expand(1, 1, 1)).contains(nearTarget),
                    "Native chunk entity query must include the nearest target");
            require(nearTarget.boundingBox.expand(0.3, 0.3, 0.3).func_1169_a(
                    Vec3D.createVector(2, 64.5, 2), Vec3D.createVector(6, 64.5, 2)) != null,
                    "Native target hitbox must intersect the projectile segment");
            int soundsBeforeImpact = presentationEvents.size();
            defaultShot.onUpdate();
            require(defaultShot.isDead && nearTarget.health == creature.living.maxHealth - 3
                    && farTarget.health == creature.living.maxHealth && collector.health == 20,
                    "Projectile must hit the nearest target and exclude its owner during grace ticks: "
                            + defaultShot.isDead + ", " + nearTarget.health + ", " + farTarget.health
                            + ", " + collector.health + ", x=" + defaultShot.posX
                            + ", bounds=" + nearTarget.boundingBox.minY + ".." + nearTarget.boundingBox.maxY);
            require(presentationEvents.size() == soundsBeforeImpact + 1,
                    "A projectile collision must emit its impact binding exactly once");
            require("projectile".equals(lua.get("lastDamageCause").tojstring())
                    && "mymod:default_shot".equals(lua.get("lastDirectSource").tojstring())
                    && lua.get("lastHealthLost").toint() == 3,
                    "Living damage callbacks must distinguish the projectile from its responsible attacker");
            LuaLivingEntity scriptDamaged = (LuaLivingEntity) EntitySpawner.spawn(world, creature.key,
                    14, 64, 2, 0, 0).entity;
            require(scriptDamaged != null && scriptDamaged.attackEntityFrom(null, 7)
                    && scriptDamaged.health == 32 && lua.get("lastHealthLost").toint() == 4,
                    "Living pre-damage changes must pass through native health handling");
            LuaLivingEntity mortal = (LuaLivingEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:mortal"), 5, 64, 8, 0, 0).entity;
            require(mortal != null && mortal.attackEntityFrom(null, 5) && mortal.isDead
                    && lua.get("mortalHealthLost").toint() == 4
                    && "before,death,after,deactivate:death,remove:death,"
                            .equals(lua.get("livingTrace").tojstring()),
                    "Fatal living damage must preserve native death order and defer removal until after damage");
            NBTTagCompound deadTag = new NBTTagCompound();
            mortal.writeEntityToNBT(deadTag);
            LuaLivingEntity restoredDead = new LuaLivingEntity(world);
            restoredDead.readEntityFromNBT(deadTag);
            require(restoredDead.entityState().deathNotified()
                    && "death".equals(restoredDead.entityState().removalReason()),
                    "Saved living death state must prevent replaying death rewards and callbacks");
            restoredDead.onDeath(null);
            require("before,death,after,deactivate:death,remove:death,"
                            .equals(lua.get("livingTrace").tojstring()),
                    "Restoring a dead living entity must not repeat onDeath");
            LuaProjectileEntity selfRemovingShot = (LuaProjectileEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:self_removing_shot"), 2, 64.5, 2, -90, 0, collector).entity;
            require(selfRemovingShot != null, "Callback projectile must spawn");
            selfRemovingShot.onUpdate();
            require(selfRemovingShot.isDead && nearTarget.health == creature.living.maxHealth - 3,
                    "Removal in onImpact must stop default damage and further collision handling");
            LuaLivingEntity multipart = (LuaLivingEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:multipart_creature"), 9, 64, 2, 0, 0).entity;
            require(multipart != null, "Multipart living target must spawn");
            multipart.onUpdate();
            LuaEntityPart rootPart = null;
            for (Object candidate : world.loadedEntityList) {
                if (candidate instanceof LuaEntityPart && ((LuaEntityPart) candidate).parent() == multipart) {
                    rootPart = (LuaEntityPart) candidate;
                }
            }
            require(rootPart != null && rootPart.canBeCollidedWith(),
                    "A named physical part must join the native collision list");
            require(rootPart.attackEntityFrom(null, 5) && multipart.health == 10,
                    "Part damage must pass through its own callback before reaching the parent");
            require(!rootPart.attackEntityFrom(null, 9) && multipart.isDead && multipart.health == 10,
                    "A part callback removing its parent must not apply damage afterward");
            LuaLivingEntity isolated = (LuaLivingEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:isolated_part"), 11, 64, 2, 0, 0).entity;
            require(isolated != null, "Callback isolation target must spawn");
            isolated.onUpdate();
            LuaEntityPart isolatedPart = null;
            for (Object candidate : world.loadedEntityList) {
                if (candidate instanceof LuaEntityPart && ((LuaEntityPart) candidate).parent() == isolated) {
                    isolatedPart = (LuaEntityPart) candidate;
                }
            }
            require(isolatedPart != null, "Callback isolation target must have a physical part");
            require(!isolatedPart.attackEntityFrom(null, 5) && isolatedPart.attackEntityFrom(null, 5)
                    && isolated.health == 7 && lua.get("partDamageCalls").toint() == 1,
                    "A failed part damage callback must cancel its current hit then fall back to native damage");
            require(isolatedPart.interact(collector) && lua.get("partInteractions").toint() == 1,
                    "Part interaction must keep working after part damage fails");
            TestWorld stageWorld = new TestWorld();
            LuaPropEntity stageEntity = (LuaPropEntity) EntitySpawner.spawn(stageWorld,
                    stageFour.key, 10, 64, 10, 0, 0).entity;
            require(stageEntity != null && !stageEntity.canBeCollidedWith(),
                    "A disabled body target must retain its entity without entering ray targeting");
            stageEntity.onUpdate();
            LuaEntityPart damagePart = null;
            LuaEntityPart interactionPart = null;
            for (Object candidate : stageWorld.loadedEntityList) {
                if (candidate instanceof LuaEntityPart && ((LuaEntityPart) candidate).parent() == stageEntity) {
                    LuaEntityPart part = (LuaEntityPart) candidate;
                    if ("hit".equals(part.roleName())) {
                        damagePart = part;
                    } else if ("interaction".equals(part.roleName())) {
                        interactionPart = part;
                    }
                }
            }
            require(damagePart != null && interactionPart != null
                    && damagePart.posX == 11 && interactionPart.posX == 9,
                    "Damage and interaction shapes must create separate positioned proxies");
            TestPlayer stagePlayer = new TestPlayer(stageWorld);
            stagePlayer.setPosition(10, 64, 10);
            require(stageWorld.entityJoinedWorld(stagePlayer), "Sensor fixture player must join its native chunk");
            require(!damagePart.interact(stagePlayer)
                    && interactionPart.interact(stagePlayer)
                    && lua.get("stageFourInteractions").toint() == 1,
                    "Only the interaction shape may invoke its part interaction callback");
            require(!interactionPart.attackEntityFrom(stagePlayer, 3)
                    && stageEntity.entityState().health() == 10,
                    "An interaction-only shape must reject damage");
            LuaCallbackScope stageScope = new LuaCallbackScope(true);
            LuaTable stageHandle = LuaEntityActionAccess.create(stageScope, null, stageEntity);
            require(stageHandle.get("setInteractionOffset").invoke(org.luaj.vm2.LuaValue.varargsOf(
                    new org.luaj.vm2.LuaValue[]{stageHandle, org.luaj.vm2.LuaValue.valueOf("root"),
                            org.luaj.vm2.LuaValue.valueOf(-2), org.luaj.vm2.LuaValue.valueOf(0.5),
                            org.luaj.vm2.LuaValue.ZERO})).arg1().toboolean()
                    && stageHandle.get("setSensorOffset").invoke(org.luaj.vm2.LuaValue.varargsOf(
                    new org.luaj.vm2.LuaValue[]{stageHandle, org.luaj.vm2.LuaValue.valueOf("nearby"),
                            org.luaj.vm2.LuaValue.ZERO, org.luaj.vm2.LuaValue.valueOf(0.5),
                            org.luaj.vm2.LuaValue.ZERO})).arg1().toboolean(),
                    "Lua must move interaction and sensing shapes independently");
            stageScope.close();
            require(interactionPart.posX == 8 && damagePart.posX == 11,
                    "Moving an interaction shape must preserve the damage shape position");
            stageEntity.onUpdate();
            stageEntity.onUpdate();
            require("enter,stay,".equals(lua.get("sensorTrace").tojstring()),
                    "A sensor must report enter once and stay on later scan intervals");
            stagePlayer.setPosition(15, 64, 10);
            stageEntity.onUpdate();
            require(lua.get("sensorTrace").tojstring().endsWith("leave:left,"),
                    "Leaving a sensor normally must report the left reason");
            stagePlayer.setPosition(10, 64, 10);
            stageEntity.onUpdate();
            stagePlayer.setEntityDead();
            stageEntity.onUpdate();
            require(lua.get("sensorTrace").tojstring().endsWith("enter,leave:removed,"),
                    "A removed occupant must leave its sensors with an explicit reason");
            TestPlayer unloadingPlayer = new TestPlayer(stageWorld);
            unloadingPlayer.setPosition(10, 64, 10);
            require(stageWorld.entityJoinedWorld(unloadingPlayer), "Unload sensor fixture must join the world");
            stageEntity.onUpdate();
            EntityLifecycleEvents.chunkUnloaded(stageWorld.chunk);
            require(lua.get("sensorTrace").tojstring().endsWith("enter,leave:chunk_unload,"),
                    "Parent chunk deactivation must flush sensor occupants with its unload reason");
            LuaProjectileEntity proxyShot = (LuaProjectileEntity) EntitySpawner.spawn(stageWorld,
                    AssetKey.parse("mymod:default_shot"), 7, 64.5, 10, -90, 0).entity;
            require(proxyShot != null, "Interaction-proxy projectile fixture must spawn");
            proxyShot.onUpdate();
            require(proxyShot.isDead && stageEntity.entityState().health() == 7,
                    "Projectile sweeps must ignore interaction proxies and damage the later hit proxy");
            presentationEvents.clear();
            TestWorld presentationWorld = new TestWorld();
            LuaPropEntity presented = (LuaPropEntity) EntitySpawner.spawn(presentationWorld,
                    presentation.key, 5, 64, 5, 0, 0).entity;
            require(presented != null, "Presentation fixture must spawn");
            presented.onGround = true;
            presented.onUpdate();
            presented.setPosition(5.2, 64, 5);
            presented.onGround = true;
            presented.onUpdate();
            require(presentationEvents.size() == 2,
                    "A due ambient sound and completed grounded step must each emit once");
            require(presented.attackEntityFrom(null, 1) && presentationEvents.size() == 3,
                    "Accepted nonfatal damage must emit the hurt binding once");
            LuaCallbackScope presentationScope = new LuaCallbackScope(true);
            LuaTable presentationHandle = LuaEntityActionAccess.create(presentationScope, null, presented);
            lua.get("drivePresentation").call(presentationHandle);
            require(presentationEvents.size() == 4,
                    "Explicit entity sound playback must emit one positional presentation event");
            require(!presented.entityState().presentation().visible(presentation.render)
                    && presented.entityState().presentation().offsetX() == 1
                    && presented.entityState().presentation().rotationRoll() == 30
                    && presented.entityState().presentation().scaleZ() == 4,
                    "Per-instance visual overrides must remain separate from entity transforms");
            ModelAppearance presentedAppearance = EntityVisuals.get(presentation);
            ModelPose selectedPose = presentedAppearance.evaluate("entity", presented.ticksExisted,
                    0, presented.posX, presented.posY, presented.posZ,
                    presented.entityState().presentation().animation());
            require(Math.abs(selectedPose.getRotation("head").toEuler().z - 90) < 0.001,
                    "A selected clip must evaluate from its independent per-instance start time");
            LuaPropEntity unselected = (LuaPropEntity) EntitySpawner.spawn(presentationWorld,
                    presentation.key, 6, 64, 5, 0, 0).entity;
            ModelPose defaultPose = presentedAppearance.evaluate("entity", unselected.ticksExisted,
                    0, unselected.posX, unselected.posY, unselected.posZ,
                    unselected.entityState().presentation().animation());
            require(Math.abs(defaultPose.getRotation("head").toEuler().z) < 0.001,
                    "Selecting an animation on one instance must not change another instance's pose");
            NBTTagCompound presentationTag = new NBTTagCompound();
            require(presented.addEntityID(presentationTag), "Presentation fixture must serialize normally");
            LuaPropEntity restoredPresentation = (LuaPropEntity) EntityList.createEntityFromNBT(
                    presentationTag, presentationWorld);
            require(restoredPresentation.entityState().presentation().animation() == null
                    && restoredPresentation.entityState().presentation().visible(presentation.render)
                    && restoredPresentation.entityState().presentation().offsetX() == 0,
                    "Animation and visual overrides must remain transient across save and load");
            presentationHandle.get("resetVisuals").call(presentationHandle);
            require(presented.entityState().presentation().visible(presentation.render)
                    && presented.entityState().presentation().scaleX() == 1,
                    "Resetting visual overrides must restore declaration defaults");
            presentationScope.close();
            require(presented.attackEntityFrom(null, 10) && presented.isDead
                    && presentationEvents.size() == 5,
                    "Fatal damage must emit death without an additional hurt sound");
            LuaPropEntity movingCrate = new LuaPropEntity(world);
            movingCrate.entityState().attach(crate);
            movingCrate.setPosition(2, 64, 2);
            movingCrate.motionX = 0.2;
            movingCrate.onUpdate();
            require(movingCrate.posX > 2, "Dynamic props must move through native collision handling");
            LuaLivingEntity stationaryCreature = new LuaLivingEntity(world);
            stationaryCreature.entityState().attach(pilot);
            stationaryCreature.setPosition(2, 64, 2);
            stationaryCreature.motionX = 0.2;
            stationaryCreature.onUpdate();
            require(stationaryCreature.posX == 2, "Static living entities must keep their position");
            stationaryCreature.attackEntityFrom(null, 100);
            require(lua.get("deathCount").toint() == 1,
                    "Living death must deliver one post-death callback");
            EntityTypeDefinition scriptedCrate = EntityTypeRegistry.find(AssetKey.parse("mymod:scripted_crate"));
            LuaPropEntity scriptedInstance = new LuaPropEntity(world);
            scriptedInstance.entityState().attach(scriptedCrate);
            scriptedInstance.setPosition(2, 64, 2);
            scriptedInstance.motionX = 0.2;
            scriptedInstance.onUpdate();
            require(scriptedInstance.posX == 2 && scriptedInstance.ticksExisted == 1,
                    "Manual lifecycle must tick without automatic movement");
            LuaCallbackScope worldScope = new LuaCallbackScope(true);
            LuaTable worldHandle = LuaWorldActionAccess.create(worldScope, world, 2, 64, 2);
            require(worldHandle.get("getDifficulty").call(worldHandle).toint() == world.difficultySetting,
                    "AI world access must report native difficulty");
            require(worldHandle.get("getSpawnPoint").call(worldHandle).get("y").isnumber(),
                    "AI world access must expose spawn coordinates");
            LuaTable movingHandle = LuaEntityActionAccess.create(worldScope, null, movingCrate);
            require(movingHandle.get("setPosition").invoke(org.luaj.vm2.LuaValue.varargsOf(
                    new org.luaj.vm2.LuaValue[]{movingHandle, org.luaj.vm2.LuaValue.valueOf(3),
                            org.luaj.vm2.LuaValue.valueOf(64), org.luaj.vm2.LuaValue.valueOf(3)}))
                    .arg1().toboolean(), "Lua must move an entity to a loaded position");
            require(movingCrate.posX == 3, "Position writes must update the live entity");
            require(movingHandle.get("facePosition").invoke(org.luaj.vm2.LuaValue.varargsOf(
                    new org.luaj.vm2.LuaValue[]{movingHandle, org.luaj.vm2.LuaValue.valueOf(5),
                            org.luaj.vm2.LuaValue.valueOf(64), org.luaj.vm2.LuaValue.valueOf(3)}))
                    .arg1().toboolean(), "Lua must steer an entity toward a waypoint");
            LuaTable projectileOwner = LuaEntityActionAccess.create(worldScope, null, collector);
            LuaTable projectilePosition = new LuaTable();
            projectilePosition.set("x", 12.0);
            projectilePosition.set("y", 70.0);
            projectilePosition.set("z", 12.0);
            LuaTable projectileOptions = new LuaTable();
            projectileOptions.set("position", projectilePosition);
            projectileOptions.set("owner", projectileOwner);
            LuaValue spawnedProjectileHandle = worldHandle.get("spawnEntity").invoke(
                    LuaValue.varargsOf(new LuaValue[]{worldHandle, LuaValue.valueOf("mymod:default_shot"),
                            projectileOptions})).arg1();
            require(!spawnedProjectileHandle.isnil(), "Lua must spawn a projectile with a live same-world owner");
            LuaValue resolvedProjectileOwner = spawnedProjectileHandle.get("getOwner")
                    .call(spawnedProjectileHandle);
            require("collector".equals(resolvedProjectileOwner.get("getName").call(resolvedProjectileOwner)
                    .tojstring()) && "player:collector".equals(spawnedProjectileHandle.get("getOwnerIdentity")
                            .call(spawnedProjectileHandle).tojstring()),
                    "Lua projectile spawning must expose its launch owner and stable identity");
            LuaTable invalidOwnerOptions = new LuaTable();
            invalidOwnerOptions.set("position", projectilePosition);
            invalidOwnerOptions.set("owner", projectileOwner);
            boolean rejectedPropOwner = false;
            try {
                worldHandle.get("spawnEntity").invoke(LuaValue.varargsOf(new LuaValue[]{worldHandle,
                        LuaValue.valueOf("mymod:lamp"), invalidOwnerOptions}));
            } catch (org.luaj.vm2.LuaError expected) {
                rejectedPropOwner = true;
            }
            require(rejectedPropOwner, "Spawn owners must be rejected for non-projectile types");
            worldScope.close();

            LuaProjectileEntity luaOwnedProjectile = null;
            for (Object candidate : world.loadedEntityList) {
                if (candidate instanceof LuaProjectileEntity && ((Entity) candidate).posX == 12.0
                        && ((Entity) candidate).posY == 70.0 && ((Entity) candidate).posZ == 12.0) {
                    luaOwnedProjectile = (LuaProjectileEntity) candidate;
                    break;
                }
            }
            require(luaOwnedProjectile != null, "The owner-aware Lua spawn must join the native world");
            NBTTagCompound ownedProjectileTag = new NBTTagCompound();
            require(luaOwnedProjectile.addEntityID(ownedProjectileTag),
                    "An owner-aware projectile must retain its native save ID");
            LuaProjectileEntity restoredOwnedProjectile = (LuaProjectileEntity) EntityList.createEntityFromNBT(
                    ownedProjectileTag, world);
            require(restoredOwnedProjectile != null && restoredOwnedProjectile.getOwner() == collector
                    && "player:collector".equals(restoredOwnedProjectile.getOwnerIdentity()),
                    "A player projectile owner must resolve from its stable identity after save/load");

            LuaProjectileEntity entityOwnedProjectile = (LuaProjectileEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:default_shot"), 13, 70, 13, 0, 0, capable).entity;
            require(entityOwnedProjectile != null
                    && ("entity:" + capable.entityState().identity()).equals(entityOwnedProjectile.getOwnerIdentity()),
                    "A BetaMoon entity must be accepted as a stable projectile owner");
            NBTTagCompound entityOwnedProjectileTag = new NBTTagCompound();
            require(entityOwnedProjectile.addEntityID(entityOwnedProjectileTag),
                    "A BetaMoon-owned projectile must serialize");
            LuaProjectileEntity restoredEntityOwnedProjectile = (LuaProjectileEntity) EntityList.createEntityFromNBT(
                    entityOwnedProjectileTag, world);
            require(restoredEntityOwnedProjectile != null && restoredEntityOwnedProjectile.getOwner() == capable,
                    "A loaded BetaMoon projectile owner must resolve by stable instance identity");

            EntityItem transientOwner = new EntityItem(world, 11, 70, 11, new ItemStack(1, 1, 0));
            require(world.entityJoinedWorld(transientOwner), "A native runtime-only projectile owner must join");
            LuaProjectileEntity transientOwnedProjectile = (LuaProjectileEntity) EntitySpawner.spawn(world,
                    AssetKey.parse("mymod:default_shot"), 14, 70, 14, 0, 0, transientOwner).entity;
            require(transientOwnedProjectile != null && transientOwnedProjectile.getOwner() == transientOwner
                    && transientOwnedProjectile.getOwnerIdentity() == null,
                    "A native entity owner must work at runtime without claiming a stable identity");
            NBTTagCompound transientOwnedProjectileTag = new NBTTagCompound();
            require(transientOwnedProjectile.addEntityID(transientOwnedProjectileTag),
                    "A runtime-owned projectile must serialize without its transient owner");
            LuaProjectileEntity restoredTransientProjectile = (LuaProjectileEntity) EntityList.createEntityFromNBT(
                    transientOwnedProjectileTag, world);
            require(restoredTransientProjectile != null && restoredTransientProjectile.getOwner() == null,
                    "An arbitrary native projectile owner must not be reconstructed unsafely after save/load");

            LuaProjectileEntity legacyOwnedProjectile = new LuaProjectileEntity(world);
            legacyOwnedProjectile.entityState().attach(EntityTypeRegistry.find(
                    AssetKey.parse("mymod:default_shot")));
            NBTTagCompound legacyOwnedProjectileTag = new NBTTagCompound();
            require(legacyOwnedProjectile.addEntityID(legacyOwnedProjectileTag),
                    "A legacy owner fixture must retain its projectile save ID");
            legacyOwnedProjectileTag.setString("BetaMoonOwnerName", "collector");
            LuaProjectileEntity restoredLegacyProjectile = (LuaProjectileEntity) EntityList.createEntityFromNBT(
                    legacyOwnedProjectileTag, world);
            require(restoredLegacyProjectile != null && restoredLegacyProjectile.getOwner() == collector
                    && "player:collector".equals(restoredLegacyProjectile.getOwnerIdentity()),
                    "Legacy player owner saves must remain readable");

            TestWorld queryWorld = new TestWorld();
            LuaPropEntity queryCenter = (LuaPropEntity) EntitySpawner.spawn(queryWorld, lamp.key,
                    8.5, 70.5, 8.5, 0, 0).entity;
            LuaPropEntity radialNeighbor = (LuaPropEntity) EntitySpawner.spawn(queryWorld, lamp.key,
                    11.5, 70.5, 8.5, 0, 0).entity;
            LuaPropEntity boxCorner = (LuaPropEntity) EntitySpawner.spawn(queryWorld, lamp.key,
                    11.4, 73.4, 11.4, 0, 0).entity;
            require(queryCenter != null && radialNeighbor != null && boxCorner != null,
                    "Nearby-query fixtures must join the isolated world");
            try (LuaCallbackScope queryScope = new LuaCallbackScope(true)) {
                LuaTable queryWorldHandle = LuaWorldActionAccess.create(queryScope, queryWorld, 8, 70, 8);
                LuaTable worldNearby = queryWorldHandle.get("getNearbyEntities")
                        .call(queryWorldHandle, LuaValue.valueOf(4)).checktable();
                require(worldNearby.length() == 2,
                        "World nearby queries must exclude broad-phase box corners outside the radius: "
                                + worldNearby.length());
                LuaTable queryCenterHandle = LuaEntityActionAccess.create(queryScope, null, queryCenter);
                LuaTable entityNearby = queryCenterHandle.get("getNearbyEntities")
                        .call(queryCenterHandle, LuaValue.valueOf(4)).checktable();
                require(entityNearby.length() == 1
                        && entityNearby.get(1).get("getPosition").call(entityNearby.get(1))
                                .get("x").todouble() == radialNeighbor.posX,
                        "World and entity nearby queries must share spherical distance semantics");
            }
            ItemUseDefinition launcher = new ItemUseDefinition(lua.load("return {use={projectile='mymod:shot'}}")
                    .call());
            require(AssetKey.parse("mymod:shot").equals(launcher.customProjectile),
                    "Item use must accept a custom projectile key");
            LuaLivingEntity pilotInstance = new LuaLivingEntity(null);
            pilotInstance.entityState().attach(pilot);
            LuaCallbackScope callback = new LuaCallbackScope(true);
            LuaTable handle = LuaEntityActionAccess.create(callback, null, pilotInstance);
            require(handle.get("getMaxHealth").call(handle).toint() == pilot.living.maxHealth,
                    "AI access must read the configured health limit");
            require(handle.get("setMovement").invoke(org.luaj.vm2.LuaValue.varargsOf(new org.luaj.vm2.LuaValue[]{
                    handle, org.luaj.vm2.LuaValue.ZERO, org.luaj.vm2.LuaValue.ZERO,
                    org.luaj.vm2.LuaValue.FALSE})).arg1().toboolean(),
                    "Manual AI must accept direct movement input");
            LuaTable memory = handle.get("memory").checktable();
            LuaTable route = lua.load("return {{x=2,y=64,z=2},{x=3,y=64,z=2}}").call().checktable();
            memory.get("set").call(memory, org.luaj.vm2.LuaValue.valueOf("route"), route);
            route.get(1).set("x", 99);
            LuaTable savedRoute = memory.get("get").call(memory, org.luaj.vm2.LuaValue.valueOf("route"))
                    .checktable();
            require(savedRoute.get(1).get("x").toint() == 2,
                    "Transient AI memory must copy route plans on write");
            savedRoute.get(1).set("x", 88);
            require(memory.get("get").call(memory, org.luaj.vm2.LuaValue.valueOf("route"))
                    .get(1).get("x").toint() == 2, "Transient AI memory must copy route plans on read");
            callback.close();
            try {
                handle.get("getPosition").call(handle);
                throw new AssertionError("Entity handles must expire after their callback");
            } catch (org.luaj.vm2.LuaError expected) {
                // Live handles cannot escape their callback.
            }
            try {
                EntityTypeRegistry.publish("other.lua", Collections.singletonMap(lamp.key, lamp));
                throw new AssertionError("Another script must not claim an existing type");
            } catch (IllegalArgumentException expected) {
                require(EntityTypeRegistry.find(lamp.key) == lamp, "Rejected publication must leave the type intact");
            }

            LuaPropEntity first = new LuaPropEntity(null);
            first.entityState().attach(lamp);
            first.entityState().data().set(lamp, "uses", org.luaj.vm2.LuaValue.valueOf(7));
            NBTTagCompound tag = new NBTTagCompound();
            require(first.addEntityID(tag), "The prop bridge must have a native save ID");
            require(tag.getString("id").equals("BetaMoonProp"), "The prop save ID must be stable");
            String identity = first.entityState().identity();
            EntityTypeRegistry.retainOwners(Collections.emptySet());
            Entity loaded = EntityList.createEntityFromNBT(tag, null);
            require(loaded instanceof LuaPropEntity, "Minecraft must restore the prop bridge class");
            LuaPropEntity missing = (LuaPropEntity) loaded;
            require(missing.entityState().definition() == null, "Missing definitions must leave saved entities dormant");
            require(missing.entityState().identity().equals(identity), "Saved identity must survive a load");
            EntityTypeRegistry.publish("entities.lua", Collections.singletonMap(lamp.key, lamp));
            require(missing.entityState().definition() == lamp, "Restored definitions must reactivate saved entities");
            require(missing.entityState().data().get(lamp, "uses").toint() == 7,
                    "Entity data must survive a missing-definition interval");
            LuaPropEntity reloaded = (LuaPropEntity) EntityList.createEntityFromNBT(tag, world);
            reloaded.onUpdate();
            reloaded.onUpdate();
            require(lua.get("loadCount").toint() == 1,
                    "Saved instances must receive one load callback when their definition is available");
            LuaProjectileEntity projectile = new LuaProjectileEntity(null);
            projectile.entityState().attach(shot);
            NBTTagCompound shotTag = new NBTTagCompound();
            require(projectile.addEntityID(shotTag), "Projectile bridge must have a native save ID");
            require("BetaMoonProjectile".equals(shotTag.getString("id")), "Projectile save ID must be stable");
            require(EntityList.createEntityFromNBT(shotTag, null) instanceof LuaProjectileEntity,
                    "Minecraft must restore projectile instances");
            LuaLivingEntity living = new LuaLivingEntity(null);
            living.entityState().attach(creature);
            NBTTagCompound livingTag = new NBTTagCompound();
            require(living.addEntityID(livingTag), "Living bridge must have a native save ID");
            require("BetaMoonLiving".equals(livingTag.getString("id")), "Living save ID must be stable");
            require(EntityList.createEntityFromNBT(livingTag, null) instanceof LuaLivingEntity,
                    "Minecraft must restore living instances");
            LuaPickupEntity drop = new LuaPickupEntity(null);
            drop.initializeItem(pickup);
            NBTTagCompound pickupTag = new NBTTagCompound();
            require(drop.addEntityID(pickupTag), "Pickup bridge must have a native save ID");
            require("BetaMoonPickup".equals(pickupTag.getString("id")), "Pickup save ID must be stable");
            require(EntityList.createEntityFromNBT(pickupTag, null) instanceof LuaPickupEntity,
                    "Minecraft must restore pickup instances");
            TestWorld chunkWorld = new TestWorld();
            LuaPropEntity chunkProp = (LuaPropEntity) EntitySpawner.spawn(chunkWorld, lamp.key,
                    2, 64, 2, 0, 0).entity;
            require(chunkProp != null, "Chunk fixture must contain a spawned prop");
            chunkProp.entityState().data().set(lamp, "uses", org.luaj.vm2.LuaValue.valueOf(9));
            chunkProp.onUpdate();
            NBTTagCompound chunkTag = new NBTTagCompound();
            ChunkLoader.storeChunkInCompound(chunkWorld.chunk, chunkWorld, chunkTag);
            require(chunkTag.getTagList("Entities").tagCount() == 1,
                    "Native chunk serialization must save the parent, not its transient hitboxes");
            int removalsBeforeUnload = lua.get("removeCount").toint();
            chunkWorld.chunk.onChunkUnload();
            chunkWorld.updateEntityList();
            require(!chunkProp.isDead && lua.get("removeCount").toint() == removalsBeforeUnload,
                    "Chunk unload must not remove a saved entity or call onRemove");
            EntityTypeRegistry.retainOwners(Collections.emptySet());
            Chunk restoredChunk = ChunkLoader.loadChunkIntoWorldFromCompound(chunkWorld, chunkTag);
            chunkWorld.replaceChunk(restoredChunk);
            restoredChunk.onChunkLoad();
            require(chunkWorld.loadedEntityList.size() == 1,
                    "Native chunk load must restore exactly one persistent parent");
            LuaPropEntity restoredProp = (LuaPropEntity) chunkWorld.loadedEntityList.get(0);
            require(restoredProp.entityState().definition() == null,
                    "Native chunk load must keep a missing definition dormant");
            EntityTypeRegistry.publish("entities.lua", Collections.singletonMap(lamp.key, lamp));
            require(restoredProp.entityState().data().get(lamp, "uses").toint() == 9,
                    "Native chunk load must preserve data after definition recovery");
            restoredProp.onUpdate();
            require(lua.get("loadCount").toint() == 2,
                    "Recovered chunk instances must run onLoad exactly once");
            verifyDiskWorldSave(lamp);
            String restoredIdentity = restoredProp.entityState().identity();
            double oldX = restoredProp.posX;
            try (ScriptExecutionScope owner = ScriptExecutionScope.open("entities.lua");
                    ScriptEntityScope scope = ScriptEntityScope.open("entities.lua")) {
                lua.load("betamoon.entities:add{key='mymod:lamp',kind='prop',"
                        + "appearance={model='minecraft:block/torch',texture='test.png'},"
                        + "physics={mode='dynamic',gravity=0},"
                        + "data={uses={type='integer',default=2}},"
                        + "onTick=function(ctx) reloadTicks=reloadTicks+1 end}").call();
                scope.publish();
            }
            restoredProp.motionX = 0.2;
            restoredProp.onUpdate();
            require(restoredProp.posX > oldX && restoredProp.entityState().identity().equals(restoredIdentity)
                    && restoredProp.entityState().data().get(restoredProp.entityState().definition(), "uses").toint() == 9
                    && lua.get("reloadTicks").toint() == 1 && lua.get("loadCount").toint() == 2,
                    "Hot reload must update motion and callbacks without replacing or reloading the entity");
            EntityTypeDefinition reloadedType = restoredProp.entityState().definition();
            ModelAppearance previousAppearance = EntityVisuals.get(reloadedType);
            require(previousAppearance != null, "A usable entity appearance must bind before pack switching");
            ModelAppearanceDeclaration unavailableAppearance = new ModelAppearanceDeclaration(
                    lua.load("return {model='missing.json',texture='test.png'}").call());
            EntityTypeDefinition unavailableType = new EntityTypeDefinition(reloadedType.key, reloadedType.kind,
                    reloadedType.lifecycle, reloadedType.displayName, reloadedType.width, reloadedType.height,
                    reloadedType.body, reloadedType.render, reloadedType.sounds, reloadedType.spawning,
                    reloadedType.inventory, reloadedType.equipment,
                    reloadedType.relations, reloadedType.mount, reloadedType.behavior,
                    unavailableAppearance, reloadedType.projectile, reloadedType.living, reloadedType.pickup,
                    reloadedType.physics, reloadedType.health, reloadedType.drops, reloadedType.data,
                    reloadedType.parts,
                    reloadedType.sensors,
                    reloadedType.onInteract,
                    reloadedType.onImpact, reloadedType.onTick, reloadedType.onPickup, reloadedType.onSpawn,
                    reloadedType.onLoad, reloadedType.onDeath, reloadedType.onRemove,
                    reloadedType.onActivate, reloadedType.onDeactivate, reloadedType.onBeforeDamage,
                    reloadedType.onAfterDamage, reloadedType.tickInterval);
            EntityTypeRegistry.publish("entities.lua", Collections.singletonMap(lamp.key, unavailableType));
            require(EntityVisuals.get(unavailableType) == previousAppearance,
                    "Missing replacement geometry must preserve the last usable appearance");
            files.files.put("missing.json", ModelFoundationTest.GEOMETRY.getBytes(StandardCharsets.UTF_8));
            ClientAssets.refresh();
            require(EntityVisuals.get(unavailableType) != previousAppearance,
                    "Pack refresh must retry a previously failed entity appearance");
            files.files.remove("missing.json");
            ClientAssets.refresh();
            require(EntityVisuals.get(unavailableType) != null,
                    "Switching away from the pack must retain a usable entity appearance");
            System.out.println("Entity Lua API passed: declarations, collisions, collection, chunk recovery and refresh.");
        } finally {
            EntityPresentationEvents.install(null);
            EntityTypeRegistry.clear();
            EntityVisuals.prune();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int countItemEntities(World world, int itemId) {
        int count = 0;
        for (Object value : world.loadedEntityList) {
            if (value instanceof EntityItem && ((EntityItem) value).item.itemID == itemId) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasItemEntity(World world, int itemId, int stackSize) {
        for (Object value : world.loadedEntityList) {
            if (value instanceof EntityItem && ((EntityItem) value).item.itemID == itemId
                    && ((EntityItem) value).item.stackSize == stackSize) {
                return true;
            }
        }
        return false;
    }

    private static int countItemQuantity(World world, int itemId) {
        int count = 0;
        for (Object value : world.loadedEntityList) {
            if (value instanceof EntityItem && ((EntityItem) value).item.itemID == itemId) {
                count += ((EntityItem) value).item.stackSize;
            }
        }
        return count;
    }

    private static void verifyDiskWorldSave(EntityTypeDefinition definition) throws IOException {
        Path buildRoot = Paths.get("build").toAbsolutePath().normalize();
        Files.createDirectories(buildRoot);
        Path testRoot = Files.createTempDirectory(buildRoot, "entity-world-");
        try {
            SaveHandler handler = new SaveHandler(testRoot.toFile(), "world", false);
            World first = new World(handler, "entity_test", new WorldProvider() {
            }, 123L);
            first.getChunkFromChunkCoords(0, 0);
            int y = Math.min(120, first.getHeightValue(2, 2) + 2);
            LuaPropEntity spawned = (LuaPropEntity) EntitySpawner.spawn(first, definition.key,
                    2, y, 2, 0, 0).entity;
            require(spawned != null, "A custom entity must join a generated Beta world chunk");
            spawned.entityState().data().set(definition, "uses", org.luaj.vm2.LuaValue.valueOf(11));
            String identity = spawned.entityState().identity();
            first.saveWorld(true, null);

            World restored = new World(handler, "entity_test", 123L, new WorldProvider() {
            });
            restored.getChunkFromChunkCoords(0, 0);
            LuaPropEntity loaded = null;
            for (Object candidate : restored.loadedEntityList) {
                if (candidate instanceof LuaPropEntity) {
                    loaded = (LuaPropEntity) candidate;
                }
            }
            require(loaded != null && identity.equals(loaded.entityState().identity())
                    && loaded.entityState().data().get(definition, "uses").toint() == 11,
                    "A disk-saved Beta world must restore custom identity and typed data");
        } finally {
            try (Stream<Path> paths = Files.walk(testRoot)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                });
            }
        }
    }

    private static final class Memory implements AssetProvider {
        private final Map<String, byte[]> files = new HashMap<>();

        public boolean exists(AssetPath path) {
            return files.containsKey(path.toString());
        }

        public String getName() {
            return "entity fixture";
        }

        public byte[] read(AssetPath path, int limit) {
            return files.get(path.toString());
        }
    }

    private static class TestWorld extends World {
        private Chunk chunk;

        private TestWorld() {
            super(null, "entity_api_test", new WorldProvider() {
            }, 0L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                public boolean chunkExists(int x, int z) {
                    return true;
                }

                public Chunk provideChunk(int x, int z) {
                    return chunk;
                }

                public Chunk prepareChunk(int x, int z) {
                    return chunk;
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
                    return "entity api fixture";
                }
            };
        }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }

        @Override
        public void checkSessionLock() {
            // The fixture serializes a chunk in memory without a disk save handler.
        }

        private void replaceChunk(Chunk restored) {
            chunk = restored;
        }

    }

    private static final class AiWorld extends TestWorld {
        private int pathCalls;
        private boolean blockPaths;
        private boolean unavailable;

        @Override
        public boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2) {
            return !unavailable && super.checkChunksExist(x1, y1, z1, x2, y2, z2);
        }

        @Override
        public PathEntity getPathToEntity(Entity source, Entity target, float range) {
            return getEntityPathToXYZ(source, (int) target.posX, (int) target.posY,
                    (int) target.posZ, range);
        }

        @Override
        public PathEntity getEntityPathToXYZ(Entity source, int x, int y, int z, float range) {
            pathCalls++;
            return blockPaths ? null : new PathEntity(new PathPoint[]{new PathPoint(x, y, z)});
        }
    }

    private static final class TestPlayer extends EntityPlayer {
        private TestPlayer(World world) {
            super(world);
        }

        @Override
        public void func_6420_o() {
        }
    }
}
