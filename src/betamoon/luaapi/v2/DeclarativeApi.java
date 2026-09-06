package betamoon.luaapi.v2;

import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Adds declarative facades for materials, recipes, world generation, and events. */
public final class DeclarativeApi {
    private DeclarativeApi() {
    }

    public static void attach(LuaTable root, LuaTable backend) {
        attachMaterials(root, backend);
        attachRecipes(root, backend);
        attachWorldGen(root, backend);
    }

    private static void attachMaterials(LuaTable root, LuaTable backend) {
        LuaTable materials = new LuaTable();
        materials.set("tools", new MaterialRegistry(backend, true));
        materials.set("armor", new MaterialRegistry(backend, false));
        root.set("materials", materials);
    }

    private static void attachRecipes(LuaTable root, LuaTable backend) {
        LuaTable recipes = (LuaTable) root.get("recipes");
        recipes.set("add", new AddRecipe(backend, recipes));
    }

    private static void attachWorldGen(LuaTable root, LuaTable backend) {
        LuaTable worldgen = (LuaTable) root.get("worldgen");
        worldgen.set("ores", new OreRegistry(backend));
        worldgen.set("biomes", new BiomeRegistry(backend));
    }


    private static final class MaterialRegistry extends LuaTable {
        private final LuaTable root;
        private final boolean tools;
        private final Map values = new HashMap();

        private MaterialRegistry(LuaTable root, boolean tools) {
            this.root = root;
            this.tools = tools;
            set("add", new AddMaterial(this));
            set("get", new GetMaterial(this));
            set("require", new RequireMaterial(this));
        }
    }

    private static final class AddMaterial extends VarArgFunction {
        private final MaterialRegistry registry;
        private AddMaterial(MaterialRegistry registry) { this.registry = registry; }
        public Varargs invoke(Varargs args) {
            LuaValue def = argument(args, registry);
            if (!def.istable()) throw new LuaError("material add expects a definition table.");
            String key = requiredString(def, "key");
            LuaValue value;
            if (registry.tools) {
                value = registry.root.get("createToolMaterial").invoke(LuaValue.varargsOf(new LuaValue[] {
                    LuaValue.valueOf(key), required(def, "harvestLevel"), required(def, "durability"),
                    required(def, "efficiency"), required(def, "damage")
                })).arg1();
            } else {
                value = registry.root.get("createArmorMaterial").invoke(LuaValue.varargsOf(new LuaValue[] {
                    LuaValue.valueOf(key), required(def, "protection")
                })).arg1();
            }
            registry.values.put(normalize(key), value);
            return value;
        }
    }

    private static final class GetMaterial extends VarArgFunction {
        private final MaterialRegistry registry;
        private GetMaterial(MaterialRegistry registry) { this.registry = registry; }
        public Varargs invoke(Varargs args) {
            String key = argument(args, registry).checkjstring();
            LuaValue value = (LuaValue) registry.values.get(normalize(key));
            if (value != null) return value;
            // Vanilla materials remain accepted by content definitions as strings.
            return LuaValue.valueOf(key);
        }
    }

    private static final class RequireMaterial extends VarArgFunction {
        private final MaterialRegistry registry;
        private RequireMaterial(MaterialRegistry registry) { this.registry = registry; }
        public Varargs invoke(Varargs args) {
            String key = argument(args, registry).checkjstring();
            LuaValue value = (LuaValue) registry.values.get(normalize(key));
            if (value == null) throw new LuaError("Material '" + key + "' has not been declared by BetaMoon.");
            return value;
        }
    }

    private static final class AddRecipe extends VarArgFunction {
        private final LuaTable root;
        private final LuaTable service;
        private AddRecipe(LuaTable root, LuaTable service) { this.root = root; this.service = service; }
        public Varargs invoke(Varargs args) {
            LuaValue def = args.arg1() == service ? args.arg(2) : args.arg1();
            if (!def.istable()) throw new LuaError("recipes:add expects a definition table.");
            String type = requiredString(def, "type").toLowerCase();
            LuaValue output = required(def, "output");
            if (type.equals("shaped")) {
                LuaValue pattern = required(def, "pattern");
                LuaValue ingredients = def.get("ingredients");
                if (ingredients.isnil()) ingredients = required(def, "key");
                root.get("addShapedRecipe").invoke(LuaValue.varargsOf(new LuaValue[] { output, pattern, ingredients }));
            } else if (type.equals("shapeless")) {
                root.get("addShapelessRecipe").invoke(LuaValue.varargsOf(new LuaValue[] {
                    output, required(def, "ingredients")
                }));
            } else if (type.equals("smelting")) {
                root.get("addSmeltingRecipe").invoke(LuaValue.varargsOf(new LuaValue[] {
                    required(def, "input"), output
                }));
            } else {
                throw new LuaError("Unknown recipe type: " + type);
            }
            // Resolve through the public registry so creation and queries return one handle shape.
            LuaTable criteria = new LuaTable();
            criteria.set("type", LuaValue.valueOf(type));
            criteria.set("output", output);
            LuaValue results = service.get("find").invoke(LuaValue.varargsOf(new LuaValue[] {
                service, criteria
            })).arg1();
            int length = results.length();
            return length == 0 ? LuaValue.NIL : results.get(length);
        }
    }

    private static final class OreRegistry extends LuaTable {
        private final LuaTable root;
        private OreRegistry(LuaTable root) { this.root = root; set("add", new AddOre(this)); }
    }

    private static final class BiomeRegistry extends LuaTable {
        private final LuaTable root;
        private BiomeRegistry(LuaTable root) { this.root = root; set("add", new AddBiome(this)); }
    }

    private static final class AddBiome extends VarArgFunction {
        private final BiomeRegistry registry;
        private AddBiome(BiomeRegistry registry) { this.registry = registry; }
        public Varargs invoke(Varargs args) {
            LuaValue def = argument(args, registry);
            if (!def.istable()) throw new LuaError("worldgen.biomes:add expects a definition table.");
            LuaValue world = registry.root.get("startWorldGen").call();
            LuaValue basedOn = def.get("basedOn");
            LuaValue biome = basedOn.isnil()
                ? invokeMethod(world, "addBiomeGen", required(def, "name"))
                : invokeMethod(world, "addBiomeGenFromDefault", basedOn, required(def, "name"));
            LuaValue color = def.get("color");
            if (!color.isnil()) invokeMethod(biome, "setColor", color);
            LuaValue foliage = def.get("foliageColor");
            if (!foliage.isnil()) invokeMethod(biome, "setFoliageColor", foliage);
            LuaValue surface = def.get("surface");
            if (surface.istable()) {
                if (!surface.get("top").isnil()) invokeMethod(biome, "setTopBlock", surface.get("top"));
                if (!surface.get("filler").isnil()) invokeMethod(biome, "setFillerBlock", surface.get("filler"));
            }
            LuaValue range = def.get("range");
            if (range.istable()) {
                applyRange(biome, "setTemperatureRange", range.get("temperature"));
                applyRange(biome, "setHumidityRange", range.get("humidity"));
            }
            LuaValue trees = def.get("trees");
            if (trees.istable()) {
                if (!trees.get("type").isnil()) invokeMethod(biome, "setTreeGenerator", trees.get("type"));
                if (!trees.get("bigTreeChance").isnil()) invokeMethod(biome, "setBigTreeChance", trees.get("bigTreeChance"));
            }
            LuaValue weather = def.get("weather");
            if (weather.istable()) {
                boolean snow = weather.get("snow").toboolean();
                boolean rain = weather.get("rain").toboolean();
                if (snow) invokeMethod(biome, "enableSnow");
                else if (rain) invokeMethod(biome, "enableRain");
                else invokeMethod(biome, "disableRain");
            }
            LuaValue spawns = def.get("spawns");
            if (spawns.istable()) applySpawns(biome, spawns);
            invokeMethod(biome, "finishBiomeGen");
            invokeMethod(world, "finishWorldGen");
            return LuaValue.NIL;
        }
    }

    private static void applyRange(LuaValue biome, String method, LuaValue range) {
        if (range.isnil()) return;
        if (!range.istable()) throw new LuaError(method + " range must be a table.");
        LuaValue min = range.get("min").isnil() ? range.get(1) : range.get("min");
        LuaValue max = range.get("max").isnil() ? range.get(2) : range.get("max");
        invokeMethod(biome, method, min, max);
    }

    private static void applySpawns(LuaValue biome, LuaValue groups) {
        LuaValue groupKey = LuaValue.NIL;
        while (true) {
            Varargs group = groups.next(groupKey); groupKey = group.arg1(); if (groupKey.isnil()) break;
            String type = groupKey.checkjstring();
            LuaValue entries = group.arg(2);
            if (!entries.istable()) throw new LuaError("Biome spawn group '" + type + "' must be a list.");
            invokeMethod(biome, "clearSpawns", LuaValue.valueOf(type));
            for (int i = 1; i <= entries.length(); i++) {
                LuaValue entry = entries.get(i);
                invokeMethod(biome, "addSpawn", LuaValue.valueOf(type), required(entry, "entity"),
                    required(entry, "weight"));
            }
        }
    }

    private static final class AddOre extends VarArgFunction {
        private final OreRegistry registry;
        private AddOre(OreRegistry registry) { this.registry = registry; }
        public Varargs invoke(Varargs args) {
            LuaValue def = argument(args, registry);
            if (!def.istable()) throw new LuaError("worldgen.ores:add expects a definition table.");
            LuaValue height = required(def, "height");
            if (!height.istable()) throw new LuaError("Ore height must be { min=..., max=... }.");
            LuaValue world = registry.root.get("startWorldGen").call();
            LuaValue ore = invokeMethod(world, "addOreGen", required(def, "block"), required(def, "veinsPerChunk"),
                required(def, "veinSize"), required(height, "min"), required(height, "max"));
            invokeMethod(ore, "setDimension", def.get("dimension").isnil()
                ? LuaValue.valueOf("overworld") : def.get("dimension"));
            LuaValue replace = def.get("replace");
            if (!replace.isnil()) invokeMethod(ore, "setSpawnBlock", replace);
            LuaValue biomes = def.get("biomes");
            if (!biomes.isnil()) invokeMethod(ore, "setBiomes", biomes);
            invokeMethod(ore, "finishOreGen");
            invokeMethod(world, "finishWorldGen");
            return LuaValue.NIL;
        }
    }

    private static LuaValue invokeMethod(LuaValue receiver, String method, LuaValue... args) {
        LuaValue[] values = new LuaValue[args.length + 1];
        values[0] = receiver;
        System.arraycopy(args, 0, values, 1, args.length);
        return receiver.get(method).invoke(LuaValue.varargsOf(values)).arg1();
    }

    private static LuaValue argument(Varargs args, LuaValue receiver) {
        return args.arg(args.arg1() == receiver ? 2 : 1);
    }

    private static LuaValue required(LuaValue table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) throw new LuaError("Definition requires '" + key + "'.");
        return value;
    }

    private static String requiredString(LuaValue table, String key) {
        return required(table, key).checkjstring();
    }

    private static String normalize(String key) { return key.trim().toLowerCase(); }
}
