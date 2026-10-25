# Reference identity and label contract for 0.11

This is an internal contract audit for the integrated 0.11 branch. Lua `==` on a table compares the table identity. A
key or native ID remains the portable identity for saved data; no Lua reference table should be serialized.

| Public reference family | Repeated lookup `==` | Durable comparison | Reload and stale behavior |
| --- | --- | --- | --- |
| Block materials and custom step sounds | Stable for the retained native registration | Canonical material or step-sound key | Compatible reload retains the reference; structural changes are rejected. |
| Fluids | Stable for the retained `FluidRegistration` | Fluid key | Compatible reload updates the registration in place; its reference stays live. |
| Assets and sound events | Stable for the typed key while a caller holds the reference | Asset kind and key, or sound-event key | The handle follows its key across compatible re-registration. When absent, lookup returns nil; operations needing the definition fail. |
| Biomes and ores | Stable where the native entry is retained by the existing registry | Biome/ore key or native ID as documented by that API | Native registrations survive compatible reload; a changed declaration may update the retained entry. |
| Blocks, items, tools and armor | Fresh value-like view; do not use `==` for equivalence | Numeric `id` and selected `damage` | A fresh lookup reflects the current native object and selected variant. Old views may hold an earlier native object. |
| Other named worldgen, loot, dimension, atmosphere, entity-type, particle, service, capability and network references | No `==` guarantee across add/get/query or reload | The registry's canonical key | Treat a retained table as generation-scoped unless that API explicitly says otherwise; look it up again after reload. |
| World, entity, action-context, data, pose, effect and callback handles | No interning | A documented saved world/entity key or detached value, where available | They may expire with their callback, world, script generation or native instance. Never keep them in a global cache. |

The weak fluid, asset and sound-event caches contain only immutable keys or retained fluid registrations and weak
reference values. They do not retain worlds, Lua scripts or disposed generations. The block/item name index contains
only names and native IDs. Registration and loader passes invalidate it; numeric lookup verifies the live array slot.

## User-visible names

- Content and asset keys, numeric IDs, config keys, field paths, and dependency names remain stable identifiers.
- Config `label` and field `label` remain presentation text separate from JSON field names; choice `label` remains
  separate from its saved `value`. The GUI keeps validation paths in `ScriptConfigValidationException.path` so it can
  focus the exact field while showing a readable problem.
- Dependency version failures retain the package name, installed version and minimum version as distinct values
  until the resolver formats a script issue. The existing manifest `name` is also dependency identity; changing that
  contract needs a separate compatibility design.
- No translation API or translated message catalog is introduced here. New diagnostics should keep structured keys
  and paths available at their source and format prose at the presentation boundary.
