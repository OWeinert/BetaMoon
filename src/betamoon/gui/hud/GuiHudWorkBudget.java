package betamoon.gui.hud;

import betamoon.gui.presentation.GuiWorkReservation;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One shared admission pool. Revisions of the same document cannot paint
 * simultaneously.
 */
public final class GuiHudWorkBudget {
    public static final int ICON_QUADS = 4096;
    public static final int ITEMS = 64;
    public static final int OWNER_ITEMS = 32;
    private final List<Lease> leases = new ArrayList<>();

    public GuiWorkReservation reserve(Object document, Object revision, String path, String owner, int quads,
            int items) {
        if (document == null || revision == null || path == null || owner == null || quads < 0 || items < 0
                || quads > ICON_QUADS || items > ITEMS) {
            throw new IllegalArgumentException("Invalid HUD work reservation");
        }
        Lease lease = new Lease(document, revision, path, owner, quads, items);
        leases.add(lease);
        try {
            validate();
        } catch (RuntimeException failure) {
            lease.close();
            throw failure;
        }
        return lease;
    }

    private void validate() {
        Map<Object, Map<Object, Map<String, Cost>>> groups = new IdentityHashMap<>();
        Map<Object, String> owners = new IdentityHashMap<>();
        for (Lease lease : leases) {
            String previous = owners.put(lease.document, lease.owner);
            if (previous != null && !previous.equals(lease.owner)) {
                throw new IllegalArgumentException("HUD document work must have one consuming owner");
            }
            Cost cost = groups.computeIfAbsent(lease.document, key -> new IdentityHashMap<>())
                    .computeIfAbsent(lease.revision, key -> new LinkedHashMap<>())
                    .computeIfAbsent(lease.path, key -> new Cost());
            cost.quads = Math.max(cost.quads, lease.quads);
            cost.items = Math.max(cost.items, lease.items);
        }
        int quads = 0;
        int items = 0;
        Map<String, Integer> ownerItems = new LinkedHashMap<>();
        for (Map.Entry<Object, Map<Object, Map<String, Cost>>> group : groups.entrySet()) {
            int groupQuads = 0;
            int groupItems = 0;
            for (Map<String, Cost> revision : group.getValue().values()) {
                int revisionQuads = 0;
                int revisionItems = 0;
                for (Cost cost : revision.values()) {
                    revisionQuads += cost.quads;
                    revisionItems += cost.items;
                }
                groupQuads = Math.max(groupQuads, revisionQuads);
                groupItems = Math.max(groupItems, revisionItems);
            }
            quads += groupQuads;
            items += groupItems;
            String owner = owners.get(group.getKey());
            ownerItems.put(owner, ownerItems.getOrDefault(owner, 0) + groupItems);
        }
        if (quads > ICON_QUADS || items > ITEMS
                || ownerItems.values().stream().anyMatch(value -> value > OWNER_ITEMS)) {
            throw new IllegalArgumentException("HUD work exceeds 4096 icon quads, 64 items or 32 items per owner");
        }
    }

    private static final class Cost {
        int quads;
        int items;
    }

    private final class Lease implements GuiWorkReservation {
        final Object document;
        final Object revision;
        final String path;
        final String owner;
        final int quads;
        final int items;
        boolean closed;

        Lease(Object document, Object revision, String path, String owner, int quads, int items) {
            this.document = document;
            this.revision = revision;
            this.path = path;
            this.owner = owner;
            this.quads = quads;
            this.items = items;
        }

        public void close() {
            if (!closed) {
                closed = true;
                leases.remove(this);
            }
        }
    }
}
