package betamoon.luaapi.entity;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityBodyDefinition;
import betamoon.entity.EntityBehaviorDefinition;
import betamoon.entity.EntityDataField;
import betamoon.entity.EntityDropDefinition;
import betamoon.entity.EntityEquipmentDefinition;
import betamoon.entity.EntityHealthDefinition;
import betamoon.entity.EntityInventoryDefinition;
import betamoon.entity.EntityKind;
import betamoon.entity.EntityLifecycle;
import betamoon.entity.EntityMountDefinition;
import betamoon.entity.EntityPartDefinition;
import betamoon.entity.EntityPhysicsDefinition;
import betamoon.entity.EntityRelationsDefinition;
import betamoon.entity.EntityRenderDefinition;
import betamoon.entity.EntitySensorDefinition;
import betamoon.entity.EntitySoundsDefinition;
import betamoon.entity.EntitySpawnDefinition;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.LivingDefinition;
import betamoon.entity.PickupDefinition;
import betamoon.entity.ProjectileDefinition;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Reads a complete entity declaration without implicit inheritance. */
final class EntityDeclaration {
    private EntityDeclaration() {
    }

    static EntityTypeDefinition read(LuaValue value) {
        fields(value, "entity", "key", "kind", "lifecycle", "displayName", "appearance", "width", "height", "body",
                "render", "sounds", "spawning", "inventory", "equipment", "relations", "mount", "behavior",
                "data", "projectile", "living", "pickup", "physics", "parts", "sensors",
                "onInteract", "onImpact", "onPickup", "onTick",
                "onSpawn", "onLoad", "onActivate", "onDeactivate", "onDeath", "onRemove",
                "onBeforeDamage", "onAfterDamage", "health", "drops", "tickInterval");
        AssetKey key = parseKey(required(value, "key"), "entity.key");
        EntityKind kind;
        try {
            kind = EntityKind.parse(value.get("kind").isnil()
                    ? "prop" : string(value.get("kind"), "entity.kind"));
        } catch (IllegalArgumentException exception) {
            throw error("entity.kind", exception.getMessage());
        }
        EntityLifecycle lifecycle;
        try {
            lifecycle = EntityLifecycle.parse(value.get("lifecycle").isnil() ? "native"
                    : string(value.get("lifecycle"), "entity.lifecycle"));
        } catch (IllegalArgumentException exception) {
            throw error("entity.lifecycle", exception.getMessage());
        }
        String displayName = value.get("displayName").isnil()
                ? key.toString()
                : string(value.get("displayName"), "entity.displayName");
        float width = dimension(value.get("width"), kind == EntityKind.PROJECTILE || kind == EntityKind.PICKUP
                ? 0.25f : 0.6f,
                "entity.width");
        float height = dimension(value.get("height"), kind == EntityKind.PROJECTILE || kind == EntityKind.PICKUP
                ? 0.25f : 1.0f,
                "entity.height");
        EntityBodyDefinition body = new EntityBodyDefinition(
                value.get("body").isnil() ? new LuaTable() : value.get("body"), kind);
        EntityRenderDefinition render = new EntityRenderDefinition(
                value.get("render").isnil() ? new LuaTable() : value.get("render"), kind);
        EntitySoundsDefinition sounds = new EntitySoundsDefinition(
                value.get("sounds").isnil() ? new LuaTable() : value.get("sounds"));
        if (kind != EntityKind.PICKUP && value.get("appearance").isnil()) {
            throw error("entity.appearance", "a model appearance is required");
        }
        ModelAppearanceDeclaration appearance = value.get("appearance").isnil() ? null
                : new ModelAppearanceDeclaration(value.get("appearance"));
        if (kind != EntityKind.PROJECTILE && !value.get("projectile").isnil()) {
            throw error("entity.projectile", "requires kind = 'projectile'");
        }
        ProjectileDefinition projectile = kind == EntityKind.PROJECTILE
                ? new ProjectileDefinition(value.get("projectile").isnil() ? new LuaTable() : value.get("projectile"))
                : null;
        if (kind != EntityKind.LIVING && !value.get("living").isnil()) {
            throw error("entity.living", "requires kind = 'living'");
        }
        LivingDefinition living = kind == EntityKind.LIVING
                ? new LivingDefinition(value.get("living").isnil() ? new LuaTable() : value.get("living"))
                : null;
        if (living != null && living.composed() && lifecycle == EntityLifecycle.MANUAL) {
            throw error("entity.living.ai.stages", "requires lifecycle = 'native'");
        }
        if (kind != EntityKind.LIVING && !value.get("spawning").isnil()) {
            throw error("entity.spawning", "requires kind = 'living'");
        }
        EntitySpawnDefinition spawning = kind == EntityKind.LIVING && !value.get("spawning").isnil()
                ? new EntitySpawnDefinition(value.get("spawning"), living.despawn) : null;
        if (spawning != null && spawning.nativeDespawn && lifecycle == EntityLifecycle.MANUAL) {
            throw error("entity.spawning.despawn", "native despawn requires lifecycle = 'native'");
        }
        boolean contentHolder = kind == EntityKind.PROP || kind == EntityKind.LIVING;
        if (!contentHolder && !value.get("inventory").isnil()) {
            throw error("entity.inventory", "requires kind = 'prop' or 'living'");
        }
        if (!contentHolder && !value.get("equipment").isnil()) {
            throw error("entity.equipment", "requires kind = 'prop' or 'living'");
        }
        EntityInventoryDefinition inventory = value.get("inventory").isnil() ? null
                : new EntityInventoryDefinition(value.get("inventory"), displayName);
        EntityEquipmentDefinition equipment = value.get("equipment").isnil() ? null
                : new EntityEquipmentDefinition(value.get("equipment"));
        if (!contentHolder && !value.get("relations").isnil()) {
            throw error("entity.relations", "requires kind = 'prop' or 'living'");
        }
        if (!contentHolder && !value.get("mount").isnil()) {
            throw error("entity.mount", "requires kind = 'prop' or 'living'");
        }
        EntityRelationsDefinition relations = value.get("relations").isnil() ? null
                : new EntityRelationsDefinition(value.get("relations"));
        EntityMountDefinition mount = value.get("mount").isnil() ? null
                : new EntityMountDefinition(value.get("mount"), height);
        EntityBehaviorDefinition behavior = value.get("behavior").isnil() ? null
                : new EntityBehaviorDefinition(value.get("behavior"));
        if (kind != EntityKind.PICKUP && !value.get("pickup").isnil()) {
            throw error("entity.pickup", "requires kind = 'pickup'");
        }
        PickupDefinition pickup = kind == EntityKind.PICKUP
                ? new PickupDefinition(required(value, "pickup")) : null;
        EntityPhysicsDefinition physics = new EntityPhysicsDefinition(
                value.get("physics").isnil() ? new LuaTable() : value.get("physics"), kind);
        if (kind == EntityKind.LIVING && !value.get("health").isnil()) {
            throw error("entity.health", "living entities use living.maxHealth");
        }
        EntityHealthDefinition health = value.get("health").isnil()
                ? null : new EntityHealthDefinition(value.get("health"));
        Map<String, EntityPartDefinition> parts = new LinkedHashMap<>();
        LuaValue partTable = value.get("parts");
        if (!partTable.isnil()) {
            if (kind == EntityKind.PICKUP) {
                throw error("entity.parts", "pickup entities use their native item hitbox");
            }
            for (LuaValue name : partTable.checktable().keys()) {
                String partName = string(name, "entity.parts key");
                if (!partName.matches("[a-zA-Z_][a-zA-Z0-9_]{0,63}")) {
                    throw error("entity.parts", "part names must be identifiers of at most 64 characters");
                }
                parts.put(partName, new EntityPartDefinition(partName, partTable.get(name)));
            }
        }
        Map<String, EntitySensorDefinition> sensors = new LinkedHashMap<>();
        LuaValue sensorTable = value.get("sensors");
        if (!sensorTable.isnil()) {
            if (!sensorTable.istable() || sensorTable.checktable().keys().length > 16) {
                throw error("entity.sensors", "expected at most 16 named sensors");
            }
            for (LuaValue name : sensorTable.checktable().keys()) {
                String sensorName = string(name, "entity.sensors key");
                if (!sensorName.matches("[a-z][a-z0-9_]{0,63}")) {
                    throw error("entity.sensors",
                            "sensor names must be lowercase identifiers of at most 64 characters");
                }
                sensors.put(sensorName, new EntitySensorDefinition(sensorName, sensorTable.get(name)));
            }
        }
        Map<String, EntityDataField> data = new LinkedHashMap<>();
        LuaValue dataTable = value.get("data");
        if (!dataTable.isnil()) {
            if (!dataTable.istable() || dataTable.checktable().keys().length > 64) {
                throw error("entity.data", "expected at most 64 persistent fields");
            }
            for (LuaValue name : dataTable.checktable().keys()) {
                String fieldName = string(name, "entity.data key");
                if (!fieldName.matches("[a-z][a-z0-9_]{0,63}")) {
                    throw error("entity.data", "field names must be lowercase identifiers of at most 64 characters");
                }
                LuaValue field = dataTable.get(name);
                data.put(fieldName, EntityDataField.parse(fieldName, field, "entity.data." + fieldName));
            }
            int maximumNodes = 0;
            for (EntityDataField field : data.values()) {
                maximumNodes += field.maximumNodes();
                if (maximumNodes > 16384) {
                    throw error("entity.data", "schemas may contain at most 16384 stored values per entity");
                }
            }
        }
        LuaValue onInteract = value.get("onInteract");
        if (!onInteract.isnil() && !onInteract.isfunction()) {
            throw error("entity.onInteract", "expected a function");
        }
        LuaValue onImpact = value.get("onImpact");
        if (!onImpact.isnil() && (kind != EntityKind.PROJECTILE || !onImpact.isfunction())) {
            throw error("entity.onImpact", "requires a projectile function");
        }
        LuaValue onTick = value.get("onTick");
        if (!onTick.isnil() && !onTick.isfunction()) {
            throw error("entity.onTick", "expected a function");
        }
        if (lifecycle == EntityLifecycle.MANUAL && onTick.isnil()) {
            throw error("entity.onTick", "required when lifecycle = 'manual'");
        }
        int tickInterval = value.get("tickInterval").isnil() ? 1
                : integer(value.get("tickInterval"), "entity.tickInterval", 1, 1200);
        LuaValue onPickup = value.get("onPickup");
        if (!onPickup.isnil() && (kind != EntityKind.PICKUP || !onPickup.isfunction())) {
            throw error("entity.onPickup", "requires a pickup function");
        }
        LuaValue onSpawn = optionalCallback(value, "onSpawn");
        LuaValue onLoad = optionalCallback(value, "onLoad");
        LuaValue onDeath = optionalCallback(value, "onDeath");
        LuaValue onRemove = optionalCallback(value, "onRemove");
        LuaValue onActivate = optionalCallback(value, "onActivate");
        LuaValue onDeactivate = optionalCallback(value, "onDeactivate");
        LuaValue onBeforeDamage = optionalCallback(value, "onBeforeDamage");
        LuaValue onAfterDamage = optionalCallback(value, "onAfterDamage");
        EntityDropDefinition drops = new EntityDropDefinition(value.get("drops"));
        return new EntityTypeDefinition(key, kind, lifecycle, displayName, width, height, body, render, sounds,
                spawning, inventory, equipment, relations, mount, behavior,
                appearance, projectile, living, pickup,
                physics, health, drops,
                data, parts, sensors,
                onInteract, onImpact, onTick, onPickup,
                onSpawn, onLoad, onDeath, onRemove,
                onActivate, onDeactivate, onBeforeDamage, onAfterDamage, tickInterval);
    }

    private static LuaValue optionalCallback(LuaValue definition, String name) {
        LuaValue callback = definition.get(name);
        if (!callback.isnil() && !callback.isfunction()) {
            throw error("entity." + name, "expected a function");
        }
        return callback;
    }

    private static AssetKey parseKey(LuaValue value, String path) {
        try {
            return AssetKey.parse(string(value, path));
        } catch (IllegalArgumentException exception) {
            throw error(path, exception.getMessage());
        }
    }

    private static float dimension(LuaValue value, float fallback, String path) {
        if (value.isnil()) {
            return fallback;
        }
        double parsed = number(value, path);
        if (parsed <= 0 || parsed > 16) {
            throw error(path, "expected a size greater than 0 and at most 16 blocks");
        }
        return (float) parsed;
    }

}
