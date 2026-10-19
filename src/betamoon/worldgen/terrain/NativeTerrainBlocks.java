package betamoon.worldgen.terrain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Stable Beta 1.7.3 block names, independent of runtime Java field mappings.
 */
final class NativeTerrainBlocks {
    private static final Map<String, Integer> NAMES = create();

    private NativeTerrainBlocks() {
    }

    static Integer find(String name) {
        return NAMES.get(name);
    }

    private static Map<String, Integer> create() {
        Map<String, Integer> names = new HashMap<String, Integer>();
        names.put("stone", 1);
        names.put("grass", 2);
        names.put("dirt", 3);
        names.put("cobblestone", 4);
        names.put("planks", 5);
        names.put("sapling", 6);
        names.put("bedrock", 7);
        names.put("water_moving", 8);
        names.put("water_still", 9);
        names.put("lava_moving", 10);
        names.put("lava_still", 11);
        names.put("sand", 12);
        names.put("gravel", 13);
        names.put("ore_gold", 14);
        names.put("ore_iron", 15);
        names.put("ore_coal", 16);
        names.put("wood", 17);
        names.put("leaves", 18);
        names.put("sponge", 19);
        names.put("glass", 20);
        names.put("ore_lapis", 21);
        names.put("block_lapis", 22);
        names.put("dispenser", 23);
        names.put("sand_stone", 24);
        names.put("music_block", 25);
        names.put("block_bed", 26);
        names.put("rail_powered", 27);
        names.put("rail_detector", 28);
        names.put("piston_sticky_base", 29);
        names.put("web", 30);
        names.put("tall_grass", 31);
        names.put("dead_bush", 32);
        names.put("piston_base", 33);
        names.put("piston_extension", 34);
        names.put("piston_moving", 36);
        names.put("plant_yellow", 37);
        names.put("plant_red", 38);
        names.put("mushroom_brown", 39);
        names.put("mushroom_red", 40);
        names.put("block_gold", 41);
        names.put("block_steel", 42);
        names.put("stair_double", 43);
        names.put("stair_single", 44);
        names.put("brick", 45);
        names.put("tnt", 46);
        names.put("book_shelf", 47);
        names.put("cobblestone_mossy", 48);
        names.put("obsidian", 49);
        names.put("torch_wood", 50);
        names.put("fire", 51);
        names.put("mob_spawner", 52);
        names.put("stair_compact_planks", 53);
        names.put("chest", 54);
        names.put("redstone_wire", 55);
        names.put("ore_diamond", 56);
        names.put("block_diamond", 57);
        names.put("workbench", 58);
        names.put("crops", 59);
        names.put("tilled_field", 60);
        names.put("stone_oven_idle", 61);
        names.put("stone_oven_active", 62);
        names.put("sign_post", 63);
        names.put("door_wood", 64);
        names.put("ladder", 65);
        names.put("rail", 66);
        names.put("stair_compact_cobblestone", 67);
        names.put("sign_wall", 68);
        names.put("lever", 69);
        names.put("pressure_plate_stone", 70);
        names.put("door_steel", 71);
        names.put("pressure_plate_planks", 72);
        names.put("ore_redstone", 73);
        names.put("ore_redstone_glowing", 74);
        names.put("torch_redstone_idle", 75);
        names.put("torch_redstone_active", 76);
        names.put("button", 77);
        names.put("snow", 78);
        names.put("ice", 79);
        names.put("block_snow", 80);
        names.put("cactus", 81);
        names.put("block_clay", 82);
        names.put("reed", 83);
        names.put("jukebox", 84);
        names.put("fence", 85);
        names.put("pumpkin", 86);
        names.put("netherrack", 87);
        names.put("slow_sand", 88);
        names.put("glow_stone", 89);
        names.put("portal", 90);
        names.put("pumpkin_lantern", 91);
        names.put("cake", 92);
        names.put("redstone_repeater_idle", 93);
        names.put("redstone_repeater_active", 94);
        names.put("locked_chest", 95);
        names.put("trapdoor", 96);
        names.put("cloth", 35);
        names.put("wool", 35);
        names.put("log", 17);
        names.put("gold_ore", 14);
        names.put("iron_ore", 15);
        names.put("coal_ore", 16);
        names.put("lapis_ore", 21);
        names.put("lapis_block", 22);
        names.put("sandstone", 24);
        names.put("grass_block", 2);
        names.put("bricks", 45);
        names.put("bookshelf", 47);
        names.put("mossy_cobblestone", 48);
        names.put("torch", 50);
        names.put("diamond_ore", 56);
        names.put("diamond_block", 57);
        names.put("crafting_table", 58);
        names.put("farmland", 60);
        names.put("furnace", 61);
        names.put("furnace_lit", 62);
        names.put("redstone_ore", 73);
        names.put("snow_layer", 78);
        names.put("snow_block", 80);
        names.put("clay", 82);
        names.put("reeds", 83);
        names.put("soul_sand", 88);
        names.put("glowstone", 89);
        names.put("jack_o_lantern", 91);
        names.put("iron_block", 42);
        names.put("gold_block", 41);
        return Collections.unmodifiableMap(names);
    }
}
