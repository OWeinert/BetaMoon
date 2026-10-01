package betamoon.fluid;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.Item;

/** Thread-safe publication and constant-time block lookup for registered fluids. */
public final class FluidRegistry {
    private static final int BLOCK_LIMIT = 256;
    private static final FluidRegistry INSTANCE = new FluidRegistry();

    private final Map<ContentKey, FluidRegistration> byKey = new LinkedHashMap<>();
    private final Map<Integer, FluidRegistration> byBucketItemId = new HashMap<>();
    private volatile FluidRegistration[] byBlockId = new FluidRegistration[BLOCK_LIMIT];
    private boolean vanillaRegistered;

    private FluidRegistry() {
    }

    public static FluidRegistry instance() {
        return INSTANCE;
    }

    /** Registers native water/lava once, after Minecraft's static block initialization. */
    public synchronized void registerVanilla() {
        if (vanillaRegistered) {
            return;
        }
        publish(vanillaWater());
        publish(vanillaLava());
        vanillaRegistered = true;
    }

    public synchronized FluidRegistration publish(FluidDefinition definition) {
        preflight(definition);
        FluidRegistration existing = byKey.get(definition.key());
        if (existing != null) {
            existing.update(definition);
            return existing;
        }

        FluidRegistration registration = new FluidRegistration(definition);
        FluidRegistration[] updatedBlockIds = byBlockId.clone();
        updatedBlockIds[definition.flowingBlockId()] = registration;
        updatedBlockIds[definition.stillBlockId()] = registration;

        byKey.put(definition.key(), registration);
        if (definition.hasBucket()) {
            byBucketItemId.put(Integer.valueOf(definition.bucketItemId()), registration);
        }
        byBlockId = updatedBlockIds;
        return registration;
    }

    /** Validates complete structural identity without publishing it. */
    public synchronized void preflight(FluidDefinition definition) {
        FluidRegistration existing = byKey.get(definition.key());
        if (existing != null) {
            ensureSlotsBelongTo(existing, definition);
            FluidDefinition current = existing.definition();
            if (!current.owner().equals(definition.owner())) {
                throw new IllegalStateException("Fluid '" + definition.key() + "' belongs to another owner.");
            }
            if (!current.structuralSignature().equals(definition.structuralSignature())) {
                throw new IllegalStateException("Fluid '" + definition.key()
                        + "' changed structural fields. Restart Minecraft to apply this change.");
            }
            return;
        }

        ensureBlockSlotAvailable(definition.flowingBlockId(), definition.key());
        ensureBlockSlotAvailable(definition.stillBlockId(), definition.key());
        if (definition.hasBucket()) {
            FluidRegistration bucketOwner = byBucketItemId.get(Integer.valueOf(definition.bucketItemId()));
            if (bucketOwner != null) {
                throw new IllegalStateException("Bucket item ID " + definition.bucketItemId()
                        + " is already used by fluid '" + bucketOwner.key() + "'.");
            }
        }
    }

    private void ensureSlotsBelongTo(FluidRegistration expected, FluidDefinition definition) {
        if (byBlockId[definition.flowingBlockId()] != expected || byBlockId[definition.stillBlockId()] != expected) {
            throw new IllegalStateException("Fluid '" + definition.key() + "' changed its registered block IDs.");
        }
        if (definition.hasBucket() && byBucketItemId.get(Integer.valueOf(definition.bucketItemId())) != expected) {
            throw new IllegalStateException("Fluid '" + definition.key() + "' changed its registered bucket item ID.");
        }
    }

    private void ensureBlockSlotAvailable(int blockId, ContentKey incoming) {
        FluidRegistration registration = byBlockId[blockId];
        if (registration != null) {
            throw new IllegalStateException("Block ID " + blockId + " is already used by fluid '"
                    + registration.key() + "', cannot register '" + incoming + "'.");
        }
    }

    public synchronized FluidRegistration get(ContentKey key) {
        return byKey.get(key);
    }

    public FluidRegistration getByBlockId(int blockId) {
        FluidRegistration[] registrations = byBlockId;
        return blockId >= 0 && blockId < registrations.length ? registrations[blockId] : null;
    }

    public synchronized FluidRegistration getByBucketItemId(int itemId) {
        return byBucketItemId.get(Integer.valueOf(itemId));
    }

    public synchronized List<FluidRegistration> snapshot() {
        return Collections.unmodifiableList(new ArrayList<FluidRegistration>(byKey.values()));
    }

    public boolean isSameFluid(int firstBlockId, int secondBlockId) {
        FluidRegistration first = getByBlockId(firstBlockId);
        return first != null && first == getByBlockId(secondBlockId);
    }

    private static FluidDefinition vanillaWater() {
        return new FluidDefinition(ContentKey.of("minecraft", ContentType.FLUID, "water"), "minecraft", "water",
                FluidCarrier.WATER, FluidFlowPolicy.water(), new FluidAppearance(205, 206, 0xFFFFFF, 1, 0, 3),
                FluidDisplacementPolicy.DROP, Block.waterMoving.blockID, Block.waterStill.blockID,
                Item.bucketWater.shiftedIndex, true);
    }

    private static FluidDefinition vanillaLava() {
        return new FluidDefinition(ContentKey.of("minecraft", ContentType.FLUID, "lava"), "minecraft", "lava",
                FluidCarrier.LAVA, FluidFlowPolicy.lava(), new FluidAppearance(237, 238, 0xFFFFFF, 0, 15, 255),
                FluidDisplacementPolicy.DESTROY, Block.lavaMoving.blockID, Block.lavaStill.blockID,
                Item.bucketLava.shiftedIndex, true);
    }
}
