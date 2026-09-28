# BetaMoon Examples

These examples form a tutorial as well as a capability reference. Copy the Lua
files you want, or a complete manifested example folder, into
`.minecraft/lua_scripts/`. Scripts hot reload after a file save settles, or
through **Reload** on BetaMoon's Scripts screen. Structural tile-entity content
is kept loaded for world safety and requires a Minecraft restart after changes.

Read files in name order. `01_beg`, `02_int`, and `03_adv` sort the beginner,
intermediate, and advanced categories correctly in normal file explorers.
Beginner 02 is a minimal packaged mod. Beginner 20 uses letters for two
independent mods that demonstrate cross-mod exports. Packaged lessons are
self-contained folders with one `betamoon.mod.json`; copy each complete folder
without rearranging its files.

Most examples use the vanilla texture atlas. Choose different numeric block and
item IDs when another installed mod already owns an example ID. Engine callbacks
on vanilla content and several global events require the BetaMoon Java agent.

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
| 11 | `01_beg_11_food.lua` | Food properties |
| 12 | `01_beg_12_drops.lua` | Custom block drops |
| 13 | `01_beg_13_vanilla_recipes.lua` | Shaped, shapeless, and smelting recipes |
| 14 | `01_beg_14_tool.lua` | Tool materials and tool types |
| 15 | `01_beg_15_armor.lua` | Armor materials, pieces, and textures |
| 16 | `01_beg_16_utilities.lua` | Stack and position utilities |
| 17 | `01_beg_17_chat.lua` | Chat output and formatting |
| 18 | `01_beg_18_projectile.lua` | Projectile ammunition and cooldowns |
| 19 | `01_beg_19_ore_generation.lua` | Ore generation in new chunks |
| 20a–20b | `01_beg_20a_module_export.lua`, `01_beg_20b_module_import.lua` | Sharing an exported table between scripts |
| 21 | `01_beg_21_item_subtypes.lua` | Metadata subtypes and render variants |
| 22 | `01_beg_22_builtin_model_item.lua` | Built-in slab model on an item |
| 23 | `01_beg_23_biome_from_default.lua` | Adapting a vanilla biome |
| 24 | `01_beg_24_first_event.lua` | Responding to one global game event |
| 25 | `01_beg_25_first_block_interaction.lua` | Handling a block activation |
| 26 | `01_beg_26_first_item_use.lua` | Handling a consumable item's right-click action |
| 27 | `01_beg_27_click_sound.lua` | Registered WAV and local playback from item use |
| 28 | `01_beg_28_custom_fuel.lua` | Registering an item as vanilla furnace fuel |
| 29 | `01_beg_29_simple_feature_placement.lua` | Separating a reusable feature from its scheduled placement |
| 30 | `01_beg_30_simple_tree_placement.lua` | Scheduling a vanilla-adapter tree with surface conditions |

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
| 20 | `02_int_20_random_and_continuous_ticks.lua` | Default and vanilla random block ticks |
| 21 | `02_int_21_scheduled_and_display_ticks.lua` | Scheduled gameplay ticks and client display ticks |
| 22 | `02_int_22_redstone_switch.lua` | Directional digital redstone output |
| 23 | `02_int_23_first_entity.lua` | Model-backed prop declaration and spawning from an item |
| 24 | `02_int_24_custom_projectile.lua` | Model-backed projectile behavior, ammunition, and impact handling |
| 25 | `02_int_25_pickup_entity.lua` | Typed pickup entities and inventory collection |
| 26 | `02_int_26_configurable_explosion.lua` | Selective explosion effects, attribution, and result snapshots |
| 27 | `02_int_27_direct_feature_placement.lua` | Previewing and atomically placing a feature from an item callback |
| 28 | `02_int_28_composite_and_ordered_features.lua` | Weighted/sequence features and explicit placement dependencies |

## Advanced

Advanced lessons build complete systems. Lessons 03, 08 through 11, and 18
through 20 are manifested packages. Their entrypoints preserve gameplay assets
and, where useful, organize private modules with `require`.

| Lesson | File | Topic |
| --- | --- | --- |
| 01 | `03_adv_01_animated_model_item.lua` | Animation JSON combined with Lua pose control |
| 02 | `03_adv_02_redstone_timer.lua` | Input-edge observation and scheduled output pulses |
| 03 | `03_adv_03_basic_storage/` (`betamoon.mod.json`, `main.lua`, `data.lua`, `layout.lua`) | Private declaration modules combined into saved storage, a container, and a GUI |
| 04 | `03_adv_04_custom_furnace.lua` | A complete smelting machine with a composed private fuel set |
| 05 | `03_adv_05_tile_redstone_controller.lua` | Persistent tile data controlling redstone output |
| 06 | `03_adv_06_gui_showcase.lua` | Container GUI elements, interactive controls, session state, and synchronized presentation |
| 07 | `03_adv_07_animated_machine.lua` | Animated block variants, material slots, glow, and sound |
| 08 | `03_adv_08_simple_alloy/` (`betamoon.mod.json`, `main.lua`, `recipe_type.lua`, `recipes.lua`) | Simple named-slot recipe types, recipes, and a processing machine |
| 09 | `03_adv_09_contextual_processor/` (`betamoon.mod.json`, `main.lua`, `recipe_type.lua`, `recipes.lua`) | Heat/power conditions and commit-time context revalidation |
| 10 | `03_adv_10_advanced_fabrication/` (`betamoon.mod.json`, `main.lua`, `recipe_types.lua`, `recipes.lua`) | Pools, grids, custom matching, bindings, and atomic processing |
| 11 | `03_adv_11_matcher_cookbook/` (`betamoon.mod.json`, `main.lua`, `recipe_type.lua`) | A context-driven custom allocation policy in a working machine |
| 12 | `03_adv_12_entity_data.lua` | Model-backed entity with persistent interaction data |
| 13 | `03_adv_13_native_living_ai.lua` | Native living behavior composed with custom Lua AI |
| 14 | `03_adv_14_manual_multipart_ai.lua` | Fully manual multipart creature behavior and hit regions |
| 15 | `03_adv_15_energy_network.lua` | GUI-guided generation, nearest-first flow, bounded rates, endpoint consumers, and adjacent cable grids |
| 16 | `03_adv_16_wireless_trigger.lua` | Editable wireless channels, transmitter/receiver roles, retained state, and pulses |
| 17 | `03_adv_17_world_service_and_data.lua` | Persistent per-world services and detached world, chunk, and player views |
| 18 | `03_adv_18_local_structure/` | Packaged local JSON structure, palette variants, transforms, processors, and placement |
| 19 | `03_adv_19_regional_structures/` | Connector-pool regional assembly and non-loading structure lookup |
| 20 | `03_adv_20_complete_worldgen_pack/` | Modular surface, features, structure, biome decorators, and climate source |

## Trying the interactive examples

| Lesson | Content and use |
| --- | --- |
| Beginner 10 | **Mosaic Block** (232) and **Mosaic Token** (5030): compare one registered texture on both. |
| Beginner 18 | **Snowball Launcher** (5023): two iron ingots + snowball. Carry snowball ammunition and right-click. |
| Beginner 22 | **Slab Model Item** (5031): compare its 3D model in inventory, hand, and on the ground. |
| Beginner 25 | **Greeting Block** (228): cobblestone + stick. Place and right-click it to report its position. |
| Beginner 26 | **Signal Bell** (5028): sugar + redstone makes four. Right-click to play its sound and consume one. |
| Beginner 27 | **Clicker** (5032): right-click to hear the local WAV. |
| Beginner 28 | **Compressed Coal** (5041): eight coal around clay; burns for 12,800 ticks in a vanilla furnace. |
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
| Intermediate 20 | Blocks 222–223: obtain both through a creative/debug inventory and compare their update timing. |
| Intermediate 22 | **Directional Switch** (212): stone + redstone. Toggle it and connect wire to its output face. |
| Intermediate 23 | **Stone Sample Entity**: obtain Sample Placer (5035), copy `builtin_model_item/`, place it, then punch it to recover the placer. |
| Intermediate 24 | **Pebble Launcher** (5037): copy `entity_examples/`, carry cobblestone, and fire a custom projectile. |
| Intermediate 25 | **Gem Dropper** (5038): place a typed pickup and collect its full stack. |
| Intermediate 26 | **Configurable Charge** (5042): right-click for a concussive blast or sneak-right-click for demolition. |
| Intermediate 27 | **Builder's Rod** (5044): stick + glowstone dust. Use it on the top of a block with four clear blocks above it. |
| Advanced 01 | **Clockwork Bird Model Item** (5034): observe clip animation with Lua head movement. |
| Advanced 02 | **Pulse Timer** (213): cobblestone + redstone. A rising north input produces a one-second south output. |
| Advanced 03 | **Basic Storage** (224): chest surrounded by planks. Its screen reports occupied saved slots. |
| Advanced 04 | **Fast Furnace** (204): vanilla furnace surrounded by iron. It smelts in 100 ticks and also accepts redstone as its private fuel. |
| Advanced 05 | **Tile Redstone Controller** (225): insert a control key, then use input edges to toggle persistent output. |
| Advanced 06 | **GUI Showcase** (208): chest + redstone. Use its Page and Sample slots to explore five pages, including interactive controls. |
| Advanced 07 | **Animated Machine** (234): right-click to toggle its rotor, glow, and click. |
| Advanced 08 | **Alloy Furnace** (209): see the simple-alloy table below. |
| Advanced 09 | **Contextual Processor** (226): process dirt cold/unpowered, or heat sand with fuel and redstone. |
| Advanced 10 | **Advanced Fabricator** (216): see the fabrication table below. |
| Advanced 11 | **Focused Press** (227): its matcher prefers material slot 1 unpowered and slot 5 powered. |
| Advanced 12 | **Data Totem**: obtain Data Totem Placer (5036), place and click it to check saved data, then punch it to recover the placer. |
| Advanced 13 | **Clockwork Watcher** (5039): copy `animated_model_item/`, place a living creature, and test its wandering and retaliation. |
| Advanced 14 | **Manual Guardian** (5040): copy `animated_model_item/`, place it, and inspect Lua-controlled decisions and optional head/wing hitboxes. |
| Advanced 15 | Connect an **Energy Generator**, **Energy Consumer**, and **Energy Battery** with an **Energy Cable** grid. Attach the battery directly to the grid rather than through the consumer: consumer faces accept energy but never bridge two cable grids. Right-click every block to see its live values and per-tick limits. Energy behaves like consumed flow: the nearest consumers receive their operating energy first, so distant consumers may remain unpowered during a shortage. Buffers fill and batteries charge only after all operating needs are met. Remove the generator to see battery backup. |
| Advanced 19 | Copy the complete package, craft **Ruin Surveyor** (5045) from a compass and paper, use it on a block, then explore toward the reported candidate. |

## Multi-file lesson details

- Beginner 02 is one packaged mod. Its manifest selects `main.lua`; the
  entrypoint uses `require("lifecycle")` to load its private module. It deliberately
  registers nothing, making it a minimal structure to copy before adding content.

- Beginner 20 requires both loose scripts and deliberately treats them as separate
  mods. The exporter publishes a public table through `betamoon.modules`; the
  importer declares a dependency and reads that cross-mod export.

- Advanced 03, 08 through 11, and 18 through 20 each demonstrate a packaged mod. Their
  manifests select `main.lua`, while `require` loads private declarations and
  registration functions from neighboring files where the lesson needs them.
  Every file shares the package's lifecycle and resource owner. Copy the whole
  folder into `lua_scripts`; restart after structural declaration or gameplay-data edits.

- Advanced 03 keeps its persistent inventory/data schema in `data.lua` and its
  container/GUI layout in `layout.lua`. `main.lua` composes both declarations and
  owns every registered resource.

- Advanced 08 creates these simple alloy recipes:

  | Base | Additive | Retained mold | Results |
  | --- | --- | --- | --- |
  | 1 gold ore | 1 coal or charcoal | 1 stick | 3 gold ingots + 1 cobblestone |
  | 4 dirt or 4 cobblestone | 1 coal | Empty | 1 stone |

- Advanced 09 demonstrates conditions independently of inventory matching:

  | Input | Machine context | Result |
  | --- | --- | --- |
  | 1 dirt | Heat at most 100 and no power | 4 clay balls |
  | 1 sand | Heat at least 400 and powered | 2 glass |

  The processor passes a validated context during discovery and fresh values to
  `canApply` and `apply`, so a lost signal cannot commit a now-invalid hot recipe.

- Advanced 10 uses one shared 3x3 work area:

  | Rule | Work area | Result |
  | --- | --- | --- |
  | Unordered pool | 2 sand + 2 gravel in any work slots; catalyst empty | 4 clay plus flint and cobblestone byproducts |
  | Transformable grid | Iron in four corners and a stick in the center | 1 iron block |
  | Custom sequence | Iron immediately followed by coal in flattened slot order | 1 gold ingot |

  `recipe_types.lua` explains detached matcher snapshots and validated `plan:use`
  allocations. `main.lua` explains bindings, candidate order, signatures,
  preflight checks, and atomic application.

- Advanced 11 narrows custom matching to one clear policy. One recipe consumes
  four cobblestone from a single chosen material slot; the die recipe consumes four
  sand while retaining a stick. Extra occupied pool slots are allowed and untouched.

## Assets

- For each new asset lesson, copy its matching folder beside the Lua script: `example/` for Beginner 10,
  `builtin_model_item/`, `click_sound/`, `blockbench_model_item/`,
  `facing_model_block/`, `animated_model_item/`, `animated_machine/`, or `entity_examples/`.
  Keep the folder name and contents unchanged.

- Beginner 09 needs `example_block.png`. Beginner 15 needs the six
  `example_armor_*.png` files. Copy those files beside their scripts.

- Advanced 06 needs the complete `gui_showcase/` directory beside its script.
  Leave **Page** empty for an automatic tour, or insert 1–4 items to hold a page.
  Put any item in **Sample** to preview it. The adjacent comments document all four
  pages and the `BACKGROUND` setting.
