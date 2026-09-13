package betamoon.luaapi.item;

import net.minecraft.src.Entity;
import net.minecraft.src.EntityArrow;
import net.minecraft.src.EntityEgg;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntitySnowball;
import net.minecraft.src.World;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;

/** Supported projectiles that a declarative item can fire. */
public enum ProjectileType {
    ARROW("arrow") {
        Entity create(World world, EntityPlayer player) {
            return new EntityArrow(world, player);
        }
    },
    SNOWBALL("snowball") {
        Entity create(World world, EntityPlayer player) {
            return new EntitySnowball(world, player);
        }
    },
    EGG("egg") {
        Entity create(World world, EntityPlayer player) {
            return new EntityEgg(world, player);
        }
    };

    private final String luaName;

    ProjectileType(String luaName) {
        this.luaName = luaName;
    }

    abstract Entity create(World world, EntityPlayer player);

    public String getLuaName() {
        return luaName;
    }

    public static ProjectileType parse(String name) {
        for (ProjectileType type : values()) {
            if (type.luaName.equals(name)) {
                return type;
            }
        }
        throw error("use.projectile", "expected arrow, snowball, or egg");
    }
}
