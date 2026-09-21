package betamoon.luaapi.block;

import betamoon.client.assets.AssetLocation;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;

import betamoon.minecraft.MinecraftBuiltins;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.Material;
import net.minecraft.src.StepSound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import static betamoon.luaapi.utils.LuaDeclarationValues.internalName;

/**
 * Reads block properties without allocating a block or installing resources.
 */
final class BlockDeclaration {
    final ModelAppearanceDeclaration appearance;
    final int id;
    final String name;
    final String displayName;
    final String materialName;
    final Material material;
    final Float hardness;
    final Float resistance;
    final Integer light;
    final Integer lightOpacity;
    final StepSound stepSound;
    final boolean unbreakable;
    final Texture texture;
    final List<FaceTexture> textures;
    final Map<String, Integer> harvest;
    final List<Drop> drops;
    final BlockDefinition callbacks;
    final BlockComponents components;
    final BlockTickRegistry.ParsedDefinition ticks;

    BlockDeclaration(LuaValue definition) {
        callbacks = new BlockDefinition(definition);
        appearance = callbacks.visual.appearance;
        if (!callbacks.visual.isDynamic()) {
            BlockModelRegistry.validate(appearance);
            for (ModelAppearanceDeclaration variant : callbacks.visual.appearances().values()) {
                BlockModelRegistry.validate(variant);
            }
        }
        id = required(definition, "id").checkint();
        if (id < 0 || id > 255) {
            throw new LuaError("Block: id outside allowed range (0-255): " + id);
        }
        BlockCallbackRegistry.validateIdentity(id, callbacks);
        components = new BlockComponents(definition);
        ticks = BlockTickRegistry.parse(id, definition.get("onTick"), definition.get("onDisplayTick"));
        materialName = required(definition, "material").checkjstring();
        material = resolveMaterial(materialName);
        name = internalName(definition);
        displayName = definition.get("displayName").optjstring(name);
        hardness = optionalFloat(definition.get("hardness"));
        resistance = optionalFloat(definition.get("resistance"));
        light = optionalInteger(definition.get("light"));
        lightOpacity = optionalInteger(definition.get("lightOpacity"));
        LuaValue sound = definition.get("stepSound");
        stepSound = sound.isnil() ? null : resolveStepSound(sound.checkjstring());
        unbreakable = definition.get("unbreakable").toboolean();
        texture = Texture.read(definition.get("texture"));
        textures = readTextures(definition.get("textures"));
        harvest = readHarvest(definition.get("harvest"));
        drops = readDrops(definition.get("drops"));
    }

    private static Float optionalFloat(LuaValue value) {
        return value.isnil() ? null : Float.valueOf((float) value.checkdouble());
    }

    private static Integer optionalInteger(LuaValue value) {
        return value.isnil() ? null : Integer.valueOf((int) value.checkdouble());
    }

    private static Map<String, Integer> readHarvest(LuaValue value) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (value.istable()) {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = value.next(key);
                key = entry.arg1();
                if (key.isnil()) {
                    break;
                }
                result.put(key.checkjstring(), entry.arg(2).checkint());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private List<Drop> readDrops(LuaValue value) {
        List<Drop> result = new ArrayList<>();
        if (value.istable()) {
            for (int i = 1; i <= value.length(); i++) {
                LuaValue entry = value.get(i);
                int itemId = resolveDropId(required(entry, "item"));
                int min = 1;
                int max = 1;
                if (!entry.get("min").isnil()) {
                    if (entry.get("max").isnil()) {
                        throw new LuaError("Block: maxQuantity is required when minQuantity is provided.");
                    }
                    min = entry.get("min").checkint();
                    max = entry.get("max").checkint();
                }
                if (min > max) {
                    throw new LuaError("Block: minQuantity must be less or equal to maxQuantity.");
                }
                result.add(new Drop(itemId, min, max));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<FaceTexture> readTextures(LuaValue value) {
        List<FaceTexture> result = new ArrayList<>();
        if (!value.isnil()) {
            if (!value.istable()) {
                throw new LuaError("Block: texture map must be a table.");
            }
            addTexture(result, value.get("all"), -1);
            LuaValue sides = value.get("sides");
            addTexture(result, sides.isnil() ? value.get("side") : sides, -2);
            addTexture(result, value.get("top"), 1);
            addTexture(result, value.get("bottom"), 0);
            addTexture(result, value.get("north"), 2);
            addTexture(result, value.get("south"), 3);
            addTexture(result, value.get("west"), 4);
            addTexture(result, value.get("east"), 5);
            addTexture(result, value.get("front"), 3);
            addTexture(result, value.get("back"), 2);
        }
        return Collections.unmodifiableList(result);
    }

    private static void addTexture(List<FaceTexture> result, LuaValue value, int side) {
        Texture texture = Texture.read(value);
        if (texture != null) {
            result.add(new FaceTexture(side, texture));
        }
    }

    static final class Texture {
        final Integer index;
        final AssetLocation path;

        private Texture(Integer index, AssetLocation path) {
            this.index = index;
            this.path = path;
        }

        static Texture read(LuaValue value) {
            if (value.isnil()) {
                return null;
            }
            if (value.isnumber()) {
                return new Texture(value.checkint(), null);
            }
            return new Texture(null, AssetInputs.texture(value));
        }
    }

    static final class FaceTexture {
        final int side;
        final Texture texture;

        FaceTexture(int side, Texture texture) {
            this.side = side;
            this.texture = texture;
        }
    }

    static final class Drop {
        final int itemId;
        final int min;
        final int max;

        Drop(int itemId, int min, int max) {
            this.itemId = itemId;
            this.min = min;
            this.max = max;
        }
    }

    private static Material resolveMaterial(String name) {
        Material material = MinecraftBuiltins.resolveBlockMaterial(name);
        if (material == null) {
            throw new LuaError("Block: unknown material: " + name);
        }
        return material;
    }

    /**
     * Resolves a step sound name to the corresponding Minecraft sound.
     *
     * @param name
     *            step sound name from Lua
     * @return matching StepSound instance
     */
    private static StepSound resolveStepSound(String name) {
        StepSound sound = MinecraftBuiltins.resolveStepSound(name);
        if (sound == null) {
            throw new LuaError("Block: unknown step sound: " + name);
        }
        return sound;
    }

    private int resolveDropId(LuaValue value) {
        if (value.isnumber()) {
            int id = value.toint();
            if (id < 0) {
                throw new LuaError("Block: drop id has to be positive: " + id);
            }
            if (id < Block.blocksList.length) {
                if (Block.blocksList[id] == null && id != this.id) {
                    throw new LuaError("Block: drop block id is not registered: " + id);
                }
                return id;
            }
            if (id >= Item.itemsList.length) {
                throw new LuaError("Block: drop item id out of range: " + id);
            }
            if (Item.itemsList[id] == null) {
                throw new LuaError("Block: drop item id is not registered: " + id);
            }
            return id;
        }
        if (value.istable()) {
            LuaValue idValue = value.get("id");
            if (!idValue.isnil()) {
                return resolveDropId(idValue);
            }
            LuaValue getter = value.get("getId");
            if (!getter.isnil()) {
                return resolveDropId(getter.call(value));
            }
        }
        throw new LuaError("Block: drop item must be an id or item/block handle.");
    }

}
