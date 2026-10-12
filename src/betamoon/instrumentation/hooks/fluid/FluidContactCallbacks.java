package betamoon.instrumentation.hooks.fluid;

import betamoon.fluid.FluidEntityPolicy;
import betamoon.fluid.contact.FluidContactRuntime;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityItem;
import net.minecraft.src.Material;

/** Small bridge from mapped native methods to the fluid contact runtime. */
public final class FluidContactCallbacks {
    private FluidContactCallbacks() {
    }

    public static int pass() {
        return 0;
    }

    public static void complete() {
    }

    public static int customBody(Entity entity) {
        return FluidContactRuntime.hasCustomBody(entity) ? 1 : 0;
    }

    public static int customItemBody(EntityItem item) {
        return FluidContactRuntime.hasCustomBody(item) ? 1 : 0;
    }

    public static boolean itemWaterMovement(EntityItem item, boolean nativeResult, int customBody) {
        return customBody == 0 ? nativeResult : FluidContactRuntime.movement(item, FluidEntityPolicy.Movement.WATER);
    }

    public static boolean waterMovement(Entity entity, boolean nativeResult, int customBody) {
        return customBody == 0 ? nativeResult : FluidContactRuntime.movement(entity, FluidEntityPolicy.Movement.WATER);
    }

    public static boolean lavaMovement(Entity entity, boolean nativeResult, int customBody) {
        return customBody == 0 ? nativeResult : FluidContactRuntime.movement(entity, FluidEntityPolicy.Movement.LAVA);
    }

    public static int customEye(Entity entity) {
        return FluidContactRuntime.hasCustomEye(entity) ? 1 : 0;
    }

    public static boolean eyeMaterial(Entity entity, Material material, boolean nativeResult, int customEye) {
        return customEye == 0 ? nativeResult : FluidContactRuntime.eyeMatchesMaterial(entity, material);
    }

    public static boolean drowning(EntityLiving entity, Material material) {
        return material == Material.water && FluidContactRuntime.hasCustomEye(entity)
                ? FluidContactRuntime.shouldDrown(entity) : entity.isInsideOfMaterial(material);
    }

    public static boolean wet(Entity entity, boolean nativeResult) {
        return FluidContactRuntime.isWet(entity, nativeResult);
    }

    public static int beforeEntityTick(Entity entity) {
        return entity.fire;
    }

    public static int beforeLivingTick(EntityLiving entity) {
        return entity.fire;
    }

    public static void afterEntityTick(Entity entity, int previousFire, boolean immuneToFire) {
        FluidContactRuntime.finishEntityTick(entity, previousFire, immuneToFire);
    }

    public static void afterLivingTick(EntityLiving entity, int previousFire, boolean immuneToFire) {
        FluidContactRuntime.finishLivingTick(entity, previousFire, immuneToFire);
    }

    public static int skipNativeLavaDamage(Entity entity) {
        return FluidContactRuntime.shouldSkipNativeLavaDamage(entity) ? 1 : 0;
    }
}
