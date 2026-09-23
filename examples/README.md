# BetaMoon Examples

These examples form a tutorial as well as a capability reference. Copy the Lua
files you want into `.minecraft/lua_scripts/`. Scripts hot reload after a file
save settles, or through **Reload** on BetaMoon's Scripts screen. Structural
tile-entity content is kept loaded for world safety and requires a Minecraft
restart after changes.

Read files in name order. `01_beg`, `02_int`, and `03_adv` sort the beginner,
intermediate, and advanced categories correctly in normal file explorers.
Letters after a lesson number identify files that belong to one lesson. Unless
the comments say otherwise, each unlettered example can be loaded independently.

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
| 02 | `01_beg_02_simple_block.lua` | Registering a block |
| 03 | `01_beg_03_simple_item.lua` | Registering an item |
| 04 | `01_beg_04_resource_references.lua` | Optional/required lookup, references, and stacks |
| 05 | `01_beg_05_block_query.lua` | Finding registered blocks and reading result lists |
| 06 | `01_beg_06_item_query.lua` | Finding items, tools, and armor |
| 07 | `01_beg_07_simple_override.lua` | Changing one property of an existing resource |
| 08 | `01_beg_08_textures.lua` | Custom block and item textures |
| 09 | `01_beg_09_registered_texture.lua` | Reusable registered PNG for a block and item |
| 10 | `01_beg_10_food.lua` | Food properties |
| 11 | `01_beg_11_drops.lua` | Custom block drops |
| 12 | `01_beg_12_vanilla_recipes.lua` | Shaped, shapeless, and smelting recipes |
| 13 | `01_beg_13_tool.lua` | Tool materials and tool types |
| 14 | `01_beg_14_armor.lua` | Armor materials, pieces, and textures |
| 15 | `01_beg_15_utilities.lua` | Stack and position utilities |
| 16 | `01_beg_16_chat.lua` | Chat output and formatting |
| 17 | `01_beg_17_projectile.lua` | Projectile ammunition and cooldowns |
| 18 | `01_beg_18_ore_generation.lua` | Ore generation in new chunks |
| 19a–19b | `01_beg_19a_module_export.lua`, `01_beg_19b_module_import.lua` | Sharing an exported table between scripts |
| 20 | `01_beg_20_item_subtypes.lua` | Metadata subtypes and render variants |
| 21 | `01_beg_21_builtin_model_item.lua` | Built-in slab model on an item |
| 22 | `01_beg_22_biome_from_default.lua` | Adapting a vanilla biome |
| 23 | `01_beg_23_first_event.lua` | Responding to one global game event |
| 24 | `01_beg_24_first_block_interaction.lua` | Handling a block activation |
| 25 | `01_beg_25_first_item_use.lua` | Handling a consumable item's right-click action |
| 26 | `01_beg_26_click_sound.lua` | Registered WAV and local playback from item use |
| 27 | `01_beg_27_custom_fuel.lua` | Registering an item as vanilla furnace fuel |

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

## Advanced

Advanced lessons build complete systems. Keep every file from a lettered lesson
together and read its dependency comments before loading it.

| Lesson | File | Topic |
| --- | --- | --- |
| 01 | `03_adv_01_animated_model_item.lua` | Animation JSON combined with Lua pose control |
| 02 | `03_adv_02_redstone_timer.lua` | Input-edge observation and scheduled output pulses |
| 03a–03c | `03_adv_03a_basic_storage_data.lua`, `03_adv_03b_basic_storage_layout.lua`, `03_adv_03c_basic_storage_block.lua` | Modular declarations combined into saved storage, a container, and a GUI |
| 04 | `03_adv_04_custom_furnace.lua` | A complete smelting machine with a composed private fuel set |
| 05 | `03_adv_05_tile_redstone_controller.lua` | Persistent tile data controlling redstone output |
| 06 | `03_adv_06_gui_showcase.lua` | Container GUI elements and synchronized presentation |
| 07 | `03_adv_07_animated_machine.lua` | Animated block variants, material slots, glow, and sound |
| 08a–08c | `03_adv_08a_simple_alloy_recipe_type.lua`, `03_adv_08b_simple_alloy_recipes.lua`, `03_adv_08c_simple_alloy_furnace.lua` | Simple named-slot recipe types, recipes, and a processing machine |
| 09a–09c | `03_adv_09a_contextual_recipe_type.lua`, `03_adv_09b_contextual_recipes.lua`, `03_adv_09c_contextual_processor.lua` | Heat/power conditions and commit-time context revalidation |
| 10a–10c | `03_adv_10a_advanced_fabrication_types.lua`, `03_adv_10b_advanced_fabrication_recipes.lua`, `03_adv_10c_advanced_fabricator.lua` | Pools, grids, custom matching, bindings, and atomic processing |
| 11a–11b | `03_adv_11a_matcher_cookbook_type.lua`, `03_adv_11b_matcher_cookbook_press.lua` | A context-driven custom allocation policy in a working machine |
| 12 | `03_adv_12_entity_data.lua` | Model-backed entity with persistent interaction data |

## Trying the interactive examples

| Lesson | Content and use |
| --- | --- |
| Beginner 09 | **Mosaic Block** (232) and **Mosaic Token** (5030): compare one registered texture on both. |
| Beginner 17 | **Snowball Launcher** (5023): two iron ingots + snowball. Carry snowball ammunition and right-click. |
| Beginner 21 | **Slab Model Item** (5031): compare its 3D model in inventory, hand, and on the ground. |
| Beginner 24 | **Greeting Block** (228): cobblestone + stick. Place and right-click it to report its position. |
| Beginner 25 | **Signal Bell** (5028): sugar + redstone makes four. Right-click to play its sound and consume one. |
| Beginner 26 | **Clicker** (5032): right-click to hear the local WAV. |
| Beginner 27 | **Compressed Coal** (5041): eight coal around clay; burns for 12,800 ticks in a vanilla furnace. |
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
| Advanced 01 | **Clockwork Bird Model Item** (5034): observe clip animation with Lua head movement. |
| Advanced 02 | **Pulse Timer** (213): cobblestone + redstone. A rising north input produces a one-second south output. |
| Advanced 03 | **Basic Storage** (224): chest surrounded by planks. Its screen reports occupied saved slots. |
| Advanced 04 | **Fast Furnace** (204): vanilla furnace surrounded by iron. It smelts in 100 ticks and also accepts redstone as its private fuel. |
| Advanced 05 | **Tile Redstone Controller** (225): insert a control key, then use input edges to toggle persistent output. |
| Advanced 06 | **GUI Showcase** (208): chest + redstone. Use its Page and Sample slots to explore the gallery. |
| Advanced 07 | **Animated Machine** (234): right-click to toggle its rotor, glow, and click. |
| Advanced 08 | **Alloy Furnace** (209): see the simple-alloy table below. |
| Advanced 09 | **Contextual Processor** (226): process dirt cold/unpowered, or heat sand with fuel and redstone. |
| Advanced 10 | **Advanced Fabricator** (216): see the fabrication table below. |
| Advanced 11 | **Focused Press** (227): its matcher prefers material slot 1 unpowered and slot 5 powered. |
| Advanced 12 | **Data Totem**: obtain Data Totem Placer (5036), place and click it to check saved data, then punch it to recover the placer. |
| Advanced 13 | **Clockwork Watcher** (5039): copy `animated_model_item/`, place a living creature, and test its wandering and retaliation. |
| Advanced 14 | **Manual Guardian** (5040): copy `animated_model_item/`, place it, and inspect Lua-controlled decisions and optional head/wing hitboxes. |

## Multi-file lesson details

- Beginner 19 requires both module files. The exporter constructs its local public
  table inside `modInit` and publishes it as the final initialization action.

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

  Its type file explains detached matcher snapshots and validated `plan:use`
  allocations. Its machine file explains bindings, candidate order, signatures,
  preflight checks, and atomic application.

- Advanced 11 narrows custom matching to one clear policy. One recipe consumes
  four cobblestone from a single chosen material slot; the die recipe consumes four
  sand while retaining a stick. Extra occupied pool slots are allowed and untouched.

## Assets

- For each new asset lesson, copy its matching folder beside the Lua script: `example/` for Beginner 09,
  `builtin_model_item/`, `click_sound/`, `blockbench_model_item/`,
  `facing_model_block/`, `animated_model_item/`, `animated_machine/`, or `entity_examples/`.
  Keep the folder name and contents unchanged.

- Beginner 08 needs `example_block.png`. Beginner 14 needs the six
  `example_armor_*.png` files. Copy those files beside their scripts.

- Advanced 06 needs the complete `gui_showcase/` directory beside its script.
  Leave **Page** empty for an automatic tour, or insert 1–4 items to hold a page.
  Put any item in **Sample** to preview it. The adjacent comments document all four
  pages and the `BACKGROUND` setting.
