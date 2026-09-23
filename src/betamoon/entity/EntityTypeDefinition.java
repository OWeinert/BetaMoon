package betamoon.entity;

import betamoon.data.DataField;
import betamoon.assets.AssetKey;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/** Immutable declaration for one custom entity type. */
public final class EntityTypeDefinition {
    public final AssetKey key;
    public final EntityKind kind;
    public final EntityLifecycle lifecycle;
    public final String displayName;
    public final float width;
    public final float height;
    public final EntityBodyDefinition body;
    public final EntityRenderDefinition render;
    public final EntitySoundsDefinition sounds;
    public final EntitySpawnDefinition spawning;
    public final EntityInventoryDefinition inventory;
    public final EntityEquipmentDefinition equipment;
    public final EntityRelationsDefinition relations;
    public final EntityMountDefinition mount;
    public final EntityBehaviorDefinition behavior;
    public final ModelAppearanceDeclaration appearance;
    public final ProjectileDefinition projectile;
    public final LivingDefinition living;
    public final PickupDefinition pickup;
    public final EntityPhysicsDefinition physics;
    public final EntityHealthDefinition health;
    public final EntityDropDefinition drops;
    public final Map<String, DataField> data;
    public final Map<String, EntityPartDefinition> parts;
    public final Map<String, EntitySensorDefinition> sensors;
    public final LuaValue onInteract;
    public final LuaValue onImpact;
    public final LuaValue onTick;
    public final LuaValue onSpawn;
    public final LuaValue onLoad;
    public final LuaValue onDeath;
    public final LuaValue onRemove;
    public final LuaValue onPickup;
    public final LuaValue onActivate;
    public final LuaValue onDeactivate;
    public final LuaValue onBeforeDamage;
    public final LuaValue onAfterDamage;
    public final int tickInterval;

    public EntityTypeDefinition(AssetKey key, EntityKind kind, EntityLifecycle lifecycle,
            String displayName, float width, float height,
            EntityBodyDefinition body, EntityRenderDefinition render, EntitySoundsDefinition sounds,
            EntitySpawnDefinition spawning, EntityInventoryDefinition inventory,
            EntityEquipmentDefinition equipment, EntityRelationsDefinition relations, EntityMountDefinition mount,
            EntityBehaviorDefinition behavior,
            ModelAppearanceDeclaration appearance, ProjectileDefinition projectile, LivingDefinition living,
            PickupDefinition pickup, EntityPhysicsDefinition physics, EntityHealthDefinition health,
            EntityDropDefinition drops,
            Map<String, DataField> data, Map<String, EntityPartDefinition> parts,
            Map<String, EntitySensorDefinition> sensors,
            LuaValue onInteract, LuaValue onImpact, LuaValue onTick, LuaValue onPickup,
            LuaValue onSpawn, LuaValue onLoad, LuaValue onDeath, LuaValue onRemove,
            LuaValue onActivate, LuaValue onDeactivate, LuaValue onBeforeDamage, LuaValue onAfterDamage,
            int tickInterval) {
        this.key = key;
        this.kind = kind;
        this.lifecycle = lifecycle;
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.body = body;
        this.render = render;
        this.sounds = sounds;
        this.spawning = spawning;
        this.inventory = inventory;
        this.equipment = equipment;
        this.relations = relations;
        this.mount = mount;
        this.behavior = behavior;
        this.appearance = appearance;
        this.projectile = projectile;
        this.living = living;
        this.pickup = pickup;
        this.physics = physics;
        this.health = health;
        this.drops = drops;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.parts = Collections.unmodifiableMap(new LinkedHashMap<>(parts));
        this.sensors = Collections.unmodifiableMap(new LinkedHashMap<>(sensors));
        this.onInteract = onInteract;
        this.onImpact = onImpact;
        this.onTick = onTick;
        this.onPickup = onPickup;
        this.onBeforeDamage = onBeforeDamage;
        this.onAfterDamage = onAfterDamage;
        this.onActivate = onActivate;
        this.onDeactivate = onDeactivate;
        this.onSpawn = onSpawn;
        this.onLoad = onLoad;
        this.onDeath = onDeath;
        this.onRemove = onRemove;
        this.tickInterval = tickInterval;
    }
}
