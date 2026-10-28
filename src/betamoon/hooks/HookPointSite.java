package betamoon.hooks;

import betamoon.interop.catalog.CatalogValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Frozen selector alternatives plus bounded observed match evidence. */
public final class HookPointSite {
    public final HookPoint point;
    public final List<HookPoint.Member> alternatives;
    public final Set<String> owners = new TreeSet<>();
    public volatile int operationId = -1;
    public volatile int observed = -1;
    public volatile List<Map<String, Object>> matches = Collections.emptyList();

    public HookPointSite(HookPoint point, List<HookPoint.Member> alternatives) {
        this.point = point;
        this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
    }

    public Map<String, Object> transport() {
        List<Map<String, Object>> members = new ArrayList<>();
        for (HookPoint.Member member : alternatives) {
            members.add(
                    CatalogValues.record("owner", member.owner, "name", member.name, "descriptor", member.descriptor));
        }
        return CatalogValues.record("key", point.key(), "point", point.transport(), "alternatives", members, "owners",
                new ArrayList<>(owners), "operationId", operationId, "observed", observed, "matches", matches,
                "matchesTruncated", observed > matches.size(), "maximumMatchRecords", 128, "selectorConfidence",
                "exact_operand_member", "ordinalBasis", "filtered_operations");
    }

    public static HookPointSite transport(Map<String, Object> value) {
        List<HookPoint.Member> members = new ArrayList<>();
        for (Object item : (List<?>) value.get("alternatives")) {
            Map<String, Object> member = CatalogValues.object(item);
            members.add(new HookPoint.Member(CatalogValues.text(member, "owner"), CatalogValues.text(member, "name"),
                    CatalogValues.text(member, "descriptor")));
        }
        HookPointSite site = new HookPointSite(HookPoint.transport(CatalogValues.object(value.get("point"))), members);
        site.owners.addAll(CatalogValues.strings(value.get("owners")));
        site.operationId = CatalogValues.integer(value, "operationId");
        site.observed = CatalogValues.integer(value, "observed");
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Object item : (List<?>) value.get("matches")) {
            matches.add(CatalogValues.object(item));
        }
        site.matches = Collections.unmodifiableList(matches);
        return site;
    }
}
