package betamoon.luaapi;

import betamoon.wrappers.ItemAxeWrapper;
import betamoon.wrappers.ItemHoeWrapper;
import betamoon.wrappers.ItemPickaxeWrapper;
import betamoon.wrappers.ItemSpadeWrapper;
import betamoon.wrappers.ItemSwordWrapper;
import forge.MinecraftForge;
import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.Item;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class ItemToolApi {
    /**
     * Utility class that installs tool-related Lua bindings.
     */
    private ItemToolApi() {
    }

    static void attach(LuaTable module) {
        module.set("createTool", new CreateTool());
    }

    private static final class CreateTool extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            int shiftedId = args.checkint(base);
            if (shiftedId < 256) {
                throw new LuaError("Tool id must be a shifted id (>= 256): " + shiftedId);
            }
            if (shiftedId >= Item.itemsList.length) {
                throw new LuaError("Tool id out of range: " + shiftedId);
            }
            int id = shiftedId - 256;
            LuaValue materialValue = args.arg(base + 1);
            EnumToolMaterial material = resolveToolMaterial(materialValue);
            return new PendingToolHandle(id, material);
        }
    }

    private static final class PendingToolHandle extends LuaTable {
        private final int id;
        private final EnumToolMaterial material;

        private PendingToolHandle(int id, EnumToolMaterial material) {
            this.id = id;
            this.material = material;
            set("pickaxe", new AsPickaxe(this));
            set("axe", new AsAxe(this));
            set("shovel", new AsShovel(this));
            set("hoe", new AsHoe(this));
            set("sword", new AsSword(this));
        }
    }

    private static final class AsPickaxe extends VarArgFunction {
        private final PendingToolHandle handle;

        private AsPickaxe(PendingToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ItemPickaxeWrapper tool = new ItemPickaxeWrapper(handle.id, handle.material);
            tool.setIconCoord(0, 0);
            MinecraftForge.setToolClass(tool, "pickaxe", handle.material.getHarvestLevel());
            return new ToolHandle(tool);
        }
    }

    private static final class AsAxe extends VarArgFunction {
        private final PendingToolHandle handle;

        private AsAxe(PendingToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ItemAxeWrapper tool = new ItemAxeWrapper(handle.id, handle.material);
            tool.setIconCoord(0, 0);
            MinecraftForge.setToolClass(tool, "axe", handle.material.getHarvestLevel());
            return new ToolHandle(tool);
        }
    }

    private static final class AsShovel extends VarArgFunction {
        private final PendingToolHandle handle;

        private AsShovel(PendingToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ItemSpadeWrapper tool = new ItemSpadeWrapper(handle.id, handle.material);
            tool.setIconCoord(0, 0);
            MinecraftForge.setToolClass(tool, "shovel", handle.material.getHarvestLevel());
            return new ToolHandle(tool);
        }
    }

    private static final class AsHoe extends VarArgFunction {
        private final PendingToolHandle handle;

        private AsHoe(PendingToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ItemHoeWrapper tool = new ItemHoeWrapper(handle.id, handle.material);
            tool.setIconCoord(0, 0);
            MinecraftForge.setToolClass(tool, "hoe", handle.material.getHarvestLevel());
            return new ToolHandle(tool);
        }
    }

    private static final class AsSword extends VarArgFunction {
        private final PendingToolHandle handle;

        private AsSword(PendingToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int damage = (int) LuaApiUtils.getNumberArg(args, 1);
            ItemSwordWrapper tool = new ItemSwordWrapper(handle.id, handle.material, damage);
            tool.setIconCoord(0, 0);
            MinecraftForge.setToolClass(tool, "sword", handle.material.getHarvestLevel());
            return new ToolHandle(tool);
        }
    }

    private static final class ToolHandle extends LuaTable {
        private final Item item;

        private ToolHandle(Item item) {
            this.item = item;
            set("setItemName", new SetItemName(this));
            set("setMaxStackSize", new SetMaxStackSize(this));
            set("setMaxDamage", new SetMaxDamage(this));
            set("setHasSubtypes", new SetHasSubtypes(this));
            set("setFull3D", new SetFull3D(this));
            set("setIconCoord", new SetIconCoord(this));
            set("setEfficiency", new SetEfficiency(this));
            set("setDamageVsEntity", new SetDamageVsEntity(this));
            set("addTexture", new AddTexture(this));
            set("register", new RegisterTool(this));
            set("getId", new GetId(this));
        }
    }

    private static final class SetItemName extends VarArgFunction {
        private final ToolHandle handle;

        private SetItemName(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            handle.item.setItemName(name);
            return handle;
        }
    }

    private static final class SetMaxStackSize extends VarArgFunction {
        private final ToolHandle handle;

        private SetMaxStackSize(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int value = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.item.setMaxStackSize(value);
            return handle;
        }
    }

    private static final class SetMaxDamage extends VarArgFunction {
        private final ToolHandle handle;

        private SetMaxDamage(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int value = (int) LuaApiUtils.getNumberArg(args, 1);
            if (handle.item instanceof ItemPickaxeWrapper) {
                ((ItemPickaxeWrapper) handle.item).setMaxDamageValue(value);
            } else if (handle.item instanceof ItemAxeWrapper) {
                ((ItemAxeWrapper) handle.item).setMaxDamageValue(value);
            } else if (handle.item instanceof ItemSpadeWrapper) {
                ((ItemSpadeWrapper) handle.item).setMaxDamageValue(value);
            } else if (handle.item instanceof ItemHoeWrapper) {
                ((ItemHoeWrapper) handle.item).setMaxDamageValue(value);
            } else if (handle.item instanceof ItemSwordWrapper) {
                ((ItemSwordWrapper) handle.item).setMaxDamageValue(value);
            } else {
                throw new LuaError("Tool type does not support max damage.");
            }
            return handle;
        }
    }

    private static final class SetHasSubtypes extends VarArgFunction {
        private final ToolHandle handle;

        private SetHasSubtypes(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            boolean value = args.arg(1).toboolean();
            if (handle.item instanceof ItemPickaxeWrapper) {
                ((ItemPickaxeWrapper) handle.item).setHasSubtypesValue(value);
            } else if (handle.item instanceof ItemAxeWrapper) {
                ((ItemAxeWrapper) handle.item).setHasSubtypesValue(value);
            } else if (handle.item instanceof ItemSpadeWrapper) {
                ((ItemSpadeWrapper) handle.item).setHasSubtypesValue(value);
            } else if (handle.item instanceof ItemHoeWrapper) {
                ((ItemHoeWrapper) handle.item).setHasSubtypesValue(value);
            } else if (handle.item instanceof ItemSwordWrapper) {
                ((ItemSwordWrapper) handle.item).setHasSubtypesValue(value);
            } else {
                throw new LuaError("Tool type does not support subtypes.");
            }
            return handle;
        }
    }

    private static final class SetFull3D extends VarArgFunction {
        private final ToolHandle handle;

        private SetFull3D(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.item.setFull3D();
            return handle;
        }
    }

    private static final class SetIconCoord extends VarArgFunction {
        private final ToolHandle handle;

        private SetIconCoord(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 2 && args.arg(1).istable()) ? 2 : 1;
            int iconX = args.checkint(base);
            int iconY = args.checkint(base + 1);
            handle.item.setIconCoord(iconX, iconY);
            return handle;
        }
    }

    private static final class SetEfficiency extends VarArgFunction {
        private final ToolHandle handle;

        private SetEfficiency(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            double value = LuaApiUtils.getNumberArg(args, 1);
            if (handle.item instanceof ItemPickaxeWrapper) {
                ((ItemPickaxeWrapper) handle.item).setEfficiencyValue((float) value);
            } else if (handle.item instanceof ItemAxeWrapper) {
                ((ItemAxeWrapper) handle.item).setEfficiencyValue((float) value);
            } else if (handle.item instanceof ItemSpadeWrapper) {
                ((ItemSpadeWrapper) handle.item).setEfficiencyValue((float) value);
            } else {
                throw new LuaError("Tool type does not support efficiency.");
            }
            return handle;
        }
    }

    private static final class SetDamageVsEntity extends VarArgFunction {
        private final ToolHandle handle;

        private SetDamageVsEntity(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int value = (int) LuaApiUtils.getNumberArg(args, 1);
            if (handle.item instanceof ItemPickaxeWrapper) {
                ((ItemPickaxeWrapper) handle.item).setDamageValue(value);
            } else if (handle.item instanceof ItemAxeWrapper) {
                ((ItemAxeWrapper) handle.item).setDamageValue(value);
            } else if (handle.item instanceof ItemSpadeWrapper) {
                ((ItemSpadeWrapper) handle.item).setDamageValue(value);
            } else if (handle.item instanceof ItemSwordWrapper) {
                ((ItemSwordWrapper) handle.item).setDamageValue(value);
            } else {
                throw new LuaError("Tool type does not support damage.");
            }
            return handle;
        }
    }

    private static final class AddTexture extends VarArgFunction {
        private final ToolHandle handle;

        private AddTexture(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String relativePath = LuaApiUtils.getStringArg(args, 1);
            int textureIndex = LuaApiUtils.registerTexture(EnumTexAtlas.ITEMS, relativePath);
            handle.item.setIconIndex(textureIndex);
            return handle;
        }
    }

    private static final class RegisterTool extends VarArgFunction {
        private final ToolHandle handle;

        private RegisterTool(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (args.narg() >= 1 && !args.arg(1).isnil()) {
                String displayName = LuaApiUtils.getStringArg(args, 1);
                ModLoader.AddName(handle.item, displayName);
            }
            return LuaValue.NIL;
        }
    }

    private static final class GetId extends VarArgFunction {
        private final ToolHandle handle;

        private GetId(ToolHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.item.shiftedIndex);
        }
    }

    private static EnumToolMaterial resolveToolMaterial(LuaValue value) {
        if (value.isuserdata()) {
            Object userdata = value.touserdata();
            if (userdata instanceof EnumToolMaterial) {
                return (EnumToolMaterial) userdata;
            }
        }
        if (value.isstring()) {
            String name = value.checkjstring();
            if (name.equalsIgnoreCase("diamond")) {
                name = "emerald";
            }
            try {
                return EnumToolMaterial.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new LuaError("Unknown tool material: " + name);
            }
        }
        throw new LuaError("Tool material must be a material userdata or a name string.");
    }

}
