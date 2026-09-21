package betamoon.luaapi.entity;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityBehaviorDefinition;
import betamoon.entity.EntityDamageEvents;
import betamoon.entity.EntityDeaths;
import betamoon.entity.EntityEquipmentDefinition;
import betamoon.entity.EntityInstanceState;
import betamoon.entity.EntityInventoryState;
import betamoon.entity.EntityLoot;
import betamoon.entity.EntityMounts;
import betamoon.entity.EntityPresentationEvents;
import betamoon.entity.EntityPresentationState;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.LuaEntityPart;
import betamoon.entity.LuaLivingEntity;
import betamoon.entity.LuaPickupEntity;
import betamoon.entity.LuaProjectileEntity;
import betamoon.entity.MultipartEntity;
import betamoon.entity.TypedEntity;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.audio.SoundEvents;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.List;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityCreature;
import net.minecraft.src.EntityList;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.PathEntity;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Narrow live access for a single callback invocation. */
public final class LuaEntityActionAccess {
    private LuaEntityActionAccess() {
    }

    public static LuaTable create(LuaCallbackScope scope, EntityPlayer player, final Entity entity) {

        final LuaEntityReference api = new LuaEntityReference(scope, entity);
        api.set("isPlayer", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(entity instanceof EntityPlayer);
            }
        });
        api.set("isSneaking", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(entity.isSneaking());
            }
        });
        api.set("isAlive", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(entity.isEntityAlive());
            }
        });
        api.set("getNativeType", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                String name = EntityList.getEntityString(entity);
                return name == null ? NIL : valueOf(name);
            }
        });
        api.set("getName", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                if (entity instanceof EntityPlayer) {
                    return valueOf(((EntityPlayer) entity).username);
                }
                if (entity instanceof TypedEntity) {
                    EntityTypeDefinition definition = ((TypedEntity) entity).entityState().definition();
                    if (definition != null) {
                        return valueOf(definition.displayName);
                    }
                }
                String nativeType = EntityList.getEntityString(entity);
                return nativeType == null ? NIL : valueOf(nativeType);
            }
        });
        api.set("getAge", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(entity.ticksExisted);
            }
        });
        api.set("getEnvironment", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                LuaTable state = new LuaTable();
                state.set("onGround", valueOf(entity.onGround));
                state.set("inWater", valueOf(entity.isInWater()));
                state.set("burning", valueOf(entity.isBurning()));
                state.set("collidedHorizontally", valueOf(entity.isCollidedHorizontally));
                state.set("collidedVertically", valueOf(entity.isCollidedVertically));
                return state;
            }
        });
        api.set("heal", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                int amount = integer(argument(a, api, 1), "heal", 0, 32767);
                if (entity instanceof EntityLiving) {
                    if (entity.isDead || ((EntityLiving) entity).health <= 0) {
                        return FALSE;
                    }
                    ((EntityLiving) entity).heal(amount);
                    return TRUE;
                }
                if (!(entity instanceof TypedEntity) || entity.isDead) {
                    return FALSE;
                }
                EntityInstanceState state = ((TypedEntity) entity).entityState();
                EntityTypeDefinition definition = state.definition();
                if (definition == null || definition.health == null || state.health() <= 0) {
                    return FALSE;
                }
                state.health(Math.min(definition.health.max, state.health() + amount));
                return TRUE;
            }
        });
        api.set("damage", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                int amount = integer(argument(a, api, 1), "damage", 0, 32767);
                return valueOf(EntityDamageEvents.withOrigin(player, "script",
                        () -> entity.attackEntityFrom(player, amount)));
            }
        });
        api.set("kill", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                LuaValue attackerValue = argument(a, api, 1);
                Entity attacker = attackerValue.isnil() ? null : target(attackerValue, entity, "kill");
                if (!attackerValue.isnil() && attacker == null) {
                    return FALSE;
                }
                return valueOf(EntityDeaths.kill(entity, attacker));
            }
        });
        api.set("dropLoot", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                return valueOf(EntityLoot.dropLoot(entity));
            }
        });
        api.set("dropItem", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                ItemStack stack = itemStack(argument(a, api, 1), "dropItem.item");
                return valueOf(EntityLoot.dropItem(entity, stack));
            }
        });
        api.set("attack", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                Entity victim = target(argument(a, api, 1), entity, "attack");
                int amount = integer(argument(a, api, 2), "attack.damage", 0, 32767);
                return valueOf(victim != null && EntityDamageEvents.withOrigin(entity, "script",
                        () -> victim.attackEntityFrom(entity, amount)));
            }
        });
        api.set("canSee", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                Entity other = target(argument(a, api, 1), entity, "canSee");
                return valueOf(other != null && entity instanceof EntityLiving
                        && ((EntityLiving) entity).canEntityBeSeen(other));
            }
        });
        api.set("getDistanceTo", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                Entity other = target(argument(a, api, 1), entity, "getDistanceTo");
                return other == null ? NIL : valueOf(entity.getDistanceToEntity(other));
            }
        });
        api.set("setVelocity", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                double x = number(argument(a, api, 1), "velocity.x");
                double y = number(argument(a, api, 2), "velocity.y");
                double z = number(argument(a, api, 3), "velocity.z");
                if (Math.abs(x) > 16 || Math.abs(y) > 16 || Math.abs(z) > 16) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("setVelocity",
                            "each motion component must be within -16..16 blocks per tick");
                }
                entity.motionX = x;
                entity.motionY = y;
                entity.motionZ = z;
                return NIL;
            }
        });
        api.set("getVelocity", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return velocitySnapshot(entity);
            }
        });
        api.set("setMovement", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (!(entity instanceof LuaLivingEntity)) {
                    return FALSE;
                }
                double forward = number(argument(a, api, 1), "setMovement.forward");
                double strafe = number(argument(a, api, 2), "setMovement.strafe");
                LuaValue jump = argument(a, api, 3);
                if (Math.abs(forward) > 1 || Math.abs(strafe) > 1 || !jump.isboolean()) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("setMovement",
                            "forward and strafe must be -1..1 and jump must be a boolean");
                }
                return valueOf(((LuaLivingEntity) entity).setManualMovement((float) forward,
                        (float) strafe, jump.toboolean()));
            }
        });
        api.set("setFacing", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                float yaw = (float) number(argument(a, api, 1), "setFacing.yaw");
                float pitch = (float) number(argument(a, api, 2), "setFacing.pitch");
                if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("setFacing",
                            "angles exceed the supported range");
                }
                entity.rotationYaw = yaw;
                entity.rotationPitch = pitch;
                return NIL;
            }
        });
        api.set("facePosition", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                double x = number(argument(a, api, 1), "facePosition.x");
                double y = number(argument(a, api, 2), "facePosition.y");
                double z = number(argument(a, api, 3), "facePosition.z");
                float maxYaw = argument(a, api, 4).isnil() ? 180
                        : (float) number(argument(a, api, 4), "facePosition.maxYaw");
                float maxPitch = argument(a, api, 5).isnil() ? 180
                        : (float) number(argument(a, api, 5), "facePosition.maxPitch");
                if (maxYaw < 0 || maxYaw > 180 || maxPitch < 0 || maxPitch > 180) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("facePosition",
                            "maximum yaw and pitch must be 0..180 degrees");
                }
                double dx = x - entity.posX;
                double dy = y - entity.posY;
                double dz = z - entity.posZ;
                if (dx * dx + dy * dy + dz * dz < 0.000000000001) {
                    return FALSE;
                }
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                entity.rotationYaw = turnToward(entity.rotationYaw, yaw, maxYaw);
                entity.rotationPitch = turnToward(entity.rotationPitch, pitch, maxPitch);
                return TRUE;
            }
        });
        api.set("faceToward", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                Entity other = target(argument(a, api, 1), entity, "faceToward");
                if (other == null || !(entity instanceof EntityLiving)) {
                    return FALSE;
                }
                float maxYaw = argument(a, api, 2).isnil() ? 30
                        : (float) number(argument(a, api, 2), "faceToward.maxYaw");
                float maxPitch = argument(a, api, 3).isnil() ? 30
                        : (float) number(argument(a, api, 3), "faceToward.maxPitch");
                if (maxYaw < 0 || maxYaw > 180 || maxPitch < 0 || maxPitch > 180) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("faceToward",
                            "maximum yaw and pitch must be 0..180 degrees");
                }
                ((EntityLiving) entity).faceEntity(other, maxYaw, maxPitch);
                return TRUE;
            }
        });
        api.set("setPartOffset", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (!(entity instanceof MultipartEntity)) {
                    return FALSE;
                }
                String name = argument(a, api, 1).checkjstring();
                double x = number(argument(a, api, 2), "setPartOffset.x");
                double y = number(argument(a, api, 3), "setPartOffset.y");
                double z = number(argument(a, api, 4), "setPartOffset.z");
                if (Math.abs(x) > 16 || Math.abs(y) > 16 || Math.abs(z) > 16) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("setPartOffset",
                            "offset components must be within -16..16 blocks");
                }
                return valueOf(((MultipartEntity) entity).setPartOffset(name, x, y, z));
            }
        });
        api.set("setInteractionOffset", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (!(entity instanceof MultipartEntity)) {
                    return FALSE;
                }
                String name = argument(a, api, 1).checkjstring();
                double x = number(argument(a, api, 2), "setInteractionOffset.x");
                double y = number(argument(a, api, 3), "setInteractionOffset.y");
                double z = number(argument(a, api, 4), "setInteractionOffset.z");
                if (Math.abs(x) > 16 || Math.abs(y) > 16 || Math.abs(z) > 16) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("setInteractionOffset",
                            "offset components must be within -16..16 blocks");
                }
                return valueOf(((MultipartEntity) entity).setInteractionOffset(name, x, y, z));
            }
        });
        if (entity instanceof TypedEntity && !(entity instanceof LuaEntityPart)) {
            EntityInstanceState typedState = ((TypedEntity) entity).entityState();
            api.set("setSensorOffset", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireMutable();
                    String name = argument(a, api, 1).checkjstring();
                    double x = number(argument(a, api, 2), "setSensorOffset.x");
                    double y = number(argument(a, api, 3), "setSensorOffset.y");
                    double z = number(argument(a, api, 4), "setSensorOffset.z");
                    if (Math.abs(x) > 16 || Math.abs(y) > 16 || Math.abs(z) > 16) {
                        throw betamoon.luaapi.utils.LuaDeclarationValues.error("setSensorOffset",
                                "offset components must be within -16..16 blocks");
                    }
                    return valueOf(typedState.setSensorOffset(entity, name, x, y, z));
                }
            });
            api.set("playAnimation", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireMutable();
                    EntityTypeDefinition definition = typedState.definition();
                    if (definition == null || definition.appearance == null
                            || definition.appearance.animation == null) {
                        return FALSE;
                    }
                    String clip = argument(a, api, 1).checkjstring();
                    if (clip.isEmpty() || clip.length() > 256) {
                        throw betamoon.luaapi.utils.LuaDeclarationValues.error("playAnimation.clip",
                                "expected 1..256 characters");
                    }
                    LuaValue value = argument(a, api, 2);
                    LuaTable options = value.isnil() ? new LuaTable() : value.checktable();
                    fields(options, "playAnimation", "speed", "startTime", "restart");
                    double speed = options.get("speed").isnil() ? 1
                            : number(options.get("speed"), "playAnimation.speed");
                    double startTime = options.get("startTime").isnil() ? 0
                            : number(options.get("startTime"), "playAnimation.startTime");
                    LuaValue restartValue = options.get("restart");
                    if (speed < 0 || speed > 100 || startTime < 0 || startTime > 3600
                            || !restartValue.isnil() && !restartValue.isboolean()) {
                        throw betamoon.luaapi.utils.LuaDeclarationValues.error("playAnimation",
                                "speed must be 0..100, startTime 0..3600, and restart boolean");
                    }
                    typedState.presentation().playAnimation(clip, speed, startTime,
                            restartValue.optboolean(false), entity.ticksExisted);
                    return TRUE;
                }
            });
            api.set("stopAnimation", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireMutable();
                    EntityPresentationState presentation = typedState.presentation();
                    boolean playing = presentation.animation() != null;
                    presentation.stopAnimation();
                    return valueOf(playing);
                }
            });
            api.set("getAnimation", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    EntityPresentationState.Animation animation = typedState.presentation().animation();
                    if (animation == null) {
                        return NIL;
                    }
                    LuaTable result = new LuaTable();
                    result.set("clip", animation.clip);
                    result.set("speed", animation.speed);
                    result.set("time", animation.seconds(entity.ticksExisted));
                    return result;
                }
            });
            api.set("setVisible", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireMutable();
                    LuaValue visible = argument(a, api, 1);
                    if (!visible.isboolean()) {
                        throw betamoon.luaapi.utils.LuaDeclarationValues.error("setVisible", "expected a boolean");
                    }
                    typedState.presentation().visible(visible.toboolean());
                    return NONE;
                }
            });
            api.set("setVisualOffset", visualVector(scope, api, typedState, "setVisualOffset", -16, 16, false));
            api.set("setVisualScale", visualVector(scope, api, typedState, "setVisualScale", 0, 64, true));
            api.set("setVisualRotation", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireMutable();
                    float yaw = (float) number(argument(a, api, 1), "setVisualRotation.yaw");
                    float pitch = (float) number(argument(a, api, 2), "setVisualRotation.pitch");
                    float roll = (float) number(argument(a, api, 3), "setVisualRotation.roll");
                    if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
                        throw betamoon.luaapi.utils.LuaDeclarationValues.error("setVisualRotation",
                                "angles exceed the supported range");
                    }
                    typedState.presentation().visualRotation(yaw, pitch, roll);
                    return NONE;
                }
            });
            api.set("resetVisuals", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireMutable();
                    typedState.presentation().resetVisuals();
                    return NONE;
                }
            });
            installInventoryAccess(api, scope, entity, typedState);
            installRelationAccess(api, scope, entity, typedState);
            installMountAccess(api, scope, entity, typedState);
            installBehaviorAccess(api, scope, entity, typedState);
        }
        api.set("getPassenger", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return entity.riddenByEntity == null ? NIL : create(scope, null, entity.riddenByEntity);
            }
        });
        api.set("getVehicle", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return entity.ridingEntity == null ? NIL : create(scope, null, entity.ridingEntity);
            }
        });
        api.set("dismount", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                return valueOf(EntityMounts.dismount(entity));
            }
        });
        api.set("playSound", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (entity.isDead || entity.worldObj == null) {
                    return FALSE;
                }
                AssetKey event = SoundEvents.requireKey(argument(a, api, 1), "playSound.event");
                LuaValue value = argument(a, api, 2);
                LuaTable options = value.isnil() ? new LuaTable() : value.checktable();
                fields(options, "playSound", "volume", "pitch", "range");
                float volume = optionalSoundNumber(options.get("volume"), 0, 1, "playSound.volume");
                float pitch = optionalSoundNumber(options.get("pitch"), 0.01, 4, "playSound.pitch");
                float range = optionalSoundNumber(options.get("range"), 0.01, 1024, "playSound.range");
                EntityPresentationEvents.sound(entity, event, volume, pitch, range);
                return TRUE;
            }
        });
        if (entity instanceof LuaEntityPart) {
            api.set("getPartName", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    return valueOf(((LuaEntityPart) entity).partName());
                }
            });
            api.set("getPartRole", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    return valueOf(((LuaEntityPart) entity).roleName());
                }
            });
            api.set("getParent", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    return create(scope, null, ((LuaEntityPart) entity).parent());
                }
            });
        }
        if (entity instanceof LuaProjectileEntity) {
            api.set("getOwner", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    Entity owner = ((LuaProjectileEntity) entity).getOwner();
                    return owner == null ? NIL : create(scope, null, owner);
                }
            });
            api.set("getOwnerIdentity", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    String identity = ((LuaProjectileEntity) entity).getOwnerIdentity();
                    return identity == null ? NIL : valueOf(identity);
                }
            });
        }
        if (entity instanceof LuaPickupEntity) {
            api.set("getPickupStack", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    net.minecraft.src.ItemStack stack = ((LuaPickupEntity) entity).item;
                    if (stack == null) {
                        return NIL;
                    }
                    LuaTable snapshot = new LuaTable();
                    snapshot.set("id", stack.itemID);
                    snapshot.set("count", stack.stackSize);
                    snapshot.set("damage", stack.getItemDamage());
                    return snapshot;
                }
            });
            api.set("collect", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireMutable();
                    Entity receiver = target(argument(a, api, 1), entity, "collect");
                    return valueOf(receiver instanceof EntityPlayer
                            && ((LuaPickupEntity) entity).collectInto((EntityPlayer) receiver));
                }
            });
        }
        api.set("getHealth", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                if (entity instanceof EntityLiving) {
                    return valueOf(Math.max(0, ((EntityLiving) entity).health));
                }
                if (entity instanceof TypedEntity) {
                    EntityInstanceState state = ((TypedEntity) entity).entityState();
                    EntityTypeDefinition definition = state.definition();
                    if (definition != null && definition.health != null) {
                        return valueOf(state.health());
                    }
                }
                return NIL;
            }
        });
        api.set("getMaxHealth", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                if (!(entity instanceof TypedEntity)) {
                    return NIL;
                }
                EntityTypeDefinition definition = ((TypedEntity) entity).entityState().definition();
                if (definition == null) {
                    return NIL;
                }
                if (definition.living != null) {
                    return valueOf(definition.living.maxHealth);
                }
                return definition.health == null ? NIL : valueOf(definition.health.max);
            }
        });
        api.set("navigateTo", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (!(entity instanceof EntityCreature) || entity.worldObj == null
                        || entity instanceof LuaLivingEntity && !((LuaLivingEntity) entity).usesNativeNavigation()) {
                    return FALSE;
                }
                int x = integer(argument(a, api, 1), "navigateTo.x", -30000000, 30000000);
                int y = integer(argument(a, api, 2), "navigateTo.y", 0, 127);
                int z = integer(argument(a, api, 3), "navigateTo.z", -30000000, 30000000);
                float range = argument(a, api, 4).isnil() ? 16
                        : (float) number(argument(a, api, 4), "navigateTo.range");
                if (range <= 0 || range > 32 || !entity.worldObj.blockExists(x, y, z)
                        || !pathAreaLoaded(entity, range)) {
                    return FALSE;
                }
                if (entity instanceof LuaLivingEntity
                        && ((LuaLivingEntity) entity).navigationStatus() != null) {
                    return valueOf(((LuaLivingEntity) entity).requestNavigation(x, y, z, range));
                }
                PathEntity path = entity.worldObj.getEntityPathToXYZ(entity, x, y, z, range);
                ((EntityCreature) entity).setPathToEntity(path);
                return valueOf(path != null);
            }
        });
        api.set("getNavigationStatus", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                if (!(entity instanceof LuaLivingEntity)) {
                    return NIL;
                }
                String status = ((LuaLivingEntity) entity).navigationStatus();
                return status == null ? NIL : valueOf(status);
            }
        });
        api.set("getNavigationWaypoint", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                if (!(entity instanceof LuaLivingEntity)) {
                    return NIL;
                }
                Vec3D point = ((LuaLivingEntity) entity).navigationWaypoint();
                if (point == null) {
                    return NIL;
                }
                LuaTable position = new LuaTable();
                position.set("x", point.xCoord);
                position.set("y", point.yCoord);
                position.set("z", point.zCoord);
                return position;
            }
        });
        api.set("cancelNavigation", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                return valueOf(entity instanceof LuaLivingEntity
                        && ((LuaLivingEntity) entity).cancelNavigation());
            }
        });
        api.set("getPathTo", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                if (entity.worldObj == null || entity.isDead) {
                    return NIL;
                }
                int x = integer(argument(a, api, 1), "getPathTo.x", -30000000, 30000000);
                int y = integer(argument(a, api, 2), "getPathTo.y", 0, 127);
                int z = integer(argument(a, api, 3), "getPathTo.z", -30000000, 30000000);
                float range = argument(a, api, 4).isnil() ? 16
                        : (float) number(argument(a, api, 4), "getPathTo.range");
                if (range <= 0 || range > 32 || !entity.worldObj.blockExists(x, y, z)
                        || !pathAreaLoaded(entity, range)) {
                    return NIL;
                }
                PathEntity path = entity.worldObj.getEntityPathToXYZ(entity, x, y, z, range);
                if (path == null) {
                    return NIL;
                }
                LuaTable points = new LuaTable();
                for (int index = 1; index <= path.pathLength && index <= 256; index++) {
                    Vec3D point = path.getPosition(entity);
                    LuaTable position = new LuaTable();
                    position.set("x", point.xCoord);
                    position.set("y", point.yCoord);
                    position.set("z", point.zCoord);
                    points.set(index, position);
                    path.incrementPathIndex();
                }
                return points;
            }
        });
        api.set("setTarget", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (!(entity instanceof EntityCreature)) {
                    return FALSE;
                }
                LuaValue requested = argument(a, api, 1);
                if (requested.isnil()) {
                    ((EntityCreature) entity).setTarget(null);
                    ((EntityCreature) entity).setPathToEntity(null);
                    if (entity instanceof LuaLivingEntity) {
                        ((LuaLivingEntity) entity).cancelNavigation();
                    }
                    return TRUE;
                }
                Entity target = target(requested, entity, "setTarget");
                if (target == null || target == entity) {
                    return FALSE;
                }
                ((EntityCreature) entity).setTarget(target);
                return TRUE;
            }
        });
        api.set("getTarget", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                if (!(entity instanceof EntityCreature)) {
                    return NIL;
                }
                Entity target = ((EntityCreature) entity).getTarget();
                return target == null || target.isDead ? NIL : create(scope, null, target);
            }
        });
        api.set("remove", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (entity instanceof TypedEntity) {
                    ((TypedEntity) entity).entityState().removalReason("explicit");
                }
                entity.setEntityDead();
                return NIL;
            }
        });
        if (entity instanceof TypedEntity) {
            EntityInstanceState state = ((TypedEntity) entity).entityState();
            api.set("data", EntityDataAccess.create(scope, entity, state));
            api.set("memory", EntityMemoryAccess.create(scope, entity, state));
            api.set("getIdentity", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    return valueOf(state.identity());
                }
            });
            api.set("getType", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    scope.requireActive();
                    return new EntityTypeReference(AssetKey.parse(state.typeName()));
                }
            });
        }
        api.set("getPosition", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                LuaTable position = new LuaTable();
                position.set("x", entity.posX);
                position.set("y", entity.posY);
                position.set("z", entity.posZ);
                return position;
            }
        });
        api.set("setPosition", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (entity instanceof LuaEntityPart || entity.isDead || entity.worldObj == null) {
                    return FALSE;
                }
                double x = number(argument(a, api, 1), "setPosition.x");
                double y = number(argument(a, api, 2), "setPosition.y");
                double z = number(argument(a, api, 3), "setPosition.z");
                if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000 || y < -64 || y > 4096
                        || !entity.worldObj.blockExists((int) Math.floor(x), 64, (int) Math.floor(z))) {
                    return FALSE;
                }
                entity.setPosition(x, y, z);
                return TRUE;
            }
        });
        api.set("getRotation", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                LuaTable rotation = new LuaTable();
                rotation.set("yaw", entity.rotationYaw);
                rotation.set("pitch", entity.rotationPitch);
                return rotation;
            }
        });
        api.set("getNearbyEntities", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                double radius = number(argument(a, api, 1), "getNearbyEntities.radius");
                if (radius <= 0 || radius > 32) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("getNearbyEntities.radius",
                            "expected a radius > 0 and <= 32");
                }
                AxisAlignedBB area = entity.boundingBox.expand(radius, radius, radius);
                List nearby = entity.worldObj.getEntitiesWithinAABBExcludingEntity(entity, area);
                LuaTable found = new LuaTable();
                int count = 0;
                for (Object value : nearby) {
                    Entity candidate = (Entity) value;
                    if (!candidate.isDead && entity.getDistanceSqToEntity(candidate) <= radius * radius
                            && count < 256) {
                        found.set(++count, create(scope, null, candidate));
                    }
                }
                return found;
            }
        });
        return api;

    }

    private static void installInventoryAccess(LuaTable api, LuaCallbackScope scope, Entity entity,
            EntityInstanceState state) {
        api.set("getInventorySize", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                return type == null || type.inventory == null ? NIL : valueOf(type.inventory.size);
            }
        });
        api.set("getInventoryStack", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.inventory == null) {
                    return NIL;
                }
                int slot = inventorySlot(argument(args, api, 1), type, "getInventoryStack.slot");
                return stackSnapshot(state.inventory(entity, type).getStackInSlot(slot));
            }
        });
        api.set("setInventoryStack", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.inventory == null) {
                    return FALSE;
                }
                int slot = inventorySlot(argument(args, api, 1), type, "setInventoryStack.slot");
                LuaValue value = argument(args, api, 2);
                ItemStack stack = value.isnil() ? null : itemStack(value, "setInventoryStack.stack");
                state.inventory(entity, type).setInventorySlotContents(slot, stack);
                return TRUE;
            }
        });
        api.set("removeInventoryStack", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.inventory == null) {
                    return NIL;
                }
                int slot = inventorySlot(argument(args, api, 1), type, "removeInventoryStack.slot");
                int count = integer(argument(args, api, 2), "removeInventoryStack.count", 1, 64);
                return stackSnapshot(state.inventory(entity, type).decrStackSize(slot, count));
            }
        });
        api.set("openInventory", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.inventory == null) {
                    return FALSE;
                }
                Entity opener = target(argument(args, api, 1), entity, "openInventory");
                EntityInventoryState inventory = state.inventory(entity, type);
                if (!(opener instanceof EntityPlayer) || !inventory.canInteractWith((EntityPlayer) opener)) {
                    return FALSE;
                }
                ((EntityPlayer) opener).displayGUIChest(inventory);
                return TRUE;
            }
        });
        api.set("getEquipmentStack", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                EntityEquipmentDefinition.Slot slot = equipmentSlot(argument(args, api, 1), type,
                        "getEquipmentStack.slot");
                return slot == null ? NIL : stackSnapshot(state.inventory(entity, type).equipment(slot));
            }
        });
        api.set("setEquipmentStack", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                EntityEquipmentDefinition.Slot slot = equipmentSlot(argument(args, api, 1), type,
                        "setEquipmentStack.slot");
                if (slot == null) {
                    return FALSE;
                }
                LuaValue value = argument(args, api, 2);
                state.inventory(entity, type).equipment(slot,
                        value.isnil() ? null : itemStack(value, "setEquipmentStack.stack"));
                return TRUE;
            }
        });
    }

    private static void installRelationAccess(LuaTable api, LuaCallbackScope scope, Entity entity,
            EntityInstanceState state) {
        api.set("getOwner", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.relations == null || !type.relations.ownership) {
                    return NIL;
                }
                Entity owner = state.relations().owner(entity.worldObj);
                return owner == null ? NIL : create(scope, null, owner);
            }
        });
        api.set("getOwnerIdentity", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                String identity = type == null || type.relations == null || !type.relations.ownership
                        ? null : state.relations().ownerIdentity();
                return identity == null ? NIL : valueOf(identity);
            }
        });
        api.set("setOwner", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.relations == null || !type.relations.ownership) {
                    return FALSE;
                }
                LuaValue requested = argument(args, api, 1);
                if (requested.isnil()) {
                    state.relations().setOwner(null);
                    return TRUE;
                }
                Entity owner = target(requested, entity, "setOwner");
                return valueOf(owner != null && owner != entity && state.relations().setOwner(owner));
            }
        });
        api.set("getTeam", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.relations == null) {
                    return NIL;
                }
                String team = state.relations().team(type.relations);
                return team.isEmpty() ? NIL : valueOf(team);
            }
        });
        api.set("setTeam", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.relations == null) {
                    return FALSE;
                }
                LuaValue requested = argument(args, api, 1);
                if (requested.isnil()) {
                    state.relations().resetTeam();
                    return TRUE;
                }
                try {
                    state.relations().team(AssetKey.parse(string(requested, "setTeam.team")).toString());
                    return TRUE;
                } catch (IllegalArgumentException exception) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error("setTeam.team", exception.getMessage());
                }
            }
        });
        api.set("isAlliedWith", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.relations == null) {
                    return FALSE;
                }
                Entity other = target(argument(args, api, 1), entity, "isAlliedWith");
                return valueOf(other == entity
                        || other != null && state.relations().allied(type.relations, other));
            }
        });
    }

    private static void installMountAccess(LuaTable api, LuaCallbackScope scope, Entity entity,
            EntityInstanceState state) {
        api.set("addPassenger", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.mount == null) {
                    return FALSE;
                }
                Entity passenger = target(argument(args, api, 1), entity, "addPassenger");
                return valueOf(EntityMounts.addPassenger(entity, passenger, type.mount));
            }
        });
        api.set("removePassenger", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                return valueOf(type != null && type.mount != null && EntityMounts.removePassenger(entity));
            }
        });
    }

    private static void installBehaviorAccess(LuaTable api, LuaCallbackScope scope, Entity entity,
            EntityInstanceState state) {
        api.set("getBehaviorState", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                String current = type == null ? null : state.behavior().state(type.behavior);
                return current == null ? NIL : valueOf(current);
            }
        });
        api.set("setBehaviorState", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.behavior == null || type.behavior.states.isEmpty()) {
                    return FALSE;
                }
                String next = EntityBehaviorDefinition.name(argument(args, api, 1), "setBehaviorState.state");
                return valueOf(state.behavior().setState(entity, type, next));
            }
        });
        api.set("startTimer", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.behavior == null || type.behavior.onTimer.isnil()) {
                    return FALSE;
                }
                String name = EntityBehaviorDefinition.name(argument(args, api, 1), "startTimer.name");
                int ticks = integer(argument(args, api, 2), "startTimer.ticks", 1, 120000);
                LuaValue repeatValue = argument(args, api, 3);
                int repeat = repeatValue.isnil() ? 0
                        : integer(repeatValue, "startTimer.repeatTicks", 1, 120000);
                state.behavior().start(name, ticks, repeat);
                return TRUE;
            }
        });
        api.set("cancelTimer", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.behavior == null) {
                    return FALSE;
                }
                String name = EntityBehaviorDefinition.name(argument(args, api, 1), "cancelTimer.name");
                return valueOf(state.behavior().cancel(name));
            }
        });
        api.set("getTimer", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntityTypeDefinition type = state.definition();
                if (type == null || type.behavior == null) {
                    return NIL;
                }
                String name = EntityBehaviorDefinition.name(argument(args, api, 1), "getTimer.name");
                int remaining = state.behavior().remaining(name);
                return remaining < 0 ? NIL : valueOf(remaining);
            }
        });
    }

    private static int inventorySlot(LuaValue value, EntityTypeDefinition type, String path) {
        return integer(value, path, 1, type.inventory.size) - 1;
    }

    private static EntityEquipmentDefinition.Slot equipmentSlot(LuaValue value, EntityTypeDefinition type,
            String path) {
        if (type == null || type.equipment == null) {
            return null;
        }
        EntityEquipmentDefinition.Slot slot = EntityEquipmentDefinition.Slot.parse(string(value, path), path);
        return type.equipment.slots.contains(slot) ? slot : null;
    }

    private static ItemStack itemStack(LuaValue value, String path) {
        ItemStack stack = LuaApiUtils.readItemStack(value, true, path);
        if (stack.itemID <= 0 || stack.itemID >= Item.itemsList.length || Item.itemsList[stack.itemID] == null) {
            throw betamoon.luaapi.utils.LuaDeclarationValues.error(path, "must reference a registered item");
        }
        int limit = Math.min(64, stack.getMaxStackSize());
        if (stack.stackSize < 1 || stack.stackSize > limit) {
            throw betamoon.luaapi.utils.LuaDeclarationValues.error(path + ".count",
                    "expected integer 1.." + limit);
        }
        if (stack.getItemDamage() < 0 || stack.getItemDamage() > 32767) {
            throw betamoon.luaapi.utils.LuaDeclarationValues.error(path + ".damage",
                    "expected integer 0..32767");
        }
        return stack;
    }

    private static LuaValue stackSnapshot(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return LuaValue.NIL;
        }
        LuaTable snapshot = new LuaTable();
        snapshot.set("id", stack.itemID);
        snapshot.set("count", stack.stackSize);
        snapshot.set("damage", stack.getItemDamage());
        return snapshot;
    }

    private static VarArgFunction visualVector(LuaCallbackScope scope, LuaTable receiver,
            EntityInstanceState state, String method, double min, double max, boolean scale) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                double x = number(argument(args, receiver, 1), method + ".x");
                double y = number(argument(args, receiver, 2), method + ".y");
                double z = number(argument(args, receiver, 3), method + ".z");
                if (x < min || x > max || y < min || y > max || z < min || z > max) {
                    throw betamoon.luaapi.utils.LuaDeclarationValues.error(method,
                            "components must be within " + min + ".." + max);
                }
                if (scale) {
                    state.presentation().visualScale(x, y, z);
                } else {
                    state.presentation().visualOffset(x, y, z);
                }
                return NONE;
            }
        };
    }

    private static float optionalSoundNumber(LuaValue value, double min, double max, String path) {
        if (value.isnil()) {
            return Float.NaN;
        }
        double parsed = number(value, path);
        if (parsed < min || parsed > max) {
            throw betamoon.luaapi.utils.LuaDeclarationValues.error(path,
                    "expected " + min + ".." + max);
        }
        return (float) parsed;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static LuaTable velocitySnapshot(Entity entity) {
        LuaTable velocity = new LuaTable();
        velocity.set("x", entity.motionX);
        velocity.set("y", entity.motionY);
        velocity.set("z", entity.motionZ);
        return velocity;
    }

    private static int coordinate(LuaValue value) {
        return integer(value, "coordinate", -30000000, 30000000);
    }

    private static boolean pathAreaLoaded(Entity entity, float range) {
        int radius = (int) Math.ceil(range + 8);
        int x = (int) Math.floor(entity.posX);
        int z = (int) Math.floor(entity.posZ);
        return entity.worldObj.checkChunksExist(x - radius, 0, z - radius,
                x + radius, 127, z + radius);
    }

    private static float turnToward(float current, float target, float maximum) {
        float difference = (target - current + 540) % 360 - 180;
        return current + Math.max(-maximum, Math.min(maximum, difference));
    }

    public static Entity target(LuaValue value, Entity source, String method) {
        return target(value, source.worldObj, method);
    }

    public static Entity target(LuaValue value, World world, String method) {
        Entity candidate = entityHandle(value, method);
        return candidate.isDead || candidate.worldObj != world ? null : candidate;
    }

    static Entity entityHandle(LuaValue value, String method) {
        if (!(value instanceof LuaEntityReference)) {
            throw betamoon.luaapi.utils.LuaDeclarationValues.error(method, "expected an entity handle");
        }
        LuaEntityReference reference = (LuaEntityReference) value;
        reference.scope.requireActive();
        return reference.entity;
    }

    private static final class LuaEntityReference extends LuaTable {
        private final LuaCallbackScope scope;
        private final Entity entity;

        private LuaEntityReference(LuaCallbackScope scope, Entity entity) {
            this.scope = scope;
            this.entity = entity;
        }
    }
}
