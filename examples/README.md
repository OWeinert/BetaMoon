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
| 09 | `01_beg_09_food.lua` | Food properties |
| 10 | `01_beg_10_drops.lua` | Custom block drops |
| 11 | `01_beg_11_vanilla_recipes.lua` | Shaped, shapeless, and smelting recipes |
| 12 | `01_beg_12_tool.lua` | Tool materials and tool types |
| 13 | `01_beg_13_armor.lua` | Armor materials, pieces, and textures |
| 14 | `01_beg_14_utilities.lua` | Stack and position utilities |
| 15 | `01_beg_15_chat.lua` | Chat output and formatting |
| 16 | `01_beg_16_projectile.lua` | Projectile ammunition and cooldowns |
| 17 | `01_beg_17_ore_generation.lua` | Ore generation in new chunks |
| 18a–18b | `01_beg_18a_module_export.lua`, `01_beg_18b_module_import.lua` | Sharing an exported table between scripts |
| 19 | `01_beg_19_item_subtypes.lua` | Metadata subtypes and render variants |
| 20 | `01_beg_20_biome_from_default.lua` | Adapting a vanilla biome |
| 21 | `01_beg_21_first_event.lua` | Responding to one global game event |
| 22 | `01_beg_22_first_block_interaction.lua` | Handling a block activation |
| 23 | `01_beg_23_first_item_use.lua` | Handling a consumable item's right-click action |

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
| 12 | `02_int_12_targeted_item_actions.lua` | Direct block/entity targets and explicit ray tracing |
| 13 | `02_int_13_tool_interactions.lua` | Wrench actions, orientation, and multiple tool classes |
| 14 | `02_int_14_dynamic_tool_callbacks.lua` | Per-block harvesting and mining-speed queries |
| 15 | `02_int_15_block_shapes.lua` | Collision, selection, drawing bounds, and climbing |
| 16 | `02_int_16_launch_pad.lua` | Player filtering and motion-preserving launch behavior |
| 17 | `02_int_17_special_blocks.lua` | Replaceable, translucent, luminous, and unbreakable blocks |
| 18 | `02_int_18_random_and_continuous_ticks.lua` | Default and vanilla random block ticks |
| 19 | `02_int_19_scheduled_and_display_ticks.lua` | Scheduled gameplay ticks and client display ticks |
| 20 | `02_int_20_redstone_switch.lua` | Directional digital redstone output |

## Advanced

Advanced lessons build complete systems. Keep every file from a lettered lesson
together and read its dependency comments before loading it.

| Lesson | File | Topic |
| --- | --- | --- |
| 01 | `03_adv_01_redstone_timer.lua` | Input-edge observation and scheduled output pulses |
| 02a–02c | `03_adv_02a_basic_storage_data.lua`, `03_adv_02b_basic_storage_layout.lua`, `03_adv_02c_basic_storage_block.lua` | Modular declarations combined into saved storage, a container, and a GUI |
| 03 | `03_adv_03_custom_furnace.lua` | A complete fuel-burning vanilla-smelting machine |
| 04 | `03_adv_04_tile_redstone_controller.lua` | Persistent tile data controlling redstone output |
| 05 | `03_adv_05_gui_showcase.lua` | Container GUI elements and synchronized presentation |
| 06a–06c | `03_adv_06a_simple_alloy_recipe_type.lua`, `03_adv_06b_simple_alloy_recipes.lua`, `03_adv_06c_simple_alloy_furnace.lua` | Simple named-slot recipe types, recipes, and a processing machine |
| 07a–07c | `03_adv_07a_contextual_recipe_type.lua`, `03_adv_07b_contextual_recipes.lua`, `03_adv_07c_contextual_processor.lua` | Heat/power conditions and commit-time context revalidation |
| 08a–08c | `03_adv_08a_advanced_fabrication_types.lua`, `03_adv_08b_advanced_fabrication_recipes.lua`, `03_adv_08c_advanced_fabricator.lua` | Pools, grids, custom matching, bindings, and atomic processing |
| 09a–09b | `03_adv_09a_matcher_cookbook_type.lua`, `03_adv_09b_matcher_cookbook_press.lua` | A context-driven custom allocation policy in a working machine |

## Trying the interactive examples

| Lesson | Content and use |
| --- | --- |
| Beginner 16 | **Snowball Launcher** (5023): two iron ingots + snowball. Carry snowball ammunition and right-click. |
| Beginner 22 | **Greeting Block** (228): cobblestone + stick. Place and right-click it to report its position. |
| Beginner 23 | **Signal Bell** (5028): sugar + redstone makes four. Right-click to play its sound and consume one. |
| Intermediate 08 | **Interaction Block** (210): cobblestone + stick. Right-click toggles activity; sneak-right-click locks mining. |
| Intermediate 09 | **Attached Lamp** (217): stone + glowstone dust makes four. Attach one to any solid face and remove its support. |
| Intermediate 10 | **Lifecycle Observer** (218): stone + paper. Click it, alter a neighbor, collide with it, then break or explode it. |
| Intermediate 11 | **Healing Powder** (5020): sugar + wheat makes two. Right-click to heal with a one-second cooldown. |
| Intermediate 12 | **Example Surveyor** (5026): iron + redstone. Use it on a block, in the air, or on an entity. |
| Intermediate 13 | **Wrench**, **Multi-tool**, and **Practice Block** (5021, 5022, 211): craft from iron, stick, and cobblestone. |
| Intermediate 14 | **Adaptive Pick** (5027): diamond over two sticks. Compare ores, obsidian, and ordinary stone. |
| Intermediate 15 | **Climbing Post** (215): planks + stick makes four. Stack them and move against their narrow shape. |
| Intermediate 16 | **Launch Pad** (214): planks + redstone. Walk onto it while moving to see horizontal speed preserved. |
| Intermediate 17 | Blocks 219–221: obtain the sprout, luminous glass, and sealed casing through a creative/debug inventory. |
| Intermediate 18 | Blocks 222–223: obtain both through a creative/debug inventory and compare their update timing. |
| Intermediate 20 | **Directional Switch** (212): stone + redstone. Toggle it and connect wire to its output face. |
| Advanced 01 | **Pulse Timer** (213): cobblestone + redstone. A rising north input produces a one-second south output. |
| Advanced 02 | **Basic Storage** (224): chest surrounded by planks. Its screen reports occupied saved slots. |
| Advanced 03 | **Fast Furnace** (204): vanilla furnace surrounded by iron. It uses normal smelting recipes in 100 ticks. |
| Advanced 04 | **Tile Redstone Controller** (225): insert a control key, then use input edges to toggle persistent output. |
| Advanced 05 | **GUI Showcase** (208): chest + redstone. Use its Page and Sample slots to explore the gallery. |
| Advanced 06 | **Alloy Furnace** (209): see the simple-alloy table below. |
| Advanced 07 | **Contextual Processor** (226): process dirt cold/unpowered, or heat sand with fuel and redstone. |
| Advanced 08 | **Advanced Fabricator** (216): see the fabrication table below. |
| Advanced 09 | **Focused Press** (227): its matcher prefers material slot 1 unpowered and slot 5 powered. |

## Multi-file lesson details

- Beginner 18 requires both module files. The exporter constructs its local public
  table inside `modInit` and publishes it as the final initialization action.

- Advanced 02 keeps persistent indexes, screen layout, and structural registration
  separate. The first two scripts export plain declaration tables. The third imports
  them and registers all structural handles under one owner, preserving BetaMoon's
  tile/container/GUI compatibility rules.

- Advanced 06 creates these simple alloy recipes:

  | Base | Additive | Retained mold | Results |
  | --- | --- | --- | --- |
  | 1 gold ore | 1 coal or charcoal | 1 stick | 3 gold ingots + 1 cobblestone |
  | 4 dirt or 4 cobblestone | 1 coal | Empty | 1 stone |

- Advanced 07 demonstrates conditions independently of inventory matching:

  | Input | Machine context | Result |
  | --- | --- | --- |
  | 1 dirt | Heat at most 100 and no power | 4 clay balls |
  | 1 sand | Heat at least 400 and powered | 2 glass |

  The processor passes a validated context during discovery and fresh values to
  `canApply` and `apply`, so a lost signal cannot commit a now-invalid hot recipe.

- Advanced 08 uses one shared 3x3 work area:

  | Rule | Work area | Result |
  | --- | --- | --- |
  | Unordered pool | 2 sand + 2 gravel in any work slots; catalyst empty | 4 clay plus flint and cobblestone byproducts |
  | Transformable grid | Iron in four corners and a stick in the center | 1 iron block |
  | Custom sequence | Iron immediately followed by coal in flattened slot order | 1 gold ingot |

  Its type file explains detached matcher snapshots and validated `plan:use`
  allocations. Its machine file explains bindings, candidate order, signatures,
  preflight checks, and atomic application.

- Advanced 09 narrows custom matching to one clear policy. One recipe consumes
  four cobblestone from a single chosen material slot; the die recipe consumes four
  sand while retaining a stick. Extra occupied pool slots are allowed and untouched.

## Assets

- Beginner 08 needs `example_block.png`. Beginner 13 needs the six
  `example_armor_*.png` files. Copy those files beside their scripts.

- Advanced 05 needs the complete `gui_showcase/` directory beside its script.
  Leave **Page** empty for an automatic tour, or insert 1–4 items to hold a page.
  Put any item in **Sample** to preview it. The adjacent comments document all four
  pages and the `BACKGROUND` setting.
