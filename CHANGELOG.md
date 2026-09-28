# Changelog

## 0.7.3

BetaMoon 0.7.3 expands reversible Lua overrides across registered gameplay systems.

### Container GUIs

- Add standard held-key repetition to focused text-box controls, including delayed repeating Backspace and Delete.

### Override API

- Make multi-property override application transactional, keep reference fields live, and compose callback layers
  through single-use `ctx:base()` calls to the next lower layer.
- Add block step sound, slipperiness, unbreakable, fire, declarative drops, and display-tick cadence overrides, plus
  item full-3D, efficiency, and entity-damage overrides.
- Allow recognized native shaped, shapeless, and smelting recipes to replace their inputs without losing stable
  registration identity or lookup ordering.
- Add queryable, reversible overlays for sound events, BetaMoon entity types, ore rules, biomes, fuel registrations,
  fuel-set includes, and container GUI presentations.
- Add callback/cadence overlays for tile entities, containers, capability operations, world services, and logical
  networks while keeping persistent schemas and identity fields structural.
- Validate candidates before publication, rebuild affected indexes/snapshots only, and restore exact lower/base state
  when a layer is removed or its owning script unloads.
