package betamoon.luaapi;

import betamoon.wrappers.BlockWrapper;
import betamoon.resources.EnumTexAtlas;
import forge.MinecraftForge;
import java.util.logging.Logger;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import net.minecraft.src.StepSound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class BlockApi {
    private static final Logger LOGGER = Logger.getLogger("BetaMoon");
    /**
     * Utility class that installs block-related Lua bindings.
     */
    private BlockApi() {
    }

    static void attach(LuaTable module) {
        module.set("createBlock", new CreateBlock());
    }

    private static final class CreateBlock extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 3 && args.arg(1).istable()) ? 2 : 1;
            int id = args.checkint(base);
            if (id < 0 || id > 255) {
                throw new LuaError("Block: id outside allowed range (0-255): " + id);
            }
            String materialName = args.checkjstring(base + 1);
            String name = args.checkjstring(base + 2);
            Material material = resolveMaterial(materialName);
            try {
                BlockWrapper block = new BlockWrapper(id, 0, material, name);
                return new BlockHandle(block);
            } catch (RuntimeException e) {
                throw new LuaError("Block: " + String.valueOf(e.getMessage()));
            }
        }
    }

    static final class BlockHandle extends LuaTable {
        final BlockWrapper block;
        private boolean registered;

        @SuppressWarnings("deprecation")
        private BlockHandle(BlockWrapper block) {
            this.block = block;
            set("setHardness", new SetHardness(this));
            set("setResistance", new SetResistance(this));
            set("setLightValue", new SetLightValue(this));
            set("setLightOpacity", new SetLightOpacity(this));
            set("setStepSound", new SetStepSound(this));
            set("setUnbreakable", new SetUnbreakable(this));
            set("setBlockHarvestLevel", new SetBlockHarvestLevel(this));
            set("setTextureId", new SetTextureId(this));
            set("addTexture", new AddTexture(this));
            set("setSideTexture", new SetSideTexture(this));
            set("setTextureMap", new SetTextureMap(this));
            set("addCustomDrop", new AddCustomDrop(this));
            set("addOreGen", OreGenApi.createAddOreGen(this));
            set("register", new RegisterBlock(this));
            set("getId", new GetId(this));
        }

        private boolean canMutate(String action) {
            if (!registered) {
                return true;
            }
            LOGGER.warning("Ignored block mutation after register: id=" + block.blockID + " action=" + action);
            return false;
        }
    }

    private static final class SetHardness extends VarArgFunction {
        private final BlockHandle handle;

        private SetHardness(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setHardness")) {
                return handle;
            }
            double value = LuaApiUtils.getNumberArg(args, 1);
            handle.block.setHardness((float) value);
            return handle;
        }
    }

    private static final class SetResistance extends VarArgFunction {
        private final BlockHandle handle;

        private SetResistance(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setResistance")) {
                return handle;
            }
            double value = LuaApiUtils.getNumberArg(args, 1);
            handle.block.setResistance((float) value);
            return handle;
        }
    }

    private static final class SetLightValue extends VarArgFunction {
        private final BlockHandle handle;

        private SetLightValue(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setLightValue")) {
                return handle;
            }
            double value = LuaApiUtils.getNumberArg(args, 1);
            handle.block.setLightValue((int) value);
            return handle;
        }
    }

    private static final class SetLightOpacity extends VarArgFunction {
        private final BlockHandle handle;

        private SetLightOpacity(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setLightOpacity")) {
                return handle;
            }
            int value = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.block.setLightOpacity(value);
            return handle;
        }
    }

    private static final class SetStepSound extends VarArgFunction {
        private final BlockHandle handle;

        private SetStepSound(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setStepSound")) {
                return handle;
            }
            String sound = LuaApiUtils.getStringArg(args, 1);
            handle.block.setStepSound(resolveStepSound(sound));
            return handle;
        }
    }

    private static final class SetUnbreakable extends VarArgFunction {
        private final BlockHandle handle;

        private SetUnbreakable(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setUnbreakable")) {
                return handle;
            }
            handle.block.setBlockUnbreakable();
            return handle;
        }
    }

    private static final class SetBlockHarvestLevel extends VarArgFunction {
        private final BlockHandle handle;

        private SetBlockHarvestLevel(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setBlockHarvestLevel")) {
                return handle;
            }
            int base = (args.narg() >= 2 && args.arg(1).istable()) ? 2 : 1;
            String toolType = args.checkjstring(base);
            int harvestLevel = args.checkint(base + 1);
            MinecraftForge.setBlockHarvestLevel(handle.block, toolType, harvestLevel);
            return handle;
        }
    }

    private static final class SetTextureId extends VarArgFunction {
        private final BlockHandle handle;

        private SetTextureId(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setTextureId")) {
                return handle;
            }
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            int textureId = args.checkint(base);
            handle.block.blockIndexInTexture = textureId;
            return handle;
        }
    }

    private static final class AddTexture extends VarArgFunction {
        private final BlockHandle handle;

        private AddTexture(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("addTexture")) {
                return handle;
            }
            String relativePath = LuaApiUtils.getStringArg(args, 1);
            int textureIndex = LuaApiUtils.registerTexture(EnumTexAtlas.BLOCKS, relativePath);
            handle.block.blockIndexInTexture = textureIndex;
            return handle;
        }
    }

    private static final class SetSideTexture extends VarArgFunction {
        private final BlockHandle handle;

        private SetSideTexture(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setSideTexture")) {
                return handle;
            }
            int base = (args.narg() >= 2 && args.arg(1).istable()) ? 2 : 1;
            LuaValue sideValue = args.arg(base);
            LuaValue textureValue = args.arg(base + 1);
            int textureIndex = resolveTextureIndex(textureValue);
            applySideTexture(handle.block, sideValue, textureIndex);
            return handle;
        }
    }

    private static final class SetTextureMap extends VarArgFunction {
        private final BlockHandle handle;

        private SetTextureMap(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setTextureMap")) {
                return handle;
            }
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            LuaValue tableValue = args.arg(base);
            if (!tableValue.istable()) {
                throw new LuaError("Block: texture map must be a table.");
            }
            // Apply "all" first, then "side", then specific overrides.
            LuaValue value = tableValue.get("all");
            if (!value.isnil()) {
                int textureIndex = resolveTextureIndex(value);
                handle.block.setAllSideTextures(textureIndex);
            }
            value = tableValue.get("sides");
            if (value.isnil()) {
                value = tableValue.get("side");
            }
            if (!value.isnil()) {
                int textureIndex = resolveTextureIndex(value);
                for (int side = 2; side <= 5; side++) {
                    handle.block.setSideTextureIndex(side, textureIndex);
                }
            }
            applyTextureEntry(handle.block, tableValue, "top", 1);
            applyTextureEntry(handle.block, tableValue, "bottom", 0);
            applyTextureEntry(handle.block, tableValue, "north", 2);
            applyTextureEntry(handle.block, tableValue, "south", 3);
            applyTextureEntry(handle.block, tableValue, "west", 4);
            applyTextureEntry(handle.block, tableValue, "east", 5);
            // Convenience aliases for fixed-orientation blocks.
            applyTextureEntry(handle.block, tableValue, "front", 3);
            applyTextureEntry(handle.block, tableValue, "back", 2);
            return handle;
        }
    }

    private static final class AddCustomDrop extends VarArgFunction {
        private final BlockHandle handle;

        private AddCustomDrop(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("addCustomDrop")) {
                return handle;
            }
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            int itemId = resolveDropId(args.arg(base));
            int minQuantity = 1;
            int maxQuantity = 1;
            if (args.narg() >= base + 1 && !args.arg(base + 1).isnil()) {
                if (args.narg() < base + 2 || args.arg(base + 2).isnil()) {
                    throw new LuaError("Block: maxQuantity is required when minQuantity is provided.");
                }
                minQuantity = args.checkint(base + 1);
                maxQuantity = args.checkint(base + 2);
            }
            if (minQuantity > maxQuantity) {
                throw new LuaError("Block: minQuantity must be less or equal to maxQuantity.");
            }
            handle.block.addCustomDrop(itemId, minQuantity, maxQuantity);
            return handle;
        }
    }

    private static void applyTextureEntry(BlockWrapper block, LuaValue table, String key, int side) {
        LuaValue value = table.get(key);
        if (value.isnil()) {
            return;
        }
        int textureIndex = resolveTextureIndex(value);
        block.setSideTextureIndex(side, textureIndex);
    }

    private static void applySideTexture(BlockWrapper block, LuaValue sideValue, int textureIndex) {
        if (sideValue.isnumber()) {
            int side = sideValue.checkint();
            if (side < 0 || side > 5) {
                throw new LuaError("Block: side index must be between 0 and 5.");
            }
            block.setSideTextureIndex(side, textureIndex);
            return;
        }
        if (!sideValue.isstring()) {
            throw new LuaError("Block: side must be a number or string.");
        }
        String name = sideValue.checkjstring().toLowerCase();
        if (name.equals("all")) {
            block.setAllSideTextures(textureIndex);
            return;
        }
        if (name.equals("side") || name.equals("sides")) {
            for (int side = 2; side <= 5; side++) {
                block.setSideTextureIndex(side, textureIndex);
            }
            return;
        }
        int side = resolveSideName(name);
        block.setSideTextureIndex(side, textureIndex);
    }

    private static int resolveSideName(String name) {
        if (name.equals("bottom") || name.equals("down")) return 0;
        if (name.equals("top") || name.equals("up")) return 1;
        if (name.equals("north") || name.equals("back")) return 2;
        if (name.equals("south") || name.equals("front")) return 3;
        if (name.equals("west")) return 4;
        if (name.equals("east")) return 5;
        throw new LuaError("Block: unknown side: " + name);
    }

    private static int resolveTextureIndex(LuaValue value) {
        if (value.isnumber()) {
            return value.checkint();
        }
        if (value.isstring()) {
            String relativePath = value.checkjstring();
            return LuaApiUtils.registerTexture(EnumTexAtlas.BLOCKS, relativePath);
        }
        throw new LuaError("Block: texture must be a number or string.");
    }

    private static final class RegisterBlock extends VarArgFunction {
        private final BlockHandle handle;

        private RegisterBlock(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.registered) {
                LOGGER.warning("Ignored duplicate block register: id=" + handle.block.blockID);
                return handle;
            }
            ModLoader.RegisterBlock(handle.block);
            if (args.narg() >= 1 && !args.arg(1).isnil()) {
                String displayName = LuaApiUtils.getStringArg(args, 1);
                ModLoader.AddName(handle.block, displayName);
            }
            handle.registered = true;
            return handle;
        }
    }

    private static final class GetId extends VarArgFunction {
        private final BlockHandle handle;

        private GetId(BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.block.blockID);
        }
    }

    /**
     * Resolves a material name to the corresponding Minecraft material.
     *
     * @param name material name from Lua
     * @return matching Material instance
     */
    private static Material resolveMaterial(String name) {
        String key = name.toLowerCase();
        if (key.equals("air")) return Material.air;
        if (key.equals("grass")) return Material.grassMaterial;
        if (key.equals("ground")) return Material.ground;
        if (key.equals("wood")) return Material.wood;
        if (key.equals("rock") || key.equals("stone")) return Material.rock;
        if (key.equals("iron")) return Material.iron;
        if (key.equals("water")) return Material.water;
        if (key.equals("lava")) return Material.lava;
        if (key.equals("leaves")) return Material.leaves;
        if (key.equals("plants")) return Material.plants;
        if (key.equals("sponge")) return Material.sponge;
        if (key.equals("cloth")) return Material.cloth;
        if (key.equals("fire")) return Material.fire;
        if (key.equals("sand")) return Material.sand;
        if (key.equals("circuits")) return Material.circuits;
        if (key.equals("glass")) return Material.glass;
        if (key.equals("tnt")) return Material.tnt;
        if (key.equals("ice")) return Material.ice;
        if (key.equals("snow")) return Material.snow;
        if (key.equals("builtsnow")) return Material.builtSnow;
        if (key.equals("cactus")) return Material.cactus;
        if (key.equals("clay")) return Material.clay;
        if (key.equals("pumpkin")) return Material.pumpkin;
        if (key.equals("portal")) return Material.portal;
        if (key.equals("cake")) return Material.cakeMaterial;
        throw new LuaError("Block: unknown material: " + name);
    }

    /**
     * Resolves a step sound name to the corresponding Minecraft sound.
     *
     * @param name step sound name from Lua
     * @return matching StepSound instance
     */
    private static StepSound resolveStepSound(String name) {
        String key = name.toLowerCase();
        if (key.equals("stone")) return Block.soundStoneFootstep;
        if (key.equals("wood")) return Block.soundWoodFootstep;
        if (key.equals("gravel")) return Block.soundGravelFootstep;
        if (key.equals("grass")) return Block.soundGrassFootstep;
        if (key.equals("metal")) return Block.soundMetalFootstep;
        if (key.equals("glass")) return Block.soundGlassFootstep;
        if (key.equals("cloth")) return Block.soundClothFootstep;
        if (key.equals("sand")) return Block.soundSandFootstep;
        throw new LuaError("Block: unknown step sound: " + name);
    }

    private static int resolveDropId(LuaValue value) {
        if (value.isnumber()) {
            int id = value.toint();
            if (id < 0) {
                throw new LuaError("Block: drop id has to be positive: " + id);
            }
            if (id < Block.blocksList.length) {
                if(Block.blocksList[id] == null) {
                    throw new LuaError("Block: drop block id is not registered: " + id);
                }
                return id;
            }
            if(Item.itemsList[id] == null) {
                throw new LuaError("Block: drop item id is not registered: " + id);
            }
            if (id >= Item.itemsList.length) {
                throw new LuaError("Block: drop item id out of range: " + id);
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
