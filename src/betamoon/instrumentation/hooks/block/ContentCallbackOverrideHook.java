package betamoon.instrumentation.hooks.block;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Redirects native block and item callback call sites without replacing
 * registry objects.
 */
public final class ContentCallbackOverrideHook implements HookModule {
    private static final String PREFIX = "net/minecraft/src/";

    @Override
    public String getId() {
        return "betamoon:content_callback_overrides";
    }

    @Override
    public void register(HookRegistrar registrar) {
        redirect(registrar, "Block", "tick", "updateTick", "(Lnet/minecraft/src/World;IIILjava/util/Random;)V", "World",
                "WorldGenHellLava", "WorldGenLiquids");
        redirect(registrar, "Block", "added", "onBlockAdded", "(Lnet/minecraft/src/World;III)V", "Chunk");
        redirect(registrar, "Block", "removed", "onBlockRemoval", "(Lnet/minecraft/src/World;III)V", "Chunk");
        redirect(registrar, "Block", "neighbor", "onNeighborBlockChange", "(Lnet/minecraft/src/World;IIII)V", "World");
        redirect(registrar, "Block", "placed", "onBlockPlacedBy",
                "(Lnet/minecraft/src/World;IIILnet/minecraft/src/EntityLiving;)V", "ItemBlock", "ItemReed");
        redirect(registrar, "Block", "click", "onBlockClicked",
                "(Lnet/minecraft/src/World;IIILnet/minecraft/src/EntityPlayer;)V", "PlayerControllerSP",
                "PlayerControllerMP");
        redirect(registrar, "Block", "activate", "blockActivated",
                "(Lnet/minecraft/src/World;IIILnet/minecraft/src/EntityPlayer;)Z", "PlayerController");
        redirect(registrar, "Block", "canPlace", "canPlaceBlockOnSide", "(Lnet/minecraft/src/World;IIII)Z", "World");
        redirect(registrar, "Block", "walk", "onEntityWalking",
                "(Lnet/minecraft/src/World;IIILnet/minecraft/src/Entity;)V", "Entity");
        redirect(registrar, "Block", "collide", "onEntityCollidedWithBlock",
                "(Lnet/minecraft/src/World;IIILnet/minecraft/src/Entity;)V", "Entity");
        redirect(registrar, "Block", "exploded", "onBlockDestroyedByExplosion", "(Lnet/minecraft/src/World;III)V",
                "Explosion");
        redirect(registrar, "Block", "drops", "dropBlockAsItemWithChance", "(Lnet/minecraft/src/World;IIIIF)V", "Block",
                "Explosion");
        String useFirst = "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;IIII)Z";
        registrar.register(new CallRedirectHookDefinition(getId() + ":use_first",
                new MethodRef(new ClassRef(PREFIX + "PlayerController"), "sendPlaceBlock", useFirst),
                new MethodRef(new ClassRef("forge/IUseItemFirst"), "onItemUseFirst", useFirst),
                HandlerRef.of("betamoon/luaapi/item/ItemCallbackOverrides", "useFirstInterface",
                        "(Lforge/IUseItemFirst;" + useFirst.substring(1)))
                .inAllMethods());
        redirect(registrar, "Item", "useOnBlock", "onItemUse",
                "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;IIII)Z",
                "ItemStack");
        redirect(registrar, "Item", "use", "onItemRightClick",
                "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/World;Lnet/minecraft/src/EntityPlayer;)Lnet/minecraft/src/ItemStack;",
                "ItemStack");
        redirect(registrar, "Item", "hit", "hitEntity",
                "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/EntityLiving;Lnet/minecraft/src/EntityLiving;)Z",
                "ItemStack");
        redirect(registrar, "Item", "destroyed", "onBlockDestroyed",
                "(Lnet/minecraft/src/ItemStack;IIIILnet/minecraft/src/EntityLiving;)Z", "ItemStack");
        redirect(registrar, "Item", "crafted", "onCreated",
                "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/World;Lnet/minecraft/src/EntityPlayer;)V",
                "ItemStack");
        redirect(registrar, "Item", "inventoryTick", "onUpdate",
                "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/World;Lnet/minecraft/src/Entity;IZ)V", "ItemStack");
        redirect(registrar, "Item", "canHarvest", "canHarvestBlock", "(Lnet/minecraft/src/Block;)Z", "ItemStack");
        redirect(registrar, "Item", "miningSpeed", "getStrVsBlock",
                "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/Block;)F", "ItemStack");
        redirect(registrar, "Item", "miningSpeed", "getStrVsBlock",
                "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/Block;I)F", "EntityPlayer");

        String captureOwner = "betamoon/luaapi/block/BlockDropOverrideCapture";
        registrar.register(AroundHookDefinition
                .builder(getId() + ":capture_drops",
                        new MethodRef(new ClassRef(PREFIX + "Block"), "dropBlockAsItem_do",
                                "(Lnet/minecraft/src/World;IIILnet/minecraft/src/ItemStack;)V"))
                .capture(
                        HandlerRef.of(captureOwner, "capture",
                                "(Lnet/minecraft/src/World;IIILnet/minecraft/src/ItemStack;)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3), ValueBinding.argument(4))
                .onReturn(HandlerRef.of(captureOwner, "complete", "()V")).skipWhenCapturedNonZero().build());
    }

    private void redirect(HookRegistrar registrar, String kind, String handler, String method, String descriptor,
            String... callers) {
        String handlerOwner = "betamoon/luaapi/" + kind.toLowerCase() + "/" + kind + "CallbackOverrides";
        MethodRef invocation = new MethodRef(new ClassRef(PREFIX + kind), method, descriptor);
        for (String caller : callers) {
            registrar.register(new CallRedirectHookDefinition(getId() + ":" + caller + ":" + method + ":" + descriptor,
                    new MethodRef(new ClassRef(PREFIX + caller), method, descriptor), invocation,
                    HandlerRef.of(handlerOwner, handler, "(L" + PREFIX + kind + ";" + descriptor.substring(1)))
                    .inAllMethods());
        }
    }
}
