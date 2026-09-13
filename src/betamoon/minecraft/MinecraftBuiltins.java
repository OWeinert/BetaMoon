package betamoon.minecraft;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.EntityList;
import net.minecraft.src.Material;
import net.minecraft.src.StepSound;

/**
 * Canonical public names for Minecraft values used by BetaMoon declarations.
 */
public final class MinecraftBuiltins {
    private static final Map<String, String> BIOMES = names(
            new String[][]{{"rainforest", "Rainforest"}, {"swampland", "Swampland"},
                    {"seasonal_forest", "Seasonal Forest"}, {"forest", "Forest"}, {"savanna", "Savanna"},
                    {"shrubland", "Shrubland"}, {"taiga", "Taiga"}, {"desert", "Desert"}, {"plains", "Plains"},
                    {"ice_desert", "Ice Desert"}, {"tundra", "Tundra"}, {"hell", "Hell"}, {"sky", "Sky"}});
    private static final Map<String, String> BLOCK_MATERIAL_NAMES = names(new String[][]{{"air", "air"},
            {"grass", "grass"}, {"ground", "ground"}, {"wood", "wood"}, {"rock", "rock"}, {"iron", "iron"},
            {"water", "water"}, {"lava", "lava"}, {"leaves", "leaves"}, {"plants", "plants"}, {"sponge", "sponge"},
            {"cloth", "cloth"}, {"fire", "fire"}, {"sand", "sand"}, {"circuits", "circuits"}, {"glass", "glass"},
            {"tnt", "tnt"}, {"ice", "ice"}, {"snow", "snow"}, {"built_snow", "builtsnow"}, {"cactus", "cactus"},
            {"clay", "clay"}, {"pumpkin", "pumpkin"}, {"portal", "portal"}, {"cake", "cake"}});
    private static final Map<String, String> STEP_SOUND_NAMES = names(
            new String[][]{{"stone", "stone"}, {"wood", "wood"}, {"gravel", "gravel"}, {"grass", "grass"},
                    {"metal", "metal"}, {"glass", "glass"}, {"cloth", "cloth"}, {"sand", "sand"}});
    private static final Map<String, String> TOOL_MATERIALS = names(new String[][]{{"wood", "wood"}, {"stone", "stone"},
            {"iron", "iron"}, {"diamond", "diamond"}, {"gold", "gold"}});
    private static final Map<String, String> ARMOR_MATERIALS = names(new String[][]{{"leather", "leather"},
            {"chain", "chain"}, {"iron", "iron"}, {"diamond", "diamond"}, {"gold", "gold"}});
    private static final Map<String, String> ARMOR_SLOTS = names(
            new String[][]{{"head", "head"}, {"chest", "chest"}, {"legs", "legs"}, {"feet", "feet"}});
    private static final Map<String, String> ENTITIES = names(new String[][]{{"item", "Item"}, {"painting", "Painting"},
            {"arrow", "Arrow"}, {"snowball", "Snowball"}, {"primed_tnt", "PrimedTnt"}, {"falling_sand", "FallingSand"},
            {"minecart", "Minecart"}, {"boat", "Boat"}, {"mob", "Mob"}, {"monster", "Monster"}, {"creeper", "Creeper"},
            {"skeleton", "Skeleton"}, {"spider", "Spider"}, {"giant", "Giant"}, {"zombie", "Zombie"},
            {"slime", "Slime"}, {"ghast", "Ghast"}, {"pig_zombie", "PigZombie"}, {"pig", "Pig"}, {"sheep", "Sheep"},
            {"cow", "Cow"}, {"chicken", "Chicken"}, {"squid", "Squid"}, {"wolf", "Wolf"}});
    private static final Map<String, String> PARTICLES = names(new String[][]{{"bubble", "bubble"}, {"smoke", "smoke"},
            {"note", "note"}, {"portal", "portal"}, {"explode", "explode"}, {"flame", "flame"}, {"lava", "lava"},
            {"footstep", "footstep"}, {"splash", "splash"}, {"large_smoke", "largesmoke"}, {"redstone_dust", "reddust"},
            {"snowball_poof", "snowballpoof"}, {"snow_shovel", "snowshovel"}, {"slime", "slime"}, {"heart", "heart"}});
    private static final Map<String, String> GUI_BACKGROUNDS = names(
            new String[][]{{"container", "minecraft:container"}, {"chest", "minecraft:chest"},
                    {"dispenser", "minecraft:dispenser"}, {"furnace", "minecraft:furnace"},
                    {"crafting", "minecraft:crafting"}, {"inventory", "minecraft:inventory"}});
    private static final Map<String, String> GUI_SPRITES = names(
            new String[][]{{"furnace_flame", "minecraft:furnace_flame"}, {"furnace_arrow", "minecraft:furnace_arrow"},
                    {"crafting_arrow", "minecraft:crafting_arrow"}, {"slot", "minecraft:slot"},
                    {"output_slot", "minecraft:output_slot"}});
    private static final Map<String, String> RANDOM_SOUNDS = names(
            new String[][]{{"click", "random.click"}, {"pop", "random.pop"}, {"orb", "random.orb"},
                    {"bow", "random.bow"}, {"item_break", "random.break"}, {"explode", "random.explode"},
                    {"fizz", "random.fizz"}, {"door_open", "random.door_open"}, {"door_close", "random.door_close"}});

    private MinecraftBuiltins() {
    }

    public static Map<String, String> biomes() {
        return BIOMES;
    }

    public static Map<String, String> blockMaterials() {
        return BLOCK_MATERIAL_NAMES;
    }

    public static Map<String, String> stepSounds() {
        return STEP_SOUND_NAMES;
    }

    public static Map<String, String> toolMaterials() {
        return TOOL_MATERIALS;
    }

    public static Map<String, String> armorMaterials() {
        return ARMOR_MATERIALS;
    }

    public static Map<String, String> armorSlots() {
        return ARMOR_SLOTS;
    }

    public static Map<String, String> entities() {
        return ENTITIES;
    }

    public static Map<String, String> particles() {
        return PARTICLES;
    }

    public static Map<String, String> guiBackgrounds() {
        return GUI_BACKGROUNDS;
    }

    public static Map<String, String> guiSprites() {
        return GUI_SPRITES;
    }

    public static Map<String, String> randomSounds() {
        return RANDOM_SOUNDS;
    }

    public static Material resolveBlockMaterial(String name) {
        String key = normalizedKey(name);
        if (key.equals("air")) {
            return Material.air;
        }
        if (key.equals("grass")) {
            return Material.grassMaterial;
        }
        if (key.equals("ground")) {
            return Material.ground;
        }
        if (key.equals("wood")) {
            return Material.wood;
        }
        if (key.equals("rock") || key.equals("stone")) {
            return Material.rock;
        }
        if (key.equals("iron")) {
            return Material.iron;
        }
        if (key.equals("water")) {
            return Material.water;
        }
        if (key.equals("lava")) {
            return Material.lava;
        }
        if (key.equals("leaves")) {
            return Material.leaves;
        }
        if (key.equals("plants")) {
            return Material.plants;
        }
        if (key.equals("sponge")) {
            return Material.sponge;
        }
        if (key.equals("cloth")) {
            return Material.cloth;
        }
        if (key.equals("fire")) {
            return Material.fire;
        }
        if (key.equals("sand")) {
            return Material.sand;
        }
        if (key.equals("circuits")) {
            return Material.circuits;
        }
        if (key.equals("glass")) {
            return Material.glass;
        }
        if (key.equals("tnt")) {
            return Material.tnt;
        }
        if (key.equals("ice")) {
            return Material.ice;
        }
        if (key.equals("snow")) {
            return Material.snow;
        }
        if (key.equals("builtsnow") || key.equals("built_snow")) {
            return Material.builtSnow;
        }
        if (key.equals("cactus")) {
            return Material.cactus;
        }
        if (key.equals("clay")) {
            return Material.clay;
        }
        if (key.equals("pumpkin")) {
            return Material.pumpkin;
        }
        if (key.equals("portal")) {
            return Material.portal;
        }
        if (key.equals("cake")) {
            return Material.cakeMaterial;
        }
        return null;
    }

    public static StepSound resolveStepSound(String name) {
        String key = normalizedKey(name);
        if (key.equals("stone")) {
            return Block.soundStoneFootstep;
        }
        if (key.equals("wood")) {
            return Block.soundWoodFootstep;
        }
        if (key.equals("gravel")) {
            return Block.soundGravelFootstep;
        }
        if (key.equals("grass")) {
            return Block.soundGrassFootstep;
        }
        if (key.equals("metal")) {
            return Block.soundMetalFootstep;
        }
        if (key.equals("glass")) {
            return Block.soundGlassFootstep;
        }
        if (key.equals("cloth")) {
            return Block.soundClothFootstep;
        }
        if (key.equals("sand")) {
            return Block.soundSandFootstep;
        }
        return null;
    }

    public static BiomeGenBase resolveBiome(String name) {
        String target = normalizedLookup(name);
        if (target.length() == 0) {
            return null;
        }
        try {
            Field[] fields = BiomeGenBase.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType() != BiomeGenBase.class) {
                    continue;
                }
                Object value = field.get(null);
                if (value instanceof BiomeGenBase) {
                    BiomeGenBase biome = (BiomeGenBase) value;
                    if (normalizedLookup(biome.biomeName).equals(target)
                            || normalizedLookup(field.getName()).equals(target)) {
                        return biome;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Class<?> resolveEntity(String name) {
        Map<?, ?> entities = entityMap("stringToClassMapping", "a");
        if (entities == null) {
            return null;
        }
        String target = normalizedLookup(name);
        for (Map.Entry<?, ?> entry : entities.entrySet()) {
            if (entry.getKey() instanceof String && normalizedLookup((String) entry.getKey()).equals(target)) {
                return (Class<?>) entry.getValue();
            }
        }
        return null;
    }

    public static Class<?> resolveEntity(int id) {
        Map<?, ?> entities = entityMap("IDtoClassMapping", "c");
        return entities == null ? null : (Class<?>) entities.get(Integer.valueOf(id));
    }

    private static Map<?, ?> entityMap(String fieldName, String obfuscatedFieldName) {
        try {
            Field field;
            try {
                field = EntityList.class.getDeclaredField(fieldName);
            } catch (Exception ignored) {
                field = EntityList.class.getDeclaredField(obfuscatedFieldName);
            }
            field.setAccessible(true);
            return (Map<?, ?>) field.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, String> names(String[][] entries) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i++) {
            result.put(entries[i][0], entries[i][1]);
        }
        return Collections.unmodifiableMap(result);
    }

    private static String normalizedKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private static String normalizedLookup(String value) {
        return normalizedKey(value).replace("_", "").replace("-", "").replace(" ", "");
    }
}
