# BetaMoon Examples

These examples form a tutorial as well as a capability reference. Copy the Lua
files you want, or a complete manifested example folder, into
`.minecraft/betamoon/scripts/`. Scripts hot reload after a file save settles, or
through **Reload** on BetaMoon's Scripts screen. Structural tile-entity content
is kept loaded for world safety and requires a Minecraft restart after changes.

Read files in name order. `01_beg`, `02_int`, and `03_adv` sort the beginner,
intermediate, and advanced categories correctly in normal file explorers.
Beginner 02 is a minimal packaged mod. Beginner 11 extends the registered
texture lesson; beginner 22 uses letters for two independent mods that
demonstrate cross-mod exports. Packaged lessons are self-contained folders
with one `betamoon.mod.json`; copy each complete folder
without rearranging its files. Their manifests are the sole source of package
metadata (`name`, `version`, `description`, and `dependencies`); entrypoint Lua
files contain behavior without duplicating those fields.

Most examples use the vanilla texture atlas. Choose different numeric block and
item IDs when another installed mod already owns an example ID. Engine callbacks
on vanilla content and several global events require the BetaMoon Java agent.
Numbered lesson scripts may use the reserved `example` content namespace when
kept under their distributed filenames. If you rename a lesson or adapt it into
your own mod, change its `example:` keys to a namespace of your own.

Named Minecraft values are available through the read-only `betamoon.mc` table,
such as `betamoon.mc.blockMaterials.rock` and
`betamoon.mc.world.biomes.desert`. These constants evaluate to ordinary strings,
so the matching raw strings remain valid when values come from configuration or
another script. BetaMoon-specific choices, such as biome tree policies, live with
their API at `betamoon.worldgen.treeModes`.

## Beginner

Beginner lessons introduce one concept at a time and establish the vocabulary
used by later examples.

| Lesson | File | Topic |
| --- | --- | --- |
| 01 | `01_beg_01_base.lua` | Script metadata and lifecycle hook signatures |
| 02 | `01_beg_02_multifile_mod/` (`betamoon.mod.json`, `main.lua`, `lifecycle.lua`) | Minimal packaged mod structure and a private module |
| 03 | `01_beg_03_simple_block.lua` | Registering a block |
| 04 | `01_beg_04_simple_item.lua` | Registering an item |
| 05 | `01_beg_05_resource_references.lua` | Optional/required lookup, references, and stacks |
| 06 | `01_beg_06_block_query.lua` | Finding registered blocks and reading result lists |
| 07 | `01_beg_07_item_query.lua` | Finding items, tools, and armor |
| 08 | `01_beg_08_simple_override.lua` | Changing one property of an existing resource |
| 09 | `01_beg_09_textures.lua` | Custom block and item textures |
| 10 | `01_beg_10_registered_texture.lua` | Reusable registered PNG for a block and item |
| 11 | `01_beg_11_animated_texture.lua` | Animated version of a shared block and item texture |
| 12 | `01_beg_12_script_config.lua` | Persistent settings, in-game editing, and live changes |
| 13 | `01_beg_13_food.lua` | Food properties |
| 14 | `01_beg_14_drops.lua` | Custom block drops |
| 15 | `01_beg_15_vanilla_recipes.lua` | Shaped, shapeless, and smelting recipes |
| 16 | `01_beg_16_tool.lua` | Tool materials and tool types |
| 17 | `01_beg_17_armor.lua` | Armor materials, pieces, and textures |
| 18 | `01_beg_18_utilities.lua` | Stack and position utilities |
| 19 | `01_beg_19_chat.lua` | Chat output and formatting |
| 20 | `01_beg_20_projectile.lua` | Projectile ammunition and cooldowns |
| 21 | `01_beg_21_ore_generation.lua` | Ore generation in new chunks |
| 22a–22b | `01_beg_22a_module_export.lua`, `01_beg_22b_module_import.lua` | Sharing an exported table between scripts |
| 23 | `01_beg_23_item_subtypes.lua` | Metadata subtypes and render variants |
| 24 | `01_beg_24_builtin_model_item.lua` | Built-in slab model on an item |
| 25 | `01_beg_25_biome_from_default.lua` | Adapting a vanilla biome |
| 26 | `01_beg_26_first_event.lua` | Responding to one global game event |
| 27 | `01_beg_27_first_block_interaction.lua` | Handling a block activation |
| 28 | `01_beg_28_first_item_use.lua` | Handling a consumable item's right-click action |
| 29 | `01_beg_29_click_sound.lua` | Registered WAV and local playback from item use |
| 30 | `01_beg_30_custom_fuel.lua` | Registering an item as vanilla furnace fuel |
| 31 | `01_beg_31_simple_feature_placement.lua` | Separating a reusable feature from its scheduled placement |
| 32 | `01_beg_32_simple_tree_placement.lua` | Scheduling a vanilla-adapter tree with surface conditions |

## Intermediate

Intermediate lessons combine declarations with searches, callbacks, saved block
state, physical behavior, and event-driven logic.

| Lesson | File | Topic |
| --- | --- | --- |
| 01 | `02_int_01_recipe_query.lua` | Querying and temporarily changing recipes |
| 02 | `02_int_02_overrides.lua` | Direct, conditional, prioritized, and bulk overrides |
| 03 | `02_int_03_display_overrides.lua` | Extending a vanilla display tick with `ctx:base()` |
| 04 | `02_int_04_hot_reload_lifecycle.lua` | Script generations and explicit cleanup |
| 05 | `02_int_05_biome_from_scratch.lua` | Declaring a biome from scratch |
| 06 | `02_int_06_events.lua` | Global events and typed event contexts |
| 07 | `02_int_07_input_shortcuts.lua` | Practical keyboard, mouse, and screen events |
| 08 | `02_int_08_block_interactions.lua` | Activation, saved state, mining permission, and queried drops |
| 09 | `02_int_09_attached_blocks.lua` | Floor, wall, and ceiling support |
| 10 | `02_int_10_block_lifecycle.lua` | Placement queries and lifecycle callbacks |
| 11 | `02_int_11_item_interactions.lua` | Item use, consumption, cooldowns, and inventory callbacks |
| 12 | `02_int_12_blockbench_model_item.lua` | Bedrock JSON model, texture, named parts, and item displays |
| 13 | `02_int_13_targeted_item_actions.lua` | Direct block/entity targets and explicit ray tracing |
| 14 | `02_int_14_tool_interactions.lua` | Wrench actions, orientation, and multiple tool classes |
| 15 | `02_int_15_dynamic_tool_callbacks.lua` | Per-block harvesting and mining-speed queries |
| 16 | `02_int_16_block_shapes.lua` | Collision, selection, drawing bounds, and climbing |
| 17 | `02_int_17_facing_model_block.lua` | One built-in stair model rotated by placement state |
| 18 | `02_int_18_launch_pad.lua` | Player filtering and motion-preserving launch behavior |
| 19 | `02_int_19_special_blocks.lua` | Replaceable, translucent, luminous, and unbreakable blocks |
| 20 | `02_int_20_custom_fluid.lua` | Paired fluid blocks, animated appearance, a bucket, and entity/camera policies |
| 21 | `02_int_21_random_and_continuous_ticks.lua` | Default and vanilla random block ticks |
| 22 | `02_int_22_scheduled_and_display_ticks.lua` | Scheduled gameplay ticks and client display ticks |
| 23 | `02_int_23_redstone_switch.lua` | Directional digital redstone output |
| 24 | `02_int_24_first_entity.lua` | Model-backed prop declaration and spawning from an item |
| 25 | `02_int_25_custom_projectile.lua` | Model-backed projectile behavior, ammunition, and impact handling |
| 26 | `02_int_26_pickup_entity.lua` | Typed pickup entities and inventory collection |
| 27 | `02_int_27_configurable_explosion.lua` | Selective explosion effects, attribution, and result snapshots |
| 28 | `02_int_28_direct_feature_placement.lua` | Previewing and atomically placing a feature from an item callback |
| 29 | `02_int_29_composite_and_ordered_features.lua` | Weighted/sequence features and explicit placement dependencies |
| 30 | `02_int_30_named_actions_controller.lua` | Named input actions, configurable controls, and staged player movement |
| 31 | `02_int_31_custom_particles.lua` | Custom PNG particles in a ring around a block, with per-instance rotation and size updates |
| 32 | `02_int_32_local_structure/` | Packaged local JSON structure with a rounded stepped foundation, deterministic loot, and placement |
| 33 | `02_int_33_sunken_cellar/` | Minimal footprint-to-surface excavation for a negatively offset cellar |
| 34 | `02_int_34_excavated_foundation/` | Exact clearance voxels, a named 3D excavation mask, and an authored foundation footprint |

## Advanced

Advanced lessons build complete systems. Lessons 03, 09 through 11, 13, 14, 17
and 20 are manifested packages. Their entrypoints preserve gameplay assets
and, where useful, organize private modules with `require`.

| Lesson | File | Topic |
| --- | --- | --- |
| 01 | `03_adv_01_animated_model_item.lua` | Animation JSON combined with Lua pose control |
| 02 | `03_adv_02_redstone_timer.lua` | Input-edge observation and scheduled output pulses |
| 03 | `03_adv_03_basic_storage/` (`betamoon.mod.json`, `main.lua`, `data.lua`, `layout.lua`) | Private declaration modules combined into saved storage, a container, and a GUI |
| 04 | `03_adv_04_custom_furnace.lua` | A complete smelting machine with a composed private fuel set |
| 05 | `03_adv_05_tile_redstone_controller.lua` | Persistent tile data controlling redstone output |
| 06 | `03_adv_06_visual_portal.lua` | Controllerless custom frame with item activation and owned, pass-through portal panels |
| 07 | `03_adv_07_gui_showcase.lua` | Container GUI elements, interactive controls, temporary state, and synchronized presentation |
| 08 | `03_adv_08_animated_machine.lua` | Animated block variants, material slots, glow, and sound |
| 09 | `03_adv_09_simple_alloy/` (`betamoon.mod.json`, `main.lua`, `recipe_type.lua`, `recipes.lua`) | Simple named-slot recipe types, recipes, and a processing machine |
| 10 | `03_adv_10_contextual_processor/` (`betamoon.mod.json`, `main.lua`, `recipe_type.lua`, `recipes.lua`) | Heat/power conditions and commit-time context revalidation |
| 11 | `03_adv_11_advanced_fabrication/` (`betamoon.mod.json`, `main.lua`, `recipe_types.lua`, `recipes.lua`) | Pools, grids, custom matching, bindings, and atomic processing |
| 12 | `03_adv_12_charcoal_kiln.lua` | Exact brick multiblock, fuel-free charcoal processing, flame progress and a warning tooltip |
| 13 | `03_adv_13_regional_structures/` | Regional assembly with bounded/authored foundations, selector-conforming roads, and lookup |
| 14 | `03_adv_14_matcher_cookbook/` (`betamoon.mod.json`, `main.lua`, `recipe_type.lua`) | A context-driven custom allocation policy in a working machine |
| 15 | `03_adv_15_entity_data.lua` | Model-backed entity with persistent interaction data |
| 16 | `03_adv_16_native_living_ai.lua` | Native living behavior composed with custom Lua AI |
| 17 | `03_adv_17_rolling_meadows/` | JSON terrain preset with bounded hill noise, a meadow biome, caves, coal and oaks |
| 18 | `03_adv_18_manual_multipart_ai.lua` | Fully manual multipart creature behavior and hit regions |
| 19 | `03_adv_19_energy_network.lua` | GUI-guided generation, nearest-first flow, bounded rates, endpoint consumers, and adjacent cable grids |
| 20 | `03_adv_20_hollow_marches/` | Spawn-relative region fields, blended terraced mesas, stratified materials, cave sheets, authored heights and diagnostics |
| 21 | `03_adv_21_flat_dimension.lua` | Superflat custom dimension, reusable item/block activation and persistent return travel |
| 22 | `03_adv_22_wireless_trigger.lua` | Editable wireless channels, transmitter/receiver roles, retained state, and pulses |
| 23 | `03_adv_23_world_service_and_data.lua` | Persistent per-world services and detached world, chunk, and player views |

## Trying the interactive examples

| Lesson | Content and use |
| --- | --- |
| Beginner 10 | **Mosaic Block** (232) and **Mosaic Token** (5030): compare one registered texture on both. |
| Beginner 11 | **Pulsing Crystal** block (241) and item (5048): compare their synchronized frames in the world, inventory, and hand. |
| Beginner 12 | **Script Configuration**: select the script on the Scripts screen, click the gear beside its icon, edit the greeting, and Save to see the change in chat. |
| Beginner 20 | **Snowball Launcher** (5023): two iron ingots + snowball. Carry snowball ammunition and right-click. |
| Beginner 24 | **Slab Model Item** (5031): compare its 3D model in inventory, hand, and on the ground. |
| Beginner 27 | **Greeting Block** (228): cobblestone + stick. Place and right-click it to report its position. |
| Beginner 28 | **Signal Bell** (5028): sugar + redstone makes four. Right-click to play its sound and consume one. |
| Beginner 29 | **Clicker** (5032): right-click to hear the local WAV. |
| Beginner 30 | **Compressed Coal** (5041): eight coal around clay; burns for 12,800 ticks in a vanilla furnace. |
| Intermediate 08 | **Interaction Block** (210): cobblestone + stick. Right-click toggles activity; sneak-right-click locks mining. |
| Intermediate 09 | **Attached Lamp** (217): stone + glowstone dust makes four. Attach one to any solid face and remove its support. |
| Intermediate 10 | **Lifecycle Observer** (218): stone + paper. Click it, alter a neighbor, collide with it, then break or explode it. |
| Intermediate 11 | **Healing Powder** (5020): sugar + wheat makes two. Right-click to heal with a one-second cooldown. |
| Intermediate 12 | **Desk Lamp Model Item** (5033): compare its exported model in three item contexts. |
| Intermediate 13 | **Example Surveyor** (5026): iron + redstone. Use it on a block, in the air, or on an entity. |
| Intermediate 14 | **Wrench**, **Multi-tool**, and **Practice Block** (5021, 5022, 211): craft from iron, stick, and cobblestone. |
| Intermediate 15 | **Adaptive Pick** (5027): diamond over two sticks. Compare ores, obsidian, and ordinary stone. |
| Intermediate 16 | **Climbing Post** (215): planks + stick makes four. Stack them and move against their narrow shape. |
| Intermediate 17 | **Facing Stair Display** (233): place from different directions and walk over its rotated stair collision. |
| Intermediate 18 | **Launch Pad** (214): planks + redstone. Walk onto it while moving to see horizontal speed preserved. |
| Intermediate 19 | Blocks 219–221: obtain the sprout, luminous glass, and sealed casing through a creative/debug inventory. |
| Intermediate 20 | **Acid Bucket** (5047): enable the Java agent, obtain the filled bucket in creative mode, place a source, then enter it to compare movement, damage, and green fog with water. |
| Intermediate 21 | Blocks 222–223: obtain both through a creative/debug inventory and compare their update timing. |
| Intermediate 23 | **Directional Switch** (212): stone + redstone. Toggle it and connect wire to its output face. |
| Intermediate 24 | **Stone Sample Entity**: obtain Sample Placer (5035), copy `builtin_model_item/`, place it, then punch it to recover the placer. |
| Intermediate 25 | **Pebble Launcher** (5037): copy `entity_examples/`, carry cobblestone, and fire a custom projectile. |
| Intermediate 26 | **Gem Dropper** (5038): place a typed pickup and collect its full stack. |
| Intermediate 27 | **Configurable Charge** (5042): right-click for a concussive blast or sneak-right-click for demolition. |
| Intermediate 28 | **Builder's Rod** (5044): stick + glowstone dust. Use it on the top of a block with four clear blocks above it. |
| Intermediate 31 | **Particle Ring Block** (242): copy `custom_particles/` with the script, enable the Java agent, craft stone + redstone dust, then place it nearby to see rotating cyan sparks in a ring above its top. |
| Intermediate 32 | Copy the complete package and explore newly generated Overworld chunks for a compact shrine with a glowstone beacon. |
| Intermediate 33 | Copy the complete package and explore new dry-land chunks for buried cellars whose footprint is excavated up to the original surface. |
| Intermediate 34 | Copy the complete package and explore new dry-land chunks for partially buried chambers with exact authored interior and entrance excavation. |
| Advanced 01 | **Clockwork Bird Model Item** (5034): observe clip animation with Lua head movement. |
| Advanced 02 | **Pulse Timer** (213): cobblestone + redstone. A rising north input produces a one-second south output. |
| Advanced 03 | **Basic Storage** (224): chest surrounded by planks. Its screen reports occupied saved slots. |
| Advanced 04 | **Fast Furnace** (204): vanilla furnace surrounded by iron. It smelts in 100 ticks and also accepts redstone as its private fuel. |
| Advanced 05 | **Tile Redstone Controller** (225): insert a control key, then use input edges to toggle persistent output. |
| Advanced 06 | **Visual Portal Frame** (243): obsidian + redstone. Build the complete 4x5 frame from these blocks, leave a 2x3 opening clear, and use flint and steel against an inner frame face. The purple panels never teleport. |
| Advanced 07 | **GUI Showcase** (208): chest + redstone. Use its Page and Sample slots to explore five pages, including interactive controls. |
| Advanced 08 | **Animated Machine** (234): right-click to toggle its rotor, glow, and click. |
| Advanced 09 | **Alloy Furnace** (209): see the simple-alloy table below. |
| Advanced 10 | **Contextual Processor** (226): process dirt cold/unpowered, or heat sand with fuel and redstone. |
| Advanced 11 | **Advanced Fabricator** (216): see the fabrication table below. |
| Advanced 12 | **Charcoal Kiln** (246): furnace surrounded by eight brick blocks. Complete the exact 3x3x3 shell below, insert any vanilla log, and collect charcoal without fuel. Hover the warning triangle when the structure is incomplete. |
| Advanced 13 | Copy the complete package, craft **Ruin Surveyor** (5045) from a compass and paper, use it on a block, then explore toward the reported branching Path Ruins. |
| Advanced 14 | **Focused Press** (227): its matcher prefers material slot 1 unpowered and slot 5 powered. |
| Advanced 15 | **Data Totem**: obtain Data Totem Placer (5036), place and click it to check saved data, then punch it to recover the placer. |
| Advanced 16 | **Clockwork Watcher** (5039): copy `animated_model_item/`, place a living creature, and test its wandering and retaliation. |
| Advanced 17 | Copy `03_adv_17_rolling_meadows/` and select **Rolling Meadows** in Create World; compare the JSON noise, height, caves and population settings. |
| Advanced 18 | **Manual Guardian** (5040): copy `animated_model_item/`, place it, and inspect Lua-controlled decisions and optional head/wing hitboxes. |
| Advanced 19 | Connect an **Energy Generator**, **Energy Consumer**, and **Energy Battery** with an **Energy Cable** grid. Attach the battery directly to the grid rather than through the consumer: consumer faces accept energy but never bridge two cable grids. Right-click every block to see its live values and per-tick limits. Energy behaves like consumed flow: the nearest consumers receive their operating energy first, so distant consumers may remain unpowered during a shortage. Buffers fill and batteries charge only after all operating needs are met. Remove the generator to see battery backup. |
| Advanced 20 | Copy `03_adv_20_hollow_marches/`, select **Hollow Marches**, and explore outward from spawn. Craft **Marches Surveyor** (5050) from a compass, paper and redstone; sneak-use it for a bounded raw preview export. |
| Advanced 21 | Craft a **Flat Dimension Anchor** (247) from obsidian + a lapis block, and a **Flat Dimension Key** (5051) from a gold ingot + diamond. Place the anchor and use the key on it to enter **Flat Dimension**. Look down and use the key on the generated spawn anchor to return. Requires singleplayer and the Java agent. |

## Multi-file lesson details

- Beginner 02 is one packaged mod. Its manifest selects `main.lua`; the
  entrypoint uses `require("lifecycle")` to load its private module. It deliberately
  registers nothing, making it a minimal structure to copy before adding content.

- Beginner 22 requires both loose scripts and deliberately treats them as separate
  mods. The exporter publishes a public table through `betamoon.modules`; the
  importer declares a dependency and reads that cross-mod export.

- Advanced 03, 09 through 11, 13, 14, 17 and 20 each demonstrate a packaged mod. Their
  manifests select `main.lua`, while `require` loads private declarations and
  registration functions from neighboring files where the lesson needs them.
  Every file shares the package's lifecycle and resource owner. Copy the whole
  folder into `betamoon/scripts`; restart after structural declaration or gameplay-data edits.

- Advanced 03 keeps its persistent inventory/data schema in `data.lua` and its
  container/GUI layout in `layout.lua`. `main.lua` composes both declarations and
  owns every registered resource.

- Advanced 09 creates these simple alloy recipes:

  | Base | Additive | Retained mold | Results |
  | --- | --- | --- | --- |
  | 1 gold ore | 1 coal or charcoal | 1 stick | 3 gold ingots + 1 cobblestone |
  | 4 dirt or 4 cobblestone | 1 coal | Empty | 1 stone |

- Advanced 10 demonstrates conditions independently of inventory matching:

  | Input | Machine context | Result |
  | --- | --- | --- |
  | 1 dirt | Heat at most 100 and no power | 4 clay balls |
  | 1 sand | Heat at least 400 and powered | 2 glass |

  The processor passes a validated context during discovery and fresh values to
  `canApply` and `apply`, so a lost signal cannot commit a now-invalid hot recipe.

- Advanced 11 uses one shared 3x3 work area:

  | Rule | Work area | Result |
  | --- | --- | --- |
  | Unordered pool | 2 sand + 2 gravel in any work slots; catalyst empty | 4 clay plus flint and cobblestone byproducts |
  | Transformable grid | Iron in four corners and a stick in the center | 1 iron block |
  | Custom sequence | Iron immediately followed by coal in flattened slot order | 1 gold ingot |

  `recipe_types.lua` explains detached matcher snapshots and validated `plan:use`
  allocations. `main.lua` explains bindings, candidate order, signatures,
  preflight checks, and atomic application.

- Advanced 14 narrows custom matching to one clear policy. One recipe consumes
  four cobblestone from a single chosen material slot; the die recipe consumes four
  sand while retaining a stick. Extra occupied pool slots are allowed and untouched.

## Flat Dimension example

Advanced 21 is a single Lua file and requires singleplayer with the Java agent.
Copy `03_adv_21_flat_dimension.lua` into the scripts directory. No extra assets are needed.

1. Craft one obsidian block with one lapis block, in any arrangement, to make a **Flat Dimension Anchor**.
2. Craft one gold ingot with one diamond to make a **Flat Dimension Key**.
3. Place the anchor and right-click it with the key to enter **Flat Dimension**.
4. You arrive standing on its return anchor at `(0, 3, 0)`, with your feet at Y=4.
   Look down and right-click that anchor with the same key to return to where you left.

The key is not consumed, has no durability or damage variants, and has a one-second use cooldown.
Return history survives saving and reopening the world. If no return point exists, the key instead
returns you to a safe Overworld spawn. Death in Flat Dimension also respawns you in the Overworld.
The generated return anchor resists explosions and cannot be mined in survival; keep the two blocks
above it clear for safe entry. Other anchors remain mineable.

The terrain is bedrock at Y=0, two dirt layers, then grass at Y=3. Caves and population are disabled.
For the normal demonstration, leave **World Generation: Minecraft** selected when creating your
Overworld, then use the key. The example's **Flat Dimension** generator is also available as a preset.

## Assets

- For each new asset lesson, copy its matching folder beside the Lua script.
  Beginners 10 and 11 and Intermediate 20 use `example/`; other lessons use
  `builtin_model_item/`, `click_sound/`, `blockbench_model_item/`,
  `facing_model_block/`, `animated_model_item/`, `animated_machine/`, or `entity_examples/`.
  Keep the folder name and contents unchanged.

- Beginner 09 needs `example_block.png`. Beginner 17 needs the six
  `example_armor_*.png` files. Copy those files beside their scripts.

- Advanced 07 needs the complete `gui_showcase/` directory beside its script.
  Leave **Page** empty for an automatic tour, or insert 1–4 items to hold a page.
  Put any item in **Sample** to preview it. The adjacent comments document all four
  pages and the `BACKGROUND` setting.

## Multiblock examples

Advanced 06 uses the Java agent and a custom frame four blocks wide and five blocks high.
Craft obsidian and redstone into Visual Portal Frame blocks (243) and build the entire frame from them.
Its layers, from bottom to top, are `?@F?`, `FIIF`, `FIIF`, `FIIF`, `?FF?`: both `@` and `F` require
that same frame block, `I` is the opening, and `?` corners are optional. No special controller position,
placement order, facing direction, or tile entity is required. Use flint and steel on an inner frame face.
The surface (244) animates and emits portal particles, but passing through has no travel effect.
Breaking the frame or a panel extinguishes the surface; repair it and activate again. Cleanup preserves
player replacements. The panels use Minecraft's portal texture, so no extra asset directory is needed.

Advanced 12 uses the following exact layers, from bottom to top and with front rows listed first:

| Layer | Front row | Middle row | Back row |
| --- | --- | --- | --- |
| Bottom | `BBB` | `B.B` | `BBB` |
| Middle | `B@B` | `B.B` | `BBB` |
| Top | `BBB` | `BBB` | `BBB` |

`B` is a brick block, `.` must be air, and `@` is the outward-facing Charcoal Kiln controller.
The finished structure uses 24 brick blocks and one controller. It processes oak, spruce and birch
logs into charcoal, one log per 200 game ticks. The two-slot screen has no fuel slot. The vanilla
flame fills at the furnace arrow's position; a small warning triangle identifies an incomplete or
unverified structure. A full output pauses processing, while changed input or an invalid structure
resets progress. Both multiblock scripts require a restart after edits.

When updating these lessons, replace older advanced-script copies so renamed lessons are not loaded twice.
