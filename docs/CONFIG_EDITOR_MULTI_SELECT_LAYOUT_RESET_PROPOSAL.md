# Config editor: multi-select, layout and per-field reset

Status: active pre-1.0 proposal, 2026-10-21. This document records a proposed Lua-facing contract and editor
behavior. No API or GUI implementation is included here.

## Goal and scope

Make common settings easier to browse and edit while keeping their saved values ordinary config data. Add three
features to the existing `betamoon.config(key, defaults, options)` editor:

1. `input = "multiSelect"` for a fixed set of selectable string or number values stored as a list.
2. `layout` metadata for visible section headings, dividers and explicit field order.
3. A **Reset** button beside each declared field that restores that field's default in the current draft.

These features should work together in the current scrolling config screen. They do not add color pickers, keybinding
controls, arbitrary themes or new saved JSON fields. The global **Reset to Defaults** action remains useful.

## Proposed Lua declaration

```lua
local config = betamoon.config("mymod:display", {
    enabledEffects = { "fog", "particles" },
    showHints = true,
    musicVolume = 0.7,
    ambientVolume = 0.5,
}, {
    layout = {
        { heading = "Visual effects" },
        "enabledEffects",
        "showHints",
        { divider = true },
        { heading = "Audio" },
        "musicVolume",
        "ambientVolume",
    },
    fields = {
        enabledEffects = {
            label = "Enabled effects",
            input = "multiSelect",
            choices = {
                { value = "fog", label = "Fog" },
                { value = "particles", label = "Particles" },
                { value = "screen_shake", label = "Screen shake" },
            },
        },
        musicVolume = { label = "Music", input = "slider", min = 0, max = 1, step = 0.1 },
        ambientVolume = { label = "Ambience", input = "slider", min = 0, max = 1, step = 0.1 },
    },
})
```

`config.enabledEffects` remains a dense Lua array. Its JSON value remains an array such as
`["fog", "particles"]`. Headings, dividers, order and reset buttons are editor metadata only. Existing scripts
that omit these options retain their current controls and saved format.

## Multi-select contract

- Allow `input = "multiSelect"` only on a list default. Require a nonempty, dense `choices` array using the existing
  scalar choice syntax: a value or `{ value = ..., label = ... }`. Each value is a finite number or string; all values
  have the same type and are unique. A choice label affects display only. Cap choices at 64 for a bounded editor.
- The default list is a unique subset of `choices`, including an empty subset. Duplicate or unknown default entries
  are declaration errors. For an empty `{}` default, the input metadata must supply an array shape hint before
  snapshotting so it cannot become an empty object.
- A multi-select field does not also use `kind`, `element`, `key`, `value`, numeric bounds or `maxItems`. Its
  selectable set is determined by `choices`; it is not a general add/remove collection. `expanded` may choose whether
  the choice rows start open, as it already does for other expandable controls.
- Render one focusable summary row, such as **Enabled effects: 2 of 3 selected**, that expands to one checkable row
  per choice. Every row shows the text label and an explicit selected indicator. Space or Enter toggles a focused
  choice; keyboard focus, scroll, tooltips and the existing narrow-screen layout must remain usable. A collapsed
  summary must still reveal that the field has an unsaved change.
- Toggling a choice writes the draft array in declaration order. Merely opening the editor or saving another field
  must not reorder an untouched array. Direct Lua saves may use a different order; accept any unique valid subset
  without silently sorting it. This preserves array semantics for scripts that inspect order. Existing `onChange`
  paths for lists remain index-based; reordering or removing a choice may report several changed indices.
- Validate the subset on load, reload, GUI Save and `config.save()`, not only when a checkbox is clicked. A saved
  value removed from `choices` must produce an actionable field-path error and leave the file intact. Authors can
  keep that choice or use the existing schema migration mechanism to convert old values; the editor must not drop
  them implicitly.

## Layout metadata

Add optional `options.layout` for top-level fields and optional `layout` inside metadata for a fixed nested object.
Each layout entry is either a declared field key, `{ heading = "Text" }`, or `{ divider = true }`. Layout references
are local to that object; nested keys are arranged by their containing object's layout. A heading is static text,
and a divider is a visual separator. Neither is a collapsible value group, a config path or an `onChange` source.
Existing fixed objects remain the collapsible grouping mechanism.

Validate layout entries when the config is declared: reject unknown or repeated field keys, empty heading text,
malformed entries, empty dividers, dangling headings and dividers at the end. A heading needs at least one following
field before the next heading. A divider needs a field on each side; placing one before a heading is valid when it
separates two populated groups. Append any declared fields not mentioned in `layout` in the existing default-derived
order. This ensures a metadata edit cannot hide a saved field. Layout does not affect JSON or the order of Lua
callbacks.

The current form already draws a thin separator between top-level rows. Explicit dividers should have more spacing
and a clear visual weight so they communicate an intentional section break. Headings should align with field labels
and remain legible in the Minecraft-style palette. Neither decorative row should take keyboard focus or show a
modified marker. A heading following a divider should not create a duplicate line.

## Per-field reset behavior

Place a compact **Reset** button or clearly labeled reset icon beside each declared field, including scalar fields,
fixed object parents, list/dictionary parents and multi-select fields. The button is enabled only when that field's
declared draft value differs from its declared default and restoring it passes the same edit/read-only rules as Save.
Ignore unknown keys when comparing a fixed object with its declared default. Disable reset for a read-only field or
inherited read-only subtree. Do not show per-entry reset for dynamic list or dictionary rows in the first version;
their parent resets the collection, while entries retain edit/remove controls.

A click changes the current draft only. The user still chooses **Save** to write the file and run `onChange`, or
**Cancel** to discard the reset. The existing asterisk continues to mean "different from the editor baseline," not
"different from the default." A reset can therefore create an asterisk, and a value edited back to the baseline
clears it even if that value is not the default. The Reset button compares with the declared default, so it can be
enabled while there is no unsaved asterisk.

Resetting a scalar or multi-select field replaces its draft value with a copy of the declaration default. Resetting
a fixed object restores its declared descendants recursively while retaining unknown keys, matching the current
global Reset behavior. Resetting a dynamic list/dictionary restores its declared default entries as a whole. A
parent reset must never mutate a read-only descendant: build the candidate, check it against the current baseline
with the existing validator and disable the parent button if it would violate a lock. Individual editable children
remain resettable.

The editor currently keeps typed text in controls until it commits the form. Before a reset that rebuilds rows,
commit valid edits in other fields so they are not lost. If another field contains invalid pending text, keep that
text, focus the invalid control and leave the reset unapplied. The target field may contain invalid pending text;
reset should be able to replace that text with its valid default. Preserve scroll position and the expansion state
of unrelated groups. The global Reset action should follow the same pending-edit rule when the GUI is changed.

## Implementation path and verification

1. Extend config metadata parsing and field descriptors with multi-select and object-local layout. Supply list
   shape hints for an empty multi-select default and validate declaration errors with precise field paths.
2. Add shared subset validation to disk merge/reload and edits, including direct Lua saves. Preserve original file
   bytes on load or migration failure and keep the existing atomic Save and external-edit guard.
3. Represent headings and dividers as presentation rows in `betamoon.gui`, separate from value rows. Add the
   multi-select control and per-field reset actions with keyboard support and narrow-screen layout.
4. Verify nonempty and empty defaults, changed choices, duplicate/unknown values, read-only descendants, nested
   layouts, missing/duplicate layout references, pending invalid text, baseline markers, callback paths, file
   reload and JSON compatibility. Include a real client pass for scroll, focus, hover text and small window sizes.
5. When implemented, update `../BetaMoon-VSC-Lua-Addon/internal/api.lua`, run `npm run sync-api` and
   `npm run check-api`, addon tests and relevant LuaLS checks, then update the user-facing configuration wiki page.

The recommended first release includes all three features above. More control styles can be decided separately
after the editor has these structure and recovery affordances.
