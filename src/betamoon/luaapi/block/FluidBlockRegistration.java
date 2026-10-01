package betamoon.luaapi.block;

import betamoon.content.ContentKey;
import betamoon.content.ContentKeyException;
import betamoon.content.ContentNamespaces;
import betamoon.content.ContentType;
import betamoon.fluid.FluidBlockRole;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.block.FlowingFluidBlock;
import betamoon.fluid.block.FluidBlock;
import betamoon.fluid.block.StillFluidBlock;
import betamoon.luaapi.fluid.LuaFluidDefinition;
import betamoon.luamodloader.LuaContentRegistry;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemBlock;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Specialized block-domain registration for one half of a fluid pair. */
public final class FluidBlockRegistration {
    private FluidBlockRegistration() {
    }

    public static void preflight(LuaValue value) {
        fields(value, "fluid block", "type", "fluid", "fluidRole", "id", "key", "displayName");
        if (!"fluid".equals(required(value, "type").checkjstring())) {
            throw new LuaError("fluid block.type must be 'fluid'.");
        }
        LuaFluidDefinition token = LuaFluidDefinition.require(required(value, "fluid"));
        FluidBlockRole role = role(required(value, "fluidRole").checkjstring());
        int id = integer(required(value, "id"), "fluid block.id", 1, 255);
        ContentKey key = blockKey(required(value, "key"));
        FluidRegistration retainedFluid = FluidRegistry.instance().get(token.draft().key());
        if (retainedFluid != null) {
            int retainedId = role == FluidBlockRole.FLOWING
                    ? retainedFluid.definition().flowingBlockId()
                    : retainedFluid.definition().stillBlockId();
            if (retainedId != id || !retainedFluid.owner().equals(token.draft().owner())
                    || !retainedFluid.definition().carrier().value().equals(token.draft().carrier().value())) {
                throw new LuaError("Fluid '" + token.draft().key()
                        + "' changed structural fields. Restart Minecraft to apply this change.");
            }
        }
        String signature = signature(token, role, key);
        LuaContentRegistry.Entry entry = LuaContentRegistry.find("block", id);
        if (entry == null) {
            if (Block.blocksList[id] != null) {
                throw new LuaError("Fluid block: block ID " + id + " is already occupied.");
            }
            return;
        }
        LuaContentRegistry.remember("block", id, entry.value, signature);
        if (!(entry.value instanceof FluidBlock) || !matchesRole((FluidBlock) entry.value, role)) {
            throw new LuaError("Fluid block " + id + " has an incompatible retained native type.");
        }
    }

    public static Block register(LuaValue value) {
        preflight(value);
        fields(value, "fluid block", "type", "fluid", "fluidRole", "id", "key", "displayName");
        if (!"fluid".equals(required(value, "type").checkjstring())) {
            throw new LuaError("fluid block.type must be 'fluid'.");
        }
        LuaFluidDefinition token = LuaFluidDefinition.require(required(value, "fluid"));
        FluidBlockRole role = role(required(value, "fluidRole").checkjstring());
        int id = integer(required(value, "id"), "fluid block.id", 1, 255);
        ContentKey key = blockKey(required(value, "key"));
        String displayName = value.get("displayName").optjstring(key.name());
        String signature = signature(token, role, key);

        LuaContentRegistry.Entry entry = LuaContentRegistry.find("block", id);
        FluidBlock block;
        if (entry == null) {
            try {
                block = role == FluidBlockRole.FLOWING
                        ? new FlowingFluidBlock(id, token.draft().carrier().material(), token.draft().appearance())
                        : new StillFluidBlock(id, token.draft().carrier().material(), token.draft().appearance());
            } catch (RuntimeException error) {
                throw new LuaError("Fluid block: " + error.getMessage());
            }
            entry = LuaContentRegistry.remember("block", id, block, signature);
        } else {
            LuaContentRegistry.remember("block", id, entry.value, signature);
            if (!(entry.value instanceof FluidBlock) || !matchesRole((FluidBlock) entry.value, role)) {
                throw new LuaError("Fluid block " + id + " has an incompatible retained native type.");
            }
            block = (FluidBlock) entry.value;
        }
        block.configureName(key.name());
        if (!entry.registered) {
            ModLoader.RegisterBlock(block);
            if (Item.itemsList[id] instanceof ItemBlock) {
                Item.itemsList[id] = null;
            }
            entry.registered = true;
        }
        ModLoader.AddName(block, displayName);
        token.attachBlock(role, block);
        return block;
    }

    private static boolean matchesRole(FluidBlock block, FluidBlockRole role) {
        return role == FluidBlockRole.FLOWING ? block instanceof FlowingFluidBlock : block instanceof StillFluidBlock;
    }

    private static String signature(LuaFluidDefinition token, FluidBlockRole role, ContentKey key) {
        return "fluid_block:" + token.draft().key() + ":" + role.name().toLowerCase() + ":"
                + token.draft().carrier().value() + ":" + key;
    }

    private static FluidBlockRole role(String value) {
        if ("flowing".equals(value)) {
            return FluidBlockRole.FLOWING;
        }
        if ("still".equals(value)) {
            return FluidBlockRole.STILL;
        }
        throw new LuaError("fluid block.fluidRole must be flowing or still.");
    }

    private static ContentKey blockKey(LuaValue value) {
        try {
            ContentKey key = ContentKey.parseForType(value.checkjstring(), ContentType.BLOCK);
            ContentNamespaces.requireUserNamespace(key);
            return key;
        } catch (ContentKeyException error) {
            throw new LuaError("fluid block.key: " + error.getMessage());
        }
    }
}
