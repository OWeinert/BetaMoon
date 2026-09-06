package betamoon.instrumentation.hooks.block;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.MethodRef;

/** Canonical named symbols used by the block hook modules. */
final class BlockHookTargets {
    static final ClassRef PLAYER_CONTROLLER = new ClassRef("net/minecraft/src/PlayerController");
    static final FieldRef PLAYER_CONTROLLER_MC = new FieldRef(PLAYER_CONTROLLER, "mc",
        "Lnet/minecraft/client/Minecraft;");
    static final MethodRef SEND_BLOCK_REMOVED = new MethodRef(PLAYER_CONTROLLER, "sendBlockRemoved", "(IIII)Z");
    static final MethodRef SEND_PLACE_BLOCK = new MethodRef(PLAYER_CONTROLLER, "sendPlaceBlock",
        "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;Lnet/minecraft/src/ItemStack;IIII)Z");

    private BlockHookTargets() {
    }
}
