package betamoon.luaapi.item;

import betamoon.content.ContentKey;
import betamoon.content.ContentKeyException;
import betamoon.content.ContentNamespaces;
import betamoon.content.ContentType;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.item.FluidBucketItem;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luaapi.fluid.LuaFluidDefinition;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.resources.EnumTexAtlas;
import net.minecraft.src.Item;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Specialized item-domain registration for a fluid's filled bucket. */
public final class FluidBucketRegistration {
    private FluidBucketRegistration() {
    }

    public static void preflight(LuaValue value) {
        fields(value, "fluid bucket", "type", "fluid", "id", "key", "displayName", "texture", "icon");
        if (!"fluid_bucket".equals(required(value, "type").checkjstring())) {
            throw new LuaError("fluid bucket.type must be 'fluid_bucket'.");
        }
        LuaFluidDefinition token = LuaFluidDefinition.require(required(value, "fluid"));
        int id = integer(required(value, "id"), "fluid bucket.id", 256, Item.itemsList.length - 1);
        ContentKey key = itemKey(required(value, "key"));
        FluidRegistration retainedFluid = FluidRegistry.instance().get(token.draft().key());
        if (retainedFluid != null && (!retainedFluid.definition().hasBucket()
                || retainedFluid.definition().bucketItemId() != id
                || !retainedFluid.owner().equals(token.draft().owner()))) {
            throw new LuaError("Fluid '" + token.draft().key()
                    + "' changed structural fields. Restart Minecraft to apply this change.");
        }
        validateTexture(value);
        String signature = signature(token, key);
        LuaContentRegistry.Entry entry = LuaContentRegistry.find("item", id);
        if (entry == null) {
            if (Item.itemsList[id] != null) {
                throw new LuaError("Fluid bucket: item ID " + id + " is already occupied.");
            }
            return;
        }
        LuaContentRegistry.remember("item", id, entry.value, signature);
        if (!(entry.value instanceof FluidBucketItem)) {
            throw new LuaError("Fluid bucket " + id + " has an incompatible retained native type.");
        }
    }

    public static Item register(LuaValue value) {
        preflight(value);
        fields(value, "fluid bucket", "type", "fluid", "id", "key", "displayName", "texture", "icon");
        if (!"fluid_bucket".equals(required(value, "type").checkjstring())) {
            throw new LuaError("fluid bucket.type must be 'fluid_bucket'.");
        }
        LuaFluidDefinition token = LuaFluidDefinition.require(required(value, "fluid"));
        int id = integer(required(value, "id"), "fluid bucket.id", 256, Item.itemsList.length - 1);
        ContentKey key = itemKey(required(value, "key"));
        String displayName = value.get("displayName").optjstring(key.name());
        int texture = texture(value);
        String signature = signature(token, key);

        LuaContentRegistry.Entry entry = LuaContentRegistry.find("item", id);
        FluidBucketItem item;
        if (entry == null) {
            try {
                item = new FluidBucketItem(id);
            } catch (RuntimeException error) {
                throw new LuaError("Fluid bucket: " + error.getMessage());
            }
            entry = LuaContentRegistry.remember("item", id, item, signature);
        } else {
            LuaContentRegistry.remember("item", id, entry.value, signature);
            if (!(entry.value instanceof FluidBucketItem)) {
                throw new LuaError("Fluid bucket " + id + " has an incompatible retained native type.");
            }
            item = (FluidBucketItem) entry.value;
        }
        item.configureName(key.name());
        item.setIconIndex(texture);
        ModLoader.AddName(item, displayName);
        entry.registered = true;
        token.attachBucket(item);
        return item;
    }

    private static int texture(LuaValue value) {
        LuaValue texture = value.get("texture");
        if (!texture.isnil()) {
            return texture.isnumber()
                    ? texture.checkint()
                    : LuaApiUtils.registerTexture(EnumTexAtlas.ITEMS, AssetInputs.texture(texture));
        }
        LuaValue icon = value.get("icon");
        if (icon.istable()) {
            return icon.get("x").checkint() + icon.get("y").checkint() * 16;
        }
        throw new LuaError("Fluid bucket requires texture or icon = {x, y}.");
    }

    private static void validateTexture(LuaValue value) {
        LuaValue texture = value.get("texture");
        if (!texture.isnil()) {
            if (texture.isnumber()) {
                if (texture.checkint() < 0) {
                    throw new LuaError("Fluid bucket texture index must be nonnegative.");
                }
            } else {
                AssetInputs.texture(texture);
            }
            return;
        }
        LuaValue icon = value.get("icon");
        if (!icon.istable()) {
            throw new LuaError("Fluid bucket requires texture or icon = {x, y}.");
        }
        icon.get("x").checkint();
        icon.get("y").checkint();
    }

    private static String signature(LuaFluidDefinition token, ContentKey key) {
        return "fluid_bucket:" + token.draft().key() + ":" + key;
    }

    private static ContentKey itemKey(LuaValue value) {
        try {
            ContentKey key = ContentKey.parseForType(value.checkjstring(), ContentType.ITEM);
            ContentNamespaces.requireUserNamespace(key);
            return key;
        } catch (ContentKeyException error) {
            throw new LuaError("fluid bucket.key: " + error.getMessage());
        }
    }
}
