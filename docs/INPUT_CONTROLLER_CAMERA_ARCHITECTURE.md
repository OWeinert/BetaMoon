# Input, Controller, Camera, and Targeting Architecture

## Scope

This project gives client-side Lua packages layered control over physical input,
player intent, world actions, targeting, and the rendered view. Native first-person
play remains the default. Dedicated-server transport and multiplayer prediction are
out of scope, but common declarations must not depend on client classes.

The implementation is split into independently verifiable increments:

1. mapped native seams and their transformation tests;
2. named input actions, transition state, rebinding, and consumable contexts;
3. layered controller stages for movement, look, actions, and inventory;
4. camera rigs, interpolation, cursor rays, targeting policies, and highlights;
5. examples, diagnostics, lifecycle tests, and real-client compatibility checks.

## Native client flow

One client tick follows these relevant paths:

1. `Minecraft.runTick()` drains queued keyboard and mouse events. It forwards
   movement keys to `EntityPlayerSP.handleKeyPress`, changes the selected hotbar
   slot from the wheel, opens inventory or menu screens, and invokes
   `Minecraft.clickMouse` for discrete world actions.
2. `Minecraft.func_6254_a` owns held block breaking independently from the
   discrete click path.
3. `EntityPlayerSP.onLivingUpdate()` asks its `MovementInput` to publish strafe,
   forward, jump, and sneak intent before native living-entity movement consumes
   those values.
4. `EntityRenderer.updateCameraAndRender()` samples relative mouse motion and
   applies it through the inherited player rotation method.
5. `EntityRenderer.getMouseOver()` calculates the native center-crosshair target
   and writes `Minecraft.objectMouseOver`.
6. `EntityRenderer.orientCamera()` applies native first/third-person view
   transforms. `setupCameraTransform()` establishes projection and FOV for each
   eye and calls the orientation path.

The old `BetaMoonEventHandler` keyboard and mouse polling remains a compatibility
observer while the new router is introduced. It cannot consume queued native
events and is not the authority for action transitions.

## Verified instrumentation seams

`ClientControlHook` owns the cohesive hook module. Each target is mapped in named
source form, checked against the supported runtime JAR, idempotent, and reported by
the normal instrumentation diagnostics.

| Concern | Native seam | Contract |
| --- | --- | --- |
| Input cycle | around `Minecraft.runTick()` | Open and close one coherent input dispatch cycle. |
| Queued devices | redirect LWJGL `next` and event-value calls in gameplay and base GUI loops | Record exact keyboard/button/wheel transitions and hide consumed values from native consumers. |
| GUI consumption | around base `GuiScreen` keyboard and mouse handlers | Skip the complete GUI event path when a context consumes it. |
| Movement key | around `EntityPlayerSP.handleKeyPress(int, boolean)` | Permit a context to consume native movement forwarding. |
| Movement intent | around `MovementInputFromOptions.updatePlayerMoveState(EntityPlayer)` | Publish or replace final movement intent after native calculation. |
| Look | redirect the local-player rotation call in `EntityRenderer.updateCameraAndRender(float)` | Route only camera-derived player look; do not intercept arbitrary entity rotation. |
| Discrete action | around `Minecraft.clickMouse(int)` | Decide once whether the complete native action may run. |
| Held breaking | around `Minecraft.func_6254_a(int, boolean)` | Own the continuous breaking path independently. |
| Targeting | around `EntityRenderer.getMouseOver(float)` | Preserve, modify, or replace the native target. |
| View orientation | around `EntityRenderer.orientCamera(float)` | Preserve or replace native camera transforms. |
| Projection | around `EntityRenderer.setupCameraTransform(float, int)` | Apply validated projection/FOV adjustments per eye. |

An inherited virtual method may be mapped on its declaring superclass while the
JVM instruction names the concrete receiver type. `CallRedirectHookDefinition`
therefore supports an explicit invocation owner; the method name and descriptor
still resolve from their declaring class. This is a general bytecode capability,
not a look-specific instruction search.

The initial seam commit passed through to Minecraft. Named-action routing now owns
queued-device and family-capture decisions; controller, targeting, and camera seams
still pass through until their corresponding runtimes have lifecycle, failure, and
fallback tests.

## Runtime boundaries

The stack has four layers with one-way dependencies:

1. `InputRouter` converts queued device changes into immutable transition
   snapshots and resolves named actions.
2. `ControlLayerStack` resolves the highest applicable owner for each stage.
3. `PlayerControllerRuntime` validates Lua intent and applies movement, look,
   interaction, and inventory decisions.
4. `CameraRuntime` and `TargetingRuntime` compute tick poses, interpolated render
   poses, rays, targets, and highlight state.

Declaration parsing, registries, runtime state, Lua handles, and native callbacks
remain separate. Compiled definitions contain no Minecraft client instances.

## Ownership and execution modes

Every input context, controller layer, and camera activation has a package owner,
priority, monotonically increasing token, and generation. Equal-priority layers
resolve newest-first. Releasing a token reveals the next layer; no caller restores
a previously observed global value.

Controller stages share these modes:

- `native`: leave Minecraft unchanged;
- `modify`: calculate native state, then validate a bounded replacement or veto;
- `manual`: skip the native stage and require a validated engine result;
- `disabled`: consume the stage without producing an action.

Failures disable only the failing callback or stage for its current generation,
release its active capture, report an actionable script issue, and fall back to the
next layer or native behavior. Emergency engine controls always outrank script
contexts.

## Input-cycle contract

Physical state is sampled on the game thread. Each cycle has stable `pressed`,
`held`, `released`, and analog values. Transitions are dispatched at most once per
cycle; ordinary held queries do not call Lua every render frame. Focus loss,
screen replacement, world replacement, player replacement, and script unload
synthesize releases before clearing state so captures cannot remain stuck.

Contexts declare the families they capture: named actions, movement, look,
world actions, inventory, and GUI. A handled named action does not implicitly
consume unrelated GUI input. Device bindings support keyboard keys, mouse buttons,
wheel direction, and modifier combinations. Pointer axes join this contract with
the controller/camera increment. Rebinding is separate from immutable action
declarations, is validated for conflicts, and is currently runtime-scoped rather
than persisted as a user setting.

User-configurable command hotkeys are a separate native layer. Each script hotkey
has a `hotkey` content key and is registered through `ModLoader.RegisterKey`, then
published into the already-created `GameSettings.keyBindings` array because Lua
scripts load after ModLoader's startup registration pass. Reloading options after
that append restores the player's saved Controls assignment.

ModLoader cannot unregister a key. The registry therefore retains one native
`KeyBinding` slot per canonical hotkey for the process lifetime, while script
unload removes its callback and owner. Redeclaration reattaches to that slot,
preserving the current key code. Labels may be relocalized on reload; ModLoader's
held-repeat policy is immutable for the retained slot and requires a restart to
change. Native hotkeys do not participate in input consumption. Contextual or
consumable commands belong in named input maps.

## Camera and targeting contract

Camera definitions produce a logical pose on ticks. Java interpolates position,
rotation, FOV, and blend weight for rendered frames. Shake is an additive layer
and never mutates the base rig. Native first person is a zero-overhead fallback.

Targeting consumes a detached ray and bounded policy. Results never expose native
objects; they contain hit kind, distance, hit position, face or normal, optional
block state, and an expiring entity handle. Screen rays use the same active render
pose and projection as the camera. Queries reject unloaded space and enforce range
and result caps.

## Lifecycle and thread rules

All mutation and Lua dispatch occurs on the Minecraft game thread. Script unload
removes owner resources generation-safely. A world, player, GUI, or focus change
clears transient input and controller state; persistent package declarations may
be reacquired against the new player. Camera render hooks use immutable snapshots
published by the tick path and do not invoke arbitrary Lua per frame.

No hook replaces `Minecraft.playerController`, inserts a camera proxy into world
entity lists, or changes native gameplay when no layer is active.
