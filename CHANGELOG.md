# Changelog

## 1.0.0

### Added

- Owned HUD screens and crosshair/native-part presentation with scoped snapshots, regional input and explicit gameplay cursor mode.
- Per-part HUD status and native-hook capabilities with independent hide/replacement ownership and native fallback.
- Owned native-menu sections and main/pause replacements with deterministic ordering, typed native actions and fault fallback.
- Worldless and world-context Lua client screens with typed state/data, retained refresh, navigation and modal dialogs.
- Declared client/world UI actions with bounded fair queues, detached tickets and stale-request cancellation.
- Public reusable UI components, themes and prepared templates over the shared Java/Lua controls.
- Fixed and responsive Lua grid layouts with reactive column settings and rejected-layout retention.
- Keyed virtual Lua lists, tables and trees with custom rows, row-local state, retained drafts and keyboard navigation.
- Shared table columns/headers with stable primitive sorting, filtering and synchronized horizontal scrolling.
- Bounded collection scrollbar dragging with pinned table headers and retained/controlled horizontal offsets.
- Authored scroll viewports with retained/admitted offsets, measured content and shared nested keyboard navigation.
- Decimal numeric steppers with distinct pointer targets, keyboard stepping and retained/controlled values.
- Keyed radio groups with disabled choices, controlled selection and focused-option reveal through shared scrolling.
- Plain multiline textareas with wrapping, scrolling, selection, clipboard editing and retained/controlled drafts.
- Direct numeric entry with finite decimal validation, optional explicit stepping and retained/controlled drafts.
- Keyed dropdowns, context menus and tabs with shared keyboard navigation and viewport-fitted popovers.
- Anchored registered-screen popovers with focus restoration, outside/Escape/Tab dismissal and owned cleanup.
- Segmented, radial, value-label and indeterminate status presentations over shared numeric ranges.
- Prepared text/images/bitmap fonts, geometry and explicit scoped raw graphics with owned shaders, buffers and targets.
- Four progressive client UI examples with configurable shortcuts, editor definitions and Lua API wiki references.

- Native Interop API catalog foundations for mapped Minecraft classes, native members, and source-file discovery.
- Native class/member queries, installed-mod discovery, reusable metadata caches, and attached source browsing.
- Native debug reports with an optional detailed-catalog checkbox in Debug Tools.
- Native type reflection, scoped Minecraft/mod calls, exact values, typed arrays and native collection views.
- Exact direct access to private/protected fields and methods, including inherited and nested fields.
- Native callback phase guards, world/player/screen lifetime checks, and owner-bound native copies and retention.
- Explicit native disposal, scoped owned-value reuse, and native effect/ownership diagnostics.
- Lua-backed Java interfaces with exact method callbacks, return validation, and per-method execution policies.
- Reusable owned native attachments and current-world observers with deferred registration cleanup.
- Validated native authoring catalog exports and locally generated Minecraft/mod LuaLS definitions.
- VS Code native catalog import/refresh, exact class/member selectors and verified local/archive source navigation.
- Bounded contextual native failure diagnostics and native reflection/world-observer examples.
- Scripted method hooks with automatic Lua registry capture, mapped/literal targets and reload preflight.
- Scoped entry/return/error observations, transactional typed edits and cancellation, plus hook diagnostics and editor support.
- Bounded native refresh cancellation with scan diagnostics and integrated lifecycle/performance verification.
- Reliable lazy hook activation across game classloaders and isolated diagnostics for incompatible hook agents.
- Copied native exception causes, actionable failure guidance, exact hook-site inspection and verified editor diagnostic exports.
- Reusable native data plans with general field/call/array/collection selections, runtime inputs and detached one/many-request snapshots.
- Explicit client preparation/action capabilities with owned screen resources, authorized deferred commands and lifecycle cleanup.
- Around hooks with scoped one-use continuation, typed results, explicit native-error recovery and precise reentry behavior.
- Exact call-site and field-operation hooks with mandatory match counts, scoped operands and transactional typed edits.
- Exact constructor-return observations with saved arguments, scoped receivers and explicit declaring-constructor lifecycle semantics.
- Owned native subclass/abstract-class hosts with exact overrides, scoped setup/super calls, closed-instance policies and reusable class attachments.
- Lower-overhead native calls, hook dispatch, scoped handles and bulk queries with prepared metadata and incremental cache eviction.
- Counts-only hook diagnostics by default, optional sampled/full timing and bounded repeated-failure aggregation.
- Indexed native metadata and cancellable detailed exports with progress, captured counters and atomic completion.

### UI foundations

- Customize native player inventory panels, semantic slot positions, preview and shared screen content with owned fallback.
- Reuse detached inventory layout snapshots in reactive bindings and reusable component callbacks.
- Revoke failed attached painting immediately and cancel its queued world actions at lifecycle boundaries.

- Share editor state and single-line key behavior across authored textboxes and native-looking Java fields.
- Keep native menu input behind eligible contributed controls and settle GUI gestures in protected settings screens.

- Extract shared visual definitions, source observation, bindings, transitions, element handles and geometry
  from container GUIs while preserving their Lua declarations and behavior.
- Separate shared UI updates from painting, publish reusable displayed geometry for input, and cancel pointer
  gestures without triggering release actions.
- Establish shared graphics scopes, transform/clip conversion, GL state restoration and detached model publication.
- Connect typed reactive documents to the retained view tree, with validated visual refresh, modal focus scopes,
  and input consumption preserved across screen replacement.
- Prepare shared Java UI textures and observations outside painting and retain usable images after invalid refreshes.
- Add shared control behavior, typed screen primitives, replaceable templates and immutable style presets.
- Share native button skins across adapters and support prepared bitmap fonts and nine-slice visuals.
- Add reusable component compilation with typed props, local state, events, slots, scoped exports and keyed refresh.
- Compile bounded custom paint and irregular hit regions into Java frames, with matching displayed input geometry.
- Establish explicit raw graphics callbacks, prepared shaders/buffers/render targets and owner-scoped failure cleanup.
- Preserve rendering state through deep UI composition and keep failed paint refreshes on their last usable frame.
- Compile realized Lua rows through shared component plans with keyed typed/editor state, scoped resources/themes,
  complete paint-budget admission and transactional refresh.

### Fixed

- Shared viewports reveal nested keyboard focus, page by keyboard/track and cancel moving or stale scrollbar gestures.
- Keyboard navigation reveals an unchanged selected list row after it moves offscreen during reorder.

- Nested GUI scrolling now hands an exhausted wheel gesture to an eligible parent and handles extreme deltas without overflow.
- Deferred UI actions from failed callbacks, rejected GUI command batches or expired component owners now cancel before dispatch.
