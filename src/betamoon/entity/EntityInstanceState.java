package betamoon.entity;

import betamoon.assets.AssetKey;
import java.util.UUID;
import java.util.Map;
import betamoon.network.protocol.WireValue;
import net.minecraft.src.Entity;
import net.minecraft.src.NBTTagCompound;

/** Stable type and saved identity attached after native-safe construction. */
public final class EntityInstanceState {
    private final EntityKind nativeKind;
    private String typeName = "";
    private String identity = UUID.randomUUID().toString();
    private final EntityDataStore data = new EntityDataStore();
    private final EntityMemoryStore memory = new EntityMemoryStore();
    private final EntityInventoryState inventory = new EntityInventoryState();
    private final EntityRelationsState relations = new EntityRelationsState();
    private final EntityBehaviorState behavior = new EntityBehaviorState();
    private int health = -1;
    private boolean pendingLoad;
    private boolean deathNotified;
    private boolean removeNotified;
    private boolean damageInProgress;
    private boolean active;
    private String nextActivationReason = "chunk_load";
    private String removalReason = "removed";
    private EntitySensorManager sensors;
    private EntityPresentationState presentation;

    public EntityInstanceState(EntityKind nativeKind) {
        this.nativeKind = nativeKind;
    }

    public void attach(EntityTypeDefinition definition) {
        if (definition.kind != nativeKind) {
            throw new IllegalArgumentException("Entity kind does not match its native bridge: " + definition.key);
        }
        typeName = definition.key.toString();
        data.bind(definition);
        initializeHealth(definition);
    }

    public void attachNetwork(EntityTypeDefinition definition, String stableIdentity, long revision,
            Map<String, WireValue> snapshot) {
        if (stableIdentity == null || stableIdentity.isEmpty() || stableIdentity.length() > 128) {
            throw new IllegalArgumentException("Invalid stable entity identity");
        }
        attach(definition);
        data.applyNetworkSnapshot(definition, revision, snapshot);
        identity = stableIdentity;
    }

    public boolean applyNetworkDelta(long baseRevision, long revision, Map<String, WireValue> changedFields) {
        EntityTypeDefinition definition = definition();
        if (definition == null) {
            throw new IllegalStateException("Cannot synchronize an entity with a missing definition");
        }
        return data.applyNetworkDelta(definition, baseRevision, revision, changedFields);
    }

    public void read(NBTTagCompound tag) {
        typeName = tag.getString("BetaMoonType");
        String savedIdentity = tag.getString("BetaMoonIdentity");
        if (!savedIdentity.isEmpty()) {
            identity = savedIdentity;
        }
        data.load(tag.getCompoundTag("BetaMoonData"));
        inventory.read(tag);
        relations.read(tag);
        behavior.read(tag);
        health = tag.hasKey("BetaMoonHealth") ? tag.getInteger("BetaMoonHealth") : -1;
        deathNotified = tag.getBoolean("BetaMoonDeathNotified");
        removalReason = tag.hasKey("BetaMoonRemovalReason")
                ? tag.getString("BetaMoonRemovalReason") : "removed";
        pendingLoad = true;
        active = false;
        nextActivationReason = "chunk_load";
        definition();
    }

    public void write(NBTTagCompound tag) {
        tag.setString("BetaMoonType", typeName);
        tag.setString("BetaMoonIdentity", identity);
        tag.setCompoundTag("BetaMoonData", data.raw());
        inventory.write(tag);
        relations.write(tag);
        behavior.write(tag);
        if (health >= 0) {
            tag.setInteger("BetaMoonHealth", health);
        }
        if (deathNotified) {
            tag.setBoolean("BetaMoonDeathNotified", true);
        }
        if (!"removed".equals(removalReason)) {
            tag.setString("BetaMoonRemovalReason", removalReason);
        }
    }

    public String typeName() {
        return typeName;
    }

    public String identity() {
        return identity;
    }

    public EntityDataStore data() {
        return data;
    }

    public EntityMemoryStore memory() {
        return memory;
    }

    public EntityInventoryState inventory(Entity entity, EntityTypeDefinition definition) {
        return inventory.bind(entity, definition);
    }

    public void dropContents(Entity entity, EntityTypeDefinition definition) {
        inventory.dropContents(entity, definition);
    }

    public EntityRelationsState relations() {
        return relations;
    }

    public EntityBehaviorState behavior() {
        return behavior;
    }

    void tickAuxiliary(Entity entity, EntityTypeDefinition definition) {
        tickSensors(entity, definition);
        tickPresentation(entity, definition);
        behavior.tick(entity, definition);
    }

    public int health() {
        return health;
    }

    public void health(int value) {
        health = value;
    }

    public boolean takePendingLoad() {
        if (!pendingLoad) {
            return false;
        }
        pendingLoad = false;
        return true;
    }

    public boolean markDeathNotified() {
        if (deathNotified) {
            return false;
        }
        deathNotified = true;
        return true;
    }

    public boolean deathNotified() {
        return deathNotified;
    }

    public boolean damageInProgress() {
        return damageInProgress;
    }

    public void beginDamage() {
        damageInProgress = true;
    }

    public void endDamage() {
        damageInProgress = false;
    }

    public boolean active() {
        return active;
    }

    public String nextActivationReason() {
        return nextActivationReason;
    }

    public void activate() {
        active = true;
    }

    public void deactivate(String nextReason) {
        active = false;
        nextActivationReason = nextReason;
    }

    public boolean markRemoveNotified() {
        if (removeNotified) {
            return false;
        }
        removeNotified = true;
        return true;
    }

    public void removalReason(String reason) {
        if ("death".equals(reason)) {
            removalReason = reason;
            return;
        }
        if (deathNotified) {
            return;
        }
        if (!"explicit".equals(removalReason) || "explicit".equals(reason)) {
            removalReason = reason;
        }
    }

    public String removalReason() {
        return removalReason;
    }

    EntitySensorManager sensors(Entity entity) {
        if (sensors == null) {
            sensors = new EntitySensorManager(entity);
        }
        return sensors;
    }

    EntitySensorManager sensorsIfPresent() {
        return sensors;
    }

    void tickSensors(Entity entity, EntityTypeDefinition definition) {
        if (sensors == null && definition.sensors.isEmpty()) {
            return;
        }
        sensors(entity).tick(definition);
    }

    public boolean setSensorOffset(Entity entity, String name, double x, double y, double z) {
        EntityTypeDefinition definition = definition();
        if (definition == null || !definition.sensors.containsKey(name)) {
            return false;
        }
        return sensors(entity).setOffset(name, x, y, z);
    }

    public EntityPresentationState presentation() {
        if (presentation == null) {
            presentation = new EntityPresentationState(identity);
        }
        return presentation;
    }

    EntityPresentationState presentationIfPresent() {
        return presentation;
    }

    void tickPresentation(Entity entity, EntityTypeDefinition definition) {
        if (presentation == null && !definition.sounds.ticks()) {
            return;
        }
        presentation().tick(entity, definition);
    }

    public EntityTypeDefinition definition() {
        try {
            EntityTypeDefinition definition = EntityTypeRegistry.find(AssetKey.parse(typeName));
            if (definition == null || definition.kind != nativeKind) {
                return null;
            }
            if (!data.bind(definition)) {
                return null;
            }
            initializeHealth(definition);
            return definition;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private void initializeHealth(EntityTypeDefinition definition) {
        if (definition.health != null) {
            health = health < 0 ? definition.health.max : Math.min(health, definition.health.max);
        }
    }
}
