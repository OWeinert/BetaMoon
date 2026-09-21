package betamoon.luaapi.item;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityKind;
import betamoon.entity.EntityTypeDefinition;
import betamoon.luaapi.entity.EntityTypeReference;
import betamoon.luamodloader.ScriptEntityScope;
import net.minecraft.src.Item;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.id;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * Consumption, remainders, cooldowns and projectile settings for a general item
 * use.
 */
public final class ItemUseDefinition {
    public final boolean food;
    public final int consume;
    public final int remainder;
    public final int cooldown;
    public final ProjectileType projectile;
    public final AssetKey customProjectile;
    public final int ammunition;

    public ItemUseDefinition(LuaValue def) {
        food = def.get("type").optjstring("item").equalsIgnoreCase("food");
        LuaValue use = def.get("use");
        int count = 0;
        int rem = -1;
        int delay = 0;
        int ammo = -1;
        ProjectileType shot = null;
        AssetKey customShot = null;
        if (!use.isnil()) {
            fields(use, "use", "consume", "remainder", "cooldown", "projectile", "ammunition");
            count = use.get("consume").isnil() ? 0 : integer(use.get("consume"), "use.consume", 0, 64);
            if (food && !use.get("consume").isnil() && count != 1) {
                throw error("use.consume", "food consumes exactly one item");
            }
            if (!use.get("remainder").isnil()) {
                rem = id(use.get("remainder"), "use.remainder");
                if (rem == 0) {
                    throw error("use.remainder", "air is not an item");
                }
            }
            delay = use.get("cooldown").isnil() ? 0 : integer(use.get("cooldown"), "use.cooldown", 0, 1000000);
            if (!use.get("projectile").isnil()) {
                LuaValue choice = use.get("projectile");
                if (choice instanceof EntityTypeReference) {
                    customShot = ((EntityTypeReference) choice).key();
                } else {
                    String name = string(choice, "use.projectile");
                    if (name.indexOf(':') >= 0) {
                        customShot = AssetKey.parse(name);
                    } else {
                        shot = ProjectileType.parse(name);
                    }
                }
                if (food) {
                    throw error("use.projectile", "food cannot also fire a projectile");
                }
                if (customShot != null) {
                    EntityTypeDefinition type = ScriptEntityScope.findVisible(customShot);
                    if (type == null || type.kind != EntityKind.PROJECTILE) {
                        throw error("use.projectile", "expected a registered projectile entity type");
                    }
                }
            }
            if (!use.get("ammunition").isnil()) {
                ammo = id(use.get("ammunition"), "use.ammunition");
            }
            if (ammo >= 0 && shot == null && customShot == null) {
                throw error("use.ammunition", "requires projectile");
            }
            if (rem >= 0 && count == 0 && !food) {
                throw error("use.remainder", "requires consuming an item");
            }
        }
        consume = food ? 1 : count;
        remainder = rem;
        cooldown = delay;
        projectile = shot;
        customProjectile = customShot;
        ammunition = ammo;
        validateItem(remainder, "use.remainder");
        validateItem(ammunition, "use.ammunition");
    }

    public boolean hasProjectile() {
        return projectile != null || customProjectile != null;
    }

    private static void validateItem(int id, String path) {
        if (id >= 0 && (id == 0 || id >= Item.itemsList.length || Item.itemsList[id] == null)) {
            throw error(path, "reference an item registered before this definition");
        }
    }
}
