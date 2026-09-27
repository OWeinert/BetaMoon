package betamoon.luaapi.entity;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityBehaviorDefinition;
import betamoon.entity.EntityBodyDefinition;
import betamoon.entity.EntityDropDefinition;
import betamoon.entity.EntityHealthDefinition;
import betamoon.entity.EntityKind;
import betamoon.entity.EntityPhysicsDefinition;
import betamoon.entity.EntityRenderDefinition;
import betamoon.entity.EntitySoundsDefinition;
import betamoon.entity.EntitySpawnDefinition;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.EntityTypeRegistry;
import betamoon.entity.LivingDefinition;
import betamoon.entity.PickupDefinition;
import betamoon.entity.ProjectileDefinition;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.luaapi.utils.LuaOverrideCallback;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import betamoon.luaapi.utils.LuaOverrideLayers;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptEntityScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable live reference to a BetaMoon entity type. */
public final class EntityTypeReference extends LuaTable {
    private static final OverrideManager.Property<EntityTypeRegistry.Entry, Patch> DEFINITION =
            new OverrideManager.Property<EntityTypeRegistry.Entry, Patch>("definition",
                    new OverrideManager.PropertyAdapter<EntityTypeRegistry.Entry, Patch>() {
                        public Patch read(EntityTypeRegistry.Entry target) {
                            EntityTypeDefinition value = target.base;
                            return Patch.base(value);
                        }

                        public void write(EntityTypeRegistry.Entry target, Patch value) {
                            target.definition = value.original == null ? value.apply(target.base) : value.original;
                        }
                    }, new OverrideManager.ValueResolver<Patch>() {
                        public Patch resolve(Patch base, List<Patch> layers) {
                            if (layers.isEmpty()) {
                                return base;
                            }
                            String displayName = base.displayName;
                            Float width = base.width;
                            Float height = base.height;
                            Integer tickInterval = base.tickInterval;
                            EntityBodyDefinition body = base.body;
                            EntityRenderDefinition render = base.render;
                            EntitySoundsDefinition sounds = base.sounds;
                            EntitySpawnDefinition spawning = base.spawning;
                            EntityBehaviorDefinition behavior = base.behavior;
                            ProjectileDefinition projectile = base.projectile;
                            LivingDefinition living = base.living;
                            PickupDefinition pickup = base.pickup;
                            EntityPhysicsDefinition physics = base.physics;
                            EntityHealthDefinition health = base.health;
                            EntityDropDefinition drops = base.drops;
                            Map<String, LuaOverrideLayers<LuaOverrideDefinition>> callbacks = new LinkedHashMap<>();
                            for (Patch layer : layers) {
                                displayName = layer.displayName == null ? displayName : layer.displayName;
                                width = layer.width == null ? width : layer.width;
                                height = layer.height == null ? height : layer.height;
                                tickInterval = layer.tickInterval == null ? tickInterval : layer.tickInterval;
                                body = layer.body == null ? body : layer.body;
                                render = layer.render == null ? render : layer.render;
                                sounds = layer.sounds == null ? sounds : layer.sounds;
                                spawning = layer.spawning == null ? spawning : layer.spawning;
                                behavior = layer.behavior == null ? behavior : layer.behavior;
                                projectile = layer.projectile == null ? projectile : layer.projectile;
                                living = layer.living == null ? living : layer.living;
                                pickup = layer.pickup == null ? pickup : layer.pickup;
                                physics = layer.physics == null ? physics : layer.physics;
                                health = layer.health == null ? health : layer.health;
                                drops = layer.drops == null ? drops : layer.drops;
                                for (Map.Entry<String, LuaOverrideLayers<LuaOverrideDefinition>> callback
                                        : layer.callbacks.entrySet()) {
                                    callbacks.put(callback.getKey(), LuaOverrideLayers.concat(
                                            callbacks.get(callback.getKey()), callback.getValue()));
                                }
                            }
                            return new Patch(displayName, width, height, tickInterval, body, render, sounds, spawning,
                                    behavior, projectile, living, pickup, physics, health, drops, callbacks, null);
                        }
                    });

    private final AssetKey key;

    public EntityTypeReference(AssetKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(EntityTypeReference.this.key.toString());
            }
        });
        set("override", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return override(args.arg(args.arg1() == EntityTypeReference.this ? 2 : 1));
            }
        });
    }

    @Override
    public LuaValue get(LuaValue field) {
        if (!field.isstring()) {
            return super.get(field);
        }
        String name = field.tojstring();
        EntityTypeRegistry.Entry entry = EntityTypeRegistry.entry(key);
        EntityTypeDefinition definition = entry == null ? ScriptEntityScope.findVisible(key) : entry.definition;
        if (name.equals("key")) {
            return valueOf(key.toString());
        }
        if (name.equals("exists")) {
            return valueOf(definition != null);
        }
        if (definition == null) {
            return super.get(field);
        }
        if (name.equals("owner")) {
            return valueOf(entry == null ? LuaScriptRegistry.getCurrentScriptFile() : entry.owner);
        }
        if (name.equals("kind")) {
            return valueOf(definition.kind.name().toLowerCase());
        }
        if (name.equals("displayName")) {
            return valueOf(definition.displayName);
        }
        if (name.equals("width")) {
            return valueOf(definition.width);
        }
        if (name.equals("height")) {
            return valueOf(definition.height);
        }
        if (name.equals("tickInterval")) {
            return valueOf(definition.tickInterval);
        }
        return super.get(field);
    }

    public AssetKey key() {
        return key;
    }

    boolean matches(LuaValue criteria) {
        EntityTypeRegistry.Entry entry = EntityTypeRegistry.entry(key);
        if (entry == null) {
            return false;
        }
        if (!criteria.get("key").isnil()
                && !key.toString().equals(criteria.get("key").checkjstring())) {
            return false;
        }
        if (!criteria.get("owner").isnil()
                && !entry.owner.equals(criteria.get("owner").checkjstring())) {
            return false;
        }
        return criteria.get("kind").isnil()
                || entry.definition.kind.name().equalsIgnoreCase(criteria.get("kind").checkjstring());
    }

    LuaValue override(LuaValue value) {
        final EntityTypeRegistry.Entry entry = EntityTypeRegistry.entry(key);
        if (entry == null) {
            throw new LuaError("Entity type is no longer registered: " + key);
        }
        if (!value.istable()) {
            throw new LuaError("Entity type override expects a table.");
        }
        LuaValue when = value.get("when");
        if (!when.isnil() && !when.istable()) {
            throw new LuaError("Entity type override when must be a table.");
        }
        final LuaTable handle = new LuaTable();
        handle.set("target", this);
        if (!when.isnil() && !when.get("owner").isnil()
                && !entry.owner.equals(when.get("owner").checkjstring())) {
            handle.set("active", FALSE);
            handle.set("reason", "target owner did not match");
            return handle;
        }
        LuaValue changes = value.get("changes");
        if (changes.isnil()) {
            changes = value;
        }
        if (!changes.istable()) {
            throw new LuaError("Entity type override changes must be a table.");
        }
        String displayName = null;
        Float width = null;
        Float height = null;
        Integer tickInterval = null;
        EntityBodyDefinition body = null;
        EntityRenderDefinition render = null;
        EntitySoundsDefinition sounds = null;
        EntitySpawnDefinition spawning = null;
        EntityBehaviorDefinition behavior = null;
        ProjectileDefinition projectile = null;
        LivingDefinition living = null;
        PickupDefinition pickup = null;
        EntityPhysicsDefinition physics = null;
        EntityHealthDefinition health = null;
        EntityDropDefinition drops = null;
        Map<String, LuaOverrideLayers<LuaOverrideDefinition>> callbacks = new LinkedHashMap<>();
        LuaValue field = NIL;
        while (!(field = changes.next(field).arg1()).isnil()) {
            String name = field.checkjstring();
            if (name.equals("when") || name.equals("priority") || name.equals("target") || name.equals("changes")) {
                continue;
            }
            LuaValue changed = changes.get(field);
            if (name.equals("displayName")) {
                displayName = changed.checkjstring();
            } else if (name.equals("width")) {
                width = Float.valueOf(dimension(changed, "width"));
            } else if (name.equals("height")) {
                height = Float.valueOf(dimension(changed, "height"));
            } else if (name.equals("tickInterval")) {
                int interval = changed.checkint();
                if (interval < 1 || interval > 1200) {
                    throw new LuaError("Entity tickInterval must be between 1 and 1200.");
                }
                tickInterval = Integer.valueOf(interval);
            } else if (name.equals("body")) {
                body = new EntityBodyDefinition(changed.checktable(), entry.base.kind);
            } else if (name.equals("render")) {
                render = new EntityRenderDefinition(changed.checktable(), entry.base.kind);
            } else if (name.equals("sounds")) {
                sounds = new EntitySoundsDefinition(changed.checktable());
            } else if (name.equals("physics")) {
                physics = new EntityPhysicsDefinition(changed.checktable(), entry.base.kind);
            } else if (name.equals("drops")) {
                drops = new EntityDropDefinition(changed);
            } else if (name.equals("behavior")) {
                behavior = new EntityBehaviorDefinition(changed.checktable());
            } else if (name.equals("projectile") && entry.base.kind == EntityKind.PROJECTILE) {
                projectile = new ProjectileDefinition(changed.checktable());
            } else if (name.equals("living") && entry.base.kind == EntityKind.LIVING) {
                living = new LivingDefinition(changed.checktable());
            } else if (name.equals("spawning") && entry.base.kind == EntityKind.LIVING) {
                LivingDefinition effectiveLiving = living == null ? entry.definition.living : living;
                spawning = new EntitySpawnDefinition(changed.checktable(), effectiveLiving.despawn);
            } else if (name.equals("pickup") && entry.base.kind == EntityKind.PICKUP) {
                pickup = new PickupDefinition(changed.checktable());
            } else if (name.equals("health") && entry.base.kind != EntityKind.LIVING) {
                health = new EntityHealthDefinition(changed.checktable());
            } else if (isCallback(name)) {
                validateCallbackKind(name, entry.base.kind);
                callbacks.put(name, LuaOverrideLayers.single(new LuaOverrideDefinition(name, changed)));
            } else {
                throw new LuaError("Property '" + name + "' cannot be overridden on an entity type.");
            }
        }
        Patch patch = new Patch(displayName, width, height, tickInterval, body, render, sounds, spawning, behavior,
                projectile, living, pickup, physics, health, drops, callbacks, null);
        final OverrideManager.Layer<EntityTypeRegistry.Entry, Patch> layer = OverrideManager.apply(
                "entityType:" + key, entry, DEFINITION, patch, value.get("priority").optint(0));
        handle.set("active", TRUE);
        handle.set("remove", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                if (handle.get("active").toboolean()) {
                    layer.remove();
                    handle.set("active", FALSE);
                }
                return NIL;
            }
        });
        return handle;
    }

    private static boolean isCallback(String name) {
        return name.equals("onInteract") || name.equals("onImpact") || name.equals("onTick")
                || name.equals("onPickup") || name.equals("onSpawn") || name.equals("onLoad")
                || name.equals("onDeath") || name.equals("onRemove") || name.equals("onActivate")
                || name.equals("onDeactivate") || name.equals("onBeforeDamage") || name.equals("onAfterDamage");
    }

    private static void validateCallbackKind(String name, EntityKind kind) {
        if (name.equals("onImpact") && kind != EntityKind.PROJECTILE) {
            throw new LuaError("entity.onImpact requires a projectile entity type.");
        }
        if (name.equals("onPickup") && kind != EntityKind.PICKUP) {
            throw new LuaError("entity.onPickup requires a pickup entity type.");
        }
    }

    private static float dimension(LuaValue value, String path) {
        double number = value.checkdouble();
        if (!Double.isFinite(number) || number <= 0 || number > 16) {
            throw new LuaError("Entity " + path + " must be greater than 0 and at most 16.");
        }
        return (float) number;
    }

    private static final class Patch {
        private final String displayName;
        private final Float width;
        private final Float height;
        private final Integer tickInterval;
        private final EntityBodyDefinition body;
        private final EntityRenderDefinition render;
        private final EntitySoundsDefinition sounds;
        private final EntitySpawnDefinition spawning;
        private final EntityBehaviorDefinition behavior;
        private final ProjectileDefinition projectile;
        private final LivingDefinition living;
        private final PickupDefinition pickup;
        private final EntityPhysicsDefinition physics;
        private final EntityHealthDefinition health;
        private final EntityDropDefinition drops;
        private final Map<String, LuaOverrideLayers<LuaOverrideDefinition>> callbacks;
        private final EntityTypeDefinition original;

        private Patch(String displayName, Float width, Float height, Integer tickInterval) {
            this(displayName, width, height, tickInterval, null, null, null, null, null, null, null, null, null, null,
                    null, new LinkedHashMap<String, LuaOverrideLayers<LuaOverrideDefinition>>(), null);
        }

        private Patch(String displayName, Float width, Float height, Integer tickInterval, EntityBodyDefinition body,
                EntityRenderDefinition render, EntitySoundsDefinition sounds, EntitySpawnDefinition spawning,
                EntityBehaviorDefinition behavior, ProjectileDefinition projectile, LivingDefinition living,
                PickupDefinition pickup, EntityPhysicsDefinition physics, EntityHealthDefinition health,
                EntityDropDefinition drops, Map<String, LuaOverrideLayers<LuaOverrideDefinition>> callbacks,
                EntityTypeDefinition original) {
            this.displayName = displayName;
            this.width = width;
            this.height = height;
            this.tickInterval = tickInterval;
            this.body = body;
            this.render = render;
            this.sounds = sounds;
            this.spawning = spawning;
            this.behavior = behavior;
            this.projectile = projectile;
            this.living = living;
            this.pickup = pickup;
            this.physics = physics;
            this.health = health;
            this.drops = drops;
            this.callbacks = callbacks;
            this.original = original;
        }

        private static Patch base(EntityTypeDefinition definition) {
            return new Patch(definition.displayName, Float.valueOf(definition.width), Float.valueOf(definition.height),
                    Integer.valueOf(definition.tickInterval), definition.body, definition.render, definition.sounds,
                    definition.spawning, definition.behavior, definition.projectile, definition.living,
                    definition.pickup, definition.physics, definition.health, definition.drops,
                    new LinkedHashMap<String, LuaOverrideLayers<LuaOverrideDefinition>>(), definition);
        }

        private EntityTypeDefinition apply(EntityTypeDefinition base) {
            return new EntityTypeDefinition(base.key, base.kind, base.lifecycle,
                    displayName == null ? base.displayName : displayName,
                    width == null ? base.width : width.floatValue(),
                    height == null ? base.height : height.floatValue(),
                    body == null ? base.body : body, render == null ? base.render : render,
                    sounds == null ? base.sounds : sounds, spawning == null ? base.spawning : spawning,
                    base.inventory, base.equipment, base.relations, base.mount,
                    behavior == null ? base.behavior : behavior, base.appearance,
                    projectile == null ? base.projectile : projectile, living == null ? base.living : living,
                    pickup == null ? base.pickup : pickup, physics == null ? base.physics : physics,
                    health == null ? base.health : health, drops == null ? base.drops : drops,
                    base.data, base.parts, base.sensors,
                    callback(base.onInteract, "onInteract"), callback(base.onImpact, "onImpact"),
                    callback(base.onTick, "onTick"), callback(base.onPickup, "onPickup"),
                    callback(base.onSpawn, "onSpawn"), callback(base.onLoad, "onLoad"),
                    callback(base.onDeath, "onDeath"), callback(base.onRemove, "onRemove"),
                    callback(base.onActivate, "onActivate"), callback(base.onDeactivate, "onDeactivate"),
                    callback(base.onBeforeDamage, "onBeforeDamage"),
                    callback(base.onAfterDamage, "onAfterDamage"),
                    tickInterval == null ? base.tickInterval : tickInterval.intValue());
        }

        private LuaValue callback(final LuaValue original, String name) {
            LuaOverrideLayers<LuaOverrideDefinition> definitions = callbacks.get(name);
            if (definitions == null) {
                return original;
            }
            final LuaOverrideCallback callback = new LuaOverrideCallback(definitions);
            return new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    final LuaTable context = args.arg1().checktable();
                    return callback.invoke(context,
                            () -> original.isnil() ? LuaValue.NIL : original.call(context),
                            LuaOverrideCallback.Result.VALUE);
                }
            };
        }
    }
}
