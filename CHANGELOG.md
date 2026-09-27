# Changelog

## 0.7.3

BetaMoon 0.7.3 expands reversible Lua overrides across registered gameplay systems and improves the advanced network
and interactive-GUI examples.

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

### Examples and documentation

- Add a minimal beginner multi-file mod tutorial with a manifest, entrypoint, and private module, and renumber the
  later beginner lessons accordingly.
- Fix the hybrid-network example's capability-call budget usage and clarify how to operate its local and remote
  network paths.
- Add editable channel controls to the wireless transmitter and receiver example.
- Integrate the interactive machine GUI controls into the GUI showcase and remove the redundant standalone example.
- Expand the Lua definitions and wiki reference for the new lookup, live-reference, and override surfaces.
